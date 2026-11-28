package app.wave.music.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A language Wave speaks: its code, and its name in itself (how people look for it). */
class Language(val code: String, val name: String, val english: String)

/**
 * Wave's translations. Everything Wave shows is written in English in the code; on the way to the
 * screen [tr] swaps it for the chosen language. Text with names or numbers in it ("Added to {0}")
 * is matched as a pattern, and what fills the gaps is translated too when it's one of Wave's own
 * words. Anything without a translation stays in English.
 */
object L10n {
    val languages = listOf(
        Language("en", "English", "English"),
        Language("es", "Español", "Spanish"),
        Language("pt", "Português (Brasil)", "Portuguese (Brazil)"),
        Language("fr", "Français", "French"),
        Language("de", "Deutsch", "German"),
    )

    /** The language showing now (read while drawing, so the whole app changes when it does). */
    var code by mutableStateOf("en")
        private set

    /** Settings › Language: "" follows the phone. */
    fun apply(setting: String) {
        val wanted = setting.ifBlank { java.util.Locale.getDefault().language }
        val next = languages.firstOrNull { it.code == wanted }?.code ?: "en"
        if (next == code) return
        code = next
        cache.clear()
        // YouTube Music's own words (home's rows, search groups) in the same language; home reloads.
        runCatching {
            val hl = if (next == "pt") "pt-BR" else next
            if (com.metrolist.innertube.YouTube.locale.hl != hl) {
                com.metrolist.innertube.YouTube.locale = com.metrolist.innertube.YouTube.locale.copy(hl = hl)
                app.wave.music.data.HomeRepository.clear()
            }
        }
    }

    private class Table(val exact: Map<String, String>, val patterns: List<Pair<Regex, String>>) {
        /** For text that's been upper-cased before it's shown ("MADE FOR YOU"). */
        val lower: Map<String, String> = exact.mapKeys { it.key.lowercase() }
    }

    private val tables = HashMap<String, Table>()
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val slot = Regex("\\{(\\d+)\\}")

    private fun table(code: String): Table = tables.getOrPut(code) {
        val raw = when (code) {
            "es" -> Es.text
            "pt" -> Pt.text
            "fr" -> Fr.text
            "de" -> De.text
            else -> ""
        }
        val exact = HashMap<String, String>()
        val patterns = ArrayList<Pair<String, String>>()
        raw.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val key = line.substring(0, tab).replace("\\n", "\n")
            val value = line.substring(tab + 1).replace("\\n", "\n")
            if (slot.containsMatchIn(key)) patterns += key to value else exact[key] = value
        }
        // Most specific first: the more fixed words a pattern has, the sooner it's tried.
        val compiled = patterns.sortedByDescending { it.first.replace(slot, "").length }.map { (key, value) ->
            val pattern = buildString {
                append('^')
                var at = 0
                slot.findAll(key).forEach { m ->
                    append(Regex.escape(key.substring(at, m.range.first)))
                    append("(.+?)")
                    at = m.range.last + 1
                }
                append(Regex.escape(key.substring(at)))
                append('$')
            }
            Regex(pattern, RegexOption.DOT_MATCHES_ALL) to value
        }
        Table(exact, compiled)
    }

    /** [text] in the language showing now. */
    fun tr(text: String): String {
        val lang = code
        if (lang == "en" || text.isBlank()) return text
        cache[text]?.let { return it }
        val result = translate(table(lang), text, depth = 0)
        if (cache.size > 4000) cache.clear()
        cache[text] = result
        return result
    }

    private fun translate(t: Table, text: String, depth: Int): String {
        t.exact[text]?.let { return it }
        if (text.any { it.isLetter() } && text == text.uppercase()) t.lower[text.lowercase()]?.let { return it.uppercase() }
        val trimmed = text.trim()
        if (trimmed != text && trimmed.isNotEmpty()) t.exact[trimmed]?.let { return text.replace(trimmed, it) }
        if (depth > 2) return text
        for ((regex, value) in t.patterns) {
            val m = regex.matchEntire(text) ?: continue
            val parts = m.groupValues.drop(1).map { translate(t, it, depth + 1) }
            return slot.replace(value) { r -> parts.getOrElse(r.groupValues[1].toInt()) { "" } }
        }
        // Joined pieces ("Song · The Weeknd"): each piece on its own.
        if (" · " in text) return text.split(" · ").joinToString(" · ") { translate(t, it, depth + 1) }
        return text
    }
}

/** Shorthand for [L10n.tr]. */
fun tr(text: String): String = L10n.tr(text)
