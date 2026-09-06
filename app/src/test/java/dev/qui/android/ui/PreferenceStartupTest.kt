package dev.qui.android.ui

import androidx.lifecycle.viewModelScope
import dev.qui.android.data.*
import dev.qui.android.data.remote.SessionStore
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceStartupTest {
    @Test fun `startup waits for saved privacy and page instead of visible defaults`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshots = MutableSharedFlow<AppPreferencesStore.Snapshot>(replay = 1)
        val store = mockk<AppPreferencesStore>(relaxed = true)
        every { store.snapshot } returns snapshots
        val session = mockk<SessionStore>()
        every { session.isConfigured } returns flowOf(false)
        val icons = mockk<TrackerIconStore>()
        every { icons.icons } returns MutableStateFlow(emptyMap())
        val root = RootViewModel(store, session, mockk(), icons, mockk())
        try {
            runCurrent()
            assertNull(root.preferences.value)
            val saved = AppPreferencesStore.Snapshot(
                incognito = true, lastMainPage = MainPage.Settings, autoUpdateCheck = false,
            )
            snapshots.emit(saved)
            runCurrent()
            assertEquals(saved, root.preferences.value)
            root.rememberMainPage("detail/1/hash")
            root.rememberMainPage(null)
            runCurrent()
            coVerify(exactly = 0) { store.setLastMainPage(any()) }
            root.rememberMainPage("dashboard")
            runCurrent()
            coVerify(exactly = 1) { store.setLastMainPage(MainPage.Dashboard) }
        } finally {
            root.viewModelScope.cancel()
            Dispatchers.resetMain()
            unmockkAll()
        }
    }
}
