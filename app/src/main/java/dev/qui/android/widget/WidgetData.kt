/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * One fetch shared by every widget. Both providers and the list widget's
 * RemoteViewsFactory read the same snapshot, so a single refresh redraws all of
 * them from consistent numbers instead of each hitting the server on its own.
 */

package dev.qui.android.widget

import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.model.TorrentFilters
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A single torrent row on the list widget. */
data class WidgetRow(
    val instanceId: Int,
    val hash: String,
    val name: String,
    val state: String,
    val progress: Int,
    val dlspeed: Long,
    val upspeed: Long,
    val eta: Long,
    val size: Long,
)

/** Everything the widgets draw, gathered in one pass. */
data class WidgetSnapshot(
    val signedIn: Boolean = true,
    /** Set when the server could not be reached; the widgets show it in place of numbers. */
    val error: String? = null,
    /** Client name, or null when more than one client is being summed. */
    val instanceName: String? = null,
    val instanceCount: Int = 0,
    val download: Long = 0,
    val upload: Long = 0,
    val total: Int = 0,
    val downloading: Int = 0,
    val seeding: Int = 0,
    val stopped: Int = 0,
    val errored: Int = 0,
    val totalSize: Long = 0,
    /** Smallest free space across clients — the one that runs out first. */
    val freeSpace: Long? = null,
    val rows: List<WidgetRow> = emptyList(),
    /** Follows the app's own setting so the widget and the list read the same. */
    val speedUnit: SpeedUnit = SpeedUnit.Bytes,
    /** These numbers outlived a failed refresh; updatedAt says how old they are. */
    val stale: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasData: Boolean get() = signedIn && error == null
}

@Singleton
class WidgetDataSource @Inject constructor(
    private val repository: QuiRepository,
    private val session: SessionStore,
    private val prefs: AppPreferencesStore,
) {
    private val mutex = Mutex()

    /**
     * The last successful fetch. The list widget's factory runs on a binder thread
     * moments after the provider redraws, and reusing the snapshot keeps it from
     * firing a second round of requests for the same numbers.
     */
    @Volatile
    var cached: WidgetSnapshot? = null
        private set

    /** Serialised so several widgets updating at once still make one round trip. */
    suspend fun load(force: Boolean = false): WidgetSnapshot = mutex.withLock {
        val previous = cached
        if (!force && previous != null &&
            System.currentTimeMillis() - previous.updatedAt < REUSE_WINDOW_MS
        ) {
            return previous
        }

        val fresh = fetch()
        // A failed refresh should not blank a widget that was showing real numbers a
        // moment ago; keep the last reading and mark it as aged instead.
        val next = if (fresh.error != null && previous != null && previous.hasData) {
            previous.copy(stale = true)
        } else {
            fresh
        }
        cached = next
        return next
    }

    private suspend fun fetch(): WidgetSnapshot {
        if (session.isConfigured.first() != true) {
            return WidgetSnapshot(signedIn = false)
        }

        val speedUnit = prefs.snapshot.first().speedUnit
        val instances = repository.instances().getOrNull()?.filter { it.isActive }
            ?: return WidgetSnapshot(error = ERROR_UNREACHABLE, speedUnit = speedUnit)
        if (instances.isEmpty()) {
            return WidgetSnapshot(error = ERROR_NO_CLIENTS, speedUnit = speedUnit)
        }

        var download = 0L
        var upload = 0L
        var total = 0
        var downloading = 0
        var seeding = 0
        var stopped = 0
        var errored = 0
        var totalSize = 0L
        var freeSpace: Long? = null
        var reached = 0

        instances.forEach { instance ->
            // limit=1 still carries the full stats/counts blocks, so this is the
            // cheapest call that answers every tile on the widget.
            val response = repository.torrents(
                instanceId = instance.id,
                page = 0,
                limit = 1,
                sort = "added_on",
                order = "desc",
                search = null,
                filters = null,
            ).getOrNull() ?: return@forEach

            reached++
            download += response.serverState?.dlInfoSpeed
                ?: response.stats?.totalDownloadSpeed ?: 0
            upload += response.serverState?.upInfoSpeed
                ?: response.stats?.totalUploadSpeed ?: 0
            total += response.total
            totalSize += response.stats?.totalSize ?: 0

            val status = response.counts?.status.orEmpty()
            downloading += status["downloading"] ?: 0
            seeding += status["uploading"] ?: 0
            stopped += status["stopped"] ?: 0
            errored += status["errored"] ?: 0

            response.serverState?.freeSpaceOnDisk?.takeIf { it > 0 }?.let { space ->
                freeSpace = freeSpace?.let { minOf(it, space) } ?: space
            }
        }

        if (reached == 0) return WidgetSnapshot(error = ERROR_UNREACHABLE, speedUnit = speedUnit)

        return WidgetSnapshot(
            instanceName = instances.singleOrNull()?.name,
            instanceCount = instances.size,
            download = download,
            upload = upload,
            total = total,
            downloading = downloading,
            seeding = seeding,
            stopped = stopped,
            errored = errored,
            totalSize = totalSize,
            freeSpace = freeSpace,
            rows = fetchRows(instances.map { it.id }),
            speedUnit = speedUnit,
        )
    }

    /**
     * Transferring torrents first — that is what someone glances at a widget for.
     * With nothing moving the list would be empty and useless, so it falls back to
     * the most recently added.
     */
    private suspend fun fetchRows(instanceIds: List<Int>): List<WidgetRow> {
        val active = query(instanceIds, TorrentFilters(status = listOf("active")), "dlspeed")
        val rows = if (active.isEmpty()) {
            query(instanceIds, null, "added_on")
        } else {
            active
        }
        return rows.take(ROW_LIMIT)
    }

    private suspend fun query(
        instanceIds: List<Int>,
        filters: TorrentFilters?,
        sort: String,
    ): List<WidgetRow> {
        val response = if (instanceIds.size > 1) {
            repository.crossInstanceTorrents(
                instanceIds = instanceIds,
                page = 0,
                limit = ROW_LIMIT,
                sort = sort,
                order = "desc",
                search = null,
                filters = filters,
            )
        } else {
            repository.torrents(
                instanceId = instanceIds.first(),
                page = 0,
                limit = ROW_LIMIT,
                sort = sort,
                order = "desc",
                search = null,
                filters = filters,
            )
        }.getOrNull() ?: return emptyList()

        return response.rows.map { torrent ->
            WidgetRow(
                instanceId = torrent.instanceId ?: instanceIds.first(),
                hash = torrent.hash,
                name = torrent.name,
                state = torrent.state,
                progress = (torrent.progress * 100).toInt().coerceIn(0, 100),
                dlspeed = torrent.dlspeed,
                upspeed = torrent.upspeed,
                eta = torrent.eta,
                size = torrent.size,
            )
        }
    }

    companion object {
        const val ERROR_UNREACHABLE = "unreachable"
        const val ERROR_NO_CLIENTS = "no_clients"
        private const val ROW_LIMIT = 12
        private const val REUSE_WINDOW_MS = 5_000L
    }
}
