// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.KeyEvent
import io.github.ninjin0802.keyboard.app.view.CandidateBarView
import io.github.ninjin0802.keyboard.app.view.BarSuggestion
import io.github.ninjin0802.keyboard.app.view.SuggestionKind
import io.github.ninjin0802.keyboard.app.view.Palette
import io.github.ninjin0802.keyboard.app.core.CoreConfig
import io.github.ninjin0802.keyboard.app.core.coreDataDirectory
import io.github.ninjin0802.keyboard.app.layout.LayoutId
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** On-device checks for the settings Activity, not only for persisted values. */
class SettingsScreenDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun activityDensity() = instrumentation.targetContext.resources.displayMetrics.density

    private fun open(): MainActivity {
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent) as MainActivity
    }

    private fun findText(view: View, expected: String): TextView? {
        if (view is TextView && view.text.toString() == expected) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findText(view.getChildAt(index), expected)?.let { return it }
            }
        }
        return null
    }

    /** Every text on the page, from the top down (the page is one column of rows). */
    private fun texts(view: View): List<String> {
        if (view is TextView) return listOf(view.text.toString())
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
    }

    private fun allSwitches(view: View): List<AppToggle> {
        if (view is AppToggle) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { allSwitches(view.getChildAt(it)) }
    }

    /** The row of a title: the title sits inside a vertical labels column. */
    private fun rowOf(activity: MainActivity, title: String): View {
        val label = findText(activity.window.decorView, title)
        assertNotNull("No row $title", label)
        return (label!!.parent as View).parent as View
    }

    private fun findDescribed(view: View, description: String): View? {
        if (view.contentDescription?.toString() == description) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findDescribed(view.getChildAt(index), description)?.let { return it }
            }
        }
        return null
    }

    private fun page(activity: MainActivity): List<String> {
        var shown = emptyList<String>()
        instrumentation.runOnMainSync { shown = texts(activity.window.decorView) }
        return shown
    }

    @Test fun onePageHasTheSectionsAndRowsInOrder() {
        val activity = open()
        try {
            val shown = page(activity)
            val order = listOf("言語", "日本語", "English", "設定", "振動", "12キーのトグル入力", "ライブ変換",
                "絵文字", "日本語入力時のスペース", "クリップボード", "履歴の保存期間", "辞書", "ユーザー辞書",
                "個人情報", "メール・電話・住所", "このアプリについて", "ライセンス",
                "別のキーボードに切り替える", "Androidのキーボード設定を開く")
            // The setup row's detail can repeat a title further up ("Androidのキーボード設定を開く"): take the last.
            val positions = order.map { title -> shown.lastIndexOf(title).also { assertTrue("No $title in $shown", it >= 0) } }
            assertEquals("Sections and rows are out of order: $shown", positions.sorted(), positions)
            // The old category pages and their entries are gone; there is no demo editor.
            for (gone in listOf("修正と候補", "入力レイアウト", "緊急時の切り替え", "試し書き", "済み", "選択中")) {
                assertFalse("$gone is still shown", gone in shown)
            }
            // A pending setup step, when there is one, comes before everything else.
            val setup = shown.indexOfFirst { it == "Meltypeを有効にする" || it == "Meltypeを選択する" }
            if (setup >= 0) assertTrue("Setup is not at the top: $shown", setup < shown.indexOf("言語"))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun languageSettingsOfferOnlyJapaneseAndEnglishQwerty() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val label = findText(activity.window.decorView, "言語")
                    ?: throw AssertionError("Languages category missing")
                ((label.parent as View).parent as View).performClick()
                val root = activity.window.decorView
                val japanese = findText(root, "QWERTY（ローマ字）")
                val english = findText(root, "QWERTY")
                assertNotNull("Japanese QWERTY must be shown", japanese)
                assertNotNull("English QWERTY must be shown", english)
                assertTrue("Flick layouts must not be offered in #1149",
                    findText(root, "12キー（フリック）") == null &&
                    findText(root, "12-key flick") == null)
                assertFalse("Japanese layout is fixed, not selectable",
                    ((japanese!!.parent as View).parent as View).hasOnClickListeners())
                assertFalse("English layout is fixed, not selectable",
                    ((english!!.parent as View).parent as View).hasOnClickListeners())
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun launcherAlwaysOffersEscapeWhenTheInputMethodCannotStart() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                val picker = findText(root, "別のキーボードに切り替える")
                assertNotNull("System IME picker remains a last resort if the IME process cannot start",
                    picker)
                val pickerRow = (picker!!.parent as View).parent as View
                assertTrue(pickerRow.hasOnClickListeners())
                // Emergency switching must not require opening a Settings screen.
                // Onboarding can still show Android setup before Meltype is enabled.
                if (findText(root, "Meltypeを有効にする") == null) {
                    assertTrue(findText(root, "Androidのキーボード設定を開く") == null)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun liveConversionSwitchChangesAndPersistsItsValue() {
        val settings = Settings(instrumentation.targetContext)
        val previous = settings.liveConversionEnabled
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val toggle = allSwitches(activity.window.decorView).singleOrNull { it.contentDescription == "ライブ変換" }
                assertNotNull("ライブ変換 is a switch on the page", toggle)
                assertEquals(previous, toggle!!.isChecked())
                toggle.setChecked(!previous)
                assertEquals(!previous, Settings(activity).liveConversionEnabled)
                // Kept where the Core reads it (config.json), not in the keyboard's own preferences.
                assertEquals(!previous, CoreConfig(coreDataDirectory(activity)).boolean(CoreConfig.LIVE_CONVERSION))
                toggle.setChecked(previous)
                assertEquals(previous, settings.liveConversionEnabled)
            }
        } finally {
            settings.liveConversionEnabled = previous
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun settingsAreSwitchesAndToggleWaitFollowsTheToggle() {
        val settings = Settings(instrumentation.targetContext)
        val previous = settings.toggleEnabled
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val titles = allSwitches(activity.window.decorView).map { it.contentDescription.toString() }
                assertEquals(listOf("振動", "12キーのトグル入力", "ライブ変換", "絵文字"), titles)
                val toggle = allSwitches(activity.window.decorView).single { it.contentDescription == "12キーのトグル入力" }
                if (toggle.isChecked()) toggle.setChecked(false)
            }
            instrumentation.waitForIdleSync()
            assertFalse("トグル判定時間 is shown while toggle input is off", "トグル判定時間" in page(activity))
            instrumentation.runOnMainSync {
                allSwitches(activity.window.decorView).single { it.contentDescription == "12キーのトグル入力" }.setChecked(true)
            }
            instrumentation.waitForIdleSync()
            val shown = page(activity)
            val wait = shown.indexOf("トグル判定時間")
            assertTrue("トグル判定時間 is not shown while toggle input is on: $shown", wait >= 0)
            // Right below the toggle (its title and description come first), before the next setting.
            assertTrue(wait > shown.indexOf("12キーのトグル入力") && wait < shown.indexOf("ライブ変換"))
            assertTrue(settings.toggleEnabled)
        } finally {
            settings.toggleEnabled = previous
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun choicesOpenAsDropDownsOnTheSamePage() {
        val settings = Settings(instrumentation.targetContext)
        val previous = settings.japaneseLayout
        val activity = open()
        try {
            instrumentation.runOnMainSync { rowOf(activity, "日本語").performClick() }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val menu = activity.openDropdownForTest
                assertNotNull("The row opens a drop-down menu", menu)
                assertEquals(listOf("QWERTY（ローマ字）", "12キー（フリック）"), menu!!.labels)
                assertTrue("The list is open", menu.isShowing)
                assertEquals(if (previous == LayoutId.ROMAJI) 0 else 1, menu.selected)
                // Still the same page: the choice did not open another one.
                assertNotNull(findText(activity.window.decorView, "Meltype の設定"))
                val other = if (previous == LayoutId.ROMAJI) 1 else 0
                menu.pick(other)
            }
            instrumentation.waitForIdleSync()
            val chosen = if (previous == LayoutId.ROMAJI) LayoutId.KANA else LayoutId.ROMAJI
            assertEquals(chosen, Settings(activity).japaneseLayout)
            assertTrue("The row shows the new choice",
                (if (chosen == LayoutId.ROMAJI) "QWERTY（ローマ字）" else "12キー（フリック）") in page(activity))
        } finally {
            settings.japaneseLayout = previous
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun japaneseSpaceIsTheCoreSpaceWidth() {
        val settings = Settings(instrumentation.targetContext)
        val config = CoreConfig(coreDataDirectory(instrumentation.targetContext))
        val previous = config.string(CoreConfig.SPACE_WIDTH)
        val activity = open()
        try {
            fun choose(index: Int) {
                instrumentation.runOnMainSync { rowOf(activity, "日本語入力時のスペース").performClick() }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val menu = activity.openDropdownForTest!!
                    assertEquals(listOf("半角", "全角"), menu.labels)
                    menu.pick(index)
                }
                instrumentation.waitForIdleSync()
            }
            choose(1)
            assertEquals("全角 is the Core's SpaceWidth FullWidth", CoreConfig.SPACE_FULL_WIDTH, config.string(CoreConfig.SPACE_WIDTH))
            assertTrue(settings.japaneseSpaceFullWidth)
            choose(0)
            assertEquals("半角 is the Core's default", CoreConfig.SPACE_STANDARD, config.string(CoreConfig.SPACE_WIDTH))
            assertFalse(settings.japaneseSpaceFullWidth)
        } finally {
            config.set(CoreConfig.SPACE_WIDTH, previous ?: CoreConfig.SPACE_STANDARD)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun systemBarsAreRespectedWhenThePageIsDrawnAgain() {
        val settings = Settings(instrumentation.targetContext)
        val previous = settings.toggleEnabled
        val activity = open()
        try {
            // Explicit synthetic insets make this regression reproducible on
            // pre-edge-to-edge API29 as well as Android 15+ devices.
            val insets = WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(0, 68, 0, 28))
                .build()
            fun assertProtected() {
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                if (Build.VERSION.SDK_INT >= 35) {
                    // Check the real Android 15+ inset as well: the screenshot
                    // regression occurred because the title was under the clock.
                    val statusTop = root.rootWindowInsets?.getInsets(
                        WindowInsets.Type.statusBars())?.top ?: 0
                    assertTrue("Emulator must expose a visible status bar", statusTop > 0)
                    assertTrue("Settings must start below the actual OS clock",
                        root.paddingTop >= statusTop)
                }
                root.dispatchApplyWindowInsets(insets)
                assertTrue("Settings must clear the status bar", root.paddingTop >= 68)
                assertTrue("Settings must clear the navigation bar", root.paddingBottom >= 28)
            }
            instrumentation.runOnMainSync {
                assertProtected()
                // Switching toggle input draws the page again.
                allSwitches(activity.window.decorView).single { it.contentDescription == "12キーのトグル入力" }.let {
                    it.setChecked(!it.isChecked())
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertProtected() }
        } finally {
            settings.toggleEnabled = previous
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun dictionaryAndPersonalInformationAreSeparateNonModalEditors() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val personal = findText(activity.window.decorView, "メール・電話・住所")
                assertNotNull("Personal information has its own settings category", personal)
                val row = (personal!!.parent as View).parent as View
                row.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertNotNull(findText(root, "メールアドレス"))
                assertNotNull(findText(root, "電話番号"))
                assertNotNull(findText(root, "住所"))
                val addEmail = findText(root, "メールアドレスを登録")
                assertNotNull(addEmail)
                ((addEmail!!.parent as View).parent as View).performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertNotNull("The editor must be in the content pane",
                    findText(root, "登録内容"))
                assertNotNull(findText(root, "保存"))
                assertNotNull(findText(root, "キャンセル"))
                // No Android AlertDialog / modal is used for personal information.
                val pane = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                assertTrue("Editor must remain in the activity root", pane.isAttachedToWindow)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun emailAndClipboardHaveDistinctGboardLikeCandidateSurfaces() {
        instrumentation.runOnMainSync {
            val bar = CandidateBarView(instrumentation.targetContext)
            bar.show(emptyList(), null, BarSuggestion("sample.user@example.com", SuggestionKind.EMAIL))
            val row = bar.getChildAt(0) as LinearLayout
            val email = row.getChildAt(0) as TextView
            assertEquals("sample.user\n@example.com", email.text.toString())
            val text = email.text as Spanned
            val spans = text.getSpans(0, text.length, RelativeSizeSpan::class.java)
            assertTrue("The domain must use a smaller font", spans.any { it.sizeChange < 1f })
            bar.show(emptyList(), null, BarSuggestion("Copied text", SuggestionKind.CLIPBOARD_TEXT))
            val pill = row.getChildAt(0)
            assertTrue("Clipboard should be a container, not a plain candidate cell",
                pill is LinearLayout)
            assertTrue("Clipboard chip is an explicitly rounded custom surface",
                pill.background is GradientDrawable)
            assertEquals("The paste chip must look like an existing white key",
                Palette.of(instrumentation.targetContext).key,
                (pill.background as GradientDrawable).color?.defaultColor)
            assertEquals("Clipboard chip text must use the key foreground",
                Palette.of(instrumentation.targetContext).text,
                (pill as LinearLayout).getChildAt(1).let { (it as TextView).currentTextColor })
            assertEquals("クリップボードを貼り付ける: Copied text",
                pill.contentDescription.toString())

            // The screenshot thumbnail must sit within a pill as concentric
            // rounded corners: 34dp/R17 outside; 26dp/R13 inside; 4dp inset.
            val thumb = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                bar.show(emptyList(), null, BarSuggestion("Screenshot",
                    SuggestionKind.CLIPBOARD_IMAGE, thumb))
                val imagePill = row.getChildAt(0) as LinearLayout
                assertNotNull(findText(imagePill, "Screenshot"))
                val image = imagePill.getChildAt(0) as ImageView
                val density = activityDensity()
                fun dp(n: Int) = (n * density + 0.5f).toInt()
                assertEquals(dp(4), imagePill.paddingLeft)
                assertEquals(dp(34), imagePill.layoutParams.height)
                assertEquals(dp(26), image.layoutParams.width)
                assertEquals(dp(26), image.layoutParams.height)
                assertEquals(dp(17).toFloat(),
                    (imagePill.background as GradientDrawable).cornerRadius, 0.1f)
                assertEquals(dp(13).toFloat(),
                    (image.background as GradientDrawable).cornerRadius, 0.1f)
                assertEquals(Palette.of(instrumentation.targetContext).key,
                    (imagePill.background as GradientDrawable).color?.defaultColor)
            } finally {
                thumb.recycle()
            }
            bar.show(emptyList(), null, BarSuggestion("09012345678", SuggestionKind.PHONE))
            assertEquals("09012345678", (row.getChildAt(0) as TextView).text.toString())
        }
    }

    @Test fun backFromTheDictionaryReturnsToTheSettingsPage() {
        val activity = open()
        try {
            fun openDictionary() {
                instrumentation.runOnMainSync { rowOf(activity, "ユーザー辞書").performClick() }
                instrumentation.waitForIdleSync()
                assertTrue("The dictionary page is shown", "登録した単語" in page(activity))
            }
            openDictionary()
            instrumentation.runOnMainSync {
                val back = findDescribed(activity.window.decorView, "戻る")
                assertNotNull("Visible back control must exist on the dictionary page", back)
                back!!.performClick()
            }
            instrumentation.waitForIdleSync()
            assertTrue("Toolbar Back must return to the settings page", "このアプリについて" in page(activity))
            openDictionary()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            assertTrue("Android system Back must return to the settings page", "このアプリについて" in page(activity))
            assertFalse("Back from the dictionary should not close the Activity", activity.isFinishing)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun clipboardRetentionIsADropDownOnTheSettingsPage() {
        val activity = open()
        try {
            instrumentation.runOnMainSync { rowOf(activity, "履歴の保存期間").performClick() }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val menu = activity.openDropdownForTest
                assertNotNull("The retention row opens a drop-down menu", menu)
                assertEquals(io.github.ninjin0802.keyboard.app.clipboard.ClipboardRetention.OPTIONS.map { "${it}日" }, menu!!.labels)
                assertEquals(io.github.ninjin0802.keyboard.app.clipboard.ClipboardRetention.OPTIONS
                    .indexOf(io.github.ninjin0802.keyboard.app.clipboard.ClipboardRetention.days(activity)), menu.selected)
                menu.dismiss()
                assertNotNull("Still the settings page", findText(activity.window.decorView, "Meltype の設定"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    /** The screenshot row asks for photo access; it is shown until screenshots are allowed and on. */
    @Test fun screenshotRowIsShownUntilScreenshotsAreAllowed() {
        val activity = open()
        try {
            val allowed = io.github.ninjin0802.keyboard.app.clipboard.RecentScreenshotSource(activity).enabled()
            assertEquals("最近のスクリーンショット" in page(activity), !allowed)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun wideScreensKeepOneReadableColumn() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val displayed = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                assertTrue("The settings are one scrolling page", displayed is ScrollView)
                val column = ((displayed as ScrollView).getChildAt(0) as ViewGroup).getChildAt(0)
                val density = activity.resources.displayMetrics.density
                assertTrue("The column is at most 640dp wide: " + column.width / density,
                    column.width <= (640 * density + 1).toInt())
                assertNotNull(findText(displayed, "言語"))
                assertNotNull(findText(displayed, "日本語入力時のスペース"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
