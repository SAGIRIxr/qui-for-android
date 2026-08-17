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
import dev.qui.android.data.UpdateChecker
import dev.qui.android.data.UpdateStatus
import dev.qui.android.data.ViewMode
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val prefsStore: AppPreferencesStore,
    private val session: SessionStore,
    private val repository: QuiRepository,
    private val trackerIconStore: TrackerIconStore,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    /**
     * Set once per app start when a newer release exists and the user has not asked to
     * be left alone about that particular version. Null the rest of the time, which is
     * what keeps the dialog from being a nag.
     */
    private val _updatePrompt = MutableStateFlow<UpdateStatus?>(null)
    val updatePrompt: StateFlow<UpdateStatus?> = _updatePrompt.asStateFlow()

    init {
        viewModelScope.launch {
            if (!prefsStore.snapshot.first().autoUpdateCheck) return@launch
            // Nothing to update towards until there is a server to use the app with,
            // and a dialog over the login screen would be in the way.
            if (session.isConfigured.first { it } != true) return@launch

            val status = updateChecker.check()
            if (!status.appUpdateAvailable) return@launch
            if (status.appLatest == prefsStore.snapshot.first().skippedUpdate) return@launch

            _updatePrompt.value = status
        }
    }

    fun dismissUpdatePrompt() {
        _updatePrompt.value = null
    }

    /** "Do not tell me about this one again"; a later release still prompts. */
    fun skipUpdate() {
        val tag = _updatePrompt.value?.appLatest
        _updatePrompt.value = null
        viewModelScope.launch { prefsStore.setSkippedUpdate(tag) }
    }

    fun setAutoUpdateCheck(enabled: Boolean) = viewModelScope.launch {
        prefsStore.setAutoUpdateCheck(enabled)
        // Turning it back on should not stay silenced by an old decision.
        if (enabled) prefsStore.setSkippedUpdate(null)
    }

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

    fun setWidgetRefreshMinutes(minutes: Int) = viewModelScope.launch {
        // QuiApplication watches the preference and re-declares the schedule.
        prefsStore.setWidgetRefreshMinutes(minutes)
    }

    fun logout() = viewModelScope.launch { repository.logout() }
}
