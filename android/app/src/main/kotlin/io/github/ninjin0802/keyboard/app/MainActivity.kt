// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import io.github.ninjin0802.keyboard.app.layout.LayoutId
import java.util.Locale

/**
 * The settings: one page that scrolls from top to bottom, in sections with a heading each.
 * A choice opens a drop-down menu at its row; nothing opens another page.
 * Only a pending setup step is shown (at the top); no tutorial or demo editor.
 */
class MainActivity : Activity() {
    private val settings by lazy { Settings(this) }
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(n: Int) = (n * density + .5f).toInt()

    /** The page being shown, to keep its scroll position when it is drawn again. */
    private var scroller: ScrollView? = null

    /** The last drop-down menu opened (device tests choose an item in it). */
    internal var openDropdownForTest: PopupMenu? = null
        private set

    /**
     * Android 15+ enforces edge-to-edge. Keep all settings content below the
     * status bar / display cutout and above the system navigation bar.
     */
    private fun protectSystemBars(root: View): View {
        // Some OEM edge-to-edge windows dispatch consumed (zero) insets to
        // Activity content even though the status bar is visibly overlaying it.
        // Use the actual window insets where available and the platform bar
        // dimensions as a fallback, rather than assuming a zero top inset.
        fun platformBarHeight(name: String): Int {
            val id = resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) resources.getDimensionPixelSize(id) else 0
        }
        fun applyBars(insets: WindowInsets?) {
            @Suppress("DEPRECATION")
            val reportedTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())?.top ?: 0
            } else insets?.systemWindowInsetTop ?: 0
            @Suppress("DEPRECATION")
            val reportedBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            } else insets?.systemWindowInsetBottom ?: 0
            // Legacy and synthetic window-inset dispatch can carry dimensions
            // not populated in the newer typed accessors (also true on OEMs).
            // Keep the larger nonzero reported value so nothing enters the bars.
            @Suppress("DEPRECATION")
            val legacyTop = insets?.systemWindowInsetTop ?: 0
            @Suppress("DEPRECATION")
            val legacyBottom = insets?.systemWindowInsetBottom ?: 0
            val top = maxOf(reportedTop, legacyTop, platformBarHeight("status_bar_height"))
            val bottom = maxOf(reportedBottom, legacyBottom, platformBarHeight("navigation_bar_height"))
            if (root.paddingTop != top || root.paddingBottom != bottom) {
                root.setPadding(0, top, 0, bottom)
            }
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            applyBars(insets)
            insets
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // Initial content insets can be zero before the first traversal.
                applyBars(window.decorView.rootWindowInsets)
                view.requestApplyInsets()
            }
            override fun onViewDetachedFromWindow(view: View) = Unit
        })
        // Protect the very first rendered frame, before Android delivers insets.
        applyBars(window.decorView.rootWindowInsets)
        return root
    }

    private data class Theme(val background: Int, val card: Int, val text: Int,
                             val secondary: Int, val line: Int)
    private val theme by lazy {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES) Theme(
            0xFF1D1D1F.toInt(), 0xFF39393C.toInt(), Color.WHITE,
            0xFFC4C4CA.toInt(), 0xFF505055.toInt()
        ) else Theme(
            0xFFF5F5F8.toInt(), Color.WHITE, 0xFF1A1A20.toInt(),
            0xFF686872.toInt(), 0xFFE5E5EA.toInt()
        )
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Let our root own *all* bar insets. Android 15+ is edge-to-edge
        // regardless of target window flags; explicitly match that behavior
        // on Android 11-14 so our layout receives unconsumed status insets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES) {
            @Suppress("DEPRECATION")
            // Keep LAYOUT_FULLSCREEN / LAYOUT_HIDE_NAVIGATION flags installed
            // by setDecorFitsSystemWindows(false); replacing all flags here
            // would silently undo the edge-to-edge layout policy.
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun background(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(22).toFloat()
    }

    private fun text(value: String, size: Float, muted: Boolean = false, strong: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size
            setTextColor(if (muted) theme.secondary else theme.text)
            if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun LinearLayout.divider() {
        addView(View(this@MainActivity).apply { setBackgroundColor(theme.line) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                marginStart = dp(18); marginEnd = dp(18)
            })
    }

    /**
     * One row. With [checked], a switch. With [value], the current choice and a drop-down mark:
     * [action] gets the row to open the menu at. With only [action], a row that opens something.
     */
    private fun LinearLayout.entry(
        title: String, detail: String? = null,
        checked: Boolean? = null, onToggle: ((Boolean) -> Unit)? = null,
        value: String? = null, action: ((View) -> Unit)? = null,
    ) {
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), dp(14), dp(10))
            minimumHeight = dp(if (detail == null) 60 else 76)
        }
        val labels = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 16f, strong = true))
        if (detail != null) labels.addView(text(detail, 13f, muted = true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        if (checked != null) {
            val toggle = Switch(this@MainActivity).apply {
                isChecked = checked
                contentDescription = title
                setOnCheckedChangeListener { _, on -> onToggle?.invoke(on) }
            }
            row.addView(toggle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
            row.isFocusable = true
        } else if (action != null) {
            if (value != null) {
                row.addView(text(value, 15f, muted = true),
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            }
            row.addView(text(if (value != null) "▾" else "›", 22f, muted = true).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), -2).apply { marginStart = dp(4) })
            row.setOnClickListener { action(it) }
            row.isFocusable = true
        }
        addView(row)
    }

    /** A section: its heading, then its rows on one card. */
    private fun section(container: LinearLayout, heading: String?, items: LinearLayout.() -> Unit) {
        if (heading != null) container.addView(text(heading, 13f, strong = true),
            LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(8); topMargin = dp(16); bottomMargin = dp(10)
            })
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = background(theme.card)
            clipToOutline = true
            items()
        }
        container.addView(group, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun hint(container: LinearLayout, value: String) {
        container.addView(text(value, 13f, muted = true),
            LinearLayout.LayoutParams(-1, -2).apply {
                marginStart = dp(8); marginEnd = dp(8); bottomMargin = dp(8)
            })
    }

    /** Opens a drop-down menu at [anchor] with [options]; [selected] is checked. */
    private fun dropdown(anchor: View, selected: Int, options: List<Pair<String, () -> Unit>>) {
        val menu = PopupMenu(this, anchor, Gravity.END)
        options.forEachIndexed { index, option -> menu.menu.add(0, index, index, option.first) }
        menu.menu.setGroupCheckable(0, true, true)
        menu.menu.findItem(selected)?.isChecked = true
        menu.setOnMenuItemClickListener { item ->
            options.getOrNull(item.itemId)?.second?.invoke()
            render()
            true
        }
        openDropdownForTest = menu
        menu.show()
    }

    /** Remains accessible from the launcher even when the IME process crashes. */
    private fun showSystemKeyboardPicker() {
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        } catch (error: RuntimeException) {
            showSystemKeyboardSettings()
        }
    }

    private fun showSystemKeyboardSettings() {
        startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS))
    }

    /** 0: Meltype is enabled and selected. 1: not enabled. 2: enabled but another keyboard is selected. */
    private fun setupStatus(): Int {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val id = ComponentName(this, KeyboardService::class.java).flattenToShortString()
        if (imm.enabledInputMethodList.none { it.id == id }) return 1
        val selected = SystemSettings.Secure.getString(
            contentResolver, SystemSettings.Secure.DEFAULT_INPUT_METHOD)
        return if (selected == id) 0 else 2
    }

    /** Draws the page again, keeping where it was scrolled to. On a wide screen the column stays readable. */
    private fun render() {
        val scrolled = scroller?.scrollY ?: 0
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(21), dp(10), dp(21), dp(40))
        }
        content.addView(text("Meltype の設定", 19f, strong = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(56)
        })
        sections(content)
        val column = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(
                minOf(dp(MAX_COLUMN_DP), resources.displayMetrics.widthPixels), -2,
                Gravity.CENTER_HORIZONTAL))
        }
        val page = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(theme.background)
            addView(column)
        }
        scroller = page
        setContentView(protectSystemBars(page))
        if (scrolled > 0) page.post { page.scrollTo(0, scrolled) }
    }

    private fun sections(content: LinearLayout) {
        // Shown only while Meltype is not enabled or not selected yet.
        when (setupStatus()) {
            1 -> section(content, null) {
                entry("Meltypeを有効にする", "Androidのキーボード設定を開く") {
                    startActivity(Intent(SystemSettings.ACTION_INPUT_METHOD_SETTINGS))
                }
            }
            2 -> section(content, null) {
                entry("Meltypeを選択する", "入力方法として有効にする") {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                }
            }
        }
        languages(content)
        general(content)
        about(content)
    }

    private fun languages(content: LinearLayout) {
        section(content, "言語") {
            val romaji = settings.japaneseLayout == LayoutId.ROMAJI
            entry("日本語", value = if (romaji) "QWERTY（ローマ字）" else "12キー（フリック）") { anchor ->
                dropdown(anchor, if (romaji) 0 else 1,
                    listOf("QWERTY（ローマ字）" to { settings.japaneseLayout = LayoutId.ROMAJI },
                        "12キー（フリック）" to { settings.japaneseLayout = LayoutId.KANA }))
            }
            divider()
            val qwerty = settings.englishLayout == LayoutId.QWERTY
            entry("English", value = if (qwerty) "QWERTY" else "12-key flick") { anchor ->
                dropdown(anchor, if (qwerty) 0 else 1,
                    listOf("QWERTY" to { settings.englishLayout = LayoutId.QWERTY },
                        "12-key flick" to { settings.englishLayout = LayoutId.LATIN_FLICK }))
            }
        }
        hint(content, "日本語QWERTYでは、入力内容から日本語と英語を自動判別する。")
    }

    private fun general(content: LinearLayout) {
        section(content, "設定") {
            entry("振動", "キーを押したときに振動する",
                checked = settings.hapticEnabled,
                onToggle = { settings.hapticEnabled = it })
            divider()
            entry("12キーのトグル入力", "連続タップで同じキーの文字を切り替える",
                checked = settings.toggleEnabled,
                onToggle = { settings.toggleEnabled = it; render() })
            if (settings.toggleEnabled) {
                divider()
                entry("トグル判定時間", value = waitText(settings.toggleWaitMs)) { anchor ->
                    dropdown(anchor, progressOf(settings.toggleWaitMs),
                        (0 until WAIT_STEPS).map { step ->
                            waitText(waitOfProgress(step)) to { settings.toggleWaitMs = waitOfProgress(step) }
                        })
                }
            }
            divider()
            entry("ライブ変換", "入力中に漢字・かなの変換候補を更新する",
                checked = settings.liveConversionEnabled,
                onToggle = { settings.liveConversionEnabled = it })
            divider()
            entry("絵文字", "絵文字キー・パネル・候補を表示する",
                checked = settings.emojiEnabled,
                onToggle = { settings.emojiEnabled = it })
            divider()
            val full = settings.japaneseSpaceFullWidth
            entry("日本語入力時のスペース", value = if (full) "全角" else "半角") { anchor ->
                dropdown(anchor, if (full) 1 else 0,
                    listOf("半角" to { settings.japaneseSpaceFullWidth = false },
                        "全角" to { settings.japaneseSpaceFullWidth = true }))
            }
        }
    }

    private fun about(content: LinearLayout) {
        section(content, "このアプリについて") {
            entry("ライセンス", "オープンソースに関する情報") {
                startActivity(Intent(this@MainActivity, LicensesActivity::class.java))
            }
            divider()
            // The way out when the keyboard does not work: never hidden behind the setup state.
            entry("別のキーボードに切り替える", "Meltypeが動かないときもAndroidの切替画面を開く") {
                showSystemKeyboardPicker()
            }
            divider()
            entry("Androidのキーボード設定を開く", "ほかのキーボードを選び直す") {
                showSystemKeyboardSettings()
            }
        }
    }

    companion object {
        /** The widest the settings column gets (unfolded and large screens). */
        private const val MAX_COLUMN_DP = 640
        private const val WAIT_STEPS = 18
        private const val WAIT_BASE_MS = 300
        private const val WAIT_STEP_MS = 100
        fun waitOfProgress(n: Int) =
            WAIT_BASE_MS + n.coerceIn(0, WAIT_STEPS - 1) * WAIT_STEP_MS
        fun progressOf(ms: Int) =
            ((ms - WAIT_BASE_MS) / WAIT_STEP_MS).coerceIn(0, WAIT_STEPS - 1)
        fun waitText(ms: Int) =
            String.format(Locale.US, "%.1f 秒", ms / 1000.0)
    }
}
