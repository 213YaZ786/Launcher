package com.yaz.launcher.ui.component

// The suite's entrance pop and button press, ported from Store (YAZ/Store at HEAD, ui/component/Pop.kt), itself Files':
// - the entrance pop is ShelfPill's (feature/shelf/ShelfPill.kt): an Animatable scale settled by a spring of damping 0.6 and
//   stiffness 420, Compose's value-based animation recipe (https://developer.android.com/develop/ui/compose/animation/value-based);
// - the press is RoundAction's (ui/component/RoundAction.kt): the surface sinks to 0.88 under the finger on a spring of damping 0.45
//   and stiffness 700, read from the clickable's interaction source
//   (https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions).
// Added to Store's: a long press (combinedClickable, https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press).
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay

/**
 * An icon that pops in when it first appears: [seen] holds the keys already shown, so one scrolled back into view stays still.
 * [index] staggers the first ones by 30 ms, eight at most.
 */
fun Modifier.entrancePop(seen: MutableSet<String>, key: String, index: Int = 0): Modifier = composed {
    val first = remember { seen.add(key) }
    val pop = remember { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (pop.value < 1f) {
            delay(index.coerceIn(0, 8) * 30L)
            pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
        }
    }
    graphicsLayer {
        val scale = 0.86f + 0.14f * pop.value
        scaleX = scale
        scaleY = scale
        alpha = pop.value.coerceIn(0f, 1f)
    }
}

/**
 * A tap target that sinks a little under the finger (RoundAction's press), with the haptic tick of every tap, and a long press
 * when [onLongClick] is given.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.sinkClickable(
    label: String,
    depth: Float = 0.88f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptics = rememberHaptics()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) depth else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "sink")
    graphicsLayer {
        scaleX = sink
        scaleY = sink
    }.combinedClickable(
        interactionSource = press,
        indication = null,
        role = Role.Button,
        onClickLabel = label,
        onLongClick = onLongClick,
        onClick = {
            haptics.tick()
            onClick()
        }
    )
}
