/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Two independent update signals: this app's own releases on GitHub, and the qui
 * server's, which qui itself already reports through /api/version/latest. Neither
 * downloads anything — they only point at the release page, because an app that
 * side-loads its own updates is a thing users should opt into deliberately.
 */

package dev.qui.android.data

import dev.qui.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val RELEASES_URL =
    "https://api.github.com/repos/SAGIRIxr/qui-for-android/releases/latest"

data class UpdateStatus(
    val appVersion: String = BuildConfig.VERSION_NAME,
    val appLatest: String? = null,
    val appReleaseUrl: String? = null,
    val serverLatest: String? = null,
    val serverReleaseUrl: String? = null,
    val checkedAt: Long? = null,
) {
    val appUpdateAvailable: Boolean
        get() = appLatest != null && isNewer(appLatest, appVersion)
}

@Singleton
class UpdateChecker @Inject constructor(
    private val repository: QuiRepository,
) {
    // Deliberately not the app's authenticated client: this request goes to GitHub,
    // and the qui API key has no business being attached to it.
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        val app = runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val obj = json.parseToJsonElement(body).jsonObject
                obj["tag_name"]?.jsonPrimitive?.content to
                    obj["html_url"]?.jsonPrimitive?.content
            }
        }.getOrNull()

        // qui's own update notice; absent when the server is already current.
        val server = repository.latestVersion().getOrNull()

        UpdateStatus(
            appLatest = app?.first,
            appReleaseUrl = app?.second,
            serverLatest = server?.tagName,
            serverReleaseUrl = server?.htmlUrl,
            checkedAt = System.currentTimeMillis(),
        )
    }
}

/**
 * Compares two release tags. Anything unparseable is treated as "not newer" so a
 * malformed tag can never nag the user about an update that does not exist.
 */
internal fun isNewer(candidate: String, current: String): Boolean {
    val a = versionParts(candidate)
    val b = versionParts(current)
    if (a.isEmpty() || b.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun versionParts(tag: String): List<Int> =
    tag.trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: return emptyList() }
