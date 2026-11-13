// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import io.github.ninjin0802.keyboard.app.R

/**
 * A clipboard icon from Material Symbols (res/drawable/ms_*.xml), tinted with [color] and drawn
 * centred in the view at a size that follows the view, like the other clipboard controls.
 */
class ClipboardIconView(
    context: Context,
    symbol: String,
    color: Int,
) : View(context) {
    private val icon: Drawable = context.getDrawable(iconOf(symbol))!!.mutate().apply { setTint(color) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf((minOf(width, height) * ICON_SCALE).toInt(), (MAX_ICON_DP * resources.displayMetrics.density).toInt())
        if (size <= 0) return
        val left = (width - size) / 2
        val top = (height - size) / 2
        icon.setBounds(left, top, left + size, top + size)
        icon.draw(canvas)
    }

    companion object {
        /** The icon's share of the smaller side of the view. */
        private const val ICON_SCALE = 0.6f

        /** Material Symbols are drawn for 24dp. */
        private const val MAX_ICON_DP = 24

        /** The Material Symbols icon of each clipboard kind and action. */
        fun iconOf(symbol: String): Int = when (symbol) {
            "close" -> R.drawable.ms_close
            "text" -> R.drawable.ms_notes
            "rich" -> R.drawable.ms_article
            "image" -> R.drawable.ms_image
            "file" -> R.drawable.ms_description
            "link" -> R.drawable.ms_link
            "clipboard" -> R.drawable.ms_content_paste
            "copy" -> R.drawable.ms_content_copy
            "edit" -> R.drawable.ms_edit
            "delete" -> R.drawable.ms_delete
            "ocr" -> R.drawable.ms_document_scanner
            "clean" -> R.drawable.ms_format_clear
            "translate" -> R.drawable.ms_translate
            else -> R.drawable.ms_notes
        }
    }
}
