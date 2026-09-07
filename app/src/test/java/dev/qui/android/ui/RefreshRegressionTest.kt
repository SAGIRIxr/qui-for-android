package dev.qui.android.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.qui.android.data.*
import dev.qui.android.data.model.*
import dev.qui.android.data.remote.*
import dev.qui.android.ui.dashboard.DashboardViewModel
import dev.qui.android.ui.detail.DetailTab
import dev.qui.android.ui.detail.TorrentDetailViewModel
import dev.qui.android.ui.torrents.TorrentsViewModel
import dev.qui.android.widget.QuiWidgets
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<QuiRepository>()
    private val prefs = mockk<AppPreferencesStore>(relaxed = true)
    private val history = mockk<SearchHistoryStore>(relaxed = true)
    private val stream = mockk<QuiStreamClient>()
    private val events = MutableSharedFlow<StreamEvent>()
    private val models = mutableListOf<ViewModel>()
    private val instances = listOf(Instance(id = 1, name = "one"), Instance(id = 2, name = "two"))

    private fun regression(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            // viewModelScope is not a child of TestScope; stop polling before
            // runTest drains its scheduler, not in JUnit's later teardown.
            models.forEach { it.viewModelScope.cancel() }
        }
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { prefs.snapshot } returns MutableStateFlow(AppPreferencesStore.Snapshot())
        every { history.entries } returns flowOf(emptyList())
        every { stream.stream(any()) } returns events
        coEvery { repository.instances() } returns Result.success(instances)
        coEvery { repository.capabilities(any()) } returns Result.success(InstanceCapabilities())
        coEvery { repository.categories(any()) } returns Result.success(emptyMap())
        coEvery { repository.tags(any()) } returns Result.success(emptyList())
        coEvery { repository.torrents(any(), any(), any(), any(), any(), any(), any()) } answers {
            Result.success(TorrentResponse(torrents = listOf(Torrent(hash = "hash", name = "fresh")), total = 500))
        }
        coEvery { repository.torrentProperties(any(), any()) } returns Result.success(TorrentProperties())
        coEvery { repository.torrentPeers(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.torrentFiles(any(), any()) } returns Result.success(emptyList())
        mockkObject(QuiWidgets)
        every { QuiWidgets.refresh(any()) } just Runs
    }

    @After fun teardown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun torrents() = TorrentsViewModel(repository, stream, prefs, history).also { models += it }

    @Test fun `list actions deduplicate but both speed limits run and failures retain selection`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.bulkAction(any(), any()) } coAnswers {
            gate.await()
            Result.failure(Exception("denied"))
        }
        val model = torrents()
        runCurrent()
        model.enterSelection("hash")
        model.runAction("setDownloadLimit")
        model.runAction("setDownloadLimit")
        model.runAction("setUploadLimit")
        runCurrent()
        assertEquals(2, model.state.value.actionPending)
        coVerify(exactly = 2) { repository.bulkAction(any(), any()) }
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, model.state.value.actionPending)
        assertEquals("denied", model.state.value.actionError)
        assertEquals(setOf("hash"), model.state.value.selection)
    }

    @Test fun `completed old action does not clear a reselected identical row`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.bulkAction(any(), any()) } coAnswers { gate.await(); Result.success(Unit) }
        val model = torrents()
        runCurrent()
        model.enterSelection("hash")
        model.runAction("pause")
        runCurrent()
        model.clearSelection()
        model.enterSelection("hash")
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.state.value.selectionMode)
        assertTrue(model.state.value.actionSucceeded)
    }

    @Test fun `instance card request selects requested server and rejects missing server`() = regression {
        val model = torrents()
        runCurrent()
        assertTrue(model.state.value.instancesLoaded)
        model.openInstance(2)
        runCurrent()
        assertEquals(2, model.state.value.selectedInstanceId)
        model.openInstance(99)
        assertTrue(model.state.value.instanceUnavailable)
        assertEquals(2, model.state.value.selectedInstanceId)
        model.selectInstance(1)
        assertFalse(model.state.value.instanceUnavailable)
    }
    private fun detail() = TorrentDetailViewModel(repository,
        SavedStateHandle(mapOf("instanceId" to 1, "hash" to "hash")), prefs).also { models += it }

    @Test fun `old instance response cannot overwrite new instance even if cancellation is ignored`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.torrents(1, any(), any(), any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { gate.await() }
            Result.success(TorrentResponse(torrents = listOf(Torrent(name = "old"))))
        }
        val model = torrents()
        runCurrent()
        model.selectInstance(2)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, model.state.value.selectedInstanceId)
        assertEquals("fresh", model.state.value.torrents.single().name)
    }

    @Test fun `load more issues one REST request for the expanded window`() = regression {
        val model = torrents()
        runCurrent()
        model.loadMore()
        model.loadMore()
        runCurrent()
        coVerify(exactly = 1) { repository.torrents(1, 0, 200, any(), any(), any(), any()) }
    }

    @Test fun `stream snapshot takes precedence over an older REST response`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.torrents(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            Result.success(TorrentResponse(torrents = listOf(Torrent(name = "old"))))
        }
        val model = torrents()
        runCurrent()
        events.emit(StreamEvent.Snapshot(StreamPayload(data = TorrentResponse(torrents = listOf(Torrent(name = "live"))))))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("live", model.state.value.torrents.single().name)
    }

    @Test fun `full disk is retained in unified free space`() = regression {
        coEvery { repository.crossInstanceTorrents(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(TorrentResponse())
        coEvery { repository.torrents(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(TorrentResponse(serverState = ServerState(freeSpaceOnDisk = 0)))
        val model = torrents()
        runCurrent()
        model.selectUnified()
        runCurrent()
        advanceTimeBy(1_501)
        runCurrent()
        assertEquals(2, model.state.value.unifiedFreeSpace.size)
        assertEquals(0L, model.state.value.headlineFreeSpace)
    }

    @Test fun `peers load without waiting for slow header properties`() = regression {
        coEvery { repository.torrentProperties(any(), any()) } coAnswers { awaitCancellation() }
        val model = detail()
        model.selectTab(DetailTab.Peers)
        runCurrent()
        coVerify(exactly = 1) { repository.torrentPeers(1, "hash") }
        assertFalse(model.state.value.tabLoading)
    }

    @Test fun `repeated operation is blocked and error survives polling`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.bulkAction(any(), any()) } coAnswers {
            gate.await()
            Result.failure(IllegalStateException("denied"))
        }
        val model = detail()
        runCurrent()
        model.action("pause")
        model.action("pause")
        runCurrent()
        assertTrue(model.state.value.actionBusy)
        gate.complete(Unit)
        runCurrent()
        model.refresh()
        runCurrent()
        coVerify(exactly = 1) { repository.bulkAction(any(), any()) }
        assertEquals("denied", model.state.value.actionError)
        assertFalse(model.state.value.actionBusy)
    }

    @Test fun `file priority failure is visible`() = regression {
        coEvery { repository.setFilePriority(any(), any(), any(), any()) } returns Result.failure(Exception("priority rejected"))
        val model = detail()
        runCurrent()
        model.setFilePriority(listOf(0), 1)
        runCurrent()
        assertEquals("priority rejected", model.state.value.actionError)
    }

    @Test fun `successful operation immediately refreshes detail`() = regression {
        coEvery { repository.bulkAction(any(), any()) } returns Result.success(Unit)
        val model = detail()
        runCurrent()
        model.action("resume")
        runCurrent()
        assertTrue(model.state.value.actionSucceeded)
        coVerify(exactly = 2) { repository.torrentProperties(1, "hash") }
    }

    @Test fun `dashboard publishes healthy instance while another is still loading`() = regression {
        coEvery { repository.torrents(2, any(), any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        val model = DashboardViewModel(mockk<Context>(), repository, prefs).also { models += it }
        model.start()
        runCurrent()
        assertNotNull(model.state.value.cards.first { it.instance.id == 1 }.updatedAt)
        assertTrue(model.state.value.cards.first { it.instance.id == 2 }.refreshing)
    }

    @Test fun `dashboard limits duplicate speed toggles and surfaces failure`() = regression {
        coEvery { repository.toggleAltSpeedLimits(1) } returns Result.failure(Exception("forbidden"))
        val model = DashboardViewModel(mockk<Context>(), repository, prefs).also { models += it }
        model.toggleAltSpeedLimits(1)
        model.toggleAltSpeedLimits(1)
        runCurrent()
        coVerify(exactly = 1) { repository.toggleAltSpeedLimits(1) }
        assertEquals("forbidden", model.state.value.actionError)
        assertTrue(model.state.value.pendingActions.isEmpty())
    }

    @Test fun `dashboard retains timestamp and marks stale when instance discovery fails`() = regression {
        val model = DashboardViewModel(mockk<Context>(), repository, prefs).also { models += it }
        model.start()
        runCurrent()
        val timestamp = model.state.value.cards.first().updatedAt
        coEvery { repository.instances() } returns Result.failure(Exception("offline"))
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals("offline", model.state.value.error)
        assertEquals(timestamp, model.state.value.cards.first().updatedAt)
        assertTrue(model.state.value.cards.none { it.isHealthy })
        assertEquals(0, model.state.value.totalTorrents)
    }

    @Test fun `web seed cache avoids duplicate reads and explicit refresh invalidates it`() = regression {
        coEvery { repository.torrentWebSeeds(any(), any()) } returns Result.success(emptyList())
        val model = detail()
        model.selectTab(DetailTab.WebSeeds)
        runCurrent()
        model.selectTab(DetailTab.Peers)
        runCurrent()
        model.selectTab(DetailTab.WebSeeds)
        runCurrent()
        coVerify(exactly = 1) { repository.torrentWebSeeds(1, "hash") }
        model.refresh()
        runCurrent()
        coVerify(exactly = 2) { repository.torrentWebSeeds(1, "hash") }
    }

    @Test fun `late metadata cannot overwrite newly selected instance`() = regression {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.tags(1) } coAnswers {
            withContext(NonCancellable) { gate.await() }
            Result.success(listOf("old"))
        }
        coEvery { repository.tags(2) } returns Result.success(listOf("new"))
        val model = torrents()
        runCurrent()
        model.selectInstance(2)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), model.state.value.tags)
    }
}
