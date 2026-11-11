// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Licenses use the same chrome and system-bar handling as all other settings. */
class LicensesActivity : Activity() {
    private lateinit var licenseText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = AppChrome.colors(this)
        fun dp(n: Int) = AppChrome.dp(this, n)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(7), dp(20), dp(10))
            addView(AppChrome.action(this@LicensesActivity, "‹", filled = false) {
                finish()
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(AppChrome.text(this@LicensesActivity, "ライセンス", 20f, strong = true))
        }
        root.addView(header)
        licenseText = TextView(this).apply {
            text = getString(R.string.licenses_loading)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(palette.text)
            setTextIsSelectable(true)
            setPadding(dp(17), dp(18), dp(17), dp(18))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            background = AppChrome.shape(this@LicensesActivity, palette.card, 18)
            addView(licenseText)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            marginStart = dp(18); marginEnd = dp(18); bottomMargin = dp(15)
        })
        AppChrome.protectBars(this, root)
        setContentView(root)
        val assets = applicationContext.assets
        Thread {
            val all = FILES.joinToString("\n\n") { name ->
                "==== " + name + " ====\n" + try {
                    assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (_: java.io.IOException) { "(読み込めませんでした)" }
            }
            runOnUiThread { if (!isDestroyed) licenseText.text = all }
        }.start()
    }
    companion object {
        private val FILES = listOf("THIRD-PARTY-NOTICES.md", "LICENSE", "MOZC_NOTICES.txt")
    }
}
