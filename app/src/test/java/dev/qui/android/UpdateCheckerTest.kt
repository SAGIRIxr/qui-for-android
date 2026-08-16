/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android

import dev.qui.android.data.isNewer
import dev.qui.android.data.localizedReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a higher release is newer`() {
        assertTrue(isNewer("v0.3.0", "0.2.0"))
        assertTrue(isNewer("0.2.1", "0.2.0"))
        assertTrue(isNewer("v1.0.0", "0.9.9"))
    }

    @Test
    fun `the same release is not newer`() {
        assertFalse(isNewer("v0.2.0", "0.2.0"))
        assertFalse(isNewer("0.2.0", "v0.2.0"))
    }

    @Test
    fun `an older release is not newer`() {
        assertFalse(isNewer("v0.1.9", "0.2.0"))
        assertFalse(isNewer("0.2.0", "0.10.0"))
    }

    @Test
    fun `shorter tags compare as if zero-padded`() {
        assertTrue(isNewer("v0.3", "0.2.9"))
        assertFalse(isNewer("v0.2", "0.2.0"))
    }

    @Test
    fun `a pre-release suffix is ignored`() {
        assertTrue(isNewer("v0.3.0-beta.1", "0.2.0"))
    }

    @Test
    fun `an unparseable tag never claims to be newer`() {
        assertFalse(isNewer("nightly", "0.2.0"))
        assertFalse(isNewer("v0.x.0", "0.2.0"))
        assertFalse(isNewer("", "0.2.0"))
    }

    // --- localizedReleaseNotes ---

    private val bilingual = """
        ## 简体中文
        - 中文条目

        ## English
        - English entry
    """.trimIndent()

    @Test
    fun `picks the section naming the locale`() {
        assertEquals("- 中文条目", localizedReleaseNotes(bilingual, "简体中文"))
        assertEquals("- English entry", localizedReleaseNotes(bilingual, "English"))
    }

    @Test
    fun `a heading the body does not carry falls back to the whole body`() {
        assertEquals(bilingual.trim(), localizedReleaseNotes(bilingual, "Deutsch"))
    }

    @Test
    fun `notes without language headings come back whole`() {
        val plain = "### Fixed\n- something"
        assertEquals(plain, localizedReleaseNotes(plain, "English"))
    }

    @Test
    fun `blank or absent notes are null`() {
        assertNull(localizedReleaseNotes(null, "English"))
        assertNull(localizedReleaseNotes("   ", "English"))
    }
}
