// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.github.ninjin0802.keyboard.app.layout.LayoutId

/**
 * Space long-press menu. This view knows nothing about the input session:
 * the IME owns confirmation and switching through the supplied callbacks.
 * The menu does not take editor focus.
 */
internal class LanguagePickerPopup(private val context: Context) {
    private var showing: PopupWindow? = null

    fun dismiss() {
        val popup = showing
        showing = null
        popup?.dismiss()
    }

    fun show(
        anchor: View,
        selected: LayoutId,
        allowEmoji: Boolean,
        onSelectLanguage: (LayoutId) -> Unit,
        onEmoji: () -> Unit,
    ): Boolean {
        dismiss()
        val density = context.resources.displayMetrics.density
        val palette = Palette.of(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(),
                (8 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                setColor(palette.key)
                cornerRadius = 17 * density
            }
            elevation = 10 * density
        }
        // focusable=false preserves the editor's composition and cursor.
        val popup = PopupWindow(column, (230 * density).toInt(), -2, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 12 * density
            isOutsideTouchable = true
        }
        fun item(text: String, onClick: () -> Unit) {
            column.addView(TextView(context).apply {
                this.text = text
                textSize = 17f
                setTextColor(palette.text)
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                setOnClickListener {
                    popup.dismiss()
                    onClick()
                }
            }, LinearLayout.LayoutParams(-1, (50 * density).toInt()))
        }
        for ((title, target) in listOf(
            "日本語（自動判別）" to LayoutId.ROMAJI,
            "English" to LayoutId.QWERTY,
        )) {
            item((if (selected == target) "✓  " else "   ") + title) {
                onSelectLanguage(target)
            }
        }
        if (allowEmoji) item("   絵文字", onEmoji)
        showing = popup
        popup.setOnDismissListener { if (showing === popup) showing = null }
        popup.showAtLocation(anchor, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            0, (context.resources.displayMetrics.heightPixels * 0.33f).toInt())
        return true
    }
}
