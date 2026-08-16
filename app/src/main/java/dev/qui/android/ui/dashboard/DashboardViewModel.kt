/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Backs the per-instance cards. qui's InstanceCard shows counts, transfer totals, disk
 * figures and the alternative-speed switch, and every one of those numbers already
 * rides along on the torrent listing's stats/serverState, so one request per instance
 * fills the whole card.
 */

package dev.qui.android.ui.dashboard

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.R
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
    val allTimeDownloaded: Long? = null,
    val allTimeUploaded: Long? = null,
    val torrentCount: Int = 0,
    val downloading: Int = 0,
    val seeding: Int = 0,
    val errored: Int = 0,
    val totalSize: Long? = null,
    val freeSpace: Long? = null,
    val peerConnections: Long? = null,
    val altSpeedEnabled: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val isHealthy: Boolean get() = errorRes == null
}

data class DashboardUiState(
    val cards: List<InstanceCard> = emptyList(),
    val isLoading: Boolean = true,
) {
    val totalDownloadSpeed: Long get() = cards.sumOf { it.downloadSpeed }
    val totalUploadSpeed: Long get() = cards.sumOf { it.uploadSpeed }
    val totalTorrents: Int get() = cards.sumOf { it.torrentCount }
    val totalSize: Long get() = cards.sumOf { it.totalSize ?: 0L }
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
     * Polls per instance. The dashboard has no stream of its own in qui either; it
     * refreshes on an interval.
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

    fun toggleAltSpeedLimits(instanceId: Int) {
        viewModelScope.launch {
            repository.toggleAltSpeedLimits(instanceId)
            refresh()
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
                    return@async InstanceCard(instance, errorRes = R.string.instances_disabled)
                }

                // A one-row listing is the cheapest way to read the server-side totals:
                // the response carries stats, counts and serverState regardless of limit.
                val response = repository.torrents(
                    instanceId = instance.id,
                    page = 0,
                    limit = 1,
                    sort = "added_on",
                    order = "desc",
                    search = null,
                    filters = null,
                ).getOrNull()
                    ?: return@async InstanceCard(
                        instance,
                        errorRes = R.string.instances_not_reachable,
                    )

                val stats = response.stats
                val server = response.serverState

                InstanceCard(
                    instance = instance,
                    downloadSpeed = server?.dlInfoSpeed ?: stats?.totalDownloadSpeed ?: 0,
                    uploadSpeed = server?.upInfoSpeed ?: stats?.totalUploadSpeed ?: 0,
                    sessionDownloaded = server?.dlInfoData ?: stats?.totalDownloadData ?: 0,
                    sessionUploaded = server?.upInfoData ?: stats?.totalUploadData ?: 0,
                    allTimeDownloaded = server?.alltimeDl,
                    allTimeUploaded = server?.alltimeUl,
                    torrentCount = response.total,
                    downloading = stats?.downloading ?: 0,
                    seeding = stats?.seeding ?: 0,
                    errored = stats?.error ?: 0,
                    totalSize = stats?.totalSize,
                    freeSpace = server?.freeSpaceOnDisk,
                    peerConnections = server?.totalPeerConnections,
                    altSpeedEnabled = server?.useAltSpeedLimits ?: false,
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
