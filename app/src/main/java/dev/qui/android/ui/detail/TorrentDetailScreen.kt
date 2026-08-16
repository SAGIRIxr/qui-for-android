/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's TorrentDetailsPanel: a header with live speeds and progress, an action
 * row, and the General / Trackers / Peers / Content / HTTP Sources tabs.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.qui.android.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.R
import dev.qui.android.data.model.TorrentFile
import dev.qui.android.data.model.TorrentPeer
import dev.qui.android.data.model.TorrentTracker
import dev.qui.android.ui.components.BadgeVariant
import dev.qui.android.ui.components.QuiBadge
import dev.qui.android.ui.components.QuiCard
import dev.qui.android.ui.components.TrackerIcon
import dev.qui.android.ui.components.QuiProgress
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatDuration
import dev.qui.android.ui.format.formatEta
import dev.qui.android.ui.format.formatProgress
import dev.qui.android.ui.format.formatRatio
import dev.qui.android.ui.format.formatSpeed
import dev.qui.android.ui.format.formatUnixTime
import dev.qui.android.ui.theme.QuiTheme
import dev.qui.android.ui.torrents.TextInputDialog
import dev.qui.android.ui.torrents.statusBadgeFor
import dev.qui.android.ui.torrents.trackerHost

@Composable
fun TorrentDetailScreen(
    onBack: () -> Unit,
    viewModel: TorrentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette
    var showRename by remember { mutableStateOf(false) }

    val title = state.torrent?.name
        ?: state.properties?.name
        ?: stringResource(R.string.detail_fallback_title)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.foreground,
                    navigationIconContentColor = palette.foreground,
                ),
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.properties == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DetailHeader(state = state, speedUnit = prefs.speedUnit)

            // Icons alone were guesswork, so each action carries its own label.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DetailAction(
                    icon = Icons.Default.PlayArrow,
                    label = stringResource(R.string.action_resume),
                ) { viewModel.action("resume") }
                DetailAction(
                    icon = Icons.Default.Pause,
                    label = stringResource(R.string.action_pause),
                ) { viewModel.action("pause") }
                DetailAction(
                    icon = Icons.Default.Refresh,
                    label = stringResource(R.string.action_recheck),
                ) { viewModel.action("recheck") }
                DetailAction(
                    icon = Icons.Default.Campaign,
                    label = stringResource(R.string.action_reannounce),
                ) { viewModel.action("reannounce") }
                DetailAction(
                    icon = Icons.Default.DriveFileRenameOutline,
                    label = stringResource(R.string.action_rename),
                ) { showRename = true }
            }

            val tabs = DetailTab.entries
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                edgePadding = 8.dp,
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    DetailTab.General ->
                                        stringResource(R.string.detail_tab_general)
                                    DetailTab.Trackers ->
                                        stringResource(R.string.detail_tab_trackers)
                                    DetailTab.Peers ->
                                        stringResource(R.string.detail_tab_peers)
                                    DetailTab.Content ->
                                        stringResource(R.string.detail_tab_content)
                                    DetailTab.WebSeeds ->
                                        stringResource(R.string.detail_tab_web_seeds)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            when (state.tab) {
                DetailTab.General -> GeneralTab(state)
                DetailTab.Trackers -> TrackersTab(state.trackers)
                DetailTab.Peers -> PeersTab(state.peers, prefs.speedUnit)
                DetailTab.Content -> ContentTab(state.files, viewModel::setFilePriority)
                DetailTab.WebSeeds -> WebSeedsTab(state.webSeeds.map { it.url })
            }
        }
    }

    if (showRename) {
        TextInputDialog(
            title = stringResource(R.string.detail_rename_title),
            initial = title,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                viewModel.rename(it)
            },
        )
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = QuiTheme.palette
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.foreground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailHeader(state: DetailUiState, speedUnit: dev.qui.android.data.SpeedUnit) {
    val palette = QuiTheme.palette
    val torrent = state.torrent
    val props = state.properties

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (torrent != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badge = statusBadgeFor(torrent, supportsTrackerHealth = true)
                QuiBadge(badge.label, badge.variant, overrideColor = badge.overrideColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatProgress(torrent.progress)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                if (torrent.eta > 0 && torrent.eta != dev.qui.android.ui.format.ETA_INFINITE) {
                    Text(
                        text = formatEta(torrent.eta),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.mutedForeground,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            QuiProgress(progress = torrent.progress.toFloat())
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "↓ ${formatSpeed(props?.dlSpeed ?: torrent?.dlspeed ?: 0, speedUnit)}",
                style = MaterialTheme.typography.bodySmall,
                color = QuiTheme.downloadColor,
            )
            Text(
                text = "↑ ${formatSpeed(props?.upSpeed ?: torrent?.upspeed ?: 0, speedUnit)}",
                style = MaterialTheme.typography.bodySmall,
                color = QuiTheme.uploadColor,
            )
            val ratio = props?.shareRatio ?: torrent?.ratio ?: 0.0
            Text(
                text = stringResource(R.string.detail_ratio) + " " + formatRatio(ratio),
                style = MaterialTheme.typography.bodySmall,
                color = QuiTheme.ratioColor(ratio),
            )
        }
    }
}

@Composable
private fun GeneralTab(state: DetailUiState) {
    val props = state.properties ?: return
    val torrent = state.torrent

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
          QuiCard {
            StatRow(stringResource(R.string.detail_size), formatBytes(props.totalSize))
            StatRow(stringResource(R.string.detail_downloaded), formatBytes(props.totalDownloaded))
            StatRow(stringResource(R.string.detail_uploaded), formatBytes(props.totalUploaded))
            StatRow(stringResource(R.string.detail_wasted), formatBytes(props.totalWasted))
            StatRow(stringResource(R.string.detail_ratio), formatRatio(props.shareRatio))
            StatRow(
                label = stringResource(R.string.detail_seeds),
                value = stringResource(R.string.detail_x_of_y, props.seeds, props.seedsTotal),
            )
            StatRow(
                label = stringResource(R.string.detail_peers),
                value = stringResource(R.string.detail_x_of_y, props.peers, props.peersTotal),
            )
            StatRow(
                label = stringResource(R.string.detail_connections),
                value = stringResource(
                    R.string.detail_x_of_y,
                    props.nbConnections,
                    props.nbConnectionsLimit,
                ),
            )
          }
        }

        item {
          QuiCard {
            StatRow(stringResource(R.string.detail_time_active), formatDuration(props.timeElapsed))
            StatRow(stringResource(R.string.detail_seeding_time), formatDuration(props.seedingTime))
            StatRow(stringResource(R.string.detail_added), formatUnixTime(props.additionDate))
            StatRow(stringResource(R.string.detail_completed), formatUnixTime(props.completionDate))
            StatRow(stringResource(R.string.detail_last_seen), formatUnixTime(props.lastSeen))
            StatRow(stringResource(R.string.detail_reannounce_in), formatDuration(props.reannounce))
          }
        }

        item {
          QuiCard {
            StatRow(
                label = stringResource(R.string.detail_pieces),
                value = "${props.piecesHave} / ${props.piecesNum} " +
                    "(${formatBytes(props.pieceSize)})",
            )
            StatRow(
                label = stringResource(R.string.detail_private),
                value = stringResource(
                    if (props.isPrivate) R.string.common_yes else R.string.common_no
                ),
            )
            StatRow(stringResource(R.string.detail_save_path), props.savePath, mono = true)
            if (props.downloadPath.isNotBlank()) {
                StatRow(
                    label = stringResource(R.string.detail_download_path),
                    value = props.downloadPath,
                    mono = true,
                )
            }
            torrent?.let {
                StatRow(stringResource(R.string.detail_category), it.category.ifBlank { "—" })
                StatRow(
                    label = stringResource(R.string.detail_tags),
                    value = it.tagList.joinToString(", ").ifBlank { "—" },
                )
            }
          }
        }

        item {
          QuiCard {
            StatRow(
                label = stringResource(R.string.detail_hash_v1),
                value = props.infohashV1.ifBlank { props.hash },
                mono = true,
            )
            if (props.infohashV2.isNotBlank()) {
                StatRow(stringResource(R.string.detail_hash_v2), props.infohashV2, mono = true)
            }
            if (props.createdBy.isNotBlank()) {
                StatRow(stringResource(R.string.detail_created_by), props.createdBy)
            }
            if (props.comment.isNotBlank()) {
                StatRow(stringResource(R.string.detail_comment), props.comment)
            }
          }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, mono: Boolean = false) {
    val palette = QuiTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedForeground,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrackersTab(trackers: List<TorrentTracker>) {
    val palette = QuiTheme.palette

    if (trackers.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_trackers))
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(trackers) { tracker ->
            QuiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackerIcon(host = trackerHost(tracker.url), size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tracker.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    QuiBadge(
                        text = trackerStatusLabel(tracker.status),
                        // Only status 2 means the tracker actually answered.
                        variant = when (tracker.status) {
                            2 -> BadgeVariant.Secondary
                            4 -> BadgeVariant.Destructive
                            else -> BadgeVariant.Outline
                        },
                        compact = true,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("S ${tracker.numSeeds}")
                        append(" · L ${tracker.numLeeches}")
                        append(" · P ${tracker.numPeers}")
                        if (tracker.msg.isNotBlank()) append(" · ${tracker.msg}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.mutedForeground,
                )
            }
        }
    }
}

/** qBittorrent's tracker status enum. */
@Composable
@ReadOnlyComposable
private fun trackerStatusLabel(status: Int): String = stringResource(
    when (status) {
        0 -> R.string.tracker_status_disabled
        1 -> R.string.tracker_status_not_contacted
        2 -> R.string.tracker_status_working
        3 -> R.string.tracker_status_updating
        4 -> R.string.tracker_status_not_working
        else -> R.string.tracker_status_unknown
    }
)

@Composable
private fun PeersTab(peers: List<TorrentPeer>, speedUnit: dev.qui.android.data.SpeedUnit) {
    val palette = QuiTheme.palette

    if (peers.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_peers))
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(peers) { peer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${peer.ip}:${peer.port}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            peer.client?.takeIf { it.isNotBlank() },
                            peer.countryCode?.takeIf { it.isNotBlank() },
                            peer.flags?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${formatProgress(peer.progress)}%",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "↓${formatSpeed(peer.dlSpeed ?: 0, speedUnit, compact = true)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = QuiTheme.downloadColor,
                        )
                        Text(
                            text = "↑${formatSpeed(peer.upSpeed ?: 0, speedUnit, compact = true)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = QuiTheme.uploadColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTab(
    files: List<TorrentFile>,
    onSetPriority: (List<Int>, Int) -> Unit,
) {
    val palette = QuiTheme.palette

    if (files.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_files))
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(files) { file ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // qBittorrent priorities: 0 skip, 1 normal, 6 high, 7 maximum.
                        // Tapping cycles skip → normal → high → maximum.
                        val next = when (file.priority) {
                            0 -> 1
                            1 -> 6
                            6 -> 7
                            else -> 0
                        }
                        onSetPriority(listOf(file.index), next)
                    }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatBytes(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mutedForeground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatProgress(file.progress)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mutedForeground,
                    )
                    Spacer(Modifier.weight(1f))
                    QuiBadge(
                        text = filePriorityLabel(file.priority),
                        variant = if (file.priority == 0) {
                            dev.qui.android.ui.components.BadgeVariant.Outline
                        } else {
                            dev.qui.android.ui.components.BadgeVariant.Secondary
                        },
                        compact = true,
                    )
                }
                Spacer(Modifier.height(4.dp))
                QuiProgress(progress = file.progress.toFloat(), height = 4.dp)
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun filePriorityLabel(priority: Int): String = stringResource(
    when (priority) {
        0 -> R.string.file_priority_skip
        6 -> R.string.file_priority_high
        7 -> R.string.file_priority_maximum
        else -> R.string.file_priority_normal
    }
)

@Composable
private fun WebSeedsTab(urls: List<String>) {
    if (urls.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_web_seeds))
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(urls) { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun EmptyTab(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = QuiTheme.palette.mutedForeground,
        )
    }
}
