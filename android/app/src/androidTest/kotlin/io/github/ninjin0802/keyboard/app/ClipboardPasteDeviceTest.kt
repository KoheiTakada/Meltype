// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.text.Selection
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninjin0802.keyboard.app.clipboard.ClipboardHistoryStore
import io.github.ninjin0802.keyboard.app.layout.KeyAction
import io.github.ninjin0802.keyboard.app.layout.LayoutId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pasting from the clipboard history with the installed keyboard, through real touches on the
 * Japanese QWERTY (romaji) keyboard, the layout that goes through the native Core.
 *
 * A paste is not the Core's own edit: what the Core is composing is committed as shown before
 * the paste, the pasted text lands at the caret, and typing goes on after it without the Core
 * rewriting the text before the paste.
 */
class ClipboardPasteDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    private fun waitUntil(
        label: String,
        millis: Long = 15_000,
        retryEveryMillis: Long = 0,
        retry: () -> Unit = {},
        describe: () -> String = { "" },
        check: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + millis
        var nextRetry = SystemClock.elapsedRealtime() + retryEveryMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (check()) return
            if (retryEveryMillis > 0 && SystemClock.elapsedRealtime() >= nextRetry) {
                retry()
                nextRetry = SystemClock.elapsedRealtime() + retryEveryMillis
            }
            Thread.sleep(200)
        }
        throw AssertionError("Clipboard paste timed out: $label ${describe()}".trim())
    }

    private fun requestKeyboard(activity: ImeSmokeActivity, editor: EditText) {
        instrumentation.runOnMainSync {
            if (!activity.hasWindowFocus()) return@runOnMainSync
            val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            editor.requestFocus()
            imm.restartInput(editor)
            imm.showSoftInput(editor, 0)
        }
    }

    private fun describeImeState(activity: ImeSmokeActivity): String {
        val service = KeyboardService.activeForSmokeTest?.get()
        val keys = service?.activeKeysForSmokeTest()
        return "(windowFocus=" + activity.hasWindowFocus() +
            ", japaneseFocus=" + activity.japaneseEditor.hasFocus() +
            ", defaultIme=" + Settings.Secure.getString(target.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) +
            ", serviceCreated=" + (service != null) +
            ", inputViewShown=" + service?.isInputViewShown +
            ", layout=" + keys?.layout?.id + ")"
    }

    /** Touches the screen at ([x], [y]) as a finger would and lets the editor and the keyboard settle. */
    private fun touch(label: String, x: Float, y: Float) {
        val clock = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(clock, clock, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(clock, clock + 55, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            assertTrue("Touch DOWN not delivered for $label", instrumentation.uiAutomation.injectInputEvent(down, true))
            assertTrue("Touch UP not delivered for $label", instrumentation.uiAutomation.injectInputEvent(up, true))
        } finally {
            down.recycle()
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        // The editor and the keyboard exchange the edit and the new selection asynchronously.
        Thread.sleep(150)
        instrumentation.waitForIdleSync()
    }

    /** Touches [view] at ([fx], [fy]) of its width and height. */
    private fun touchView(label: String, view: View, fx: Float = 0.5f, fy: Float = 0.5f) {
        val location = IntArray(2)
        var width = 0
        var height = 0
        instrumentation.runOnMainSync {
            view.getLocationOnScreen(location)
            width = view.width
            height = view.height
        }
        touch(label, location[0] + width * fx, location[1] + height * fy)
    }

    /** Types [keys] on the romaji keyboard: letters, ' ' for Space and '\n' for Enter. */
    private fun typeRomaji(keys: String) {
        for (char in keys) {
            val keyboard = KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()
                ?: throw AssertionError("Keys not shown")
            assertEquals(LayoutId.ROMAJI, keyboard.layout.id)
            val matches: (KeyAction) -> Boolean = when (char) {
                ' ' -> { action: KeyAction -> action == KeyAction.Space }
                '\n' -> { action: KeyAction -> action == KeyAction.Enter }
                else -> { action: KeyAction -> action is KeyAction.Romaji && action.char == char }
            }
            val key = keyboard.layout.keys.firstOrNull { matches(it.action) }
                ?: throw AssertionError("No on-screen key for '$char'")
            val location = IntArray(2)
            instrumentation.runOnMainSync { keyboard.getLocationOnScreen(location) }
            touch("'$char'",
                location[0] + keyboard.width * ((key.col + key.width / 2f) / keyboard.layout.columns),
                location[1] + keyboard.height * ((key.row + 0.5f) / keyboard.layout.rows))
        }
    }

    /** The views of the keyboard whose description contains [text]. */
    private fun keyboardViews(text: String): List<View> {
        val found = ArrayList<View>()
        instrumentation.runOnMainSync {
            KeyboardService.activeForSmokeTest?.get()?.inputRootForSmokeTest()
                ?.findViewsWithText(found, text, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        }
        return found.filter { it.isShown && it.width > 0 && it.height > 0 }
    }

    /** Opens the history with its button and pastes the only entry by touching its card. */
    private fun pasteFromHistory(onOpen: () -> Unit = {}) {
        val button = keyboardViews("クリップボード履歴").firstOrNull()
            ?: throw AssertionError("No clipboard history button")
        touchView("clipboard history button", button)
        waitUntil("the history shows the copied text") { keyboardViews(CARD).isNotEmpty() }
        onOpen()
        // The card's text, away from its action icons at the bottom right.
        touchView("the copied text", keyboardViews(CARD).first(), fx = 0.25f, fy = 0.2f)
        waitUntil("the keyboard comes back after pasting") {
            KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()?.layout?.id == LayoutId.ROMAJI
        }
    }

    private fun focusJapanese(activity: ImeSmokeActivity) {
        val editor = activity.japaneseEditor
        requestKeyboard(activity, editor)
        waitUntil(
            "Japanese QWERTY keyboard for the editor",
            millis = 45_000,
            retryEveryMillis = 2_000,
            retry = { requestKeyboard(activity, editor) },
            describe = { describeImeState(activity) },
        ) {
            editor.hasFocus() &&
                KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()?.layout?.id == LayoutId.ROMAJI
        }
    }

    /** Sets the editor's text and caret as the app would, then waits for the keyboard on it again. */
    private fun reset(activity: ImeSmokeActivity, text: String, caret: Int = text.length) {
        instrumentation.runOnMainSync {
            activity.japaneseEditor.setText(text)
            activity.japaneseEditor.setSelection(caret)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        // setText can restart the editor's input.
        focusJapanese(activity)
    }

    /** Runs [block] with one text in the history and the Japanese layout set to QWERTY romaji. */
    private fun withHistory(block: (ImeSmokeActivity) -> Unit) {
        val store = ClipboardHistoryStore(target)
        store.writableDatabase.delete("clips", null, null)
        File(target.noBackupFilesDir, "clipboard-media").listFiles()?.forEach { it.delete() }
        store.saveText(PASTED)
        val settings = io.github.ninjin0802.keyboard.app.Settings(target)
        val originalLayout = settings.japaneseLayout
        settings.japaneseLayout = LayoutId.ROMAJI
        val activity = instrumentation.startActivitySync(
            Intent(target, ImeSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ImeSmokeActivity
        try {
            focusJapanese(activity)
            block(activity)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            settings.japaneseLayout = originalLayout
            store.writableDatabase.delete("clips", null, null)
            store.close()
        }
    }

    private fun composingStart(editor: EditText) = BaseInputConnection.getComposingSpanStart(editor.text)

    private fun caret(editor: EditText) = Selection.getSelectionStart(editor.text)

    @Test fun pastingWhileComposingCommitsTheCompositionFirst() = withHistory { activity ->
        val editor = activity.japaneseEditor
        reset(activity, "")
        typeRomaji("ka")
        waitUntil("か is composing", describe = { "text=" + editor.text }) {
            editor.text.toString() == "か" && composingStart(editor) >= 0
        }
        pasteFromHistory(onOpen = {
            assertEquals("Opening the history keeps the composing text as shown", "か", editor.text.toString())
            assertEquals("Opening the history commits the composing text", -1, composingStart(editor))
        })
        waitUntil("the copied text is pasted after か", describe = { "text=" + editor.text }) {
            editor.text.toString() == "か$PASTED"
        }
        assertEquals(-1, composingStart(editor))
        assertEquals("The caret is after the pasted text", editor.text.length, caret(editor))
        typeRomaji("a\n")
        waitUntil("typing goes on after the pasted text", describe = { "text=" + editor.text }) {
            editor.text.toString() == "か${PASTED}あ"
        }
    }

    @Test fun pastingWhileConvertingKeepsTheShownConversion() = withHistory { activity ->
        val editor = activity.japaneseEditor
        reset(activity, "")
        typeRomaji("kyou ")
        waitUntil("きょう is being converted", describe = { "text=" + editor.text }) {
            editor.text.isNotEmpty() && composingStart(editor) >= 0
        }
        val shown = editor.text.toString()
        pasteFromHistory()
        waitUntil("the copied text is pasted after the conversion", describe = { "text=" + editor.text }) {
            editor.text.toString() == shown + PASTED
        }
        assertEquals(-1, composingStart(editor))
        assertEquals(editor.text.length, caret(editor))
    }

    @Test fun pastingInTheMiddleLeavesTheCaretAfterThePastedText() = withHistory { activity ->
        val editor = activity.japaneseEditor
        reset(activity, "あい", caret = 1)
        pasteFromHistory()
        waitUntil("the copied text is pasted at the caret", describe = { "text=" + editor.text }) {
            editor.text.toString() == "あ${PASTED}い"
        }
        assertEquals(1 + PASTED.length, caret(editor))
        typeRomaji("u\n")
        waitUntil("typing goes on at the caret", describe = { "text=" + editor.text }) {
            editor.text.toString() == "あ${PASTED}うい"
        }
    }

    /**
     * Without a paste, i + want re-commits i as I (ImeLaunchDeviceTest). The paste comes between:
     * the Core must not rewrite the text before it.
     */
    @Test fun typingAfterAPasteDoesNotRewriteTheTextBeforeIt() = withHistory { activity ->
        val editor = activity.japaneseEditor
        reset(activity, "")
        typeRomaji("i \n")
        waitUntil("i is converted and committed", describe = { "text=" + editor.text }) {
            editor.text.isNotEmpty() && composingStart(editor) < 0
        }
        val committed = editor.text.toString()
        pasteFromHistory()
        waitUntil("the copied text is pasted", describe = { "text=" + editor.text }) {
            editor.text.toString() == committed + PASTED
        }
        typeRomaji("want ")
        waitUntil("want is typed after the pasted text", describe = { "text=" + editor.text }) {
            editor.text.endsWith("want ")
        }
        assertEquals(committed + PASTED + "want ", editor.text.toString())
    }

    private companion object {
        const val PASTED = "ネコ"

        /** The description of the history card that pastes [PASTED]. */
        const val CARD = PASTED + "。タップで貼り付け"
    }
}
