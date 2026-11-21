package cz.playlingo.core

/**
 * Řádek s úkolem pod titulky (GTA: „Go to the safehouse.“ dole pod replikou, mezi nimi volný řádek) jako samostatná
 * věc vedle titulků (od 0.97.43, přání majitele): vyčlení ho [TextLogic.objectiveLine], ukazuje se ve vlastním pruhu
 * na místě anglického úkolu a drží se, dokud je úkol ve hře vidět – nezávisle na tom, kdy zmizí titulek.
 *
 * [update] dostane po každém čtení obrazu přečtený úkol (nebo null) a řekne, co udělat. Drobné chyby čtení
 * (jedno písmeno) se za nový úkol nepovažují; zmizí až po [misses] čteních bez něj (čtečka ho občas vynechá).
 */
class ObjectiveTracker(private val misses: Int = 2) {
    sealed class Change {
        /** Nový úkol – přeložit a ukázat. */
        data class Show(val text: String) : Change()
        /** Úkol zmizel – schovat. */
        object Hide : Change()
        /** Nic se nemění. */
        object Keep : Change()
    }

    /** Právě zobrazený úkol (anglicky), null = žádný. */
    var current: String? = null
        private set
    private var missed = 0

    /** Naučená horní hrana úkolu jako zlomek výšky obrazu (-1 = ještě nevíme). */
    var top = -1f
        private set

    fun update(read: String?, topFraction: Float = -1f): Change {
        val t = read?.let { TextLogic.clean(it) }?.takeIf { it.isNotBlank() }
        if (t == null) {
            if (current == null) return Change.Keep
            missed++
            if (missed < misses) return Change.Keep
            current = null; missed = 0
            return Change.Hide
        }
        missed = 0
        if (topFraction in 0f..1f) top = if (top < 0f) topFraction else top * 0.7f + topFraction * 0.3f
        val cur = current
        if (cur != null && same(cur, t)) return Change.Keep
        current = t
        return Change.Show(t)
    }

    /** Konec čtení (zastavení, jiná hra, pauza): úkol pryč, místo se pamatuje dál. */
    fun reset() { current = null; missed = 0 }

    companion object {
        /** Stejný úkol i s drobnou chybou čtení (nejvýš 1 znak na 12). */
        fun same(a: String, b: String): Boolean {
            val x = TextLogic.simplify(a); val y = TextLogic.simplify(b)
            if (x == y) return true
            val limit = maxOf(1, maxOf(x.length, y.length) / 12)
            return kotlin.math.abs(x.length - y.length) <= limit && TextLogic.levenshtein(x, y) <= limit
        }
    }
}
