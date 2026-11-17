package cz.playlingo.core

/**
 * Zvětšení výřezu před čtením. Čtečka textu potřebuje písmo vysoké aspoň kolem 20 pixelů;
 * drobné herní titulky v obrazu 720p mívají 11–16. Když jsou řádky v obraze malé, výřez se před
 * čtením zvětší; když jsou velké, čte se bez zvětšení (rychleji).
 */
class TextScale(private val maxPixels: Int = 950_000) {
    /** Aktuální zvětšení: 1, 1,5 nebo 2. */
    var scale = 1f
        private set
    private var wanted = 1f
    private var votes = 0

    /**
     * [lineHeights] = výšky přečtených řádků v pixelech PŮVODNÍHO obrazu (bez zvětšení),
     * [cropPixels] = počet pixelů výřezu. Vrací true, když se zvětšení změnilo.
     */
    fun update(lineHeights: List<Int>, cropPixels: Int): Boolean {
        val hs = lineHeights.filter { it >= 6 }.sorted()
        if (hs.isEmpty()) return false
        val median = hs[hs.size / 2]
        var target = when {
            median < 14 -> 2f
            median < 21 -> 1.5f
            median > 28 -> 1f
            else -> scale                       // mezi tím nic neměnit (ať to nekmitá)
        }
        // strop: zvětšený výřez nesmí být tak velký, aby čtení trvalo neúměrně dlouho
        while (target > 1f && cropPixels * target * target > maxPixels) target -= 0.5f
        if (target == scale) { votes = 0; return false }
        if (target == wanted) votes++ else { wanted = target; votes = 1 }
        if (votes < 2) return false             // až po dvou shodných čteních
        scale = target; votes = 0
        return true
    }
}
