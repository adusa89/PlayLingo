package cz.testpreklad.core

/**
 * Ukázková „hra“: anglické titulky, které se střídají podle času. Stejný rozvrh používá
 * obrazovka ukázky i ukázkové video, takže pruh s překladem sedí na to, co je vidět.
 */
object DemoGame {
    val lines = listOf(
        "Marek: We need money, Jonas.",
        "",
        "Jonas: I reckon we won't find anything in Greyford.",
        "",
        "Get on your horse, we ride at dawn.",
        "",
        "Silas: If we keep going like this,\nthe marshals will find us.",
        "",
        // různé podoby titulků, jak je hry píšou
        "MIRA: Are you sure this is the right way?",          // jméno velkými písmeny
        "",
        "Tobin: no idea, but we can't stay here",             // malé písmeno na začátku, bez tečky
        "",
        "WE HAVE TO GET OUT OF HERE!",                        // celé velkými písmeny
        "",
        "Wait",                                               // jedno krátké slovo
        "",
        "[Old Kessler] You two were never supposed to meet.", // jméno v závorkách
        "",
        "Mira: I'll take the left door.\nTobin: Then I'm going right.",   // dva mluvčí najednou
        "",
    )
    const val SHOWN_MS = 6000L
    const val GAP_MS = 1500L
    val cycleMs: Long = lines.sumOf { if (it.isEmpty()) GAP_MS else SHOWN_MS }

    /** Pořadí titulku, který je vidět v čase [ms] od začátku. */
    fun indexAt(ms: Long): Int {
        var t = ((ms % cycleMs) + cycleMs) % cycleMs
        for ((i, l) in lines.withIndex()) {
            val d = if (l.isEmpty()) GAP_MS else SHOWN_MS
            if (t < d) return i
            t -= d
        }
        return 0
    }

    fun subtitleAt(ms: Long): String = lines[indexAt(ms)]

    /** Kolik ms je právě zobrazený titulek na obrazovce (pro měření zpoždění překladu). */
    fun shownFor(ms: Long): Long {
        var t = ((ms % cycleMs) + cycleMs) % cycleMs
        for (l in lines) {
            val d = if (l.isEmpty()) GAP_MS else SHOWN_MS
            if (t < d) return t
            t -= d
        }
        return 0
    }
}
