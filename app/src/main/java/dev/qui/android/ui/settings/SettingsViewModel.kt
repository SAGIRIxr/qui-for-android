/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.SearchHistoryStore
import dev.qui.android.data.TrackerIconStore
import dev.qui.android.data.UpdateChecker
import dev.qui.android.data.UpdateStatus
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountInfo(
    val username: String? = null,
    val serverUrl: String? = null,
    val version: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: QuiRepository,
    private val session: SessionStore,
    private val updateChecker: UpdateChecker,
    private val searchHistoryStore: SearchHistoryStore,
    private val trackerIconStore: TrackerIconStore,
) : ViewModel() {

    private val _account = MutableStateFlow(AccountInfo())
    val account: StateFlow<AccountInfo> = _account.asStateFlow()

    private val _update = MutableStateFlow(UpdateStatus())
    val update: StateFlow<UpdateStatus> = _update.asStateFlow()

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    val searchHistoryCount: StateFlow<Int> = searchHistoryStore.entries
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val trackerIconCount: StateFlow<Int> = trackerIconStore.icons
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            _account.update { it.copy(serverUrl = session.currentServerUrl()) }

            repository.currentUser().onSuccess { user ->
                _account.update { it.copy(username = user.username) }
            }
            // The server's own version, not the newest release on GitHub.
            repository.serverVersion().onSuccess { info ->
                _account.update { it.copy(version = info.version) }
            }
        }
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (_checkingUpdate.value) return
        _checkingUpdate.value = true
        viewModelScope.launch {
            _update.value = updateChecker.check()
            _checkingUpdate.value = false
        }
    }

    fun clearSearchHistory() = viewModelScope.launch { searchHistoryStore.clear() }

    fun clearTrackerIcons() = viewModelScope.launch { trackerIconStore.clear() }
}
