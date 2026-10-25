package app.wave.music.ui.components

import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.request.ImageRequest
import coil3.toBitmap

internal actual fun imagePixels(image: coil3.Image): IntArray? = runCatching {
    val bitmap = image.toBitmap().asComposeImageBitmap()
    IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
}.getOrNull()

internal actual fun ImageRequest.Builder.forPixels(): ImageRequest.Builder = this
