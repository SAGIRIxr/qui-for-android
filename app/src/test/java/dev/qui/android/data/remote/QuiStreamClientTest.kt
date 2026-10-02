package dev.qui.android.data.remote

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class QuiStreamClientTest {
    private val session = mockk<SessionStore> {
        coEvery { currentServerUrl() } returns "https://qui.example/"
        coEvery { currentCookie() } returns null
        coEvery { currentApiKey() } returns null
    }

    private fun client(body: String = "", code: Int = 200, failure: Boolean = false): QuiStreamClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (failure) throw IOException("connection lost")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        return QuiStreamClient(http, Json { ignoreUnknownKeys = true }, session)
    }

    @Test fun `EOF completes flow and delivers every frame even when consumer is slower`() = runBlocking {
        val body = (1..150).joinToString("") { "event: update\ndata: {\"data\":{\"total\":$it}}\n\n" }
        val events = withTimeout(5_000) { client(body).stream(listOf(StreamSubscription("test", 1))).toList() }
        assertEquals(150, events.size)
        assertEquals(150, (events.last() as StreamEvent.Snapshot).payload.data?.total)
    }

    @Test fun `HTTP and socket failures emit failure and complete for reconnect`() = runBlocking {
        listOf(client(code = 403), client(failure = true)).forEach { stream ->
            val events = withTimeout(5_000) { stream.stream(listOf(StreamSubscription("test", 1))).toList() }
            assertTrue(events.single() is StreamEvent.Failed)
        }
    }

    @Test fun `server error does not drop connection before recovery data`() = runBlocking {
        val body = "event: stream-error\ndata: {\"error\":\"temporary\"}\n\n" +
            "event: heartbeat\ndata: {}\n\n" +
            "event: update\ndata: {\"data\":{\"total\":1}}\n\n"
        val events = withTimeout(5_000) { client(body).stream(listOf(StreamSubscription("test", 1))).toList() }
        assertTrue(events[0] is StreamEvent.Failed)
        assertEquals(StreamEvent.Heartbeat, events[1])
        assertTrue(events[2] is StreamEvent.Snapshot)
    }

    @Test fun `malformed data frame ends stream for full baseline recovery`() = runBlocking {
        val events = withTimeout(5_000) {
            client("event: delta\ndata: invalid\n\n").stream(listOf(StreamSubscription("test", 1))).toList()
        }
        assertTrue(events.single() is StreamEvent.Failed)
    }
}
