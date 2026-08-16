/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.torrents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.SearchHistoryStore
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ViewMode
import dev.qui.android.data.model.ActionTarget
import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.model.Category
import dev.qui.android.data.model.FilterState
import dev.qui.android.data.model.Instance
import dev.qui.android.data.model.ServerState
import dev.qui.android.data.model.Torrent
import dev.qui.android.data.model.TorrentCounts
import dev.qui.android.data.model.TorrentFilters
import dev.qui.android.data.model.TorrentStats
import dev.qui.android.data.remote.QuiStreamClient
import dev.qui.android.data.remote.StreamEvent
import dev.qui.android.data.remote.StreamSubscription
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How many rows a page holds. qui's mobile list uses 100 and grows the window. */
private const val PAGE_SIZE = 100

data class TorrentsUiState(
    val instances: List<Instance> = emptyList(),
    val selectedInstanceId: Int? = null,
    val torrents: List<Torrent> = emptyList(),
    val total: Int = 0,
    val stats: TorrentStats? = null,
    val counts: TorrentCounts? = null,
    val serverState: ServerState? = null,
    val categories: Map<String, Category> = emptyMap(),
    val tags: List<String> = emptyList(),
    val supportsTrackerHealth: Boolean = false,
    val search: String = "",
    val sortField: String = "added_on",
    val sortOrder: String = "desc",
    val filters: TorrentFilters = TorrentFilters(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val streamConnected: Boolean = false,
    val selection: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    // qui calls this scope "Unified": every active client merged into one list.
    val unifiedScope: Boolean = false,
) {
    val selectedInstance: Instance?
        get() = instances.firstOrNull { it.id == selectedInstanceId }

    val activeInstanceIds: List<Int>
        get() = instances.filter { it.isActive }.map { it.id }

    /** More than one client is the only case where merging them means anything. */
    val canUnify: Boolean
        get() = activeInstanceIds.size > 1
}

@HiltViewModel
class TorrentsViewModel @Inject constructor(
    private val repository: QuiRepository,
    private val streamClient: QuiStreamClient,
    private val prefsStore: AppPreferencesStore,
    private val searchHistoryStore: SearchHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TorrentsUiState())
    val state: StateFlow<TorrentsUiState> = _state.asStateFlow()

    val preferences: StateFlow<AppPreferencesStore.Snapshot> = prefsStore.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferencesStore.Snapshot())

    /** Recent queries, newest first, for the search sheet. */
    val searchHistory: StateFlow<List<String>> = searchHistoryStore.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var streamJob: Job? = null
    private var loadedPages = 1

    init {
        viewModelScope.launch {
            // Read the stored snapshot directly: the StateFlow above starts on defaults
            // and would otherwise lose the persisted sort and last-opened instance.
            val prefs = prefsStore.snapshot.first()
            _state.update {
                it.copy(
                    sortField = prefs.sortField,
                    sortOrder = prefs.sortOrder,
                    selectedInstanceId = prefs.lastInstanceId,
                    unifiedScope = prefs.unifiedScope,
                )
            }
            loadInstances()
        }
    }

    private suspend fun loadInstances() {
        repository.instances()
            .onSuccess { instances ->
                val active = instances.filter { it.isActive }
                // Prefer whatever is already on screen, then the instance the user last
                // opened, then simply the first active one.
                val remembered = _state.value.selectedInstanceId
                    ?: preferences.value.lastInstanceId
                val target = remembered?.takeIf { id -> active.any { it.id == id } }
                    ?: active.firstOrNull()?.id

                _state.update { it.copy(instances = instances, selectedInstanceId = target) }

                // A stored unified scope only survives if there is still more than one
                // active client to merge; otherwise fall back to a single one.
                if (_state.value.unifiedScope && _state.value.canUnify) {
                    _state.update { it.copy(unifiedScope = false) }
                    selectUnified()
                } else {
                    _state.update { it.copy(unifiedScope = false) }
                    target?.let { selectInstance(it) }
                }
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message, isLoading = false) }
            }
    }

    fun selectInstance(instanceId: Int) {
        val current = _state.value
        if (!current.unifiedScope &&
            current.selectedInstanceId == instanceId &&
            current.torrents.isNotEmpty()
        ) {
            return
        }
        _state.update {
            it.copy(
                selectedInstanceId = instanceId,
                unifiedScope = false,
                torrents = emptyList(),
                selection = emptySet(),
                selectionMode = false,
                isLoading = true,
            )
        }
        loadedPages = 1
        viewModelScope.launch {
            prefsStore.setLastInstance(instanceId)
            prefsStore.setUnifiedScope(false)
        }
        loadMetadata(instanceId)
        restart()
    }

    /**
     * Switches to qui's unified scope. Categories and tags still come from one client
     * because qBittorrent defines them per instance; the list itself is merged.
     */
    fun selectUnified() {
        val current = _state.value
        if (current.unifiedScope || !current.canUnify) return
        _state.update {
            it.copy(
                unifiedScope = true,
                torrents = emptyList(),
                selection = emptySet(),
                selectionMode = false,
                isLoading = true,
            )
        }
        loadedPages = 1
        viewModelScope.launch { prefsStore.setUnifiedScope(true) }
        current.activeInstanceIds.firstOrNull()?.let(::loadMetadata)
        restart()
    }

    private fun loadMetadata(instanceId: Int) {
        viewModelScope.launch {
            repository.capabilities(instanceId).onSuccess { caps ->
                _state.update { it.copy(supportsTrackerHealth = caps.supportsTrackerHealth) }
            }
        }
        viewModelScope.launch {
            repository.categories(instanceId).onSuccess { cats ->
                _state.update { it.copy(categories = cats) }
            }
        }
        viewModelScope.launch {
            repository.tags(instanceId).onSuccess { tags ->
                _state.update { it.copy(tags = tags) }
            }
        }
    }

    /**
     * Restarts the live stream with the current query. Every change to sort, search or
     * filters has to re-subscribe because qui does the filtering server-side.
     */
    private fun restart() {
        streamJob?.cancel()
        val unified = _state.value.unifiedScope
        val instanceId = if (unified) 0 else _state.value.selectedInstanceId ?: return

        // A REST fetch fills the list immediately; the stream then keeps it live. This
        // is the same ordering the web UI uses so the screen is never blank while the
        // SSE connection is negotiating.
        viewModelScope.launch { fetchPage(reset = true) }

        streamJob = viewModelScope.launch {
            var backoffSeconds = 1L
            while (true) {
                val current = _state.value
                val subscription = StreamSubscription(
                    key = if (unified) "android-unified" else "android-${current.selectedInstanceId}",
                    instanceId = instanceId,
                    // qui's aggregated subscription is instanceId 0 plus the members.
                    instanceIds = current.activeInstanceIds.takeIf { unified },
                    page = 0,
                    limit = PAGE_SIZE * loadedPages,
                    sort = current.sortField,
                    order = current.sortOrder,
                    search = current.search,
                    filters = current.filters.takeIf { !it.isEmpty },
                )

                try {
                    streamClient.stream(listOf(subscription)).collect { event ->
                        backoffSeconds = 1
                        handleStreamEvent(event)
                    }
                } catch (_: Exception) {
                    // Falls through to the backoff below.
                }

                _state.update { it.copy(streamConnected = false) }
                delay(backoffSeconds * 1000)
                backoffSeconds = (backoffSeconds * 2).coerceAtMost(30)
            }
        }
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Snapshot -> {
                val data = event.payload.data ?: return
                _state.update { current ->
                    current.copy(
                        torrents = data.rows,
                        total = data.total,
                        stats = data.stats ?: current.stats,
                        counts = data.counts ?: current.counts,
                        serverState = data.serverState ?: current.serverState,
                        categories = data.categories ?: current.categories,
                        tags = data.tags ?: current.tags,
                        supportsTrackerHealth = data.trackerHealthSupported
                            ?: current.supportsTrackerHealth,
                        hasMore = data.hasMore ?: (data.rows.size < data.total),
                        isLoading = false,
                        isRefreshing = false,
                        streamConnected = true,
                        error = null,
                    )
                }
            }

            is StreamEvent.Delta -> {
                val data = event.payload.data ?: return
                _state.update { current -> current.applyDelta(data.rows, event.payload.delta?.order) }
            }

            is StreamEvent.Failed -> _state.update {
                it.copy(streamConnected = false, isLoading = false, isRefreshing = false)
            }

            StreamEvent.Heartbeat -> _state.update { it.copy(streamConnected = true) }
        }
    }

    /**
     * Applies a delta frame the way qui's stream-merge does: changed rows are patched in
     * place, and `order` — sent only when membership or ordering changed — becomes the
     * new sequence, dropping rows that left the page.
     */
    private fun TorrentsUiState.applyDelta(
        changed: List<Torrent>,
        order: List<String>?,
    ): TorrentsUiState {
        if (changed.isEmpty() && order == null) return this

        val byKey = torrents.associateByTo(LinkedHashMap()) { it.key }
        changed.forEach { byKey[it.key] = it }

        val next = if (order != null) {
            order.mapNotNull { byKey[it] }
        } else {
            torrents.map { byKey[it.key] ?: it }
        }

        return copy(
            torrents = next,
            streamConnected = true,
            isLoading = false,
            isRefreshing = false,
            // Selections referring to rows that left the page would silently act on
            // nothing, so they are pruned here.
            selection = selection.intersect(next.map { it.key }.toSet()),
        )
    }

    private suspend fun fetchPage(reset: Boolean) {
        val current = _state.value

        val request = if (current.unifiedScope) {
            repository.crossInstanceTorrents(
                instanceIds = current.activeInstanceIds,
                page = 0,
                limit = PAGE_SIZE * loadedPages,
                sort = current.sortField,
                order = current.sortOrder,
                search = current.search,
                filters = current.filters,
            )
        } else {
            repository.torrents(
                instanceId = current.selectedInstanceId ?: return,
                page = 0,
                limit = PAGE_SIZE * loadedPages,
                sort = current.sortField,
                order = current.sortOrder,
                search = current.search,
                filters = current.filters,
            )
        }

        request
            .onSuccess { response ->
                _state.update {
                    it.copy(
                        torrents = response.rows,
                        total = response.total,
                        stats = response.stats ?: it.stats,
                        counts = response.counts ?: it.counts,
                        serverState = response.serverState ?: it.serverState,
                        categories = response.categories ?: it.categories,
                        tags = response.tags ?: it.tags,
                        supportsTrackerHealth = response.trackerHealthSupported
                            ?: it.supportsTrackerHealth,
                        hasMore = response.hasMore ?: (response.rows.size < response.total),
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = error.message)
                }
            }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading) return
        loadedPages += 1
        viewModelScope.launch { fetchPage(reset = false) }
        restart()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            loadInstances()
            fetchPage(reset = true)
        }
    }

    fun setSearch(value: String) {
        _state.update { it.copy(search = value, isLoading = true) }
        loadedPages = 1
        restart()
    }

    /** Runs a query from the search sheet and remembers it. */
    fun submitSearch(query: String) {
        setSearch(query)
        viewModelScope.launch { searchHistoryStore.record(query) }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch { searchHistoryStore.remove(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryStore.clear() }
    }

    fun setSort(field: String, order: String) {
        _state.update { it.copy(sortField = field, sortOrder = order, isLoading = true) }
        viewModelScope.launch { prefsStore.setSort(field, order) }
        loadedPages = 1
        restart()
    }

    /**
     * Incognito lives in qui's mobile action bar rather than a settings page, because
     * it is something you flip on the spot before handing someone your phone.
     */
    fun toggleIncognito() {
        val next = !preferences.value.incognito
        viewModelScope.launch { prefsStore.setIncognito(next) }
    }

    /** Cycles the list density, in the order qui lists the three modes. */
    fun cycleViewMode() {
        val next = when (preferences.value.viewMode) {
            ViewMode.Normal -> ViewMode.Compact
            ViewMode.Compact -> ViewMode.UltraCompact
            ViewMode.UltraCompact -> ViewMode.Normal
        }
        viewModelScope.launch { prefsStore.setViewMode(next) }
    }

    /** qui's mobile header lets you flip the unit inline, without opening settings. */
    fun toggleSpeedUnit() {
        val next = if (preferences.value.speedUnit == SpeedUnit.Bytes) {
            SpeedUnit.Bits
        } else {
            SpeedUnit.Bytes
        }
        viewModelScope.launch { prefsStore.setSpeedUnit(next) }
    }

    fun setFilters(filters: TorrentFilters) {
        _state.update { it.copy(filters = filters, isLoading = true) }
        loadedPages = 1
        restart()
    }

    fun clearFilters() = setFilters(TorrentFilters())

    /** Cycles a chip through include → exclude → neutral, like qui's sidebar rows. */
    fun toggleFilter(kind: FilterKind, value: String, target: FilterState) {
        val f = _state.value.filters

        fun List<String>.toggled(on: Boolean) =
            if (on) (this + value).distinct() else this - value

        val next = when (kind) {
            FilterKind.Status -> f.copy(
                status = f.status.toggled(target == FilterState.Include),
                excludeStatus = f.excludeStatus.toggled(target == FilterState.Exclude),
            )
            FilterKind.Category -> f.copy(
                categories = f.categories.toggled(target == FilterState.Include),
                excludeCategories = f.excludeCategories.toggled(target == FilterState.Exclude),
            )
            FilterKind.Tag -> f.copy(
                tags = f.tags.toggled(target == FilterState.Include),
                excludeTags = f.excludeTags.toggled(target == FilterState.Exclude),
            )
            FilterKind.Tracker -> f.copy(
                trackers = f.trackers.toggled(target == FilterState.Include),
                excludeTrackers = f.excludeTrackers.toggled(target == FilterState.Exclude),
            )
        }

        setFilters(next)
    }

    // ---- selection ----

    fun toggleSelection(key: String) = _state.update { current ->
        val next = if (key in current.selection) current.selection - key else current.selection + key
        current.copy(selection = next, selectionMode = next.isNotEmpty())
    }

    fun enterSelection(key: String) = _state.update {
        it.copy(selection = it.selection + key, selectionMode = true)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet(), selectionMode = false) }

    fun selectAllLoaded() = _state.update { current ->
        current.copy(
            selection = current.torrents.map { it.key }.toSet(),
            selectionMode = current.torrents.isNotEmpty(),
        )
    }

    // ---- actions ----

    /**
     * Runs a bulk action against the current selection. Deltas from the stream reflect
     * the result, so nothing is optimistically mutated here.
     */
    fun runAction(
        action: String,
        keys: Set<String> = _state.value.selection,
        configure: BulkActionRequest.() -> BulkActionRequest = { this },
        onResult: (Result<Unit>) -> Unit = {},
    ) {
        val current = _state.value
        // Instance 0 is qui's all-instances sentinel; the targets carry the real ids.
        val instanceId = if (current.unifiedScope) 0 else current.selectedInstanceId ?: return
        if (keys.isEmpty()) return

        val hashes = keys.map { it.substringAfterLast(':') }
        val targets = keys.mapNotNull { key ->
            val parts = key.split(':')
            if (parts.size == 2) {
                parts[0].toIntOrNull()?.let { ActionTarget(it, parts[1]) }
            } else {
                null
            }
        }.takeIf { it.size == keys.size }

        viewModelScope.launch {
            val result = repository.bulkAction(
                instanceId,
                BulkActionRequest(hashes = hashes, action = action, targets = targets).configure(),
            )
            result.onSuccess { clearSelection() }
            onResult(result)
            // A REST re-read closes the gap when the stream is down.
            if (!_state.value.streamConnected) fetchPage(reset = true)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}

enum class FilterKind { Status, Category, Tag, Tracker }
