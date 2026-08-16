/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.model.Instance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Instance list shared by the nav bar badge and the instance picker. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val repository: QuiRepository,
) : ViewModel() {

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    fun refresh() = viewModelScope.launch {
        repository.instances().onSuccess { _instances.value = it }
    }
}
