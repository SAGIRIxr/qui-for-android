/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's share-limit dialog. qBittorrent encodes each limit in one number:
 * -2 means "follow the global setting", -1 means unlimited, and anything >= 0 is the
 * limit itself — so each field is a three-way choice plus a value.
 */

package dev.qui.android.ui.torrents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.qui.android.R
import dev.qui.android.ui.theme.QuiTheme

/** The three states qBittorrent packs into a single limit field. */
private enum class LimitMode { Global, Unlimited, Custom }

private const val GLOBAL = -2.0
private const val UNLIMITED = -1.0

@Composable
fun ShareLimitDialog(
    onDismiss: () -> Unit,
    onConfirm: (ratio: Double, seedingMinutes: Long, inactiveMinutes: Long) -> Unit,
) {
    val palette = QuiTheme.palette

    var ratioMode by remember { mutableStateOf(LimitMode.Global) }
    var ratioValue by remember { mutableStateOf("") }
    var seedMode by remember { mutableStateOf(LimitMode.Global) }
    var seedValue by remember { mutableStateOf("") }
    var inactiveMode by remember { mutableStateOf(LimitMode.Global) }
    var inactiveValue by remember { mutableStateOf("") }

    // A custom field with nothing typed in it has no value to send.
    val ready = (ratioMode != LimitMode.Custom || ratioValue.toDoubleOrNull() != null) &&
        (seedMode != LimitMode.Custom || seedValue.toLongOrNull() != null) &&
        (inactiveMode != LimitMode.Custom || inactiveValue.toLongOrNull() != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_limits_action)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.share_limits_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                )

                LimitField(
                    label = stringResource(R.string.share_limit_ratio),
                    mode = ratioMode,
                    onModeChange = { ratioMode = it },
                    value = ratioValue,
                    onValueChange = { input ->
                        ratioValue = input.filter { it.isDigit() || it == '.' }
                    },
                    placeholder = stringResource(R.string.share_limit_ratio_placeholder),
                    help = stringResource(R.string.share_limit_ratio_help),
                    decimal = true,
                )
                LimitField(
                    label = stringResource(R.string.share_limit_seeding_time),
                    mode = seedMode,
                    onModeChange = { seedMode = it },
                    value = seedValue,
                    onValueChange = { input -> seedValue = input.filter(Char::isDigit) },
                    placeholder = stringResource(R.string.share_limit_seed_time_placeholder),
                    help = stringResource(R.string.share_limit_seed_time_help),
                )
                LimitField(
                    label = stringResource(R.string.share_limit_inactive),
                    mode = inactiveMode,
                    onModeChange = { inactiveMode = it },
                    value = inactiveValue,
                    onValueChange = { input -> inactiveValue = input.filter(Char::isDigit) },
                    placeholder = stringResource(R.string.share_limit_inactive_placeholder),
                    help = stringResource(R.string.share_limit_inactive_help),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    onConfirm(
                        resolve(ratioMode, ratioValue.toDoubleOrNull() ?: 0.0),
                        resolve(seedMode, (seedValue.toLongOrNull() ?: 0L).toDouble()).toLong(),
                        resolve(inactiveMode, (inactiveValue.toLongOrNull() ?: 0L).toDouble()).toLong(),
                    )
                },
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun resolve(mode: LimitMode, custom: Double): Double = when (mode) {
    LimitMode.Global -> GLOBAL
    LimitMode.Unlimited -> UNLIMITED
    LimitMode.Custom -> custom
}

@Composable
private fun LimitField(
    label: String,
    mode: LimitMode,
    onModeChange: (LimitMode) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    help: String,
    decimal: Boolean = false,
) {
    val palette = QuiTheme.palette

    Spacer(Modifier.height(14.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            LimitMode.Global to R.string.share_limit_use_global,
            LimitMode.Unlimited to R.string.share_limit_unlimited,
            LimitMode.Custom to R.string.share_limit_custom,
        ).forEach { (option, text) ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                label = {
                    Text(stringResource(text), style = MaterialTheme.typography.labelMedium)
                },
            )
        }
    }
    if (mode == LimitMode.Custom) {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = help,
            style = MaterialTheme.typography.labelSmall,
            color = palette.mutedForeground,
        )
    }
}
