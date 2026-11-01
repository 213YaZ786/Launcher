package com.yaz.launcher.feature.home

// The drag state of the panels. AnchoredDraggableState and Modifier.anchoredDraggable are Compose's official recipe
// (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling, "Anchored draggable"): one state
// with anchors, set when the layout is measured, the offset read to place what follows the finger.
// The nested scroll connection that hands a list's leftover drag to the state is Material 3's sheet way
// (androidx material3 SheetDefaults.kt, ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection:
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt),
// written for either axis and either way out: a drag the list cannot use moves the panel (onPostScroll), a drag back first
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
): NestedScrollConnection =
    // The panel closes towards its edge: to the right for the right panel (+1), to the left for the left one (-1).
    closeConnection(open, Side.CLOSED, if (open == Side.RIGHT) 1f else -1f, vertical = false, minFling = minFling, spec = spec)

/**
 * Closes the Apps panel open at the bottom with the drag the vertical grid leaves over: the grid at its top cannot go further down, so the
 * panel follows the finger down; a drag up inside the grid scrolls it. The panel closes downwards (+1), as Trebuchet's drawer closes by a
 * downward drag only once its list is at the top (AllAppsSwipeController.canInterceptTouch asks shouldContainerScroll,
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AllAppsSwipeController.java).
 */
internal fun AnchoredDraggableState<Sheet>.closeWithScrollDown(
    minFling: Float,
    spec: AnimationSpec<Float>
): NestedScrollConnection = closeConnection(Sheet.OPEN, Sheet.CLOSED, 1f, vertical = true, minFling = minFling, spec = spec)

// The connection of Material 3's ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection (SheetDefaults.kt, vertical and closing downwards there): [vertical] picks the
// axis, [out] the sign of the way out (+1 down or right, -1 left), [open] and [closed] the two places of the state.
private fun <T> AnchoredDraggableState<T>.closeConnection(
    open: T,
    closed: T,
    out: Float,
    vertical: Boolean,
    minFling: Float,
    spec: AnimationSpec<Float>
): NestedScrollConnection = object : NestedScrollConnection {

    private val openAt: Float get() = anchors.positionOf(open)

    // The component on the state's axis, as Material 3's Offset.toFloat() and Velocity.toFloat() in
    // ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt);
    // the velocity one is a plain function, as the two extensions share one JVM signature.
    private fun Offset.along(): Float = if (vertical) y else x
    private fun alongVelocity(v: Velocity): Float = if (vertical) v.y else v.x
    private fun offsetOf(value: Float): Offset = if (vertical) Offset(0f, value) else Offset(value, 0f)
    private fun velocityOf(value: Float): Velocity = if (vertical) Velocity(0f, value) else Velocity(value, 0f)

    // settleWith, after Material 3's SheetState.settle (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt):
    // settle(velocity) is deprecated and throws on a state built without thresholds, so a fling faster than the platform's minimum
    // (ViewConfiguration.getScaledMinimumFlingVelocity) goes the way it points, any other to the nearest anchor (settle(animationSpec)).
    // The state is built without specs (its snapAnimationSpec is never set and throws): the caller's spring is passed in.
    private suspend fun settleWith(velocity: Float) {
        when {
            velocity * out > minFling -> animateTo(closed, spec)
            velocity * out < -minFling -> animateTo(open, spec)
            else -> settle(spec)
        }
    }

    private fun partlyClosed(): Boolean = (offsetOrZero() - openAt) * out > 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.along()
        return if (delta * out < 0f && source == NestedScrollSource.UserInput && partlyClosed()) offsetOf(dispatchRawDelta(delta)) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val delta = available.along()
        return if (delta * out > 0f && source == NestedScrollSource.UserInput) offsetOf(dispatchRawDelta(delta)) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = alongVelocity(available)
        return if (velocity * out < 0f && partlyClosed()) {
            settleWith(velocity)
            velocityOf(velocity)
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settleWith(alongVelocity(available))
        return velocityOf(alongVelocity(available))
    }
}
