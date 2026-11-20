package cz.playlingo.core

/**
 * Barvy jmen mluvčích v pruhu: každá postava má svou, aby se v rozhovoru dalo poznat, kdo mluví.
 *
 * Stejné jméno má po celou dobu čtení stejnou barvu ("MIRA" i "Mira" jsou jedna postava).
 * První mluvčí dostane žlutou jako dřív; další barva se vybere podle jména (pořád stejně),
 * ale tak, aby se nepotkala s barvou nikoho z posledních mluvčích. Všechny barvy jsou světlé,
 * dobře čitelné na černém pruhu, a liší se od bílého textu i šedého originálu.
 */
class SpeakerColors(
    /**
     * Pevné barvy postav z profilu hry ([GameProfile.colorsByKey]: zjednodušené jméno i přezdívky → barva), jako
     * ve hře (GTA VI: Jason žlutě, Lucia modře). Ostatní postavy dostanou barvu z palety, jinou než tyto.
     */
    private val preset: Map<String, Int> = emptyMap(),
) {

    companion object {
        /** Žlutá (dosavadní barva jmen), modrá, zelená, růžová, fialová, oranžová, tyrkysová. */
        val PALETTE: IntArray = longArrayOf(
            0xFFF2C14E, 0xFF7FC8F8, 0xFF8EDB8A, 0xFFF59A9A, 0xFFC7A6F7, 0xFFF5A55A, 0xFF6FE0D2,
        ).map { it.toInt() }.toIntArray()

        private const val MAX_NAMES = 200

        /** Jméno sjednocené pro porovnání (velikost písmen, diakritika, interpunkce). */
        fun key(name: String): String = TextLogic.simplify(name)

        /** Barva jako "#RRGGBB" (pro televizi LG). */
        fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    }

    // v pořadí, jak naposledy mluvili (nejnovější na konci)
    private val known = LinkedHashMap<String, Int>(16, 0.75f, true)

    @Synchronized fun colorOf(name: String): Int {
        val k = key(name)
        if (k.isEmpty()) return PALETTE[0]
        preset[k]?.let { return it }
        known[k]?.let { return it }
        // paleta bez barev, které mají postavy z profilu hry (ať se nepletou)
        val palette = PALETTE.filter { it !in preset.values }.ifEmpty { PALETTE.toList() }
        val recent = known.values.toList().takeLast(palette.size - 1).toSet()
        val start = if (known.isEmpty()) 0 else Math.floorMod(k.hashCode(), palette.size)
        var color = palette[start]
        for (i in palette.indices) {
            val c = palette[(start + i) % palette.size]
            if (c !in recent) { color = c; break }
        }
        known[k] = color
        if (known.size > MAX_NAMES) known.remove(known.keys.first())
        return color
    }

    /** Barva naučená z obrazu hry – nahradí přidělenou (ne barvu z profilu hry). */
    @Synchronized fun learn(name: String, color: Int) {
        val k = key(name)
        if (k.isEmpty() || preset.containsKey(k)) return
        known[k] = color
    }

    /** Barvy všech mluvčích v pruhu (jméno → barva). */
    fun colorsFor(segments: List<Segment>): Map<String, Int> =
        segments.mapNotNull { it.speaker }.distinct().associateWith { colorOf(it) }
}
