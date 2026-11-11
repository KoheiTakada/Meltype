// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ninjin0802
package io.github.ninjin0802.keyboard.app.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.ninjin0802.keyboard.engine.Candidate

/** 変換候補を横に並べる。高さは候補が空でも変わらない（縦画面 44dp、横画面 36dp）。 */
class CandidateBarView(context: Context) : HorizontalScrollView(context) {
    var onPick: ((index: Int) -> Unit)? = null
    var onInlinePick: (() -> Unit)? = null

    private var palette = Palette.of(context)
    private val density = resources.displayMetrics.density

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        // Also restores the 12dp leading/trailing inset visible on iOS.
        setPadding((7*density).toInt(), 0, (7*density).toInt(), 0)
    }
    private var shown: List<Candidate> = emptyList()
    private var shownSelected: Int? = null
    private var inlineText: String? = null

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(Color.TRANSPARENT)
        setPadding((10*density).toInt(), (10*density).toInt(),
                   (10*density).toInt(), (8*density).toInt())
        clipToPadding = false
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    /** Supplemental Android suggestion is visually the third choice, never part of Core's candidate indices. */
    fun show(candidates: List<Candidate>, selected: Int?, supplement: String? = null) {
        if (candidates == shown && selected == shownSelected && supplement == inlineText) return
        val changed = candidates != shown || supplement != inlineText
        if (changed) {
            row.removeAllViews()
            val pad = (17 * density).toInt()
            fun add(textValue: String, index: Int?) {
                val tv = TextView(context).apply {
                    text = textValue
                    contentDescription = if (index == null) "補完候補: " + textValue else "変換候補: " + textValue
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (index == null) 17f else 20f)
                    gravity = Gravity.CENTER
                    setPadding(pad, 0, pad, 0)
                    minWidth = (64 * density).toInt()
                    minimumWidth = (64 * density).toInt()
                    maxWidth = (210 * density).toInt()
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = true
                    typeface = Typeface.DEFAULT
                    setOnClickListener {
                        if (index == null) onInlinePick?.invoke() else onPick?.invoke(index)
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { marginStart = (3 * density).toInt(); marginEnd = (3 * density).toInt() }
                row.addView(tv, lp)
            }
            candidates.forEachIndexed { i, candidate ->
                if (i == 2 && supplement != null) add(supplement, null)
                add(candidate.text, i)
            }
            if (candidates.size < 3 && supplement != null) add(supplement, null)
            scrollTo(0, 0)
        }
        shown = candidates
        shownSelected = selected
        inlineText = supplement
        for (i in 0 until row.childCount) {
            val original = if (supplement == null) i
                else when {
                    i == minOf(2, candidates.size) -> null
                    i > 2 -> i - 1
                    else -> i
                }
            val tv = row.getChildAt(i) as TextView
            val on = original != null && original == selected
            tv.background = null
            tv.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tv.setTextColor(if (on) palette.pill else palette.text)
        }
        if (selected != null) {
            val i = if (supplement != null && selected >= 2) selected + 1 else selected
            if (i in 0 until row.childCount) {
                val target = row.getChildAt(i)
                post { scrollIntoView(target) }
            }
        }
    }

    private fun scrollIntoView(v: View) {
        val left = v.left
        val right = v.right
        when {
            left < scrollX -> scrollTo(left, 0)
            right > scrollX + width -> scrollTo(right - width, 0)
        }
    }

    /** 画面が回ったら、作り直されなくても、その向きの高さで測り直す。 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        palette = Palette.of(context)
        val currentSelection = shownSelected
        shownSelected = null
        show(shown, currentSelection, inlineText)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthDp = MeasureSpec.getSize(widthMeasureSpec) / density
        val heightDp = maxOf(
            resources.configuration.screenHeightDp.toFloat(),
            resources.displayMetrics.heightPixels / density,
        )
        val barHeight = (KeyboardHeights.forViewport(widthDp, heightDp).barDp * density).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY))
    }
}
