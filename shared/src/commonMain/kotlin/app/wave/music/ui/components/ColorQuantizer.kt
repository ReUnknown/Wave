package app.wave.music.ui.components

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One color found in a cover, and how many of its pixels have it. */
internal class Swatch(val rgb: Int, val population: Int) {
    val hsl: FloatArray = FloatArray(3).also { colorToHsl(rgb, it) }
}

/**
 * Groups a cover's pixels into colors (4 bits per channel, averaged within each group), the same
 * idea as Android's Palette, so song colors come out the same on phones and computers.
 */
internal fun quantize(pixels: IntArray): List<Swatch> {
    val count = IntArray(4096)
    val r = LongArray(4096)
    val g = LongArray(4096)
    val b = LongArray(4096)
    for (p in pixels) {
        if ((p ushr 24) < 128) continue // transparent
        val pr = (p shr 16) and 0xFF
        val pg = (p shr 8) and 0xFF
        val pb = p and 0xFF
        val key = ((pr shr 4) shl 8) or ((pg shr 4) shl 4) or (pb shr 4)
        count[key]++
        r[key] += pr.toLong()
        g[key] += pg.toLong()
        b[key] += pb.toLong()
    }
    return (0 until 4096).filter { count[it] > 0 }.map { k ->
        val n = count[k]
        Swatch((0xFF shl 24) or ((r[k] / n).toInt() shl 16) or ((g[k] / n).toInt() shl 8) or (b[k] / n).toInt(), n)
    }
}

/** RGB → hue (0..360), saturation and lightness (0..1), as Android's ColorUtils does it. */
internal fun colorToHsl(color: Int, out: FloatArray) {
    val rf = ((color shr 16) and 0xFF) / 255f
    val gf = ((color shr 8) and 0xFF) / 255f
    val bf = (color and 0xFF) / 255f
    val maxC = max(rf, max(gf, bf))
    val minC = min(rf, min(gf, bf))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    var h: Float
    val s: Float
    if (maxC == minC) {
        h = 0f
        s = 0f
    } else {
        h = when (maxC) {
            rf -> ((gf - bf) / delta) % 6f
            gf -> ((bf - rf) / delta) + 2f
            else -> ((rf - gf) / delta) + 4f
        }
        s = delta / (1f - abs(2f * l - 1f))
    }
    h = (h * 60f) % 360f
    if (h < 0) h += 360f
    out[0] = h.coerceIn(0f, 360f)
    out[1] = s.coerceIn(0f, 1f)
    out[2] = l.coerceIn(0f, 1f)
}

/** Hue, saturation, lightness → RGB. */
internal fun hslToColor(hsl: FloatArray): Int {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    val c = (1f - abs(2 * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - abs((h / 60f % 2f) - 1f))
    val (r, g, b) = when (h.toInt() / 60) {
        0 -> Triple(c + m, x + m, m)
        1 -> Triple(x + m, c + m, m)
        2 -> Triple(m, c + m, x + m)
        3 -> Triple(m, x + m, c + m)
        4 -> Triple(x + m, m, c + m)
        else -> Triple(c + m, m, x + m)
    }
    fun ch(v: Float) = (v * 255).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
