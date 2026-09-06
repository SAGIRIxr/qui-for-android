/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's MobileScrollContext. Two bars stacked at the bottom of a phone screen
 * eat a lot of a torrent list, so scrolling down retracts them and scrolling back up
 * brings them straight back. The state is shared because the nav bar lives in the app
 * shell while the action bar lives in the torrent screen, and they must move together.
 */

package dev.qui.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/** Ignore jitter below this, so a shaky thumb does not flap the bars. */
private const val THRESHOLD_PX = 12f

class MobileScrollState {

    var barsVisible by mutableStateOf(true)
        private set

    private var accumulated = 0f

    /** `delta` is the scroll offset consumed by the list: negative means finger up. */
    fun onScroll(delta: Float) {
        if (delta == 0f || !delta.isFinite()) return
        // Direction changes restart the count, so a reversal reacts immediately rather
        // than having to first undo the distance travelled the other way.
        if (delta > 0 != accumulated > 0) accumulated = 0f
        accumulated += delta

        if (abs(accumulated) < THRESHOLD_PX) return
        barsVisible = accumulated > 0
        accumulated = 0f
    }

    /** Forces the bars back, e.g. once the list is at the top again. */
    fun show() {
        accumulated = 0f
        barsVisible = true
    }
}

val LocalMobileScroll = staticCompositionLocalOf { MobileScrollState() }

/** One clock for the footer and action row, even though they live in nested Scaffolds. */
val LocalBottomBarsTransition = staticCompositionLocalOf<Transition<Boolean>> {
    error("Bottom bar transition must be provided by the app shell")
}

@Composable
fun CollapsingBottomBar(content: @Composable AnimatedVisibilityScope.() -> Unit) {
    LocalBottomBarsTransition.current.AnimatedVisibility(
        visible = { it },
        // Animate measured height, not just drawing offset. Scaffold can release the
        // space to the list on every frame instead of leaving a blank until the end.
        enter = expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top),
        exit = shrinkVertically(animationSpec = tween(220), shrinkTowards = Alignment.Top),
        content = content,
    )
}

/** Feeds list scrolling into [MobileScrollState] without consuming any of it. */
fun MobileScrollState.nestedScrollConnection(): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Overscroll and an unscrollable list must not retract the controls.
            onScroll(consumed.y)
            return Offset.Zero
        }
    }
