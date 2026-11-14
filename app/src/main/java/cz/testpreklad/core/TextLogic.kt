package cz.testpreklad.core

import kotlin.math.max

/** Jeden řádek textu rozpoznaný OCR (souřadnice v pixelech snímku). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

/** Blok textu = skupina řádků, které k sobě patří (např. dvouřádkový titulek). */
data class OcrBlock(val lines: List<OcrLine>)

/** Část titulku: volitelné jméno mluvčího ("Dutch") a samotný text. */
data class Segment(val speaker: String?, val body: String)

/**
 * Výsledek výběru textu z jedné oblasti obrazovky.
 * [top]/[bottom] = svislý rozsah použitých řádků v pixelech oblasti (-1, když nic).
 */
data class Picked(
    val text: String, val lineCount: Int, val top: Int = -1, val bottom: Int = -1,
    /** Vodorovný rozsah použitých řádků v pixelech oblasti (-1, když nic). */
    val left: Int = -1, val right: Int = -1,
)

/**
 * Čistá logika bez závislosti na Androidu – dá se testovat na počítači.
 */
object TextLogic {

    private val WS = Regex("\\s+")

    // "Dutch: We need money." / "Uncle Bob: ..." / "Mary-Beth: ..." (max 3 slova, každé s velkým písmenem)
    // Mezera za dvojtečkou není povinná (OCR ji někdy vynechá), ale pak musí následovat písmeno nebo uvozovka.
    private val SPEAKER = Regex("^([A-Z][A-Za-z'.\\-]{0,20}(?: [A-Z][A-Za-z'.\\-]{0,20}){0,2}):\\s*((?=[A-Za-z\"'(.]).+)$")
    // "[Dutch] We need money." – jméno v hranatých závorkách
    private val SPEAKER_BRACKET = Regex("^\\[([A-Za-z][A-Za-z'.\\- ]{0,30})]\\s*(.+)$")
    // samotné jméno na řádku ("DUTCH:" nebo "[Dutch]"), text je až na dalším řádku
    private val SPEAKER_ONLY = Regex("^(?:[A-Z][A-Za-z'.\\-]{0,20}(?: [A-Z][A-Za-z'.\\-]{0,20}){0,2}:|\\[[A-Za-z][A-Za-z'.\\- ]{0,30}])$")
    // jedno slovo jako celá replika: "Wait", "RUN" (první písmeno velké, zbytek malý, nebo celé velké)
    private val SINGLE_WORD = Regex("^(?:[A-Z][a-z]{2,}|[A-Z]{3,})[.!?…,]*$")

    // Slova, která vypadají jako jméno mluvčího, ale nejsou (nápovědy, úkoly…)
    private val NOT_SPEAKERS = setOf(
        "Note", "Tip", "Hint", "Objective", "Mission", "Goal", "Reward", "Warning", "Bounty",
        "Wanted", "Status", "Chapter", "Honor", "Help", "Info", "Location", "Time", "Cost", "Price"
    )

    // "Ill"/"IIl"/"I1l" před dalším slovem je skoro vždy špatně přečtené "I'll"
    private val ILL = Regex("\\bI[Il1]l(?=\\s+[a-z])")
    private val CONTRACTIONS = mapOf(
        "im" to "I'm", "ive" to "I've", "dont" to "don't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't",
        "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't",
        "hasnt" to "hasn't", "youre" to "you're", "theyre" to "they're", "thats" to "that's",
        "whats" to "what's", "theres" to "there's",
    )
    private val NO_APOSTROPHE = Regex("\\b(?:[Ii]m|[Ii]ve|[Dd]ont|[Dd]idnt|[Dd]oesnt|[Ii]snt|[Ww]asnt|[Aa]rent|[Ww]erent|" +
        "[Cc]ouldnt|[Ww]ouldnt|[Ss]houldnt|[Hh]avent|[Hh]asnt|[Yy]oure|[Tt]heyre|[Tt]hats|[Ww]hats|[Tt]heres)\\b")

    /** Opraví typické chyby OCR a sjednotí mezery. */
    fun clean(raw: String): String {
        var t = raw
            .replace('|', 'I')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(WS, " ")
            .trim()
        // osamocené "l" nebo "1" na místě zájmena I ("l think" -> "I think")
        t = t.replace(Regex("(^|[\\s\"'(])[l1](?=\\s+[a-z'])"), "$1I")
        // drobné písmo: OCR vynechá apostrof ("Ill take" → "I'll take", "dont" → "don't")
        t = t.replace(ILL) { "I'll" }
        t = t.replace(NO_APOSTROPHE) { m ->
            val w = m.value
            val fixed = CONTRACTIONS[w.lowercase()] ?: return@replace w
            if (w[0].isUpperCase()) fixed.replaceFirstChar { it.uppercaseChar() } else fixed
        }
        return t
    }

    /**
     * Rozhodne, jestli řádek vypadá jako opravdový text (titulek, nápověda),
     * a ne jako HUD (čísla, munice, kompas, peníze) nebo šum z OCR.
     */
    fun isUseful(raw: String): Boolean {
        val t = clean(raw)
        if (t.length < 2) return false
        val nonSpace = t.count { !it.isWhitespace() }
        val letters = t.count { it.isLetter() }
        if (letters < 2) return false
        if (letters.toFloat() / nonSpace < 0.6f) return false
        val words = t.split(' ').count { w -> w.count { it.isLetter() } >= 2 }
        val endsLikeSentence = t.last() in ".!?…"
        if (words >= 2) return true
        // samotné jméno mluvčího ("MIO:") – text přijde na dalším řádku
        if (SPEAKER_ONLY.matches(t)) return true
        // jedno slovo: buď delší ("Hello"), krátká odpověď s interpunkcí ("No."), nebo slovo jako "Wait"
        return letters >= 5 || (endsLikeSentence && letters >= 2) || SINGLE_WORD.matches(t)
    }

    // "Press X to mount", "Hold [] to aim", "Tap @ for ..." – nápovědy k ovládání, nejsou to titulky
    private val CONTROL_PROMPT = Regex(
        "^(?i:press|hold|tap|mash|click|push)\\s+" +
            // tlačítko: "X", "R2", "@", "[]", "(X)" – ne obyčejné slovo ("Hold on to me" je replika)
            "(?:[A-Z0-9]{1,3}|[^\\sA-Za-z0-9]{1,3}|\\[[^\\]]{0,4}\\]|\\([^)]{0,4}\\))" +
            "(?:\\s*\\+\\s*(?:[A-Z0-9]{1,3}|[^\\sA-Za-z0-9]{1,3}))?" +
            "\\s+(?i:to|for)\\b"
    )

    /** Nápověda k ovládání hry (ne titulek). */
    fun isControlPrompt(raw: String): Boolean = CONTROL_PROMPT.containsMatchIn(clean(raw))

    /**
     * Z bloků OCR složí text. Bloky řadí shora dolů, řádky v bloku spojí mezerou,
     * bloky mezi sebou novým řádkem. Ignoruje příliš malý text (HUD).
     *
     * Režim "jen titulky" ([subtitlesOnly]): titulky jsou vždy vodorovně vycentrované,
     * takže řádky, jejichž střed je dál než [centerTolerance] px od středu obrazovky
     * ([screenCenterX] v souřadnicích oblasti), se zahodí – stejně jako nápovědy k ovládání.
     * Menu, inventář, HUD a různá hlášení tak aplikace nečte.
     */
    fun assemble(
        blocks: List<OcrBlock>,
        minLineHeight: Int,
        subtitlesOnly: Boolean = false,
        screenCenterX: Int = 0,
        centerTolerance: Int = Int.MAX_VALUE,
    ): Picked {
        val sorted = blocks.sortedBy { b -> b.lines.minOfOrNull { it.top } ?: Int.MAX_VALUE }
        // 1) řádky, které vypadají jako titulek (podle velikosti, obsahu a vycentrování)
        val kept = ArrayList<Pair<Int, OcrLine>>()      // (pořadí bloku, řádek)
        sorted.forEachIndexed { bi, block ->
            // titulek o víc řádcích může mít řádky zarovnané vlevo – pak je vycentrovaný jen blok jako celek
            val blockCentered = block.lines.isNotEmpty() && kotlin.math.abs(
                (block.lines.minOf { it.left } + block.lines.maxOf { it.right }) / 2 - screenCenterX
            ) <= centerTolerance
            block.lines
                .filter { it.height >= minLineHeight && isUseful(it.text) }
                .filter { l ->
                    !subtitlesOnly || (
                        !isControlPrompt(l.text) &&
                            (blockCentered || kotlin.math.abs((l.left + l.right) / 2 - screenCenterX) <= centerTolerance)
                        )
                }
                .forEach { kept.add(bi to it) }
        }
        // 2) "jen titulky": texty daleko nad sebou k sobě nepatří (nadpis v menu + titulek dole).
        //    Bere se jen nejspodnější skupina – pruh pak zakryje jen titulek, ne půl obrazovky.
        var used: List<Pair<Int, OcrLine>> = kept
        if (subtitlesOnly && kept.size > 1) {
            val byTop = kept.sortedBy { it.second.top }
            var start = 0
            for (k in 1 until byTop.size) {
                val prevBottom = byTop.subList(start, k).maxOf { it.second.bottom }
                val gap = byTop[k].second.top - prevBottom
                if (gap > 1.5f * maxOf(byTop[k - 1].second.height, byTop[k].second.height)) start = k
            }
            used = byTop.subList(start, byTop.size)
        }
        if (used.isEmpty()) return Picked("", 0)
        // řádky jednoho bloku se spojí mezerou; řádek, který začíná jménem mluvčího, je nová replika
        val parts = ArrayList<String>()
        for (ls in used.groupBy { it.first }.toSortedMap().values) {
            var cur = ""
            for (l in ls.map { it.second }.sortedBy { it.top }) {
                val t = clean(l.text)
                if (cur.isNotEmpty() && !SPEAKER_ONLY.matches(cur) && splitSpeaker(t).speaker != null) { parts.add(cur); cur = t }
                else cur = if (cur.isEmpty()) t else "$cur $t"
            }
            if (cur.isNotEmpty()) parts.add(cur)
        }
        // samotné jméno bez textu není titulek
        if (parts.all { SPEAKER_ONLY.matches(it) }) return Picked("", 0)
        val ls = used.map { it.second }
        return Picked(
            mergeSpeakerLines(parts).joinToString("\n").trim(), ls.size,
            ls.minOf { it.top }, ls.maxOf { it.bottom }, ls.minOf { it.left }, ls.maxOf { it.right },
        )
    }

    /**
     * Není to angličtina? (Obsahuje písmena s diakritikou nebo jiné písmo, např. „Potřebujeme peníze“.)
     * Takový text je skoro jistě náš vlastní překlad přečtený z obrazovky – nikdy se nepřekládá znovu.
     */
    fun looksForeign(text: String): Boolean {
        var n = 0
        for (c in text) if (c.isLetter() && c.code > 0x7F) { n++; if (n >= 2) return true }
        return false
    }

    /** Vypadá text jako celá věta (ne rozpracovaný nebo useknutý titulek)? */
    fun looksComplete(text: String): Boolean {
        val t = text.trim()
        return t.length >= 8 && t.last() in ".!?…\"')"
    }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun simplify(s: String): String {
        val n = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return n.replace(NON_ALNUM, " ").trim()
    }

    /**
     * Když OCR rozdělí jedno dlouhé souvětí do dvou bloků a druhý nezačíná mluvčím,
     * ponechá je oddělené – titulky s mluvčím ale drží pohromadě.
     */
    private fun mergeSpeakerLines(parts: List<String>): List<String> {
        val out = ArrayList<String>()
        for (p in parts) {
            val prev = out.lastOrNull()
            if (prev != null && SPEAKER_ONLY.matches(prev)) { out[out.size - 1] = "$prev $p"; continue }
            if (prev != null && splitSpeaker(p).speaker == null && splitSpeaker(prev).speaker != null &&
                prev.last() !in ".!?…\"" && p.first().isLowerCase()
            ) {
                out[out.size - 1] = "$prev $p"
            } else out.add(p)
        }
        return out
    }

    /** Oddělí jméno mluvčího ("Arthur: Hello") od textu. */
    fun splitSpeaker(line: String): Segment {
        val t = line.trim()
        val m = SPEAKER.find(t) ?: SPEAKER_BRACKET.find(t) ?: return Segment(null, t)
        val name = m.groupValues[1].trim()
        if (NOT_SPEAKERS.any { it.equals(name, ignoreCase = true) }) return Segment(null, t)
        return Segment(name, m.groupValues[2].trim())
    }

    /**
     * Text psaný jen VELKÝMI písmeny převede na běžnou větu ("WE HAVE TO GO!" → "We have to go!").
     * Překladač si s velkými písmeny neporadí (bere je jako zkratky a jména). Jiný text nechá být.
     */
    fun normalizeCase(text: String): String {
        val letters = text.count { it.isLetter() }
        if (letters < 4 || text.any { it.isLowerCase() }) return text
        val sb = StringBuilder(text.lowercase())
        var start = true
        for (i in sb.indices) {
            val c = sb[i]
            if (c.isLetter()) {
                if (start) { sb[i] = c.uppercaseChar(); start = false }
            } else if (c == '.' || c == '!' || c == '?' || c == '…') start = true
        }
        // zájmeno "I" a jeho tvary (I'm, I'll, I've, I'd)
        return sb.toString().replace(Regex("(?<![A-Za-z'])i(?=$|[^A-Za-z]|'(?:m|ll|ve|d)\\b)"), "I")
    }

    /** Jméno psané velkými písmeny ukáže jako běžné jméno ("MARY-BETH" → "Mary-Beth"). */
    fun normalizeName(name: String): String {
        // OCR občas přečte první písmeno jména malé ("old Kessler")
        if (name.isNotEmpty() && name[0].isLowerCase()) return name.replaceFirstChar { it.uppercaseChar() }
        if (name.length < 3 || name.any { it.isLowerCase() }) return name
        val sb = StringBuilder(name.lowercase())
        var start = true
        for (i in sb.indices) {
            if (sb[i].isLetter()) { if (start) { sb[i] = sb[i].uppercaseChar(); start = false } } else start = true
        }
        return sb.toString()
    }

    /**
     * Text připravený pro překladač: samotné slovo bez tečky ("Wait") se překládá špatně,
     * proto dostane tečku. [finishTranslation] ji z překladu zase odebere.
     */
    fun forTranslator(body: String): String {
        val t = body.trim()
        return if (t.isNotEmpty() && !t.contains(' ') && t.last().isLetter()) "$t." else t
    }

    /** Uklidí překlad: pryč s interpunkcí na začátku, tečka navíc pryč, velké písmeno jako v originále. */
    fun finishTranslation(source: String, translated: String): String {
        var t = translated.trim().trimStart(',', '.', ';', ':', ' ')
        val s = source.trim()
        if (s.isNotEmpty() && s.last().isLetter() && t.endsWith(".") && !t.endsWith("..")) t = t.dropLast(1)
        return matchFirstCase(s, t)
    }

    /** Překlad začíná velkým písmenem, když jím začínal i původní text. */
    fun matchFirstCase(source: String, translated: String): String {
        val s = source.firstOrNull { it.isLetter() } ?: return translated
        val i = translated.indexOfFirst { it.isLetter() }
        if (i < 0 || !s.isUpperCase() || translated[i].isUpperCase()) return translated
        return translated.substring(0, i) + translated[i].uppercaseChar() + translated.substring(i + 1)
    }

    /** Rozdělí celý text pásu na segmenty (každý řádek může mít svého mluvčího). */
    fun segments(text: String): List<Segment> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { splitSpeaker(it) }

    /** Podobnost dvou textů (tolerance chyb OCR mezi snímky). */
    fun similar(a: String, b: String, threshold: Float = 0.88f): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y) return true
        val maxLen = max(x.length, y.length)
        // rychlé odmítnutí: příliš rozdílná délka
        if (1f - kotlin.math.abs(x.length - y.length).toFloat() / maxLen < threshold) return false
        return 1f - levenshtein(x, y).toFloat() / maxLen >= threshold
    }

    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
