package cz.playlingo.core

/**
 * Překlad základního menu hry (od 0.97.34, zatím GTA VI): menu otevřené zleva (Map / Game / Store…), horní lišta
 * Nastavení (Control Scheme, Controller…) a nadpis s popisem vybrané volby vpravo. Jednotlivé volby Nastavení
 * zatím ne (je jich moc). Podle snímků ze hry majitele 20. 11. 2026 (`zaznamy/hra-…`, obraz 16 : 9):
 *  - menu zleva: položky vlevo (x < 25 %), výška 15–70 %, vybraná má bílý pruh a tmavé písmo – čtečka ji často
 *    nepřečte, proto se pozná i jako ta, která v čtení chybí ([MenuTracker]),
 *  - lišta Nastavení: nahoře (text kolem 8 % výšky), vybraná záložka bílá s tmavým písmem,
 *  - popis vybrané volby: vpravo (x > 50 %), nadpis VERZÁLKAMI a pod ním 1–3 řádky textu.
 * Od 0.97.36 se menu zleva ukazuje jako seznam pod sebou hned vedle menu ([MenuView.box] → `TvLook` poloha „misto“).
 * Od 0.97.58 Nastavení zvlášť: jen vybraná záložka těsně nad lištou ([tabPlace], v pruhu) a nadpis s popisem
 * ve vlastním okně ([MenuView.descBox], v TV zpráva `menupopis`; od 0.97.59 pod anglickým popisem, [descPlace]).
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

/** Slovo řádku (rámeček z čtečky) s průměrným jasem pod ním – záložky Nastavení čtečka často spojí do jednoho řádku. */
data class MenuWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val bright: Int)

/**
 * Řádek z celé horní části obrazu: text, poloha v pixelech a průměrný jas pod ním (0–255; vybraná položka ~200).
 * [words] = slova s vlastními rámečky (od 0.97.58; prázdné = nevíme).
 */
data class MenuLine(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val bright: Int,
    val words: List<MenuWord> = emptyList(),
)

/**
 * Co je na obrazovce za menu. [kind] = „pause“ (menu zleva) / „settings“; [items] v pořadí shora (zleva), [tops]
 * jejich poloha (px, pro řazení při doplňování); [selected] −1 = nepoznáno; [box] = kam položit překlad (levý horní
 * roh ve zlomcích obrazu).
 */
data class MenuView(
    val kind: String, val items: List<String>, val selected: Int, val title: String? = null, val description: String? = null,
    val tops: List<Int> = emptyList(), val box: Pair<Float, Float>? = null,
    /** Rozestup řádků menu ve hře (zlomek výšky obrazu; 0 = neznámý) – překlad řádek proti řádku (od 0.97.40). */
    val step: Float = 0f,
    /**
     * Kde se u každé položky pozná výběr podle jasu (vlevo, nahoře, vpravo, dole ve zlomcích obrazu; od 0.97.58):
     * u menu zleva kus pruhu vpravo za textem, u záložek rámeček záložky. Podle nich služba pozná vybranou položku
     * na každém novém snímku bez čtečky ([MenuTracker.fast]). Prázdné pole = nevíme.
     */
    val rects: List<FloatArray> = emptyList(),
    /** Nastavení: kam dát překlad nadpisu a popisu vybrané volby ([GameMenu.descPlace], zlomky; dole = nejníž) a výška řádku popisu ve hře. */
    val descBox: FloatArray? = null,
    val descLine: Float = 0f,
) {
    /** Jen na porovnání, jestli se menu změnilo (jiná vybraná položka, jiný popis). */
    val key: String get() = listKey + "#" + descKey
    /** Seznam / lišta a vybraná položka (u Nastavení zvlášť od popisu – každý má svůj překlad). */
    val listKey: String get() = kind + "|" + items.joinToString("|") + "#" + selected
    val descKey: String get() = (title ?: "") + "#" + (description ?: "")
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
            tops = found.map { it.second.top }, box = x.coerceAtMost(0.6f) to y, step = rowStep(found.map { it.second.top }, h),
            rects = found.map { pauseRect(it.second, w, h) })
    }

    /**
     * Místo, kde se u položky menu zleva pozná bílý pruh: kousek vpravo za textem (tam je jen pozadí – tmavé, nebo
     * bílé u vybrané; samotný text by rozdíl zmenšil). GTA VI: pruh je široký ~27 % obrazu, nejdelší položka končí ~13 %.
     */
    internal fun pauseRect(l: MenuLine, w: Int, h: Int): FloatArray {
        val r = l.right.toFloat() / w
        val x0 = r + 0.01f
        val top = l.top.toFloat() / h
        val bottom = maxOf(l.bottom, l.top + 1).toFloat() / h
        return if (x0 + 0.03f <= 0.25f) floatArrayOf(x0, top, minOf(x0 + 0.05f, 0.25f), bottom)
        else floatArrayOf(l.left.toFloat() / w, top, r, bottom)
    }

    /** Nastavení: záložky v horní liště a nadpis + popis vybrané volby vpravo. */
    private fun settings(lines: List<MenuLine>, w: Int, h: Int, spec: MenuSpec): MenuView? {
        // záložky: čtečka je někdy spojí do jednoho řádku („CONTROL SCHEME CONTROLLER GAMEPLAY…“) → rozdělit;
        // text lišty je v GTA VI kolem 8 % výšky (41–52 bodů z 540), proto do 12 %
        val tabs = ArrayList<Tab>()
        for (l in lines.filter { it.top < h * 0.12f }.sortedBy { it.left }) {
            for (t in splitTabs(l, spec)) if (tabs.none { it.name == t.name }) tabs += t
        }
        if (tabs.size < MIN_ITEMS) return null
        // popis vybrané volby: nadpis verzálkami vpravo nahoře a řádky pod ním ve stejném sloupci
        val right = lines.filter { it.left > w * 0.5f && it.top > h * 0.05f && it.top < h * 0.5f }.sortedBy { it.top }
        val head = right.firstOrNull { isHeading(it.text) }
        var description: String? = null
        var descBox: FloatArray? = null
        var descLine = 0f
        if (head != null) {
            val lineH = maxOf(1, head.bottom - head.top)
            val body = right.filter { it.top > head.top && it.top < head.bottom + lineH * 8 && !isHeading(it.text) &&
                kotlin.math.abs(it.left - head.left) < w * 0.06f }
            description = body.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim().ifEmpty { null }
            descBox = descPlace(head, body, lines, w, h)
            descLine = (body.map { it.bottom - it.top }.sorted().getOrNull(body.size / 2) ?: lineH).toFloat() / h
        }
        val x = (head?.left?.toFloat()?.div(w) ?: 0.55f)
        return MenuView("settings", tabs.map { it.name }, selected(tabs.map { it.bright }),
            head?.text?.trim()?.let { titleCase(it) }, description, tabs.map { it.left }, x to 0.34f,
            rects = tabs.map { floatArrayOf(it.left.toFloat() / w, it.top.toFloat() / h, it.right.toFloat() / w, maxOf(it.bottom, it.top + 1).toFloat() / h) },
            descBox = descBox, descLine = descLine)
    }

    /** Dolní hranice překladu popisu, když pod ním ve hře nic není (dole bývá nápověda tlačítek). */
    const val DESC_BOTTOM = 0.88f

    /**
     * Kam dát překlad nadpisu a popisu volby (od 0.97.59, přání majitele: přes anglický popis zasahoval doleva do seznamu
     * voleb): anglický popis nechat, překlad pod něj do volného místa – vlevo nejdál k levé hraně nadpisu, nahoře hned pod
     * posledním řádkem popisu, vpravo k okraji sloupce, dole po nejbližší text ve sloupci pod popisem (jinak [DESC_BOTTOM]).
     * Výsledek = vlevo, nahoře, vpravo, nejníž dole (zlomky); text se zalomí na šířku a když se nevejde, zmenší se písmo.
     * Když pod popisem skoro nic volného není (méně než 6 % výšky), jde překlad přes anglický nadpis a popis jako dřív.
     */
    internal fun descPlace(head: MenuLine, body: List<MenuLine>, lines: List<MenuLine>, w: Int, h: Int): FloatArray {
        val all = listOf(head) + body
        val left = (head.left.toFloat() / w - 0.006f).coerceAtLeast(0f)
        val right = maxOf(all.maxOf { it.right }.toFloat() / w + 0.01f, 0.97f).coerceAtMost(0.995f)
        val textBottom = all.maxOf { it.bottom }
        val below = lines.filter { it.top > textBottom && it.right > head.left && it !in all }.minOfOrNull { it.top }
        val limit = minOf(below?.let { it.toFloat() / h - 0.008f } ?: DESC_BOTTOM, DESC_BOTTOM)
        val top = textBottom.toFloat() / h + 0.012f
        return if (limit - top >= 0.06f) floatArrayOf(left, top, right, limit)
        else floatArrayOf(left, (head.top.toFloat() / h - 0.008f).coerceAtLeast(0f), right, maxOf(limit, textBottom.toFloat() / h + 0.008f))
    }

    /** Záložka lišty Nastavení: název z profilu, jas pod ní (−1 = nevíme) a rámeček v pixelech. */
    internal data class Tab(val name: String, val bright: Int, val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Záložky v jednom řádku: najde známé názvy zleva doprava. S rámečky slov ([MenuLine.words], od 0.97.58) má každá
     * záložka svůj rámeček a jas (vybraná se pozná, i když čtečka lištu spojí); bez nich jeden jas na řádek.
     */
    internal fun splitTabs(l: MenuLine, spec: MenuSpec): List<Tab> {
        val words = l.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val boxes = l.words.takeIf { it.size == words.size }
        val out = ArrayList<Tab>()
        var i = 0
        while (i < words.size) {
            var hit: String? = null; var len = 0
            for (n in 3 downTo 1) {
                if (i + n > words.size) continue
                spec.tabKeys[TextLogic.simplify(words.subList(i, i + n).joinToString(" "))]?.let { hit = it; len = n }
                if (hit != null) break
            }
            val name = hit
            if (name != null) {
                val ws = boxes?.subList(i, i + len)
                out += if (ws != null) Tab(name, ws.sumOf { it.bright } / ws.size, ws.minOf { it.left }, ws.minOf { it.top },
                    ws.maxOf { it.right }, ws.maxOf { it.bottom })
                else Tab(name, l.bright, l.left, l.top, l.right, l.bottom)
                i += len
            } else i++
        }
        // bez rámečků slov: jeden řádek = jeden jas; víc záložek v řádku se rozliší jen když čtečka dá každou zvlášť
        return if (out.size > 1 && boxes == null) out.map { it.copy(bright = -1) } else out
    }

    /**
     * Kam položit překlad vybrané záložky: těsně nad ni (nad lištou je v GTA VI jen nadpis SETTINGS), levý okraj
     * jako záložka. Vrací levý horní roh a výšku řádku (zlomky; jako [MenuView.box] a [MenuView.step] – pruh se
     * položí od `y − 0,25 × řádek` a je vysoký jeden řádek). null = rámeček záložky neznáme.
     */
    fun tabPlace(rect: FloatArray?): Pair<Pair<Float, Float>, Float>? {
        if (rect == null || rect.size < 4 || rect[3] <= rect[1]) return null
        val tabH = rect[3] - rect[1]
        val bottom = rect[1] - 0.004f
        if (bottom <= 0.01f) return null
        val step = minOf(tabH * 2.4f, bottom)
        val top = (bottom - step).coerceAtLeast(0f)
        return ((rect[0] - 0.006f).coerceAtLeast(0f) to top + step * 0.25f) to step
    }

    /**
     * Pásy levého sloupce Nastavení (seznam voleb, zlomky obrazu), jejichž jas služba hlídá na každém snímku:
     * posun bílého pruhu na jinou volbu = jiný popis vpravo → číst hned, ne až po intervalu.
     */
    val WATCH: List<FloatArray> = (0 until 20).map { i -> val y = 0.15f + i * 0.035f; floatArrayOf(0.02f, y, 0.48f, y + 0.03f) }

    /** Změnil se jas některého pásu o víc než [limit] (0–255)? Bez předchozího měření ne. */
    fun watchChanged(prev: IntArray?, cur: IntArray, limit: Int = 35): Boolean {
        if (prev == null || prev.size != cur.size) return false
        for (i in cur.indices) if (kotlin.math.abs(cur[i] - prev[i]) > limit) return true
        return false
    }

    /**
     * Rozestup položek pod sebou: nejmenší rozdíl sousedních přečtených položek (chybějící položka by rozdíl
     * zdvojnásobila). 0 = neznámý (méně než 2 položky).
     */
    internal fun rowStep(tops: List<Int>, h: Int): Float {
        val d = tops.sorted().zipWithNext { a, b -> b - a }.filter { it > 0 }
        return if (d.isEmpty() || h <= 0) 0f else d.minOrNull()!!.toFloat() / h
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
 * zmizí až po [MISSES] čteních bez něj za sebou (4 × 0,4 s – při posunu výběru hra menu na okamžik překreslí). [update] vrací, co ukázat (null = nic), a jestli se to změnilo.
 *
 * Od 0.97.58 se vybraná položka pozná hlavně **podle jasu na každém novém snímku** ([fast]): služba změří jas
 * známých míst položek ([MenuView.rects]) bez čtečky (pár set bodů) – posun výběru je v překladu do jednoho snímku,
 * ne až po dalším čtení (čtení s čekáním trvá přes 1 s). Čtení jen doplňuje položky, popis a zavření menu;
 * jeho výběr (ze staršího snímku) nepřebije novější výběr podle jasu.
 */
class MenuTracker(private val spec: MenuSpec) {
    private var kind: String? = null
    private val tops = LinkedHashMap<String, Int>()
    private val rects = HashMap<String, FloatArray>()
    private var misses = 0
    private var last: MenuView? = null
    private var prevStep = 0f
    /** Výběr podle jasu: kdy naposledy a která položka (viz [fast]). */
    private var fastAt = -1L
    private var fastItem: String? = null

    data class Result(val view: MenuView?, val changed: Boolean)

    /** Co je teď ukázané (null = menu zavřené). */
    val view: MenuView? get() = last

    fun reset() { kind = null; tops.clear(); rects.clear(); misses = 0; last = null; prevStep = 0f; fastAt = -1L; fastItem = null }

    /** Místa k měření jasu pro [fast] – v pořadí položek ukázaného menu; prázdné = nejde (nějaké místo chybí). */
    fun sampleRects(): List<FloatArray> {
        val v = last ?: return emptyList()
        val out = v.items.mapNotNull { rects[it]?.takeIf { r -> r.size >= 4 && r[2] > r[0] && r[3] > r[1] } }
        return if (out.size == v.items.size && out.size >= 2) out else emptyList()
    }

    /**
     * Jas míst ze [sampleRects] na novém snímku (čas [now]) → vybraná položka bez čtečky. Výrazně nejsvětlejší
     * (bílý pruh, [GameMenu.selected]) = vybraná; nejasné měření (přechod, menu se zavírá) nic nemění.
     */
    fun fast(bright: IntArray, now: Long): Result {
        val v = last ?: return Result(null, false)
        if (bright.size != v.items.size) return Result(v, false)
        val sel = GameMenu.selected(bright.toList())
        if (sel < 0) return Result(v, false)
        fastAt = now; fastItem = v.items[sel]
        if (sel == v.selected) return Result(v, false)
        val nv = v.copy(selected = sel)
        last = nv
        return Result(nv, true)
    }

    /** Výsledek čtení snímku pořízeného v [grabbedAt] (0 = bez času – výběr podle jasu se nebere v úvahu). */
    fun update(read: MenuView?, grabbedAt: Long = 0L): Result {
        if (read == null) {
            if (last == null) return Result(null, false)
            if (++misses < MISSES) return Result(last, false)
            reset(); return Result(null, true)
        }
        misses = 0
        if (read.kind != kind) { kind = read.kind; tops.clear(); rects.clear(); prevStep = 0f; fastAt = -1L; fastItem = null }
        read.items.forEachIndexed { i, item ->
            tops[item] = read.tops.getOrElse(i) { tops[item] ?: 0 }
            read.rects.getOrNull(i)?.let { rects[item] = it }
        }
        // pořadí položek: menu zleva podle výšky, lišta podle pořadí v profilu hry (zleva doprava)
        val order = if (read.kind == "pause") spec.pause else spec.tabs
        val items = tops.keys.sortedWith(compareBy<String>({ tops[it] ?: 0 }, { order.indexOf(it) }))
        var sel = read.selected.takeIf { it >= 0 }?.let { items.indexOf(read.items[it]) } ?: -1
        if (sel < 0) {
            // vybraná položka se nepřečetla (tmavé písmo na bílém pruhu) → ta jediná, která v tomhle čtení chybí
            val missing = items.filter { it !in read.items }
            if (missing.size == 1) sel = items.indexOf(missing[0])
        }
        // novější výběr podle jasu má přednost před čtením staršího snímku
        if (grabbedAt > 0L && fastAt >= grabbedAt) fastItem?.let { f -> items.indexOf(f).takeIf { it >= 0 }?.let { sel = it } }
        // místo překladu drží během otevřeného menu pozici: nikdy se neposune dolů (vybraná horní položka se občas
        // nepřečte a seznam by „ujel“ o řádek – majitel 20. 11. 2026), jen nahoru, když se najde výš položená položka
        val prev = last?.takeIf { it.kind == read.kind }?.box
        val box = when {
            prev == null -> read.box
            read.box == null -> prev
            else -> prev.first to minOf(prev.second, read.box.second)
        }
        // rozestup řádků: nejmenší známý (spolehlivější, když čtení nějakou položku vynechá)
        val step = listOf(prevStep, read.step).filter { it > 0f }.minOrNull() ?: 0f
        prevStep = step
        val v = read.copy(items = items, selected = sel, tops = items.map { tops[it] ?: 0 }, box = box, step = step,
            rects = items.map { rects[it] ?: FloatArray(0) })
        val changed = v.key != last?.key
        last = v
        return Result(v, changed)
    }

    companion object { const val MISSES = 4 }
}
