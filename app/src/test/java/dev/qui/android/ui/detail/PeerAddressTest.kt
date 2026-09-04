package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentPeer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerAddressTest {

    @Test
    fun `canonical key wins for IPv4 IPv6 and I2P`() {
        assertEquals(
            "192.0.2.1:6881",
            formatPeerAddress(TorrentPeer(key = "192.0.2.1:6881", ip = "ignored", port = 1)),
        )
        assertEquals(
            "[2001:db8::1]:6881",
            formatPeerAddress(TorrentPeer(key = "[2001:db8::1]:6881")),
        )
        assertEquals(
            "exampledestination.b32.i2p",
            formatPeerAddress(TorrentPeer(key = "exampledestination.b32.i2p")),
        )
    }

    @Test
    fun `falls back to IPv4 and bracketed IPv6`() {
        assertEquals(
            "192.0.2.2:8080",
            formatPeerAddress(TorrentPeer(ip = "192.0.2.2", port = 8080)),
        )
        assertEquals(
            "[2001:db8::2]:8080",
            formatPeerAddress(TorrentPeer(ip = "2001:db8::2", port = 8080)),
        )
    }

    @Test
    fun `falls back to IP alone and dash for missing fields`() {
        assertEquals("192.0.2.3", formatPeerAddress(TorrentPeer(ip = "192.0.2.3")))
        assertEquals("192.0.2.3", formatPeerAddress(TorrentPeer(ip = "192.0.2.3", port = 0)))
        assertEquals("-", formatPeerAddress(TorrentPeer()))
    }

    @Test
    fun `missing JSON address fields remain null`() {
        val peer = Json { ignoreUnknownKeys = true }.decodeFromString<TorrentPeer>("{}")

        assertNull(peer.ip)
        assertNull(peer.port)
    }
}
