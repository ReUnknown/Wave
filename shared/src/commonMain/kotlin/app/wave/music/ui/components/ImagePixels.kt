package app.wave.music.ui.components

import coil3.request.ImageRequest

/** A decoded image's pixels as ARGB ints, for working out its colors. */
internal expect fun imagePixels(image: coil3.Image): IntArray?

/** Decodes into memory the CPU can read (Android would otherwise keep it on the GPU). */
internal expect fun ImageRequest.Builder.forPixels(): ImageRequest.Builder

/**
 * Computers: [image] shrunk to [size] px on its longest side and blurred by [sigma] (in those
 * small pixels), once, into a plain bitmap that's cheap to draw every frame. Null where blurs
 * are drawn live instead (Android).
 */
internal expect fun bakeBlur(image: coil3.Image, size: Int, sigma: Float): androidx.compose.ui.graphics.ImageBitmap?
