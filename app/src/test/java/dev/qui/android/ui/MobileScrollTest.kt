package dev.qui.android.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class MobileScrollTest {
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
