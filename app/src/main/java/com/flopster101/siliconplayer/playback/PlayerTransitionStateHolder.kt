package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Single observable holder for player drag/animation state.
 *
 * Bundling these flags into one [remember]-ed object keeps the Compose-generated
 * bytecode for `AppNavigation` under the 64KB JVM method limit while still letting
 * any composable observe individual fields reactively (each property is backed by
 * its own `mutableStateOf`/`mutableFloatStateOf`).
 *
 * The holder also exposes a derived [isAnyAnimating] getter that the playback poll
 * loop reads via a provider lambda. While any drag/animation is in progress the
 * poll loop suspends position/duration publishing so the UI thread can finish
 * the heavier composition / layout work without contention.
 */
@Stable
internal class PlayerTransitionStateHolder {
    var miniExpandPreviewProgress by mutableFloatStateOf(0f)
    var expandFromMiniDrag by mutableStateOf(false)
    var collapseFromSwipe by mutableStateOf(false)
    var collapseDragInProgress by mutableStateOf(false)
    var expandedOverlayCurrentVisible by mutableStateOf(false)
    var expandedOverlaySettledVisible by mutableStateOf(false)

    /**
     * Short grace period after [PlayerTransitionStateHolder.driveTapTransition] is
     * triggered by an [isPlayerExpanded] change. Covers tap-to-expand /
     * tap-to-collapse paths that do not go through a finger drag.
     */
    var isTapTransitioning by mutableStateOf(false)

    val isAnyAnimating: Boolean
        get() = isTapTransitioning ||
            collapseDragInProgress ||
            expandFromMiniDrag ||
            miniExpandPreviewProgress > 0f
}

/**
 * Drives [PlayerTransitionStateHolder.isTapTransitioning] from changes to
 * [isPlayerExpanded]. The first observed value is treated as the initial state and
 * does not produce a spurious transition window on app launch.
 */
@Composable
internal fun PlayerTransitionStateHolder.DriveTapTransition(
    isPlayerExpanded: Boolean,
    transitionDurationMs: Long = 400L
) {
    var hasObservedInitial by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayerExpanded) {
        if (!hasObservedInitial) {
            hasObservedInitial = true
            return@LaunchedEffect
        }
        isTapTransitioning = true
        delay(transitionDurationMs)
        isTapTransitioning = false
    }
}
