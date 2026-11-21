package cz.playlingo.core

/**
 * Fronta titulků s nejkratší dobou zobrazení (od 0.97.46, přání majitele): rychle po sobě jdoucí repliky se
 * nepřepíšou dřív, než je jde přečíst – každá je v pruhu aspoň [minMs]. Další čekají ve frontě a fronta se
 * dožene v mezeře mezi titulky. Aby překlad neujížděl za hrou, čekají nejvýš [maxWaiting] repliky a žádná
 * déle než [maxLagMs] (starší se zahodí, poslední zůstane vždy). [minMs] = 0 → bez fronty, jako dřív.
 *
 * Čas se předává zvenku (testy); služba volá [poll] podle [dueIn].
 */
class ShowQueue<T>(
    @Volatile var minMs: Long,
    private val maxWaiting: Int = 3,
    private val maxLagMs: Long = 5000,
) {
    sealed class Action<out T> {
        data class Show<T>(val value: T) : Action<T>()
        object Hide : Action<Nothing>()
        object None : Action<Nothing>()
    }

    private class Item<T>(val value: T, val arrived: Long)

    private val waiting = ArrayDeque<Item<T>>()
    /** Co je v pruhu a od kdy (0 = nic). */
    private var shown = false
    private var shownAt = 0L
    /** Anglický titulek zmizel: schovat nejdřív v tuhle chvíli (0 = ne). */
    private var hideAt = 0L

    val isShown: Boolean @Synchronized get() = shown
    val waitingCount: Int @Synchronized get() = waiting.size

    /** Je zobrazený titulek v pruhu už dost dlouho, aby šel nahradit nebo schovat? */
    @Synchronized fun minElapsed(now: Long): Boolean = !shown || now - shownAt >= minMs

    /** Nový přeložený titulek. Vrací [Action.Show] (ukázat hned), nebo [Action.None] (čeká ve frontě). */
    @Synchronized fun submit(value: T, now: Long): Action<T> {
        hideAt = 0
        if (minMs <= 0 || (waiting.isEmpty() && minElapsed(now))) return showNow(value, now)
        waiting.addLast(Item(value, now))
        while (waiting.size > maxWaiting) waiting.removeFirst()
        return Action.None
    }

    /** Anglický titulek zmizel; překlad zůstane do [at] (prodloužení „Nechat déle“), pak se schová – až se fronta vyprázdní. */
    @Synchronized fun requestHide(at: Long) { hideAt = if (at <= 0) 1 else at }

    /** Co udělat teď: další z fronty, schovat, nebo nic. */
    @Synchronized fun poll(now: Long): Action<T> {
        if (!minElapsed(now)) return Action.None
        // zastaralé repliky pryč (ujížděly by za hrou), poslední zůstává
        while (waiting.size > 1 && now - waiting.first().arrived > maxLagMs) waiting.removeFirst()
        // z fronty: žádost o schování platí dál (anglický titulek už zmizel) – schová se po nejkratší době
        waiting.removeFirstOrNull()?.let { val h = hideAt; val a = showNow(it.value, now); hideAt = h; return a }
        if (shown && hideAt > 0 && now >= hideAt) { shown = false; hideAt = 0; return Action.Hide }
        return Action.None
    }

    /** Za kolik ms má smysl zavolat [poll] znovu (-1 = není na co čekat). */
    @Synchronized fun dueIn(now: Long): Long {
        if (!shown) return -1
        val minLeft = (shownAt + minMs - now).coerceAtLeast(0)
        return when {
            waiting.isNotEmpty() -> minLeft
            hideAt > 0 -> maxOf(minLeft, hideAt - now).coerceAtLeast(0)
            else -> -1
        }
    }

    /** Konec čtení, pauza, nové nastavení: nic neukazovat a nic nečeká. */
    @Synchronized fun clear() { waiting.clear(); shown = false; shownAt = 0; hideAt = 0 }

    private fun showNow(value: T, now: Long): Action<T> {
        shown = true; shownAt = now; hideAt = 0
        return Action.Show(value)
    }
}
