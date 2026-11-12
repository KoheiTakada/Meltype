// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ninjin0802
package io.github.ninjin0802.keyboard.app.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import io.github.ninjin0802.keyboard.engine.Candidate
import io.github.ninjin0802.keyboard.app.clipboard.ClipboardIconView

/** 変換候補を横に並べる。高さは候補が空でも変わらない（縦画面 44dp、横画面 36dp）。 */
class CandidateBarView(context: Context) : HorizontalScrollView(context) {
    var onPick: ((index: Int) -> Unit)? = null
    var onInlinePick: (() -> Unit)? = null

    private var palette = Palette.of(context)
    private val density = resources.displayMetrics.density
    private fun dp(n: Int) = (n * density + .5f).toInt()

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Also restores the 12dp leading/trailing inset visible on iOS.
        setPadding((7*density).toInt(), 0, (7*density).toInt(), 0)
    }
    private var shown: List<Candidate> = emptyList()
    private var shownSelected: Int? = null
    private var inlineSuggestion: BarSuggestion? = null

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setBackgroundColor(Color.TRANSPARENT)
        // Keep a full 2-line candidate visible at both 44dp portrait and 36dp landscape.
        setPadding(dp(10), dp(3), dp(10), dp(3))
        clipToPadding = false
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Personal completion occupies slot three; copied text/images use one centered pill. */
    fun show(candidates: List<Candidate>, selected: Int?, supplement: BarSuggestion? = null) {
        if (candidates == shown && selected == shownSelected && supplement == inlineSuggestion) return
        val redraw = candidates != shown || supplement != inlineSuggestion
        val pillOnly = supplement?.isClipboard == true && candidates.isEmpty()
        if (redraw) {
            row.removeAllViews()
            row.gravity = if (pillOnly) Gravity.CENTER else Gravity.CENTER_VERTICAL
            if (pillOnly) {
                addClipboardPill(supplement!!)
            } else {
                candidates.forEachIndexed { index, candidate ->
                    if (index == 2 && supplement != null) addCandidate(supplement.text, null, supplement.kind)
                    addCandidate(candidate.text, index, null)
                }
                if (candidates.size < 3 && supplement != null) {
                    addCandidate(supplement.text, null, supplement.kind)
                }
            }
            scrollTo(0, 0)
        }
        shown = candidates
        shownSelected = selected
        inlineSuggestion = supplement
        if (pillOnly) return
        for (index in 0 until row.childCount) {
            val nativeIndex = when {
                supplement == null -> index
                index == minOf(2, candidates.size) -> null
                index > 2 -> index - 1
                else -> index
            }
            val label = row.getChildAt(index) as TextView
            val active = nativeIndex != null && nativeIndex == selected
            label.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            label.setTextColor(if (active) palette.pill else palette.text)
        }
        if (selected != null) {
            val index = if (supplement != null && selected >= 2) selected + 1 else selected
            if (index in 0 until row.childCount) {
                val target = row.getChildAt(index)
                post { scrollIntoView(target) }
            }
        }
    }

    private fun addCandidate(value: String, nativeIndex: Int?, kind: SuggestionKind?) {
        val split = kind?.let { PersonalCandidateLines.split(value, it) }
        val secondary = split?.second
        val label = TextView(context).apply {
            contentDescription = (if (nativeIndex == null) "補完候補: " else "変換候補: ") + value
            val primarySize = if (kind == SuggestionKind.PHONE ||
                kind == SuggestionKind.PHONE_SEGMENT || kind == null) 20f else 17f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, primarySize)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            // TextView.minWidth affects measuring; View.minimumWidth is also
            // required for the actual accessible 64dp touch target.
            minWidth = dp(64)
            minimumWidth = dp(64)
            maxWidth = dp(220)
            includeFontPadding = false
            if (secondary != null) {
                setSingleLine(false)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                val styled = SpannableStringBuilder(split?.first ?: value).append("\n").append(secondary)
                val secondLine = (split?.first ?: value).length + 1
                styled.setSpan(RelativeSizeSpan(.67f), secondLine, styled.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(palette.subText), secondLine, styled.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = styled
                setLineSpacing(-dp(2).toFloat(), 1f)
            } else {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                text = value
            }
            setOnClickListener {
                if (nativeIndex == null) onInlinePick?.invoke() else onPick?.invoke(nativeIndex)
            }
        }
        row.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT
        ).apply { marginStart = dp(2); marginEnd = dp(2) })
    }

    /** Copied content does not impersonate a Core conversion candidate. */
    private fun addClipboardPill(suggestion: BarSuggestion) {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(0xFF3B3B3F.toInt())
                cornerRadius = dp(40).toFloat()
            }
            isClickable = true
            isFocusable = true
            contentDescription = if (suggestion.kind == SuggestionKind.CLIPBOARD_IMAGE)
                "スクリーンショットを貼り付ける"
            else "クリップボードを貼り付ける: " + suggestion.text.take(60)
            setOnClickListener { onInlinePick?.invoke() }
        }
        if (suggestion.kind == SuggestionKind.CLIPBOARD_IMAGE && suggestion.thumbnail != null) {
            item.addView(ImageView(context).apply {
                setImageBitmap(suggestion.thumbnail)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(0xFF66666B.toInt())
                    cornerRadius = dp(17).toFloat()
                }
                clipToOutline = true
            }, LinearLayout.LayoutParams(dp(27), dp(27)).apply { marginEnd = dp(8) })
        } else {
            item.addView(ClipboardIconView(context,
                if (suggestion.kind == SuggestionKind.CLIPBOARD_IMAGE) "image" else "copy",
                Color.WHITE),
                LinearLayout.LayoutParams(dp(23), dp(23)).apply { marginEnd = dp(8) })
        }
        item.addView(TextView(context).apply {
            text = if (suggestion.kind == SuggestionKind.CLIPBOARD_IMAGE) "Screenshot"
                else suggestion.text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            maxWidth = dp(175)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        row.addView(item, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
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
        val currentSupplement = inlineSuggestion
        shownSelected = -1
        inlineSuggestion = null
        show(shown, currentSelection, currentSupplement)
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
