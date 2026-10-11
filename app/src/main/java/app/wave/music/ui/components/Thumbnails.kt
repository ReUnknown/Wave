package app.wave.music.ui.components

private val sizeParams = Regex("=w\\d+-h\\d+")
private val ytimgSizes = Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg")

/** Asks YouTube's image servers for a bigger version of a thumbnail. */
fun String.atSize(px: Int): String = when {
    sizeParams.containsMatchIn(this) -> replace(sizeParams, "=w$px-h$px")
    "i.ytimg.com" in this -> replace(ytimgSizes, "/maxresdefault.jpg")
    else -> this
}
