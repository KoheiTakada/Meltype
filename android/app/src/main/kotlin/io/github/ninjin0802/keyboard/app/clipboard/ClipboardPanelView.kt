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
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import io.github.ninjin0802.keyboard.app.view.Palette
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A mode of the existing keyboard, not a floating overlay or a separate clipboard app. */
class ClipboardPanelView(context: Context) : LinearLayout(context) {
    var onPick: ((HistoryClip) -> Unit)? = null
    var onAction: ((HistoryClip, String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val scale = resources.displayMetrics.density
    private val colors = Palette.of(context)
    private val cardList = LinearLayout(context).apply { orientation = VERTICAL }
    private val count = TextView(context)
    private val filters = LinearLayout(context).apply { orientation = HORIZONTAL }
    private var contents: List<HistoryClip> = emptyList()
    private var filter = "all"

    init {
        orientation = VERTICAL
        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(7), dp(10), dp(4))
        }
        heading.addView(label("履歴", 17f, colors.text).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LayoutParams(0, dp(37), 1f))
        heading.addView(count, LayoutParams(LayoutParams.WRAP_CONTENT, dp(37)).apply {
            marginEnd = dp(16)
        })
        heading.addView(label("閉じる", 14f, colors.accent).apply {
            gravity = Gravity.CENTER
            setOnClickListener { onClose?.invoke() }
            contentDescription = "クリップボード履歴を閉じる"
        }, LayoutParams(dp(53), dp(37)))
        addView(heading)

        val tabStrip = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(filters, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        addView(tabStrip, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))
        addView(ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(cardList, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        renderTabs()
    }

    fun display(clips: List<HistoryClip>) {
        contents = clips
        count.text = clips.size.toString() + "件 / 30日"
        count.setTextColor(colors.subText)
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        count.gravity = Gravity.CENTER
        renderList()
    }

    private fun renderTabs() {
        filters.removeAllViews()
        for ((id, name) in listOf(
            "all" to "すべて", "text" to "テキスト", "link" to "リンク",
            "rich" to "書式付き", "image" to "画像", "file" to "ファイル",
        )) {
            val active = filter == id
            val chip = label(name, 13f, if (active) Color.WHITE else colors.text).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = shape(if (active) colors.accent else 0x44FFFFFF, 12f)
                setOnClickListener {
                    filter = id
                    renderTabs()
                    renderList()
                }
            }
            filters.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginStart = dp(6)
            })
        }
    }

    private fun renderList() {
        cardList.removeAllViews()
        val visible = contents.filter { filter == "all" || it.kind == filter }
        if (visible.isEmpty()) {
            cardList.addView(label("履歴がありません", 14f, colors.subText).apply {
                gravity = Gravity.CENTER
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(100)))
            return
        }
        for (clip in visible) {
            val tint = kindColor(clip.kind)
            val card = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape(0xCCFFFFFF.toInt(), 13f)
                isClickable = true
                isLongClickable = true
                contentDescription = kindLabel(clip.kind) + "、" + clip.text.take(70)
                setOnClickListener { onPick?.invoke(clip) }
                setOnLongClickListener { showActions(this, clip); true }
            }
            card.addView(View(context).apply { background = shape(tint, 2f) },
                LayoutParams(dp(4), LayoutParams.MATCH_PARENT).apply { marginEnd = dp(10) })
            if (clip.kind == "image") {
                val file = clip.storedName?.let { File(File(context.noBackupFilesDir, "clipboard-media"), it) }
                if (file?.isFile == true) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, options)
                    val sample = maxOf(1, maxOf(options.outWidth / 160, options.outHeight / 110))
                    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                        inSampleSize = sample
                    })
                    if (bitmap != null) card.addView(ImageView(context).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }, LayoutParams(dp(64), dp(52)).apply { marginEnd = dp(10) })
                }
            }
            val info = LinearLayout(context).apply { orientation = VERTICAL }
            val first = label(kindLabel(clip.kind) + "  ·  " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clip.createdAt)),
                11f, tint)
            info.addView(first)
            info.addView(label(clip.text.ifBlank { clip.fileName ?: "ファイル" }, 14f, colors.text).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            })
            card.addView(info, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            card.addView(label("⋯", 22f, colors.subText).apply {
                gravity = Gravity.CENTER
                contentDescription = "追加操作"
                setOnClickListener { showActions(this, clip) }
            }, LayoutParams(dp(38), dp(48)))
            cardList.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, dp(82)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
                bottomMargin = dp(7)
            })
        }
    }

    private fun showActions(anchor: View, clip: HistoryClip) {
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, "貼り付け")
        menu.menu.add(0, 2, 1, "コピー")
        if (!clip.isMedia) {
            menu.menu.add(0, 3, 2, "編集")
            menu.menu.add(0, 4, 3, "翻訳")
        }
        if (clip.kind == "image") menu.menu.add(0, 5, 4, "OCR（文字認識）")
        menu.menu.add(0, 6, 5, "削除")
        menu.setOnMenuItemClickListener {
            val action = when (it.itemId) {
                1 -> "paste"; 2 -> "copy"; 3 -> "edit"
                4 -> "translate"; 5 -> "ocr"; 6 -> "delete"
                else -> return@setOnMenuItemClickListener false
            }
            if (action == "paste") onPick?.invoke(clip) else onAction?.invoke(clip, action)
            true
        }
        menu.show()
    }

    private fun label(value: String, sp: Float, color: Int) = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = true
    }

    private fun shape(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * scale
    }

    private fun kindColor(kind: String): Int = when (kind) {
        "link" -> 0xFF1766CB.toInt()
        "image" -> 0xFF9254BB.toInt()
        "file" -> 0xFFB77A16.toInt()
        "rich" -> 0xFF16836D.toInt()
        else -> 0xFF586474.toInt()
    }

    private fun kindLabel(kind: String): String = when (kind) {
        "link" -> "リンク"; "image" -> "画像"; "file" -> "ファイル"
        "rich" -> "書式付き"; else -> "テキスト"
    }

    private fun dp(value: Int): Int = (value * scale + 0.5f).toInt()
}
