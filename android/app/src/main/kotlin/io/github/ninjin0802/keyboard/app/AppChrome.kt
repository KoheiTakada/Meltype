// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.EditText
import android.widget.TextView

/** Shared Meltype look for Settings, Dictionary, Clipboard editing and future screens. */
object AppChrome {
    data class Colors(val background: Int, val card: Int, val text: Int,
                      val secondary: Int, val divider: Int, val accent: Int, val danger: Int)

    fun colors(context: Context): Colors {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return if (dark) Colors(
            0xFF1D1D1F.toInt(), 0xFF39393C.toInt(), Color.WHITE,
            0xFFC4C4CA.toInt(), 0xFF505055.toInt(), 0xFF4A9CFF.toInt(), 0xFFFF7575.toInt())
        else Colors(
            0xFFF5F5F8.toInt(), Color.WHITE, 0xFF1A1A20.toInt(),
            0xFF686872.toInt(), 0xFFE5E5EA.toInt(), 0xFF1368D3.toInt(), 0xFFC64040.toInt())
    }

    fun dp(context: Context, n: Int): Int = (n * context.resources.displayMetrics.density + .5f).toInt()

    fun shape(context: Context, color: Int, radius: Int = 20,
              stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, radius).toFloat()
        if (stroke != null) setStroke(dp(context, 1), stroke)
    }

    fun text(context: Context, value: String, size: Float = 16f,
             muted: Boolean = false, strong: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(if (muted) colors(context).secondary else colors(context).text)
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun input(context: Context, hintText: String, value: String = "",
              multiline: Boolean = false): EditText = EditText(context).apply {
        background = shape(context, colors(context).card, 15, colors(context).divider)
        setTextColor(colors(context).text)
        setHintTextColor(colors(context).secondary)
        textSize = 17f
        setPadding(dp(context, 16), dp(context, 13), dp(context, 16), dp(context, 13))
        hint = hintText
        setSingleLine(!multiline)
        if (multiline) minLines = 4
        setText(value)
        setSelection(text.length)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun action(context: Context, label: String, filled: Boolean = true,
               destructive: Boolean = false, onClick: () -> Unit): TextView =
        text(context, label, 15f, strong = true).apply {
            val colors = colors(context)
            gravity = android.view.Gravity.CENTER
            minHeight = dp(context, 48)
            background = shape(context, if (filled) {
                if (destructive) colors.danger else colors.accent
            } else colors.card, 14)
            setTextColor(if (filled) Color.WHITE else if (destructive) colors.danger else colors.text)
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /**
     * Android 15+ edge-to-edge and Android 17 gesture navigation both require
     * real insets. Use the same robust fallback as Meltype MainActivity.
     */
    fun protectBars(activity: Activity, root: View) {
        val c = colors(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity.window.setDecorFitsSystemWindows(false)
        activity.window.statusBarColor = c.background
        activity.window.navigationBarColor = c.background
        if (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES) {
            @Suppress("DEPRECATION")
            run {
                activity.window.decorView.systemUiVisibility =
                    activity.window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        fun platform(name: String): Int {
            val res = activity.resources
            val id = res.getIdentifier(name, "dimen", "android")
            return if (id != 0) res.getDimensionPixelSize(id) else 0
        }
        fun apply(insets: WindowInsets?) {
            @Suppress("DEPRECATION")
            val legacyTop = insets?.systemWindowInsetTop ?: 0
            @Suppress("DEPRECATION")
            val legacyBottom = insets?.systemWindowInsetBottom ?: 0
            val top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())?.top ?: 0
            else legacyTop
            val nav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            else legacyBottom
            val ime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0 else 0
            val safeTop = maxOf(top, legacyTop, platform("status_bar_height"))
            val safeBottom = maxOf(nav, legacyBottom, ime, platform("navigation_bar_height"))
            if (root.paddingTop != safeTop || root.paddingBottom != safeBottom)
                root.setPadding(0, safeTop, 0, safeBottom)
        }
        root.setOnApplyWindowInsetsListener { _, insets -> apply(insets); insets }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                apply(activity.window.decorView.rootWindowInsets)
                view.requestApplyInsets()
            }
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
        apply(activity.window.decorView.rootWindowInsets)
    }
}

/** Custom-drawn switch: no platform widget appearance or OEM drawable leaks. */
class AppToggle(context: Context) : View(context), Checkable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val unit = resources.displayMetrics.density
    private var checked = false
    var onToggle: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "切り替え"
    }

    override fun isChecked(): Boolean = checked
    override fun setChecked(value: Boolean) {
        if (checked == value) return
        checked = value
        invalidate()
        onToggle?.invoke(value)
    }
    override fun toggle() { setChecked(!checked) }
    override fun performClick(): Boolean {
        toggle()
        super.performClick()
        return true
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((52 * unit + .5f).toInt(), (36 * unit + .5f).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = AppChrome.colors(context)
        val midY = height / 2f
        val r = 15f * unit
        paint.color = if (checked) p.accent else p.divider
        canvas.drawRoundRect(0f, midY-r, width.toFloat(), midY+r, r, r, paint)
        paint.color = Color.WHITE
        val cx = if (checked) width - r - 3f * unit else r + 3f * unit
        canvas.drawCircle(cx, midY, 12f * unit, paint)
    }
    override fun getAccessibilityClassName(): CharSequence = "android.widget.Switch"
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = checked
    }
}
