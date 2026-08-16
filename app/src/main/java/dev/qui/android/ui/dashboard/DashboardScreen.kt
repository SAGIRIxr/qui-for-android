/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's Dashboard: an all-clients summary, then one card per instance with the
 * same figures qui's InstanceCard shows — the downloading/seeding/total counts, live
 * speeds, session and all-time transfer, disk usage, and the alternative-speed switch.
 */

package dev.qui.android.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.R
import dev.qui.android.data.SpeedUnit
import dev.qui.android.ui.components.Metric
import dev.qui.android.ui.components.QuiCard
import dev.qui.android.ui.components.StatLine
import dev.qui.android.ui.components.StatusDot
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatSpeed
import dev.qui.android.ui.theme.QuiTheme

@Composable
fun DashboardScreen(
    onOpenInstance: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette

    LaunchedEffect(Unit) { viewModel.start() }

    if (state.isLoading && state.cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_instances_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.dashboard_no_instances_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            QuiCard {
                Text(
                    text = stringResource(R.string.dashboard_all_clients).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.mutedForeground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Metric(
                        label = stringResource(R.string.dashboard_download),
                        value = formatSpeed(state.totalDownloadSpeed, prefs.speedUnit),
                        color = QuiTheme.downloadColor,
                    )
                    Metric(
                        label = stringResource(R.string.dashboard_upload),
                        value = formatSpeed(state.totalUploadSpeed, prefs.speedUnit),
                        color = QuiTheme.uploadColor,
                    )
                    Metric(
                        label = stringResource(R.string.dashboard_torrents),
                        value = "${state.totalTorrents}",
                    )
                    if (state.totalSize > 0) {
                        Metric(
                            label = stringResource(R.string.dashboard_total_size),
                            value = formatBytes(state.totalSize),
                        )
                    }
                }
            }
        }

        items(state.cards, key = { it.instance.id }) { card ->
            InstanceCardView(
                card = card,
                speedUnit = prefs.speedUnit,
                onOpen = { onOpenInstance(card.instance.id) },
                onToggleAltSpeed = { viewModel.toggleAltSpeedLimits(card.instance.id) },
            )
        }
    }
}

@Composable
private fun InstanceCardView(
    card: InstanceCard,
    speedUnit: SpeedUnit,
    onOpen: () -> Unit,
    onToggleAltSpeed: () -> Unit,
) {
    val palette = QuiTheme.palette
    var expanded by remember { mutableStateOf(false) }

    QuiCard(onClick = if (card.isHealthy) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.instance.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.instance.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(connected = card.instance.connected)
        }

        if (card.errorRes != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(card.errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = palette.destructive,
            )
            return@QuiCard
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric(
                label = stringResource(R.string.dashboard_downloading),
                value = "${card.downloading}",
            )
            Metric(
                label = stringResource(R.string.dashboard_seeding),
                value = "${card.seeding}",
            )
            Metric(
                label = stringResource(R.string.dashboard_total),
                value = "${card.torrentCount}",
            )
            if (card.errored > 0) {
                Metric(
                    label = stringResource(R.string.dashboard_errors),
                    value = "${card.errored}",
                    color = palette.destructive,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = palette.border)
        Spacer(Modifier.height(10.dp))

        StatLine(
            label = stringResource(R.string.dashboard_download),
            value = formatSpeed(card.downloadSpeed, speedUnit),
            icon = Icons.Default.ArrowDownward,
            valueColor = QuiTheme.downloadColor,
        )
        Spacer(Modifier.height(6.dp))
        StatLine(
            label = stringResource(R.string.dashboard_upload),
            value = formatSpeed(card.uploadSpeed, speedUnit),
            icon = Icons.Default.ArrowUpward,
            valueColor = QuiTheme.uploadColor,
        )
        card.totalSize?.let {
            Spacer(Modifier.height(6.dp))
            StatLine(
                label = stringResource(R.string.dashboard_total_size),
                value = formatBytes(it),
            )
        }
        card.freeSpace?.let {
            Spacer(Modifier.height(6.dp))
            StatLine(
                label = stringResource(R.string.dashboard_free_space),
                value = formatBytes(it),
                icon = Icons.Default.Sd,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                StatLine(
                    label = stringResource(R.string.dashboard_session),
                    value = "↓ ${formatBytes(card.sessionDownloaded)}   " +
                        "↑ ${formatBytes(card.sessionUploaded)}",
                )
                if (card.allTimeDownloaded != null || card.allTimeUploaded != null) {
                    Spacer(Modifier.height(6.dp))
                    StatLine(
                        label = stringResource(R.string.dashboard_all_time),
                        value = "↓ ${formatBytes(card.allTimeDownloaded ?: 0)}   " +
                            "↑ ${formatBytes(card.allTimeUploaded ?: 0)}",
                    )
                }
                card.peerConnections?.let {
                    Spacer(Modifier.height(6.dp))
                    StatLine(
                        label = stringResource(R.string.dashboard_peer_connections),
                        value = "$it",
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dashboard_alt_speed_limits),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.mutedForeground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = card.altSpeedEnabled,
                        onCheckedChange = { onToggleAltSpeed() },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.dashboard_show_less else R.string.dashboard_show_more
                ),
                style = MaterialTheme.typography.labelMedium,
                color = palette.mutedForeground,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = palette.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
