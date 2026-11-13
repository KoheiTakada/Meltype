// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout

/** Debug-only editor used to verify a selected IME can actually type. */
class ImeSmokeActivity : Activity() {
    lateinit var editor: EditText
        private set
    lateinit var japaneseEditor: EditText
        private set

    /**
     * A text field that restarts the input right after its first character, as Chrome does for many
     * web form fields (the page reacts to the first input event).
     */
    lateinit var restartingEditor: EditText
        private set

    /** How many times [restartingEditor] restarted the input. */
    @Volatile var restarts = 0
        private set

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        editor = EditText(this).apply {
            id = android.R.id.edit
            hint = "IME smoke input"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
            isFocusableInTouchMode = true
            textSize = 20f
        }
        japaneseEditor = EditText(this).apply {
            hint = "Japanese input smoke"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            isFocusableInTouchMode = true
            textSize = 20f
        }
        restartingEditor = EditText(this).apply {
            hint = "Restarts the input after the first character"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            isFocusableInTouchMode = true
            textSize = 20f
        }
        restartingEditor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (restarts > 0 || s.isNullOrEmpty()) return
                restarts++
                restartingEditor.post {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(restartingEditor)
                }
            }
        })
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 80, 24, 24)
            addView(editor, LinearLayout.LayoutParams(-1, -2))
            addView(japaneseEditor, LinearLayout.LayoutParams(-1, -2))
            addView(restartingEditor, LinearLayout.LayoutParams(-1, -2))
        })
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editor.requestFocus()
        editor.post {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
