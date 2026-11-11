// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.app.Activity
import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import io.github.ninjin0802.keyboard.app.AppToggle
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import io.github.ninjin0802.keyboard.app.view.CandidateBarView
import io.github.ninjin0802.keyboard.app.view.BarSuggestion
import io.github.ninjin0802.keyboard.app.view.SuggestionKind
import io.github.ninjin0802.keyboard.app.core.CoreConfig
import io.github.ninjin0802.keyboard.app.core.coreDataDirectory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** On-device checks for the settings Activity, not only for persisted values. */
class SettingsScreenDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun open(): Activity {
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent)
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

    private fun allSwitches(view: View): List<AppToggle> {
        if (view is AppToggle) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { allSwitches(view.getChildAt(it)) }
    }

    @Test fun homeHasGboardStyleCategoriesWithoutDemoOrCompletedSteps() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertNotNull(findText(root, "言語"))
                assertNotNull(findText(root, "設定"))
                assertNotNull(findText(root, "修正と候補"))
                assertNotNull(findText(root, "絵文字"))
                assertFalse(findText(root, "試し書き") != null)
                assertFalse(findText(root, "済み") != null)
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
                val button = findText(activity.window.decorView, "修正と候補")
                assertNotNull(button)
                // The title sits inside a vertical labels column.
                val row = (button!!.parent as View).parent as View
                assertTrue(row.hasOnClickListeners())
                row.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull(findText(activity.window.decorView, "ライブ変換"))
                val toggles = allSwitches(activity.window.decorView)
                assertEquals(1, toggles.size)
                assertEquals(previous, toggles[0].isChecked())
                toggles[0].setChecked(!previous)
                assertEquals(!previous, Settings(activity).liveConversionEnabled)
                // Kept where the Core reads it (config.json), not in the keyboard's own preferences.
                assertEquals(!previous, CoreConfig(coreDataDirectory(activity)).boolean(CoreConfig.LIVE_CONVERSION))
                toggles[0].setChecked(previous)
                assertEquals(previous, settings.liveConversionEnabled)
            }
        } finally {
            settings.liveConversionEnabled = previous
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun systemBarsAreRespectedOnHomeAndDetailScreens() {
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
                val languages = findText(activity.window.decorView, "言語")
                assertNotNull(languages)
                ((languages!!.parent as View).parent as View).performClick()
                assertProtected()
                assertNotNull(findText(activity.window.decorView, "入力レイアウト"))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun toolbarBackAndSystemBackBothReturnToSettingsHome() {
        val activity = open()
        try {
            fun openLanguages() {
                instrumentation.runOnMainSync {
                    val languages = findText(activity.window.decorView, "言語")
                    assertNotNull(languages)
                    ((languages!!.parent as View).parent as View).performClick()
                }
                instrumentation.waitForIdleSync()
            }
            openLanguages()
            instrumentation.runOnMainSync {
                assertNotNull(findText(activity.window.decorView, "入力レイアウト"))
                val back = findText(activity.window.decorView, "‹")
                assertNotNull("Visible back control must exist on detail screen", back)
                back!!.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull("Toolbar Back must navigate home",
                    findText(activity.window.decorView, "Meltype の設定"))
            }
            openLanguages()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertNotNull("Android system Back must navigate home",
                    findText(activity.window.decorView, "Meltype の設定"))
                assertFalse("Back from a detail page should not close the Activity",
                    activity.isFinishing)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun dictionaryAndPersonalInformationAreSeparateNonModalEditors() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val personal = findText(activity.window.decorView, "個人情報")
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
                val addEmail = findText(root, "＋ メールアドレスを登録")
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
            bar.show(emptyList(), null, BarSuggestion("kohei.t0312@gmail.com", SuggestionKind.EMAIL))
            val row = bar.getChildAt(0) as LinearLayout
            val email = row.getChildAt(0) as TextView
            assertEquals("kohei.t0312\n@gmail.com", email.text.toString())
            val text = email.text as Spanned
            val spans = text.getSpans(0, text.length, RelativeSizeSpan::class.java)
            assertTrue("The domain must use a smaller font", spans.any { it.sizeChange < 1f })
            bar.show(emptyList(), null, BarSuggestion("Copied text", SuggestionKind.CLIPBOARD_TEXT))
            val pill = row.getChildAt(0)
            assertTrue("Clipboard should be a container, not a plain candidate cell",
                pill is LinearLayout)
            assertTrue("Clipboard chip is an explicitly rounded custom surface",
                pill.background is GradientDrawable)
            assertEquals("クリップボードを貼り付ける: Copied text",
                pill.contentDescription.toString())
            bar.show(emptyList(), null, BarSuggestion("Screenshot", SuggestionKind.CLIPBOARD_IMAGE))
            val imagePill = row.getChildAt(0) as LinearLayout
            assertNotNull(findText(imagePill, "Screenshot"))
            bar.show(emptyList(), null, BarSuggestion("09012345678", SuggestionKind.PHONE))
            assertEquals("09012345678", (row.getChildAt(0) as TextView).text.toString())
        }
    }

    @Test fun unfoldedScreenKeepsCategoriesBesideActiveSettings() {
        val activity = open()
        try {
            instrumentation.runOnMainSync {
                val width = activity.resources.configuration.screenWidthDp
                val container = activity.findViewById<ViewGroup>(android.R.id.content)
                val displayed = container.getChildAt(0)
                if (width >= 700) {
                    val split = displayed as? android.widget.LinearLayout
                    assertNotNull("Expected an unfolded two-pane layout", split)
                    assertEquals(android.widget.LinearLayout.HORIZONTAL, split!!.orientation)
                    assertEquals(3, split.childCount) // Left pane, divider, right pane
                    assertNotNull(findText(split, "言語"))
                    assertNotNull(findText(split, "修正と候補"))
                    assertNotNull(findText(split, "日本語入力時のスペース"))
                } else {
                    assertTrue(displayed is android.widget.ScrollView)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
