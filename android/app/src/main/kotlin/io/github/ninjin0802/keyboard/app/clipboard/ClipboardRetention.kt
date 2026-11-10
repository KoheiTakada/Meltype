// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context

/** Private per-install retention settings. The shipped default is 30 days. */
object ClipboardRetention {
    const val DEFAULT_DAYS = 30
    val OPTIONS = listOf(1, 7, 14, 30, 90, 180, 365)

    private const val PREFS = "clipboard_settings"
    private const val KEY_DAYS = "retention_days"

    fun days(context: Context): Int {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAYS, DEFAULT_DAYS)
        return value.takeIf { it in OPTIONS } ?: DEFAULT_DAYS
    }

    fun millis(context: Context): Long = days(context) * 24L * 60L * 60L * 1000L

    fun setDays(context: Context, days: Int) {
        require(days in OPTIONS) { "Unsupported clipboard retention duration" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DAYS, days).apply()
    }
}
