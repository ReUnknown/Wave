package app.wave.music.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.wave.music.i18n.L10n

/*
 * Wave's Text and Icon: Material's, with what they say put into the chosen language on the way to
 * the screen (Icon's label is what TalkBack reads). Every screen uses these instead of Material's.
 */

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    // Reading the language here redraws the text when it changes.
    @Suppress("UNUSED_VARIABLE") val lang = L10n.code
    androidx.compose.material3.Text(
        text = L10n.tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
    fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
    textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
    softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
)

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    @Suppress("UNUSED_VARIABLE") val lang = L10n.code
    androidx.compose.material3.Icon(imageVector, contentDescription?.let(L10n::tr), modifier, tint)
}

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    @Suppress("UNUSED_VARIABLE") val lang = L10n.code
    androidx.compose.material3.Icon(painter, contentDescription?.let(L10n::tr), modifier, tint)
}

@Composable
fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    @Suppress("UNUSED_VARIABLE") val lang = L10n.code
    androidx.compose.material3.Icon(bitmap, contentDescription?.let(L10n::tr), modifier, tint)
}
