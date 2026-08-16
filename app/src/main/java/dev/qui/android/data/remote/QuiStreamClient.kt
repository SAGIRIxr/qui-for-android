/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Client for qui's multiplexed SSE endpoint (GET /api/stream), which is what keeps the
 * web UI's torrent list live. Event names come from internal/api/sse/manager.go:
 * init, update, delta, stream-error, heartbeat, activity.
 */

package dev.qui.android.data.remote

import dev.qui.android.data.model.TorrentFilters
import dev.qui.android.data.model.TorrentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StreamSubscription(
    val key: String,
    val instanceId: Int,
    val instanceIds: List<Int>? = null,
    val page: Int = 0,
    val limit: Int = 100,
    val sort: String = "added_on",
    val order: String = "desc",
    val search: String = "",
    val filters: TorrentFilters? = null,
)

@Serializable
data class StreamMeta(
    val instanceId: Int = 0,
    val rid: Long? = null,
    val fullUpdate: Boolean? = null,
    val timestamp: String = "",
    val lastSuccessfulSync: String? = null,
    val retryInSeconds: Int? = null,
    val streamKey: String? = null,
)

@Serializable
data class StreamDelta(val order: List<String>? = null)

@Serializable
data class StreamPayload(
    val type: String = "",
    val data: TorrentResponse? = null,
    val delta: StreamDelta? = null,
    val meta: StreamMeta? = null,
    val error: String? = null,
)

sealed interface StreamEvent {
    /** Full page snapshot; replaces whatever the caller currently holds. */
    data class Snapshot(val payload: StreamPayload) : StreamEvent

    /**
     * Incremental frame. Changed rows ride in `payload.data.torrents`; `delta.order`
     * carries the full page key sequence only when membership or ordering changed.
     */
    data class Delta(val payload: StreamPayload) : StreamEvent

    data class Failed(val message: String, val retryInSeconds: Int?) : StreamEvent
    data object Heartbeat : StreamEvent
}

/** Six missed heartbeats. Long enough to ride out a stall, short enough to notice a death. */
private const val STREAM_READ_TIMEOUT_SECONDS = 30L

@Singleton
class QuiStreamClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val session: SessionStore,
) {
    /**
     * Opens the stream and emits events until cancelled. Reconnection is the caller's
     * job so that backoff can be tied to screen lifecycle.
     */
    fun stream(subscriptions: List<StreamSubscription>): Flow<StreamEvent> = callbackFlow {
        val baseUrl = session.currentServerUrl()
        if (baseUrl == null) {
            close(IllegalStateException("No qui server configured"))
            return@callbackFlow
        }

        val streamsParam = URLEncoder.encode(json.encodeToString(subscriptions), "UTF-8")
        val url = "${baseUrl}api/stream?streams=$streamsParam"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")

        session.currentApiKey()?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("X-API-Key", it) }
        session.currentCookie()?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Cookie", it) }

        // qui heartbeats every 5 seconds (internal/api/sse/manager.go), so a read
        // timeout comfortably above that is a liveness check rather than a limit on
        // how long the stream may stay open. Disabling it entirely — the obvious
        // reading of "SSE connections are idle between frames" — means a socket that
        // dies silently while the phone is asleep never throws, and the list simply
        // stops updating with no reconnect.
        val streamingClient = client.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val call = streamingClient.newCall(requestBuilder.build())

        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        trySend(StreamEvent.Failed("HTTP ${response.code}", null))
                        return@use
                    }
                    val source = response.body?.source() ?: return@use
                    readEvents(source) { event, data ->
                        if (!isActive) return@readEvents false
                        dispatch(event, data)?.let { trySend(it) }
                        true
                    }
                }
            } catch (e: Exception) {
                if (isActive) trySend(StreamEvent.Failed(e.message ?: "stream failed", null))
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun dispatch(event: String, data: String): StreamEvent? {
        if (event == "heartbeat") return StreamEvent.Heartbeat
        val payload = runCatching { json.decodeFromString<StreamPayload>(data) }.getOrNull()
            ?: return null

        return when (event.ifEmpty { payload.type }) {
            "init", "update" -> StreamEvent.Snapshot(payload)
            "delta" -> StreamEvent.Delta(payload)
            "stream-error" -> StreamEvent.Failed(
                payload.error ?: "stream error",
                payload.meta?.retryInSeconds,
            )
            "heartbeat" -> StreamEvent.Heartbeat
            else -> null
        }
    }

    /**
     * Minimal SSE framing: accumulate `event:` and `data:` lines until a blank line.
     * Multiple `data:` lines in one frame are joined with newlines, per the spec.
     */
    private inline fun readEvents(
        source: BufferedSource,
        onEvent: (event: String, data: String) -> Boolean,
    ) {
        var eventName = ""
        val dataLines = StringBuilder()

        while (true) {
            val line = source.readUtf8Line() ?: return

            if (line.isEmpty()) {
                if (dataLines.isNotEmpty()) {
                    if (!onEvent(eventName, dataLines.toString())) return
                }
                eventName = ""
                dataLines.setLength(0)
                continue
            }

            when {
                line.startsWith(":") -> Unit // comment / keep-alive
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (dataLines.isNotEmpty()) dataLines.append('\n')
                    dataLines.append(line.removePrefix("data:").removePrefix(" "))
                }
            }
        }
    }
}

@Serializable
data class ActivityEvent(
    @SerialName("type") val type: String = "",
    @SerialName("instanceId") val instanceId: Int? = null,
    @SerialName("message") val message: String? = null,
)
