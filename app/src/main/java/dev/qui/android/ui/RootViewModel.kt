/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ThemeMode
import dev.qui.android.data.TrackerIconStore
import dev.qui.android.data.TrackerSortColumn
import dev.qui.android.data.ViewMode
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val prefsStore: AppPreferencesStore,
    private val session: SessionStore,
    private val repository: QuiRepository,
    private val trackerIconStore: TrackerIconStore,
) : ViewModel() {

    val preferences: StateFlow<AppPreferencesStore.Snapshot> = prefsStore.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesStore.Snapshot())

    /**
     * Provided once for the whole tree so the list, the detail screen and anything else
     * showing a tracker share one decoded cache.
     */
    val trackerIcons: StateFlow<Map<String, ImageBitmap>> = trackerIconStore.icons

    fun loadTrackerIcons() = viewModelScope.launch { trackerIconStore.refresh() }

    val isConfigured: StateFlow<Boolean?> = session.isConfigured
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(id: String, variation: String?) = viewModelScope.launch {
        prefsStore.setTheme(id, variation)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsStore.setThemeMode(mode) }

    fun setViewMode(mode: ViewMode) = viewModelScope.launch { prefsStore.setViewMode(mode) }

    fun setSpeedUnit(unit: SpeedUnit) = viewModelScope.launch { prefsStore.setSpeedUnit(unit) }

    fun setIncognito(enabled: Boolean) = viewModelScope.launch { prefsStore.setIncognito(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setDynamicColor(enabled)
    }

    fun setConfirmDelete(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setConfirmDelete(enabled)
    }

    fun setRefreshSeconds(seconds: Int) = viewModelScope.launch {
        prefsStore.setRefreshSeconds(seconds)
    }

    fun setShowGlobalStats(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setShowGlobalStats(enabled)
    }

    fun setShowTrackerBreakdown(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setShowTrackerBreakdown(enabled)
    }

    fun setShowInstanceCards(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setShowInstanceCards(enabled)
    }

    fun setTrackerSortColumn(column: TrackerSortColumn) = viewModelScope.launch {
        prefsStore.setTrackerSortColumn(column)
    }

    fun logout() = viewModelScope.launch { repository.logout() }
}
