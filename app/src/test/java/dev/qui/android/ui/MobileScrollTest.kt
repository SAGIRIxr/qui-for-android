package dev.qui.android.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class MobileScrollTest {
    @Test fun `outgoing screen fling cannot overwrite the destination reset`() {
        val state = MobileScrollState()
        var resumed = true
        val connection = state.nestedScrollConnection { resumed }
        connection.onPostScroll(Offset(0f, -1000f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(state.barsVisible)
        resumed = false
        state.show()
        assertEquals(Offset.Zero, connection.onPostScroll(
            Offset(0f, -100f), Offset.Zero, NestedScrollSource.SideEffect,
        ))
        assertTrue(state.barsVisible)
        resumed = true
        connection.onPostScroll(Offset(0f, -20f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(state.barsVisible)
    }

    @Test fun `inactive callbacks do not add to the next gesture threshold`() {
        val state = MobileScrollState()
        var resumed = false
        val connection = state.nestedScrollConnection { resumed }
        connection.onPostScroll(Offset(0f, -8f), Offset.Zero, NestedScrollSource.SideEffect)
        resumed = true
        connection.onPostScroll(Offset(0f, -5f), Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(state.barsVisible)
    }

    @Test fun `dashboard stays visible when a late fling arrives after the back reset`() {
        val state = MobileScrollState()
        state.onScroll(-1000f)
        assertFalse(bottomBarsVisible(Routes.TORRENTS, state.barsVisible))
        state.show() // The navigation effect currently performs this once on Back.
        state.onScroll(-60f) // Outgoing screen can still be composed during the transition.
        assertTrue(bottomBarsVisible(Routes.DASHBOARD, state.barsVisible))
    }

    @Test fun `non list destinations never inherit hidden scrolling controls`() {
        listOf(Routes.DASHBOARD, Routes.SETTINGS, Routes.DETAIL, Routes.LOGIN, null).forEach { route ->
            assertTrue("Hidden scroll state leaked into $route", bottomBarsVisible(route, false))
        }
    }

    @Test fun `only the torrent page follows scroll hiding and showing`() {
        assertFalse(bottomBarsVisible(Routes.TORRENTS, false))
        assertTrue(bottomBarsVisible(Routes.TORRENTS, true))
    }

    @Test fun `repeated back cycles cannot strand the dashboard without navigation`() {
        val state = MobileScrollState()
        repeat(4) {
            state.onScroll(-500f)
            assertFalse(bottomBarsVisible(Routes.TORRENTS, state.barsVisible))
            assertTrue(bottomBarsVisible(Routes.DASHBOARD, state.barsVisible))
            state.show()
        }
    }

    @Test fun `small movements do not flap controls`() {
        val state = MobileScrollState()
        state.onScroll(-5f)
        assertTrue(state.barsVisible)
        state.onScroll(-7f)
        assertFalse(state.barsVisible)
    }

    @Test fun `reversing direction shows controls without undoing earlier travel`() {
        val state = MobileScrollState()
        state.onScroll(-100f)
        state.onScroll(-8f)
        state.onScroll(12f)
        assertTrue(state.barsVisible)
    }

    @Test fun `zero and invalid deltas do not discard partial upward progress`() {
        val state = MobileScrollState()
        state.onScroll(-12f)
        state.onScroll(7f)
        state.onScroll(0f)
        state.onScroll(Float.NaN)
        state.onScroll(Float.POSITIVE_INFINITY)
        state.onScroll(5f)
        assertTrue(state.barsVisible)
    }

    @Test fun `unconsumed overscroll does not hide controls or consume list movement`() {
        val state = MobileScrollState()
        val connection = state.nestedScrollConnection()
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.UserInput))
        assertEquals(Offset.Zero, connection.onPostScroll(
            Offset.Zero, Offset(0f, -100f), NestedScrollSource.UserInput,
        ))
        assertTrue(state.barsVisible)
        assertEquals(Offset.Zero, connection.onPostScroll(
            Offset(0f, -20f), Offset.Zero, NestedScrollSource.UserInput,
        ))
        assertFalse(state.barsVisible)
    }

    @Test fun `show resets direction tracking for the next gesture`() {
        val state = MobileScrollState()
        state.onScroll(-16f)
        state.onScroll(-8f)
        state.show()
        state.onScroll(-5f)
        assertTrue(state.barsVisible)
    }
}
