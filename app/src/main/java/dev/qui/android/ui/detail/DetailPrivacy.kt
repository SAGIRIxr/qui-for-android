package dev.qui.android.ui.detail

import dev.qui.android.ui.torrents.*

/** Display-only copy. Never pass masked values to repository operations. */
internal fun DetailUiState.forDisplay(incognito: Boolean): DetailUiState {
    if (!incognito) return this
    val name = incognitoName(hash)
    val path = incognitoSavePath(hash)
    val hidden = "••••"
    return copy(
        hash = hidden,
        torrent = torrent?.copy(
            hash = hidden, name = name, category = incognitoCategory(hash),
            tags = incognitoTags(hash).joinToString(","), tracker = hidden,
            savePath = path, downloadPath = path, contentPath = path,
            magnetUri = hidden, infohashV1 = hidden, infohashV2 = hidden,
            instanceName = hidden, ratio = incognitoRatio(hash),
        ),
        properties = properties?.copy(
            hash = hidden, name = name, savePath = path, downloadPath = path,
            infohashV1 = hidden, infohashV2 = hidden, createdBy = hidden, comment = hidden,
            shareRatio = incognitoRatio(hash),
        ),
        trackers = trackers.map { it.copy(url = hidden, msg = if (it.msg.isEmpty()) "" else hidden) },
        peers = peers.map { it.copy(
            key = hidden, ip = hidden, port = null, client = hidden, peerIdClient = hidden,
            files = hidden, country = null, countryCode = null, flags = null, flagsDesc = null,
        ) },
        files = files.map { it.copy(name = incognitoFileName(hash, it.index)) },
        webSeeds = webSeeds.map { it.copy(url = hidden) },
        error = error?.let { hidden }, tabError = tabError?.let { hidden },
        actionError = actionError?.let { hidden },
    )
}
