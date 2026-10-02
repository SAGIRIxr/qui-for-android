/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.torrents

import dev.qui.android.data.model.TorrentResponse
import dev.qui.android.data.remote.StreamPayload
import dev.qui.android.data.remote.StreamVersion
import java.io.IOException

/** One connection's baseline, kept separate from any REST fallback shown on screen. */
internal class StreamSnapshot {
    private var snapshot: TorrentResponse? = null
    private var version: StreamVersion? = null

    fun accept(payload: StreamPayload, delta: Boolean): TorrentResponse {
        val frame = payload.data ?: throw IOException("Missing torrent stream data")
        val nextVersion = payload.version
        if (nextVersion != null && !nextVersion.isValid) throw IOException("Invalid stream version")
        val next = if (!delta) {
            frame
        } else {
            val previous = snapshot ?: throw IOException("Missing torrent stream baseline")
            val baseVersion = payload.delta?.baseVersion
            // Pre-1.29 servers omit both versions. Once a version is present, require
            // an exact predecessor and a single increment within the same generation.
            if (version != null || nextVersion != null || baseVersion != null) {
                if (baseVersion == null || !baseVersion.isValid || baseVersion != version ||
                    nextVersion == null || nextVersion.major != baseVersion.major ||
                    baseVersion.minor == Long.MAX_VALUE || nextVersion.minor != baseVersion.minor + 1
                ) throw IOException("Torrent stream baseline changed")
            }
            val rowsByKey = previous.rows.associateByTo(LinkedHashMap()) { it.key }
            frame.rows.forEach { rowsByKey[it.key] = it }
            val keys = payload.delta?.order ?: previous.rows.map { it.key }
            val rows = keys.map { rowsByKey[it] ?: throw IOException("Missing torrent stream row") }
            val unified = previous.crossInstanceTorrents != null || frame.crossInstanceTorrents != null ||
                previous.isCrossInstance == true || frame.isCrossInstance == true
            frame.copy(
                torrents = if (unified) emptyList() else rows,
                crossInstanceTorrents = if (unified) rows else null,
                counts = frame.counts ?: previous.counts,
            )
        }
        // qui sends categories/tags in full; omission means the collection is empty.
        return next.copy(categories = next.categories.orEmpty(), tags = next.tags.orEmpty()).also {
            snapshot = it
            version = nextVersion
        }
    }
}
