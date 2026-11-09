// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.ClipData
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.webkit.URLUtil
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** A clip is retained for exactly 30 days from its most recent copy. */
data class HistoryClip(
    val id: Long,
    val kind: String,
    val text: String,
    val html: String?,
    val mime: String,
    val fileName: String?,
    val storedName: String?,
    val createdAt: Long,
) {
    val isMedia: Boolean get() = kind == "image" || kind == "file"
}

/**
 * Private, non-backed-up SQLite history and owned copies of media.
 * Never persist third-party content:// URIs: their read grants can expire.
 * Call from a background thread except for individual reads on editor screens.
 */
class ClipboardHistoryStore(private val app: Context) :
    SQLiteOpenHelper(app, "clipboard_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE clips (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, " +
                "text TEXT NOT NULL, html TEXT, mime TEXT NOT NULL, " +
                "file_name TEXT, stored_name TEXT, created_at INTEGER NOT NULL, " +
                "fingerprint TEXT NOT NULL UNIQUE)"
        )
        db.execSQL("CREATE INDEX clips_by_time ON clips(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Clipboard schema migration required")
    }

    /** The marker is set by password managers and other privacy-aware apps. */
    fun isSensitive(clip: ClipData): Boolean =
        clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true

    fun capture(clip: ClipData): Boolean {
        if (isSensitive(clip)) return false
        var saved = false
        for (index in 0 until minOf(clip.itemCount, 8)) {
            val item = clip.getItemAt(index)
            val uri = item.uri
            val type = uri?.let { runCatching { app.contentResolver.getType(it) }.getOrNull() }
                ?: (0 until clip.description.mimeTypeCount)
                    .map { clip.description.getMimeType(it) }
                    .firstOrNull { it.startsWith("image/") || it.startsWith("application/") }
            if (uri != null && type != null) {
                val kind = if (type.startsWith("image/")) "image" else "file"
                val name = uri.lastPathSegment?.takeLast(100) ?: kind
                val stored = copyToPrivateFile(uri)
                if (stored != null) {
                    val hash = sha256(stored.readBytes())
                    insert(kind, name, null, type, name, stored.name, kind + ":" + hash)
                    saved = true
                    continue
                }
            }
            val plain = item.text?.toString() ?: continue
            if (plain.isBlank()) continue
            val html = item.htmlText
            val kind = when {
                URLUtil.isNetworkUrl(plain.trim()) -> "link"
                !html.isNullOrBlank() -> "rich"
                else -> "text"
            }
            val content = plain.take(MAX_TEXT)
            val fingerprint = kind + ":" + sha256((if (html == null) content else content + "\u0000" + html.take(MAX_TEXT)).toByteArray())
            insert(kind, plain.take(MAX_TEXT), html?.take(MAX_TEXT), "text/plain", null, null, fingerprint)
            saved = true
        }
        if (saved) prune()
        return saved
    }

    fun saveText(value: String): Long? {
        if (value.isBlank()) return null
        val kind = if (URLUtil.isNetworkUrl(value.trim())) "link" else "text"
        val id = insert(
            kind, value.take(MAX_TEXT), null, "text/plain", null, null,
            kind + ":" + sha256(value.take(MAX_TEXT).toByteArray()),
        )
        prune()
        return id
    }

    private fun insert(
        kind: String, text: String, html: String?, mime: String,
        fileName: String?, storedName: String?, fingerprint: String,
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT id, stored_name FROM clips WHERE fingerprint = ?", arrayOf(fingerprint)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val oldName = cursor.getString(1)
                    // Keep existing IDs: editors may still hold a URI grant to this row.
                    if (storedName != null && oldName != storedName) ownedFile(storedName)?.delete()
                    val updated = android.content.ContentValues().apply {
                        put("created_at", System.currentTimeMillis())
                    }
                    db.update("clips", updated, "id = ?", arrayOf(id.toString()))
                    db.setTransactionSuccessful()
                    return id
                }
            }
            val values = android.content.ContentValues().apply {
                put("kind", kind)
                put("text", text)
                put("html", html)
                put("mime", mime)
                put("file_name", fileName)
                put("stored_name", storedName)
                put("created_at", System.currentTimeMillis())
                put("fingerprint", fingerprint)
            }
            val id = db.insertOrThrow("clips", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun list(): List<HistoryClip> {
        prune()
        val result = ArrayList<HistoryClip>()
        readableDatabase.rawQuery(
            "SELECT id, kind, text, html, mime, file_name, stored_name, created_at " +
                "FROM clips ORDER BY created_at DESC, id DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += HistoryClip(
                    c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getString(5), c.getString(6), c.getLong(7),
                )
            }
        }
        return result
    }

    fun get(id: Long): HistoryClip? =
        readableDatabase.rawQuery(
            "SELECT id, kind, text, html, mime, file_name, stored_name, created_at " +
                "FROM clips WHERE id = ? AND created_at >= ?",
            arrayOf(id.toString(), (System.currentTimeMillis() - RETENTION_MS).toString()),
        ).use { c ->
            if (!c.moveToFirst()) null else HistoryClip(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getString(5), c.getString(6), c.getLong(7),
            )
        }

    fun edit(id: Long, value: String): Boolean {
        val previous = get(id) ?: return false
        if (previous.isMedia || value.isBlank()) return false
        val kind = if (URLUtil.isNetworkUrl(value.trim())) "link" else "text"
        val values = android.content.ContentValues().apply {
            put("text", value.take(MAX_TEXT))
            putNull("html")
            put("kind", kind)
            put("mime", "text/plain")
            put("fingerprint", kind + ":" + sha256(value.take(MAX_TEXT).toByteArray()))
        }
        return runCatching {
            writableDatabase.update("clips", values, "id = ?", arrayOf(id.toString())) > 0
        }.getOrDefault(false)
    }

    fun delete(id: Long) {
        get(id)?.storedName?.let { ownedFile(it)?.delete() }
        writableDatabase.delete("clips", "id = ?", arrayOf(id.toString()))
    }

    /** Expired media are removed with the rows; no clipboard data is exported or backed up. */
    fun prune() {
        val db = writableDatabase
        val cutoff = (System.currentTimeMillis() - RETENTION_MS).toString()
        db.rawQuery("SELECT stored_name FROM clips WHERE created_at < ?", arrayOf(cutoff)).use { c ->
            while (c.moveToNext()) c.getString(0)?.let { ownedFile(it)?.delete() }
        }
        db.delete("clips", "created_at < ?", arrayOf(cutoff))
    }

    fun mediaFile(clip: HistoryClip): File? =
        clip.storedName?.let(::ownedFile)?.takeIf { it.isFile }

    private fun ownedFile(name: String): File? {
        if (!name.matches(Regex("[a-f0-9-]{36}"))) return null
        return File(File(app.noBackupFilesDir, "clipboard-media"), name)
    }

    private fun copyToPrivateFile(uri: Uri): File? {
        val dir = File(app.noBackupFilesDir, "clipboard-media")
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, UUID.randomUUID().toString())
        try {
            val source = app.contentResolver.openInputStream(uri) ?: return null
            source.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_MEDIA_BYTES) throw IOException("Clipboard media too large")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (target.length() == 0L) throw IOException("Empty clipboard media")
            return target
        } catch (_: Exception) {
            target.delete()
            return null
        }
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_TEXT = 200_000
        const val MAX_MEDIA_BYTES = 16L * 1024 * 1024
        const val CONTENT_AUTHORITY = "app.meltype.keyboard.clipboard"

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
