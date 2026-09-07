/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's AddTorrentDialog: magnet/URL input, .torrent file picking, and the
 * category / tags / start-paused / save-path options qBittorrent accepts on add.
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.qui.android.ui.torrents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.R
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.addintent.TorrentPayload
import dev.qui.android.ui.theme.QuiTheme

@Composable
fun AddTorrentSheet(
    instanceId: Int?,
    categories: List<String>,
    knownTags: List<String>,
    prefill: AddIntent?,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    onPartialAdded: () -> Unit = {},
    onPrefillConsumed: () -> Unit = {},
    viewModel: AddTorrentViewModel = hiltViewModel(),
) {
    val palette = QuiTheme.palette
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy = androidx.compose.runtime.rememberUpdatedState(state.isBusy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !busy.value },
    )

    LaunchedEffect(prefill, state.isBusy) {
        if (!state.isBusy && prefill != null) {
            viewModel.applyPrefill(prefill)
            onPrefillConsumed()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val payloads = uris.mapNotNull { uri ->
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "torrent.torrent"
                TorrentPayload(name, bytes)
            }.getOrNull()
        }
        viewModel.addFiles(payloads)
    }

    ModalBottomSheet(onDismissRequest = { if (!state.isBusy) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_title),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.urls,
                enabled = !state.isBusy,
                onValueChange = viewModel::setUrls,
                label = { Text(stringResource(R.string.add_magnet_or_url)) },
                placeholder = { Text(stringResource(R.string.add_magnet_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("application/x-bittorrent", "*/*")) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_choose_files))
            }

            state.files.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_remove),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(enabled = !state.isBusy) { viewModel.removeFile(file) },
                    )
                }
            }

            if (categories.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.add_category),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            enabled = !state.isBusy,
                            onClick = {
                                viewModel.setCategory(if (state.category == category) "" else category)
                            },
                            label = { Text(category) },
                        )
                    }
                }
            }

            if (knownTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.add_tags),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    knownTags.forEach { tag ->
                        FilterChip(
                            selected = tag in state.tags,
                            enabled = !state.isBusy,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.savePath,
                enabled = !state.isBusy,
                onValueChange = viewModel::setSavePath,
                label = { Text(stringResource(R.string.add_save_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ToggleRow(
                label = stringResource(R.string.add_start_paused),
                checked = state.startPaused,
                enabled = !state.isBusy,
                onChange = viewModel::setStartPaused,
            )
            ToggleRow(
                label = stringResource(R.string.add_skip_hash_check),
                checked = state.skipHashCheck,
                enabled = !state.isBusy,
                onChange = viewModel::setSkipHashCheck,
            )
            ToggleRow(
                label = stringResource(R.string.add_sequential),
                checked = state.sequential,
                enabled = !state.isBusy,
                onChange = viewModel::setSequential,
            )
            ToggleRow(
                label = stringResource(R.string.add_first_last_piece),
                checked = state.firstLastPiece,
                enabled = !state.isBusy,
                onChange = viewModel::setFirstLastPiece,
            )

            if (state.addedCount > 0) {
                Text(stringResource(R.string.add_partial_result, state.addedCount))
            }
            state.error?.let {
                val hidden = dev.qui.android.ui.LocalAppPreferences.current.incognito
                Text(if (hidden || it.isBlank()) stringResource(R.string.operation_failed) else it,
                    style = MaterialTheme.typography.bodySmall, color = palette.destructive)
            }

            Button(
                onClick = { instanceId?.let { viewModel.submit(it, onAdded, onPartialAdded) } },
                enabled = instanceId != null && !state.isBusy && state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.add_submit))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
