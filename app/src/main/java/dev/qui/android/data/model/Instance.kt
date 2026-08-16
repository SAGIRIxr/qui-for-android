/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Mirrors autobrr/qui's web/src/types/instances.ts and types/app.ts.
 */

package dev.qui.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Instance(
    val id: Int = 0,
    val name: String = "",
    val host: String = "",
    val username: String = "",
    val hasApiKey: Boolean? = null,
    val basicUsername: String? = null,
    val tlsSkipVerify: Boolean = false,
    val hasLocalFilesystemAccess: Boolean = false,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val connected: Boolean = false,
    val hasDecryptionError: Boolean = false,
    val connectionStatus: String? = null,
    val recentErrors: List<InstanceError>? = null,
)

@Serializable
data class InstanceError(
    val id: Int = 0,
    val instanceId: Int = 0,
    val errorType: String = "",
    val errorMessage: String = "",
    val occurredAt: String = "",
)

@Serializable
data class InstanceCapabilities(
    val supportsTorrentCreation: Boolean = false,
    val supportsTorrentExport: Boolean = false,
    val supportsSetTags: Boolean = false,
    val supportsSetComment: Boolean = false,
    val supportsTrackerHealth: Boolean = false,
    val supportsTrackerEditing: Boolean = false,
    val supportsRenameTorrent: Boolean = false,
    val supportsRenameFile: Boolean = false,
    val supportsRenameFolder: Boolean = false,
    val supportsFilePriority: Boolean = false,
    val supportsSubcategories: Boolean = false,
    val subcategoriesAlwaysEnabled: Boolean = false,
    val supportsTorrentTmpPath: Boolean = false,
    val supportsPathAutocomplete: Boolean = false,
    val supportsFreeSpacePathSource: Boolean = false,
    val supportsSetRSSFeedURL: Boolean = false,
    val supportsShareLimitsAction: Boolean = false,
    val supportsShareLimitsMode: Boolean? = null,
    val webAPIVersion: String? = null,
)

@Serializable
data class AppPreferences(
    @SerialName("dl_limit") val dlLimit: Long? = null,
    @SerialName("up_limit") val upLimit: Long? = null,
    @SerialName("alt_dl_limit") val altDlLimit: Long? = null,
    @SerialName("alt_up_limit") val altUpLimit: Long? = null,
    @SerialName("save_path") val savePath: String? = null,
    @SerialName("temp_path") val tempPath: String? = null,
    @SerialName("temp_path_enabled") val tempPathEnabled: Boolean? = null,
    @SerialName("auto_tmm_enabled") val autoTmmEnabled: Boolean? = null,
    @SerialName("max_connec") val maxConnections: Int? = null,
    @SerialName("max_active_downloads") val maxActiveDownloads: Int? = null,
    @SerialName("max_active_uploads") val maxActiveUploads: Int? = null,
    @SerialName("max_active_torrents") val maxActiveTorrents: Int? = null,
    @SerialName("queueing_enabled") val queueingEnabled: Boolean? = null,
    @SerialName("dht") val dht: Boolean? = null,
    @SerialName("pex") val pex: Boolean? = null,
    @SerialName("lsd") val lsd: Boolean? = null,
    @SerialName("listen_port") val listenPort: Int? = null,
    @SerialName("start_paused_enabled") val startPausedEnabled: Boolean? = null,
    @SerialName("use_subcategories") val useSubcategories: Boolean? = null,
)

@Serializable
data class QBittorrentAppInfo(
    val version: String? = null,
    val webAPIVersion: String? = null,
)

@Serializable
data class AuthUser(
    val id: Int? = null,
    val username: String = "",
    @SerialName("auth_method") val authMethod: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

@Serializable
data class AuthResponse(
    val username: String? = null,
    val message: String? = null,
)

@Serializable
data class SetupCheckResponse(val setupRequired: Boolean = false)

@Serializable
data class VersionInfo(
    val version: String? = null,
    val commit: String? = null,
    val date: String? = null,
)

@Serializable
data class LatestVersion(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)
