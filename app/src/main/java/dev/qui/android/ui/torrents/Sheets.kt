/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Bottom sheets standing in for qui's FilterSidebar, sort dropdown, instance switcher
 * and torrent context menu.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.qui.android.ui.torrents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.model.FilterState
import dev.qui.android.data.model.Instance
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.components.StatusDot
import dev.qui.android.ui.theme.QuiTheme

@Composable
fun FilterSheet(
    state: TorrentsUiState,
    onDismiss: () -> Unit,
    onToggle: (FilterKind, String, FilterState) -> Unit,
    onClear: () -> Unit,
) {
    val palette = QuiTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!state.filters.isEmpty) {
                    TextButton(onClick = onClear) { Text("Clear all") }
                }
            }

            FilterGroup(
                title = "Status",
                options = STATUS_FILTER_OPTIONS.map { it.value to it.label },
                counts = state.counts?.status.orEmpty(),
                included = state.filters.status,
                excluded = state.filters.excludeStatus,
                onToggle = { value, target -> onToggle(FilterKind.Status, value, target) },
            )

            val categoryOptions = (state.categories.keys + state.counts?.categories?.keys.orEmpty())
                .distinct()
                .sorted()
                .map { it to it.ifEmpty { "Uncategorized" } }
            if (categoryOptions.isNotEmpty()) {
                FilterGroup(
                    title = "Categories",
                    options = categoryOptions,
                    counts = state.counts?.categories.orEmpty(),
                    included = state.filters.categories,
                    excluded = state.filters.excludeCategories,
                    onToggle = { value, target -> onToggle(FilterKind.Category, value, target) },
                )
            }

            val tagOptions = (state.tags + state.counts?.tags?.keys.orEmpty())
                .distinct()
                .sorted()
                .map { it to it.ifEmpty { "Untagged" } }
            if (tagOptions.isNotEmpty()) {
                FilterGroup(
                    title = "Tags",
                    options = tagOptions,
                    counts = state.counts?.tags.orEmpty(),
                    included = state.filters.tags,
                    excluded = state.filters.excludeTags,
                    onToggle = { value, target -> onToggle(FilterKind.Tag, value, target) },
                )
            }

            val trackerOptions = state.counts?.trackers?.keys.orEmpty()
                .sorted()
                .map { it to it.ifEmpty { "Unknown" } }
            if (trackerOptions.isNotEmpty()) {
                FilterGroup(
                    title = "Trackers",
                    options = trackerOptions,
                    counts = state.counts?.trackers.orEmpty(),
                    included = state.filters.trackers,
                    excluded = state.filters.excludeTrackers,
                    onToggle = { value, target -> onToggle(FilterKind.Tracker, value, target) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tap a row to include it, tap the minus to exclude it.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedForeground,
            )
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    options: List<Pair<String, String>>,
    counts: Map<String, Int>,
    included: List<String>,
    excluded: List<String>,
    onToggle: (String, FilterState) -> Unit,
) {
    val palette = QuiTheme.palette

    Spacer(Modifier.height(12.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = palette.mutedForeground,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))

    options.forEach { (value, label) ->
        val isIncluded = value in included
        val isExcluded = value in excluded
        val count = counts[value]

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onToggle(
                        value,
                        if (isIncluded || isExcluded) FilterState.Neutral else FilterState.Include,
                    )
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isIncluded,
                onCheckedChange = {
                    onToggle(value, if (isIncluded) FilterState.Neutral else FilterState.Include)
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isExcluded) palette.destructive else palette.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                )
            }
            TextButton(
                onClick = {
                    onToggle(value, if (isExcluded) FilterState.Neutral else FilterState.Exclude)
                },
            ) {
                Text(
                    text = "−",
                    color = if (isExcluded) palette.destructive else palette.mutedForeground,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun SortSheet(
    currentField: String,
    currentOrder: String,
    onDismiss: () -> Unit,
    onSelect: (field: String, order: String) -> Unit,
) {
    val palette = QuiTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
        ) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(TORRENT_SORT_OPTIONS) { option ->
                    val selected = option.value == currentField
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Re-picking the active field flips direction, the way
                                // clicking a sorted column header does in qui.
                                val order = if (selected) {
                                    if (currentOrder == "asc") "desc" else "asc"
                                } else {
                                    defaultSortOrder(option.value)
                                }
                                onSelect(option.value, order)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) palette.primary else palette.foreground,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = if (currentOrder == "asc") {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                                contentDescription = currentOrder,
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun InstanceSheet(
    instances: List<Instance>,
    selectedId: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val palette = QuiTheme.palette
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "qBittorrent clients",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            instances.forEach { instance ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = instance.isActive) { onSelect(instance.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (instance.isActive) palette.foreground else palette.mutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = instance.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (instance.isActive) {
                                palette.foreground
                            } else {
                                palette.mutedForeground
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = instance.host,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (instance.id == selectedId) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = palette.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    StatusDot(connected = instance.connected)
                }
            }
        }
    }
}

/**
 * Torrent context menu, covering the actions qui exposes in TorrentContextMenu that
 * make sense without a desktop pointer.
 */
@Composable
fun TorrentActionsSheet(
    targetCount: Int,
    singleTorrent: Torrent?,
    categories: List<String>,
    tags: List<String>,
    confirmDelete: Boolean,
    onDismiss: () -> Unit,
    onAction: (String, BulkActionRequest.() -> BulkActionRequest) -> Unit,
) {
    val palette = QuiTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showDelete by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = singleTorrent?.name ?: "$targetCount selected",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            ActionRow(Icons.Default.PlayArrow, "Resume") { onAction("resume") { this } }
            ActionRow(Icons.Default.Pause, "Pause") { onAction("pause") { this } }
            ActionRow(Icons.Default.Refresh, "Force recheck") { onAction("recheck") { this } }
            ActionRow(Icons.Default.Campaign, "Reannounce") { onAction("reannounce") { this } }
            ActionRow(Icons.Default.PriorityHigh, "Top of queue") { onAction("topPriority") { this } }
            ActionRow(Icons.Default.LowPriority, "Bottom of queue") {
                onAction("bottomPriority") { this }
            }
            ActionRow(Icons.Default.Folder, "Set category") { showCategory = true }
            ActionRow(Icons.Default.Sell, "Set tags") { showTags = true }
            ActionRow(Icons.Default.Speed, "Speed limits") { showSpeed = true }
            ActionRow(Icons.Default.Delete, "Delete", destructive = true) {
                if (confirmDelete) showDelete = true else onAction("delete") { this }
            }
        }
    }

    if (showDelete) {
        DeleteDialog(
            count = targetCount,
            onDismiss = { showDelete = false },
            onConfirm = { alsoFiles ->
                showDelete = false
                onAction("delete") { copy(deleteFiles = alsoFiles) }
            },
        )
    }

    if (showCategory) {
        PickerDialog(
            title = "Set category",
            options = listOf("" to "(none)") + categories.map { it to it },
            allowFreeText = true,
            onDismiss = { showCategory = false },
            onConfirm = { value ->
                showCategory = false
                onAction("setCategory") { copy(category = value) }
            },
        )
    }

    if (showTags) {
        PickerDialog(
            title = "Set tags",
            options = tags.map { it to it },
            allowFreeText = true,
            multiSelect = true,
            onDismiss = { showTags = false },
            onConfirm = { value ->
                showTags = false
                onAction("setTags") { copy(tags = value) }
            },
        )
    }

    if (showSpeed) {
        SpeedLimitDialog(
            onDismiss = { showSpeed = false },
            onConfirm = { down, up ->
                showSpeed = false
                if (down != null) onAction("setDownloadLimit") { copy(downloadLimit = down) }
                if (up != null) onAction("setUploadLimit") { copy(uploadLimit = up) }
            },
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = QuiTheme.palette
    val color = if (destructive) palette.destructive else palette.foreground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun DeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var deleteFiles by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Delete torrent?" else "Delete $count torrents?") },
        text = {
            Column {
                Text(
                    if (deleteFiles) {
                        "The downloaded files will be permanently deleted from disk. This cannot be undone."
                    } else {
                        "The torrents will be removed from qBittorrent. Downloaded files stay on disk."
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text("Also delete files from disk")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles) }) {
                Text("Delete", color = QuiTheme.palette.destructive)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    allowFreeText: Boolean = false,
    multiSelect: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var freeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = when {
                                    !multiSelect -> setOf(value)
                                    value in selected -> selected - value
                                    else -> selected + value
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = {
                                selected = when {
                                    !multiSelect -> setOf(value)
                                    value in selected -> selected - value
                                    else -> selected + value
                                }
                            },
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (allowFreeText) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        label = { Text("Or type a new one") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val typed = freeText.trim()
                    val value = if (typed.isNotEmpty()) {
                        (selected + typed).joinToString(",")
                    } else {
                        selected.joinToString(",")
                    }
                    onConfirm(value)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SpeedLimitDialog(
    onDismiss: () -> Unit,
    onConfirm: (download: Long?, upload: Long?) -> Unit,
) {
    var download by remember { mutableStateOf("") }
    var upload by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed limits") },
        text = {
            Column {
                Text(
                    "Values are in KiB/s. Leave a field empty to leave that limit unchanged; " +
                        "use 0 for unlimited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuiTheme.palette.mutedForeground,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = download,
                    onValueChange = { download = it.filter(Char::isDigit) },
                    label = { Text("Download limit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = upload,
                    onValueChange = { upload = it.filter(Char::isDigit) },
                    label = { Text("Upload limit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(download.toLongOrNull(), upload.toLongOrNull()) },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
