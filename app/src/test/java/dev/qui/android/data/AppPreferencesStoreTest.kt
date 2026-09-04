/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data

import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesStoreTest {

    @Test
    fun `server statistics are shown by default`() {
        assertTrue(AppPreferencesStore.Snapshot().showServerStats)
    }
}
