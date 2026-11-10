// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ClipboardHistoryDeviceTest {
    private lateinit var context: Context
    private lateinit var store: ClipboardHistoryStore

    @Before fun reset() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        store = ClipboardHistoryStore(context)
        store.writableDatabase.delete("clips", null, null)
        File(context.noBackupFilesDir, "clipboard-media").listFiles()?.forEach { it.delete() }
    }

    @Test fun debugAppUsesIsolatedClipboardProvider() {
        assertEquals("app.meltype.keyboard.debug", context.packageName)
        val authority = ClipboardContentProvider.uri(context, 7L).authority
        assertEquals(context.packageName + ".clipboard", authority)
        assertEquals(context.packageName,
            context.packageManager.resolveContentProvider(authority!!, 0)?.packageName)
    }

    @Test fun textHistoryIsRetainedEditedAndDeduplicated() {
        assertTrue(store.capture(ClipData.newPlainText("sample", "こんにちは")))
        val id = store.list().single().id
        assertTrue(store.capture(ClipData.newPlainText("sample", "こんにちは")))
        assertEquals(id, store.list().single().id)
        assertTrue(store.edit(id, "修正済み"))
        assertEquals("修正済み", store.get(id)?.text)
        store.delete(id)
        assertTrue(store.list().isEmpty())
    }

    @Test fun expiredEntriesAndOwnedImagesAreDeleted() {
        val file = File(context.cacheDir, "clipboard_test.png")
        file.outputStream().use { out ->
            Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val clip = ClipData("image", arrayOf("image/png"), ClipData.Item(android.net.Uri.fromFile(file)))
        assertTrue(store.capture(clip))
        val item = store.list().single()
        assertEquals("image", item.kind)
        val stored = store.mediaFile(item)
        assertNotNull(stored)
        assertTrue(stored!!.exists())
        store.writableDatabase.execSQL("UPDATE clips SET created_at = ? WHERE id = ?",
            arrayOf(System.currentTimeMillis() - ClipboardHistoryStore.RETENTION_MS - 100L, item.id))
        store.prune()
        assertTrue(store.list().isEmpty())
        assertFalse(stored.exists())
        file.delete()
    }

    @Test fun configurableRetentionDeletesOnlyExpiredEntries() {
        val previous = ClipboardRetention.days(context)
        try {
            ClipboardRetention.setDays(context, 7)
            val old = store.saveText("古いコピー")!!
            val recent = store.saveText("新しいコピー")!!
            store.writableDatabase.execSQL(
                "UPDATE clips SET created_at = ? WHERE id = ?",
                arrayOf(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000, old)
            )
            store.prune()
            assertNull(store.get(old))
            assertNotNull(store.get(recent))
            ClipboardRetention.setDays(context, 90)
            assertEquals(90, ClipboardRetention.days(context))
        } finally {
            ClipboardRetention.setDays(context, previous)
        }
    }

    @Test fun linkCleaningIsExplicitAndOgTagsAreParsed() {
        val original = "https://example.com/articles/path?utm_source=copy&x=2#summary"
        assertEquals("https://example.com/articles/path",
            ClipboardLinkPreviewLoader.stripParameters(original))
        assertEquals("https://example.com/articles/path",
            ClipboardLinkPreviewLoader.stripParameters(
                ClipboardLinkPreviewLoader.stripParameters(original)))
        val meta = ClipboardLinkPreviewLoader.parseHtml(
            "<html><head><meta content=\"Example headline\" property=\"og:title\">" +
            "<meta property=\"og:image\" content=\"/thumb.jpg\"></head></html>"
        )
        assertEquals("Example headline", meta.title)
        assertEquals("/thumb.jpg", meta.imageUrl)
    }

    @Test fun sensitiveContentIsNotRecorded() {
        val clip = ClipData.newPlainText("secret", "123456")
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        assertFalse(store.capture(clip))
        assertTrue(store.list().isEmpty())
    }
}
