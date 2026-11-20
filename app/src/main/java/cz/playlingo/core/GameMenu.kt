package cz.playlingo.core

/**
 * Překlad základního menu hry (od 0.97.34, zatím GTA VI): menu otevřené zleva (Map / Game / Store…), horní lišta
 * Nastavení (Control Scheme, Controller…) a nadpis s popisem vybrané volby vpravo. Jednotlivé volby Nastavení
 * zatím ne (je jich moc). Podle snímků ze hry majitele 20. 11. 2026 (`zaznamy/hra-…`, obraz 16 : 9):
 *  - menu zleva: položky vlevo (x < 25 %), výška 15–70 %, vybraná má bílý pruh a tmavé písmo – čtečka ji často
 *    nepřečte, proto se pozná i jako ta, která v čtení chybí ([MenuTracker]),
 *  - lišta Nastavení: úplně nahoře (y < 8 %), vybraná záložka světlá,
 *  - popis vybrané volby: vpravo (x > 50 %), nadpis VERZÁLKAMI a pod ním 1–3 řádky textu.
 * Od 0.97.36 se ukazuje jako seznam pod sebou hned vedle menu ([MenuView.box] → `TvLook` poloha „misto“), ne v pruhu
 * dole; v TV LG / Samsung od aplikace 1.5.9 (starší ho položí jako obyčejný pruh).
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

/**
 * Co je na obrazovce za menu. [kind] = „pause“ (menu zleva) / „settings“; [items] v pořadí shora (zleva), [tops]
 * jejich poloha (px, pro řazení při doplňování); [selected] −1 = nepoznáno; [box] = kam položit překlad (levý horní
 * roh ve zlomcích obrazu).
 */
data class MenuView(
    val kind: String, val items: List<String>, val selected: Int, val title: String? = null, val description: String? = null,
    val tops: List<Int> = emptyList(), val box: Pair<Float, Float>? = null,
) {
    /** Jen na porovnání, jestli se menu změnilo (jiná vybraná položka, jiný popis). */
    val key: String get() = kind + "|" + items.joinToString("|") + "#" + selected + "#" + (title ?: "") + "#" + (description ?: "")
}

object GameMenu {
    /** Kolik položek / záložek musí být vidět, aby to bylo menu (ne náhodné slovo „Map“ ve hře). */
    const val MIN_ITEMS = 3
    /** Značka vybrané položky – „»“ je v každém písmu (šipku ▸ televize LG neuměla, byl čtvereček). */
    const val MARK = "» "

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
        // překlad hned vpravo od menu (menu má v GTA VI pevnou šířku ~27 % obrazu), od první položky dolů
        val x = maxOf(found.maxOf { it.second.right }.toFloat() / w + 0.03f, 0.3f)
        val y = found.first().second.top.toFloat() / h
        return MenuView("pause", found.map { it.first }, selected(found.map { it.second.bright }),
            tops = found.map { it.second.top }, box = x.coerceAtMost(0.6f) to y)
    }

    /** Nastavení: záložky v horní liště a nadpis + popis vybrané volby vpravo. */
    private fun settings(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        // záložky: čtečka je někdy spojí do jednoho řádku („CONTROL SCHEME CONTROLLER GAMEPLAY…“) → rozdělit
        val tabs = ArrayList<Triple<String, Int, Int>>()   // (záložka, jas, poloha zleva)
        for (l in lines.filter { it.top < h * 0.08f }.sortedBy { it.left }) {
            for ((tab, bright) in splitTabs(l, spec)) if (tabs.none { it.first == tab }) tabs += Triple(tab, bright, l.left)
        }
        if (tabs.size < MIN_ITEMS) return null
        // popis vybrané volby: nadpis verzálkami vpravo nahoře a řádky pod ním ve stejném sloupci
        val right = lines.filter { it.left > w * 0.5f && it.top > h * 0.05f && it.top < h * 0.5f }.sortedBy { it.top }
        val head = right.firstOrNull { isHeading(it.text) }
        var description: String? = null
        var below = h * 0.1f
        if (head != null) {
            val lineH = maxOf(1, head.bottom - head.top)
            val body = right.filter { it.top > head.top && it.top < head.bottom + lineH * 8 && !isHeading(it.text) &&
                kotlin.math.abs(it.left - head.left) < w * 0.06f }
            description = body.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim().ifEmpty { null }
            below = ((body.maxOfOrNull { it.bottom } ?: head.bottom) + lineH * 2).toFloat()
        }
        // překlad v pravém sloupci pod anglickým popisem (tam je volno)
        val x = (head?.left?.toFloat()?.div(w) ?: 0.55f)
        return MenuView("settings", tabs.map { it.first }, selected(tabs.map { it.second }),
            head?.text?.trim()?.let { titleCase(it) }, description, tabs.map { it.third }, x to (below / h).coerceAtMost(0.7f))
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

    /**
     * Text překladu: u menu zleva položky pod sebou (jako v menu), u Nastavení záložky v řádku jako v liště;
     * vybraná se značkou [MARK]. Pod tím nadpis a popis vybrané volby.
     */
    fun text(kind: String, items: List<String>, selected: Int, title: String?, description: String?): String {
        val marked = items.mapIndexed { i, s -> if (i == selected) MARK + s else s }
        val list = if (kind == "pause") marked.joinToString("\n") else marked.joinToString(" · ")
        val desc = listOfNotNull(title, description).joinToString(": ").ifEmpty { null }
        return listOfNotNull(list, desc).joinToString("\n")
    }
}

/**
 * Menu otevřené na obrazovce v čase: položky se **doplňují** (jedno čtení občas některou vynechá – menu tím
 * neblikne), vybraná se pozná i jako ta, která v čtení chybí (bílý pruh s tmavým písmem se čte špatně), a menu
 * zmizí až po [MISSES] čteních bez něj za sebou. [update] vrací, co ukázat (null = nic), a jestli se to změnilo.
 */
class MenuTracker(private val spec: MenuSpec) {
    private var kind: String? = null
    private val tops = LinkedHashMap<String, Int>()
    private var misses = 0
    private var last: MenuView? = null

    data class Result(val view: MenuView?, val changed: Boolean)

    fun reset() { kind = null; tops.clear(); misses = 0; last = null }

    fun update(read: MenuView?): Result {
        if (read == null) {
            if (last == null) return Result(null, false)
            if (++misses < MISSES) return Result(last, false)
            reset(); return Result(null, true)
        }
        misses = 0
        if (read.kind != kind) { kind = read.kind; tops.clear() }
        read.items.forEachIndexed { i, item -> tops[item] = read.tops.getOrElse(i) { tops[item] ?: 0 } }
        // pořadí položek: menu zleva podle výšky, lišta podle pořadí v profilu hry (zleva doprava)
        val order = if (read.kind == "pause") spec.pause else spec.tabs
        val items = tops.keys.sortedWith(compareBy<String>({ tops[it] ?: 0 }, { order.indexOf(it) }))
        var sel = read.selected.takeIf { it >= 0 }?.let { items.indexOf(read.items[it]) } ?: -1
        if (sel < 0) {
            // vybraná položka se nepřečetla (tmavé písmo na bílém pruhu) → ta jediná, která v tomhle čtení chybí
            val missing = items.filter { it !in read.items }
            if (missing.size == 1) sel = items.indexOf(missing[0])
        }
        val v = read.copy(items = items, selected = sel, tops = items.map { tops[it] ?: 0 }, box = read.box ?: last?.box)
        val changed = v.key != last?.key
        last = v
        return Result(v, changed)
    }

    companion object { const val MISSES = 2 }
}
