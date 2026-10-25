package app.wave.music.ui.components

private val sizeParams = Regex("=w\\d+-h\\d+")
private val ytimgSizes = Regex("/(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg")

/**
 * Asks YouTube's image servers for a version of a thumbnail about [px] pixels across, so small
 * covers don't download (and decode) a full-size image. Sizes snap to a few steps so the same
 * picture at nearby sizes shares one cached download.
 */
fun String.atSize(px: Int): String {
    val step = SIZE_STEPS.firstOrNull { it >= px } ?: SIZE_STEPS.last()
    return when {
        sizeParams.containsMatchIn(this) -> replace(sizeParams, "=w$step-h$step")
        // Video thumbnails only come 16:9 at 320x180 (mqdefault) or 1280x720 (maxresdefault).
        "i.ytimg.com" in this -> replace(ytimgSizes, if (step <= 226) "/mqdefault.jpg" else "/maxresdefault.jpg")
        else -> this
    }
}

private val SIZE_STEPS = intArrayOf(120, 226, 400, 544, 720, 1080)

/** Sizes in pixels for the places art is shown. */
object ArtSize {
    /** Rows, the mini player, headers (about 64dp and under). */
    const val THUMB = 226
    /** Anything blurred: it has no detail to keep, so it can be tiny. */
    const val BLUR = 120
    /** Cards and the queue's coverflow. */
    const val CARD = 544
    /** Full-width art. */
    const val FULL = 1080
}
