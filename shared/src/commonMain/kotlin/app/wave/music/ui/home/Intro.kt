package app.wave.music.ui.home

import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.WaveSettings
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topInset
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Asking for the tour again (Settings › About › Show me around). */
object Tour {
    var requested by mutableStateOf(false)
}

/**
 * Coming from Wave 1.x: your Wave shows in its old look for a moment, a wave of color rises over
 * it, and as it rolls away the new Wave is underneath. Then a welcome, with an optional tour.
 */
@Composable
fun MigrationIntro(onDone: (tour: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val sweep = remember { Animatable(0f) }
    var phase by remember { mutableIntStateOf(-1) } // -1 welcome, 0 old look, 1 sweeping, 2 tour?
    val before = remember { WaveSettings.state.value }
    LaunchedEffect(phase == -1) {
        if (phase != -1) return@LaunchedEffect
        // While the welcome shows, Wave sits in its old look underneath, ready to transform.
        WaveSettings.preview { it.copy(legacyUi = true, topSearch = true) }
    }
    LaunchedEffect(phase >= 0) {
        if (phase < 0) return@LaunchedEffect
        delay(1_300)
        phase = 1
        sweep.animateTo(0.5f, tween(900, easing = FastOutSlowInEasing))
        // Fully covered: switch to the new Wave underneath.
        WaveSettings.preview { it.copy(legacyUi = before.legacyUi, topSearch = before.topSearch) }
        delay(250)
        sweep.animateTo(1f, tween(1_000, easing = FastOutSlowInEasing))
        phase = 2
    }
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            // Nothing underneath can be tapped while this plays.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // The welcome: what's coming, and Continue to see your Wave change.
        AnimatedVisibility(phase == -1, enter = fadeIn(tween(300)), exit = fadeOut(tween(350)), modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().background(c.background).navigationBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.weight(1f))
                FloatingWaveMark(c.onBackground, 92.dp)
                Text("Wave 2.0 is here", Modifier.padding(top = 28.dp), color = c.onBackground, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                Text(
                    "A whole new look, frosted glass, a new player and lyrics, light mode and loads to customize. Your account, likes and settings are all still here.",
                    Modifier.padding(top = 12.dp).widthIn(max = 460.dp),
                    color = c.onBackground.copy(alpha = 0.72f),
                    style = TextStyle(fontFamily = prismaTextFont, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().height(56.dp).pressable(CircleShape, c.primary, pressedScale = 0.96f) { phase = 0 },
                    contentAlignment = Alignment.Center,
                ) { Text("Continue", color = c.onPrimary, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)) }
            }
        }
        if (phase in 0..1) WaveSweep(sweep.value, c.primary, c.onPrimary)
        if (phase == 0) {
            Text(
                "Your Wave",
                Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 22.dp, vertical = 12.dp),
                color = Color.White,
                style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
        AnimatedVisibility(
            visible = phase == 2,
            enter = fadeIn(tween(260)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 3 },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(c.surfaceContainerHigh)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FloatingWaveMark(c.primary, 64.dp)
                    Text("Welcome to Wave 2.0", Modifier.padding(top = 14.dp), color = c.onSurface, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                    Text(
                        "A fresh look, all yours to shape. Your account, likes and settings came along. Prefer how it was? Settings › Customize › Legacy.",
                        Modifier.padding(top = 8.dp),
                        color = c.onSurface.copy(alpha = 0.75f),
                        style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center),
                    )
                    Box(
                        Modifier.padding(top = 20.dp).fillMaxWidth().height(54.dp).pressable(CircleShape, c.primary, pressedScale = 0.96f) { onDone(true) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Show me around", color = c.onPrimary, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold)) }
                    Text(
                        "Skip",
                        Modifier.padding(top = 10.dp).clip(CircleShape).clickable { onDone(false) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = c.primary,
                        style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/**
 * A wave of color: [p] 0→0.5 rises from the bottom until it covers everything, 0.5→1 rolls on
 * up and away. Both edges ripple, and the Wave mark rides in the middle.
 */
@Composable
internal fun WaveSweep(p: Float, color: Color, ink: Color) {
    val t = rememberInfiniteTransition(label = "ripple")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val amp = with(density) { 22.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            // Top edge: from below the screen to above it over the first half.
            val top = h + amp - (h + amp * 3) * (p / 0.5f).coerceIn(0f, 1f)
            // Bottom edge: follows in the second half.
            val bottom = h + amp - (h + amp * 3) * ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            if (bottom <= top) return@Canvas
            val path = Path()
            val steps = 40
            for (i in 0..steps) {
                val x = size.width * i / steps
                val y = top + amp * sin(phase + x / size.width * 2f * PI.toFloat()).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (i in steps downTo 0) {
                val x = size.width * i / steps
                val y = if (p < 0.5f) h + amp else bottom + amp * sin(phase + 1.3f + x / size.width * 2f * PI.toFloat()).toFloat()
                path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color)
        }
        val covered = 1f - kotlin.math.abs(p - 0.5f) * 4f
        if (covered > 0f) Column(
            Modifier.align(Alignment.Center).graphicsLayer { alpha = covered.coerceIn(0f, 1f); scaleX = 0.9f + covered * 0.1f; scaleY = 0.9f + covered * 0.1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaveMark(ink, size = 84.dp)
            Text("Wave 2.0", Modifier.padding(top = 12.dp), color = ink, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 30.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun FloatingWaveMark(color: Color, size: Dp) {
    val t = rememberInfiniteTransition(label = "float")
    val lift by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "lift")
    Box(Modifier.graphicsLayer { translationY = lift * 5.dp.toPx(); rotationZ = lift * 3f }) { WaveMark(color, size = size) }
}

/** Where the parts the tour points at really are on screen (reported by the parts themselves). */
object TourTargets {
    val rects = androidx.compose.runtime.mutableStateMapOf<String, Rect>()
}

/** Marks a part of the screen the tour can light up. */
fun Modifier.tourTarget(key: String): Modifier = composed {
    // Each part has its own entry (there are many ⋮s), and forgets it when it leaves the screen,
    // so the tour never points at a spot where something used to be.
    val id = remember { "$key@${Any().hashCode()}" }
    androidx.compose.runtime.DisposableEffect(id) { onDispose { TourTargets.rects.remove(id) } }
    this.onGloballyPositioned { coords ->
        val r = coords.boundsInRoot()
        if (r.width > 1f && r.height > 1f) TourTargets.rects[id] = r else TourTargets.rects.remove(id)
    }
}

/** The best on-screen spot for [key]: fully visible, and the one nearest the top for repeated parts. */
private fun findTarget(key: String, screen: Size): Rect? = TourTargets.rects.entries
    .filter { it.key.substringBefore('@') == key }
    .map { it.value }
    .filter { it.top >= 0f && it.bottom <= screen.height && it.left >= 0f && it.right <= screen.width }
    .minByOrNull { it.top }

/** One stop on the tour: the part it lights up (or none) and what it says. */
private class TourStep(val key: String?, val title: String, val body: String)

/**
 * The tour: the screen dims, one real part at a time lights up with a short note, Next to go on,
 * Skip to leave. Parts that aren't on screen right now are skipped.
 */
@Composable
fun AppTour(playing: Boolean, topSearch: Boolean, oneRow: Boolean, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val statusTop = with(density) { topInset().toPx() }
    var at by remember { mutableIntStateOf(0) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(300)) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val d = density.density
        val nav = with(density) { androidx.compose.foundation.layout.WindowInsets.Companion.navigationBars.getBottom(this).toFloat() }
        val full = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val screen = full
        // Until you press Next, the stops keep up with what's on screen (home may still be loading);
        // after that they stay put.
        val seen = TourTargets.rects.keys.map { it.substringBefore('@') }.toSet()
        var frozen by remember { mutableStateOf<List<TourStep>?>(null) }
        val live = remember(seen, screen.width > 0f) {
            fun has(k: String) = findTarget(k, screen) != null
            buildList {
                if (has("pick")) add(TourStep("pick", "Picks for you", "Albums, playlists and new releases chosen for you. Tap a card to open it, or its play button to start it right away."))
                if (has("songrow")) add(TourStep("songrow", "Songs to play", "Tap a song to play it with the rest of the mix. Hold it for more."))
                add(TourStep(if (has("menu")) "menu" else null, "Menus from the ⋮", "Press any ⋮ (or hold a song or album) and the menu grows right out of it: play next, add to queue, add to a playlist, start radio, go to the artist."))
                if (has("tab-0")) add(TourStep("tab-0", "Home", "Your picks, mixes and what you played lately. Tap it again to jump back to the top."))
                if (has("tab-1")) add(TourStep("tab-1", "Library", "Liked songs, your playlists, albums and artists you saved, and your history."))
                if (has("tab-2")) add(TourStep("tab-2", "Search", "Songs, artists, albums and playlists, with filters. Your library shows up as you type."))
                if (has("search")) add(TourStep("search", "Search, and your library", "This searches everything, with filters. The button inside flips to your library and back."))
                if (has("song")) add(TourStep("song", "What's playing", "Tap the mini player and the player grows out of it. Swipe it down to close the song."))
                add(TourStep(null, "In the player", "Up next to reorder what's coming, Lyrics that line up with the song by themselves, and volume and speed under the little handle. Back or swipe down to tuck it away."))
                if (has("avatar")) add(TourStep("avatar", "Make it yours", "Your picture opens Settings. Customize has Modern or Legacy, the bar or a search bar, corners and buttons. Appearance has light mode, the background and blur."))
                add(TourStep(null, "That's the tour", "Want it again later? Settings › About › Show me around."))
            }
        }
        val steps = frozen ?: live
        val step = steps[at.coerceIn(0, steps.lastIndex)]
        // A little room around the part it lights up.
        val target = step.key?.let { findTarget(it, full) }?.let { Rect(it.left - 6 * d, it.top - 6 * d, it.right + 6 * d, it.bottom + 6 * d) }
        // The spotlight glides from one stop to the next.
        val l = androidx.compose.animation.core.animateFloatAsState(target?.left ?: full.width / 2, spring(dampingRatio = 0.85f, stiffness = 260f), label = "l")
        val tp = androidx.compose.animation.core.animateFloatAsState(target?.top ?: full.height / 2, spring(dampingRatio = 0.85f, stiffness = 260f), label = "t")
        val r = androidx.compose.animation.core.animateFloatAsState(target?.right ?: full.width / 2, spring(dampingRatio = 0.85f, stiffness = 260f), label = "r")
        val b = androidx.compose.animation.core.animateFloatAsState(target?.bottom ?: full.height / 2, spring(dampingRatio = 0.85f, stiffness = 260f), label = "b")
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = reveal.value }) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            if (r.value - l.value > 2f) drawRoundRect(
                Color.Black,
                topLeft = Offset(l.value, tp.value),
                size = Size(r.value - l.value, b.value - tp.value),
                cornerRadius = CornerRadius(minOf(28 * d, (b.value - tp.value) / 2f)),
                blendMode = BlendMode.Clear,
            )
        }
        // The note sits on whichever half the spotlight isn't on.
        val noteAtTop = target != null && target.center.y > full.height / 2
        Column(
            Modifier
                .align(if (noteAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(top = if (noteAtTop) with(density) { (statusTop + 72 * d).toDp() } else 0.dp)
                .navigationBarsPadding()
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceContainerHigh)
                .padding(22.dp),
        ) {
            AnimatedContent(at, transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(120)) }, label = "note") { i ->
                val s = steps[i.coerceIn(0, steps.lastIndex)]
                Column {
                    Text("${i + 1} of ${steps.size}", color = c.primary, style = TextStyle(fontFamily = prismaTextFont, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(s.title, Modifier.padding(top = 4.dp), color = c.onSurface, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Text(s.body, Modifier.padding(top = 6.dp), color = c.onSurface.copy(alpha = 0.75f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp, lineHeight = 21.sp))
                }
            }
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (at < steps.lastIndex) Text(
                    "Skip",
                    Modifier.clip(CircleShape).clickable { onDone() }.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = c.onSurface.copy(alpha = 0.7f),
                    style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(48.dp).pressable(CircleShape, c.primary, pressedScale = 0.95f) {
                        if (frozen == null) frozen = steps
                        if (at < steps.lastIndex) at++ else onDone()
                    }.padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (at < steps.lastIndex) "Next" else "Done", color = c.onPrimary, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)) }
            }
        }
    }
}


/** Once per session (each time Wave starts): opens on the wave of color, which rolls away. */
object Opening {
    var done by mutableStateOf(false)
}

/**
 * Wave starting up: the screen begins covered in the song-colored wave with the Wave mark, holds a
 * beat, then the wave rolls up and away to reveal the app underneath.
 */
@Composable
fun OpeningWave(onDone: () -> Unit) {
    val sweep = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        delay(450)
        sweep.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        onDone()
    }
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        WaveSweep(sweep.value, c.primary, c.onPrimary)
    }
}
