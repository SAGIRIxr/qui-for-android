/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Local UI preferences. These mirror the values qui persists in localStorage
 * (qui-speed-units, qui:torrent-mobile-sort, theme, view mode, incognito).
 */

package dev.qui.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore by preferencesDataStore("qui_prefs")

/** Card density in the torrent list, matching qui's mobile view modes. */
enum class ViewMode { Normal, Compact, UltraCompact }

/** Follows qui's ThemeMode: light / dark / auto. */
enum class ThemeMode { Light, Dark, Auto }

/** qui's speed unit toggle: bytes (B/s) or bits (bps). */
enum class SpeedUnit { Bytes, Bits }

@Singleton
class AppPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val themeId = stringPreferencesKey("theme_id")
        val themeVariation = stringPreferencesKey("theme_variation")
        val themeMode = stringPreferencesKey("theme_mode")
        val viewMode = stringPreferencesKey("view_mode")
        val speedUnit = stringPreferencesKey("speed_unit")
        val incognito = booleanPreferencesKey("incognito")
        val sortField = stringPreferencesKey("sort_field")
        val sortOrder = stringPreferencesKey("sort_order")
        val lastInstanceId = intPreferencesKey("last_instance_id")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val refreshSeconds = intPreferencesKey("refresh_seconds")
    }

    data class Snapshot(
        val themeId: String = "default",
        val themeVariation: String? = null,
        val themeMode: ThemeMode = ThemeMode.Auto,
        val viewMode: ViewMode = ViewMode.Compact,
        val speedUnit: SpeedUnit = SpeedUnit.Bytes,
        val incognito: Boolean = false,
        val sortField: String = "added_on",
        val sortOrder: String = "desc",
        val lastInstanceId: Int? = null,
        val dynamicColor: Boolean = false,
        val confirmDelete: Boolean = true,
        val refreshSeconds: Int = 3,
    )

    val snapshot: Flow<Snapshot> = context.prefsDataStore.data.map { prefs ->
        Snapshot(
            themeId = prefs[Keys.themeId] ?: "default",
            themeVariation = prefs[Keys.themeVariation],
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.Auto,
            viewMode = prefs[Keys.viewMode]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() }
                ?: ViewMode.Compact,
            speedUnit = prefs[Keys.speedUnit]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.Bytes,
            incognito = prefs[Keys.incognito] ?: false,
            sortField = prefs[Keys.sortField] ?: "added_on",
            sortOrder = prefs[Keys.sortOrder] ?: "desc",
            lastInstanceId = prefs[Keys.lastInstanceId],
            dynamicColor = prefs[Keys.dynamicColor] ?: false,
            confirmDelete = prefs[Keys.confirmDelete] ?: true,
            refreshSeconds = prefs[Keys.refreshSeconds] ?: 3,
        )
    }

    suspend fun setTheme(id: String, variation: String?) = context.prefsDataStore.edit {
        it[Keys.themeId] = id
        if (variation == null) it.remove(Keys.themeVariation) else it[Keys.themeVariation] = variation
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.prefsDataStore.edit {
        it[Keys.themeMode] = mode.name
    }

    suspend fun setViewMode(mode: ViewMode) = context.prefsDataStore.edit {
        it[Keys.viewMode] = mode.name
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) = context.prefsDataStore.edit {
        it[Keys.speedUnit] = unit.name
    }

    suspend fun setIncognito(enabled: Boolean) = context.prefsDataStore.edit {
        it[Keys.incognito] = enabled
    }

    suspend fun setSort(field: String, order: String) = context.prefsDataStore.edit {
        it[Keys.sortField] = field
        it[Keys.sortOrder] = order
    }

    suspend fun setLastInstance(id: Int?) = context.prefsDataStore.edit {
        if (id == null) it.remove(Keys.lastInstanceId) else it[Keys.lastInstanceId] = id
    }

    suspend fun setDynamicColor(enabled: Boolean) = context.prefsDataStore.edit {
        it[Keys.dynamicColor] = enabled
    }

    suspend fun setConfirmDelete(enabled: Boolean) = context.prefsDataStore.edit {
        it[Keys.confirmDelete] = enabled
    }

    suspend fun setRefreshSeconds(seconds: Int) = context.prefsDataStore.edit {
        it[Keys.refreshSeconds] = seconds.coerceIn(1, 60)
    }
}
