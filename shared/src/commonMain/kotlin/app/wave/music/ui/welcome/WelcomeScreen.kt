package app.wave.music.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.ui.components.WaveMark

/** First screen when signed out: a wall of expressive shapes in your colors, then sign in (or don't). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onGuest: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shapes: List<Shape> = listOf(
        MaterialShapes.Cookie9Sided.toShape(), MaterialShapes.Pill.toShape(), MaterialShapes.Sunny.toShape(),
        MaterialShapes.Clover4Leaf.toShape(), MaterialShapes.Cookie12Sided.toShape(), MaterialShapes.Flower.toShape(),
        MaterialShapes.Arch.toShape(), MaterialShapes.Gem.toShape(), MaterialShapes.SoftBurst.toShape(),
    )
    val fills = listOf(c.primaryContainer, c.tertiaryContainer, c.secondaryContainer, c.primary)

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        // Tilted wall of shapes
        Column(
            Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .rotate(-12f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { col ->
                        val i = row * 4 + col
                        Box(
                            Modifier
                                .size(120.dp)
                                .clip(shapes[i % shapes.size])
                                .background(fills[(i + row) % fills.size]),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.25f to Color.Transparent, 0.62f to c.background)),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(c.primary),
                    contentAlignment = Alignment.Center,
                ) { WaveMark(c.onPrimary, size = 30.dp) }
                Text("Wave", fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, color = c.onBackground)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Your YouTube Music.\nNothing in the way.",
                style = MaterialTheme.typography.headlineSmall,
                color = c.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No ads. Just your mixes, your playlists and the songs you keep coming back to.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
            ) {
                Text("Sign in with Google", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onGuest,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
            ) {
                Text("Continue without an account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
            }
        }
    }
}
