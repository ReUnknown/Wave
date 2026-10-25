package app.wave.music.ui.components

import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.request.ImageRequest
import coil3.toBitmap

internal actual fun imagePixels(image: coil3.Image): IntArray? = runCatching {
    val bitmap = image.toBitmap().asComposeImageBitmap()
    IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
}.getOrNull()

internal actual fun ImageRequest.Builder.forPixels(): ImageRequest.Builder = this

internal actual fun bakeBlur(image: coil3.Image, size: Int, sigma: Float): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val source = org.jetbrains.skia.Image.makeFromBitmap(image.toBitmap())
    val scale = size.toFloat() / maxOf(source.width, source.height)
    val w = maxOf(1, (source.width * scale).toInt())
    val h = maxOf(1, (source.height * scale).toInt())
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    val paint = org.jetbrains.skia.Paint().apply {
        imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(sigma, sigma, org.jetbrains.skia.FilterTileMode.CLAMP)
    }
    surface.canvas.drawImageRect(
        source,
        org.jetbrains.skia.Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
        org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
        org.jetbrains.skia.SamplingMode.LINEAR,
        paint,
        true,
    )
    surface.makeImageSnapshot().toComposeImageBitmap().also { surface.close() }
}.getOrNull()
