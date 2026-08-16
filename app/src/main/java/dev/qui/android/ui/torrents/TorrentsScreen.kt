/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's mobile torrent list: the scope/search header, the status line with the
 * inline sort control and speed-unit toggle, the card list, a selection action bar, and
 * sheets for filters, sorting and adding torrents.
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    // debounce() on the search field is still marked preview in kotlinx-coroutines.
    kotlinx.coroutines.FlowPreview::class,
)

package dev.qui.android.ui.torrents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.R
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ViewMode
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.LocalMobileScroll
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.nestedScrollConnection
import dev.qui.android.ui.components.BadgeVariant
import dev.qui.android.ui.components.QuiBadge
import dev.qui.android.ui.components.StatusDot
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatSpeed
import dev.qui.android.ui.theme.QuiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun TorrentsScreen(
    pendingAdd: MutableStateFlow<AddIntent?>,
    onOpenTorrent: (instanceId: Int, hash: String) -> Unit,
    viewModel: TorrentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showInstances by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Torrent?>(null) }

    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val mobileScroll = LocalMobileScroll.current
    val scope = rememberCoroutineScope()

    val incomingAdd by pendingAdd.collectAsStateWithLifecycle()

    // A torrent handed in from outside opens the add sheet as soon as we are composed.
    LaunchedEffect(incomingAdd) {
        if (incomingAdd != null) showAdd = true
    }

    val listState = rememberLazyListState()

    // Reaching the top always restores the bars, so they can never be stranded offscreen.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
            .distinctUntilChanged()
            .filter { it }
            .collect { mobileScroll.show() }
    }

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
        containerColor = QuiTheme.palette.background,
        topBar = {
            TorrentsTopBar(
                state = state,
                speedUnit = prefs.speedUnit,
                onOpenSearch = { showSearch = true },
                onClearSearch = { viewModel.setSearch("") },
                onOpenFilters = { showFilters = true },
                onOpenSort = { showSort = true },
                onFlipSortOrder = {
                    viewModel.setSort(
                        state.sortField,
                        if (state.sortOrder == "asc") "desc" else "asc",
                    )
                },
                onToggleSpeedUnit = viewModel::toggleSpeedUnit,
                onOpenInstances = { showInstances = true },
                onClearFilters = viewModel::clearFilters,
            )
        },
        bottomBar = {
            // qui's mobile view stacks a quick-action row above the footer nav, and
            // swaps it for the selection bar once rows are picked.
            if (state.selectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    onClear = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAllLoaded,
                    onResume = { viewModel.runAction("resume") },
                    onPause = { viewModel.runAction("pause") },
                    onMore = { showActions = true },
                )
            } else {
                AnimatedVisibility(
                    visible = mobileScroll.barsVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    TorrentsActionBar(
                        incognito = prefs.incognito,
                        viewMode = prefs.viewMode,
                        filterCount = state.filters.activeCount,
                        searchActive = state.search.isNotBlank(),
                        onOpenSearch = { showSearch = true },
                        onToggleIncognito = viewModel::toggleIncognito,
                        onCycleViewMode = viewModel::cycleViewMode,
                        onOpenFilters = { showFilters = true },
                        onAdd = { showAdd = true },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(mobileScroll.nestedScrollConnection()),
        ) {
            when {
                state.isLoading && state.torrents.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.instances.none { it.isActive } -> {
                    EmptyState(
                        title = stringResource(R.string.torrents_no_clients_title),
                        message = stringResource(R.string.torrents_no_clients_message),
                    )
                }

                state.torrents.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.torrents_empty_title),
                        message = if (state.search.isNotBlank() || !state.filters.isEmpty) {
                            stringResource(R.string.torrents_empty_filtered)
                        } else {
                            stringResource(R.string.torrents_empty_message)
                        },
                    )
                }

                else -> Box(Modifier.fillMaxSize()) {
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
                                instanceName = torrent.instanceName
                                    ?.takeIf { state.unifiedScope },
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
                                onQuickAction = { action ->
                                    if (action == "delete" && prefs.confirmDelete) {
                                        actionTarget = torrent
                                        showActions = true
                                    } else {
                                        viewModel.runAction(action, setOf(torrent.key))
                                    }
                                },
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
                                    CircularProgressIndicator(
                                        Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }

                    // A phone cannot flick back through thousands of rows, which is why
                    // qui puts this button on mobile only.
                    AnimatedVisibility(
                        visible = listState.firstVisibleItemIndex > 8,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = QuiTheme.palette.card,
                            contentColor = QuiTheme.palette.foreground,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.scope_back_to_top),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        SearchSheet(
            initialQuery = state.search,
            history = searchHistory,
            onDismiss = { showSearch = false },
            onSubmit = { query ->
                showSearch = false
                viewModel.submitSearch(query)
            },
            onRemoveHistory = viewModel::removeSearchHistory,
            onClearHistory = viewModel::clearSearchHistory,
        )
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
            unifiedScope = state.unifiedScope,
            canUnify = state.canUnify,
            onDismiss = { showInstances = false },
            onSelect = {
                viewModel.selectInstance(it)
                showInstances = false
            },
            onSelectUnified = {
                viewModel.selectUnified()
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
    speedUnit: SpeedUnit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onFlipSortOrder: () -> Unit,
    onToggleSpeedUnit: () -> Unit,
    onOpenInstances: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val palette = QuiTheme.palette

    Surface(color = palette.background) {
        Column(Modifier.fillMaxWidth()) {
            // Scope row: which client the list is showing, then the three list controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstanceChip(
                    name = when {
                        state.unifiedScope -> stringResource(R.string.scope_all_clients)
                        else -> state.selectedInstance?.name
                            ?: stringResource(R.string.torrents_select_client)
                    },
                    connected = state.selectedInstance?.connected.takeIf { !state.unifiedScope },
                    unified = state.unifiedScope,
                    onClick = onOpenInstances,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Spacer(Modifier.weight(1f))

                // qui shows free disk space in its status bar; on a phone the scope row
                // is the only place with room for it.
                state.serverState?.freeSpaceOnDisk?.takeIf { it > 0 }?.let { free ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Icon(
                            Icons.Default.Sd,
                            contentDescription = stringResource(R.string.torrents_free_space),
                            tint = palette.mutedForeground,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = formatBytes(free),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.mutedForeground,
                            maxLines = 1,
                        )
                    }
                }

                IconButton(onClick = onOpenFilters) {
                    val activeFilters = state.filters.activeCount
                    val icon = @Composable {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.torrents_filters),
                            tint = if (activeFilters > 0) palette.primary else palette.foreground,
                        )
                    }
                    if (activeFilters > 0) {
                        BadgedBox(badge = { Badge { Text("$activeFilters") } }) { icon() }
                    } else {
                        icon()
                    }
                }
            }

            // Status line, matching qui's mobile header: counts and speeds on the left,
            // the sort control and the speed-unit toggle on the right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.hasMore && state.torrents.isNotEmpty()) {
                            stringResource(
                                R.string.torrents_loaded_of,
                                state.torrents.size,
                                state.total,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.torrents_count,
                                state.total,
                                state.total,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.mutedForeground,
                        maxLines = 1,
                    )
                    state.stats?.let { stats ->
                        Text(
                            text = "↓${formatSpeed(stats.totalDownloadSpeed ?: 0, speedUnit, compact = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = QuiTheme.downloadColor,
                            maxLines = 1,
                        )
                        Text(
                            text = "↑${formatSpeed(stats.totalUploadSpeed ?: 0, speedUnit, compact = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = QuiTheme.uploadColor,
                            maxLines = 1,
                        )
                    }
                }

                TextButton(
                    onClick = onOpenSort,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.torrents_sort),
                        modifier = Modifier.size(15.dp),
                        tint = palette.mutedForeground,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(sortLabelFor(state.sortField)),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onFlipSortOrder, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (state.sortOrder == "asc") {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = stringResource(
                            if (state.sortOrder == "asc") {
                                R.string.sort_ascending
                            } else {
                                R.string.sort_descending
                            }
                        ),
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(15.dp),
                    )
                }
                TextButton(
                    onClick = onToggleSpeedUnit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        // qui labels the toggle with the unit it is currently using.
                        text = if (speedUnit == SpeedUnit.Bytes) "MiB/s" else "Mbps",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.mutedForeground,
                        maxLines = 1,
                    )
                }
            }

            ActiveScopeRow(
                search = state.search,
                filterCount = state.filters.activeCount,
                streamConnected = state.streamConnected,
                onClearSearch = onClearSearch,
                onClearFilters = onClearFilters,
            )
        }
    }
}

/** The client selector; qui shows the same name, dot and chevron in its scope dropdown. */
@Composable
private fun InstanceChip(
    name: String,
    connected: Boolean?,
    unified: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = QuiTheme.palette
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (unified) Icons.Default.Layers else Icons.Default.Storage,
            contentDescription = null,
            tint = palette.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (connected != null) {
            Spacer(Modifier.width(6.dp))
            StatusDot(connected = connected)
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = palette.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The bordered strip qui shows under the header once a search or filter is narrowing
 * the list, so it is obvious why the count dropped, plus the live/polling indicator.
 */
@Composable
private fun ActiveScopeRow(
    search: String,
    filterCount: Int,
    streamConnected: Boolean,
    onClearSearch: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val palette = QuiTheme.palette
    val hasScope = search.isNotBlank() || filterCount > 0

    if (!hasScope && streamConnected) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (search.isNotBlank()) {
            DismissibleChip(
                icon = Icons.Default.Search,
                text = search,
                onDismiss = onClearSearch,
            )
        }
        if (filterCount > 0) {
            DismissibleChip(
                icon = Icons.Default.FilterList,
                text = "$filterCount",
                onDismiss = onClearFilters,
            )
        }
        if (!streamConnected) {
            QuiBadge(
                text = stringResource(R.string.torrents_polling),
                variant = BadgeVariant.Outline,
                compact = true,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DismissibleChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onDismiss: () -> Unit,
) {
    val palette = QuiTheme.palette
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(palette.primary.copy(alpha = 0.06f))
            .border(1.dp, palette.primary.copy(alpha = 0.25f), shape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.filters_clear_all),
            tint = palette.primary,
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * qui's mobile action row: the controls you reach for mid-scroll, at thumb height,
 * rather than buried in a settings page.
 */
@Composable
private fun TorrentsActionBar(
    incognito: Boolean,
    viewMode: ViewMode,
    filterCount: Int,
    searchActive: Boolean,
    onOpenSearch: () -> Unit,
    onToggleIncognito: () -> Unit,
    onCycleViewMode: () -> Unit,
    onOpenFilters: () -> Unit,
    onAdd: () -> Unit,
) {
    val palette = QuiTheme.palette

    Surface(color = palette.background) {
        Column {
            HorizontalDivider(color = palette.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionBarButton(
                    icon = Icons.Default.Search,
                    label = stringResource(R.string.torrents_search),
                    active = searchActive,
                    onClick = onOpenSearch,
                )
                ActionBarButton(
                    icon = if (incognito) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    label = stringResource(R.string.torrents_incognito),
                    active = incognito,
                    onClick = onToggleIncognito,
                )
                ActionBarButton(
                    icon = when (viewMode) {
                        ViewMode.Normal -> Icons.Default.ViewAgenda
                        ViewMode.Compact -> Icons.AutoMirrored.Filled.ViewList
                        ViewMode.UltraCompact -> Icons.Default.DensitySmall
                    },
                    label = stringResource(
                        when (viewMode) {
                            ViewMode.Normal -> R.string.settings_view_mode_normal
                            ViewMode.Compact -> R.string.settings_view_mode_compact
                            ViewMode.UltraCompact -> R.string.settings_view_mode_ultra
                        }
                    ),
                    onClick = onCycleViewMode,
                )
                ActionBarButton(
                    icon = Icons.Default.FilterList,
                    label = stringResource(R.string.torrents_filters),
                    active = filterCount > 0,
                    badge = filterCount.takeIf { it > 0 },
                    onClick = onOpenFilters,
                )
                ActionBarButton(
                    icon = Icons.Default.Add,
                    label = stringResource(R.string.add_submit),
                    onClick = onAdd,
                )
            }
        }
    }
}

@Composable
private fun ActionBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: Int? = null,
) {
    val palette = QuiTheme.palette
    val tint = if (active) palette.primary else palette.mutedForeground

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (badge != null) {
            BadgedBox(badge = { Badge { Text("$badge") } }) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
            }
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.selection_clear),
                )
            }
            Text(
                text = pluralStringResource(R.plurals.selection_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.selection_select_all),
                )
            }
            IconButton(onClick = onResume) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_resume),
                )
            }
            IconButton(onClick = onPause) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = stringResource(R.string.action_pause),
                )
            }
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
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
                textAlign = TextAlign.Center,
            )
        }
    }
}
