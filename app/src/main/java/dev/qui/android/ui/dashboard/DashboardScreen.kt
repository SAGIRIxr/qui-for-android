/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's Dashboard: per-instance cards with connection state, live speeds and
 * torrent counts, plus an all-instances summary at the top.
 */

package dev.qui.android.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = palette.card,
                    contentColor = palette.cardForeground,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("All clients", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatBlock(
                            label = "Download",
                            value = formatSpeed(state.totalDownloadSpeed, prefs.speedUnit),
                            color = QuiTheme.downloadColor,
                        )
                        StatBlock(
                            label = "Upload",
                            value = formatSpeed(state.totalUploadSpeed, prefs.speedUnit),
                            color = QuiTheme.uploadColor,
                        )
                        StatBlock(
                            label = "Torrents",
                            value = "${state.totalTorrents}",
                        )
                    }
                }
            }
        }

        items(state.cards, key = { it.instance.id }) { card ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = card.instance.isActive) {
                        onOpenInstance(card.instance.id)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = palette.card,
                    contentColor = palette.cardForeground,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
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

                    if (card.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = card.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.destructive,
                        )
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatBlock(
                                label = "Download",
                                value = formatSpeed(card.downloadSpeed, prefs.speedUnit),
                                color = QuiTheme.downloadColor,
                            )
                            StatBlock(
                                label = "Upload",
                                value = formatSpeed(card.uploadSpeed, prefs.speedUnit),
                                color = QuiTheme.uploadColor,
                            )
                            StatBlock(label = "Torrents", value = "${card.torrentCount}")
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "Session ↓ ${formatBytes(card.sessionDownloaded)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.mutedForeground,
                            )
                            Text(
                                text = "↑ ${formatBytes(card.sessionUploaded)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.mutedForeground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val palette = QuiTheme.palette
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.mutedForeground,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color ?: palette.foreground,
        )
    }
}
