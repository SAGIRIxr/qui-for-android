/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Ports of qui's torrent-state-utils.ts labels, TorrentCardsMobile's badge variants,
 * and the sort/filter option lists from torrentSortOptions.ts and FilterSidebar.tsx.
 */

package dev.qui.android.ui.torrents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.qui.android.data.model.Torrent
import dev.qui.android.ui.components.BadgeVariant
import dev.qui.android.ui.theme.QuiTheme

/** Human-readable state labels, matching TORRENT_STATE_LABELS in qui. */
private val STATE_LABELS = mapOf(
    "downloading" to "Downloading",
    "metaDL" to "Fetching Metadata",
    "allocating" to "Allocating",
    "stalledDL" to "Stalled",
    "queuedDL" to "Queued",
    "checkingDL" to "Checking",
    "forcedDL" to "(F) Downloading",
    "uploading" to "Seeding",
    "stalledUP" to "Seeding",
    "queuedUP" to "Queued",
    "checkingUP" to "Checking",
    "forcedUP" to "(F) Seeding",
    "pausedDL" to "Paused",
    "pausedUP" to "Completed",
    "stoppedDL" to "Stopped",
    "stoppedUP" to "Completed",
    "error" to "Error",
    "missingFiles" to "Missing Files",
    "checkingResumeData" to "Checking Resume Data",
    "moving" to "Moving",
)

fun stateLabel(state: String): String = STATE_LABELS[state] ?: state

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
                label = "Tracker Down",
                variant = BadgeVariant.Outline,
                overrideColor = Color(0xFFEAB308),
            )
            "tracker_error" -> return StatusBadge(
                label = "Tracker Error",
                variant = BadgeVariant.Outline,
                overrideColor = Color(0xFFF97316),
            )
            "unregistered" -> return StatusBadge(
                label = "Unregistered",
                variant = BadgeVariant.Outline,
                overrideColor = palette.destructive,
            )
        }
    }

    return StatusBadge(stateLabel(torrent.state), stateVariant(torrent.state))
}

data class SortOption(val value: String, val label: String)

/** TORRENT_SORT_OPTIONS from qui, in the same order the web UI lists them. */
val TORRENT_SORT_OPTIONS = listOf(
    SortOption("added_on", "Recently Added"),
    SortOption("name", "Name"),
    SortOption("size", "Size"),
    SortOption("progress", "Progress"),
    SortOption("state", "Status"),
    SortOption("priority", "Priority"),
    SortOption("num_seeds", "Seeds"),
    SortOption("num_leechs", "Leechers"),
    SortOption("dlspeed", "Download Speed"),
    SortOption("upspeed", "Upload Speed"),
    SortOption("eta", "ETA"),
    SortOption("ratio", "Ratio"),
    SortOption("popularity", "Popularity"),
    SortOption("category", "Category"),
    SortOption("tags", "Tags"),
    SortOption("completion_on", "Completed On"),
    SortOption("tracker", "Tracker"),
    SortOption("dl_limit", "Download Limit"),
    SortOption("up_limit", "Upload Limit"),
    SortOption("downloaded", "Downloaded"),
    SortOption("uploaded", "Uploaded"),
    SortOption("downloaded_session", "Session Downloaded"),
    SortOption("uploaded_session", "Session Uploaded"),
    SortOption("amount_left", "Remaining"),
    SortOption("time_active", "Time Active"),
    SortOption("seeding_time", "Seeding Time"),
    SortOption("save_path", "Save Path"),
    SortOption("completed", "Completed"),
    SortOption("ratio_limit", "Ratio Limit"),
    SortOption("seen_complete", "Last Seen Complete"),
    SortOption("last_activity", "Last Activity"),
    SortOption("availability", "Availability"),
    SortOption("reannounce", "Reannounce In"),
    SortOption("private", "Private"),
)

/** String-ish columns sort ascending by default, everything else descending. */
private val ASC_BY_DEFAULT = setOf(
    "name", "state", "category", "tags", "tracker", "save_path", "infohash_v1", "infohash_v2",
)

fun defaultSortOrder(field: String): String =
    if (field in ASC_BY_DEFAULT) "asc" else "desc"

data class StatusFilterOption(val value: String, val label: String)

/** The status list from qui's FilterSidebar, minus the cross-seed entry (premium-gated). */
val STATUS_FILTER_OPTIONS = listOf(
    StatusFilterOption("downloading", "Downloading"),
    StatusFilterOption("uploading", "Seeding"),
    StatusFilterOption("completed", "Completed"),
    StatusFilterOption("stopped", "Stopped"),
    StatusFilterOption("active", "Active"),
    StatusFilterOption("inactive", "Inactive"),
    StatusFilterOption("running", "Running"),
    StatusFilterOption("stalled", "Stalled"),
    StatusFilterOption("stalled_uploading", "Stalled Uploading"),
    StatusFilterOption("stalled_downloading", "Stalled Downloading"),
    StatusFilterOption("errored", "Errored"),
    StatusFilterOption("checking", "Checking"),
    StatusFilterOption("moving", "Moving"),
    StatusFilterOption("unregistered", "Unregistered"),
    StatusFilterOption("tracker_down", "Tracker Down"),
    StatusFilterOption("tracker_error", "Tracker Error"),
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
