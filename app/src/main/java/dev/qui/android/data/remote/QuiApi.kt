/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Endpoint set mirrors autobrr/qui's internal/api/server.go routing table.
 */

package dev.qui.android.data.remote

import dev.qui.android.data.model.AddTorrentResponse
import dev.qui.android.data.model.AppPreferences
import dev.qui.android.data.model.AuthResponse
import dev.qui.android.data.model.AuthUser
import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.model.Category
import dev.qui.android.data.model.CreateCategoryRequest
import dev.qui.android.data.model.CreateTagsRequest
import dev.qui.android.data.model.DeleteTagsRequest
import dev.qui.android.data.model.EditTrackerRequest
import dev.qui.android.data.model.FilePriorityRequest
import dev.qui.android.data.model.Instance
import dev.qui.android.data.model.InstanceCapabilities
import dev.qui.android.data.model.LatestVersion
import dev.qui.android.data.model.LoginRequest
import dev.qui.android.data.model.MessageResponse
import dev.qui.android.data.model.RemoveCategoriesRequest
import dev.qui.android.data.model.RenamePathRequest
import dev.qui.android.data.model.RenameRequest
import dev.qui.android.data.model.SetupCheckResponse
import dev.qui.android.data.model.SetupRequest
import dev.qui.android.data.model.TorrentFile
import dev.qui.android.data.model.TorrentPeersResponse
import dev.qui.android.data.model.TorrentProperties
import dev.qui.android.data.model.TorrentResponse
import dev.qui.android.data.model.TorrentTracker
import dev.qui.android.data.model.TrackerUrlsRequest
import dev.qui.android.data.model.TransferInfo
import dev.qui.android.data.model.VersionInfo
import dev.qui.android.data.model.WebSeed
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface QuiApi {

    // ---- auth ----

    @GET("api/auth/check-setup")
    suspend fun checkSetup(): SetupCheckResponse

    @POST("api/auth/setup")
    suspend fun setup(@Body body: SetupRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(): AuthUser

    @GET("api/version")
    suspend fun version(): VersionInfo

    @GET("api/version/latest")
    suspend fun latestVersion(): LatestVersion

    // ---- instances ----

    @GET("api/instances")
    suspend fun instances(): List<Instance>

    @POST("api/instances/{id}/test")
    suspend fun testInstance(@Path("id") instanceId: Int): Response<ResponseBody>

    @GET("api/instances/{id}/capabilities")
    suspend fun capabilities(@Path("id") instanceId: Int): InstanceCapabilities

    @GET("api/instances/{id}/transfer-info")
    suspend fun transferInfo(@Path("id") instanceId: Int): TransferInfo

    @GET("api/instances/{id}/preferences")
    suspend fun preferences(@Path("id") instanceId: Int): AppPreferences

    @PATCH("api/instances/{id}/preferences")
    suspend fun updatePreferences(
        @Path("id") instanceId: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
    ): Response<Unit>

    @POST("api/instances/{id}/alternative-speed-limits/toggle")
    suspend fun toggleAltSpeedLimits(@Path("id") instanceId: Int): Response<ResponseBody>

    // ---- torrent listing ----

    @GET("api/instances/{id}/torrents")
    suspend fun torrents(
        @Path("id") instanceId: Int,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("sort") sort: String,
        @Query("order") order: String,
        @Query("search") search: String?,
        @Query("filters") filters: String?,
    ): TorrentResponse

    @GET("api/torrents/cross-instance")
    suspend fun crossInstanceTorrents(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("sort") sort: String,
        @Query("order") order: String,
        @Query("search") search: String?,
        @Query("filters") filters: String?,
        @Query("instanceIds") instanceIds: String?,
    ): TorrentResponse

    // ---- torrent actions ----

    @POST("api/instances/{id}/torrents/bulk-action")
    suspend fun bulkAction(
        @Path("id") instanceId: Int,
        @Body body: BulkActionRequest,
    ): Response<ResponseBody>

    @Multipart
    @POST("api/instances/{id}/torrents")
    suspend fun addTorrent(
        @Path("id") instanceId: Int,
        @Part parts: List<MultipartBody.Part>,
    ): AddTorrentResponse

    // ---- torrent details ----

    @GET("api/instances/{id}/torrents/{hash}/properties")
    suspend fun torrentProperties(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): TorrentProperties

    @GET("api/instances/{id}/torrents/{hash}/trackers")
    suspend fun torrentTrackers(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): List<TorrentTracker>

    @POST("api/instances/{id}/torrents/{hash}/trackers")
    suspend fun addTrackers(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: TrackerUrlsRequest,
    ): Response<Unit>

    @PUT("api/instances/{id}/torrents/{hash}/trackers")
    suspend fun editTracker(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: EditTrackerRequest,
    ): Response<Unit>

    @GET("api/instances/{id}/torrents/{hash}/peers")
    suspend fun torrentPeers(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): TorrentPeersResponse

    @GET("api/instances/{id}/torrents/{hash}/webseeds")
    suspend fun torrentWebSeeds(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): List<WebSeed>

    @GET("api/instances/{id}/torrents/{hash}/files")
    suspend fun torrentFiles(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): List<TorrentFile>

    @PUT("api/instances/{id}/torrents/{hash}/files")
    suspend fun setFilePriority(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: FilePriorityRequest,
    ): Response<Unit>

    @PUT("api/instances/{id}/torrents/{hash}/rename")
    suspend fun renameTorrent(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: RenameRequest,
    ): Response<Unit>

    @PUT("api/instances/{id}/torrents/{hash}/rename-file")
    suspend fun renameFile(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: RenamePathRequest,
    ): Response<Unit>

    @PUT("api/instances/{id}/torrents/{hash}/rename-folder")
    suspend fun renameFolder(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
        @Body body: RenamePathRequest,
    ): Response<Unit>

    @Streaming
    @GET("api/instances/{id}/torrents/{hash}/export")
    suspend fun exportTorrent(
        @Path("id") instanceId: Int,
        @Path("hash") hash: String,
    ): Response<ResponseBody>

    // ---- categories, tags, trackers ----

    @GET("api/instances/{id}/categories")
    suspend fun categories(@Path("id") instanceId: Int): Map<String, Category>

    @POST("api/instances/{id}/categories")
    suspend fun createCategory(
        @Path("id") instanceId: Int,
        @Body body: CreateCategoryRequest,
    ): MessageResponse

    @PUT("api/instances/{id}/categories")
    suspend fun editCategory(
        @Path("id") instanceId: Int,
        @Body body: CreateCategoryRequest,
    ): MessageResponse

    @retrofit2.http.HTTP(method = "DELETE", path = "api/instances/{id}/categories", hasBody = true)
    suspend fun removeCategories(
        @Path("id") instanceId: Int,
        @Body body: RemoveCategoriesRequest,
    ): MessageResponse

    @GET("api/instances/{id}/tags")
    suspend fun tags(@Path("id") instanceId: Int): List<String>

    @POST("api/instances/{id}/tags")
    suspend fun createTags(
        @Path("id") instanceId: Int,
        @Body body: CreateTagsRequest,
    ): MessageResponse

    @retrofit2.http.HTTP(method = "DELETE", path = "api/instances/{id}/tags", hasBody = true)
    suspend fun deleteTags(
        @Path("id") instanceId: Int,
        @Body body: DeleteTagsRequest,
    ): MessageResponse

    @GET("api/instances/{id}/trackers")
    suspend fun activeTrackers(@Path("id") instanceId: Int): Map<String, String>

    @GET("api/tracker-icons")
    suspend fun trackerIcons(): Map<String, String>

    // ---- api keys (used to authenticate this app against qui) ----

    @GET("api/api-keys")
    suspend fun apiKeys(): List<ApiKeyDto>

    @DELETE("api/api-keys/{id}")
    suspend fun deleteApiKey(@Path("id") id: Int): Response<Unit>
}

@kotlinx.serialization.Serializable
data class ApiKeyDto(
    val id: Int = 0,
    val name: String = "",
    val key: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
)
