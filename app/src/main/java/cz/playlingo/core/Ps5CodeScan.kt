package cz.playlingo.core

/**
 * Osmimístné číslo pro spárování z obrazovky PlayStationu (Nastavení → Systém → Remote Play →
 * Propojit zařízení) přečtené kamerou telefonu (`LgScanActivity` v režimu PS5, bez Androidu, s testy).
 * Na obrazovce bývá rozdělené mezerou („1234 5678“) a čtečka ho občas rozdělí i na dva řádky.
 * Víc různých osmimístných čísel najednou = nejisté → nic.
 */
object Ps5CodeScan {
    private val EIGHT = Regex("(?<!\\d)\\d{8}(?!\\d)")
    private val FOUR = Regex("^\\d{4}$")

    fun parse(lines: List<String>): String? {
        val found = LinkedHashSet<String>()
        val clean = lines.map { it.trim() }
        for ((i, line) in clean.withIndex()) {
            // „1234 5678“, „1234-5678“ → jedno číslo
            val joined = line.replace(Regex("(?<=\\d)[ \\u00A0\\-](?=\\d)"), "")
            EIGHT.findAll(joined).forEach { found.add(it.value) }
            if (FOUR.matches(line) && i + 1 < clean.size && FOUR.matches(clean[i + 1])) found.add(line + clean[i + 1])
        }
        return found.singleOrNull()
    }
}

/** Číslo platí, až když ho čtečka přečte [need]× stejně (jako [LgScanVote]). */
class Ps5CodeVote(private val need: Int = 2) {
    private val counts = HashMap<String, Int>()

    fun add(code: String?): String? {
        if (code != null) counts[code] = (counts[code] ?: 0) + 1
        return current()
    }

    fun current(): String? = counts.filter { it.value >= need }.maxByOrNull { it.value }?.key

    fun clear() = counts.clear()
}
