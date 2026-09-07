package dev.qui.android.ui

import androidx.lifecycle.viewModelScope
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.model.*
import dev.qui.android.ui.addintent.TorrentPayload
import dev.qui.android.ui.torrents.AddTorrentViewModel
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddTorrentRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<QuiRepository>()
    private lateinit var model: AddTorrentViewModel
    @Before fun setup() { Dispatchers.setMain(dispatcher); model = AddTorrentViewModel(repository) }
    @After fun teardown() { model.viewModelScope.cancel(); Dispatchers.resetMain(); unmockkAll() }

    @Test fun `partial response retains only failed items and retry closes on complete success`() = runTest(dispatcher) {
        val requests = mutableListOf<List<String>>()
        val files = mutableListOf<List<Pair<String, ByteArray>>>()
        coEvery { repository.addTorrent(any(), capture(requests), capture(files), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
            Result.success(AddTorrentResponse(added = 2, failed = 2,
                failedURLs = listOf(AddTorrentFailedUrl("bad", "denied")),
                failedFiles = listOf(AddTorrentFailedFile("bad.torrent", "denied")))),
            Result.success(AddTorrentResponse(added = 2)),
        )
        model.setUrls("good\nbad")
        model.addFiles(listOf(TorrentPayload("good.torrent", byteArrayOf(1)), TorrentPayload("bad.torrent", byteArrayOf(2))))
        var closed = 0
        var refreshed = 0
        model.submit(1, { closed++ }, { refreshed++ })
        runCurrent()
        assertEquals(0, closed)
        assertEquals(1, refreshed)
        assertEquals(2, model.state.value.addedCount)
        assertEquals("bad", model.state.value.urls)
        assertEquals("bad.torrent", model.state.value.files.single().filename)
        model.submit(1, { closed++ })
        runCurrent()
        assertEquals(listOf("bad"), requests.last())
        assertEquals("bad.torrent", files.last().single().first)
        assertEquals(1, closed)
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `busy submission blocks double taps and edits and failure keeps draft`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.addTorrent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await(); Result.failure(Exception("offline"))
        }
        model.setUrls("original")
        model.submit(1, {})
        model.submit(1, {})
        model.setUrls("changed")
        runCurrent()
        coVerify(exactly = 1) { repository.addTorrent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals("original", model.state.value.urls)
        gate.complete(Unit)
        runCurrent()
        assertFalse(model.state.value.isBusy)
        assertEquals("offline", model.state.value.error)
        assertEquals("original", model.state.value.urls)
    }
}
