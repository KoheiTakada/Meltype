// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/** Read-only bridge used only when a recipient editor accepts rich IME content. */
class ClipboardContentProvider : ContentProvider() {
    private fun entry(uri: Uri): HistoryClip {
        val id = uri.pathSegments.singleOrNull()?.toLongOrNull()
            ?: throw FileNotFoundException("Invalid clipboard item")
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")
        return ClipboardHistoryStore(ctx).get(id)
            ?.takeIf { it.isMedia } ?: throw FileNotFoundException("Expired clipboard item")
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = entry(uri).mime

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")
        val file = ClipboardHistoryStore(ctx).mediaFile(entry(uri))
            ?: throw FileNotFoundException("Missing clipboard media")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val clip = entry(uri)
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val result = MatrixCursor(columns)
        val file = ClipboardHistoryStore(ctx).mediaFile(clip)
        result.addRow(columns.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> clip.fileName ?: "clipboard"
                OpenableColumns.SIZE -> file?.length() ?: 0L
                else -> null
            }
        })
        return result
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read only")

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read only")

    companion object {
        fun uri(context: Context, id: Long): Uri =
            Uri.Builder().scheme("content").authority(context.packageName + ".clipboard")
                .appendPath(id.toString()).build()
    }
}
