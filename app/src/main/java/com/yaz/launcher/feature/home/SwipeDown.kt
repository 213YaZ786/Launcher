package com.yaz.launcher.feature.home

// A swipe down on the home screen opens the notification shade, as the Pixel Launcher does.
// - The shade: Olauncher's expandNotificationDrawer, the "statusbar" service's StatusBarManager.expandNotificationsPanel called by reflection, under the
//   normal permission EXPAND_STATUS_BAR
//   (https://github.com/tanujnotes/Olauncher/blob/master/app/src/main/java/app/olauncher/helper/Utils.kt).
// - The detection only watches: Compose's pointer input reads the gesture on the Initial pass, before the children, and never consumes it, the way
//   the panes kit's PillMenu tracker reads a press (Compose's pointer event passes,
//   https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures#pointer-event-passes), so the anchored draggable
//   swipes (left, right, up) and the long press on the wallpaper work as they did.
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** The least way down, in dp, for a swipe to count. */
private val SWIPE_DOWN_DISTANCE = 48.dp

/** Olauncher's expandNotificationDrawer: false when Android refuses or the service is missing. */
fun expandNotificationShade(context: Context): Boolean = runCatching {
    val service = context.getSystemService("statusbar")
    Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
}.isSuccess

/**
 * Tells where a gesture starts, before anything else sees it: [onDown] gets the first finger's place and the size of the box, on the Initial
 * pass, and the events are left unconsumed, so the system and the other gestures go on as they were. It lets a gesture be judged at its start (a
 * panel's drag is kept out of the system's gesture band, the grid's place is read once), the way PillMenu's tracker reads a press
 * (Compose's pointer event passes, https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures#pointer-event-passes).
 */
@Composable
fun Modifier.onGestureDown(onDown: (position: androidx.compose.ui.geometry.Offset, size: androidx.compose.ui.unit.IntSize) -> Unit): Modifier {
    val latest by rememberUpdatedState(onDown)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            latest(down.position, size)
            // Waits for the end of the gesture so the next first down is a new gesture.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * Calls [onSwipeDown] when a finger lifts after going down more than 48 dp, more down than sideways, as the only finger, with nothing open
 * ([idle] true when the gesture starts and when it ends). The events are read on the Initial pass and left unconsumed. Off when [enabled] is false.
 */
@Composable
fun Modifier.onSwipeDown(enabled: Boolean, idle: () -> Boolean, onSwipeDown: () -> Unit): Modifier {
    val isIdle by rememberUpdatedState(idle)
    val fire by rememberUpdatedState(onSwipeDown)
    if (!enabled) return this
    return pointerInput(Unit) {
        val distance = SWIPE_DOWN_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val idleAtStart = isIdle()
            var alone = true
            var last = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) alone = false
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.position
                if (!change.pressed) break
            }
            val dx = last.x - down.position.x
            val dy = last.y - down.position.y
            if (alone && idleAtStart && isIdle() && dy > distance && abs(dy) > abs(dx)) fire()
        }
    }
}
