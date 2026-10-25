package app.wave.music.ui.components

import coil3.request.ImageRequest

/** A decoded image's pixels as ARGB ints, for working out its colors. */
internal expect fun imagePixels(image: coil3.Image): IntArray?

/** Decodes into memory the CPU can read (Android would otherwise keep it on the GPU). */
internal expect fun ImageRequest.Builder.forPixels(): ImageRequest.Builder
