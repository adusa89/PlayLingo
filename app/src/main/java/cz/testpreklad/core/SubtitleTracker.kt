package cz.testpreklad.core

/**
 * Sleduje text v jednom pásu obrazovky (nahoře / dole) a rozhoduje,
 * kdy titulek zobrazit, kdy ho schovat a kdy nedělat nic.
 *
 * - Titulek, který vypadá celý, se zobrazí hned. Nedokončený text (bez tečky na konci) až poté,
 *   co ho OCR přečte [stableFrames]× po sobě (odfiltruje nabíhající titulek a náhodný šum).
 * - Když text zmizí, pruh se schová po [hideAfterEmpty] prázdných snímcích.
 * - Drobné rozdíly v OCR mezi snímky se považují za stejný text.
 */
class SubtitleTracker(
    private val stableFrames: Int = 2,
    private val hideAfterEmpty: Int = 2,
    /** Titulek, který vypadá celý, zobrazit hned po prvním přečtení (obraz přímo z konzole je čistý). */
    private val fastComplete: Boolean = false,
) {
    sealed class Action {
        object None : Action()
        object Hide : Action()
        data class Show(val text: String) : Action()
    }

    /** Text, jehož překlad je právě zobrazený ("" = nic). */
    var shown: String = ""
        private set

    private var candidate: String = ""
    private var count: Int = 0

    /** True, když se obraz ustálil – další OCR není potřeba, dokud se obraz nezmění. */
    val isSettled: Boolean
        get() = count >= maxOf(stableFrames, hideAfterEmpty) &&
            (candidate.isEmpty() || TextLogic.similar(candidate, shown))  // nepřeložený text = ještě ne

    /**
     * [slowDevice] = čtení obrazu trvá dlouho (slabá televize). Pak se nečeká na druhé potvrzení,
     * pokud titulek vypadá celý (končí tečkou, otazníkem…) – jinak by překlad přišel pozdě nebo vůbec.
     */
    fun onOcr(text: String, slowDevice: Boolean = false): Action {
        // Obraz přichází čistý přímo z konzole, takže titulek, který vypadá celý (končí tečkou,
        // otazníkem…), se překládá hned po prvním přečtení – čekání na potvrzení by jen zdržovalo.
        val needStable = if ((fastComplete || slowDevice) && TextLogic.looksComplete(text)) 1 else stableFrames
        val needEmpty = if (slowDevice) 1 else hideAfterEmpty
        if (TextLogic.similar(text, candidate)) {
            count++
            // u podobných textů si pamatujeme ten delší (OCR někdy usekne písmeno)
            if (text.length > candidate.length) candidate = text
        } else {
            candidate = text
            count = 1
        }

        if (candidate.isEmpty()) {
            if (count >= needEmpty && shown.isNotEmpty()) {
                shown = ""
                return Action.Hide
            }
            return Action.None
        }
        if (count < needStable) return Action.None
        if (TextLogic.similar(candidate, shown)) return Action.None
        return Action.Show(candidate)
    }

    /**
     * Text, který se právě objevil a ještě není potvrzený (přečtený jen jednou).
     * Služba ho začne překládat hned – než přijde potvrzení, je překlad hotový.
     */
    val pendingText: String
        get() = if (candidate.isNotEmpty() && count < stableFrames && !TextLogic.similar(candidate, shown)) candidate else ""

    /** Na obrazovce je jiný text, než jaký je přeložený (nový titulek se ustaluje nebo mizí). */
    val isChanging: Boolean get() = !TextLogic.similar(candidate, shown)

    /** Volá se po úspěšném překladu a zobrazení. */
    fun confirmShown(text: String) {
        shown = text
    }

    /** Vynutí nové vyhodnocení (např. po změně nastavení). */
    fun reset() {
        shown = ""; candidate = ""; count = 0
    }
}
