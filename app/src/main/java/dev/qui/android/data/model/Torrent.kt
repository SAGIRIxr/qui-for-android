/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Field names mirror autobrr/qui's web/src/types/torrents.ts so the same JSON
 * the web UI consumes decodes here unchanged.
 */

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.qui.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class Torrent(
    val hash: String = "",
    val name: String = "",
    @SerialName("added_on") val addedOn: Long = 0,
    @SerialName("amount_left") val amountLeft: Long = 0,
    @SerialName("auto_tmm") val autoTmm: Boolean = false,
    val availability: Double = 0.0,
    val category: String = "",
    val completed: Long = 0,
    @SerialName("completion_on") val completionOn: Long = 0,
    @SerialName("content_path") val contentPath: String = "",
    @SerialName("dl_limit") val dlLimit: Long = 0,
    val dlspeed: Long = 0,
    @SerialName("download_path") val downloadPath: String = "",
    val downloaded: Long = 0,
    @SerialName("downloaded_session") val downloadedSession: Long = 0,
    val eta: Long = 0,
    @SerialName("f_l_piece_prio") val firstLastPiecePrio: Boolean = false,
    @SerialName("force_start") val forceStart: Boolean = false,
    @SerialName("infohash_v1") val infohashV1: String = "",
    @SerialName("infohash_v2") val infohashV2: String = "",
    val popularity: Double = 0.0,
    val private: Boolean = false,
    @SerialName("last_activity") val lastActivity: Long = 0,
    @SerialName("magnet_uri") val magnetUri: String = "",
    @SerialName("max_ratio") val maxRatio: Double = 0.0,
    @SerialName("max_seeding_time") val maxSeedingTime: Long = 0,
    @SerialName("max_inactive_seeding_time") val maxInactiveSeedingTime: Long? = null,
    @SerialName("num_complete") val numComplete: Int = 0,
    @SerialName("num_incomplete") val numIncomplete: Int = 0,
    @SerialName("num_leechs") val numLeechs: Int = 0,
    @SerialName("num_seeds") val numSeeds: Int = 0,
    val priority: Int = 0,
    val progress: Double = 0.0,
    val ratio: Double = 0.0,
    @SerialName("ratio_limit") val ratioLimit: Double = 0.0,
    val reannounce: Long = 0,
    @SerialName("save_path") val savePath: String = "",
    @SerialName("seeding_time") val seedingTime: Long = 0,
    @SerialName("seeding_time_limit") val seedingTimeLimit: Long = 0,
    @SerialName("inactive_seeding_time_limit") val inactiveSeedingTimeLimit: Long? = null,
    @SerialName("share_limit_action") val shareLimitAction: String? = null,
    @SerialName("share_limits_mode") val shareLimitsMode: String? = null,
    @SerialName("seen_complete") val seenComplete: Long = 0,
    @SerialName("seq_dl") val sequentialDownload: Boolean = false,
    val size: Long = 0,
    val state: String = "",
    @SerialName("super_seeding") val superSeeding: Boolean = false,
    val tags: String = "",
    @SerialName("time_active") val timeActive: Long = 0,
    @SerialName("total_size") val totalSize: Long = 0,
    val tracker: String = "",
    @SerialName("trackers_count") val trackersCount: Int = 0,
    @SerialName("tracker_health") val trackerHealth: String? = null,
    @SerialName("up_limit") val upLimit: Long = 0,
    val uploaded: Long = 0,
    @SerialName("uploaded_session") val uploadedSession: Long = 0,
    val upspeed: Long = 0,
    // Only present on the cross-instance endpoint, where qui's Go model serialises
    // them as snake_case over both REST and the stream. Its own web client accepts
    // either casing (web/src/lib/cross-instance-torrents.ts), so we do the same.
    @SerialName("instance_id")
    @JsonNames("instanceId")
    val instanceId: Int? = null,
    @SerialName("instance_name")
    @JsonNames("instanceName")
    val instanceName: String? = null,
) {
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Stable identity across single- and cross-instance streams, matching qui's stream keys. */
    val key: String
        get() = instanceId?.let { "$it:$hash" } ?: hash
}

@Serializable
data class TorrentTracker(
    val url: String = "",
    val status: Int = 0,
    val tier: Int = 0,
    @SerialName("num_peers") val numPeers: Int = 0,
    @SerialName("num_seeds") val numSeeds: Int = 0,
    @SerialName("num_leeches") val numLeeches: Int = 0,
    @SerialName("num_downloaded") val numDownloaded: Int = 0,
    val msg: String = "",
)

@Serializable
data class TorrentProperties(
    @SerialName("addition_date") val additionDate: Long = 0,
    val comment: String = "",
    @SerialName("completion_date") val completionDate: Long = 0,
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("creation_date") val creationDate: Long = 0,
    @SerialName("dl_limit") val dlLimit: Long = 0,
    @SerialName("dl_speed") val dlSpeed: Long = 0,
    @SerialName("dl_speed_avg") val dlSpeedAvg: Long = 0,
    @SerialName("download_path") val downloadPath: String = "",
    val eta: Long = 0,
    val hash: String = "",
    @SerialName("infohash_v1") val infohashV1: String = "",
    @SerialName("infohash_v2") val infohashV2: String = "",
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("last_seen") val lastSeen: Long = 0,
    val name: String = "",
    @SerialName("nb_connections") val nbConnections: Int = 0,
    @SerialName("nb_connections_limit") val nbConnectionsLimit: Int = 0,
    val peers: Int = 0,
    @SerialName("peers_total") val peersTotal: Int = 0,
    @SerialName("piece_size") val pieceSize: Long = 0,
    @SerialName("pieces_have") val piecesHave: Int = 0,
    @SerialName("pieces_num") val piecesNum: Int = 0,
    val reannounce: Long = 0,
    @SerialName("save_path") val savePath: String = "",
    @SerialName("seeding_time") val seedingTime: Long = 0,
    val seeds: Int = 0,
    @SerialName("seeds_total") val seedsTotal: Int = 0,
    @SerialName("share_ratio") val shareRatio: Double = 0.0,
    @SerialName("time_elapsed") val timeElapsed: Long = 0,
    @SerialName("total_downloaded") val totalDownloaded: Long = 0,
    @SerialName("total_downloaded_session") val totalDownloadedSession: Long = 0,
    @SerialName("total_size") val totalSize: Long = 0,
    @SerialName("total_uploaded") val totalUploaded: Long = 0,
    @SerialName("total_uploaded_session") val totalUploadedSession: Long = 0,
    @SerialName("total_wasted") val totalWasted: Long = 0,
    @SerialName("up_limit") val upLimit: Long = 0,
    @SerialName("up_speed") val upSpeed: Long = 0,
    @SerialName("up_speed_avg") val upSpeedAvg: Long = 0,
)

@Serializable
data class TorrentFile(
    val index: Int = 0,
    val name: String = "",
    val size: Long = 0,
    val progress: Double = 0.0,
    val priority: Int = 0,
    val availability: Double = 0.0,
    @SerialName("is_seed") val isSeed: Boolean? = null,
    @SerialName("piece_range") val pieceRange: List<Int> = emptyList(),
)

@Serializable
data class TorrentPeer(
    val ip: String = "",
    val port: Int = 0,
    val connection: String? = null,
    val flags: String? = null,
    @SerialName("flags_desc") val flagsDesc: String? = null,
    val client: String? = null,
    val progress: Double = 0.0,
    @SerialName("dl_speed") val dlSpeed: Long? = null,
    @SerialName("up_speed") val upSpeed: Long? = null,
    val downloaded: Long? = null,
    val uploaded: Long? = null,
    val relevance: Double? = null,
    val files: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("peer_id_client") val peerIdClient: String? = null,
    /** Present only on the pre-sorted list qui returns. */
    val key: String? = null,
)

@Serializable
data class TorrentPeersResponse(
    val peers: Map<String, TorrentPeer>? = null,
    @SerialName("sorted_peers") val sortedPeers: List<TorrentPeer>? = null,
    @SerialName("peers_removed") val peersRemoved: List<String>? = null,
    val rid: Long = 0,
    @SerialName("full_update") val fullUpdate: Boolean = false,
    @SerialName("show_flags") val showFlags: Boolean? = null,
)

@Serializable
data class WebSeed(val url: String = "")

@Serializable
data class Category(
    val name: String = "",
    val savePath: String = "",
)

@Serializable
data class TorrentStats(
    val total: Int = 0,
    val downloading: Int = 0,
    val seeding: Int = 0,
    val paused: Int = 0,
    val error: Int = 0,
    val totalDownloadSpeed: Long? = null,
    val totalUploadSpeed: Long? = null,
    val totalDownloadData: Long? = null,
    val totalUploadData: Long? = null,
    val totalSize: Long? = null,
    val totalRemainingSize: Long? = null,
    val totalSeedingSize: Long? = null,
)

@Serializable
data class TrackerTransferStats(
    val uploaded: Long = 0,
    val downloaded: Long = 0,
    val uploadedSession: Long = 0,
    val downloadedSession: Long = 0,
    val totalSize: Long = 0,
    val count: Int = 0,
)

@Serializable
data class TorrentCounts(
    val status: Map<String, Int> = emptyMap(),
    val categories: Map<String, Int> = emptyMap(),
    val categorySizes: Map<String, Long> = emptyMap(),
    val tags: Map<String, Int> = emptyMap(),
    val tagSizes: Map<String, Long> = emptyMap(),
    val trackers: Map<String, Int> = emptyMap(),
    val trackerTransfers: Map<String, TrackerTransferStats> = emptyMap(),
    val total: Int = 0,
)

@Serializable
data class ServerState(
    @SerialName("connection_status") val connectionStatus: String = "",
    @SerialName("dht_nodes") val dhtNodes: Long = 0,
    @SerialName("dl_info_data") val dlInfoData: Long = 0,
    @SerialName("dl_info_speed") val dlInfoSpeed: Long = 0,
    @SerialName("dl_rate_limit") val dlRateLimit: Long = 0,
    @SerialName("up_info_data") val upInfoData: Long = 0,
    @SerialName("up_info_speed") val upInfoSpeed: Long = 0,
    @SerialName("up_rate_limit") val upRateLimit: Long = 0,
    val queueing: Boolean = false,
    @SerialName("use_alt_speed_limits") val useAltSpeedLimits: Boolean = false,
    @SerialName("use_subcategories") val useSubcategories: Boolean? = null,
    @SerialName("refresh_interval") val refreshInterval: Long = 0,
    @SerialName("alltime_dl") val alltimeDl: Long? = null,
    @SerialName("alltime_ul") val alltimeUl: Long? = null,
    @SerialName("total_wasted_session") val totalWastedSession: Long? = null,
    @SerialName("global_ratio") val globalRatio: String? = null,
    @SerialName("total_peer_connections") val totalPeerConnections: Long? = null,
    @SerialName("free_space_on_disk") val freeSpaceOnDisk: Long? = null,
    @SerialName("last_external_address_v4") val lastExternalAddressV4: String? = null,
    @SerialName("last_external_address_v6") val lastExternalAddressV6: String? = null,
)

@Serializable
data class TransferInfo(
    @SerialName("connection_status") val connectionStatus: String = "",
    @SerialName("dht_nodes") val dhtNodes: Long = 0,
    @SerialName("dl_info_data") val dlInfoData: Long = 0,
    @SerialName("dl_info_speed") val dlInfoSpeed: Long = 0,
    @SerialName("dl_rate_limit") val dlRateLimit: Long = 0,
    @SerialName("up_info_data") val upInfoData: Long = 0,
    @SerialName("up_info_speed") val upInfoSpeed: Long = 0,
    @SerialName("up_rate_limit") val upRateLimit: Long = 0,
)

@Serializable
data class InstanceMeta(
    val connected: Boolean = false,
    val hasDecryptionError: Boolean = false,
    val connectionStatus: String? = null,
)

@Serializable
data class TorrentResponse(
    val torrents: List<Torrent> = emptyList(),
    @SerialName("cross_instance_torrents") val crossInstanceTorrents: List<Torrent>? = null,
    val total: Int = 0,
    val stats: TorrentStats? = null,
    val counts: TorrentCounts? = null,
    val categories: Map<String, Category>? = null,
    val tags: List<String>? = null,
    val serverState: ServerState? = null,
    val useSubcategories: Boolean? = null,
    val hasMore: Boolean? = null,
    val trackerHealthSupported: Boolean? = null,
    val isCrossInstance: Boolean? = null,
    /** Set when a merged response is missing a client that failed to answer. */
    val partialResults: Boolean = false,
    val instanceMeta: InstanceMeta? = null,
) {
    /** qui returns cross-instance rows under a separate key; callers want one list. */
    val rows: List<Torrent>
        get() = crossInstanceTorrents?.takeIf { it.isNotEmpty() } ?: torrents
}

@Serializable
data class AddTorrentFailedUrl(val url: String = "", val error: String = "")

@Serializable
data class AddTorrentFailedFile(val filename: String = "", val error: String = "")

@Serializable
data class AddTorrentResponse(
    val message: String = "",
    val added: Int = 0,
    val failed: Int = 0,
    val failedURLs: List<AddTorrentFailedUrl>? = null,
    val failedFiles: List<AddTorrentFailedFile>? = null,
)
