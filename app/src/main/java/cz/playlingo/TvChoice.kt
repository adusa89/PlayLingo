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
 *  - [show] – nabídka typů; vybraný typ uloží ([set]) a zavolá `onPicked`,
 *  - [set] – uloží typ a pošle ho běžícímu překladu (i z voleb v průvodci),
 *  - [short] – televize do souhrnného řádku na Domů („LG OLED55B7 · připojeno“),
 *  - [needsInstall] – má se nabízet nahrání aplikace do televize (aplikace se z ní ještě nikdy neohlásila)?
 *  - [openInstall] – průvodce nahráním pro LG / Samsung.
 */
object TvChoice {
    fun label(c: Context, type: TvType): String = c.tx("tv_type_" + type.key)

    /** Uloží typ televize a hned ho pošle běžícímu překladu. */
    fun set(activity: Activity, t: TvType) {
        Prefs.setTvType(activity, t)
        if (TranslatorService.running) {
            activity.startService(Intent(activity, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        }
    }

    /** Nabídka typů televize. Vybraný typ se hned uloží a projeví v běžícím překladu. */
    fun show(activity: Activity, onPicked: (TvType) -> Unit = {}) {
        val types = TvType.values().toList()
        val current = types.indexOf(Prefs.tvType(activity))
        fun pick(i: Int) {
            set(activity, types[i])
            onPicked(types[i])
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

    /** Verze aplikace pro televizi přibalená v tomto APK (z receiver-manifest.json), null = nejde přečíst. */
    fun bundledVersion(c: Context, type: TvType): String? {
        val dir = when (type) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> return null }
        return try {
            val m = c.assets.open("$dir/receiver-manifest.json").use { String(it.readBytes(), Charsets.UTF_8) }
            Regex(""""version"\s*:\s*"([0-9]+\.[0-9]+\.[0-9]+)"""").find(m)?.groupValues?.get(1)
        } catch (_: Exception) { null }
    }

    /**
     * Aplikace v televizi se ohlásila starší verzí, než je přibalená (nebo bez verze) – nové věci
     * (např. vzhled pruhu od 1.3.0) v ní nefungují, má se nahrát znovu. null = nevíme / je aktuální.
     */
    fun outdated(c: Context, type: TvType = Prefs.tvType(c)): String? {
        if (!type.sends) return null
        val a = TvSender.lastSeen?.announce?.takeIf { it.platform == type.platform } ?: return null
        val have = a.version ?: return "?"
        val want = bundledVersion(c, type) ?: return null
        return try { if (cz.playlingo.core.LgInstall.compare(have, want) < 0) have else null } catch (_: Exception) { null }
    }

    /**
     * Televize do souhrnného řádku na Domů: „Android TV“, nebo u LG/Samsungu značka, model (když se
     * televize už ohlásila) a stav spojení („LG OLED55B7 · připojeno“). Druhá hodnota = připojeno.
     */
    fun short(c: Context): Pair<String, Boolean> {
        val type = Prefs.tvType(c)
        val name = when (type) { TvType.ANDROID -> "Android TV"; TvType.LG -> "LG"; TvType.SAMSUNG -> "Samsung" }
        if (!type.sends) return name to false
        val model = TvSender.lastSeen?.announce?.takeIf { it.platform == type.platform }?.model
        val connected = TranslatorService.tvConnected > 0
        val state = when {
            connected -> c.tx("tv_st_on")
            seen(c, type) -> c.tx("tv_st_off")
            else -> c.tx("tv_st_never")
        }
        return (name + (model?.let { " $it" } ?: "") + " · " + state) to connected
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
