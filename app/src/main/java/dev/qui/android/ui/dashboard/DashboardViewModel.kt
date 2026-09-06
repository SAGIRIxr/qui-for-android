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

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qui.android.R
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.TrackerSortColumn
import dev.qui.android.widget.QuiWidgets
import dev.qui.android.data.model.Instance
import dev.qui.android.data.model.TrackerTransferStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val trackerTransfers: Map<String, TrackerTransferStats> = emptyMap(),
    @StringRes val errorRes: Int? = null,
    val errorMessage: String? = null,
    val updatedAt: Long? = null,
    val refreshing: Boolean = false,
) {
    val isHealthy: Boolean get() = errorRes == null
}

/** One row of qui's Tracker Breakdown table, summed across every instance. */
data class TrackerRow(
    val host: String,
    val torrents: Int,
    val size: Long,
    val uploaded: Long,
    val downloaded: Long,
) {
    // qui shows an infinite ratio for a tracker that has only ever uploaded.
    val ratio: Double get() = if (downloaded > 0) uploaded.toDouble() / downloaded else -1.0
}

data class DashboardUiState(
    val cards: List<InstanceCard> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val pendingActions: Set<Int> = emptySet(),
    val actionError: String? = null,
    val actionSucceeded: Boolean = false,
) {
    private val healthyCards: List<InstanceCard> get() = cards.filter(InstanceCard::isHealthy)
    val totalDownloadSpeed: Long get() = healthyCards.sumOf { it.downloadSpeed }
    val totalUploadSpeed: Long get() = healthyCards.sumOf { it.uploadSpeed }
    val totalTorrents: Int get() = healthyCards.sumOf { it.torrentCount }
    val totalSize: Long get() = healthyCards.sumOf { it.totalSize ?: 0L }
    val totalDownloading: Int get() = healthyCards.sumOf { it.downloading }
    val totalSeeding: Int get() = healthyCards.sumOf { it.seeding }
    val activeTorrents: Int get() = totalDownloading + totalSeeding
    val connectedCount: Int get() = healthyCards.count { it.instance.connected }
    val serverStatistics: ServerStatistics? get() = buildServerStatistics(cards)

    /**
     * The per-tracker totals qui charts, merged across instances: a tracker seeded from
     * two clients is one row, the way the web UI presents it.
     */
    fun trackerRows(sort: TrackerSortColumn): List<TrackerRow> {
        val merged = LinkedHashMap<String, TrackerRow>()
        healthyCards.forEach { card ->
            card.trackerTransfers.forEach { (host, stats) ->
                if (host.isBlank()) return@forEach
                val existing = merged[host]
                merged[host] = TrackerRow(
                    host = host,
                    torrents = (existing?.torrents ?: 0) + stats.count,
                    size = (existing?.size ?: 0) + stats.totalSize,
                    uploaded = (existing?.uploaded ?: 0) + stats.uploaded,
                    downloaded = (existing?.downloaded ?: 0) + stats.downloaded,
                )
            }
        }
        val rows = merged.values.toList()
        return when (sort) {
            TrackerSortColumn.Tracker -> rows.sortedBy { it.host }
            TrackerSortColumn.Uploaded -> rows.sortedByDescending { it.uploaded }
            TrackerSortColumn.Downloaded -> rows.sortedByDescending { it.downloaded }
            TrackerSortColumn.Ratio -> rows.sortedByDescending { it.ratio }
            TrackerSortColumn.Torrents -> rows.sortedByDescending { it.torrents }
            TrackerSortColumn.Size -> rows.sortedByDescending { it.size }
        }
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: QuiRepository,
    private val prefsStore: AppPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferencesStore.Snapshot> = prefsStore.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesStore.Snapshot())

    private var pollJob: Job? = null
    private val refreshMutex = Mutex()

    /**
     * Whether the screen is in front. The loop lives in viewModelScope, which outlives
     * the app going to the background, so without this it keeps hitting every client
     * every few seconds behind a screen nobody is looking at.
     */
    private val resumed = MutableStateFlow(true)

    /**
     * Polls per instance. The dashboard has no stream of its own in qui either; it
     * refreshes on an interval.
     */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                resumed.first { it }
                refresh()
                delay(preferences.value.refreshSeconds.coerceAtLeast(2) * 1000L)
            }
        }
    }

    fun setResumed(value: Boolean) {
        resumed.value = value
    }

    /** The eye on each card is qui's global incognito, not a per-card state. */
    fun toggleIncognito() {
        viewModelScope.launch { prefsStore.toggleIncognito() }
    }

    fun toggleServerStatsExpanded() = viewModelScope.launch { prefsStore.toggleServerStatsExpanded() }

    fun toggleTrackerBreakdownExpanded() = viewModelScope.launch { prefsStore.toggleTrackerBreakdownExpanded() }

    fun toggleInstanceExpanded(id: Int) = viewModelScope.launch { prefsStore.toggleInstanceExpanded(id) }

    fun setTrackerSort(column: TrackerSortColumn) {
        viewModelScope.launch { prefsStore.setTrackerSortColumn(column) }
    }

    fun toggleAltSpeedLimits(instanceId: Int) {
        if (instanceId in _state.value.pendingActions) return
        _state.update { it.copy(pendingActions = it.pendingActions + instanceId, actionError = null, actionSucceeded = false) }
        viewModelScope.launch {
            try {
                repository.toggleAltSpeedLimits(instanceId).onSuccess {
                    _state.update { it.copy(actionSucceeded = true) }
                    refresh()
                }.onFailure { error ->
                    _state.update { it.copy(actionError = error.message ?: error.javaClass.simpleName) }
                }
            } finally {
                _state.update { it.copy(pendingActions = it.pendingActions - instanceId) }
            }
        }
    }

    private suspend fun refresh() = refreshMutex.withLock {
        val instances = repository.instances().getOrElse {
            _state.update { state -> state.copy(
                isLoading = false,
                error = it.message ?: it.javaClass.simpleName,
                cards = state.cards.map { card -> card.copy(errorRes = R.string.instances_not_reachable, refreshing = false) },
            ) }
            return@withLock
        }

        _state.update { state -> state.copy(
            error = null,
            isLoading = false,
            cards = instances.map { instance ->
                (state.cards.firstOrNull { it.instance.id == instance.id } ?: InstanceCard(instance))
                    .copy(instance = instance, refreshing = true)
            },
        ) }
        coroutineScope {
            instances.map { instance ->
                async {
                    val card = loadCard(instance)
                    _state.update { state ->
                        state.copy(cards = state.cards.map { old ->
                            if (old.instance.id != instance.id) old
                            else if (!card.isHealthy && old.updatedAt != null) old.copy(
                                instance = instance,
                                errorRes = card.errorRes,
                                errorMessage = card.errorMessage,
                                refreshing = false,
                            ) else card
                        })
                    }
                }
            }.awaitAll()
        }
        QuiWidgets.refresh(context)
    }

    private suspend fun loadCard(instance: Instance): InstanceCard {
        if (!instance.isActive) {
            return InstanceCard(instance, errorRes = R.string.instances_disabled)
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
        ).getOrElse { error -> return InstanceCard(
                instance,
                errorRes = R.string.instances_not_reachable,
                errorMessage = error.message ?: error.javaClass.simpleName,
            ) }

        val stats = response.stats
        val server = response.serverState

        return InstanceCard(
            instance = instance,
            updatedAt = System.currentTimeMillis(),
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
            trackerTransfers = response.counts?.trackerTransfers.orEmpty(),
        )
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
