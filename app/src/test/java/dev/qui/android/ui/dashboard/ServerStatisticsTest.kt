package dev.qui.android.ui.dashboard

import dev.qui.android.data.model.Instance
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStatisticsTest {

    @Test
    fun `excludes empty and failed instances`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(id = 1),
                    card(id = 2, downloaded = 0, uploaded = 0),
                    card(id = 3, downloaded = 10, errorRes = 1),
                    card(id = 4, downloaded = 10, uploaded = 5),
                )
            )
        )

        assertEquals(listOf(4), statistics.rows.map { it.instanceId })
    }

    @Test
    fun `sums totals and calculates weighted share ratio`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(
                        id = 1,
                        downloaded = 100,
                        uploaded = 50,
                        downloadedSession = 10,
                        uploadedSession = 20,
                    ),
                    card(
                        id = 2,
                        downloaded = 300,
                        uploaded = 600,
                        downloadedSession = 30,
                        uploadedSession = 60,
                        peers = 7,
                    ),
                )
            )
        )

        assertEquals(400L, statistics.totalDownloaded)
        assertEquals(650L, statistics.totalUploaded)
        assertEquals(1.625, statistics.shareRatio, 0.0)
        assertEquals(7L, statistics.totalPeerConnections)
        assertEquals(10L, statistics.rows.first().downloadedSession)
        assertEquals(60L, statistics.rows.last().uploadedSession)
    }

    @Test
    fun `uses zero ratio when cumulative download is zero`() {
        val statistics = requireNotNull(
            buildServerStatistics(listOf(card(id = 1, downloaded = 0, uploaded = 50)))
        )

        assertEquals(0.0, statistics.shareRatio, 0.0)
        assertEquals(0.0, statistics.rows.single().shareRatio, 0.0)
    }

    @Test
    fun `preserves unknown and explicit zero peer counts`() {
        val statistics = requireNotNull(
            buildServerStatistics(
                listOf(
                    card(id = 1, downloaded = 10, peers = null),
                    card(id = 2, downloaded = 20, peers = 0),
                )
            )
        )

        assertEquals(listOf(null, 0L), statistics.rows.map { it.peerConnections })
        assertEquals(0L, statistics.totalPeerConnections)
    }

    @Test
    fun `returns null when no instance has transfer history`() {
        assertEquals(null, buildServerStatistics(listOf(card(id = 1))))
    }

    private fun card(
        id: Int,
        downloaded: Long? = null,
        uploaded: Long? = null,
        downloadedSession: Long = 0,
        uploadedSession: Long = 0,
        peers: Long? = null,
        errorRes: Int? = null,
    ) = InstanceCard(
        instance = Instance(id = id, name = "client-$id"),
        sessionDownloaded = downloadedSession,
        sessionUploaded = uploadedSession,
        allTimeDownloaded = downloaded,
        allTimeUploaded = uploaded,
        peerConnections = peers,
        errorRes = errorRes,
    )
}
