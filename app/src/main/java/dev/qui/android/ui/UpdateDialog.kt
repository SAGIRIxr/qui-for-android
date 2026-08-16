/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * The "there is a newer build" dialog. It links to the release page rather than
 * installing anything: an app that side-loads its own updates is something users
 * should opt into deliberately.
 */

package dev.qui.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qui.android.R
import dev.qui.android.data.UpdateStatus
import dev.qui.android.data.localizedReleaseNotes
import dev.qui.android.ui.theme.QuiTheme

@Composable
fun UpdateDialog(
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    val palette = QuiTheme.palette
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.popover,
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.update_from_to,
                        status.appVersion,
                        status.appLatest.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.mutedForeground,
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.update_notes),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.mutedForeground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // The body is GitHub markdown; rendering it properly would mean
                    // pulling in a markdown engine for one dialog, so it is shown as
                    // written. Release notes here are plain bullet lists anyway.
                    text = localizedReleaseNotes(
                        status.appReleaseNotes,
                        stringResource(R.string.release_notes_heading),
                    ) ?: stringResource(R.string.update_no_notes),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.foreground,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    status.appReleaseUrl?.let {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.update_open))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.update_skip),
                        color = palette.mutedForeground,
                    )
                }
            }
        },
    )
}
