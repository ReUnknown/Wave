package app.wave.music.ui.components

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
fun NoticeHost(modifier: Modifier = Modifier, enabled: Boolean = true) {
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
        enter = slideInVertically(tween(320, easing = WaveMotion.EmphasizedDecelerate)) { it } +
            scaleIn(tween(320, easing = WaveMotion.EmphasizedDecelerate), initialScale = 0.9f) + fadeIn(tween(160)),
        exit = slideOutVertically(tween(220, easing = WaveMotion.EmphasizedAccelerate)) { it / 2 } + fadeOut(tween(180)),
    ) {
        val c = MaterialTheme.colorScheme
        val shown = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .height(44.dp)
                .shadow(14.dp, CircleShape)
                .pressable(CircleShape, c.primaryContainer, pressedScale = 0.95f) { Notices.dismiss(shown.id) }
                .border(1.dp, c.onPrimaryContainer.copy(alpha = 0.12f), CircleShape)
                .padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // A new notice while one is up swaps its words in place instead of bouncing again.
            AnimatedContent(
                shown,
                contentKey = { it.id },
                transitionSpec = { (fadeIn(tween(160, delayMillis = 60)) togetherWith fadeOut(tween(100))) using SizeTransform(clip = false) },
                label = "notice",
            ) { n ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(n.icon, null, Modifier.size(20.dp), tint = c.primary)
                    Text(n.text, color = c.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
