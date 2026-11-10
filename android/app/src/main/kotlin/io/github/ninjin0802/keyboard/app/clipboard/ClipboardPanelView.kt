// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.ninjin0802.keyboard.app.view.Palette
import java.io.File
import java.net.URI

/**
 * The clipboard is another keyboard mode: no pop-up menus, no extra screen
 * height, tap the card to paste, tap an icon to perform a secondary operation.
 */
class ClipboardPanelView(context: Context) : LinearLayout(context) {
    var onPick: ((HistoryClip) -> Unit)? = null
    var onAction: ((HistoryClip, String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val colors = Palette.of(context)
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val filters = LinearLayout(context).apply { orientation = HORIZONTAL }
    private var clips: List<HistoryClip> = emptyList()
    private var filter = "all"

    init {
        orientation = VERTICAL
        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(8), dp(3))
            addView(label("履歴", 16f, colors.text).apply {
                typeface = android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL)
            }, LayoutParams(0, dp(37), 1f))
            addView(ClipboardIconView(context, "close", colors.subText).apply {
                contentDescription = "履歴を閉じる"
                isClickable = true
                isFocusable = true
                setOnClickListener { onClose?.invoke() }
            }, LayoutParams(dp(43), dp(37)))
        }
        addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(filters, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(39)))

        addView(ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        renderTabs()
    }

    fun display(items: List<HistoryClip>) {
        clips = items
        renderList()
    }

    private fun renderTabs() {
        filters.removeAllViews()
        for ((id, name) in listOf(
            "all" to "すべて", "text" to "テキスト", "link" to "リンク",
            "rich" to "書式付き", "image" to "画像", "file" to "ファイル",
        )) {
            val active = filter == id
            filters.addView(label(name, 12f,
                if (active) colors.onAccent else colors.text).apply {
                gravity = Gravity.CENTER
                setPadding(dp(13), 0, dp(13), 0)
                background = shape(if (active) colors.accent else 0xC9FFFFFF.toInt(), 14f)
                isClickable = true
                setOnClickListener {
                    filter = id
                    renderTabs()
                    renderList()
                }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(29)).apply {
                marginStart = dp(7)
            })
        }
    }

    private fun renderList() {
        list.removeAllViews()
        val shown = clips.filter { filter == "all" || it.kind == filter }
        if (shown.isEmpty()) {
            list.addView(label("まだ履歴がありません", 13f, colors.subText).apply {
                gravity = Gravity.CENTER
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(110)))
            return
        }
        for ((index, item) in shown.withIndex()) {
            val tint = kindColor(item.kind)
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(11), dp(8), dp(11), dp(6))
                background = shape(colors.key, 13f)
                isClickable = true
                isLongClickable = true
                contentDescription = kindLabel(item.kind) + "。" + item.text.take(60) +
                    "。タップで貼り付け"
                setOnClickListener { onPick?.invoke(item) }
                setOnLongClickListener {
                    val action = when (item.kind) {
                        "image" -> "ocr"
                        "file" -> "copy"
                        else -> "edit"
                    }
                    onAction?.invoke(item, action)
                    true
                }
            }
            val metadata = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ClipboardIconView(context, item.kind, tint),
                    LayoutParams(dp(24), dp(24)))
                addView(label(relativeTime(item.createdAt), 11f, colors.subText),
                    LayoutParams(LayoutParams.WRAP_CONTENT, dp(24)).apply {
                        marginStart = dp(5)
                    })
            }
            card.addView(metadata, LayoutParams(LayoutParams.MATCH_PARENT, dp(24)))

            when (item.kind) {
                "image" -> drawImage(card, item)
                "link" -> drawLink(card, item, index)
                "file" -> drawFile(card, item)
                else -> drawText(card, item)
            }

            val actions = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                val buttons = ArrayList<Pair<String, String>>()
                buttons += "copy" to "コピー"
                if (!item.isMedia) {
                    buttons += "edit" to "編集"
                    buttons += "translate" to "翻訳"
                }
                if (item.kind == "image") buttons += "ocr" to "OCR"
                if (item.kind == "link" &&
                    ClipboardLinkPreviewLoader.stripParameters(item.text) != item.text) {
                    buttons += "clean" to "URLのパラメータを除去"
                }
                buttons += "delete" to "削除"
                for ((action, title) in buttons) {
                    addView(ClipboardIconView(context, action,
                        if (action == "delete") colors.subText else colors.accent).apply {
                        contentDescription = title
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onAction?.invoke(item, action) }
                    }, LayoutParams(dp(40), dp(32)))
                }
            }
            card.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, dp(35)).apply {
                topMargin = dp(2)
            })
            list.addView(card, LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(9); marginEnd = dp(9); bottomMargin = dp(7)
            })
        }
    }

    private fun drawText(card: LinearLayout, clip: HistoryClip) {
        card.addView(label(clip.text, 13.5f, colors.text).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
    }

    private fun drawFile(card: LinearLayout, clip: HistoryClip) {
        card.addView(label(clip.fileName ?: "ファイル", 13.5f, colors.text).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })
    }

    private fun drawImage(card: LinearLayout, clip: HistoryClip) {
        val file = clip.storedName?.let {
            File(File(context.noBackupFilesDir, "clipboard-media"), it)
        }
        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            background = shape(0xFFF3F5F7.toInt(), 10f)
            contentDescription = "クリップボードの画像"
        }
        if (file?.isFile == true) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 400) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample
            })?.let(preview::setImageBitmap)
        }
        card.addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, dp(112)).apply {
            topMargin = dp(4)
        })
    }

    private fun drawLink(card: LinearLayout, clip: HistoryClip, index: Int) {
        val link = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val thumbnail = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = shape(0xFFF3F5F7.toInt(), 9f)
            contentDescription = "Webページのプレビュー画像"
        }
        link.addView(thumbnail, LayoutParams(dp(66), dp(60)).apply {
            marginEnd = dp(10)
        })
        val copy = LinearLayout(context).apply { orientation = VERTICAL }
        val domain = runCatching { URI(clip.text).host?.removePrefix("www.") }
            .getOrNull().orEmpty().ifBlank { "Webリンク" }
        val title = label(domain, 13f, colors.text).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL)
        }
        copy.addView(title)
        copy.addView(label(clip.text, 11f, colors.subText).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        link.addView(copy, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        card.addView(link, LayoutParams(LayoutParams.MATCH_PARENT, dp(66)).apply {
            topMargin = dp(4)
        })
        // Limit background web requests if hundreds of links are in the 30-day list.
        if (index < 12) ClipboardLinkPreviewLoader.load(clip.text) { result ->
            if (title.parent != null) {
                result.title?.let { title.text = it }
                result.image?.let { thumbnail.setImageBitmap(it) }
            }
        }
    }

    private fun relativeTime(timestamp: Long): String {
        val seconds = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0)) / 1000L
        return when {
            seconds < 60 -> "今"
            seconds < 3600 -> (seconds / 60).toString() + "分前"
            seconds < 86400 -> (seconds / 3600).toString() + "時間前"
            seconds < 172800 -> "昨日"
            else -> (seconds / 86400).toString() + "日前"
        }
    }

    private fun label(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = true
    }

    private fun shape(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpF(radius)
    }

    private fun kindColor(kind: String): Int = when (kind) {
        "link" -> 0xFF1766CB.toInt()
        "image" -> 0xFF9254BB.toInt()
        "file" -> 0xFFB77A16.toInt()
        "rich" -> 0xFF16836D.toInt()
        else -> 0xFF586474.toInt()
    }

    private fun kindLabel(kind: String): String = when (kind) {
        "link" -> "リンク"
        "image" -> "画像"
        "file" -> "ファイル"
        "rich" -> "書式付き"
        else -> "テキスト"
    }

    private fun dpF(value: Float) = value * density
    private fun dp(value: Int) = (value * density + 0.5f).toInt()
}
