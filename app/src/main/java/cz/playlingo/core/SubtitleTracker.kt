package cz.playlingo.core

/**
 * Sleduje text v jednom pásu obrazovky (nahoře / dole) a rozhoduje,
 * kdy titulek zobrazit, kdy ho schovat a kdy nedělat nic.
 *
 * - Titulek, který vypadá celý, se zobrazí hned. Nedokončený text (bez tečky na konci) až poté,
 *   co ho OCR přečte [stableFrames]× po sobě (odfiltruje nabíhající titulek a náhodný šum).
 * - Když text zmizí, pruh se schová po [hideAfterEmpty] prázdných snímcích.
 * - Drobné rozdíly v OCR mezi snímky se považují za stejný text.
 * - Na nezměněném obrazu (stejný otisk oblasti, viz [FrameSignature]) ochuzené čtení – méně řádků,
 *   jen část slov nebo nic – zobrazený text nenahradí ani neschová. OCR občas z víceřádkového
 *   titulku přečte jen poslední řádek; obraz se přitom nezměnil, takže titulek tam pořád celý je.
 *   Když se obraz změní, text se zkrátit i zmizet může jako dřív.
 * - [revision] se zvýší při každém novém začátku (reset, jiná oblast). Výsledek čtení nebo překladu,
 *   který doběhne až po něm, je zastaralý a nesmí se ukázat (viz [isCurrent], [confirmShown]).
 */
class SubtitleTracker(
    private val stableFrames: Int = 2,
    private val hideAfterEmpty: Int = 2,
    /** Zobrazit titulek hned po prvním přečtení (obraz přímo z konzole je čistý, potvrzení netřeba). */
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

    /** Otisk obrazu, ze kterého je zobrazený text, a otisk posledního čtení (null = neznámý). */
    private var shownSig: IntArray? = null
    private var shownCellPixels: Int = 1
    private var readSig: IntArray? = null
    private var readCellPixels: Int = 1

    /** Pořadové číslo „začátku“; zvyšuje se při [reset] a [regionChanged]. */
    @Volatile var revision: Long = 0L
        private set

    /** Patří výsledek spuštěný při [token] (= [revision] v tu chvíli) pořád k aktuálnímu čtení? */
    fun isCurrent(token: Long): Boolean = token == revision

    /** True, když se obraz ustálil – další OCR není potřeba, dokud se obraz nezmění. */
    val isSettled: Boolean
        get() = count >= maxOf(stableFrames, hideAfterEmpty) &&
            (candidate.isEmpty() || TextLogic.similar(candidate, shown))  // nepřeložený text = ještě ne

    /**
     * [slowDevice] = čtení obrazu trvá dlouho (slabá televize). Pak se nečeká na druhé potvrzení,
     * pokud titulek vypadá celý (končí tečkou, otazníkem…) – jinak by překlad přišel pozdě nebo vůbec.
     */
    @Synchronized
    fun onOcr(text: String, slowDevice: Boolean = false, sig: IntArray? = null, cellPixels: Int = 1): Action {
        readSig = sig; readCellPixels = cellPixels
        // Obraz se od zobrazeného textu nezměnil a čtení je jen jeho ochuzená verze (méně řádků,
        // část slov, nic) → titulek na obrazovce pořád celý je, OCR ho jen přečetlo hůř.
        // Takové čtení se nebere: nenahradí zobrazený text ani ho neschová.
        if (shown.isNotEmpty() && sameImageAsShown(sig, cellPixels) && TextLogic.isSubsetRead(text, shown)) {
            // počítá se jako další potvrzení zobrazeného textu – obraz se pak může ustálit
            if (TextLogic.similar(candidate, shown)) count++
            return Action.None
        }
        // stejný text na (třeba mírně pohnutém) obraze → otisk zobrazeného textu se posune s obrazem
        if (shown.isNotEmpty() && sig != null && TextLogic.similar(text, shown)) {
            shownSig = sig; shownCellPixels = cellPixels
        }
        // Obraz přichází čistý přímo z konzole, takže titulek, který vypadá celý (končí tečkou,
        // otazníkem…), se překládá hned po prvním přečtení – čekání na potvrzení by jen zdržovalo.
        // S čistým obrazem z konzole ([fastComplete]) se zobrazí hned každý text, i nedokončený –
        // kdyby se ještě dopisoval, překlad se s dalším čtením jen doplní.
        val needStable = if (fastComplete || (slowDevice && TextLogic.looksComplete(text))) 1 else stableFrames
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
        if (TextLogic.similar(candidate, shown)) {
            // první přečtení bylo o písmeno kratší (OCR ho useklo) a další čtení se shodla na delším
            // textu → překlad se opraví
            if (fastComplete && count >= 2 && candidate.length > shown.length) return Action.Show(candidate)
            return Action.None
        }
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
    @Synchronized
    fun confirmShown(text: String, token: Long = revision): Boolean {
        if (token != revision) return false   // mezitím nový začátek → zastaralý překlad
        shown = text
        shownSig = readSig; shownCellPixels = readCellPixels
        return true
    }

    private fun sameImageAsShown(sig: IntArray?, cellPixels: Int): Boolean {
        val ref = shownSig ?: return false
        if (sig == null || cellPixels != shownCellPixels) return false
        return !FrameSignature.changed(ref, sig, cellPixels)
    }

    /** Čte se jiná oblast obrazovky: otisky už nejdou porovnat a rozpracované výsledky neplatí. */
    @Synchronized
    fun regionChanged() {
        shownSig = null; readSig = null
        revision++
    }

    /** Vynutí nové vyhodnocení (např. po změně nastavení). */
    @Synchronized
    fun reset() {
        shown = ""; candidate = ""; count = 0
        shownSig = null; readSig = null
        revision++
    }
}
