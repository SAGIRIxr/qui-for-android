/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's mobile torrent list: a search/scope header, the virtualised card list,
 * a selection action bar, and sheets for filters, sorting and adding torrents.
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package dev.qui.android.ui.torrents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.components.QuiBadge
import dev.qui.android.ui.components.BadgeVariant
import dev.qui.android.ui.components.StatusDot
import dev.qui.android.ui.format.formatSpeed
import dev.qui.android.ui.theme.QuiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun TorrentsScreen(
    pendingAdd: MutableStateFlow<AddIntent?>,
    onOpenTorrent: (instanceId: Int, hash: String) -> Unit,
    viewModel: TorrentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette

    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showInstances by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(state.search) }
    var actionTarget by remember { mutableStateOf<Torrent?>(null) }

    val incomingAdd by pendingAdd.collectAsStateWithLifecycle()

    // A torrent handed in from outside opens the add sheet as soon as we are composed.
    LaunchedEffect(incomingAdd) {
        if (incomingAdd != null) showAdd = true
    }

    // Debounced so each keystroke does not re-subscribe the stream.
    LaunchedEffect(Unit) {
        snapshotFlow { searchText }
            .debounce(350)
            .distinctUntilChanged()
            .collect { viewModel.setSearch(it) }
    }

    val listState = rememberLazyListState()

    // Endless scroll: request the next window as the tail comes into view.
    LaunchedEffect(listState, state.hasMore) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (last, total) -> total > 0 && last >= total - 10 }
            .collect { viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TorrentsTopBar(
                state = state,
                speedUnit = prefs.speedUnit,
                searchVisible = searchVisible,
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchText = ""
                },
                onOpenFilters = { showFilters = true },
                onOpenSort = { showSort = true },
                onOpenInstances = { showInstances = true },
            )
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add torrent")
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = state.selectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    onClear = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllLoaded,
                    onResume = { viewModel.runAction("resume") },
                    onPause = { viewModel.runAction("pause") },
                    onMore = { showActions = true },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.torrents.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.instances.none { it.isActive } -> {
                    EmptyState(
                        title = "No active clients",
                        message = "Add a qBittorrent instance in qui, then pull to refresh.",
                    )
                }

                state.torrents.isEmpty() -> {
                    EmptyState(
                        title = "No torrents",
                        message = if (state.search.isNotBlank() || !state.filters.isEmpty) {
                            "Nothing matches the current search and filters."
                        } else {
                            "This client has no torrents yet."
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            if (prefs.viewMode == dev.qui.android.data.ViewMode.UltraCompact) {
                                4.dp
                            } else {
                                8.dp
                            }
                        ),
                    ) {
                        items(
                            items = state.torrents,
                            key = { it.key },
                        ) { torrent ->
                            TorrentCard(
                                torrent = torrent,
                                viewMode = prefs.viewMode,
                                speedUnit = prefs.speedUnit,
                                supportsTrackerHealth = state.supportsTrackerHealth,
                                isSelected = torrent.key in state.selection,
                                selectionMode = state.selectionMode,
                                incognito = prefs.incognito,
                                onClick = {
                                    val instanceId = torrent.instanceId
                                        ?: state.selectedInstanceId
                                        ?: return@TorrentCard
                                    onOpenTorrent(instanceId, torrent.hash)
                                },
                                onLongPress = {
                                    if (state.selectionMode) {
                                        viewModel.toggleSelection(torrent.key)
                                    } else {
                                        actionTarget = torrent
                                        showActions = true
                                    }
                                },
                                onToggleSelect = { viewModel.toggleSelection(torrent.key) },
                            )
                        }

                        if (state.hasMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onToggle = viewModel::toggleFilter,
            onClear = viewModel::clearFilters,
        )
    }

    if (showSort) {
        SortSheet(
            currentField = state.sortField,
            currentOrder = state.sortOrder,
            onDismiss = { showSort = false },
            onSelect = { field, order ->
                viewModel.setSort(field, order)
                showSort = false
            },
        )
    }

    if (showInstances) {
        InstanceSheet(
            instances = state.instances,
            selectedId = state.selectedInstanceId,
            onDismiss = { showInstances = false },
            onSelect = {
                viewModel.selectInstance(it)
                showInstances = false
            },
        )
    }

    if (showActions) {
        val targets = actionTarget?.let { setOf(it.key) } ?: state.selection
        TorrentActionsSheet(
            targetCount = targets.size,
            singleTorrent = actionTarget,
            categories = state.categories.keys.toList(),
            tags = state.tags,
            confirmDelete = prefs.confirmDelete,
            onDismiss = {
                showActions = false
                actionTarget = null
            },
            onAction = { action, configure ->
                viewModel.runAction(action, targets, configure)
                showActions = false
                actionTarget = null
            },
        )
    }

    if (showAdd) {
        AddTorrentSheet(
            instanceId = state.selectedInstanceId,
            categories = state.categories.keys.toList(),
            knownTags = state.tags,
            prefill = incomingAdd,
            onDismiss = {
                showAdd = false
                pendingAdd.value = null
            },
            onAdded = {
                showAdd = false
                pendingAdd.value = null
                viewModel.refresh()
            },
        )
    }
}

@Composable
private fun TorrentsTopBar(
    state: TorrentsUiState,
    speedUnit: dev.qui.android.data.SpeedUnit,
    searchVisible: Boolean,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenInstances: () -> Unit,
) {
    val palette = QuiTheme.palette

    Surface(color = palette.background) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenInstances, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Storage, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.selectedInstance?.name ?: "Select client",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    state.selectedInstance?.let { StatusDot(connected = it.connected) }
                }

                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                    )
                }
                IconButton(onClick = onOpenSort) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
                IconButton(onClick = onOpenFilters) {
                    val activeFilters = state.filters.activeCount
                    if (activeFilters > 0) {
                        BadgedBox(badge = { Badge { Text("$activeFilters") } }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    } else {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            }

            if (searchVisible) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    placeholder = { Text("Search torrents") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }

            // Speed / count summary, mirroring the totals row in qui's mobile header.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.total} torrents",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                )
                state.stats?.let { stats ->
                    Text(
                        text = "↓${formatSpeed(stats.totalDownloadSpeed ?: 0, speedUnit, compact = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuiTheme.downloadColor,
                    )
                    Text(
                        text = "↑${formatSpeed(stats.totalUploadSpeed ?: 0, speedUnit, compact = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuiTheme.uploadColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!state.streamConnected) {
                    QuiBadge("Polling", BadgeVariant.Outline, compact = true)
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(color = QuiTheme.palette.card, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
            IconButton(onClick = onPause) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = "More actions")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    val palette = QuiTheme.palette
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedForeground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
