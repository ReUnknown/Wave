package app.wave.music.ui.components

import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.draggable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wave's own little notices ("Added to queue", "Liked"), instead of Android's toasts: a small pill
 * in the app's colors that rises above the player, stays a couple of seconds and slips away.
 * A new notice replaces the one showing. Tap it to dismiss it early.
 */
object Notices {
    class Notice(val text: String, val icon: ImageVector, val id: Long)

    private val _current = MutableStateFlow<Notice?>(null)
    val current = _current.asStateFlow()
    private var count = 0L

    fun show(text: String, icon: ImageVector? = null) {
        val failed = text.startsWith("Couldn't") || text.startsWith("Can't")
        _current.value = Notice(text, icon ?: if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, ++count)
    }

    fun dismiss(id: Long) {
        if (_current.value?.id == id) _current.value = null
    }
}

/** Shows [Notices] where it's placed (bottom center, above the player). */
@Composable
fun NoticeHost(modifier: Modifier = Modifier, enabled: Boolean = true, glass: dev.chrisbanes.haze.HazeState? = null) {
    val notice by Notices.current.collectAsState()
    // Kept while it slides away, so the pill doesn't empty out mid-exit.
    var last by remember { mutableStateOf<Notices.Notice?>(null) }
    if (notice != null) last = notice
    LaunchedEffect(notice?.id) {
        val id = notice?.id ?: return@LaunchedEffect
        delay(2_400)
        Notices.dismiss(id)
    }
    AnimatedVisibility(
        visible = notice != null && enabled,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it } +
            scaleIn(spring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.85f) + fadeIn(tween(160)),
        exit = slideOutVertically(tween(220, easing = WaveMotion.EmphasizedAccelerate)) { it / 2 } + fadeOut(tween(180)),
    ) {
        val c = MaterialTheme.colorScheme
        val shown = last ?: return@AnimatedVisibility
        // Swipe it sideways to send it away; it follows your finger and fades.
        val drag = remember { androidx.compose.animation.core.Animatable(0f) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val failed = shown.icon == Icons.Rounded.ErrorOutline
        // A thin line along the bottom shows how long it stays.
        val life = remember(shown.id) { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(shown.id) { drag.snapTo(0f); life.snapTo(1f); life.animateTo(0f, tween(2_400, easing = androidx.compose.animation.core.LinearEasing)) }
        val shape = RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .graphicsLayer {
                    translationX = drag.value
                    alpha = 1f - (kotlin.math.abs(drag.value) / 600f).coerceIn(0f, 0.9f)
                    rotationZ = drag.value / 60f
                }
                .draggable(
                    androidx.compose.foundation.gestures.rememberDraggableState { d -> scope.launch { drag.snapTo(drag.value + d) } },
                    androidx.compose.foundation.gestures.Orientation.Horizontal,
                    onDragStopped = { v ->
                        if (kotlin.math.abs(drag.value) > 140f || kotlin.math.abs(v) > 1500f) {
                            drag.animateTo(if (drag.value > 0) 1200f else -1200f, tween(160))
                            Notices.dismiss(shown.id)
                        } else drag.animateTo(0f, spring(dampingRatio = 0.6f))
                    },
                )
                .shadow(18.dp, shape)
                .clip(shape)
                .glass(glass, 24.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainerHigh.copy(alpha = 0.7f))), c.surfaceContainerHigh)
                .border(1.dp, c.onSurface.copy(alpha = 0.08f), shape)
                .clickable { Notices.dismiss(shown.id) },
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A new notice while one is up swaps its words (and icon) in place.
                AnimatedContent(
                    shown,
                    contentKey = { it.id },
                    transitionSpec = { (fadeIn(tween(160, delayMillis = 60)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false) },
                    label = "notice",
                ) { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (failed) c.errorContainer else c.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(n.icon, null, Modifier.size(19.dp), tint = if (failed) c.onErrorContainer else c.onPrimary) }
                        Text(n.text, color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .fillMaxWidth(life.value)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background((if (failed) c.error else c.primary).copy(alpha = 0.6f)),
            )
        }
    }}
