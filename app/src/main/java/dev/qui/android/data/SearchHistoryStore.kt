/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Recent searches for the search sheet. qui's web UI keeps none — the browser's own
 * input history covers it there — but a phone has no such affordance, so the last few
 * queries are kept locally and never leave the device.
 */

package dev.qui.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Beyond this the list stops being "recent" and starts being clutter. */
private const val MAX_ENTRIES = 12

/** Queries cannot contain a newline, so it is safe as the separator. */
private const val SEPARATOR = "\n"

@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("search_history")

    val entries: Flow<List<String>> = context.prefsDataStore.data.map { prefs ->
        prefs[key]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    /** Most recent first, de-duplicated, so repeating a search moves it to the top. */
    suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.prefsDataStore.edit { prefs ->
            val existing = prefs[key]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val next = (listOf(trimmed) + existing.filterNot { it.equals(trimmed, true) })
                .take(MAX_ENTRIES)
            prefs[key] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun remove(query: String) {
        context.prefsDataStore.edit { prefs ->
            val next = prefs[key]?.split(SEPARATOR)
                ?.filter { it.isNotBlank() && it != query }
                .orEmpty()
            if (next.isEmpty()) prefs.remove(key) else prefs[key] = next.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        context.prefsDataStore.edit { it.remove(key) }
    }
}
