/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Mirrors qui's magnet/protocol handler (web/src/lib/protocol-handler.ts and
 * add-intent.ts): a magnet link or .torrent file handed to the app opens the add sheet
 * pre-filled instead of dropping the user on the torrent list.
 */

package dev.qui.android.ui.addintent

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** A torrent handed to the app from outside: either URLs or decoded .torrent bytes. */
data class AddIntent(
    val urls: List<String> = emptyList(),
    val files: List<TorrentPayload> = emptyList(),
) {
    val isEmpty: Boolean get() = urls.isEmpty() && files.isEmpty()
}

data class TorrentPayload(val filename: String, val bytes: ByteArray) {
    // ByteArray needs structural equality for this to behave in state comparisons.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TorrentPayload) return false
        return filename == other.filename && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * filename.hashCode() + bytes.contentHashCode()
}

fun parseAddIntent(intent: Intent?, resolver: ContentResolver): AddIntent? {
    if (intent == null) return null

    val result = when (intent.action) {
        Intent.ACTION_VIEW -> fromUri(intent.data, resolver)
        Intent.ACTION_SEND -> fromSend(intent, resolver)
        Intent.ACTION_SEND_MULTIPLE -> fromSendMultiple(intent, resolver)
        else -> null
    }

    return result?.takeIf { !it.isEmpty }
}

private fun fromUri(uri: Uri?, resolver: ContentResolver): AddIntent? {
    if (uri == null) return null
    return when (uri.scheme?.lowercase()) {
        "magnet" -> AddIntent(urls = listOf(uri.toString()))
        "http", "https" -> AddIntent(urls = listOf(uri.toString()))
        "content", "file" -> readTorrent(uri, resolver)?.let { AddIntent(files = listOf(it)) }
        else -> null
    }
}

private fun fromSend(intent: Intent, resolver: ContentResolver): AddIntent? {
    @Suppress("DEPRECATION")
    val stream: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
    if (stream != null) {
        return readTorrent(stream, resolver)?.let { AddIntent(files = listOf(it)) }
    }

    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
    val urls = extractLinks(text)
    return urls.takeIf { it.isNotEmpty() }?.let { AddIntent(urls = it) }
}

private fun fromSendMultiple(intent: Intent, resolver: ContentResolver): AddIntent? {
    @Suppress("DEPRECATION")
    val streams: List<Uri> = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: return null
    val files = streams.mapNotNull { readTorrent(it, resolver) }
    return files.takeIf { it.isNotEmpty() }?.let { AddIntent(files = it) }
}

/** Pulls magnet and http(s) links out of arbitrary shared text. */
internal fun extractLinks(text: String): List<String> =
    text.split('\n', '\r', ' ', '\t')
        .map { it.trim() }
        .filter {
            it.startsWith("magnet:", true) ||
                it.startsWith("http://", true) ||
                it.startsWith("https://", true)
        }
        .distinct()

private fun readTorrent(uri: Uri, resolver: ContentResolver): TorrentPayload? = runCatching {
    val name = displayName(uri, resolver) ?: "torrent.torrent"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    // A .torrent is bencoded and always starts with a dictionary marker; anything else
    // would be rejected by qBittorrent anyway, so it is filtered out here.
    if (bytes.isEmpty() || bytes[0] != 'd'.code.toByte()) return null
    TorrentPayload(name, bytes)
}.getOrNull()

private fun displayName(uri: Uri, resolver: ContentResolver): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return@use cursor.getString(index)
        }
        null
    } ?: uri.lastPathSegment
}.getOrNull()
