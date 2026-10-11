package app.wave.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Wave's motion language: one set of curves and springs, so everything moves like one app. */
object WaveMotion {
    /** M3 emphasized curves: things arriving decelerate, things leaving accelerate. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Containers growing out of (and back into) the thing you tapped. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val ContainerTransform = BoundsTransform { _, _ -> tween(360, easing = Emphasized) }

    /** Small, physical feedback: presses, toggles, icons. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}

/** The shared-transition scope that home sets up, so screens can grow out of what you tapped. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Makes this the same "container" as every other element using [key]: when one leaves and the
 * other arrives, the bounds morph between them instead of one just appearing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedContainer(
    key: String,
    visibility: AnimatedVisibilityScope?,
    shape: Shape,
    /** The big side (a full screen): shows the top of itself while small, like art unfolding. */
    fromTop: Boolean = false,
): Modifier = composed {
    val shared = LocalSharedTransition.current
    if (shared == null || visibility == null) return@composed this
    with(shared) {
        this@composed.sharedBounds(
            rememberSharedContentState(key),
            visibility,
            boundsTransform = WaveMotion.ContainerTransform,
            resizeMode = if (fromTop) {
                SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.FillWidth, androidx.compose.ui.Alignment.TopCenter)
            } else {
                SharedTransitionScope.ResizeMode.scaleToBounds()
            },
            // The big side appears right away and only fades at the very end of shrinking back;
            // the small side does the opposite. So the container always has something in it.
            enter = if (fromTop) fadeIn(tween(110)) else fadeIn(tween(140, delayMillis = 210)),
            exit = if (fromTop) fadeOut(tween(140, delayMillis = 210)) else fadeOut(tween(90)),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/**
 * Follows the system back gesture before it commits, Android's predictive back: returns the
 * gesture's progress (0..1) to shape the screen with, and runs [onBack] when you let go.
 * If you cancel, the screen springs back.
 */
@Composable
fun predictiveBack(enabled: Boolean = true, onBack: () -> Unit): () -> Float {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { progress = it.progress }
            onBack()
        } catch (e: CancellationException) {
            scope.launch { animate(progress, 0f, animationSpec = WaveMotion.settle()) { v, _ -> progress = v } }
            throw e
        }
    }
    return { progress }
}

/** A page being swiped back: shrinks a little, rounds its corners and leans toward the edge. */
fun Modifier.backGesture(progress: () -> Float): Modifier = graphicsLayer {
    val p = WaveMotion.EmphasizedDecelerate.transform(progress().coerceIn(0f, 1f))
    val scale = 1f - 0.1f * p
    scaleX = scale
    scaleY = scale
    translationX = p * 24.dp.toPx()
    shape = RoundedCornerShape(32.dp.toPx() * p)
    clip = p > 0f
}

/**
 * Shows [content] while [visible], with no motion of its own: for things whose coming and going
 * is a shared container morph (see [sharedContainer]).
 */
@Composable
fun MorphVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = ExitTransition.None, content = content)
}

/**
 * Clickable with a physical press: it squishes a little under your finger and springs back.
 * Clips to [shape] and paints [color] behind the content.
 */
fun Modifier.pressable(
    shape: Shape,
    color: Color = Color.Transparent,
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, WaveMotion.bouncy(), label = "press")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(color)
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick)
}

/**
 * Play ⇄ pause that morphs: the two pause bars slide together into the play triangle
 * (each bar becomes one half of it), instead of one icon swapping for the other.
 */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    // 0 = pause bars, 1 = play triangle.
    val t by animateFloatAsState(if (playing) 0f else 1f, WaveMotion.bouncy(), label = "playPause")
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        fun pt(pause: Offset, play: Offset) = Offset(lerp(pause.x, play.x) * k, lerp(pause.y, play.y) * k)
        // Both halves go in one path and overlap a little where they meet, so there's never a
        // hairline seam (in the button's color) down the middle of the triangle.
        val path = Path()
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset) {
            path.moveTo(a.x, a.y)
            path.lineTo(b.x, b.y)
            path.lineTo(c.x, c.y)
            path.lineTo(d.x, d.y)
            path.close()
        }
        // Left bar → left half of the triangle (reaching slightly past the middle).
        quad(
            pt(Offset(6f, 5f), Offset(8f, 5f)),
            pt(Offset(10f, 5f), Offset(14.2f, 8.95f)),
            pt(Offset(10f, 19f), Offset(14.2f, 15.05f)),
            pt(Offset(6f, 19f), Offset(8f, 19f)),
        )
        // Right bar → right half (the point).
        quad(
            pt(Offset(14f, 5f), Offset(13.5f, 8.5f)),
            pt(Offset(18f, 5f), Offset(19f, 12f)),
            pt(Offset(18f, 19f), Offset(19f, 12f)),
            pt(Offset(14f, 19f), Offset(13.5f, 15.5f)),
        )
        drawPath(path, tint)
    }
}
