package cz.playlingo.core

/**
 * Překlad základního menu hry (od 0.97.34, zatím GTA VI): menu otevřené zleva (Map / Game / Store…), horní lišta
 * Nastavení (Control Scheme, Controller…) a nadpis s popisem vybrané volby vpravo. Jednotlivé volby Nastavení
 * zatím ne (je jich moc). Podle snímků ze hry majitele 20. 11. 2026 (`zaznamy/hra-…`, obraz 16 : 9):
 *  - menu zleva: položky vlevo (x < 25 %), výška 25–50 %, vybraná má bílý pruh (světlé pozadí),
 *  - lišta Nastavení: úplně nahoře (y < 8 %), vybraná záložka světlá,
 *  - popis vybrané volby: vpravo (x > 50 %), nadpis VERZÁLKAMI a pod ním 1–3 řádky textu.
 * Ukazuje se v pruhu místo titulku (v menu žádné titulky nejsou), takže funguje i v TV LG / Samsung bez změny.
 * Bez Androidu – testy `GameMenuTest`.
 */
class MenuSpec(
    /** Položky menu otevřeného zleva. */
    val pause: List<String>,
    /** Záložky horní lišty Nastavení. */
    val tabs: List<String>,
    /** Pevné překlady položek a záložek: kód jazyka → (anglicky → překlad). Jiné jazyky přeloží překladač. */
    val words: Map<String, Map<String, String>> = emptyMap(),
) {
    internal val pauseKeys = pause.associateBy { TextLogic.simplify(it) }
    internal val tabKeys = tabs.associateBy { TextLogic.simplify(it) }

    /** Pevný překlad položky, nebo null (pak ji přeloží překladač). */
    fun word(lang: Lang, item: String): String? = words[lang.code]?.entries?.firstOrNull { it.key.equals(item, ignoreCase = true) }?.value
}

/** Řádek z celé horní části obrazu: text, poloha v pixelech a průměrný jas pod ním (0–255; vybraná položka ~200). */
data class MenuLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val bright: Int)

/** Co je na obrazovce za menu: položky (v pořadí shora / zleva), vybraná (−1 = nepoznáno) a popis volby. */
data class MenuView(val items: List<String>, val selected: Int, val title: String? = null, val description: String? = null) {
    /** Jen na porovnání, jestli se menu změnilo (jiná vybraná položka, jiný popis). */
    val key: String get() = items.joinToString("|") + "#" + selected + "#" + (title ?: "") + "#" + (description ?: "")
}

object GameMenu {
    /** Kolik položek / záložek musí být vidět, aby to bylo menu (ne náhodné slovo „Map“ ve hře). */
    const val MIN_ITEMS = 3

    /** Rozpozná menu z řádků celé horní části obrazu ([w] × [h] = celý snímek). null = žádné menu. */
    fun read(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        if (w <= 0 || h <= 0) return null
        pause(lines, w, h, spec)?.let { return it }
        return settings(lines, w, h, spec)
    }

    /** Menu otevřené zleva: aspoň [MIN_ITEMS] položek pod sebou v levé čtvrtině. */
    private fun pause(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        val found = lines.filter { it.left < w * 0.25f && it.top > h * 0.15f && it.top < h * 0.7f }
            .mapNotNull { l -> spec.pauseKeys[TextLogic.simplify(stripIcon(l.text))]?.let { it to l } }
            .distinctBy { it.first }.sortedBy { it.second.top }
        if (found.size < MIN_ITEMS) return null
        return MenuView(found.map { it.first }, selected(found.map { it.second.bright }))
    }

    /** Nastavení: záložky v horní liště a nadpis + popis vybrané volby vpravo. */
    private fun settings(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        // záložky: čtečka je někdy spojí do jednoho řádku („CONTROL SCHEME CONTROLLER GAMEPLAY…“) → rozdělit
        val tabs = ArrayList<Pair<String, Int>>()   // (záložka, jas)
        for (l in lines.filter { it.top < h * 0.08f }.sortedBy { it.left }) {
            for ((tab, bright) in splitTabs(l, spec)) if (tabs.none { it.first == tab }) tabs += tab to bright
        }
        if (tabs.size < MIN_ITEMS) return null
        // popis vybrané volby: nadpis verzálkami vpravo nahoře a řádky pod ním ve stejném sloupci
        val right = lines.filter { it.left > w * 0.5f && it.top > h * 0.05f && it.top < h * 0.5f }.sortedBy { it.top }
        val head = right.firstOrNull { isHeading(it.text) }
        var description: String? = null
        if (head != null) {
            val lineH = maxOf(1, head.bottom - head.top)
            val body = right.filter { it.top > head.top && it.top < head.bottom + lineH * 8 && !isHeading(it.text) &&
                kotlin.math.abs(it.left - head.left) < w * 0.06f }
            description = body.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim().ifEmpty { null }
        }
        return MenuView(tabs.map { it.first }, selected(tabs.map { it.second }),
            head?.text?.trim()?.let { titleCase(it) }, description)
    }

    /** Záložky v jednom řádku: najde známé názvy zleva doprava; jas každé odhadne podle její části řádku. */
    internal fun splitTabs(l: MenuLine, spec: MenuSpec): List<Pair<String, Int>> {
        val words = l.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<Pair<String, Int>>()
        var i = 0
        while (i < words.size) {
            var hit: String? = null; var len = 0
            for (n in 3 downTo 1) {
                if (i + n > words.size) continue
                spec.tabKeys[TextLogic.simplify(words.subList(i, i + n).joinToString(" "))]?.let { hit = it; len = n }
                if (hit != null) break
            }
            if (hit != null) { out += hit!! to l.bright; i += len } else i++
        }
        // jeden řádek = jeden jas; víc záložek v řádku se rozliší jen když čtečka dá každou zvlášť
        return if (out.size > 1) out.map { it.first to -1 } else out
    }

    /** Vybraná = výrazně nejsvětlejší (bílý pruh); jinak −1. */
    internal fun selected(bright: List<Int>): Int {
        if (bright.size < 2 || bright.any { it < 0 }) return -1
        val sorted = bright.sorted()
        val max = sorted.last(); val second = sorted[sorted.size - 2]
        return if (max >= 130 && max - second >= 40) bright.indexOf(max) else -1
    }

    /** Nadpis popisu: verzálky, krátký („HORIZONTAL LOOK SENSITIVITY“). */
    internal fun isHeading(t: String): Boolean {
        val letters = t.filter { it.isLetter() }
        return letters.length >= 2 && letters.length <= 40 && letters.all { it.isUpperCase() }
    }

    /** „HORIZONTAL LOOK SENSITIVITY“ → „Horizontal look sensitivity“ (překladač verzálky překládá hůř). */
    internal fun titleCase(t: String): String = t.lowercase().replaceFirstChar { it.uppercase() }

    /** Ikonka před položkou se čte jako znak navíc („[] Map“, „© Help“) – vzít jen slova. */
    internal fun stripIcon(t: String): String = t.trim().dropWhile { !it.isLetter() }.trim()

    /** Text do pruhu: položky oddělené tečkou, vybraná se šipkou; pod nimi nadpis a popis. */
    fun text(items: List<String>, selected: Int, title: String?, description: String?): String {
        val row = items.mapIndexed { i, s -> if (i == selected) "▸ $s" else s }.joinToString(" · ")
        val desc = listOfNotNull(title, description).joinToString(": ").ifEmpty { null }
        return listOfNotNull(row, desc).joinToString("\n")
    }
}
