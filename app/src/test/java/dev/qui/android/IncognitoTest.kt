/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android

import dev.qui.android.ui.torrents.incognitoCategory
import dev.qui.android.ui.torrents.incognitoName
import dev.qui.android.ui.torrents.incognitoTags
import dev.qui.android.ui.torrents.trackerHost
import dev.qui.android.ui.torrents.trackerShortName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncognitoTest {

    private val hash = "8c4adbf9ebe66f1d804fb6a4fb9b74966c3ab609"

    @Test
    fun `same hash always maps to the same alias`() {
        assertEquals(incognitoName(hash), incognitoName(hash))
        assertEquals(incognitoCategory(hash), incognitoCategory(hash))
        assertEquals(incognitoTags(hash), incognitoTags(hash))
    }

    @Test
    fun `alias comes from the linux distro vocabulary`() {
        assertTrue(incognitoName(hash).endsWith(".iso"))
    }

    @Test
    fun `empty hash does not crash`() {
        assertTrue(incognitoName("").isNotEmpty())
        // An empty hash sums to 0, which lands on the "no category" branch.
        assertEquals("", incognitoCategory(""))
    }

    @Test
    fun `tags stay within the documented one-to-three range`() {
        val samples = listOf(hash, "a".repeat(40), "0123456789abcdef", "ff00ff00ff00")
        samples.forEach { sample ->
            assertTrue(incognitoTags(sample).size <= 3)
        }
    }

    @Test
    fun `tracker host strips scheme port and www`() {
        assertEquals(
            "tracker.example.org",
            trackerHost("https://tracker.example.org:443/announce"),
        )
        assertEquals("example.org", trackerHost("http://www.example.org/announce"))
        assertEquals("", trackerHost(""))
    }

    @Test
    fun `tracker short name keeps the registrable-looking label`() {
        assertEquals("example", trackerShortName("https://tracker.example.org/announce"))
        assertEquals("", trackerShortName(""))
    }
}
