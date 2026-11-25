// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.keyboard.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.meltype.keyboard.view.Palette

/**
 * 5 分以内にコピーしたもの (文字かスクリーンショット) を、候補バーの空いているときに出す 1 つのボタン。
 * 変換候補とは見た目を分け、Core の候補の番号には入れない。
 */
data class ClipboardPill(val text: String, val image: Boolean, val thumbnail: Bitmap?) {
    fun view(context: Context, palette: Palette, onClick: () -> Unit): View {
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + .5f).toInt()
        // 候補バーの高さ (縦 44dp・横 36dp) から上下の余白を引いた高さに収める
        val icon = dp(18)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(if (thumbnail != null) dp(4) else dp(10), 0, dp(12), 0)
            background = GradientDrawable().apply {
                setColor(palette.key)
                cornerRadius = dp(13).toFloat()
            }
            isClickable = true
            isFocusable = true
            contentDescription = if (image) "スクリーンショットを貼り付ける" else "クリップボードを貼り付ける: " + text.take(60)
            setOnClickListener { onClick() }
            if (thumbnail != null) {
                addView(ImageView(context).apply {
                    setImageBitmap(thumbnail)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply {
                        setColor(palette.keyPressed)
                        cornerRadius = icon / 2f
                    }
                    clipToOutline = true
                }, LinearLayout.LayoutParams(icon, icon).apply { marginEnd = dp(6) })
            } else {
                addView(ClipboardIconView(context, if (image) "image" else "clipboard", palette.text),
                    LinearLayout.LayoutParams(icon, icon).apply { marginEnd = dp(6) })
            }
            addView(TextView(context).apply {
                this.text = if (image) "Screenshot" else this@ClipboardPill.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(palette.text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                maxWidth = dp(175)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        }
    }
}
