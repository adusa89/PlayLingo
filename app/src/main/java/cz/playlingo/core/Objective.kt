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

/** Kus textu úkolu s barvou ([color] null = výchozí barva pruhu) – do TV jako `casti`, na Androidu jako span. */
data class ObjectivePart(val text: String, val color: Int?)

/**
 * Barevná slova úkolu (GTA: „Go to the <žlutě>safehouse</žlutě>.“) přenesená do překladu (pokus, výchozí vypnuto).
 * Barva každého slova se přečte z obrazu ([NameColor]); sousední slova stejné barvy jsou jeden [Run]. Do překladu se
 * barva přenese opatrně: kus najdený v překladu doslova (jména a místa se nepřekládají), jinak jeho samostatný
 * překlad; nic nenalezeno = výchozí barva.
 */
object ObjectiveColors {
    /** Souvislý kus anglického úkolu v jedné barvě (z palety [SpeakerColors.PALETTE]). */
    data class Run(val text: String, val color: Int)

    /** Slova a jejich barvy (null = bílé / neznámé) → barevné kusy; barvy se sjednotí na nejbližší z palety. */
    fun runs(words: List<String>, colors: List<Int?>): List<Run> {
        val out = ArrayList<Run>()
        var cur: Int? = null
        val sb = StringBuilder()
        fun flush() { cur?.let { c -> sb.toString().trim().takeIf { t -> t.any { it.isLetterOrDigit() } }?.let { out.add(Run(it, c)) } }; sb.setLength(0) }
        for (i in words.indices) {
            val c = colors.getOrNull(i)?.let { NameColor.nearest(it) }
            if (c != cur) { flush(); cur = c }
            if (c != null) sb.append(words[i]).append(' ')
        }
        flush()
        return out
    }

    /** Kus bez interpunkce na krajích („safehouse.“ → „safehouse“). */
    fun core(s: String): String = s.trim().trim { !it.isLetterOrDigit() }

    /**
     * Kde je [needle] v [text] (bez ohledu na velikost písmen, i bez interpunkce na krajích): jen od začátku slova;
     * konec na hranici slova, nebo se protáhne do konce slova o nejvýš 3 písmena (české koncovky: úkryt → úkrytu).
     * Pomíjí místa překrytá [taken]. null = nenalezeno.
     */
    fun locate(text: String, needle: String, taken: List<IntRange> = emptyList()): IntRange? {
        for (n in listOf(needle.trim(), core(needle)).distinct()) {
            if (n.count { it.isLetterOrDigit() } < 2) continue
            var from = 0
            while (true) {
                val i = text.indexOf(n, from, ignoreCase = true)
                if (i < 0) break
                from = i + 1
                if (i > 0 && text[i - 1].isLetterOrDigit() && n[0].isLetterOrDigit()) continue
                var end = i + n.length
                var extra = 0
                while (end < text.length && text[end].isLetter() && n.last().isLetterOrDigit()) { end++; extra++ }
                if (extra > 3) continue
                val r = i until end
                if (taken.any { it.first <= r.last && r.first <= it.last }) continue
                return r
            }
        }
        return null
    }

    /** Text rozdělený na kusy podle obarvených míst ([spans]: rozsah → barva); bez míst null. */
    fun parts(text: String, spans: List<Pair<IntRange, Int>>): List<ObjectivePart>? {
        if (spans.isEmpty()) return null
        val out = ArrayList<ObjectivePart>()
        var pos = 0
        for ((r, c) in spans.sortedBy { it.first.first }) {
            if (r.first < pos || r.last >= text.length) continue
            if (r.first > pos) out.add(ObjectivePart(text.substring(pos, r.first), null))
            out.add(ObjectivePart(text.substring(r.first, r.last + 1), c))
            pos = r.last + 1
        }
        if (pos < text.length) out.add(ObjectivePart(text.substring(pos), null))
        return out.takeIf { p -> p.any { it.color != null } }
    }
}
