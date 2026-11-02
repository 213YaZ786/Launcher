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
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

/** BaseSwipeDetector.ANIMATION_DURATION, in ms. */
private const val ANIMATION_DURATION = 1200f

/**
 * BaseSwipeDetector.calculateDuration: the settle's length, shorter for a faster release (`velocity` in px per ms) and for less travel left
 * (`progressNeeded`, the share of the segment between two anchors); never under 100 ms. The original has no other clamp.
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/BaseSwipeDetector.java#calculateDuration)
 */
internal fun calculateDuration(velocity: Float, progressNeeded: Float): Long {
    // TODO: make these values constants after tuning.
    val velocityDivisor = max(2f, abs(0.5f * velocity))
    val travelDistance = max(0.2f, progressNeeded)
    return max(100f, ANIMATION_DURATION / velocityDivisor * travelDistance).toLong()
}

/** Interpolators.FAST_FLING_PX_MS, the speed in px per ms over which a settle uses the quintic curve. */
private const val FAST_FLING_PX_MS = 10f

/** Interpolators.SCROLL. */
private val SCROLL = Easing { t ->
    val u = t - 1.0f
    u * u * u * u * u + 1
}

/** Interpolators.SCROLL_CUBIC. */
private val SCROLL_CUBIC = Easing { t ->
    val u = t - 1.0f
    u * u * u + 1
}

/**
 * Interpolators.scrollInterpolatorForVelocity: the curve of the settle, `velocity` in px per ms.
 * (https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/animationlib/src/com/android/app/animation/Interpolators.java#scrollInterpolatorForVelocity)
 */
internal fun scrollInterpolatorForVelocity(velocity: Float): Easing = if (abs(velocity) > FAST_FLING_PX_MS) SCROLL else SCROLL_CUBIC

/**
 * The animation after a release, as AbstractStateChangeTouchController.onDragEnd sets it: `duration = calculateDuration(velocity,
 * endProgress - progress)` (the share of the segment left to [target]; none when it is already there) with `scrollInterpolatorForVelocity`
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AbstractStateChangeTouchController.java#onDragEnd).
 * [velocityPxPerSecond] is Compose's (px per second); the detector's is px per ms (computeCurrentVelocity(1000) / 1000). The segment is the one
 * between the anchors around the offset; on an anchor, the way to [target].
 */
internal fun <T> AnchoredDraggableState<T>.settleSpec(target: T, velocityPxPerSecond: Float): AnimationSpec<Float> {
    val velocity = velocityPxPerSecond / 1000f
    val offset = offsetOrZero()
    val lowAt = anchors.closestAnchor(offset, false)?.let { anchors.positionOf(it) } ?: offset
    val highAt = anchors.closestAnchor(offset, true)?.let { anchors.positionOf(it) } ?: offset
    val left = abs(anchors.positionOf(target) - offset)
    val segment = if (lowAt == highAt) left else abs(highAt - lowAt)
    val progressNeeded = if (segment == 0f || left.isNaN()) 0f else left / segment
    val duration = if (progressNeeded <= 0f) 0L else calculateDuration(velocity, progressNeeded)
    return tween(duration.toInt(), easing = scrollInterpolatorForVelocity(velocity))
}

internal const val DIRECTION_POSITIVE = 1 shl 0
internal const val DIRECTION_NEGATIVE = 1 shl 1
internal const val DIRECTION_BOTH = DIRECTION_NEGATIVE or DIRECTION_POSITIVE

/** SingleAxisSwipeDetector.Direction, VERTICAL and HORIZONTAL (positive is up and right). */
internal enum class Direction {
    VERTICAL, HORIZONTAL;

    fun isPositive(displacement: Float): Boolean = if (this == VERTICAL) displacement < 0 else displacement > 0

    fun isNegative(displacement: Float): Boolean = if (this == VERTICAL) displacement > 0 else displacement < 0

    /** Returns the part of the given point that is relevant to this direction. */
    fun extractDirection(direction: Offset): Float = if (this == VERTICAL) direction.y else direction.x

    fun extractOrthogonalDirection(direction: Offset): Float = if (this == VERTICAL) direction.x else direction.y
}

/**
 * SingleAxisSwipeDetector.shouldScrollStart: a drag along [mDir] starts only once its displacement along the axis passes the touch slop
 * (times [mTouchSlopMultiplier]) and the displacement across it, and the way is one the client wants ([mScrollDirections]); a diagonal swipe
 * starts nothing along an axis it does not follow.
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/SingleAxisSwipeDetector.java#shouldScrollStart)
 */
internal fun shouldScrollStart(
    mDir: Direction,
    displacement: Offset,
    mTouchSlop: Float,
    mScrollDirections: Int = DIRECTION_BOTH,
    mTouchSlopMultiplier: Float = 1f
): Boolean {
    // Reject cases where the angle or slop condition is not met.
    val minDisplacement = max(mTouchSlop * mTouchSlopMultiplier, abs(mDir.extractOrthogonalDirection(displacement)))
    if (abs(mDir.extractDirection(displacement)) < minDisplacement) {
        return false
    }

    // Check if the client is interested in scroll in current direction.
    val displacementComponent = mDir.extractDirection(displacement)
    return canScrollNegative(mScrollDirections, mDir, displacementComponent) || canScrollPositive(mScrollDirections, mDir, displacementComponent)
}

private fun canScrollNegative(mScrollDirections: Int, mDir: Direction, displacement: Float): Boolean =
    (mScrollDirections and DIRECTION_NEGATIVE) > 0 && mDir.isNegative(displacement)

private fun canScrollPositive(mScrollDirections: Int, mDir: Direction, displacement: Float): Boolean =
    (mScrollDirections and DIRECTION_POSITIVE) > 0 && mDir.isPositive(displacement)

/** Draggable.kt's HorizontalAngleUpperBounds: foundation's nested draggables give a drag to the horizontal one up to 30 degrees from the x axis, else to the vertical one. */
private const val HORIZONTAL_ANGLE_UPPER_BOUNDS = 30

/**
 * Keeps a swipe to its axis while nothing is open. Foundation's nested draggables (the side panels' horizontal one, the bottom panel's vertical
 * one) split a drag at 30 degrees from the x axis; Trebuchet's detectors ask [shouldScrollStart], which needs the along displacement to reach
 * the across one (45 degrees). In between, a drag the vertical draggable would take but [shouldScrollStart] refuses is consumed on the Initial
 * pass, so no draggable has it: it starts when the finger comes out of that band, as BaseSwipeDetector.onTouchEvent tests shouldScrollStart on
 * every ACTION_MOVE until the drag starts (and keeps the drag once started: the gate then lets everything through). The events are otherwise
 * left alone. [active] is read when the finger goes down.
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/BaseSwipeDetector.java#onTouchEvent)
 */
internal fun Modifier.singleAxisSwipeGate(active: () -> Boolean): Modifier = pointerInput(Unit) {
    val mTouchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val mDownPos = down.position
        var idle = active()
        // BaseSwipeDetector.mState: DRAGGING once the drag may start.
        var dragging = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (idle && !dragging && event.changes.size == 1) {
                val mDisplacement = change.position - mDownPos
                val angle = atan2(abs(mDisplacement.y), abs(mDisplacement.x)) * (180f / PI.toFloat())
                val mDir = if (angle <= HORIZONTAL_ANGLE_UPPER_BOUNDS) Direction.HORIZONTAL else Direction.VERTICAL
                if (abs(mDir.extractDirection(mDisplacement)) > mTouchSlop) {
                    if (shouldScrollStart(mDir, mDisplacement, mTouchSlop)) dragging = true else change.consume()
                }
            }
        } while (change.pressed)
    }
}

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
 * The fling of the two panel states: [releaseTarget]'s end reached on [settleSpec] (Trebuchet's onDragEnd animates the progress from where
 * it is to the end, for a duration the velocity and the travel left give). A FlingBehavior is Compose's hook of Modifier.anchoredDraggable
 * (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling); its body follows foundation's
 * snapFlingBehavior, which also runs `animate` and moves the offset by `scrollBy`
 * (https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/snapping/SnapFlingBehavior.kt).
 * The built-in anchoredDraggableFlingBehavior has no velocity threshold to set (a fixed 125 dp/s), hence this one.
 */
internal fun <T> releaseFlingBehavior(
    state: AnchoredDraggableState<T>,
    flingVelocity: Float,
    success: (from: T, to: T) -> Float
): FlingBehavior = object : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val start = state.requireOffset()
        val target = state.releaseTarget(initialVelocity, flingVelocity, success)
        val end = state.anchors.positionOf(target)
        var last = start
        animate(start, end, initialVelocity, state.settleSpec(target, initialVelocity)) { value, _ ->
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
    gridAtEdge: () -> Boolean
): NestedScrollConnection =
    // The panel closes towards its edge: to the right for the right panel (+1), to the left for the left one (-1).
    closeConnection(open, if (open == Side.RIGHT) 1f else -1f, vertical = false, flingVelocity = flingVelocity, success = success, gridAtEdge = gridAtEdge)

/**
 * Closes the Apps panel open at the bottom with the drag the vertical grid leaves over: the grid at its top cannot go further down, so the
 * panel follows the finger down; a drag up inside the grid scrolls it. The panel closes downwards (+1), as Trebuchet's drawer closes by a
 * downward drag only once its list is at the top (AllAppsSwipeController.canInterceptTouch asks shouldContainerScroll,
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AllAppsSwipeController.java).
 */
internal fun AnchoredDraggableState<Sheet>.closeWithScrollDown(
    flingVelocity: Float,
    success: (from: Sheet, to: Sheet) -> Float,
    gridAtEdge: () -> Boolean
): NestedScrollConnection = closeConnection(Sheet.OPEN, 1f, vertical = true, flingVelocity = flingVelocity, success = success, gridAtEdge = gridAtEdge)

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
    // the spec is [settleSpec], the duration Trebuchet's BaseSwipeDetector.calculateDuration gives for the velocity and the travel left.
    private suspend fun settleWith(velocity: Float) {
        val target = releaseTarget(velocity, flingVelocity, success)
        animateTo(target, settleSpec(target, velocity))
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
