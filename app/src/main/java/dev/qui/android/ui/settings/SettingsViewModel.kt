/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _account = MutableStateFlow(AccountInfo())
    val account: StateFlow<AccountInfo> = _account.asStateFlow()

    init {
        viewModelScope.launch {
            _account.update { it.copy(serverUrl = session.currentServerUrl()) }

            repository.currentUser().onSuccess { user ->
                _account.update { it.copy(username = user.username) }
            }
            repository.latestVersion().onSuccess { latest ->
                _account.update { it.copy(version = latest.tagName) }
            }
        }
    }
}
