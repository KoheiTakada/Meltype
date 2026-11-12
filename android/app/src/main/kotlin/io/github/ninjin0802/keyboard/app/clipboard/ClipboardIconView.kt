// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small consistent line icons, without emoji glyph metrics or platform menu chrome. */
class ClipboardIconView(
    context: Context,
    private val symbol: String,
    color: Int,
) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val factor = minOf(width, height) / 24f * 0.54f
        if (factor <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - 24f * factor) / 2f, (height - 24f * factor) / 2f)
        canvas.scale(factor, factor)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1.8f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            canvas.drawLine(x1, y1, x2, y2, ink)
        }
        fun rect(x1: Float, y1: Float, x2: Float, y2: Float, r: Float = 2f) {
            canvas.drawRoundRect(x1, y1, x2, y2, r, r, ink)
        }
        fun poly(vararg points: Float) {
            val p = Path().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            }
            canvas.drawPath(p, ink)
        }
        when (symbol) {
            "close" -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
            "text" -> { line(4f, 6f, 20f, 6f); line(4f, 12f, 17f, 12f); line(4f, 18f, 13f, 18f) }
            "rich" -> { rect(3f, 4f, 21f, 20f); line(7f, 9f, 17f, 9f); line(7f, 14f, 14f, 14f) }
            "image" -> {
                rect(3f, 4f, 21f, 20f)
                canvas.drawCircle(9f, 10f, 1.5f, ink)
                poly(4f, 18f, 10f, 13f, 14f, 16f, 17f, 12f, 21f, 18f)
            }
            "file" -> {
                poly(7f, 3f, 15f, 3f, 20f, 8f, 20f, 21f, 5f, 21f, 5f, 3f, 7f, 3f)
                poly(15f, 3f, 15f, 8f, 20f, 8f)
                line(9f, 13f, 16f, 13f); line(9f, 17f, 16f, 17f)
            }
            "link" -> {
                rect(2f, 8f, 13f, 17f, 4f)
                rect(11f, 7f, 22f, 16f, 4f)
                line(9f, 12f, 15f, 12f)
            }
            // Clipboard-history control: conventional board, clip and ruled paper,
            // matching Gboard's recognisable clipboard silhouette rather than a glyph.
            "clipboard" -> {
                rect(5f, 6f, 19f, 21f, 2f)
                rect(9f, 3f, 15f, 8f, 1.3f)
                line(9f, 12f, 15f, 12f)
                line(9f, 15.5f, 15f, 15.5f)
                line(9f, 19f, 14f, 19f)
            }
            "copy" -> { rect(7f, 7f, 20f, 21f); poly(4f, 17f, 4f, 3f, 16f, 3f) }
            "edit" -> {
                poly(4f, 20f, 8f, 19f, 20f, 7f, 17f, 4f, 5f, 16f, 4f, 20f)
                line(15f, 6f, 18f, 9f)
            }
            "delete" -> {
                poly(5f, 6f, 19f, 6f); poly(9f, 4f, 15f, 4f)
                poly(7f, 8f, 8f, 21f, 16f, 21f, 17f, 8f)
                line(11f, 11f, 11f, 17f); line(14f, 11f, 14f, 17f)
            }
            "ocr" -> {
                poly(3f, 9f, 3f, 3f, 9f, 3f); poly(15f, 3f, 21f, 3f, 21f, 9f)
                poly(3f, 15f, 3f, 21f, 9f, 21f); poly(15f, 21f, 21f, 21f, 21f, 15f)
                line(8f, 10f, 16f, 10f); line(12f, 10f, 12f, 17f)
            }
            "clean" -> {
                rect(3f, 5f, 21f, 19f)
                line(8f, 9f, 16f, 9f); line(8f, 14f, 13f, 14f)
                line(3f, 21f, 21f, 3f)
            }
            "translate" -> {
                ink.style = Paint.Style.FILL
                ink.textSize = 17f
                ink.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText("文", 3f, 18f, ink)
                ink.typeface = null
            }
            else -> canvas.drawCircle(12f, 12f, 7f, ink)
        }
        canvas.restoreToCount(saved)
    }
}
