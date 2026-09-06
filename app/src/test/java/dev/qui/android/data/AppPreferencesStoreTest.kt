/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private suspend fun withStore(file: File, block: suspend (AppPreferencesStore) -> Unit) {
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file },
        )
        try {
            block(AppPreferencesStore(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `new store keeps original defaults`() = runTest {
        withStore(temporaryFolder.newFile("defaults.preferences_pb")) { store ->
            val prefs = store.snapshot.first()
            assertTrue(prefs.showServerStats)
            assertTrue(prefs.serverStatsExpanded)
            assertFalse(prefs.trackerBreakdownExpanded)
            assertTrue(prefs.expandedInstanceIds.isEmpty())
            assertFalse(prefs.incognito)
            assertEquals(MainPage.Torrents, prefs.lastMainPage)
        }
    }

    @Test fun `page folding and shared privacy survive closing and reopening the store`() = runTest {
        val file = temporaryFolder.newFile("restart.preferences_pb")
        withStore(file) { store ->
            store.setLastMainPage(MainPage.Dashboard)
            store.toggleServerStatsExpanded()
            store.toggleTrackerBreakdownExpanded()
            store.toggleInstanceExpanded(17)
            store.toggleInstanceExpanded(23)
            store.toggleInstanceExpanded(17)
            store.toggleIncognito()
        }
        withStore(file) { store ->
            val prefs = store.snapshot.first()
            assertEquals(MainPage.Dashboard, prefs.lastMainPage)
            assertFalse(prefs.serverStatsExpanded)
            assertTrue(prefs.trackerBreakdownExpanded)
            assertEquals(setOf(23), prefs.expandedInstanceIds)
            assertTrue(prefs.incognito)
        }
    }

    @Test fun `all main pages round trip`() = runTest {
        val file = temporaryFolder.newFile("pages.preferences_pb")
        for (page in MainPage.entries) {
            withStore(file) { it.setLastMainPage(page) }
            withStore(file) { assertEquals(page, it.snapshot.first().lastMainPage) }
        }
    }

    @Test fun `rapid toggles use stored state not a stale UI snapshot`() = runTest {
        withStore(temporaryFolder.newFile("rapid.preferences_pb")) { store ->
            coroutineScope {
                repeat(20) {
                    launch { store.toggleIncognito() }
                    launch { store.toggleServerStatsExpanded() }
                    launch { store.toggleTrackerBreakdownExpanded() }
                    launch { store.toggleInstanceExpanded(7) }
                }
            }
            val prefs = store.snapshot.first()
            assertFalse(prefs.incognito)
            assertTrue(prefs.serverStatsExpanded)
            assertFalse(prefs.trackerBreakdownExpanded)
            assertTrue(prefs.expandedInstanceIds.isEmpty())
        }
    }

    @Test fun `hiding sections does not reset expansion choices`() = runTest {
        withStore(temporaryFolder.newFile("hidden.preferences_pb")) { store ->
            store.toggleServerStatsExpanded()
            store.toggleTrackerBreakdownExpanded()
            store.toggleInstanceExpanded(4)
            store.setShowServerStats(false)
            store.setShowTrackerBreakdown(false)
            store.setShowInstanceCards(false)
            store.setShowServerStats(true)
            store.setShowTrackerBreakdown(true)
            store.setShowInstanceCards(true)
            val prefs = store.snapshot.first()
            assertFalse(prefs.serverStatsExpanded)
            assertTrue(prefs.trackerBreakdownExpanded)
            assertEquals(setOf(4), prefs.expandedInstanceIds)
        }
    }

    @Test fun `legacy privacy survives and invalid new preferences fall back safely`() = runTest {
        val file = temporaryFolder.newFile("migration.preferences_pb")
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file },
        )
        try {
            dataStore.edit {
                it[booleanPreferencesKey("incognito")] = true
                it[stringPreferencesKey("last_main_page")] = "detail/1/private-hash"
                it[stringSetPreferencesKey("dash_expanded_instance_ids")] = setOf("5", "bad", "999999999999")
            }
        } finally {
            job.cancelAndJoin()
        }
        withStore(file) { store ->
            val prefs = store.snapshot.first()
            assertTrue(prefs.incognito)
            assertEquals(MainPage.Torrents, prefs.lastMainPage)
            assertTrue(prefs.serverStatsExpanded)
            assertEquals(setOf(5), prefs.expandedInstanceIds)
            store.toggleIncognito()
            assertFalse(store.snapshot.first().incognito)
        }
    }

    @Test fun `only top level routes can be persisted for startup`() {
        MainPage.entries.forEach { assertEquals(it, MainPage.fromRoute(it.route)) }
        listOf(null, "", "login", "detail/{instanceId}/{hash}", "detail/1/hash", "unknown").forEach {
            assertNull(MainPage.fromRoute(it))
        }
    }

    @Test
    fun `server statistics are shown by default`() {
        assertTrue(AppPreferencesStore.Snapshot().showServerStats)
    }
}
