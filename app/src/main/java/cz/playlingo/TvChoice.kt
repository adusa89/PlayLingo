package cz.playlingo

import cz.playlingo.core.TvSettings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import cz.playlingo.core.Console
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

    /** Verze aplikace pro televizi, kterou nese tohle APK (z receiver-manifest.json); null = nejde zjistit. */
    fun bundledVersion(c: Context, type: TvType): String? = bundled.getOrPut(type) { readBundled(c, type) ?: "" }.ifEmpty { null }

    /** Verze v APK se za běhu nemění – soubor se čte jen jednou (Domů ji potřebuje při každém překreslení). */
    private val bundled = java.util.concurrent.ConcurrentHashMap<TvType, String>()

    private fun readBundled(c: Context, type: TvType): String? = try {
        val dir = when (type) { TvType.LG -> "lg"; TvType.SAMSUNG -> "samsung"; TvType.ANDROID -> return null }
        val m = c.assets.open("$dir/receiver-manifest.json").use { String(it.readBytes(), Charsets.UTF_8) }
        cz.playlingo.core.MiniJson.obj(m)?.get("version") as? String
    } catch (_: Exception) { null }

    /**
     * Verze z uloženého ohlášení: „model · verze“, nebo jen „verze“ (po nahrání do TV, která se od spuštění
     * aplikace ještě neohlásila – do 0.97.30 se to četlo jako „bez verze“ a Domů hlásilo „LG ? · nová …“);
     * null = aplikace v televizi před 1.1.0 verzi neposílala (jen model).
     */
    internal fun storedVersion(v: String): String? {
        val last = v.substringAfterLast(" · ").trim()
        return last.takeIf { VERSION.matches(it) }
    }
    private val VERSION = Regex("\\d+(\\.\\d+)+")

    /**
     * V televizi je starší PlayLingo než to, které nese tohle APK → (verze v televizi, nová verze), jinak null.
     * Verzi v televizi bere z posledního ohlášení (i z dřívějška, `Prefs.tvSeen`).
     */
    fun outdated(c: Context, type: TvType = Prefs.tvType(c)): Pair<String, String>? {
        val p = type.platform ?: return null
        val bundled = bundledVersion(c, type) ?: return null
        val live = TvSender.lastSeen?.announce?.takeIf { it.platform == p }
        // uloženo „model · verze“; aplikace v televizi před 1.1.0 verzi neposílala → bez verze (= zastaralá)
        val seen = live ?: Prefs.tvSeen(c, p)?.let { cz.playlingo.core.TvAnnounce(p, storedVersion(it), null, null, null) } ?: return null
        val check = cz.playlingo.core.TvCheck.of(seen, p, bundled)
        return if (check == cz.playlingo.core.TvCheck.OLDER || check == cz.playlingo.core.TvCheck.NO_VERSION)
            (seen.version ?: "?") to bundled else null
    }

    /**
     * Verze PlayLinga v televizi, když je stejná nebo novější než přibalená (= není co nahrávat);
     * null = televize se neohlásila, nebo je v ní starší verze.
     */
    fun upToDate(c: Context, type: TvType = Prefs.tvType(c)): String? {
        val p = type.platform ?: return null
        val bundled = bundledVersion(c, type) ?: return null
        val live = TvSender.lastSeen?.announce?.takeIf { it.platform == p }
        // uloženo „model · verze“; aplikace v televizi před 1.1.0 verzi neposílala → bez verze (= zastaralá)
        val seen = live ?: Prefs.tvSeen(c, p)?.let { cz.playlingo.core.TvAnnounce(p, storedVersion(it), null, null, null) } ?: return null
        return if (cz.playlingo.core.TvCheck.of(seen, p, bundled).ok) seen.version else null
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

    /** [update] = z Domů u starší aplikace v TV LG: rovnou aktualizovat, stránka s přípravou jen při chybě (od 0.97.39). */
    fun openInstall(activity: Activity, type: TvType = Prefs.tvType(activity), update: Boolean = false) {
        val cls = when (type) {
            TvType.LG -> LgInstallActivity::class.java
            TvType.SAMSUNG -> SamsungInstallActivity::class.java
            TvType.ANDROID -> return
        }
        activity.startActivity(Intent(activity, cls).putExtra(LgInstallActivity.EXTRA_UPDATE, update && type == TvType.LG))
    }

    /** Popisek tlačítka k nahrání aplikace do televize daného typu; u LG se starší verzí „Aktualizovat…“. */
    fun installLabel(c: Context, type: TvType): String = c.tx(when {
        type != TvType.LG -> "ss_open"
        outdated(c, type) != null -> "lg_open_upd"
        else -> "lg_open"
    })
}

/**
 * Spojení s aplikací v televizi LG/Samsung i tehdy, když překlad neběží (jen dokud je PlayLingo vidět):
 * Domů pak ukazuje skutečný stav „LG … · připojeno“ ještě před Spustit. Při spuštění překladu se
 * zavře a port převezme služba ([TranslatorService] volá [stop] před vlastním TvSender).
 */
/** Volby vzhledu z ovladače televize ([TvSettings]) – stejně jako v Titulkách v telefonu. */
object TvLookPrefs {
    fun message(c: android.content.Context): String = TvSettings.message(Prefs.opacity(c), Prefs.textSize(c), Prefs.matchSize(c),
        Prefs.layout(c).position, Prefs.showOriginal(c))

    fun apply(c: android.content.Context, ch: TvSettings.Change) {
        when (ch) {
            is TvSettings.Change.Opacity -> Prefs.setOpacity(c, ch.percent)
            is TvSettings.Change.TextSize -> if (ch.sp == 0) Prefs.setMatchSize(c, true)
                else { Prefs.setMatchSize(c, false); Prefs.setTextSize(c, ch.sp) }
            is TvSettings.Change.Position -> Prefs.setLayout(c, Prefs.layout(c).withPosition(ch.position))
            is TvSettings.Change.Original -> Prefs.setShowOriginal(c, ch.show)
        }
    }
}

object TvIdle {
    @Volatile private var sender: TvSender? = null

    /** Kolik televizí je připojeno k čekajícímu spojení; null = neběží. */
    val connected: Int? get() = sender?.takeIf { it.running }?.connected

    @Synchronized fun ensure(c: android.content.Context) {
        if (TranslatorService.running || !Prefs.tvSend(c)) { stop(); return }
        if (sender?.running != true) {
            val s = TvSender { }
            // „Vzhled titulků“ z televize i bez spuštěného překladu: uložit jako z telefonu a poslat zpět
            val app = c.applicationContext
            s.onChange = { ch -> android.os.Handler(android.os.Looper.getMainLooper()).post { TvLookPrefs.apply(app, ch); ensure(app) } }
            sender = if (s.start()) s else null
        }
        // stav pro televizi (překlad neběží, konzole, jazyk, hra) a volby vzhledu – posílá se jen změna
        sender?.sendStatus(TvStatus.message(c))
        sender?.sendSettings(TvLookPrefs.message(c))
    }

    @Synchronized fun stop() { sender?.stop(); sender = null }
}

/**
 * Stav pro aplikaci v televizi LG/Samsung (od 0.97.14, televize od 1.5.0): stejné body jako Domů –
 * konzole a obraz, překlad (běží / neběží), jazyk a DeepL, hra. Krátké stavové řádky, žádné věty.
 */
object TvStatus {
    fun message(c: android.content.Context): String = TvSender.statusMessage(rows(c))

    fun rows(c: android.content.Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val console = Prefs.console(c)
        val name = when (console) {
            Console.PLAYSTATION -> Prefs.ps5Host(c)?.name
            Console.XBOX -> if (XboxAccount.ready(c)) XboxAccount.consoleName(c) else null
        } ?: if (console == Console.XBOX) "Xbox" else "PS5"
        if (TranslatorService.running && !TranslatorService.demo) {
            val (b, st) = if (console == Console.PLAYSTATION && Ps5Link.asleep) "pozor" to c.tx("h_con_sleep") else when (TranslatorService.linkStatus().stage) {
                cz.playlingo.core.LinkStatus.Stage.IMAGE -> "ok" to c.tx("tv_st_on")
                cz.playlingo.core.LinkStatus.Stage.STALLED, cz.playlingo.core.LinkStatus.Stage.NO_IMAGE -> "pozor" to c.tx("h_con_noimg")
                cz.playlingo.core.LinkStatus.Stage.FAILED -> "pozor" to c.tx("h_con_fail")
                cz.playlingo.core.LinkStatus.Stage.PAUSED -> "info" to c.tx("st_paused").lowercase()
                else -> "info" to c.tx("h_con_wait")
            }
            out += b to "$name · $st"
        } else {
            val ready = Prefs.consoleReady(c)
            out += (if (ready) "info" else "pozor") to "$name · " + c.tx(if (ready) "h_con_ready" else "sum_nopair")
            out += "pozor" to c.tx("tvs_stopped")
        }
        val lang = Prefs.lang(c)
        out += if (!OfflineTranslator.isReady(lang)) "pozor" to c.tx("w_model").replace("%s", lang.label)
            else "ok" to lang.label + " · " + c.tx(if (DeeplTranslator.active(c)) "dl_mode_on" else "h_offline")
        val game = cz.playlingo.core.GameProfiles.of(Prefs.gameId(c))
        val guess = Prefs.gameGuess(c)?.let { cz.playlingo.core.GameProfiles.of(it) }
        if (guess != null) out += "pozor" to (if (game.id != "all") game.title + " · " else "") + guess.title + "?"
        else if (game.id != "all") out += "ok" to game.title
        return out
    }
}
