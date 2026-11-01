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
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateTo
import kotlin.math.abs
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

// The release thresholds of Trebuchet's touch controllers.
/** LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS, the share of the travel that commits any other transition (lineage-22.2, src/com/android/launcher3/LauncherAnimUtils.java). */
private const val SUCCESS_TRANSITION_PROGRESS = 0.5f

/** LauncherAnimUtils.TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS, the share that commits the Apps sheet on a tablet. */
private const val TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS = 0.3f

/** AllAppsSwipeController.ALL_APPS_STATE_TRANSITION_MANUAL: opening Apps on a phone needs more than 0.4 of the travel, closing it more than 1 - 0.4. */
private const val ALL_APPS_STATE_TRANSITION_MANUAL = 0.4f

/**
 * The share of the travel a release must pass to commit, as AbstractStateChangeTouchController.onDragEnd chooses `successTransitionProgress`
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AbstractStateChangeTouchController.java):
 * on a tablet 0.3 whenever Apps is one end; on a phone 0.4 from home to Apps and 1 - 0.4 from Apps to home; 0.5 for the rest (Recents,
 * Trebuchet's Overview). [fromApps] and [toApps] say that the end the drag comes from, or goes to, is the Apps panel; the other end is
 * always home (closed) or Recents here, as the panels are neighbours of the closed place.
 */
internal fun successProgress(tablet: Boolean, fromApps: Boolean, toApps: Boolean): Float = when {
    tablet && (toApps || fromApps) -> TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS
    !tablet && toApps -> ALL_APPS_STATE_TRANSITION_MANUAL
    !tablet && fromApps -> 1 - ALL_APPS_STATE_TRANSITION_MANUAL
    else -> SUCCESS_TRANSITION_PROGRESS
}

/**
 * Trebuchet's fling speed: BaseSwipeDetector.isFling is `abs(velocity) > mReleaseVelocity`, mReleaseVelocity being the pixel size of
 * R.dimen.base_swift_detector_fling_release_velocity (1dp, res/values/dimens.xml), compared with a velocity in pixels per millisecond
 * (computeCurrentVelocity(1000) / 1000): 1 dp per ms. Here the velocities are in pixels per second: [pxPerDp] times 1000.
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/BaseSwipeDetector.java)
 */
internal fun flingVelocityPx(pxPerDp: Float): Float = pxPerDp * 1000f

/**
 * Where a release ends, after AbstractStateChangeTouchController.onDragEnd (the same file as [successProgress]): the transition runs between
 * two neighbouring anchors, `from` (the state the drag started in) and `to`. A fling (faster than [flingVelocity], px/s) goes to `to` if it
 * points that way, else back to `from`; without a fling the drag commits to `to` when its progress passes [success]
 * (`interpolatedProgress > successTransitionProgress`, linear here as AllAppsSwipeController's ALL_APPS_VERTICAL_PROGRESS_MANUAL), else it
 * returns to `from`. Difference: Trebuchet keeps `mFromState` in the controller; here it is the state's [AnchoredDraggableState.settledValue],
 * or the end of the segment nearer to it when the drag has crossed an anchor (reinitCurrentAnimation takes the reached state as the new from).
 * Its FlingBlockCheck (a fling blocked at the start of a drag from home) is not ported.
 */
internal fun <T> AnchoredDraggableState<T>.releaseTarget(velocity: Float, flingVelocity: Float, success: (from: T, to: T) -> Float): T {
    val offset = offsetOrZero()
    val low = anchors.closestAnchor(offset, false) ?: return settledValue
    val high = anchors.closestAnchor(offset, true) ?: return settledValue
    val fling = abs(velocity) > flingVelocity
    val lowAt = anchors.positionOf(low)
    val highAt = anchors.positionOf(high)
    if (lowAt == highAt) {
        // On an anchor: a fling leaves it towards the next one (progress <= 0 in onDragEnd), anything else stays.
        val next = if (fling) anchors.closestAnchor(offset + if (velocity > 0f) 1f else -1f, velocity > 0f) else null
        return next ?: low
    }
    val settledAt = anchors.positionOf(settledValue)
    val from = when {
        settledValue == low -> low
        settledValue == high -> high
        settledAt.isNaN() -> low
        abs(settledAt - lowAt) <= abs(settledAt - highAt) -> low
        else -> high
    }
    val to = if (from == low) high else low
    val fromAt = if (from == low) lowAt else highAt
    val toAt = if (from == low) highAt else lowAt
    val progress = abs(offset - fromAt) / abs(toAt - fromAt)
    return if (fling) {
        if ((velocity > 0f) == (toAt > fromAt)) to else from
    } else {
        if (progress > success(from, to)) to else from
    }
}

/**
 * The fling of the two panel states: [releaseTarget]'s end reached on [spec] from the finger's velocity (Trebuchet's onDragEnd animates the
 * progress from where it is to the end; the spring is the panels' own). A FlingBehavior is Compose's hook of Modifier.anchoredDraggable
 * (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling); its body follows foundation's
 * snapFlingBehavior, which also runs `animate` and moves the offset by `scrollBy`
 * (https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/snapping/SnapFlingBehavior.kt).
 * The built-in anchoredDraggableFlingBehavior has no velocity threshold to set (a fixed 125 dp/s), hence this one.
 */
internal fun <T> releaseFlingBehavior(
    state: AnchoredDraggableState<T>,
    flingVelocity: Float,
    success: (from: T, to: T) -> Float,
    spec: AnimationSpec<Float>
): FlingBehavior = object : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = state.requireOffset()
        val end = state.anchors.positionOf(state.releaseTarget(initialVelocity, flingVelocity, success))
        var last = start
        animate(start, end, initialVelocity, spec) { value, _ ->
            scrollBy(value - last)
            last = value
        }
        return 0f
    }
}

/**
 * Closes the Apps panel open on [open] (left or right) with the drag the grid leaves over: at the grid's edge on the closing side, it
 * cannot go further and the panel follows the finger out; elsewhere the grid takes the swipe.
 */
internal fun AnchoredDraggableState<Side>.closeWithScroll(
    open: Side,
    flingVelocity: Float,
    success: (from: Side, to: Side) -> Float,
    spec: AnimationSpec<Float>,
    gridAtEdge: () -> Boolean
): NestedScrollConnection =
    // The panel closes towards its edge: to the right for the right panel (+1), to the left for the left one (-1).
    closeConnection(open, if (open == Side.RIGHT) 1f else -1f, vertical = false, flingVelocity = flingVelocity, success = success, spec = spec, gridAtEdge = gridAtEdge)

/**
 * Closes the Apps panel open at the bottom with the drag the vertical grid leaves over: the grid at its top cannot go further down, so the
 * panel follows the finger down; a drag up inside the grid scrolls it. The panel closes downwards (+1), as Trebuchet's drawer closes by a
 * downward drag only once its list is at the top (AllAppsSwipeController.canInterceptTouch asks shouldContainerScroll,
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AllAppsSwipeController.java).
 */
internal fun AnchoredDraggableState<Sheet>.closeWithScrollDown(
    flingVelocity: Float,
    success: (from: Sheet, to: Sheet) -> Float,
    spec: AnimationSpec<Float>,
    gridAtEdge: () -> Boolean
): NestedScrollConnection = closeConnection(Sheet.OPEN, 1f, vertical = true, flingVelocity = flingVelocity, success = success, spec = spec, gridAtEdge = gridAtEdge)

// The connection of Material 3's ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection (SheetDefaults.kt, vertical and closing downwards there): [vertical] picks the
// axis, [out] the sign of the way out (+1 down or right, -1 left), [open] and [closed] the two places of the state.
// Whether the panel may close is decided at the gesture's start, as Trebuchet's AllAppsSwipeController does (canInterceptTouch asks
// getAppsView().shouldContainerScroll(ev) when the touch goes down, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AllAppsSwipeController.java):
// [gridAtEdge] is read at the first scroll of a gesture (the grid has taken nothing of it yet) and kept until the fling ends. A gesture that began with
// the grid away from its edge never moves the panel: the grid stops at its edge and the leftover drag and fling are left unconsumed, and the panel
// closes only with a new gesture that begins at the edge.
private fun <T> AnchoredDraggableState<T>.closeConnection(
    open: T,
    out: Float,
    vertical: Boolean,
    flingVelocity: Float,
    success: (from: T, to: T) -> Float,
    spec: AnimationSpec<Float>,
    gridAtEdge: () -> Boolean
): NestedScrollConnection = object : NestedScrollConnection {

    private val openAt: Float get() = anchors.positionOf(open)

    // Set at the first user scroll of a gesture, cleared when its fling ends.
    private var begun = false
    private var mayClose = false

    // The component on the state's axis, as Material 3's Offset.toFloat() and Velocity.toFloat() in
    // ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt);
    // the velocity one is a plain function, as the two extensions share one JVM signature.
    private fun Offset.along(): Float = if (vertical) y else x
    private fun alongVelocity(v: Velocity): Float = if (vertical) v.y else v.x
    private fun offsetOf(value: Float): Offset = if (vertical) Offset(0f, value) else Offset(value, 0f)
    private fun velocityOf(value: Float): Velocity = if (vertical) Velocity(0f, value) else Velocity(value, 0f)

    // settleWith, after Material 3's SheetState.settle (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt)
    // for the animation, and Trebuchet's AbstractStateChangeTouchController.onDragEnd for the end: Trebuchet's fling speed (1 dp per ms) goes the
    // way it points, any other release commits by the share of the travel passed ([releaseTarget], [successProgress]), where it used the
    // platform's minimum fling and the nearest anchor. The state is built without specs (its snapAnimationSpec is never set and throws):
    // the caller's spring is passed in.
    private suspend fun settleWith(velocity: Float) {
        animateTo(releaseTarget(velocity, flingVelocity, success), spec)
    }

    private fun partlyClosed(): Boolean = (offsetOrZero() - openAt) * out > 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && !begun) {
            begun = true
            mayClose = gridAtEdge()
        }
        val delta = available.along()
        return if (delta * out < 0f && source == NestedScrollSource.UserInput && partlyClosed()) offsetOf(dispatchRawDelta(delta)) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val delta = available.along()
        return if (delta * out > 0f && source == NestedScrollSource.UserInput && mayClose) offsetOf(dispatchRawDelta(delta)) else Offset.Zero
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
        val allowed = mayClose || partlyClosed()
        // The gesture is over: the next one is judged again at its start.
        begun = false
        mayClose = false
        if (!allowed) return Velocity.Zero
        settleWith(alongVelocity(available))
        return velocityOf(alongVelocity(available))
    }
}
