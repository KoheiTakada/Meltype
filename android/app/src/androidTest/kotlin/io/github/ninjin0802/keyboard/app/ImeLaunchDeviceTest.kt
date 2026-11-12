// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.InputDevice
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ninjin0802.keyboard.app.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end IME gate, not just an isolated KeyboardView drawing test.
 *
 * The emulator runner first enables and SELECTS the exact test package's IME.
 * This test then focuses a real EditText, waits until the system has created
 * the real InputMethodService window, and injects actual screen touches into
 * three rendered keys. Success requires the EditText to contain "asd".
 */
class ImeLaunchDeviceTest {
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
        throw AssertionError("IME smoke timed out: $label ${describe()}".trim())
    }

    /**
     * Asks Android to show the keyboard for [editor].
     *
     * A single request is not enough. Android drops it when the window has
     * not gained focus yet, and starting instrumentation restarts the app
     * process that hosts the IME, so the first request can arrive while the
     * system is still reconnecting to the keyboard service. restartInput
     * makes the system bind the service again instead of waiting for it.
     */
    private fun requestKeyboard(activity: ImeSmokeActivity, editor: android.widget.EditText) {
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
        val defaultIme = Settings.Secure.getString(
            target.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val size = if (keys == null) "none" else keys.width.toString() + "x" + keys.height
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        return "(windowFocus=" + activity.hasWindowFocus() +
            ", editorFocus=" + activity.editor.hasFocus() +
            ", editorImeActive=" + imm.isActive(activity.editor) +
            ", defaultIme=" + defaultIme +
            ", serviceCreated=" + (service != null) +
            ", inputViewShown=" + service?.isInputViewShown +
            ", keys=" + size + ")"
    }

    /** A visible view alone can belong to the previous editor during a window transition. */
    private fun isImeReadyFor(
        activity: ImeSmokeActivity,
        editor: android.widget.EditText,
        expectedLayout: io.github.ninjin0802.keyboard.app.layout.LayoutId,
    ): Boolean {
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as InputMethodManager
        val service = KeyboardService.activeForSmokeTest?.get()
        val keys = service?.activeKeysForSmokeTest()
        return activity.hasWindowFocus() && editor.hasFocus() &&
            imm.isActive(editor) && service?.isInputViewShown == true &&
            keys?.layout?.id == expectedLayout &&
            keys.isAttachedToWindow && keys.isShown && keys.width > 100 && keys.height > 100
    }

    /** Touches the on-screen key that [matches], as a finger would. */
    private fun tap(keyboard: io.github.ninjin0802.keyboard.app.view.KeyboardView, label: String, matches: (KeyAction) -> Boolean) {
        val key = keyboard.layout.keys.firstOrNull { matches(it.action) } ?: throw AssertionError("No on-screen key for $label")
        var center = 0f to 0f
        instrumentation.runOnMainSync { center = keyboard.keyCenterOnScreenForSmokeTest(key) }
        val (x, y) = center
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

    /** Types [keys] on the Japanese QWERTY (romaji) keyboard: letters, ' ' for Space and '\n' for Enter. */
    private fun typeRomaji(keys: String) {
        val keyboard = KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()
            ?: throw AssertionError("Keys not shown")
        assertEquals(io.github.ninjin0802.keyboard.app.layout.LayoutId.ROMAJI, keyboard.layout.id)
        for (key in keys) when (key) {
            ' ' -> tap(keyboard, "Space") { it == KeyAction.Space }
            '\n' -> tap(keyboard, "Enter") { it == KeyAction.Enter }
            else -> tap(keyboard, key.toString()) { it is KeyAction.Romaji && it.char == key }
        }
    }

    private fun outsideCaretMoves(): Int {
        var count = -1
        instrumentation.runOnMainSync {
            count = KeyboardService.activeForSmokeTest?.get()?.outsideCaretMovesForSmokeTest() ?: -1
        }
        return count
    }

    /** Focuses [editor] and waits until the Japanese QWERTY keyboard is shown for it. */
    private fun focusJapanese(activity: ImeSmokeActivity, editor: android.widget.EditText) {
        requestKeyboard(activity, editor)
        waitUntil(
            "Japanese QWERTY keyboard for the editor",
            millis = 45_000,
            retryEveryMillis = 2_000,
            retry = { requestKeyboard(activity, editor) },
            describe = { describeImeState(activity) },
        ) {
            isImeReadyFor(activity, editor,
                io.github.ninjin0802.keyboard.app.layout.LayoutId.ROMAJI)
        }
    }

    /** Runs [block] with the Japanese layout set to QWERTY romaji, the layout that goes through the native Core. */
    private fun withRomajiActivity(block: (ImeSmokeActivity) -> Unit) {
        val settings = io.github.ninjin0802.keyboard.app.Settings(target)
        val originalLayout = settings.japaneseLayout
        settings.japaneseLayout = io.github.ninjin0802.keyboard.app.layout.LayoutId.ROMAJI
        val activity = instrumentation.startActivitySync(
            Intent(target, ImeSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ImeSmokeActivity
        try {
            block(activity)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            settings.japaneseLayout = originalLayout
        }
    }

    /** Empties [editor] (the app restarts the input) and waits until the keyboard has settled. */
    private fun clear(editor: android.widget.EditText) {
        instrumentation.runOnMainSync { editor.setText("") }
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        instrumentation.waitForIdleSync()
    }

    /** Types i, Space (convert) and Enter (commit): the Core may re-commit that word once the next one shows the language. */
    private fun commitI(editor: android.widget.EditText): String {
        typeRomaji("i \n")
        waitUntil("i is converted and committed", describe = { "text=" + editor.text }) {
            editor.text.isNotEmpty() && android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0
        }
        return editor.text.toString()
    }

    /**
     * The keyboard's own edits are not caret moves: i + want re-commits i as I. A caret move by
     * the app is one: the Core is told and leaves the word before the caret alone.
     */
    @Test fun ownEditorWritesDoNotLookLikeCaretMoves() = withRomajiActivity { activity ->
        val editor = activity.japaneseEditor
        focusJapanese(activity, editor)
        clear(editor)
        // setText can restart an editor. Check that the actual keyboard still has Japanese focus.
        focusJapanese(activity, editor)
        val start = outsideCaretMoves()
        val committed = commitI(editor)
        typeRomaji("want ")
        waitUntil("want is committed", describe = { "text=" + editor.text }) { editor.text.endsWith("want ") }
        assertTrue("The previous word was not re-committed: " + editor.text,
            editor.text.toString() != committed + "want " && editor.text.startsWith("I"))
        assertEquals("Own edits were taken as caret moves", start, outsideCaretMoves())
    }

    @Test fun caretMovesByTheAppStopRecommit() = withRomajiActivity { activity ->
        val editor = activity.japaneseEditor
        focusJapanese(activity, editor)
        clear(editor)
        focusJapanese(activity, editor)
        val committed = commitI(editor)
        val beforeMoves = outsideCaretMoves()
        // The app moves the caret away and back: the Core must not rewrite the word it can see again.
        instrumentation.runOnMainSync { editor.setSelection(0) }
        waitUntil("the move to the start is seen", describe = { "moves=" + outsideCaretMoves() }) {
            outsideCaretMoves() > beforeMoves
        }
        instrumentation.runOnMainSync { editor.setSelection(editor.text.length) }
        waitUntil("the move back to the end is seen", describe = { "moves=" + outsideCaretMoves() }) {
            outsideCaretMoves() > beforeMoves + 1
        }
        typeRomaji("want ")
        waitUntil("want is typed after the word", describe = { "text=" + editor.text }) {
            editor.text.toString() == committed + "want "
        }
    }

    /** Leaving an editor keeps the composing text there; the next editor starts without it. */
    @Test fun leavingAnEditorCommitsTheCompositionThere() = withRomajiActivity { activity ->
        val japanese = activity.japaneseEditor
        focusJapanese(activity, japanese)
        instrumentation.runOnMainSync { japanese.setText("") }
        // setText can restart editor input asynchronously; wait for Japanese focus again.
        focusJapanese(activity, japanese)
        typeRomaji("ka")
        waitUntil("か is composing", describe = { "text=" + japanese.text }) {
            japanese.text.toString() == "か" && android.view.inputmethod.BaseInputConnection.getComposingSpanStart(japanese.text) >= 0
        }
        requestKeyboard(activity, activity.editor)
        waitUntil("the other editor has the keyboard", describe = { describeImeState(activity) }) {
            activity.editor.hasFocus() &&
                KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()?.layout?.id ==
                io.github.ninjin0802.keyboard.app.layout.LayoutId.QWERTY
        }
        assertEquals("か", japanese.text.toString())
        assertEquals("The composing text was not committed", -1,
            android.view.inputmethod.BaseInputConnection.getComposingSpanStart(japanese.text))

        focusJapanese(activity, japanese)
        instrumentation.runOnMainSync { japanese.setSelection(japanese.text.length) }
        instrumentation.waitForIdleSync()
        typeRomaji("a\n")
        waitUntil("あ is typed after か", describe = { "text=" + japanese.text }) {
            japanese.text.toString() == "かあ"
        }
    }

    private fun coreView(): io.github.ninjin0802.keyboard.app.core.ImeView? {
        var view: io.github.ninjin0802.keyboard.app.core.ImeView? = null
        instrumentation.runOnMainSync { view = KeyboardService.activeForSmokeTest?.get()?.coreViewForSmokeTest() }
        return view
    }

    /** Taps a candidate on the candidate bar other than [current], as a finger would, and returns it. */
    private fun tapCandidateOtherThan(current: String): String {
        var tapped: String? = null
        instrumentation.runOnMainSync {
            val bar = KeyboardService.activeForSmokeTest?.get()?.candidateBarForSmokeTest() ?: return@runOnMainSync
            val row = bar.getChildAt(0) as android.view.ViewGroup
            val item = (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
                .firstOrNull { it.text.isNotBlank() && it.text.toString() != current } ?: return@runOnMainSync
            tapped = item.text.toString()
            item.performClick()
        }
        instrumentation.waitForIdleSync()
        return tapped ?: throw AssertionError("No candidate to tap other than $current; view=" + coreView())
    }

    /**
     * With the real converter: after Space, a candidate tap chooses it for the selected clause and
     * moves on to the next clause; the tap on the last clause commits the whole sentence.
     */
    @Test fun candidateTapsFixAConversionClauseByClause() = withRomajiActivity { activity ->
        val editor = activity.japaneseEditor
        focusJapanese(activity, editor)
        clear(editor)
        focusJapanese(activity, editor)
        typeRomaji("watashinonamaehanakanodesu ")
        waitUntil("the sentence is converted", describe = { "view=" + coreView() }) { coreView()?.converting == true }
        val clauses = coreView()!!.clauses.size
        assertTrue("The sentence was not split into clauses: " + coreView(), clauses >= 2)
        var expected = ""
        for (clause in 0 until clauses) {
            val view = coreView() ?: throw AssertionError("Committed before clause $clause: " + editor.text)
            assertEquals("Not moved on to clause $clause: $view", clause, view.selectedClause)
            assertTrue("Not composing at clause $clause: " + editor.text,
                android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) >= 0)
            val label = tapCandidateOtherThan(view.clauses[clause])
            expected = view.clauses.take(clause).joinToString("") + label + view.clauses.drop(clause + 1).joinToString("")
        }
        waitUntil("the sentence is committed as chosen", describe = { "text=" + editor.text + " expected=" + expected }) {
            editor.text.toString() == expected &&
                android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text) < 0
        }
    }

    private fun shownKeys(): io.github.ninjin0802.keyboard.app.view.KeyboardView =
        KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest() ?: throw AssertionError("Keys not shown")

    /** Opens the first symbol page with ?123, types [symbol] there and goes back to the letters. */
    private fun typeFromSymbols(symbol: String) {
        val letters = shownKeys().layout.id
        tap(shownKeys(), "?123") { it == KeyAction.Switch(io.github.ninjin0802.keyboard.app.layout.LayoutId.SYMBOLS) }
        waitUntil("the symbol page is shown") {
            shownKeys().layout.id == io.github.ninjin0802.keyboard.app.layout.LayoutId.SYMBOLS
        }
        tap(shownKeys(), symbol) { it == KeyAction.Text(symbol) }
        tap(shownKeys(), "back to the letters") { it == KeyAction.Back }
        waitUntil("the letters are shown again") { shownKeys().layout.id == letters }
    }

    private fun composingStart(editor: android.widget.EditText) =
        android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.text)

    /**
     * Japanese QWERTY sends the symbols of the symbol page to the Core as typed characters, and the
     * Core decides their width, as on the Mac: ! and ? after Japanese are full-width.
     */
    @Test fun symbolsAfterJapaneseAreTheCoresFullWidth() = withRomajiActivity { activity ->
        val editor = activity.japaneseEditor
        focusJapanese(activity, editor)
        clear(editor)
        focusJapanese(activity, editor)
        typeRomaji("sorena")
        typeFromSymbols("!")
        typeFromSymbols("?")
        assertTrue("The symbols are part of what is being typed: " + editor.text, composingStart(editor) >= 0)
        typeRomaji("\n")
        waitUntil("the sentence is committed", describe = { "text=" + editor.text }) {
            editor.text.isNotEmpty() && composingStart(editor) < 0
        }
        val text = editor.text.toString()
        assertTrue("! and ? after Japanese are full-width: $text", text.endsWith("！？"))
        assertTrue("No half-width ! or ?: $text", '!' !in text && '?' !in text)
    }

    /** The reverse: where the Core is not used (an email field, English QWERTY), ? and ! stay as typed. */
    @Test fun symbolsInAnEmailFieldStayHalfWidth() = withRomajiActivity { activity ->
        val editor = activity.editor
        fun focusEnglish() {
            requestKeyboard(activity, editor)
            waitUntil("English QWERTY keyboard for the email field", millis = 45_000, retryEveryMillis = 2_000,
                retry = { requestKeyboard(activity, editor) }, describe = { describeImeState(activity) }) {
                editor.hasFocus() && KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()?.layout?.id ==
                    io.github.ninjin0802.keyboard.app.layout.LayoutId.QWERTY
            }
        }
        focusEnglish()
        clear(editor)
        focusEnglish()
        typeFromSymbols("?")
        typeFromSymbols("!")
        waitUntil("? and ! are typed as they are", describe = { "text=" + editor.text }) {
            editor.text.toString() == "?!"
        }
    }

    /**
     * Space in Japanese QWERTY goes to the Core also when nothing is being typed, so the Core's
     * SpaceWidth decides it: half-width by default, full-width when chosen in the settings.
     */
    @Test fun spaceIsTheCoresSpaceWidth() = withRomajiActivity { activity ->
        val config = io.github.ninjin0802.keyboard.app.core.CoreConfig(
            io.github.ninjin0802.keyboard.app.core.coreDataDirectory(target))
        val previous = config.string(io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_WIDTH)
        fun spaceWidth(value: String) {
            assertTrue(config.set(io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_WIDTH, value))
            // The Core reads config.json again within a second (SettingsFile).
            Thread.sleep(1_300)
        }
        try {
            spaceWidth(io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_STANDARD)
            val editor = activity.japaneseEditor
            focusJapanese(activity, editor)
            clear(editor)
            focusJapanese(activity, editor)
            typeRomaji("ka\n")
            waitUntil("か is committed", describe = { "text=" + editor.text }) {
                editor.text.toString() == "か" && composingStart(editor) < 0
            }
            typeRomaji(" ")
            waitUntil("a half-width space by default", describe = { "text=" + editor.text }) {
                editor.text.toString() == "か "
            }
            spaceWidth(io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_FULL_WIDTH)
            typeRomaji(" ")
            waitUntil("a full-width space when chosen", describe = { "text=" + editor.text }) {
                editor.text.toString() == "か 　"
            }
        } finally {
            config.set(io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_WIDTH,
                previous ?: io.github.ninjin0802.keyboard.app.core.CoreConfig.SPACE_STANDARD)
        }
    }

    @Test fun selectedKeyboardOpensAndTypesThroughOnscreenTouch() {
        val expectedIme = target.packageName + "/" + KeyboardService::class.java.name
        assertEquals("The actual keyboard must be the system-selected default",
            expectedIme, Settings.Secure.getString(target.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD))
        // The Japanese check below must take the QWERTY romaji path, which is the
        // only one that goes through the native Core.
        val settings = io.github.ninjin0802.keyboard.app.Settings(target)
        val originalLayout = settings.japaneseLayout
        settings.japaneseLayout = io.github.ninjin0802.keyboard.app.layout.LayoutId.ROMAJI
        val activity = instrumentation.startActivitySync(
            Intent(target, ImeSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as ImeSmokeActivity
        try {
            // The old test accepted a visible keyboard left over from the
            // previous activity. Wait until Android binds this specific EditText.
            requestKeyboard(activity, activity.editor)
            waitUntil(
                "the email editor owns the visible English IME",
                millis = 45_000,
                retryEveryMillis = 2_000,
                retry = { requestKeyboard(activity, activity.editor) },
                describe = { describeImeState(activity) },
            ) {
                isImeReadyFor(activity, activity.editor,
                    io.github.ninjin0802.keyboard.app.layout.LayoutId.QWERTY)
            }

            for ((index, letter) in listOf('a', 's', 'd').withIndex()) {
                // Check ownership before every touch; the IME may detach while
                // Android switches the focused window between test activities.
                waitUntil(
                    "email editor IME ready before key $letter",
                    retryEveryMillis = 2_000,
                    retry = { requestKeyboard(activity, activity.editor) },
                    describe = { describeImeState(activity) },
                ) {
                    isImeReadyFor(activity, activity.editor,
                        io.github.ninjin0802.keyboard.app.layout.LayoutId.QWERTY)
                }
                val keyboard = KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()
                    ?: throw AssertionError("Keyboard detached before key $letter")
                tap(keyboard, letter.toString()) {
                    (it as? KeyAction.Text)?.text == letter.toString()
                }
                val expected = "asd".substring(0, index + 1)
                waitUntil(
                    "key $letter must enter exactly $expected",
                    describe = { "text=" + activity.editor.text + " " + describeImeState(activity) },
                ) {
                    activity.editor.text.toString() == expected
                }
            }
            waitUntil("the three visible keys must generate text in the editor") {
                activity.editor.text.toString() == "asd"
            }
            assertEquals("asd", activity.editor.text.toString())

            // Also exercise the normal Japanese-input route. A working email
            // keyboard alone could conceal a failing Japanese/native path.
            focusJapanese(activity, activity.japaneseEditor)
            val japanese = KeyboardService.activeForSmokeTest?.get()?.activeKeysForSmokeTest()
                ?: throw AssertionError("Japanese keys not shown")
            tap(japanese, "Japanese A") {
                when (it) {
                    is KeyAction.Romaji -> it.char == 'a'
                    is KeyAction.Flick -> it.key.chars.firstOrNull() == 'あ'
                    else -> false
                }
            }
            waitUntil("Japanese key must write composing text into the editor") {
                activity.japaneseEditor.text.isNotEmpty()
            }
            // The Kotlin romaji path would also write text. Require that the key
            // really went through the native Core and that it did not fail.
            var native = false
            instrumentation.runOnMainSync {
                native = KeyboardService.activeForSmokeTest?.get()?.nativeCoreActiveForSmokeTest() == true
            }
            assertTrue("Japanese romaji did not go through the native Core", native)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            settings.japaneseLayout = originalLayout
        }
    }
}
