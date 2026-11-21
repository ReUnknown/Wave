package app.wave.music.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import coil3.compose.AsyncImage

/**
 * Album motion: the cover drifts and breathes slowly, like an animated album cover. It's a loop
 * long and gentle enough that you notice it, not watch it.
 */
@Composable
fun Modifier.albumMotion(): Modifier {
    val t = rememberInfiniteTransition(label = "albumMotion")
    val zoom by t.animateFloat(1.04f, 1.16f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "zoom")
    val drift by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(19_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val rise by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(23_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rise")
    return graphicsLayer {
        scaleX = zoom
        scaleY = zoom
        translationX = drift * size.width * 0.025f
        translationY = rise * size.height * 0.02f
    }
}

/**
 * An album's cover with its record: on opening, the vinyl slides out from behind the cover, and
 * it spins while this album is playing.
 */
@Composable
fun AlbumWithRecord(art: String?, albumId: String, side: Dp, corner: Dp) {
    val player by PlayerConnection.state.collectAsState()
    val playingThis = player.isPlaying && player.context?.id == albumId
    val out = remember { Animatable(0f) }
    LaunchedEffect(albumId) {
        kotlinx.coroutines.delay(260)
        out.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val spin = rememberInfiniteTransition(label = "record")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "turn")
    val still = remember { Animatable(0f) }
    LaunchedEffect(playingThis) { if (!playingThis) still.snapTo(turn) }
    val angle = if (playingThis) turn else still.value
    val record = side * 0.94f
    Box(Modifier.size(width = side + record * 0.42f, height = side)) {
        // The record, sliding out to the right from behind the cover.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (side - record) / 2 + record * 0.42f * out.value)
                .size(record)
                .graphicsLayer { rotationZ = angle },
        ) { Vinyl(art, Modifier.fillMaxSize()) }
        AsyncImage(
            art?.atSize(ArtSize.CARD), null,
            Modifier.size(side).shadow(24.dp, RoundedCornerShape(corner)).clip(RoundedCornerShape(corner)),
            contentScale = ContentScale.Crop,
        )
    }
}

/** A vinyl record: grooves catching the light, the album's own art as the label. */
@Composable
private fun Vinyl(art: String?, modifier: Modifier) {
    Box(modifier.shadow(16.dp, CircleShape).clip(CircleShape).background(Color(0xFF111114)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // Grooves.
            var g = r * 0.97f
            while (g > r * 0.38f) {
                drawCircle(Color.White.copy(alpha = 0.045f), g, style = Stroke(1.dp.toPx()))
                g -= r * 0.035f
            }
            // A soft sheen across the vinyl, which turns with it.
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width / 2, size.height / 2),
                ),
                r,
            )
        }
        AsyncImage(art?.atSize(ArtSize.THUMB), null, Modifier.fillMaxSize(0.36f).clip(CircleShape), contentScale = ContentScale.Crop)
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF111114)))
    }
}
