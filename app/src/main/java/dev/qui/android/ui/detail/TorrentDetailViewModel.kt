/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.model.Torrent
import dev.qui.android.data.model.TorrentFile
import dev.qui.android.data.model.TorrentPeer
import dev.qui.android.data.model.TorrentProperties
import dev.qui.android.data.model.TorrentTracker
import dev.qui.android.data.model.WebSeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tab set from qui's TorrentDetailsPanel. */
enum class DetailTab { General, Trackers, Peers, Content, WebSeeds }

data class DetailUiState(
    val instanceId: Int = 0,
    val hash: String = "",
    val torrent: Torrent? = null,
    val properties: TorrentProperties? = null,
    val trackers: List<TorrentTracker> = emptyList(),
    val peers: List<TorrentPeer> = emptyList(),
    val files: List<TorrentFile> = emptyList(),
    val webSeeds: List<WebSeed> = emptyList(),
    val tab: DetailTab = DetailTab.General,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TorrentDetailViewModel @Inject constructor(
    private val repository: QuiRepository,
    savedStateHandle: SavedStateHandle,
    prefsStore: AppPreferencesStore,
) : ViewModel() {

    private val instanceId: Int = savedStateHandle.get<Int>("instanceId") ?: 0
    private val hash: String = savedStateHandle.get<String>("hash").orEmpty()

    private val _state = MutableStateFlow(DetailUiState(instanceId = instanceId, hash = hash))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferencesStore.Snapshot> = prefsStore.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesStore.Snapshot())

    private var pollJob: Job? = null

    private val resumed = MutableStateFlow(true)

    init {
        start()
    }

    /**
     * Polls the active tab only. qui does the same: the general row is stream-backed and
     * the heavier tabs (peers, content) poll while visible.
     */
    private fun start() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                resumed.first { it }
                loadForTab(_state.value.tab)
                delay(preferences.value.refreshSeconds.coerceAtLeast(2) * 1000L)
            }
        }
    }

    /** Same reason as the dashboard: viewModelScope does not stop at the home button. */
    fun setResumed(value: Boolean) {
        resumed.value = value
    }

    private suspend fun loadForTab(tab: DetailTab) {
        // The header needs properties regardless of which tab is open.
        repository.torrentProperties(instanceId, hash)
            .onSuccess { props ->
                _state.update { it.copy(properties = props, isLoading = false, error = null) }
            }
            .onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }

        // The list row carries fields properties lacks (state, category, tags, ratio).
        repository.torrents(
            instanceId = instanceId,
            page = 0,
            limit = 1,
            sort = "added_on",
            order = "desc",
            search = hash,
            filters = null,
        ).onSuccess { response ->
            response.rows.firstOrNull { it.hash.equals(hash, ignoreCase = true) }?.let { row ->
                _state.update { it.copy(torrent = row) }
            }
        }

        when (tab) {
            DetailTab.General -> Unit
            DetailTab.Trackers -> repository.torrentTrackers(instanceId, hash)
                .onSuccess { list -> _state.update { it.copy(trackers = list) } }

            DetailTab.Peers -> repository.torrentPeers(instanceId, hash)
                .onSuccess { list -> _state.update { it.copy(peers = list) } }

            DetailTab.Content -> repository.torrentFiles(instanceId, hash)
                .onSuccess { list -> _state.update { it.copy(files = list) } }

            DetailTab.WebSeeds -> repository.torrentWebSeeds(instanceId, hash)
                .onSuccess { list -> _state.update { it.copy(webSeeds = list) } }
        }
    }

    fun selectTab(tab: DetailTab) {
        _state.update { it.copy(tab = tab) }
        viewModelScope.launch { loadForTab(tab) }
    }

    fun action(action: String, configure: BulkActionRequest.() -> BulkActionRequest = { this }) {
        viewModelScope.launch {
            repository.bulkAction(
                instanceId,
                BulkActionRequest(hashes = listOf(hash), action = action).configure(),
            )
            loadForTab(_state.value.tab)
        }
    }

    fun rename(name: String) = viewModelScope.launch {
        repository.renameTorrent(instanceId, hash, name)
            .onSuccess { loadForTab(_state.value.tab) }
            .onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    fun setFilePriority(indexes: List<Int>, priority: Int) = viewModelScope.launch {
        repository.setFilePriority(instanceId, hash, indexes, priority)
            .onSuccess { loadForTab(DetailTab.Content) }
    }

    fun addTrackers(urls: String) = viewModelScope.launch {
        repository.addTrackers(instanceId, hash, urls)
            .onSuccess { loadForTab(DetailTab.Trackers) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
