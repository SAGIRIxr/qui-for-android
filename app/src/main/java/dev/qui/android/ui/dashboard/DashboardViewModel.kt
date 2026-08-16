/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.model.Instance
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstanceCard(
    val instance: Instance,
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val sessionDownloaded: Long = 0,
    val sessionUploaded: Long = 0,
    val torrentCount: Int = 0,
    val error: String? = null,
)

data class DashboardUiState(
    val cards: List<InstanceCard> = emptyList(),
    val isLoading: Boolean = true,
) {
    val totalDownloadSpeed: Long get() = cards.sumOf { it.downloadSpeed }
    val totalUploadSpeed: Long get() = cards.sumOf { it.uploadSpeed }
    val totalTorrents: Int get() = cards.sumOf { it.torrentCount }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: QuiRepository,
    prefsStore: AppPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferencesStore.Snapshot> = prefsStore.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesStore.Snapshot())

    private var pollJob: Job? = null

    /**
     * Polls transfer info per instance. The dashboard has no stream of its own in qui
     * either; it refreshes on an interval.
     */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(preferences.value.refreshSeconds.coerceAtLeast(2) * 1000L)
            }
        }
    }

    private suspend fun refresh() {
        val instances = repository.instances().getOrElse {
            _state.value = _state.value.copy(isLoading = false)
            return
        }

        val cards = instances.map { instance ->
            viewModelScope.async {
                if (!instance.isActive) {
                    return@async InstanceCard(instance, error = "Disabled")
                }

                val transfer = repository.transferInfo(instance.id).getOrNull()
                    ?: return@async InstanceCard(instance, error = "Not reachable")

                // A one-row listing is the cheapest way to read the server-side total.
                val total = repository.torrents(
                    instanceId = instance.id,
                    page = 0,
                    limit = 1,
                    sort = "added_on",
                    order = "desc",
                    search = null,
                    filters = null,
                ).getOrNull()?.total ?: 0

                InstanceCard(
                    instance = instance,
                    downloadSpeed = transfer.dlInfoSpeed,
                    uploadSpeed = transfer.upInfoSpeed,
                    sessionDownloaded = transfer.dlInfoData,
                    sessionUploaded = transfer.upInfoData,
                    torrentCount = total,
                )
            }
        }.awaitAll()

        _state.value = DashboardUiState(cards = cards, isLoading = false)
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
