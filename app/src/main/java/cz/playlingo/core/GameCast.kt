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
 *  - třetí osoba u jména postavy v textu: za jménem přes pár příslovcí („Andy už šel“ → „Andy už šla“,
 *    „…, že Andy byl pryč“ → „…, že Andy byla pryč“), dál přes „a/ale“ se stejným podmětem („Andy přišel
 *    a řekl“ → „Andy přišla a řekla“) i před jménem na začátku věty („Zavolal Andy.“ → „Zavolala Andy.“),
 *  - třetí osoba bez jména, když originál mluví o postavě zájmenem („Andy called. She said she's coming.“
 *    → „Zavolala Andy. Řekla, že přijde.“) – jen příčestí na začátku věty nebo vedlejší věty a jen u ženy.
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
    /** Pomocná slovesa první a druhé osoby včetně množného čísla – příčestí u nich nepatří k postavě ve 3. osobě. */
    private val AUX12 = AUX1 + AUX2 + setOf("jsme", "nejsme", "jste", "nejste", "bychom", "abychom", "kdybychom",
        "byste", "abyste", "kdybyste")
    /**
     * Slova, přes která se ve třetí osobě hledá příčestí za jménem („Andy se pak vrátil“) – zájmena v 3./4. pádě,
     * příslovce času a míry. Žádná podstatná ani přídavná jména (ta by mohla mít vlastní rod: „celý den“).
     */
    private val FILLERS3 = FILLERS - "ale" + setOf("prý", "pak", "potom", "zase", "znovu", "právě", "zrovna", "hned",
        "konečně", "nakonec", "asi", "možná", "nejspíš", "určitě", "jistě", "dneska", "teď", "ráno", "večer", "skoro",
        "vůbec", "nejdřív", "pořád", "stejně", "jenom", "tady", "ji", "je", "vás", "nás", "jim", "vám", "by", "někde",
        "zpátky", "zpět", "rychle", "brzo", "nic", "něco", "nikoho", "nikomu", "všechno", "brzy", "dávno", "nedávno", "včas", "zas", "ani", "vlastně", "sama", "sám")
    /** Spojky, za kterými začíná nová (vedlejší) věta – příčestí hned za nimi nemá vyslovený podmět. */
    private val CLAUSE = setOf("a", "ale", "že", "protože", "když", "až", "jestli", "jestliže", "pokud", "aby", "kdyby",
        "než", "zatímco", "ač", "ačkoli", "i", "tak", "nebo", "anebo", "jakmile", "dokud")
    /** Slova, která mohou stát mezi začátkem věty a příčestím, aniž by byla podmětem („Pak mi zavolal…“). */
    private val PRE = FILLERS3 - setOf("to", "je", "sama", "sám") + setOf("by")
    /** Spojky uvnitř věty, kde končí příčestí s podmětem jména („Andy viděl, jak…“, „…, kterou…“). */
    private val SUBORD = setOf("že", "který", "která", "které", "kterou", "kterého", "co", "kdo", "jak", "když", "protože",
        "aby", "kde", "kam", "jestli", "pokud", "než", "zatímco")
    /** Mužská zájmena v překladu – kde jsou, mluví se i o muži a zájmenné pravidlo se nepoužije. */
    private val CS_MALE = setOf("on", "něj", "něho", "jeho", "jemu", "ním", "nějž")

    /** Muž / žena v anglickém originálu (zájmena a běžná slova pro osobu) – pro odlišení, o kom věta mluví. */
    private val EN_MALE = Regex("\\b(he|him|his|himself|he's|he'd|he'll|guy|man|cop|boss|dude|bro|brother|father|dad|son|boy|" +
        "uncle|husband|boyfriend|sir|mister|mr|king|grandpa|nephew|gentleman|fella|homeboy)\\b", RegexOption.IGNORE_CASE)
    private val EN_FEMALE = Regex("\\b(she|her|hers|herself|she's|she'd|she'll|girl|woman|lady|mom|mother|sister|wife|" +
        "girlfriend|daughter|aunt|grandma|niece|madam|ma'am|mrs|ms|miss|queen)\\b", RegexOption.IGNORE_CASE)
    private val EN_SHE = Regex("\\b(she|she's|she'd|she'll|her|herself)\\b", RegexOption.IGNORE_CASE)
    private val EN_SHE_WAS = Regex("\\bshe(\\s+(was|wasn't|had been|has been|is|isn't)|'s been|'d been|'s)\\b", RegexOption.IGNORE_CASE)
    /** „it“ jako podmět v minulém čase (it was, it fell…) – mohl by to být mužský rod (obchod padl). */
    private val EN_IT_PAST = Regex("\\bit(\\s+(was|wasn't|had|hadn't|did|didn't|got|went|came|\\w+ed)\\b|'d\\b)", RegexOption.IGNORE_CASE)

    /** Podstatná jména a příslovce na -l / -la, která nejsou příčestí. */
    private val NOT_PART = setOf("dál", "přítel", "nepřítel", "manžel", "anděl", "hotel", "kostel", "cíl", "generál",
        "admirál", "král", "ďábel", "model", "tunel", "panel", "level", "signál", "originál", "ideál", "kanál", "pól",
        "styl", "smysl", "úmysl", "průmysl", "kel", "sál", "stůl", "orel", "kozel", "osel", "posel", "kašel", "uhel",
        "fotbal", "basketbal", "festival", "materiál", "seriál", "kapitál", "metal", "rival", "sval", "obal", "účel", "gól",
        "bál", "mol", "apríl", "medail", "potenciál", "personál", "arzenál", "detail", "email", "mail", "kokpit", "pedál",
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

        /** Mezi slovy [a] a [b] (indexy ve [words]) je jen mezera, případně čárka („přišel, ale…“). */
        fun onlyCommaBetween(a: Int, b: Int): Boolean =
            (words[a] + 1 until words[b]).all { k -> tokens[k].all { it == ',' || it.isWhitespace() } }
        /** Slovo [k] (index ve [words]) začíná větu: před ním je jen začátek, interpunkce nebo spojka a příslovce. */
        fun clauseStart(k: Int): Boolean {
            var j = k - 1
            var skipped = 0
            while (j >= 0) {
                if (broken(words[j], words[j + 1])) return true
                val w = low(words[j])
                if (w in CLAUSE) return true
                if (w !in PRE || skipped >= 3) return false
                j--; skipped++
            }
            return true
        }
        /** U slova [k] (přes výplň, ve stejné větě) je pomocné sloveso 1. nebo 2. osoby („viděl jsem“, „jsi byl“). */
        fun nearAux(k: Int): Boolean {
            for (step in intArrayOf(-1, 1)) {
                var j = k + step
                var skipped = 0
                while (j in words.indices && !broken(words[j - (if (step > 0) 1 else -1)], words[j])) {
                    val w = low(words[j])
                    if (w in AUX12) return true
                    if (w !in FILLERS3 || skipped >= 2) break
                    j += step; skipped++
                }
            }
            return false
        }
        /** Za příčestím [k] (podmět jména) pokračuje věta „a/ale + příčestí“ se stejným podmětem („přišel a řekl“). */
        fun continueSameSubject(k: Int, g: Gender) {
            var j = k + 1
            var seen = 0
            // přes předmět („vzal auto a odjel“), ale ne přes vedlejší větu nebo konec věty
            while (j in words.indices && seen < 4) {
                val w = low(words[j])
                if (w in AUX12 || w in SUBORD || tokens[words[j]].first().isUpperCase()) return
                if ((w == "a" || w == "ale") && (!broken(words[j - 1], words[j]) || onlyCommaBetween(j - 1, j))) break
                if (broken(words[j - 1], words[j])) return
                j++; seen++
            }
            if (j !in words.indices) return
            var v = j + 1
            var skipped = 0
            while (v in words.indices && !broken(words[v - 1], words[v]) && low(words[v]) in FILLERS3 && low(words[v]) !in AUX12 && skipped < 3) { v++; skipped++ }
            if (v !in words.indices || broken(words[v - 1], words[v])) return
            if (!isParticiple(low(words[v]), g) || nearAux(v)) return
            if (set(words[v], g)) { chain(v, g); continueSameSubject(v, g) }
        }
        /** Od posledního slova jména [last] hledá vpřed (přes nanejvýš tři příslovce/zájmena) přísudek jména. */
        fun after3(last: Int, g: Gender) {
            var k = last + 1
            var skipped = 0
            while (k in words.indices && !broken(words[k - 1], words[k])) {
                val w = low(words[k])
                if (w in AUX12) return
                if (w in LINK3) {
                    set(words[k], g)
                    // „Andy je viděl“ = viděla je (zájmeno), jinak „je připravený“
                    if (neighbour(k, 1, g, adjectiveOnly = true) == null && w == "je" && k + 1 in words.indices &&
                        !broken(words[k], words[k + 1]) && isParticiple(low(words[k + 1]), g) && set(words[k + 1], g)) {
                        chain(k + 1, g); continueSameSubject(k + 1, g)
                    } else if (w.endsWith("byl") || w.endsWith("byla")) continueSameSubject(k, g)
                    return
                }
                if (w in SHORT || w in SHORT_F) { set(words[k], g); k++; continue }   // „Andy sám přišel“
                if (isParticiple(w, g)) {
                    if (set(words[k], g)) {
                        chain(k, g)
                        // „Andy přišel sám“ – jen „sám/sama“, jiné přídavné jméno může patřit k předmětu
                        if (k + 1 in words.indices && !broken(words[k], words[k + 1]) && low(words[k + 1]) in setOf("sám", "sama"))
                            set(words[k + 1], g)
                        continueSameSubject(k, g)
                    }
                    return
                }
                if (w !in FILLERS3 || skipped >= 3) return
                k++; skipped++
            }
        }

        for ((n, i) in words.withIndex()) {
            val w = low(i)
            val g = when (w) { in AUX1 -> me; in AUX2 -> you; else -> null } ?: continue
            val adjOnlyAfter = w == "buď" || w == "nebuď"
            if (!adjOnlyAfter) chain(neighbour(n, -1, g), g)
            chain(neighbour(n, 1, g, adjectiveOnly = adjOnlyAfter), g)
        }
        // třetí osoba: jméno postavy a za ním (přes pár příslovcí) příčestí, nebo „je/byl“ + přídavné jméno
        val srcMentions = cast.mentions(source)
        for ((from, to, g) in mentions) {
            val n = words.indexOfFirst { starts[it] >= from }.takeIf { it >= 0 } ?: continue
            // víceslovné jméno („Cal Hampton“): pokračuje se od posledního slova jména
            var last = n
            while (last + 1 in words.indices && tokens[words[last + 1]].first().isUpperCase() && !broken(words[last], words[last + 1])) last++
            after3(last, g)
            // obrácený slovosled („Zavolal Andy.“, „Pak přišel Andy.“) – jen když originál má jméno jako podmět
            // („Andy called“) a nemluví o nikom opačného rodu („He called Andy.“ = Andy je předmět)
            val name = translated.substring(from, to)
            if (n > 0 && !broken(words[n - 1], words[n]) && subjectInSource(source, name, srcMentions) &&
                !oppositeInSource(source, g, srcMentions) && clauseStart(n - 1) && !nearAux(n - 1) &&
                isParticiple(low(words[n - 1]), g)) {
                if (set(words[n - 1], g)) { chain(n - 1, g); continueSameSubject(n - 1, g) }
            }
        }
        // zájmeno v originálu („Andy called. She said she's coming.“): jen žena (výchozí mužský rod překladače),
        // jen když originál zmiňuje ženskou postavu a nikoho mužského rodu a příčestí stojí na začátku věty
        if (srcMentions.any { it.third == Gender.F } && srcMentions.none { it.third == Gender.M } &&
            EN_SHE.containsMatchIn(source) && !EN_MALE.containsMatchIn(source) && !EN_IT_PAST.containsMatchIn(source) &&
            words.none { low(it) in CS_MALE }) {
            val sheWas = EN_SHE_WAS.containsMatchIn(source)
            for (k in words.indices) {
                val i = words[k]
                if (i in done || !clauseStart(k) || nearAux(k)) continue
                val w = low(i)
                if (w in LINK3) {
                    // „…, že je/byl připravený“ bez podmětu – jen se „she is/was“ v originálu a přídavným jménem hned za ním
                    if (!sheWas || k + 1 !in words.indices || broken(i, words[k + 1])) continue
                    if (convert(low(words[k + 1]), Gender.F, adjectiveOnly = true) == null) continue
                    set(i, Gender.F); set(words[k + 1], Gender.F, adjectiveOnly = true)
                    continue
                }
                if (!isParticiple(w, Gender.F) || w.endsWith("la")) continue
                // další slovo s velkým písmenem (jiné než ženská postava) může být podmět („Řekl Jason…“)
                if (k + 1 in words.indices && !broken(i, words[k + 1]) && tokens[words[k + 1]].first().isUpperCase() &&
                    mentions.none { it.first == starts[words[k + 1]] && it.third == Gender.F }) continue
                if (set(i, Gender.F)) chain(k, Gender.F)
            }
        }
        return tokens.joinToString("")
    }

    /** Slovo je minulé příčestí jednotného čísla (na -l / -la), které umíme převést na rod [g]. */
    private fun isParticiple(w: String, g: Gender): Boolean {
        if (w in NOT_PART || w in SHORT || w in SHORT_F || w.endsWith("ý") || w.endsWith("á")) return false
        if (!(w.endsWith("l") || w.endsWith("la"))) return false
        // „-la“ je jen ženský tvar příčestí, když jeho mužský tvar je také příčestí („šla“, „byla“, „řekla“)
        if (w.endsWith("la") && ((w.length < 4 && w != "šla") || w.dropLast(1).endsWith("tel"))) return false
        return convert(w, g, adjectiveOnly = false) != null
    }

    /** Jméno [name] je v originálu podmětem: hned za ním je sloveso malým písmem („Andy called“, „Andy's gone“). */
    private fun subjectInSource(source: String, name: String, srcMentions: List<Triple<Int, Int, Gender>>): Boolean =
        srcMentions.any { (a, b, _) ->
            source.substring(a, b) == name && Regex("^(?:'s)?\\s+(?!and\\b|or\\b)[a-z]").containsMatchIn(source.substring(b))
        }

    /** V originálu je řeč i o někom opačného rodu (zájmeno, postava) – pak není jasné, ke komu tvar patří. */
    private fun oppositeInSource(source: String, g: Gender, srcMentions: List<Triple<Int, Int, Gender>>): Boolean =
        srcMentions.any { it.third != g } || (if (g == Gender.F) EN_MALE else EN_FEMALE).containsMatchIn(source)

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
