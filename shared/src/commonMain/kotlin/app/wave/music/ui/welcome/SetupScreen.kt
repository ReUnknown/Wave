package app.wave.music.ui.welcome

import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.Session
import app.wave.music.data.Settings
import app.wave.music.data.WaveSettings
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.pressable
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private fun display(size: TextUnit) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

private const val STEPS = 5

/**
 * First-run setup, one step per page with dots along the top: welcome, your account, the look,
 * how you get around, and colors. Every choice can be changed later in Settings › Customize.
 */
@Composable
fun SetupScreen(session: Session?, onSignIn: () -> Unit, onGuest: () -> Unit, initialStep: Int = 0) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    var forward by rememberSaveable { mutableIntStateOf(1) }
    fun change(block: (Settings) -> Settings) { scope.launch { WaveSettings.update(block) } }
    fun go(to: Int) {
        forward = if (to > step) 1 else -1
        step = to.coerceIn(0, STEPS - 1)
    }
    // Finishing: Wave is set up (and new to 2.0 already, so no "welcome to 2.0"), tour if wanted.
    var tourAfter by rememberSaveable { mutableStateOf(true) }
    val finish = {
        change { it.copy(setupDone = true, seenV2 = true) }
        if (tourAfter) app.wave.music.ui.home.Tour.requested = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .background(Brush.verticalGradient(0f to c.primary.copy(alpha = 0.16f), 0.5f to Color.Transparent)),
    ) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Back, and how far along you are.
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (step > 0) Box(
                        Modifier.size(44.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f) { go(step - 1) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(STEPS) { i ->
                        val w by animateDpAsState(if (i == step) 24.dp else 8.dp, WaveMotion.bouncy(), label = "dot")
                        val fill by animateColorAsState(if (i <= step) c.primary else c.onBackground.copy(alpha = 0.2f), tween(200), label = "dotFill")
                        Box(Modifier.padding(horizontal = 3.dp).width(w).height(8.dp).clip(CircleShape).background(fill))
                    }
                }
                Box(Modifier.size(44.dp))
            }

            AnimatedContent(
                step,
                transitionSpec = {
                    (slideInHorizontally(tween(320, easing = WaveMotion.EmphasizedDecelerate)) { forward * it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { -forward * it / 4 } + fadeOut(tween(140)))
                },
                modifier = Modifier.weight(1f),
                label = "setup",
            ) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (page) {
                        0 -> Welcome()
                        1 -> Account(session, onSignIn, onGuest)
                        2 -> {
                            Heading("Pick your look", "You can switch any time in Settings › Customize.")
                            OptionCard("Modern", "Prisma: blur, big art and a dock at the bottom.", !settings.legacyUi, { change { it.copy(legacyUi = false) } }) { PhoneSketch(legacy = false, top = false) }
                            OptionCard("Legacy", "A modern take on Wave 1.0: its colors, cards and floating player.", settings.legacyUi, { change { it.copy(legacyUi = true, topSearch = true) } }) { PhoneSketch(legacy = true, top = true) }
                        }
                        3 -> {
                            Heading("How do you get around?", "Both get you to home, search and your library.")
                            OptionCard("Bottom bar", "Home, Library and Search along the bottom.", !settings.topSearch, { change { it.copy(topSearch = false) } }) { PhoneSketch(settings.legacyUi, top = false) }
                            OptionCard("Top search", "A search bar at the top that also opens your library.", settings.topSearch, { change { it.copy(topSearch = true) } }) { PhoneSketch(settings.legacyUi, top = true) }
                            if (!settings.topSearch) SwitchRow("Mini player on its own", "Float it above the bar instead of joining it", settings.separateMiniPlayer) { on -> change { it.copy(separateMiniPlayer = on) } }
                        }
                        else -> {
                            Heading("Make it yours", "A few looks you can change any time.")
                            ThemePicker(settings.theme) { v -> change { it.copy(theme = v) } }
                            SwitchRow("Match song colors", "Wave takes on the colors of what's playing", settings.matchSongColors) { on -> change { it.copy(matchSongColors = on) } }
                            SwitchRow("AMOLED black", "Pure black backgrounds in dark mode", settings.amoled) { on -> change { it.copy(amoled = on) } }
                            SwitchRow("Show me around", "A quick tour of Wave once you're in", tourAfter) { on -> tourAfter = on }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // The one big button.
            val canGo = step != 1 || session != null
            val label = when (step) {
                0 -> "Get started"
                STEPS - 1 -> "Start listening"
                else -> "Next"
            }
            Box(
                Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressable(CircleShape, if (canGo) c.primary else c.onBackground.copy(alpha = 0.12f), pressedScale = 0.96f) {
                        if (!canGo) return@pressable
                        if (step == STEPS - 1) finish() else go(step + 1)
                    },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (canGo) c.onPrimary else c.onBackground.copy(alpha = 0.5f), style = display(18.sp)) }
        }
    }
}

@Composable
private fun ColumnScope.Welcome() {
    // The Wave mark floating over slow rings of color, and the words coming in one after another.
    val c = MaterialTheme.colorScheme
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "welcome")
    val ring by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "ring")
    val lift by t.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "lift")
    val show = remember { List(3) { androidx.compose.animation.core.Animatable(0f) } }
    LaunchedEffect(Unit) {
        show.forEachIndexed { i, a ->
            launch {
                kotlinx.coroutines.delay(120L + i * 140L)
                a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 220f))
            }
        }
    }
    Spacer(Modifier.height(56.dp))
    Box(Modifier.align(Alignment.CenterHorizontally).size(220.dp).graphicsLayer { alpha = show[0].value; scaleX = 0.85f + 0.15f * show[0].value; scaleY = 0.85f + 0.15f * show[0].value }, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            repeat(3) { i ->
                val p = (ring + i / 3f) % 1f
                drawCircle(c.primary.copy(alpha = (1f - p) * 0.28f), radius = size.minDimension / 2f * (0.42f + p * 0.58f))
            }
        }
        Box(
            Modifier.size(128.dp).graphicsLayer { translationY = lift * 6.dp.toPx() }.clip(RoundedCornerShape(42.dp)).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { WaveMark(c.onPrimaryContainer, size = 74.dp) }
    }
    Text(
        "Welcome to Wave",
        Modifier.fillMaxWidth().padding(top = 24.dp).graphicsLayer { alpha = show[1].value; translationY = (1f - show[1].value) * 20.dp.toPx() },
        color = c.onBackground,
        style = display(36.sp).copy(textAlign = TextAlign.Center),
    )
    Text(
        "YouTube Music, simple and beautiful. A few quick choices and you're in.",
        Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp).graphicsLayer { alpha = show[2].value; translationY = (1f - show[2].value) * 20.dp.toPx() },
        color = c.onBackground.copy(alpha = 0.7f),
        style = body(16.sp).copy(textAlign = TextAlign.Center, lineHeight = 22.sp),
    )
}

/** System, light or dark, as three little previews. */
@Composable
private fun ThemePicker(selected: Int, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(bottom = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0 to "System", 1 to "Light", 2 to "Dark").forEach { (value, label) ->
            val on = value == selected
            val line by animateColorAsState(if (on) c.primary else Color.Transparent, tween(180), label = "themeLine")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surfaceContainerHigh)
                    .border(2.dp, line, RoundedCornerShape(22.dp))
                    .clickable { onPick(value) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(
                        when (value) {
                            1 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF7F2EE), Color(0xFFE8DFD8)))
                            2 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1E1B1A), Color(0xFF0E0D0C)))
                            else -> androidx.compose.ui.graphics.Brush.horizontalGradient(0.5f to Color(0xFFF7F2EE), 0.5f to Color(0xFF1E1B1A))
                        },
                    ),
                )
                Text(label, Modifier.padding(top = 8.dp), color = c.onSurface, style = body(14.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun Account(session: Session?, onSignIn: () -> Unit, onGuest: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Heading("Bring your music", "Sign in for your playlists, likes and mixes. Or just look around.")
    if (session != null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.primaryContainer).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (session.photoUrl != null) AsyncImage(session.photoUrl, null, Modifier.size(52.dp).clip(CircleShape))
            else Icon(Icons.Rounded.AccountCircle, null, Modifier.size(52.dp), tint = c.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text(if (session.guest) "Looking around as a guest" else session.name, color = c.onPrimaryContainer, style = display(18.sp))
                Text(if (session.guest) "You can sign in later from Settings" else (session.email ?: "Signed in"), color = c.onPrimaryContainer.copy(alpha = 0.75f), style = body(13.sp))
            }
            Icon(Icons.Rounded.CheckCircle, null, tint = c.onPrimaryContainer)
        }
        if (session.guest) Text("Sign in instead", Modifier.padding(top = 16.dp).clickable(onClick = onSignIn).padding(8.dp), color = c.primary, style = body(15.sp, FontWeight.SemiBold))
        return
    }
    Box(
        Modifier.fillMaxWidth().height(58.dp).pressable(CircleShape, c.onBackground, pressedScale = 0.96f, onClick = onSignIn),
        contentAlignment = Alignment.Center,
    ) { Text("Sign in with Google", color = c.background, style = display(17.sp)) }
    Box(
        Modifier.padding(top = 12.dp).fillMaxWidth().height(58.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(onClick = onGuest),
        contentAlignment = Alignment.Center,
    ) { Text("Continue as guest", color = c.onBackground, style = display(17.sp)) }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    val c = MaterialTheme.colorScheme
    Text(title, Modifier.padding(top = 24.dp), color = c.onBackground, style = display(32.sp))
    Text(subtitle, Modifier.padding(top = 6.dp, bottom = 20.dp), color = c.onBackground.copy(alpha = 0.7f), style = body(15.sp).copy(lineHeight = 21.sp))
}

/** A choice as a card with a little drawing of what it looks like. */
@Composable
private fun OptionCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, sketch: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) c.primaryContainer else c.surfaceContainerHigh, tween(180), label = "cardFill")
    val line by animateColorAsState(if (selected) c.primary else Color.Transparent, tween(180), label = "cardLine")
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(fill)
            .border(2.dp, line, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sketch()
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) c.onPrimaryContainer else c.onSurface, style = display(20.sp))
            Text(description, color = (if (selected) c.onPrimaryContainer else c.onSurface).copy(alpha = 0.75f), style = body(14.sp).copy(lineHeight = 19.sp))
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = c.primary)
    }
}

/** A tiny phone: rounded or square-ish cards, and either the bar at the bottom or search at the top. */
@Composable
private fun PhoneSketch(legacy: Boolean, top: Boolean) {
    val c = MaterialTheme.colorScheme
    val r: Dp = if (legacy) 6.dp else 10.dp
    Column(
        Modifier.width(74.dp).height(132.dp).clip(RoundedCornerShape(16.dp)).background(c.background).border(1.dp, c.outlineVariant, RoundedCornerShape(16.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (top) Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(9.dp), tint = c.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(9.dp), tint = c.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(if (top) 34.dp else 46.dp).clip(RoundedCornerShape(r)).background(if (legacy) c.secondaryContainer else c.primary.copy(alpha = 0.6f)))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.size(16.dp).clip(RoundedCornerShape(r / 2)).background(c.surfaceContainerHighest)) }
        }
        Spacer(Modifier.weight(1f))
        if (legacy || top) Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(r)).background(c.primaryContainer))
        if (!top) Row(
            Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).background(c.surfaceContainerHighest),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(22.dp).height(10.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Home, null, Modifier.size(7.dp), tint = c.onPrimary) }
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(8.dp), tint = c.onSurfaceVariant)
            Icon(Icons.Rounded.Search, null, Modifier.size(8.dp), tint = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh).clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onSurface, style = display(17.sp))
            Text(description, color = c.onSurface.copy(alpha = 0.65f), style = body(13.sp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
