package app.wave.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt

/** A window that floats on top, snaps to corners and tucks against the screen's edge. */
interface Floating {
    val tucked: MutableStateFlow<Boolean>
    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    var corner: Int
    var tuckRight: Boolean
    var tuckAt: Point?
    suspend fun save()
}

internal const val MARGIN = 16
internal const val BUBBLE = 56

/** The screen containing [point], minus its taskbar. */
internal fun usableArea(point: Point): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = env.screenDevices.map { it.defaultConfiguration }.firstOrNull { it.bounds.contains(point) }
        ?: env.defaultScreenDevice.defaultConfiguration
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val b = config.bounds
    return Rectangle(b.x + insets.left, b.y + insets.top, b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
}

/** Where something [w] × [h] sits in [corner] of the screen [near] is on, clear of the taskbar. */
internal fun cornerBounds(near: Point, corner: Int, w: Int, h: Int): Rectangle {
    val area = usableArea(near)
    val x = if (corner % 2 == 0) area.x + MARGIN else area.x + area.width - w - MARGIN
    val y = if (corner < 2) area.y + MARGIN else area.y + area.height - h - MARGIN
    return Rectangle(x, y, w, h)
}

/** The round button's spot against the left or right edge, at the height it was let go. */
internal fun tuckedBounds(near: Point, right: Boolean): Rectangle {
    val area = usableArea(near)
    val x = if (right) area.x + area.width - BUBBLE - 6 else area.x + 6
    val y = (near.y - BUBBLE / 2).coerceIn(area.y + MARGIN, area.y + area.height - BUBBLE - MARGIN)
    return Rectangle(x, y, BUBBLE, BUBBLE)
}

/** The corner of its screen nearest [point]. */
internal fun nearestCorner(point: Point): Int {
    val area = usableArea(point)
    val right = point.x > area.x + area.width / 2
    val bottom = point.y > area.y + area.height / 2
    return (if (bottom) 2 else 0) + (if (right) 1 else 0)
}

/**
 * See-through windows: smooth (anti-aliased) rounded corners and a crisp round button, and room
 * for shapes to animate inside the window. macOS and Windows always; Linux when its compositor
 * can blend windows.
 */
internal val seeThrough: Boolean by lazy {
    DesktopPlatform.isMac || DesktopPlatform.isWindows || runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
    }.getOrDefault(false)
}

/**
 * Where a floating window's card is and how it moves. The card is drawn inside a see-through
 * window, so moving and reshaping animate in Compose, every frame at the monitor's own refresh
 * rate: a move just slides the window; a change of size (Compact ↔ Bigger, tucking into the
 * round button) first opens the window to cover both shapes, morphs the card inside it, then
 * settles the window around the result. Nothing is resized frame by frame.
 */
@Stable
class FloatingFrame(private val window: AwtWindow, width: Int, height: Int, radius: Float) {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    var width by mutableFloatStateOf(width.toFloat())
        private set
    var height by mutableFloatStateOf(height.toFloat())
        private set
    var radius by mutableFloatStateOf(radius)
        private set

    /** The card on screen (window units). */
    val bounds: Rectangle
        get() = Rectangle(window.x + x.roundToInt(), window.y + y.roundToInt(), width.roundToInt(), height.roundToInt())

    val center: Point get() = bounds.let { Point(it.x + it.width / 2, it.y + it.height / 2) }

    private val springy = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    /** Springs the card to [target] on screen with corners of [toRadius]. */
    suspend fun go(target: Rectangle, toRadius: Float) {
        val from = bounds
        if (!seeThrough) {
            // Opaque windows (some Linux desktops): the window is the card.
            window.bounds = target
            width = target.width.toFloat(); height = target.height.toFloat(); radius = toRadius
            shape(window, toRadius.roundToInt(), toRadius * 2 >= minOf(target.width, target.height))
            return
        }
        if (from == target && radius == toRadius) return
        val fromRadius = radius
        if (from.width == target.width && from.height == target.height && x == 0f && y == 0f) {
            // Same size: just slide the window.
            val t = Animatable(0f)
            t.animateTo(1f, springy) {
                window.setLocation(lerp(from.x, target.x, value), lerp(from.y, target.y, value))
                radius = fromRadius + (toRadius - fromRadius) * value
            }
            window.setLocation(target.x, target.y)
            return
        }
        // A new size: the window covers both shapes once, the card morphs inside it.
        val union = from.union(target)
        window.bounds = union
        x = (from.x - union.x).toFloat(); y = (from.y - union.y).toFloat()
        val t = Animatable(0f)
        t.animateTo(1f, springy) {
            x = (from.x - union.x) + ((target.x - from.x) * value)
            y = (from.y - union.y) + ((target.y - from.y) * value)
            width = from.width + (target.width - from.width) * value
            height = from.height + (target.height - from.height) * value
            radius = fromRadius + (toRadius - fromRadius) * value
        }
        window.bounds = target
        x = 0f; y = 0f
        width = target.width.toFloat(); height = target.height.toFloat(); radius = toRadius
    }

    private fun lerp(a: Int, b: Int, t: Float) = a + ((b - a) * t).roundToInt()
}

/** Draws [content] as the frame's card: where it is in the window, its size and its corners. */
@Composable
internal fun FloatingCard(frame: FloatingFrame, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier
                .offset { IntOffset((frame.x * density).roundToInt(), (frame.y * density).roundToInt()) }
                .size(frame.width.dp, frame.height.dp)
                .clip(RoundedCornerShape(frame.radius.dp)),
        ) { content() }
    }
}

/**
 * Opaque windows only: the window cut to the card's shape (the X Shape extension on Linux;
 * Windows and macOS windows are see-through instead).
 */
internal fun shape(window: AwtWindow, radiusDp: Int, circle: Boolean) {
    if (seeThrough) return
    runCatching {
        val w = window.width.toDouble()
        val h = window.height.toDouble()
        window.shape = if (circle) java.awt.geom.Ellipse2D.Double(0.0, 0.0, w, h)
        else java.awt.geom.RoundRectangle2D.Double(0.0, 0.0, w, h, radiusDp * 2.0, radiusDp * 2.0)
    }
}

/**
 * Drag a floating card to move its window (a drag something inside takes for itself, like
 * seeking, stays theirs). Let go and it springs to the nearest corner, or, flung at (or dropped
 * against) the left or right edge, tucks away. Clicking the tucked button, or pulling it away
 * from the edge, brings it back. [place] springs the card to a spot (the window's own logic).
 */
internal fun Modifier.movesWindow(
    window: AwtWindow,
    frame: FloatingFrame,
    scope: CoroutineScope,
    state: Floating,
    place: suspend (Rectangle) -> Unit,
): Modifier = pointerInput(window, state) {
    var settle: Job? = null
    val slop = viewConfiguration.touchSlop * 1.5f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val grab = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val origin = window.location
        var dragging = false
        val trail = ArrayDeque<Pair<Long, Int>>()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (!dragging && change.isConsumed) return@awaitEachGesture
            if (!dragging && (change.position - down.position).getDistance() > slop) {
                dragging = true
                settle?.cancel()
            }
            if (dragging) {
                change.consume()
                val now = MouseInfo.getPointerInfo()?.location ?: continue
                window.setLocation(origin.x + now.x - grab.x, origin.y + now.y - grab.y)
                trail.addLast(System.nanoTime() to now.x)
                while (trail.size > 6) trail.removeFirst()
            }
        }
        val tucked = state.tucked.value
        if (!dragging) {
            if (tucked) state.tucked.value = false
            return@awaitEachGesture
        }
        val pointer = MouseInfo.getPointerInfo()?.location ?: frame.center
        val area = usableArea(pointer)
        val card = frame.bounds
        val speed = if (trail.size >= 2) {
            val (t0, x0) = trail.first()
            val (t1, x1) = trail.last()
            (x1 - x0) / ((t1 - t0).coerceAtLeast(1) / 1e9)
        } else 0.0
        val atLeft = pointer.x <= area.x + 24 || card.x < area.x - card.width / 3
        val atRight = pointer.x >= area.x + area.width - 24 || card.x + card.width > area.x + area.width + card.width / 3
        val flungLeft = speed < -2200 && pointer.x < area.x + area.width / 3
        val flungRight = speed > 2200 && pointer.x > area.x + area.width * 2 / 3
        if (atLeft || flungLeft || atRight || flungRight) {
            state.tuckRight = atRight || flungRight
            state.tuckAt = pointer
            if (tucked) settle = scope.launch { place(tuckedBounds(pointer, state.tuckRight)) }
            else state.tucked.value = true
        } else {
            state.corner = nearestCorner(pointer)
            if (tucked) state.tucked.value = false
            else settle = scope.launch {
                place(cornerBounds(pointer, state.corner, card.width, card.height))
                state.save()
            }
        }
    }
}
