/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data.model

import kotlinx.serialization.Serializable

/**
 * The filter blob qui accepts as the `filters` query parameter. Encoded as JSON
 * exactly like the web UI does, so the same server-side filtering applies.
 */
@Serializable
data class TorrentFilters(
    val status: List<String> = emptyList(),
    val excludeStatus: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val excludeCategories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val trackers: List<String> = emptyList(),
    val excludeTrackers: List<String> = emptyList(),
    val expr: String? = null,
) {
    val isEmpty: Boolean
        get() = status.isEmpty() && excludeStatus.isEmpty() &&
            categories.isEmpty() && excludeCategories.isEmpty() &&
            tags.isEmpty() && excludeTags.isEmpty() &&
            trackers.isEmpty() && excludeTrackers.isEmpty() &&
            expr.isNullOrBlank()

    val activeCount: Int
        get() = status.size + excludeStatus.size +
            categories.size + excludeCategories.size +
            tags.size + excludeTags.size +
            trackers.size + excludeTrackers.size
}

/** Tri-state filter chip, matching the include/exclude/neutral cycle in qui's sidebar. */
enum class FilterState { Neutral, Include, Exclude }

@Serializable
data class BulkActionRequest(
    val hashes: List<String> = emptyList(),
    val action: String = "",
    val deleteFiles: Boolean? = null,
    val category: String? = null,
    val tags: String? = null,
    val comment: String? = null,
    val enable: Boolean? = null,
    val selectAll: Boolean? = null,
    val filters: TorrentFilters? = null,
    val search: String? = null,
    val excludeHashes: List<String>? = null,
    val instanceIds: List<Int>? = null,
    val ratioLimit: Double? = null,
    val seedingTimeLimit: Long? = null,
    val inactiveSeedingTimeLimit: Long? = null,
    val shareLimitAction: String? = null,
    val shareLimitsMode: String? = null,
    val uploadLimit: Long? = null,
    val downloadLimit: Long? = null,
    val location: String? = null,
    val trackerOldURL: String? = null,
    val trackerNewURL: String? = null,
    val trackerURLs: String? = null,
    val targets: List<ActionTarget>? = null,
)

/** Cross-instance actions address torrents by (instance, hash) rather than hash alone. */
@Serializable
data class ActionTarget(val instanceId: Int, val hash: String)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val remember_me: Boolean = true,
)

@Serializable
data class SetupRequest(val username: String, val password: String)

@Serializable
data class CreateCategoryRequest(val name: String, val savePath: String = "")

@Serializable
data class CreateTagsRequest(val tags: List<String>)

@Serializable
data class DeleteTagsRequest(val tags: List<String>)

@Serializable
data class RemoveCategoriesRequest(val categories: List<String>)

@Serializable
data class RenameRequest(val name: String)

@Serializable
data class RenamePathRequest(val oldPath: String, val newPath: String)

@Serializable
data class FilePriorityRequest(val fileIndexes: List<Int>, val priority: Int)

@Serializable
data class TrackerUrlsRequest(val urls: String)

@Serializable
data class EditTrackerRequest(val oldURL: String, val newURL: String)

@Serializable
data class MessageResponse(val message: String = "")
