package com.yaz.launcher.feature.home

// The drag state of the panels. AnchoredDraggableState and Modifier.anchoredDraggable are Compose's official recipe
// (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling, "Anchored draggable"): one state
// with anchors, set when the layout is measured, the offset read to place what follows the finger.
// The nested scroll connection that hands a list's leftover drag to the state is Material 3's sheet way
// (androidx material3 SheetDefaults.kt, ConsumeSwipeWithNestedScroll:
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt),
// written for the horizontal axis and for either side: a drag the list cannot use moves the panel (onPostScroll), a drag back first
// returns the panel to its open place (onPreScroll), and the fling settles the state (onPreFling, onPostFling).
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.yaz.launcher.data.settings.PanelKind

/** The horizontal places of the two side panels: one in from the left, one in from the right. */
enum class Side { LEFT, CLOSED, RIGHT }

/** The vertical places of the bottom panel. */
enum class Sheet { CLOSED, OPEN }

/** An edge of the screen a panel comes in from; [NONE] is none (the screenshot tests start with one open). */
enum class Panel { NONE, LEFT, RIGHT, BOTTOM }

/** Which panel each edge holds: what a swipe right, a swipe left and a swipe up open. */
data class PanelLayout(
    val left: PanelKind = PanelKind.NONE,
    val right: PanelKind = PanelKind.APPS,
    val bottom: PanelKind = PanelKind.RECENTS
) {
    /** What the panel on [edge] holds. */
    fun at(edge: Panel): PanelKind = when (edge) {
        Panel.LEFT -> left
        Panel.RIGHT -> right
        Panel.BOTTOM -> bottom
        Panel.NONE -> PanelKind.NONE
    }
}

/** The offset, 0 until the anchors are set. */
internal fun AnchoredDraggableState<*>.offsetOrZero(): Float = offset.let { if (it.isNaN()) 0f else it }

/**
 * Closes the Apps panel open on [open] (left or right) with the drag the grid leaves over: at the grid's edge on the closing side, it
 * cannot go further and the panel follows the finger out; elsewhere the grid takes the swipe.
 */
internal fun AnchoredDraggableState<Side>.closeWithScroll(
    open: Side,
    minFling: Float,
    spec: AnimationSpec<Float>
): NestedScrollConnection = object : NestedScrollConnection {

    // The panel closes towards its edge: to the right for the right panel (+1), to the left for the left one (-1).
    private val out: Float = if (open == Side.RIGHT) 1f else -1f

    private val openAt: Float get() = anchors.positionOf(open)

    // settleWith, after Material 3's SheetState.settle (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt):
    // settle(velocity) is deprecated and throws on a state built without thresholds, so a fling faster than the platform's minimum
    // (ViewConfiguration.getScaledMinimumFlingVelocity) goes the way it points, any other to the nearest anchor (settle(animationSpec)).
    // The state is built without specs (its snapAnimationSpec is never set and throws): the caller's spring is passed in.
    private suspend fun settleWith(velocity: Float) {
        when {
            velocity * out > minFling -> animateTo(Side.CLOSED, spec)
            velocity * out < -minFling -> animateTo(open, spec)
            else -> settle(spec)
        }
    }

    private fun partlyClosed(): Boolean = (offsetOrZero() - openAt) * out > 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.x
        return if (delta * out < 0f && source == NestedScrollSource.UserInput && partlyClosed()) Offset(dispatchRawDelta(delta), 0f) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val delta = available.x
        return if (delta * out > 0f && source == NestedScrollSource.UserInput) Offset(dispatchRawDelta(delta), 0f) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = available.x
        return if (velocity * out < 0f && partlyClosed()) {
            settleWith(velocity)
            Velocity(velocity, 0f)
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settleWith(available.x)
        return Velocity(available.x, 0f)
    }
}
