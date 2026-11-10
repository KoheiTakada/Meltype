// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.Html
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.Executors

data class ClipboardLinkMeta(val title: String?, val imageUrl: String?)
data class ClipboardLinkPreview(val title: String?, val image: Bitmap?)

/**
 * Bounded, best-effort OGP fetcher. Clipboard content is never sent to an
 * intermediary: requests go directly to the original HTTPS page and its
 * HTTPS thumbnail. Private/local hosts and redirects to them are refused.
 * UI falls back to the URL when metadata is unavailable.
 */
object ClipboardLinkPreviewLoader {
    private val worker = Executors.newFixedThreadPool(2)
    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LinkedHashMap<String, ClipboardLinkPreview>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ClipboardLinkPreview>?) =
            size > 48
    }
    private val pending = HashMap<String, MutableList<(ClipboardLinkPreview) -> Unit>>()
    private val metaRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([\w:-]+)\s*=\s*(['"])(.*?)\2""", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""<title\b[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** A user-selected action; never silently rewrite copied links. */
    fun stripParameters(value: String): String {
        val q = value.indexOf('?').takeIf { it >= 0 } ?: value.length
        val hash = value.indexOf('#').takeIf { it >= 0 } ?: value.length
        return value.substring(0, minOf(q, hash))
    }

    fun load(value: String, callback: (ClipboardLinkPreview) -> Unit) {
        synchronized(cache) {
            cache[value]?.let { callback(it); return }
            pending[value]?.let { it.add(callback); return }
            pending[value] = mutableListOf(callback)
        }
        worker.execute {
            val preview = runCatching { fetch(value) }.getOrNull()
                ?: ClipboardLinkPreview(null, null)
            val callbacks = synchronized(cache) {
                cache[value] = preview
                pending.remove(value).orEmpty()
            }
            handler.post { callbacks.forEach { it(preview) } }
        }
    }

    fun parseHtml(html: String): ClipboardLinkMeta {
        var title: String? = null
        var image: String? = null
        for (tag in metaRegex.findAll(html)) {
            val attrs = attrRegex.findAll(tag.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[3]
            }
            when (attrs["property"]?.lowercase() ?: attrs["name"]?.lowercase()) {
                "og:title", "twitter:title" ->
                    if (title == null) title = plain(attrs["content"])
                "og:image", "og:image:url", "twitter:image" ->
                    if (image == null) image = attrs["content"]?.trim()
            }
        }
        if (title == null) title = plain(titleRegex.find(html)?.groupValues?.getOrNull(1))
        return ClipboardLinkMeta(title?.take(160), image)
    }

    private fun plain(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val text = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun safeHttps(url: URL): Boolean {
        val host = url.host.lowercase()
        if (url.protocol.lowercase() != "https" || host.isBlank() ||
            host == "localhost" || host.endsWith(".local") ||
            host.endsWith(".internal") || host.contains(':') ||
            host.matches(Regex("""\d+(\.\d+){3}""")) ||
            !host.contains('.')) return false
        return url.userInfo == null
    }

    private fun read(url: URL, maxBytes: Int, expectedMime: String): ByteArray? {
        var target = url
        for (attempt in 0..2) {
            if (!safeHttps(target)) return null
            val conn = (target.openConnection() as? HttpURLConnection) ?: return null
            try {
                conn.connectTimeout = 2500
                conn.readTimeout = 3000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Meltype/1.0 (Android clipboard preview)")
                conn.setRequestProperty("Accept", if (expectedMime == "text/html") "text/html" else "image/*")
                when (conn.responseCode) {
                    in 300..399 -> {
                        val location = conn.getHeaderField("Location") ?: return null
                        target = URL(target, location)
                        continue
                    }
                    in 200..299 -> {
                        val type = conn.contentType.orEmpty().lowercase()
                        if (!type.startsWith(expectedMime.substringBefore('/'))) return null
                        if (conn.contentLengthLong > maxBytes) return null
                        val output = ByteArrayOutputStream()
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (output.size() <= maxBytes) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        return output.toByteArray().takeIf { it.size <= maxBytes }
                    }
                    else -> return null
                }
            } catch (_: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun fetch(value: String): ClipboardLinkPreview {
        val url = URL(value)
        if (!safeHttps(url)) return ClipboardLinkPreview(null, null)
        val html = read(url, 196_608, "text/html")?.toString(Charsets.UTF_8)
            ?: return ClipboardLinkPreview(null, null)
        val meta = parseHtml(html)
        val image = runCatching {
            val address = meta.imageUrl?.let { URL(url, it) } ?: return@runCatching null
            val bytes = read(address, 1_572_864, "image/") ?: return@runCatching null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            var sample = 1
            while (options.outWidth / sample > 192 || options.outHeight / sample > 192) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
        return ClipboardLinkPreview(meta.title, image)
    }
}
