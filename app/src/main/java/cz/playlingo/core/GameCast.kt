package cz.playlingo.core

enum class Gender { M, F }

/** Postava hry: jméno, přezdívky a rod – podle rodu se v překladu opraví tvary („Lucia šel“ → „Lucia šla“). */
data class GameCharacter(val name: String, val gender: Gender, val aliases: List<String> = emptyList()) {
    val allNames: List<String> get() = listOf(name) + aliases
}

/**
 * Postavy jedné hry (nebo všech her dohromady) a jejich rod.
 *
 * Mluvčí z titulku („LUCIA: …“) se hledá bez ohledu na velikost písmen a diakritiku, i podle
 * prvního slova víceslovného jména („CAL“ = Cal Hampton). Jméno, které má v různých hrách různý rod,
 * se vynechá – raději nic než špatný tvar.
 */
class Cast(characters: List<GameCharacter>) {
    private val byKey: Map<String, Gender>
    /** Jména tak, jak je píše hra (velká písmena) – podle nich se hledá oslovení a postava v textu. */
    private val names: Map<String, Gender>

    init {
        val keys = HashMap<String, Gender?>()
        val exact = HashMap<String, Gender?>()
        fun put(m: HashMap<String, Gender?>, k: String, g: Gender) {
            if (k.isEmpty()) return
            m[k] = if (m.containsKey(k) && m[k] != g) null else g
        }
        for (c in characters) for (n in c.allNames) {
            put(keys, TextLogic.simplify(n), c.gender)
            put(exact, n, c.gender)
        }
        // první slovo víceslovného jména („Cal Hampton“ → „cal“), jen když nekoliduje s ničím jiným
        for (c in characters) for (n in c.allNames) {
            val first = TextLogic.simplify(n).substringBefore(' ')
            if (first.length >= 3 && first != TextLogic.simplify(n) && !keys.containsKey(first)) keys[first] = c.gender
        }
        byKey = keys.filterValues { it != null }.mapValues { it.value!! }
        names = exact.filterValues { it != null }.mapValues { it.value!! }
    }

    val isEmpty: Boolean get() = byKey.isEmpty()

    /** Rod mluvčího podle jména z titulku; null = neznámá postava. */
    fun genderOf(name: String?): Gender? = name?.let { byKey[TextLogic.simplify(it)] }

    // jednopísmenná jména (M, Q z bondovek) jen jako mluvčí – v textu by se pletla s čímkoli
    private val nameRx: Regex? = names.keys.filter { it.length >= 2 }.takeIf { it.isNotEmpty() }?.let { keys ->
        Regex(keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }) }

    private val vocStart: Regex? = nameRx?.let {
        Regex("^\\s*(?:(?:hey|oh|yo|listen|look|come on|okay|ok|well|damn|man)[,!]?\\s+)?(" + it.pattern + ")\\s*[,!?…]", RegexOption.IGNORE_CASE)
    }
    private val vocEnd: Regex? = nameRx?.let {
        Regex(",\\s*(" + it.pattern + ")\\s*[.!?…\"]*\\s*$", RegexOption.IGNORE_CASE)
    }

    /** Oslovená postava v anglickém originálu („Lucia, you were right.“, „You okay, Lu?“). */
    fun addressee(source: String): Gender? {
        val m = vocStart?.find(source) ?: vocEnd?.find(source) ?: return null
        return genderOf(m.groupValues[1])
    }

    /** Výskyty jmen postav v přeloženém textu (od, do, rod) – jména se nepřekládají, takže jsou v originále. */
    fun mentions(text: String): List<Triple<Int, Int, Gender>> {
        val r = nameRx ?: return emptyList()
        return r.findAll(text).mapNotNull { m ->
            val before = if (m.range.first > 0) text[m.range.first - 1] else ' '
            val after = if (m.range.last + 1 < text.length) text[m.range.last + 1] else ' '
            if (before.isLetter() || after.isLetter()) null
            else Triple(m.range.first, m.range.last + 1, names[m.value] ?: return@mapNotNull null)
        }.toList()
    }

    companion object {
        val EMPTY = Cast(emptyList())
    }
}

/**
 * Rod v českém překladu. Překladač neví, kdo mluví, a píše skoro vždy v mužském rodě („Byl jsem
 * připravený“ i za Lucii). Podle profilu hry se opraví:
 *  - první osoba podle mluvčího z titulku („LUCIA: I was ready.“ → „Byla jsem připravená.“),
 *  - druhá osoba podle oslovené postavy („Lu, you were right.“ → „Měla jsi pravdu.“),
 *  - třetí osoba hned za jménem postavy („Lucia šel domů“ → „Lucia šla domů“).
 *
 * Mění se jen tvary těsně u pomocného slovesa nebo jména (nanejvýš přes dvě krátká slova)
 * a jen spolehlivé koncovky. Neznámé slovo zůstane, jak ho překladač napsal.
 */
object CzechGender {
    /** Pomocná slovesa první osoby (rod mluvčího) a druhé osoby (rod osloveného). */
    private val AUX1 = setOf("jsem", "nejsem", "bych", "abych", "kdybych")
    private val AUX2 = setOf("jsi", "nejsi", "bys", "abys", "kdybys", "ses", "sis", "buď", "nebuď")
    /** Sloveso za jménem, za kterým následuje přídavné jméno („Lucia je připravený“). */
    private val LINK3 = setOf("je", "není", "bude", "nebude", "byl", "nebyl", "byla", "nebyla")
    private val FILLERS = setOf("mi", "se", "si", "to", "mu", "ho", "jí", "nám", "tam", "tu", "sem", "už", "ještě", "mě",
        "ti", "tě", "tak", "taky", "také", "jen", "opravdu", "fakt", "vždycky", "nikdy", "včera", "dnes", "ale")
    /** Podstatná jména a příslovce na -l / -la, která nejsou příčestí. */
    private val NOT_PART = setOf("dál", "přítel", "nepřítel", "manžel", "anděl", "hotel", "kostel", "cíl", "generál",
        "admirál", "král", "ďábel", "model", "tunel", "panel", "level", "signál", "originál", "ideál", "kanál", "pól",
        "styl", "smysl", "úmysl", "průmysl", "kel", "sál", "stůl", "orel", "kozel", "osel", "posel", "kašel", "uhel",
        "škola", "síla", "kola", "hala", "vila", "pila", "mula", "kapela", "tabule", "dcera")
    /** Krátké tvary (po „jsem/jsi“), muž → žena. */
    private val SHORT = mapOf(
        "rád" to "ráda", "nerad" to "nerada", "sám" to "sama", "připraven" to "připravena", "hotov" to "hotova",
        "zvědav" to "zvědava", "zraněn" to "zraněna", "ztracen" to "ztracena", "unaven" to "unavena", "nucen" to "nucena",
        "přesvědčen" to "přesvědčena", "ochoten" to "ochotna", "schopen" to "schopna", "neschopen" to "neschopna",
        "nemocen" to "nemocna", "povinen" to "povinna", "vděčen" to "vděčna", "spokojen" to "spokojena",
        "zatčen" to "zatčena", "mrtev" to "mrtva", "zvyklý" to "zvyklá", "živ" to "živa", "zdráv" to "zdráva",
        "jist" to "jista", "pozván" to "pozvána", "najat" to "najata", "zavřen" to "zavřena",
    )
    private val SHORT_F = SHORT.entries.associate { it.value to it.key }

    private val TOKEN = Regex("[\\p{L}']+|[^\\p{L}']+")

    /**
     * [source] anglický originál části titulku, [translated] její český překlad, [speaker] jméno mluvčího
     * z titulku (nebo null). Bez známých postav vrátí překlad beze změny.
     */
    fun fix(cast: Cast, source: String, translated: String, speaker: String?): String {
        if (cast.isEmpty) return translated
        val me = cast.genderOf(speaker)
        val you = cast.addressee(source)
        val mentions = cast.mentions(translated)
        if (me == null && you == null && mentions.isEmpty()) return translated

        val tokens = TOKEN.findAll(translated).map { it.value }.toMutableList()
        val starts = IntArray(tokens.size)
        run { var p = 0; for (i in tokens.indices) { starts[i] = p; p += tokens[i].length } }
        val words = tokens.indices.filter { tokens[it].first().isLetter() }
        fun low(i: Int) = tokens[i].lowercase()
        /** Mezi dvěma slovy je čárka nebo konec věty → k sobě nepatří. */
        fun broken(a: Int, b: Int): Boolean {
            val (x, y) = if (a < b) a to b else b to a
            return (x + 1 until y).any { k -> !tokens[k].first().isLetter() && tokens[k].any { it in ",.!?;:…–" } }
        }
        /** Velké písmeno uprostřed věty = jméno („Jsem Daniel“) – to se nemění. */
        fun midSentenceName(i: Int): Boolean {
            if (!tokens[i].first().isUpperCase()) return false
            for (k in i - 1 downTo 0) {
                if (tokens[k].first().isLetter()) return true
                if (tokens[k].any { it in ".!?…" }) return false
            }
            return false
        }
        val done = HashSet<Int>()
        fun set(i: Int, g: Gender, adjectiveOnly: Boolean = false): Boolean {
            if (i in done) return true
            if (midSentenceName(i)) return false
            val to = convert(low(i), g, adjectiveOnly) ?: return false
            if (to != low(i)) tokens[i] = keepCase(tokens[i], to)
            done += i
            return true
        }
        /** Od slova [n] (index ve [words]) ve směru [step] najde první slovo mimo výplň a zkusí ho převést. */
        fun neighbour(n: Int, step: Int, g: Gender, adjectiveOnly: Boolean = false): Int? {
            var k = n + step
            var skipped = 0
            while (k in words.indices && low(words[k]) in FILLERS && skipped < 2) { k += step; skipped++ }
            if (k !in words.indices || broken(words[n], words[k])) return null
            return if (set(words[k], g, adjectiveOnly)) k else null
        }
        /** „byl/byla“ + přídavné jméno za ním („byl jsem mrtvý“, „jsi byl připravený“). */
        fun chain(k: Int?, g: Gender) {
            if (k == null) return
            val w = low(words[k])
            if (w == "byl" || w == "byla" || w == "nebyl" || w == "nebyla") neighbour(k, 1, g, adjectiveOnly = true)
        }

        for ((n, i) in words.withIndex()) {
            val w = low(i)
            val g = when (w) { in AUX1 -> me; in AUX2 -> you; else -> null } ?: continue
            val adjOnlyAfter = w == "buď" || w == "nebuď"
            if (!adjOnlyAfter) chain(neighbour(n, -1, g), g)
            chain(neighbour(n, 1, g, adjectiveOnly = adjOnlyAfter), g)
        }
        // třetí osoba: jméno postavy a hned za ním příčestí, nebo „je/byl“ + přídavné jméno
        for ((from, _, g) in mentions) {
            val n = words.indexOfFirst { starts[it] >= from }.takeIf { it >= 0 } ?: continue
            // víceslovné jméno („Cal Hampton“): pokračuje se od posledního slova jména
            var last = n
            while (last + 1 in words.indices && tokens[words[last + 1]].first().isUpperCase() && !broken(words[last], words[last + 1])) last++
            val next = last + 1
            if (next !in words.indices || broken(words[last], words[next])) continue
            val w = low(words[next])
            if (w in LINK3) {
                set(words[next], g)
                neighbour(next, 1, g, adjectiveOnly = true)
            } else {
                chain(neighbour(last, 1, g), g)
            }
        }
        return tokens.joinToString("")
    }

    /** Převede tvar na rod [g]; null = slovo není příčestí ani přídavné jméno, které umíme. */
    private fun convert(w: String, g: Gender, adjectiveOnly: Boolean): String? {
        if (w in NOT_PART || w.length < 2) return null
        return if (g == Gender.F) {
            when {
                w in SHORT -> SHORT[w]
                w in SHORT_F -> w
                w.length >= 4 && w.endsWith("ý") -> w.dropLast(1) + "á"
                w.length >= 4 && w.endsWith("á") -> w
                adjectiveOnly -> null
                w.endsWith("šel") -> w.dropLast(3) + "šla"
                w.length >= 3 && w.endsWith("l") && !w.endsWith("tel") -> w + "a"
                w.length >= 4 && w.endsWith("la") -> w
                else -> null
            }
        } else {
            when {
                w in SHORT_F -> SHORT_F[w]
                w in SHORT -> w
                w.length >= 4 && w.endsWith("á") -> w.dropLast(1) + "ý"
                w.length >= 4 && w.endsWith("ý") -> w
                adjectiveOnly -> null
                w.endsWith("šla") -> w.dropLast(3) + "šel"
                w.length >= 4 && w.endsWith("la") -> w.dropLast(1)
                w.length >= 3 && w.endsWith("l") && !w.endsWith("tel") -> w
                else -> null
            }
        }
    }

    private fun keepCase(original: String, replacement: String): String =
        if (original.first().isUpperCase()) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
}
