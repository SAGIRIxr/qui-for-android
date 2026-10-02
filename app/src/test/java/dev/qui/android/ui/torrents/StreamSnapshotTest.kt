package dev.qui.android.ui.torrents

import dev.qui.android.data.model.*
import dev.qui.android.data.remote.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StreamSnapshotTest {
    private val version = StreamVersion(7, 1)
    private val first = TorrentResponse(
        torrents = listOf(Torrent(hash = "a", name = "first"), Torrent(hash = "b", name = "second")),
        total = 2,
        counts = TorrentCounts(total = 2),
        categories = mapOf("old" to Category("old")),
        tags = listOf("old"),
    )

    private fun seeded() = StreamSnapshot().also {
        it.accept(StreamPayload(data = first, version = version), delta = false)
    }

    @Test fun `aggregate-only delta refreshes metrics retains counts and clears omitted collections`() {
        val result = seeded().accept(StreamPayload(
            data = TorrentResponse(total = 3, stats = TorrentStats(totalDownloadSpeed = 123),
                serverState = ServerState(freeSpaceOnDisk = 42), partialResults = true, hasMore = true),
            delta = StreamDelta(baseVersion = version), version = StreamVersion(7, 2),
        ), delta = true)
        assertEquals(first.rows, result.rows)
        assertEquals(first.counts, result.counts)
        assertEquals(123L, result.stats?.totalDownloadSpeed)
        assertEquals(42L, result.serverState?.freeSpaceOnDisk)
        assertEquals(3, result.total)
        assertTrue(result.partialResults)
        assertEquals(true, result.hasMore)
        assertTrue(result.categories!!.isEmpty())
        assertTrue(result.tags!!.isEmpty())
    }

    @Test fun `missed duplicate and mismatched generations reject deltas`() {
        val invalidVersions = listOf(
            version to StreamVersion(7, 3),
            version to version,
            version to StreamVersion(8, 2),
            StreamVersion(7, 2) to StreamVersion(7, 3),
            null to StreamVersion(7, 2),
            version to null,
            null to null,
        )
        invalidVersions.forEach { (base, next) ->
            assertThrows(IOException::class.java) {
                seeded().accept(StreamPayload(data = first, delta = StreamDelta(baseVersion = base), version = next), true)
            }
        }
    }

    @Test fun `REST data cannot substitute for missing stream baseline`() {
        assertThrows(IOException::class.java) {
            StreamSnapshot().accept(StreamPayload(data = first, delta = StreamDelta(baseVersion = version),
                version = StreamVersion(7, 2)), true)
        }
    }

    @Test fun `legacy server can update and clear rows without versions`() {
        val baseline = StreamSnapshot()
        baseline.accept(StreamPayload(data = first), false)
        val changed = baseline.accept(StreamPayload(data = TorrentResponse(
            torrents = listOf(Torrent(hash = "b", name = "changed")), total = 1),
            delta = StreamDelta(order = listOf("b"))), true)
        assertEquals("changed", changed.rows.single().name)
        val empty = baseline.accept(StreamPayload(data = TorrentResponse(), delta = StreamDelta(order = emptyList())), true)
        assertTrue(empty.rows.isEmpty())
    }

    @Test fun `unified delta addresses same hash on different clients independently`() {
        val baseline = StreamSnapshot()
        val one = Torrent(hash = "same", instanceId = 1, name = "one")
        val two = Torrent(hash = "same", instanceId = 2, name = "two")
        baseline.accept(StreamPayload(data = TorrentResponse(crossInstanceTorrents = listOf(one, two), total = 2), version = version), false)
        val result = baseline.accept(StreamPayload(data = TorrentResponse(
            crossInstanceTorrents = listOf(two.copy(name = "changed")), total = 2),
            delta = StreamDelta(order = listOf("2:same", "1:same"), baseVersion = version), version = StreamVersion(7, 2)), true)
        assertEquals(listOf("changed", "one"), result.rows.map { it.name })
        assertTrue(result.torrents.isEmpty())
    }

    @Test fun `missing ordered row rejects incomplete page instead of silently dropping it`() {
        assertThrows(IOException::class.java) {
            seeded().accept(StreamPayload(data = TorrentResponse(),
                delta = StreamDelta(order = listOf("missing"), baseVersion = version), version = StreamVersion(7, 2)), true)
        }
    }

    @Test fun `wire versions are decoded and invalid full versions rejected`() {
        val payload = Json.decodeFromString<StreamPayload>("""{
            "type":"delta","data":{"total":2},"version":{"major":7,"minor":2},
            "delta":{"baseVersion":{"major":7,"minor":1}}
        }""")
        assertEquals(first.rows, seeded().accept(payload, true).rows)
        assertThrows(IOException::class.java) {
            StreamSnapshot().accept(StreamPayload(data = first, version = StreamVersion(0, 1)), false)
        }
    }
}
