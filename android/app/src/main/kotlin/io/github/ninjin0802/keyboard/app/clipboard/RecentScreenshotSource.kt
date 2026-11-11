// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.clipboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Opt-in, locally queried screenshots. No photo scan before explicit user consent. */
class RecentScreenshotSource(private val context: Context) {
    data class Screenshot(val id: Long, val uri: Uri)
    private val prefs = context.getSharedPreferences("screenshot_suggestions", Context.MODE_PRIVATE)

    fun enabled(): Boolean = prefs.getBoolean("enabled", false) && hasFullImageAccess()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
    }

    fun hasFullImageAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /** The Android 14 screenshot callback is per Activity and does not return an image.
     * MediaStore is the only supported way to access recent screenshot pixels, with consent.
     */
    fun newestRecent(maxAgeMs: Long = 120_000L): Screenshot? {
        if (!enabled()) return null
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val now = System.currentTimeMillis()
        val after = (now - maxAgeMs) / 1000L
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                columns,
                MediaStore.Images.Media.DATE_ADDED + " >= ?",
                arrayOf(after.toString()),
                MediaStore.Images.Media.DATE_ADDED + " DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                var examined = 0
                while (cursor.moveToNext() && examined++ < 60) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val folder = cursor.getString(pathIndex).orEmpty()
                    val added = cursor.getLong(dateIndex) * 1000L
                    if (added in (now - maxAgeMs)..now && looksLikeScreenshot(name, folder)) {
                        val id = cursor.getLong(idIndex)
                        return@use Screenshot(id, android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
                    }
                }
                null
            }
        } catch (_: SecurityException) { null }
          catch (_: RuntimeException) { null }
    }

    companion object {
        fun looksLikeScreenshot(name: String, relativePath: String): Boolean {
            val folder = relativePath.lowercase(java.util.Locale.ROOT)
            val file = name.lowercase(java.util.Locale.ROOT)
            return folder.contains("screenshots") || folder.contains("screenshot") ||
                folder.contains("スクリーンショット") || file.startsWith("screenshot") ||
                file.startsWith("スクリーンショット")
        }
    }
}
