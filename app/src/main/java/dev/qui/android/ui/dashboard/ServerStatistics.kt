/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.dashboard

data class ServerStatisticsRow(
    val instanceId: Int,
    val instanceName: String,
    val downloaded: Long,
    val downloadedSession: Long,
    val uploaded: Long,
    val uploadedSession: Long,
    val shareRatio: Double,
    val peerConnections: Long?,
)

data class ServerStatistics(
    val rows: List<ServerStatisticsRow>,
    val totalDownloaded: Long,
    val totalUploaded: Long,
    val shareRatio: Double,
    val totalPeerConnections: Long,
)

fun buildServerStatistics(cards: List<InstanceCard>): ServerStatistics? {
    val rows = cards.asSequence()
        .filter(InstanceCard::isHealthy)
        .mapNotNull { card ->
            val downloaded = card.allTimeDownloaded ?: 0L
            val uploaded = card.allTimeUploaded ?: 0L
            if (downloaded <= 0L && uploaded <= 0L) return@mapNotNull null

            ServerStatisticsRow(
                instanceId = card.instance.id,
                instanceName = card.instance.name,
                downloaded = downloaded,
                downloadedSession = card.sessionDownloaded,
                uploaded = uploaded,
                uploadedSession = card.sessionUploaded,
                shareRatio = ratio(uploaded, downloaded),
                peerConnections = card.peerConnections,
            )
        }
        .toList()

    val totalDownloaded = rows.sumOf(ServerStatisticsRow::downloaded)
    val totalUploaded = rows.sumOf(ServerStatisticsRow::uploaded)
    if (totalDownloaded == 0L && totalUploaded == 0L) return null

    return ServerStatistics(
        rows = rows,
        totalDownloaded = totalDownloaded,
        totalUploaded = totalUploaded,
        shareRatio = ratio(totalUploaded, totalDownloaded),
        totalPeerConnections = rows.sumOf { it.peerConnections ?: 0L },
    )
}

private fun ratio(uploaded: Long, downloaded: Long): Double =
    if (downloaded > 0L) uploaded.toDouble() / downloaded else 0.0
