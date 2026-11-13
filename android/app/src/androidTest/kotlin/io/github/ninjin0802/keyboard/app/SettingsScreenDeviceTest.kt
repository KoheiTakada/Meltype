// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
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
        if (view is TextView && view !is Switch) return listOf(view.text.toString())
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
    }

    private fun allSwitches(view: View): List<Switch> {
        if (view is Switch) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { allSwitches(view.getChildAt(it)) }
    }

    /** The row of a title: the title sits inside a vertical labels column. */
    private fun rowOf(activity: MainActivity, title: String): View {
        val label = findText(activity.window.decorView, title)
        assertNotNull("No row $title", label)
        return (label!!.parent as View).parent as View
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
                "絵文字", "日本語入力時のスペース", "このアプリについて", "ライセンス",
                "別のキーボードに切り替える", "Androidのキーボード設定を開く")
            val positions = order.map { title -> shown.indexOf(title).also { assertTrue("No $title in $shown", it >= 0) } }
            assertEquals("Sections and rows are out of order: $shown", positions.sorted(), positions)
            // The old category pages and their entries are gone; there is no demo editor.
            for (gone in listOf("修正と候補", "入力レイアウト", "緊急時の切り替え", "試し書き", "済み")) {
                assertFalse("$gone is still shown", gone in shown)
            }
            // A pending setup step, when there is one, comes before everything else.
            val setup = shown.indexOfFirst { it == "Meltypeを有効にする" || it == "Meltypeを選択する" }
            if (setup >= 0) assertTrue("Setup is not at the top: $shown", setup < shown.indexOf("言語"))
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
                val systemSettings = findText(root, "Androidのキーボード設定を開く")
                assertNotNull("System IME picker must be reachable without launching the IME",
                    picker)
                assertNotNull("Android keyboard settings must remain reachable independently",
                    systemSettings)
                val pickerRow = (picker!!.parent as View).parent as View
                val settingsRow = (systemSettings!!.parent as View).parent as View
                assertTrue(pickerRow.hasOnClickListeners())
                assertTrue(settingsRow.hasOnClickListeners())
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
                assertEquals(previous, toggle!!.isChecked)
                toggle.isChecked = !previous
                assertEquals(!previous, Settings(activity).liveConversionEnabled)
                // Kept where the Core reads it (config.json), not in the keyboard's own preferences.
                assertEquals(!previous, CoreConfig(coreDataDirectory(activity)).boolean(CoreConfig.LIVE_CONVERSION))
                toggle.isChecked = previous
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
                if (toggle.isChecked) toggle.isChecked = false
            }
            instrumentation.waitForIdleSync()
            assertFalse("トグル判定時間 is shown while toggle input is off", "トグル判定時間" in page(activity))
            instrumentation.runOnMainSync {
                allSwitches(activity.window.decorView).single { it.contentDescription == "12キーのトグル入力" }.isChecked = true
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
                val items = (0 until menu!!.menu.size()).map { menu.menu.getItem(it).title.toString() }
                assertEquals(listOf("QWERTY（ローマ字）", "12キー（フリック）"), items)
                assertTrue(menu.menu.getItem(if (previous == LayoutId.ROMAJI) 0 else 1).isChecked)
                // Still the same page: the choice did not open another one.
                assertNotNull(findText(activity.window.decorView, "Meltype の設定"))
                val other = if (previous == LayoutId.ROMAJI) 1 else 0
                assertTrue(menu.menu.performIdentifierAction(other, 0))
                menu.dismiss()
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
                    assertEquals(listOf("半角", "全角"), (0 until menu.menu.size()).map { menu.menu.getItem(it).title.toString() })
                    assertTrue(menu.menu.performIdentifierAction(index, 0))
                    menu.dismiss()
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
                    it.isChecked = !it.isChecked
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertProtected() }
        } finally {
            settings.toggleEnabled = previous
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
