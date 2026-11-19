package cz.playlingo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import cz.playlingo.core.TvSender
import cz.playlingo.core.TvType

/**
 * Typ televize na jednom místě, aby ho šlo nabídnout odkudkoli (Domů, Nastavení, průvodce prvním
 * spuštěním): Android TV (pruh ukazuje toto zařízení, výchozí), LG (webOS) nebo Samsung (Tizen) –
 * tam toto zařízení jen čte a překládá a věty posílá aplikaci PlayLingo v televizi (core/TvSender.kt).
 *
 *  - [show] – nabídka typů; vybraný typ uloží ([Prefs.setTvType]), pošle běžícímu překladu
 *    a zavolá `onPicked`,
 *  - [line] – krátký řádek pro Domů („Typ televize: LG (webOS) – připojeno“),
 *  - [needsInstall] – má se nabízet nahrání aplikace do televize (aplikace se z ní ještě nikdy neohlásila)?
 *  - [openInstall] – průvodce nahráním pro LG / Samsung.
 */
object TvChoice {
    fun label(c: Context, type: TvType): String = c.tx("tv_type_" + type.key)

    /** Nabídka typů televize. Vybraný typ se hned uloží a projeví v běžícím překladu. */
    fun show(activity: Activity, onPicked: (TvType) -> Unit = {}) {
        val types = TvType.values().toList()
        val current = types.indexOf(Prefs.tvType(activity))
        fun pick(i: Int) {
            val t = types[i]
            Prefs.setTvType(activity, t)
            if (TranslatorService.running) {
                activity.startService(Intent(activity, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
            }
            onPicked(t)
        }
        try {
            AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(activity.tx("tv_type"))
                .setSingleChoiceItems(types.map { label(activity, it) }.toTypedArray(), current) { d, i -> d.dismiss(); pick(i) }
                .show()
        } catch (_: Exception) {
            // nouzově: další možnost v pořadí
            pick((current + 1) % types.size)
        }
    }

    /**
     * Zapamatuje si, že se z televize daného typu někdy ohlásila aplikace PlayLingo (model a verze),
     * aby Domů přestalo nabízet nahrání. Volá se při každém vykreslení – stačí levné čtení.
     */
    private fun remember(c: Context, type: TvType) {
        val a = TvSender.lastSeen?.announce ?: return
        val p = type.platform ?: return
        if (a.platform != p) return
        val v = listOfNotNull(a.model, a.version).joinToString(" · ")
        if (Prefs.tvSeen(c, p) != v) Prefs.setTvSeen(c, p, v)
    }

    /** Ohlásila se už někdy aplikace z televize tohoto typu? */
    fun seen(c: Context, type: TvType): Boolean {
        remember(c, type)
        return type.platform?.let { Prefs.tvSeen(c, it) } != null
    }

    fun needsInstall(c: Context, type: TvType = Prefs.tvType(c)): Boolean = type.sends && !seen(c, type)

    /** Řádek pro Domů: typ, u LG/Samsungu model a stav spojení. Druhá hodnota = připojeno. */
    fun line(c: Context): Pair<String, Boolean> {
        val type = Prefs.tvType(c)
        val head = c.tx("tv_type") + ": " + label(c, type)
        if (!type.sends) return head to false
        val model = TvSender.lastSeen?.announce?.takeIf { it.platform == type.platform }?.model
        val connected = TranslatorService.tvConnected > 0
        val state = when {
            connected -> c.tx("tv_st_on")
            seen(c, type) -> c.tx("tv_st_off")
            else -> c.tx("tv_st_never")
        }
        return (head + (model?.let { " $it" } ?: "") + " – " + state) to connected
    }

    fun openInstall(activity: Activity, type: TvType = Prefs.tvType(activity)) {
        val cls = when (type) {
            TvType.LG -> LgInstallActivity::class.java
            TvType.SAMSUNG -> SamsungInstallActivity::class.java
            TvType.ANDROID -> return
        }
        activity.startActivity(Intent(activity, cls))
    }

    /** Popisek tlačítka k nahrání aplikace do televize daného typu. */
    fun installLabel(c: Context, type: TvType): String = c.tx(if (type == TvType.LG) "lg_open" else "ss_open")
}
