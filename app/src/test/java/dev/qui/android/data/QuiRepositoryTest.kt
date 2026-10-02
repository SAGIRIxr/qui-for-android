/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data

import dev.qui.android.data.model.BulkActionRequest
import dev.qui.android.data.remote.QuiApi
import dev.qui.android.data.remote.QuiApiProvider
import dev.qui.android.data.remote.SessionStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class QuiRepositoryTest {
    private val api = mockk<QuiApi>()
    private val provider = mockk<QuiApiProvider>()
    private val repository = QuiRepository(provider, mockk<SessionStore>(), Json)

    @Before fun setup() {
        coEvery { provider.api() } returns api
    }

    @Test fun `all response-returning writes reject HTTP errors and close their bodies`() = runTest {
        val bodies = mutableListOf<TrackedBody>()
        fun <T> rejected(): Response<T> {
            val body = TrackedBody("""{"error":"Session is no longer valid"}""")
            bodies += body
            return Response.error(403, body)
        }
        coEvery { api.updatePreferences(any(), any()) } answers { rejected() }
        coEvery { api.toggleAltSpeedLimits(any()) } answers { rejected() }
        coEvery { api.bulkAction(any(), any()) } answers { rejected() }
        coEvery { api.addTrackers(any(), any(), any()) } answers { rejected() }
        coEvery { api.editTracker(any(), any(), any()) } answers { rejected() }
        coEvery { api.setFilePriority(any(), any(), any()) } answers { rejected() }
        coEvery { api.renameTorrent(any(), any(), any()) } answers { rejected() }
        coEvery { api.renameFile(any(), any(), any()) } answers { rejected() }
        coEvery { api.renameFolder(any(), any(), any()) } answers { rejected() }

        val writes: List<Pair<String, suspend () -> Result<Unit>>> = listOf(
            "preferences" to { repository.updatePreferences(1, mapOf("dl_limit" to 1024)) },
            "alternative speed" to { repository.toggleAltSpeedLimits(1) },
            "bulk action" to { repository.bulkAction(1, BulkActionRequest(action = "pause")) },
            "add trackers" to { repository.addTrackers(1, "hash", "https://tracker.invalid/announce") },
            "edit tracker" to { repository.editTracker(1, "hash", "old", "new") },
            "file priority" to { repository.setFilePriority(1, "hash", listOf(0), 1) },
            "rename torrent" to { repository.renameTorrent(1, "hash", "new") },
            "rename file" to { repository.renameFile(1, "hash", "old", "new") },
            "rename folder" to { repository.renameFolder(1, "hash", "old", "new") },
        )
        for ((name, write) in writes) {
            val error = write().exceptionOrNull()
            assertTrue(name, error is HttpException)
            assertEquals(name, 403, (error as HttpException).code())
            assertEquals(name, "HTTP 403: Session is no longer valid", error.message)
        }
        assertEquals(writes.size, bodies.size)
        assertTrue(bodies.all { it.closed })
    }

    @Test fun `successful empty and JSON write responses stay successful and release bodies`() = runTest {
        val bulkBody = TrackedBody("""{"message":"Action completed"}""")
        val speedBody = TrackedBody("""{"enabled":true}""")
        coEvery { api.bulkAction(any(), any()) } returns Response.success(bulkBody)
        coEvery { api.toggleAltSpeedLimits(any()) } returns Response.success(speedBody)
        coEvery { api.renameTorrent(any(), any(), any()) } returns Response.success<Unit>(204, null)

        assertTrue(repository.bulkAction(1, BulkActionRequest(action = "pause")).isSuccess)
        assertTrue(repository.toggleAltSpeedLimits(1).isSuccess)
        assertTrue(repository.renameTorrent(1, "hash", "new").isSuccess)
        assertTrue(bulkBody.closed)
        assertTrue(speedBody.closed)
    }

    @Test fun `server messages and proxy errors remain visible with HTTP status`() = runTest {
        val cases = listOf(
            """{"message":"Instance is read-only"}""" to "HTTP 403: Instance is read-only",
            "upstream unavailable" to "HTTP 403: upstream unavailable",
            "" to "HTTP 403",
        )
        for ((bodyText, expected) in cases) {
            val body = TrackedBody(bodyText)
            coEvery { api.bulkAction(any(), any()) } returns Response.error(403, body)
            assertEquals(expected, repository.bulkAction(1, BulkActionRequest()).exceptionOrNull()?.message)
            assertTrue(body.closed)
        }
    }

    @Test fun `decoded category and tag endpoints retain Retrofit HTTP failures`() = runTest {
        val error = HttpException(Response.error<Unit>(500, TrackedBody("server failed")))
        coEvery { api.createCategory(any(), any()) } throws error
        coEvery { api.editCategory(any(), any()) } throws error
        coEvery { api.removeCategories(any(), any()) } throws error
        coEvery { api.createTags(any(), any()) } throws error
        coEvery { api.deleteTags(any(), any()) } throws error

        assertSame(error, repository.createCategory(1, "category", "/data").exceptionOrNull())
        assertSame(error, repository.editCategory(1, "category", "/data").exceptionOrNull())
        assertSame(error, repository.removeCategories(1, listOf("category")).exceptionOrNull())
        assertSame(error, repository.createTags(1, listOf("tag")).exceptionOrNull())
        assertSame(error, repository.deleteTags(1, listOf("tag")).exceptionOrNull())
        error.response()?.errorBody()?.close()
    }

    @Test fun `cancellation propagates instead of becoming an operation failure`() = runTest {
        val cancellation = CancellationException("screen closed")
        coEvery { api.bulkAction(any(), any()) } throws cancellation

        try {
            repository.bulkAction(1, BulkActionRequest(action = "pause"))
            fail("Expected cancellation to propagate")
        } catch (error: CancellationException) {
            assertEquals(cancellation.message, error.message)
        }
    }

    private class TrackedBody(content: String) : ResponseBody() {
        var closed = false
            private set
        private val length = content.toByteArray().size.toLong()
        private val source = object : ForwardingSource(Buffer().writeUtf8(content)) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType() = "application/json".toMediaType()
        override fun contentLength() = length
        override fun source(): BufferedSource = source
    }
}
