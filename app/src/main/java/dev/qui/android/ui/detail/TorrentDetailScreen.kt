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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.data.model.TorrentFile
import dev.qui.android.data.model.TorrentPeer
import dev.qui.android.data.model.TorrentTracker
import dev.qui.android.ui.components.QuiBadge
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

@Composable
fun TorrentDetailScreen(
    onBack: () -> Unit,
    viewModel: TorrentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette
    var showRename by remember { mutableStateOf(false) }

    val title = state.torrent?.name ?: state.properties?.name ?: "Torrent"

    Scaffold(
        topBar = {
            TopAppBar(
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { viewModel.action("resume") }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                }
                IconButton(onClick = { viewModel.action("pause") }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
                IconButton(onClick = { viewModel.action("recheck") }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recheck")
                }
                IconButton(onClick = { viewModel.action("reannounce") }) {
                    Icon(Icons.Default.Campaign, contentDescription = "Reannounce")
                }
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename")
                }
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
                                    DetailTab.General -> "General"
                                    DetailTab.Trackers -> "Trackers"
                                    DetailTab.Peers -> "Peers"
                                    DetailTab.Content -> "Content"
                                    DetailTab.WebSeeds -> "HTTP Sources"
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
            title = "Rename torrent",
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
                text = "Ratio ${formatRatio(ratio)}",
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

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            StatRow("Size", formatBytes(props.totalSize))
            StatRow("Downloaded", formatBytes(props.totalDownloaded))
            StatRow("Uploaded", formatBytes(props.totalUploaded))
            StatRow("Wasted", formatBytes(props.totalWasted))
            StatRow("Ratio", formatRatio(props.shareRatio))
            StatRow("Seeds", "${props.seeds} of ${props.seedsTotal}")
            StatRow("Peers", "${props.peers} of ${props.peersTotal}")
            StatRow("Connections", "${props.nbConnections} of ${props.nbConnectionsLimit}")
            StatRow("Time active", formatDuration(props.timeElapsed))
            StatRow("Seeding time", formatDuration(props.seedingTime))
            StatRow("Added", formatUnixTime(props.additionDate))
            StatRow("Completed", formatUnixTime(props.completionDate))
            StatRow("Last seen", formatUnixTime(props.lastSeen))
            StatRow("Reannounce in", formatDuration(props.reannounce))
            StatRow("Pieces", "${props.piecesHave} / ${props.piecesNum} (${formatBytes(props.pieceSize)})")
            StatRow("Private", if (props.isPrivate) "Yes" else "No")
            StatRow("Save path", props.savePath, mono = true)
            if (props.downloadPath.isNotBlank()) {
                StatRow("Download path", props.downloadPath, mono = true)
            }
            torrent?.let {
                StatRow("Category", it.category.ifBlank { "—" })
                StatRow("Tags", it.tagList.joinToString(", ").ifBlank { "—" })
            }
            StatRow("Hash v1", props.infohashV1.ifBlank { props.hash }, mono = true)
            if (props.infohashV2.isNotBlank()) {
                StatRow("Hash v2", props.infohashV2, mono = true)
            }
            if (props.createdBy.isNotBlank()) StatRow("Created by", props.createdBy)
            if (props.comment.isNotBlank()) StatRow("Comment", props.comment)
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
        EmptyTab("No trackers")
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(trackers) { tracker ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = tracker.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(trackerStatusLabel(tracker.status))
                        append(" · S ${tracker.numSeeds}")
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
private fun trackerStatusLabel(status: Int): String = when (status) {
    0 -> "Disabled"
    1 -> "Not contacted"
    2 -> "Working"
    3 -> "Updating"
    4 -> "Not working"
    else -> "Unknown"
}

@Composable
private fun PeersTab(peers: List<TorrentPeer>, speedUnit: dev.qui.android.data.SpeedUnit) {
    val palette = QuiTheme.palette

    if (peers.isEmpty()) {
        EmptyTab("No connected peers")
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
        EmptyTab("No file list available")
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

private fun filePriorityLabel(priority: Int): String = when (priority) {
    0 -> "Skip"
    1 -> "Normal"
    6 -> "High"
    7 -> "Maximum"
    else -> "Normal"
}

@Composable
private fun WebSeedsTab(urls: List<String>) {
    if (urls.isEmpty()) {
        EmptyTab("No HTTP sources")
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
