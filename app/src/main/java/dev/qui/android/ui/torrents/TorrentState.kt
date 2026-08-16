/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Ports of qui's torrent-state-utils.ts labels, TorrentCardsMobile's badge variants,
 * and the sort/filter option lists from torrentSortOptions.ts and FilterSidebar.tsx.
 *
 * Labels are resource ids rather than literals so the nine languages qui ships work
 * here too; the English values are copied from qui's own en locale.
 */

package dev.qui.android.ui.torrents

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.qui.android.R
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.components.BadgeVariant
import dev.qui.android.ui.theme.QuiTheme

/** Human-readable state labels, matching TORRENT_STATE_LABELS in qui. */
private val STATE_LABELS = mapOf(
    "downloading" to R.string.state_downloading,
    "metaDL" to R.string.state_metaDL,
    "allocating" to R.string.state_allocating,
    "stalledDL" to R.string.state_stalledDL,
    "queuedDL" to R.string.state_queuedDL,
    "checkingDL" to R.string.state_checkingDL,
    "forcedDL" to R.string.state_forcedDL,
    "uploading" to R.string.state_uploading,
    "stalledUP" to R.string.state_stalledUP,
    "queuedUP" to R.string.state_queuedUP,
    "checkingUP" to R.string.state_checkingUP,
    "forcedUP" to R.string.state_forcedUP,
    "pausedDL" to R.string.state_pausedDL,
    "pausedUP" to R.string.state_pausedUP,
    "stoppedDL" to R.string.state_stoppedDL,
    "stoppedUP" to R.string.state_stoppedUP,
    "error" to R.string.state_error,
    "missingFiles" to R.string.state_missingFiles,
    "checkingResumeData" to R.string.state_checkingResumeData,
    "moving" to R.string.state_moving,
)

/** Unknown states fall through as the raw qBittorrent value, as they do in qui. */
@Composable
@ReadOnlyComposable
fun stateLabel(state: String): String =
    STATE_LABELS[state]?.let { stringResource(it) } ?: state

/** Matches getStatusBadgeVariant in TorrentCardsMobile.tsx. */
private fun stateVariant(state: String): BadgeVariant = when (state) {
    "downloading", "uploading" -> BadgeVariant.Default
    "stalledDL", "stalledUP", "pausedDL", "pausedUP" -> BadgeVariant.Secondary
    "error", "missingFiles" -> BadgeVariant.Destructive
    else -> BadgeVariant.Outline
}

data class StatusBadge(
    val label: String,
    val variant: BadgeVariant,
    val overrideColor: Color? = null,
)

/**
 * Matches getStatusBadgeProps: tracker health, when the instance reports it, wins over
 * the plain torrent state and recolours the badge.
 */
@Composable
@ReadOnlyComposable
fun statusBadgeFor(torrent: Torrent, supportsTrackerHealth: Boolean): StatusBadge {
    val palette = QuiTheme.palette

    if (supportsTrackerHealth) {
        when (torrent.trackerHealth) {
            "tracker_down" -> return StatusBadge(
                label = stringResource(R.string.tracker_health_down),
                variant = BadgeVariant.Outline,
                overrideColor = Color(0xFFEAB308),
            )
            "tracker_error" -> return StatusBadge(
                label = stringResource(R.string.tracker_health_error),
                variant = BadgeVariant.Outline,
                overrideColor = Color(0xFFF97316),
            )
            "unregistered" -> return StatusBadge(
                label = stringResource(R.string.tracker_health_unregistered),
                variant = BadgeVariant.Outline,
                overrideColor = palette.destructive,
            )
        }
    }

    return StatusBadge(stateLabel(torrent.state), stateVariant(torrent.state))
}

data class SortOption(val value: String, @StringRes val label: Int)

/** TORRENT_SORT_OPTIONS from qui, in the same order the web UI lists them. */
val TORRENT_SORT_OPTIONS = listOf(
    SortOption("added_on", R.string.sort_added_on),
    SortOption("name", R.string.sort_name),
    SortOption("size", R.string.sort_size),
    SortOption("progress", R.string.sort_progress),
    SortOption("state", R.string.sort_state),
    SortOption("priority", R.string.sort_priority),
    SortOption("num_seeds", R.string.sort_num_seeds),
    SortOption("num_leechs", R.string.sort_num_leechs),
    SortOption("dlspeed", R.string.sort_dlspeed),
    SortOption("upspeed", R.string.sort_upspeed),
    SortOption("eta", R.string.sort_eta),
    SortOption("ratio", R.string.sort_ratio),
    SortOption("popularity", R.string.sort_popularity),
    SortOption("category", R.string.sort_category),
    SortOption("tags", R.string.sort_tags),
    SortOption("completion_on", R.string.sort_completion_on),
    SortOption("tracker", R.string.sort_tracker),
    SortOption("dl_limit", R.string.sort_dl_limit),
    SortOption("up_limit", R.string.sort_up_limit),
    SortOption("downloaded", R.string.sort_downloaded),
    SortOption("uploaded", R.string.sort_uploaded),
    SortOption("downloaded_session", R.string.sort_downloaded_session),
    SortOption("uploaded_session", R.string.sort_uploaded_session),
    SortOption("amount_left", R.string.sort_amount_left),
    SortOption("time_active", R.string.sort_time_active),
    SortOption("seeding_time", R.string.sort_seeding_time),
    SortOption("save_path", R.string.sort_save_path),
    SortOption("completed", R.string.sort_completed),
    SortOption("ratio_limit", R.string.sort_ratio_limit),
    SortOption("seen_complete", R.string.sort_seen_complete),
    SortOption("last_activity", R.string.sort_last_activity),
    SortOption("availability", R.string.sort_availability),
    SortOption("reannounce", R.string.sort_reannounce),
    SortOption("private", R.string.sort_private),
)

@StringRes
fun sortLabelFor(field: String): Int =
    TORRENT_SORT_OPTIONS.firstOrNull { it.value == field }?.label ?: R.string.sort_added_on

/** String-ish columns sort ascending by default, everything else descending. */
private val ASC_BY_DEFAULT = setOf(
    "name", "state", "category", "tags", "tracker", "save_path", "infohash_v1", "infohash_v2",
)

fun defaultSortOrder(field: String): String =
    if (field in ASC_BY_DEFAULT) "asc" else "desc"

data class StatusFilterOption(val value: String, @StringRes val label: Int)

/** The status list from qui's FilterSidebar, minus the cross-seed entry (premium-gated). */
val STATUS_FILTER_OPTIONS = listOf(
    StatusFilterOption("downloading", R.string.status_downloading),
    StatusFilterOption("uploading", R.string.status_uploading),
    StatusFilterOption("completed", R.string.status_completed),
    StatusFilterOption("stopped", R.string.status_stopped),
    StatusFilterOption("active", R.string.status_active),
    StatusFilterOption("inactive", R.string.status_inactive),
    StatusFilterOption("running", R.string.status_running),
    StatusFilterOption("stalled", R.string.status_stalled),
    StatusFilterOption("stalled_uploading", R.string.status_stalled_uploading),
    StatusFilterOption("stalled_downloading", R.string.status_stalled_downloading),
    StatusFilterOption("errored", R.string.status_errored),
    StatusFilterOption("checking", R.string.status_checking),
    StatusFilterOption("moving", R.string.status_moving),
    StatusFilterOption("unregistered", R.string.status_unregistered),
    StatusFilterOption("tracker_down", R.string.status_tracker_down),
    StatusFilterOption("tracker_error", R.string.status_tracker_error),
)

/**
 * qui derives the tracker filter key from the announce URL's host, stripping a leading
 * "www.". Torrents with no tracker fall into an "Unknown" bucket.
 */
fun trackerHost(tracker: String): String {
    if (tracker.isBlank()) return ""
    return runCatching {
        val host = java.net.URI(tracker).host ?: return ""
        host.removePrefix("www.")
    }.getOrDefault("")
}

/** Short label for a tracker chip: the registrable-looking part of the host. */
fun trackerShortName(tracker: String): String {
    val host = trackerHost(tracker)
    if (host.isEmpty()) return ""
    val parts = host.split('.')
    return if (parts.size >= 2) parts[parts.size - 2] else host
}
