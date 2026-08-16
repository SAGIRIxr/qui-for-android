/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data

import dev.qui.android.data.model.ActionTarget
import dev.qui.android.data.model.AddTorrentResponse
import dev.qui.android.data.model.AppPreferences
import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.model.Category
import dev.qui.android.data.model.CreateCategoryRequest
import dev.qui.android.data.model.CreateTagsRequest
import dev.qui.android.data.model.DeleteTagsRequest
import dev.qui.android.data.model.EditTrackerRequest
import dev.qui.android.data.model.FilePriorityRequest
import dev.qui.android.data.model.Instance
import dev.qui.android.data.model.InstanceCapabilities
import dev.qui.android.data.model.LoginRequest
import dev.qui.android.data.model.RemoveCategoriesRequest
import dev.qui.android.data.model.RenamePathRequest
import dev.qui.android.data.model.RenameRequest
import dev.qui.android.data.model.SetupRequest
import dev.qui.android.data.model.TorrentFile
import dev.qui.android.data.model.TorrentFilters
import dev.qui.android.data.model.TorrentPeer
import dev.qui.android.data.model.TorrentProperties
import dev.qui.android.data.model.TorrentResponse
import dev.qui.android.data.model.TorrentTracker
import dev.qui.android.data.model.TrackerUrlsRequest
import dev.qui.android.data.model.TransferInfo
import dev.qui.android.data.model.WebSeed
import dev.qui.android.data.remote.QuiApiProvider
import dev.qui.android.data.remote.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuiRepository @Inject constructor(
    private val apiProvider: QuiApiProvider,
    private val session: SessionStore,
    private val json: Json,
) {

    // ---- auth ----

    /**
     * Verifies the address and credentials before storing them, so a typo never
     * leaves the app in a half-configured state.
     */
    suspend fun loginWithPassword(
        serverUrl: String,
        username: String,
        password: String,
        trustAllCerts: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            session.setServerUrl(serverUrl, trustAllCerts)
            apiProvider.invalidate()

            val api = apiProvider.apiFor(serverUrl)
            api.login(LoginRequest(username, password, remember_me = true))

            session.setUsername(username)
            apiProvider.invalidate()
            Unit
        }
    }.onFailure { session.clearCredentials() }

    suspend fun loginWithApiKey(
        serverUrl: String,
        apiKey: String,
        trustAllCerts: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            session.setServerUrl(serverUrl, trustAllCerts)
            session.setApiKey(apiKey)
            apiProvider.invalidate()

            // Not /api/auth/me: that handler answers from the session alone
            // (handlers/auth.go GetCurrentUser), so it returns 401 for a perfectly
            // valid API key. /api/instances sits behind the same IsAuthenticated
            // middleware, which does honour X-API-Key, so a bad key still fails here
            // rather than on the first screen.
            apiProvider.api().instances()

            // An API key is not tied to a username, and the endpoint that would
            // report one is session-only; the account row shows the server instead.
            session.setUsername(null)
            Unit
        }
    }.onFailure {
        session.clearCredentials()
        apiProvider.invalidate()
    }

    suspend fun setupFirstUser(
        serverUrl: String,
        username: String,
        password: String,
        trustAllCerts: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            session.setServerUrl(serverUrl, trustAllCerts)
            apiProvider.invalidate()
            apiProvider.apiFor(serverUrl).setup(SetupRequest(username, password))
            session.setUsername(username)
            Unit
        }
    }

    suspend fun isSetupRequired(serverUrl: String): Result<Boolean> = runCatching {
        withContext(Dispatchers.IO) {
            apiProvider.apiFor(serverUrl).checkSetup().setupRequired
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { apiProvider.api().logout() }
        session.clearCredentials()
        apiProvider.invalidate()
    }

    suspend fun currentUser() = withContext(Dispatchers.IO) {
        runCatching { apiProvider.api().me() }
    }

    // ---- instances ----

    suspend fun instances(): Result<List<Instance>> = io { api ->
        api.instances().sortedBy { it.sortOrder }
    }

    suspend fun capabilities(instanceId: Int): Result<InstanceCapabilities> = io { api ->
        api.capabilities(instanceId)
    }

    suspend fun transferInfo(instanceId: Int): Result<TransferInfo> = io { api ->
        api.transferInfo(instanceId)
    }

    suspend fun preferences(instanceId: Int): Result<AppPreferences> = io { api ->
        api.preferences(instanceId)
    }

    suspend fun updatePreferences(
        instanceId: Int,
        values: Map<String, Any?>,
    ): Result<Unit> = io { api ->
        api.updatePreferences(instanceId, values)
        Unit
    }

    suspend fun toggleAltSpeedLimits(instanceId: Int): Result<Unit> = io { api ->
        api.toggleAltSpeedLimits(instanceId)
        Unit
    }

    // ---- torrents ----

    suspend fun torrents(
        instanceId: Int,
        page: Int,
        limit: Int,
        sort: String,
        order: String,
        search: String?,
        filters: TorrentFilters?,
    ): Result<TorrentResponse> = io { api ->
        api.torrents(
            instanceId = instanceId,
            page = page,
            limit = limit,
            sort = sort,
            order = order,
            search = search?.takeIf { it.isNotBlank() },
            filters = filters?.takeIf { !it.isEmpty }?.let { json.encodeToString(it) },
        )
    }

    suspend fun crossInstanceTorrents(
        instanceIds: List<Int>,
        page: Int,
        limit: Int,
        sort: String,
        order: String,
        search: String?,
        filters: TorrentFilters?,
    ): Result<TorrentResponse> = io { api ->
        api.crossInstanceTorrents(
            page = page,
            limit = limit,
            sort = sort,
            order = order,
            search = search?.takeIf { it.isNotBlank() },
            filters = filters?.takeIf { !it.isEmpty }?.let { json.encodeToString(it) },
            instanceIds = instanceIds.takeIf { it.isNotEmpty() }?.joinToString(","),
        )
    }

    suspend fun bulkAction(instanceId: Int, request: BulkActionRequest): Result<Unit> = io { api ->
        api.bulkAction(instanceId, request)
        Unit
    }

    /** Convenience wrapper for the action menu; [targets] is only needed cross-instance. */
    suspend fun action(
        instanceId: Int,
        action: String,
        hashes: List<String>,
        targets: List<ActionTarget>? = null,
        configure: BulkActionRequest.() -> BulkActionRequest = { this },
    ): Result<Unit> = bulkAction(
        instanceId,
        BulkActionRequest(hashes = hashes, action = action, targets = targets).configure(),
    )

    suspend fun addTorrent(
        instanceId: Int,
        urls: List<String>,
        files: List<Pair<String, ByteArray>>,
        category: String?,
        tags: List<String>,
        startPaused: Boolean,
        savePath: String?,
        autoTmm: Boolean?,
        skipHashCheck: Boolean,
        sequentialDownload: Boolean,
        firstLastPiecePrio: Boolean,
        contentLayout: String?,
        rename: String?,
        limitUploadSpeed: Long?,
        limitDownloadSpeed: Long?,
        limitRatio: Double?,
        limitSeedTime: Long?,
    ): Result<AddTorrentResponse> = io { api ->
        val parts = buildList {
            files.forEach { (name, bytes) ->
                add(
                    MultipartBody.Part.createFormData(
                        "torrent",
                        name,
                        bytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull()),
                    )
                )
            }
            if (urls.isNotEmpty()) addField("urls", urls.joinToString("\n"))
            category?.takeIf { it.isNotBlank() }?.let { addField("category", it) }
            if (tags.isNotEmpty()) addField("tags", tags.joinToString(","))
            addField("paused", startPaused.toString())
            autoTmm?.let { addField("autoTMM", it.toString()) }
            addField("skip_checking", skipHashCheck.toString())
            addField("sequentialDownload", sequentialDownload.toString())
            addField("firstLastPiecePrio", firstLastPiecePrio.toString())
            contentLayout?.takeIf { it.isNotBlank() }?.let { addField("contentLayout", it) }
            rename?.takeIf { it.isNotBlank() }?.let { addField("rename", it) }
            // qui ignores savepath when autoTMM is on, matching the web dialog.
            if (autoTmm != true) {
                savePath?.takeIf { it.isNotBlank() }?.let { addField("savepath", it) }
            }
            limitUploadSpeed?.takeIf { it > 0 }?.let { addField("upLimit", it.toString()) }
            limitDownloadSpeed?.takeIf { it > 0 }?.let { addField("dlLimit", it.toString()) }
            limitRatio?.takeIf { it > 0 }?.let { addField("ratioLimit", it.toString()) }
            limitSeedTime?.takeIf { it > 0 }?.let { addField("seedingTimeLimit", it.toString()) }
        }
        api.addTorrent(instanceId, parts)
    }

    private fun MutableList<MultipartBody.Part>.addField(name: String, value: String) {
        add(MultipartBody.Part.createFormData(name, value))
    }

    // ---- torrent details ----

    suspend fun torrentProperties(instanceId: Int, hash: String): Result<TorrentProperties> =
        io { api -> api.torrentProperties(instanceId, hash) }

    suspend fun torrentTrackers(instanceId: Int, hash: String): Result<List<TorrentTracker>> =
        io { api -> api.torrentTrackers(instanceId, hash) }

    suspend fun addTrackers(instanceId: Int, hash: String, urls: String): Result<Unit> =
        io { api -> api.addTrackers(instanceId, hash, TrackerUrlsRequest(urls)); Unit }

    suspend fun editTracker(
        instanceId: Int,
        hash: String,
        oldUrl: String,
        newUrl: String,
    ): Result<Unit> = io { api ->
        api.editTracker(instanceId, hash, EditTrackerRequest(oldUrl, newUrl))
        Unit
    }

    /** qui returns peers keyed by "ip:port" or pre-sorted; both are flattened here. */
    suspend fun torrentPeers(instanceId: Int, hash: String): Result<List<TorrentPeer>> =
        io { api ->
            val response = api.torrentPeers(instanceId, hash)
            response.sortedPeers
                ?: response.peers?.map { (key, peer) -> peer.copy(key = key) }
                ?: emptyList()
        }

    suspend fun torrentWebSeeds(instanceId: Int, hash: String): Result<List<WebSeed>> =
        io { api -> api.torrentWebSeeds(instanceId, hash) }

    suspend fun torrentFiles(instanceId: Int, hash: String): Result<List<TorrentFile>> =
        io { api -> api.torrentFiles(instanceId, hash) }

    suspend fun setFilePriority(
        instanceId: Int,
        hash: String,
        indexes: List<Int>,
        priority: Int,
    ): Result<Unit> = io { api ->
        api.setFilePriority(instanceId, hash, FilePriorityRequest(indexes, priority))
        Unit
    }

    suspend fun renameTorrent(instanceId: Int, hash: String, name: String): Result<Unit> =
        io { api -> api.renameTorrent(instanceId, hash, RenameRequest(name)); Unit }

    suspend fun renameFile(
        instanceId: Int,
        hash: String,
        oldPath: String,
        newPath: String,
    ): Result<Unit> = io { api ->
        api.renameFile(instanceId, hash, RenamePathRequest(oldPath, newPath))
        Unit
    }

    suspend fun renameFolder(
        instanceId: Int,
        hash: String,
        oldPath: String,
        newPath: String,
    ): Result<Unit> = io { api ->
        api.renameFolder(instanceId, hash, RenamePathRequest(oldPath, newPath))
        Unit
    }

    // ---- categories, tags, trackers ----

    suspend fun categories(instanceId: Int): Result<Map<String, Category>> =
        io { api -> api.categories(instanceId) }

    suspend fun createCategory(instanceId: Int, name: String, savePath: String): Result<Unit> =
        io { api -> api.createCategory(instanceId, CreateCategoryRequest(name, savePath)); Unit }

    suspend fun editCategory(instanceId: Int, name: String, savePath: String): Result<Unit> =
        io { api -> api.editCategory(instanceId, CreateCategoryRequest(name, savePath)); Unit }

    suspend fun removeCategories(instanceId: Int, names: List<String>): Result<Unit> =
        io { api -> api.removeCategories(instanceId, RemoveCategoriesRequest(names)); Unit }

    suspend fun tags(instanceId: Int): Result<List<String>> = io { api -> api.tags(instanceId) }

    suspend fun createTags(instanceId: Int, tags: List<String>): Result<Unit> =
        io { api -> api.createTags(instanceId, CreateTagsRequest(tags)); Unit }

    suspend fun deleteTags(instanceId: Int, tags: List<String>): Result<Unit> =
        io { api -> api.deleteTags(instanceId, DeleteTagsRequest(tags)); Unit }

    suspend fun activeTrackers(instanceId: Int): Result<Map<String, String>> =
        io { api -> api.activeTrackers(instanceId) }

    suspend fun trackerIcons(): Result<Map<String, String>> = io { api -> api.trackerIcons() }

    suspend fun serverVersion() = io { api -> api.version() }

    suspend fun latestVersion() = io { api -> api.latestVersion() }

    private suspend inline fun <T> io(
        crossinline block: suspend (dev.qui.android.data.remote.QuiApi) -> T,
    ): Result<T> = runCatching {
        withContext(Dispatchers.IO) { block(apiProvider.api()) }
    }
}
