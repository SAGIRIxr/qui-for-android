/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.model.Instance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Instance list shared by the nav bar badge and the instance picker. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val repository: QuiRepository,
    prefsStore: AppPreferencesStore,
) : ViewModel() {

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    /**
     * qui's MobileFooterNav labels the middle tab with the client currently in scope
     * rather than a generic word, so the bar always says which server you are on.
     */
    val currentInstanceName: StateFlow<String?> =
        combine(_instances, prefsStore.snapshot) { instances, prefs ->
            instances.firstOrNull { it.id == prefs.lastInstanceId && it.isActive }?.name
                ?: instances.firstOrNull { it.isActive }?.name
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = viewModelScope.launch {
        repository.instances().onSuccess { _instances.value = it }
    }
}
