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
    /** The release body, as written on GitHub. Markdown, shown as plain text. */
    val appReleaseNotes: String? = null,
    val serverLatest: String? = null,
    val serverReleaseUrl: String? = null,
    val checkedAt: Long? = null,
) {
    val appUpdateAvailable: Boolean
        get() = appLatest != null && isNewer(appLatest, appVersion)
}

private data class GithubRelease(val tag: String?, val url: String?, val notes: String?)

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
                GithubRelease(
                    tag = obj["tag_name"]?.jsonPrimitive?.content,
                    url = obj["html_url"]?.jsonPrimitive?.content,
                    notes = obj["body"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }.getOrNull()

        // qui's own update notice; absent when the server is already current.
        val server = repository.latestVersion().getOrNull()

        UpdateStatus(
            appLatest = app?.tag,
            appReleaseUrl = app?.url,
            appReleaseNotes = app?.notes,
            serverLatest = server?.tagName,
            serverReleaseUrl = server?.htmlUrl,
            checkedAt = System.currentTimeMillis(),
        )
    }
}

/**
 * Release bodies carry every language under a "## <name>" heading, built by the release
 * workflow from the per-language CHANGELOGs. This picks one out.
 *
 * Anything that does not follow that shape — an older release, or notes written by
 * hand — comes back whole, which is the only safe answer when the structure is absent.
 */
internal fun localizedReleaseNotes(body: String?, heading: String): String? {
    val text = body?.takeIf { it.isNotBlank() } ?: return null

    val headings = Regex("(?m)^## (.+?)\\s*$").findAll(text).toList()
    if (headings.size < 2) return text.trim()

    val match = headings.firstOrNull { it.groupValues[1].trim() == heading } ?: return text.trim()
    val next = headings.firstOrNull { it.range.first > match.range.first }

    return text.substring(match.range.last + 1, next?.range?.first ?: text.length).trim()
        .ifEmpty { text.trim() }
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
