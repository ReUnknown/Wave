package app.wave.music.ui.components

import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

internal actual fun imagePixels(image: coil3.Image): IntArray? = runCatching {
    val bitmap = image.toBitmap()
    IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
}.getOrNull()

internal actual fun ImageRequest.Builder.forPixels(): ImageRequest.Builder = allowHardware(false)
