/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's TorrentCardsMobile SwipeableCard, including its three densities:
 * ultra-compact (one line), compact (two rows + meta), and normal (progress bar).
 */

package dev.qui.android.ui.torrents

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.qui.android.R
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ViewMode
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.components.QuiBadge
import dev.qui.android.ui.components.QuiProgress
import dev.qui.android.ui.components.TrackerIcon
import dev.qui.android.ui.format.ETA_INFINITE
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatEta
import dev.qui.android.ui.format.formatProgress
import dev.qui.android.ui.format.formatRatio
import dev.qui.android.ui.format.formatSpeed
import dev.qui.android.ui.theme.QuiTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun TorrentCard(
    torrent: Torrent,
    viewMode: ViewMode,
    speedUnit: SpeedUnit,
    supportsTrackerHealth: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    incognito: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onQuickAction: (String) -> Unit = {},
    // Set only in the unified scope, where a row's client is not implied by the header.
    instanceName: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = QuiTheme.palette
    val badge = statusBadgeFor(torrent, supportsTrackerHealth)

    val displayName = if (incognito) incognitoName(torrent.hash) else torrent.name
    val displayCategory = if (incognito) incognitoCategory(torrent.hash) else torrent.category
    val displayTags = if (incognito) incognitoTags(torrent.hash) else torrent.tagList
    val displayRatio = if (incognito) incognitoRatio(torrent.hash) else torrent.ratio
    val trackerName = if (incognito) "tracker.example" else trackerShortName(torrent.tracker)
    // Incognito must not leak the real tracker, and an empty host suppresses the icon.
    val trackerIconHost = if (incognito) "" else trackerHost(torrent.tracker)

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val revealPx = with(density) { REVEAL_WIDTH.toPx() }

    // Selection mode owns the tap, so swiping is disabled while it is active.
    LaunchedEffect(selectionMode) {
        if (selectionMode) offsetX.animateTo(0f)
    }

    fun close() = scope.launch { offsetX.animateTo(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        // Sits behind the card and is only uncovered as the card slides left.
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val paused = torrent.state.startsWith("paused") || torrent.state.startsWith("stopped")
            QuickActionCircle(
                icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                description = stringResource(
                    if (paused) R.string.action_resume else R.string.action_pause
                ),
                tint = palette.primary,
            ) {
                close()
                onQuickAction(if (paused) "resume" else "pause")
            }
            QuickActionCircle(
                icon = Icons.Default.Refresh,
                description = stringResource(R.string.action_recheck),
                tint = palette.foreground,
            ) {
                close()
                onQuickAction("recheck")
            }
            QuickActionCircle(
                icon = Icons.Default.Delete,
                description = stringResource(R.string.action_delete),
                tint = palette.destructive,
            ) {
                close()
                onQuickAction("delete")
            }
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .then(
                if (selectionMode) {
                    Modifier
                } else {
                    Modifier.pointerInput(revealPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    // Past the halfway point the row stays open, so a
                                    // short flick is enough and a stray nudge is not.
                                    val target = if (offsetX.value < -revealPx / 2) -revealPx else 0f
                                    offsetX.animateTo(target)
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                                )
                            }
                        }
                    }
                }
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) palette.accent.copy(alpha = 0.5f) else palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, palette.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    when {
                        // An open row swallows the first tap, which is what you want
                        // when the tap was meant to dismiss it.
                        offsetX.value != 0f -> close()
                        selectionMode -> onToggleSelect()
                        else -> onClick()
                    }
                },
                onLongClick = onLongPress,
            )
            .padding(
                horizontal = when (viewMode) {
                    ViewMode.UltraCompact -> 12.dp
                    ViewMode.Compact -> 8.dp
                    ViewMode.Normal -> 16.dp
                },
                vertical = when (viewMode) {
                    ViewMode.UltraCompact -> 4.dp
                    ViewMode.Compact -> 8.dp
                    ViewMode.Normal -> 16.dp
                },
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                when (viewMode) {
                    ViewMode.UltraCompact -> UltraCompactBody(
                        torrent = torrent,
                        displayName = displayName,
                        trackerIconHost = trackerIconHost,
                        badge = badge,
                        speedUnit = speedUnit,
                    )

                    ViewMode.Compact -> CompactBody(
                        torrent = torrent,
                        displayName = displayName,
                        displayCategory = displayCategory,
                        displayTags = displayTags,
                        displayRatio = displayRatio,
                        trackerName = trackerName,
                        trackerIconHost = trackerIconHost,
                        instanceName = instanceName,
                        badge = badge,
                        speedUnit = speedUnit,
                    )

                    ViewMode.Normal -> NormalBody(
                        torrent = torrent,
                        displayName = displayName,
                        displayCategory = displayCategory,
                        displayTags = displayTags,
                        displayRatio = displayRatio,
                        trackerName = trackerName,
                        trackerIconHost = trackerIconHost,
                        instanceName = instanceName,
                        badge = badge,
                        speedUnit = speedUnit,
                    )
                }
            }

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
    }
}

/** How much of the action row a full swipe uncovers. */
private val REVEAL_WIDTH = 150.dp

@Composable
private fun QuickActionCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val palette = QuiTheme.palette
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(palette.muted)
            .border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun UltraCompactBody(
    torrent: Torrent,
    displayName: String,
    trackerIconHost: String,
    badge: StatusBadge,
    speedUnit: SpeedUnit,
) {
    val palette = QuiTheme.palette

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // qui's ultra-compact row leads with the tracker favicon before the name.
        TrackerIcon(host = trackerIconHost, size = 12.dp)
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (torrent.dlspeed > 0) {
            Text(
                text = "↓${formatSpeed(torrent.dlspeed, speedUnit, compact = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = QuiTheme.downloadColor,
                fontWeight = FontWeight.Medium,
            )
        }
        if (torrent.upspeed > 0) {
            Text(
                text = "↑${formatSpeed(torrent.upspeed, speedUnit, compact = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = QuiTheme.uploadColor,
                fontWeight = FontWeight.Medium,
            )
        }

        QuiBadge(
            text = badge.label,
            variant = badge.variant,
            overrideColor = badge.overrideColor,
            compact = true,
        )

        if (torrent.progress < 1.0) {
            Text(
                text = "${formatProgress(torrent.progress)}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.mutedForeground,
            )
        }
    }
}

@Composable
private fun CompactBody(
    torrent: Torrent,
    displayName: String,
    displayCategory: String,
    displayTags: List<String>,
    displayRatio: Double,
    trackerName: String,
    trackerIconHost: String,
    instanceName: String?,
    badge: StatusBadge,
    speedUnit: SpeedUnit,
) {
    val palette = QuiTheme.palette

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        QuiBadge(badge.label, badge.variant, overrideColor = badge.overrideColor, compact = true)
    }

    Spacer(Modifier.height(3.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatBytes(torrent.downloaded)} / ${formatBytes(torrent.size)}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedForeground,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.torrents_ratio) + " ",
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedForeground,
            )
            Text(
                text = formatRatio(displayRatio),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = QuiTheme.ratioColor(displayRatio),
            )
        }
    }

    Spacer(Modifier.height(3.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (instanceName != null) {
                MetaChip(icon = Icons.Default.Storage, text = instanceName)
            }
            if (trackerName.isNotEmpty()) {
                MetaChip(text = trackerName, trackerHost = trackerIconHost)
            }
            if (displayCategory.isNotEmpty()) {
                MetaChip(icon = Icons.Default.Folder, text = displayCategory)
            }
            if (displayTags.isNotEmpty()) {
                MetaChip(icon = Icons.Default.Sell, text = displayTags.joinToString(", "))
            }
        }

        Spacer(Modifier.width(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatProgress(torrent.progress)}%",
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedForeground,
            )
            if (torrent.dlspeed > 0) {
                Text(
                    text = "↓${formatSpeed(torrent.dlspeed, speedUnit, compact = true)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuiTheme.downloadColor,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (torrent.upspeed > 0) {
                Text(
                    text = "↑${formatSpeed(torrent.upspeed, speedUnit, compact = true)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuiTheme.uploadColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun NormalBody(
    torrent: Torrent,
    displayName: String,
    displayCategory: String,
    displayTags: List<String>,
    displayRatio: Double,
    trackerName: String,
    trackerIconHost: String,
    instanceName: String?,
    badge: StatusBadge,
    speedUnit: SpeedUnit,
) {
    val palette = QuiTheme.palette

    Text(
        text = displayName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatBytes(torrent.downloaded)} / ${formatBytes(torrent.size)}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedForeground,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (torrent.eta > 0 && torrent.eta != ETA_INFINITE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = formatEta(torrent.eta),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.mutedForeground,
                    )
                }
            }
            Text(
                text = "${formatProgress(torrent.progress)}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    QuiProgress(progress = torrent.progress.toFloat())
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ratio ",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                )
                Text(
                    text = formatRatio(displayRatio),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = QuiTheme.ratioColor(displayRatio),
                )
            }
            if (torrent.dlspeed > 0) {
                SpeedLabel(
                    icon = Icons.Default.KeyboardArrowDown,
                    color = QuiTheme.downloadColor,
                    text = formatSpeed(torrent.dlspeed, speedUnit),
                )
            }
            if (torrent.upspeed > 0) {
                SpeedLabel(
                    icon = Icons.Default.KeyboardArrowUp,
                    color = QuiTheme.uploadColor,
                    text = formatSpeed(torrent.upspeed, speedUnit),
                )
            }
        }

        QuiBadge(badge.label, badge.variant, overrideColor = badge.overrideColor)
    }

    if (trackerName.isNotEmpty() || displayCategory.isNotEmpty() || displayTags.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (instanceName != null) {
                MetaChip(icon = Icons.Default.Storage, text = instanceName)
            }
            if (trackerName.isNotEmpty()) {
                MetaChip(text = trackerName, trackerHost = trackerIconHost)
            }
            if (displayCategory.isNotEmpty()) {
                MetaChip(icon = Icons.Default.Folder, text = displayCategory)
            }
            displayTags.forEach { tag ->
                QuiBadge(tag, dev.qui.android.ui.components.BadgeVariant.Secondary, compact = true)
            }
        }
    }
}

@Composable
private fun SpeedLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetaChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trackerHost: String? = null,
) {
    val palette = QuiTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (trackerHost != null) {
            TrackerIcon(host = trackerHost, size = 13.dp)
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.mutedForeground,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
