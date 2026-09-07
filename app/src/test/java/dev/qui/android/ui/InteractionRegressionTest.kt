package dev.qui.android.ui

import dev.qui.android.data.model.*
import dev.qui.android.ui.detail.*
import dev.qui.android.ui.torrents.*
import org.junit.Assert.*
import org.junit.Test

class InteractionRegressionTest {
    @Test fun `back closes local interaction before navigating and delegates at root`() {
        assertEquals(TorrentBackAction.CloseSwipe, torrentBackAction(true, true, true))
        assertEquals(TorrentBackAction.ClearSelection, torrentBackAction(false, true, true))
        assertEquals(TorrentBackAction.ClearSelection, torrentBackAction(false, true, false))
        assertEquals(TorrentBackAction.PreviousPage, torrentBackAction(false, false, true))
        assertEquals(TorrentBackAction.System, torrentBackAction(false, false, false))
    }
    @Test fun `back follows visits including cycles and ignores duplicate tab taps`() {
        var history = listOf("settings")
        for (page in listOf("dashboard", "torrents", "torrents", "settings")) {
            history = visitMainPage(history, page)
        }
        assertEquals(listOf("settings", "dashboard", "torrents", "settings"), history)
        for (expected in listOf("torrents", "dashboard", "settings")) {
            assertEquals(expected, previousMainPage(history))
            history = history.dropLast(1)
        }
        assertNull(previousMainPage(history))
    }

    @Test fun `privacy masks every detail tab without mutating identities used by actions`() {
        val raw = DetailUiState(
            hash = "secret-hash",
            torrent = Torrent(hash = "secret-hash", name = "secret-name", category = "secret-category",
                tags = "secret-tags", tracker = "secret-tracker", savePath = "secret-path",
                downloadPath = "secret-download", contentPath = "secret-content", magnetUri = "secret-magnet",
                infohashV1 = "secret-v1", infohashV2 = "secret-v2", instanceName = "secret-instance"),
            properties = TorrentProperties(name = "secret-name", hash = "secret-hash", comment = "secret-comment",
                createdBy = "secret-creator", infohashV1 = "secret-v1", infohashV2 = "secret-v2",
                savePath = "secret-path", downloadPath = "secret-download"),
            trackers = listOf(TorrentTracker(url = "secret-tracker", msg = "secret-passkey")),
            peers = listOf(TorrentPeer(key = "secret-key", ip = "secret-ip", port = 1234,
                client = "secret-client", peerIdClient = "secret-peer", files = "secret-files")),
            files = listOf(TorrentFile(index = 7, name = "secret-file", size = 123, priority = 6)),
            webSeeds = listOf(WebSeed("secret-webseed")), error = "secret-error", actionError = "secret-error",
        )
        val display = raw.forDisplay(true)
        assertFalse(display.toString().contains("secret"))
        assertEquals(7, display.files.single().index)
        assertEquals(123L, display.files.single().size)
        assertEquals(6, display.files.single().priority)
        assertNull(display.peers.single().port)
        assertSame(raw, raw.forDisplay(false))
        assertEquals("secret-hash", raw.hash)
        assertEquals("secret-file", raw.files.single().name)
    }
}
