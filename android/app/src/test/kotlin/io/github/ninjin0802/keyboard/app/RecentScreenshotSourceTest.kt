// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import io.github.ninjin0802.keyboard.app.clipboard.RecentScreenshotSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentScreenshotSourceTest {
    @Test fun onlyScreenCapturePathsOrNamesMatch() {
        assertTrue(RecentScreenshotSource.looksLikeScreenshot(
            "Screenshot_2026-11-12.png", "Pictures/"))
        assertTrue(RecentScreenshotSource.looksLikeScreenshot(
            "abc.png", "Pictures/Screenshots/"))
        assertTrue(RecentScreenshotSource.looksLikeScreenshot(
            "スクリーンショット.png", "DCIM/"))
        assertFalse(RecentScreenshotSource.looksLikeScreenshot(
            "IMG_1234.jpg", "DCIM/Camera/"))
        assertFalse(RecentScreenshotSource.looksLikeScreenshot(
            "IMG_0012.png", "Pictures/"))
    }
}
