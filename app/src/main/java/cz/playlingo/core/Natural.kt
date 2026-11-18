package cz.playlingo.core

/**
 * Přirozenější čeština po strojovém překladu: sjednotí oslovení na tykání.
 *
 * Offline překladač střídá tykání a vykání větu od věty („Co tady děláš?“ a hned „Jste si jistý?“),
 * což v herních dialozích zní strojově. Postavy si ve hrách většinou tykají, proto se vykání
 * jedné osobě převede na tykání. Věty, které mluví k více lidem, zůstávají („Vy dva, pojďte“):
 * pozná se to z anglického originálu.
 *
 * Převádí se jen tvary, u kterých je to jisté (seznam běžných sloves a spolehlivé koncovky).
 * Neznámé slovo zůstane, jak ho překladač napsal – raději nic než špatný tvar.
 */
object Natural {
    /** Anglická věta mluví k více lidem → oslovení se nemění. */
    private val PLURAL_EN = Regex(
        "\\b(you (all|two|three|four|both|guys|lot|boys|men|people|folks|fools|idiots)|(all|both|two|three|some|many|any|none|each|one|rest) of you" +
            "|y'all|ya'll|everyone|everybody|boys|guys|gentlemen|ladies|folks|men|lads|fellas|people|friends|brothers|soldiers|you and (your|the|my|his|her|that|those) \\w+)\\b",
        RegexOption.IGNORE_CASE)

    private val WORDS: Map<String, String> = mapOf(
        // být a podmiňovací způsob
        "jste" to "jsi", "nejste" to "nejsi", "byste" to "bys", "abyste" to "abys", "kdybyste" to "kdybys",
        // zájmena
        "vy" to "ty", "vás" to "tě", "vám" to "ti", "vámi" to "tebou",
        "váš" to "tvůj", "vaše" to "tvoje", "vašeho" to "tvého", "vašemu" to "tvému", "vaší" to "tvojí", "vaši" to "tvoji",
        "vašich" to "tvých", "vašim" to "tvým", "vašimi" to "tvými", "vaším" to "tvým", "vašem" to "tvém",
        // krátká slovesa (na obecné koncovky jsou moc krátká)
        "víte" to "víš", "nevíte" to "nevíš", "máte" to "máš", "nemáte" to "nemáš", "znáte" to "znáš", "dáte" to "dáš",
        // slovesa s koncovkou -ete (sama koncovka je dvojznačná, proto jen známá)
        "chcete" to "chceš", "nechcete" to "nechceš", "můžete" to "můžeš", "nemůžete" to "nemůžeš", "budete" to "budeš",
        "nebudete" to "nebudeš", "jdete" to "jdeš", "nejdete" to "nejdeš", "jedete" to "jedeš", "přijdete" to "přijdeš",
        "půjdete" to "půjdeš", "nepůjdete" to "nepůjdeš", "dostanete" to "dostaneš", "najdete" to "najdeš", "řeknete" to "řekneš",
        "vezmete" to "vezmeš", "zemřete" to "zemřeš", "pomůžete" to "pomůžeš", "smíte" to "smíš", "nesmíte" to "nesmíš",
        "zůstanete" to "zůstaneš", "přestanete" to "přestaneš", "stanete" to "staneš", "necháte" to "necháš",
        // rozkazy, které nejdou poznat podle koncovky
        "pojďte" to "pojď", "nechte" to "nech", "držte" to "drž", "běžte" to "běž", "buďte" to "buď", "nebuďte" to "nebuď",
        "vraťte" to "vrať", "pusťte" to "pusť", "zůstaňte" to "zůstaň", "přestaňte" to "přestaň", "pomozte" to "pomoz",
        "mlčte" to "mlč", "věřte" to "věř", "ukažte" to "ukaž", "zrušte" to "zruš", "zahoďte" to "zahoď", "pospěšte" to "pospěš",
        "dostaňte" to "dostaň", "postavte" to "postav", "vstaňte" to "vstaň", "zastavte" to "zastav", "odpusťte" to "odpusť",
        "promiňte" to "promiň", "zapomeňte" to "zapomeň", "poslyšte" to "poslyš", "mluvte" to "mluv", "hoďte" to "hoď",
        "přineste" to "přines", "pojeďte" to "pojeď", "otevřete" to "otevři", "zavřete" to "zavři", "položte" to "polož",
        "odložte" to "odlož", "střelte" to "střel", "zabte" to "zab", "berte" to "ber", "neste" to "nes", "kupte" to "kup",
        "jděte" to "jdi", "nejděte" to "nejdi", "odejděte" to "odejdi", "přijďte" to "přijď", "vypadněte" to "vypadni",
        // přídavná jména a příčestí po „jsi“ / „buď“ (množné číslo → jednotné)
        "opatrní" to "opatrný", "jistí" to "jistý", "připraveni" to "připraven", "připravení" to "připravený", "hotovi" to "hotov",
        "hotoví" to "hotový", "klidní" to "klidný", "šílení" to "šílený", "zticha" to "zticha",
    )

    /** Příčestí minulá, která se z množného čísla nepřevedou jen odtržením „i“. */
    private val PARTICIPLES: Map<String, String> = mapOf(
        "šli" to "šel", "přišli" to "přišel", "odešli" to "odešel", "našli" to "našel", "vešli" to "vešel", "nešli" to "nešel",
        "mohli" to "mohl", "nemohli" to "nemohl", "řekli" to "řekl", "neřekli" to "neřekl",
    )
    private val NOT_PARTICIPLE = setOf("jestli", "zdali", "chvíli", "neděli", "kvůli", "postupně", "zatímco", "pokud")
    private val AUX = setOf("jste", "nejste", "byste", "abyste", "kdybyste")
    /** Krátká slova, která mohou stát mezi pomocným slovesem a příčestím („kdybyste mi poslouchali“). */
    private val FILLERS = setOf("mi", "se", "si", "to", "mu", "ho", "jí", "je", "nám", "tam", "tu", "sem", "už", "ještě", "mě", "ti", "tě")
    /** Oslovení končící stejně jako sloveso („Piráte!“) – nepřevádět. */
    private val NOT_VERB = setOf("piráte", "kamaráte", "magnáte", "renegáte", "kandidáte", "advokáte", "demokrate")

    private val TOKEN = Regex("[\\p{L}]+|[^\\p{L}]+")

    /** Sjednotí oslovení v českém překladu na tykání; [source] je anglický originál. */
    fun czech(source: String, translated: String): String {
        if (PLURAL_EN.containsMatchIn(source)) return translated
        val tokens = TOKEN.findAll(translated).map { it.value }.toMutableList()
        val words = tokens.indices.filter { tokens[it].first().isLetter() }
        fun low(i: Int) = tokens[i].lowercase()
        // příčestí v množném čísle u pomocného slovesa („neměli byste“, „kdybyste mi poslouchali“) → jednotné
        for ((n, i) in words.withIndex()) {
            if (low(i) !in AUX) continue
            for (step in listOf(-1, 1)) {
                var k = n + step
                var skipped = 0
                while (k in words.indices && low(words[k]) in FILLERS && skipped < 2) { k += step; skipped++ }
                if (k !in words.indices) continue
                val w = low(words[k])
                if (w !in PARTICIPLES && (w.length < 4 || !w.endsWith("li") || w in NOT_PARTICIPLE)) continue
                tokens[words[k]] = keepCase(tokens[words[k]], PARTICIPLES[w] ?: w.dropLast(1))
            }
        }
        for (i in words) {
            val w = low(i)
            val to = WORDS[w] ?: bySuffix(w) ?: continue
            tokens[i] = keepCase(tokens[i], to)
        }
        return tokens.joinToString("")
    }

    /** Spolehlivé koncovky: oznamovací způsob -áte/-íte/-ujete a rozkazy na -jte/-něte/-měte. */
    private fun bySuffix(w: String): String? {
        if (w.length < 5 || w in NOT_VERB) return null
        return when {
            w.endsWith("ujete") -> w.dropLast(5) + "uješ"
            w.endsWith("jte") -> w.dropLast(2)                 // počkejte → počkej, stůjte → stůj
            w.endsWith("něte") -> w.dropLast(4) + "ni"         // řekněte → řekni, sedněte → sedni
            w.endsWith("měte") -> w.dropLast(4) + "mi"         // vezměte → vezmi
            w.endsWith("áte") -> w.dropLast(3) + "áš"          // máte → máš, děláte → děláš
            w.endsWith("íte") -> w.dropLast(3) + "íš"          // víte → víš, musíte → musíš
            else -> null
        }
    }

    private fun keepCase(original: String, replacement: String): String =
        if (original.first().isUpperCase()) replacement.replaceFirstChar { it.uppercaseChar() } else replacement
}
