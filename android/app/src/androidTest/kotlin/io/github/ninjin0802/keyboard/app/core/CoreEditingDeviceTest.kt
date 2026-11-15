// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Android front must get right around the native Core's edits: re-commits
 * (removeBefore), moving the caret back into a commit, a caret moved elsewhere, and leaving an
 * editor while composing. The events are the ones KeyboardService sends (ImeJson).
 */
class CoreEditingDeviceTest {
    private val core = DeviceTestCore.open()

    @After fun closeCore() = core.close()

    /** Types into [editor] through the Core, sending each key as KeyboardService does. */
    private inner class Typist(var editor: TestEditor = TestEditor()) {
        val adapter = CoreInputAdapter { editor }

        fun field() = adapter.field(emptyList(), app = "io.github.ninjin0802.test")

        fun send(event: String): Boolean = adapter.apply(core.handle(event) ?: error("The Core returned nothing for $event"))

        /** '\n' is Enter. A key the Core passes is typed by the "app". */
        fun type(keys: String) {
            for (key in keys) {
                if (key == '\n') enter()
                else if (!send(ImeJson.characterKey(key.code, false, field()))) editor.commitText(key.toString(), 1)
            }
        }

        fun enter() {
            send(ImeJson.virtualKey(0x0D, field()))
        }

        /** Taps the candidate [label] on the candidate bar, as KeyboardService does. */
        fun tap(label: String) {
            val choice = adapter.choices.firstOrNull { it.label == label }
                ?: throw AssertionError("No candidate $label in " + adapter.choices.map { it.label })
            CandidateTap.tap(adapter, choice.nativeIndex, { field() }) { send(it) }
        }

        /** Choose a real alternate candidate of the Core's currently selected clause. */
        fun otherCandidate(): String {
            val view = adapter.view ?: throw AssertionError("Not composing: " + editor.content)
            return adapter.choices.firstOrNull { it.label != view.clauses[view.selectedClause] }?.label
                ?: throw AssertionError("No alternate candidate: ${adapter.choices} in $view")
        }

        /** Uses a sentence converted by Mozc into three clauses, then selects the first. */
        fun convertThreeClauses() {
            type("watashinonamaehanakanodesu ")
            val initial = adapter.view
            assertTrue("Not a conversion: $initial", initial?.converting == true)
            assertEquals("Not converted in three clauses: $initial", 3, initial!!.clauses.size)
            // Core starts on the final clause. Left moves through real clause selection;
            // Android does not get to override which clause the Core chooses.
            repeat(initial.selectedClause) {
                assertTrue("Left must be consumed during conversion",
                    send(ImeJson.virtualKey(0x25, field())))
            }
            assertEquals("First clause was not selected: ${adapter.view}",
                0, adapter.view!!.selectedClause)
        }
    }

    @Test fun aRecommitReplacesThePreviousWordInTheEditor() {
        // i converted with Space and committed by the next key turns into I once want shows the
        // sentence is English (as on the desktop: CompositionTests.AutoCorrect_JapaneseToEnglishAfterCommit).
        val typist = Typist()
        typist.type("i want ")
        assertEquals("I want ", typist.editor.content)
        assertEquals(7, typist.editor.caret)
        assertEquals(-1, typist.editor.composingStart)
    }

    @Test fun aCommittedWordCanBeRecommittedWhenCaretHasNotMoved() {
        // Keep this in its own Core host. ImeHost's "android" instance is stateful across editors.
        val stays = Typist()
        stays.type("i \n")
        stays.type("want ")
        assertTrue("Not re-committed: " + stays.editor.content, stays.editor.content.startsWith("I"))
    }

    @Test fun aCaretMovedElsewhereKeepsThePreviousWord() {
        // A distinct host reproduces a fresh editor: the first scenario must not leak into this one.
        val moved = Typist()
        moved.type("i \n")
        val committed = moved.editor.content
        moved.send(ImeJson.caretMoved(moved.field()))
        moved.type("want ")
        assertEquals(committed + "want ", moved.editor.content)
    }

    @Test fun removeBeforeTakesAwayTheComposingTextFirst() {
        val editor = TestEditor()
        editor.commitText("い ", 1)
        val adapter = CoreInputAdapter { editor }
        adapter.apply(response("[]", """{"text":"want","converting":false,"clauses":[],"selectedClause":0,"hint":"","spellingSuggestion":null,"typedKeys":null,"candidates":null}"""))
        assertEquals("い want", editor.content)
        adapter.apply(response("""[{"text":"I want ","removeBefore":"い ","currentWord":null,"moveCaretBack":0,"insertByPaste":false}]""", "null"))
        assertEquals("I want ", editor.content)
        assertEquals(7, editor.caret)
        assertEquals(-1, editor.composingStart)
        assertTrue(!adapter.composing)
    }

    @Test fun moveCaretBackPutsTheCaretInsideTheCommit() {
        val editor = TestEditor()
        editor.commitText("abc", 1)
        val adapter = CoreInputAdapter { editor }
        adapter.apply(response("[]", """{"text":"[]","converting":false,"clauses":[],"selectedClause":0,"hint":"","spellingSuggestion":null,"typedKeys":null,"candidates":null}"""))
        adapter.apply(response("""[{"text":"「」","removeBefore":null,"currentWord":null,"moveCaretBack":1,"insertByPaste":false}]""", "null"))
        assertEquals("abc「」", editor.content)
        assertEquals(4, editor.caret)
    }

    @Test fun leavingAnEditorWhileComposingKeepsTheTextThereAndStartsAfresh() {
        val first = TestEditor()
        val typist = Typist(first)
        typist.type("ka")
        assertTrue(typist.adapter.composing)
        // The editor keeps the composing text when it loses focus (KeyboardService.leaveCore).
        first.finishComposingText()
        typist.adapter.clear()
        typist.send(ImeJson.committedByApp(null, focusLeft = true))
        assertEquals("か", first.content)

        val second = TestEditor()
        typist.editor = second
        typist.send(ImeJson.focusEntered(typist.field()))
        typist.type("ki")
        typist.enter()
        assertEquals("か", first.content)
        assertEquals("き", second.content)
    }

    @Test fun anEditorWithoutItsOwnTextSendsNoTextAndCannotRemove() {
        val editor = TestEditor()
        editor.commitText("abc", 1)
        val facts = CoreInputAdapter { editor }.field(emptyList(), readable = false)
        assertNull(facts.before)
        assertNull(facts.after)
        assertTrue(!facts.canRemove)
    }

    @Test fun theSelectedTextIsSentOnlyWhenTheEditorReportedASelection() {
        val editor = TestEditor()
        editor.commitText("abcd", 1)
        editor.setSelection(1, 3)
        val adapter = CoreInputAdapter { editor }
        assertEquals("bc", adapter.field(emptyList(), selected = true).selection)
        assertEquals("a", adapter.field(emptyList(), selected = true).before)
        assertNull(adapter.field(emptyList(), selected = false).selection)
    }

    @Test fun aTapInTheFirstClauseChoosesItAndShowsTheNextClausesCandidates() {
        val typist = Typist()
        typist.convertThreeClauses()
        val first = typist.otherCandidate()
        typist.tap(first)
        val view = typist.adapter.view
        assertTrue("The tap committed the whole text: " + typist.editor.content, typist.adapter.composing)
        assertEquals(0, typist.editor.composingStart)
        assertEquals(first, view!!.clauses[0])
        assertEquals("Not moved on to the second clause", 1, view.selectedClause)
        val labels = typist.adapter.choices.map { it.label }
        assertTrue("Second clause has no candidate list: $view", labels.isNotEmpty())
        assertTrue("Second clause must offer alternatives: $labels",
            labels.any { it != view.clauses[1] })
    }

    @Test fun aTapInALaterClauseChangesOnlyThatClauseAndTheLastTapCommitsAll() {
        val typist = Typist()
        typist.convertThreeClauses()
        val first = typist.otherCandidate()
        typist.tap(first)
        val second = typist.otherCandidate()
        typist.tap(second)
        val view = typist.adapter.view!!
        assertEquals(listOf(first, second), view.clauses.take(2))
        assertEquals(2, view.selectedClause)
        assertTrue(typist.adapter.composing)

        val last = typist.otherCandidate()
        typist.tap(last)
        assertEquals(first + second + last, typist.editor.content)
        assertEquals(-1, typist.editor.composingStart)
        assertTrue(!typist.adapter.composing)
    }

    @Test fun enterInTheMiddleCommitsTheWholeTextAsShown() {
        val typist = Typist()
        typist.convertThreeClauses()
        val first = typist.otherCandidate()
        typist.tap(first)
        val shown = typist.adapter.view!!.text
        typist.enter()
        assertEquals(shown, typist.editor.content)
        assertTrue(typist.editor.content.startsWith(first))
        assertEquals(-1, typist.editor.composingStart)
    }

    @Test fun aTapInAConversionOfOneClauseCommits() {
        val typist = Typist()
        typist.type("kyouha ")
        val view = typist.adapter.view
        assertTrue("Not a conversion: $view", view?.converting == true)
        assertEquals(1, view!!.clauses.size)
        typist.tap("今日は")
        assertEquals("今日は", typist.editor.content)
        assertEquals(-1, typist.editor.composingStart)
    }

    @Test fun aTapOnAPredictionCommits() {
        val typist = Typist()
        typist.type("hell")
        assertTrue("Not a prediction: " + typist.adapter.view, typist.adapter.view?.converting == false)
        val prediction = typist.adapter.choices.firstOrNull { it.label.startsWith("hell") }
            ?: throw AssertionError("No prediction for hell: " + typist.adapter.choices)
        typist.tap(prediction.label)
        assertEquals(prediction.label, typist.editor.content)
        assertTrue(!typist.adapter.composing)
    }

    /** A whole response as the Core writes it (ImeOutput). */
    private fun response(commits: String, view: String) =
        """{"version":1,"state":{},"result":{"consumed":true,"commits":$commits,"view":$view}}"""
}
