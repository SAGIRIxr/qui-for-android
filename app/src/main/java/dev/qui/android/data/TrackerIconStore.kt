/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * qui caches tracker favicons server-side and hands them to the web UI from
 * GET /api/tracker-icons as a host -> data:image/png;base64 map. The cards show
 * them next to the tracker name, which is most of what makes the list scannable,
 * so the app fetches the same map and decodes it once.
 */

package dev.qui.android.data

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerIconStore @Inject constructor(
    private val repository: QuiRepository,
) {

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    private val mutex = Mutex()
    private var lastFetch = 0L

    /**
     * Icons only change when qui downloads a new favicon, so a long floor keeps this
     * off the hot path while the list re-subscribes.
     */
    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && _icons.value.isNotEmpty() && now - lastFetch < REFRESH_FLOOR_MS) return
            val raw = repository.trackerIcons().getOrNull() ?: return
            lastFetch = now
            _icons.value = withContext(Dispatchers.Default) { decodeAll(raw) }
        }
    }

    /** Drops the decoded bitmaps; the next refresh re-fetches them from qui. */
    suspend fun clear() {
        mutex.withLock {
            _icons.value = emptyMap()
            lastFetch = 0L
        }
    }

    private fun decodeAll(raw: Map<String, String>): Map<String, ImageBitmap> =
        raw.mapNotNull { (host, dataUrl) ->
            decode(dataUrl)?.let { host.lowercase() to it }
        }.toMap()

    private fun decode(dataUrl: String): ImageBitmap? {
        val comma = dataUrl.indexOf(',')
        if (comma < 0 || !dataUrl.startsWith("data:")) return null
        return runCatching {
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        const val REFRESH_FLOOR_MS = 10 * 60 * 1000L
    }
}

/**
 * Icon lookup with the www/bare fallback qui's resolveTrackerIconSrc does, so a
 * torrent reporting `www.example.org` still matches an icon cached as `example.org`.
 */
fun Map<String, ImageBitmap>.iconFor(host: String): ImageBitmap? {
    if (host.isEmpty()) return null
    val key = host.lowercase()
    this[key]?.let { return it }
    val alias = if (key.startsWith("www.")) key.removePrefix("www.") else "www.$key"
    return this[alias]
}
