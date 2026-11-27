// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.keyboard.core

/** A tap sends CandidateClicked with the item's list and index in the Core response. */
internal data class CoreChoice(
    val label: String,
    /** Index in the Core's list. */
    val nativeIndex: Int,
    val fromTyping: Boolean = false,
)

/** Maps the Core's view to the Android candidate bar while preserving native indices. */
internal object CoreCandidateChoices {
    /** Keep prediction candidates at the start of the bar and append the first clause's candidates. */
    fun fromView(view: ImeView?): Pair<List<CoreChoice>, Int?> {
        if (view == null || view.text.isEmpty()) return emptyList<CoreChoice>() to null
        val result = ArrayList<CoreChoice>()
        val list = view.candidates
        list?.items?.forEachIndexed { index, label ->
            if (label.isNotBlank()) result.add(CoreChoice(label, index))
        }
        val selected = list?.let { l -> result.indexOfFirst { it.nativeIndex == l.selected }.takeIf { it >= 0 } }
        view.typingCandidates.forEachIndexed { index, label ->
            if (label.isNotBlank()) result.add(CoreChoice(label, index, fromTyping = true))
        }
        return result to selected
    }
}
