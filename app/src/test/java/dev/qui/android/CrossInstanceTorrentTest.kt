/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * qui's Go model serialises cross-instance metadata as snake_case over both the REST
 * endpoint and the SSE stream. Reading only the camelCase spelling left every merged
 * row with a null instance id, which silently changed each row's key from
 * "<instanceId>:<hash>" to a bare hash — so the stream's delta order matched nothing
 * and the unified list emptied itself while still reporting thousands of torrents.
 */

package dev.qui.android

import dev.qui.android.data.model.Torrent
import dev.qui.android.data.model.TorrentResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrossInstanceTorrentTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun `reads snake_case instance metadata`() {
        val torrent = json.decodeFromString<Torrent>(
            """{"hash":"abc","name":"n","instance_id":3,"instance_name":"EOS-2"}"""
        )

        assertEquals(3, torrent.instanceId)
        assertEquals("EOS-2", torrent.instanceName)
    }

    @Test
    fun `still reads camelCase instance metadata`() {
        val torrent = json.decodeFromString<Torrent>(
            """{"hash":"abc","name":"n","instanceId":3,"instanceName":"EOS-2"}"""
        )

        assertEquals(3, torrent.instanceId)
        assertEquals("EOS-2", torrent.instanceName)
    }

    @Test
    fun `cross-instance key matches the stream's delta order key`() {
        val response = json.decodeFromString<TorrentResponse>(
            """
            {
              "cross_instance_torrents": [
                {"hash":"aaa","name":"one","instance_id":3,"instance_name":"EOS-2"},
                {"hash":"bbb","name":"two","instance_id":7,"instance_name":"EOS-7"}
              ],
              "total": 2
            }
            """.trimIndent()
        )

        // Exactly the format qui builds in internal/api/sse/delta.go crossRowKey.
        assertEquals(listOf("3:aaa", "7:bbb"), response.rows.map { it.key })
    }

    @Test
    fun `single-instance rows keep the bare hash as their key`() {
        val torrent = json.decodeFromString<Torrent>("""{"hash":"abc","name":"n"}""")

        assertNull(torrent.instanceId)
        assertEquals("abc", torrent.key)
    }
}
