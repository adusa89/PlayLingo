package cz.playlingo.core

/**
 * Barva jména mluvčího přímo z obrazu hry (od 0.97.28). Čtečka textu barvu nevrací a čte se jen jas,
 * proto se barva bere zvlášť: jen když se ukáže nový titulek se jménem, a jen z malého obdélníku,
 * kde jméno leží ([cz.playlingo.StreamReader.requestColor]). Z toho se vezmou nejsvětlejší body (písmo,
 * ne pozadí) a jejich průměrná barva. Bílé / šedé jméno = hra ho nebarví → nic.
 *
 * Naučené barvy a měření času jdou do záznamu v síti (oddíl „barvy jmen ze hry“) – podle nich se barvy
 * přepíšou natvrdo do profilu hry ([GameProfile.colors]).
 */
object NameColor {
    /** Nejsvětlejší čtvrtina bodů je písmo; z nich průměrná barva. null = bez barvy (bílá, šedá, málo bodů). */
    fun fromSamples(ys: IntArray, us: IntArray, vs: IntArray, n: Int): Int? {
        if (n < 8) return null
        val sorted = ys.copyOf(n).sorted()
        val limit = maxOf(sorted[(n * 3) / 4], 110)
        var cnt = 0; var sy = 0; var su = 0; var sv = 0
        for (i in 0 until n) if (ys[i] >= limit) { cnt++; sy += ys[i]; su += us[i]; sv += vs[i] }
        if (cnt < 4) return null
        val rgb = Yuv.pixel(sy / cnt, su / cnt, sv / cnt)
        return if (isColored(rgb)) rgb else null
    }

    /** Je to opravdu barva (ne bílá/šedá)? Rozdíl nejsilnější a nejslabší složky. */
    fun isColored(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) >= 50
    }

    /** Nejbližší barva z palety pruhu (čitelná na černém podkladu). */
    fun nearest(rgb: Int, palette: IntArray = SpeakerColors.PALETTE): Int = palette.minByOrNull { p ->
        val dr = ((p shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)
        val dg = ((p shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)
        val db = (p and 0xFF) - (rgb and 0xFF)
        dr * dr + dg * dg + db * db
    } ?: palette[0]

    // ---- naučené barvy a měření (pro záznam v síti) ----

    private val seen = LinkedHashMap<String, HashMap<Int, Int>>()
    private var samples = 0
    private var sumMicros = 0L
    private var maxMicros = 0L

    /** Změřené čtení barvy (čas v µs) a výsledek (null = jméno bez barvy). */
    @Synchronized fun record(name: String, rgb: Int?, micros: Long) {
        samples++; sumMicros += micros; if (micros > maxMicros) maxMicros = micros
        if (rgb == null) return
        val m = seen.getOrPut(SpeakerColors.key(name)) { HashMap() }
        val p = nearest(rgb)
        m[p] = (m[p] ?: 0) + 1
        if (seen.size > 200) seen.remove(seen.keys.first())
    }

    /** Naučená barva jména (z palety), když se aspoň 2× shodla; jinak null. */
    @Synchronized fun learned(name: String): Int? =
        seen[SpeakerColors.key(name)]?.entries?.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key

    /** Barvy z profilu právě hrané hry – v záznamu se u nich ukáže neshoda s obrazem. */
    @Volatile var profile: Map<String, Int> = emptyMap()

    /** Pro záznam: měření, naučené barvy („jason #F2C14E ×12“) a neshody s profilem hry (oprava profilu). */
    @Synchronized fun report(): String {
        val avg = if (samples > 0) sumMicros / samples else 0
        val sb = StringBuilder("čtení barvy: $samples×, průměr $avg µs, nejdéle $maxMicros µs\n")
        for ((k, m) in seen) {
            sb.append(k).append(' ').append(m.entries.sortedByDescending { it.value }
                .joinToString(", ") { SpeakerColors.hex(it.key) + " ×" + it.value })
            val p = profile[k]
            val best = m.entries.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
            if (p != null && best != null && best != p) sb.append("  ← NESHODA: profil ").append(SpeakerColors.hex(p))
                .append(", ve hře ").append(SpeakerColors.hex(best))
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    @Synchronized fun reset() { seen.clear(); samples = 0; sumMicros = 0; maxMicros = 0 }
}
