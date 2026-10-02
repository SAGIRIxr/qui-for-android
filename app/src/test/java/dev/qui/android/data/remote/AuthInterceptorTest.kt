/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data.remote

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AuthInterceptorTest {
    private val session = mockk<SessionStore> {
        coEvery { currentApiKey() } returns null
        coEvery { currentCookie() } returns null
    }

    /** Exercise the interceptor with the request and response seen by OkHttp. */
    private fun intercept(request: Request): Response {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers {
            Response.Builder()
                .request(firstArg())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        return AuthInterceptor(session).intercept(chain)
    }

    @Test
    fun `cookie writes include the header required by qui 1_30`() {
        coEvery { session.currentCookie() } returns "session=password-session"
        val body = "{}".toRequestBody("application/json".toMediaType())

        for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
            val request = Request.Builder()
                .url("https://qui.example/api/instances/1/torrents")
                .method(method, body)
                .build()
            val sent = intercept(request).request

            assertEquals(method, sent.method)
            assertSame(body, sent.body)
            assertEquals("session=password-session", sent.header("Cookie"))
            assertEquals("XMLHttpRequest", sent.header("X-Requested-With"))
            assertEquals("application/json", sent.header("Accept"))
        }
    }

    @Test
    fun `SSE keeps its event stream accept header with session authentication`() {
        coEvery { session.currentCookie() } returns "session=stream-session"
        val request = Request.Builder()
            .url("https://qui.example/api/stream")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val sent = intercept(request).request

        assertEquals("text/event-stream", sent.header("Accept"))
        assertEquals("no-cache", sent.header("Cache-Control"))
        assertEquals("session=stream-session", sent.header("Cookie"))
        assertEquals("XMLHttpRequest", sent.header("X-Requested-With"))
    }

    @Test
    fun `API key remains available alongside a stored cookie`() {
        // qui validates the key first, even when a valid session is also present.
        coEvery { session.currentApiKey() } returns "configured-api-key"
        coEvery { session.currentCookie() } returns "session=previous-session"
        val request = Request.Builder()
            .url("https://qui.example/api/instances")
            .build()

        val sent = intercept(request).request

        assertEquals("configured-api-key", sent.header("X-API-Key"))
        assertEquals("session=previous-session", sent.header("Cookie"))
        assertEquals("XMLHttpRequest", sent.header("X-Requested-With"))
    }

    @Test
    fun `requests without stored credentials still work for login and setup`() {
        coEvery { session.currentApiKey() } returns " "
        coEvery { session.currentCookie() } returns ""
        val request = Request.Builder()
            .url("https://qui.example/api/auth/login")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        val sent = intercept(request).request

        assertNull(sent.header("X-API-Key"))
        assertNull(sent.header("Cookie"))
        assertSame(request.body, sent.body)
        assertEquals("json", sent.body?.contentType()?.subtype)
        assertEquals("XMLHttpRequest", sent.header("X-Requested-With"))
    }

    @Test
    fun `explicit accept values survive without extra JSON headers`() {
        val request = Request.Builder()
            .url("https://qui.example/api/instances/1/torrents/export")
            .addHeader("Accept", "application/x-bittorrent")
            .addHeader("Accept", "application/octet-stream")
            .build()

        val sent = intercept(request).request

        assertEquals(request.headers.values("Accept"), sent.headers.values("Accept"))
    }
}
