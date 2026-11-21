package cz.playlingo

import android.content.Context
import android.util.Log
import cz.playlingo.core.DiagLog
import cz.playlingo.core.GameListJson
import cz.playlingo.core.GameProfiles
import cz.playlingo.core.HelpLinks
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seznam her (profily: jména, postavy s rodem, pojmy, nápisy, menu…) jako třetí aktualizace vedle aplikace
 * a aplikace v TV. Leží na stránkách s pomocí (`pomoc/hry/profily.json` + `verze.txt`, viz [GameListJson]).
 * Vestavěný seznam v APK zůstává zálohou; stažený platí, jen když je novější a jde přečíst.
 * Volba hráče (`Prefs.gameId`) se nikdy nemění – hra, která v seznamu chybí, jede s univerzálním profilem.
 */
object GameListUpdate {
    private const val DIR = "hry/"
    private const val FILE = "hry-profily.json"
    /** Automatická kontrola nejvýš jednou za 6 h (jen se zapnutým `Prefs.gamesAuto`). */
    private const val AUTO_EVERY_MS = 6 * 3600_000L

    enum class State { IDLE, CHECKING, UPDATED, CURRENT, FAILED }

    /** Stav pro řádek v Titulky → Hra (čte se z paměti, nic se nepočítá). */
    @Volatile var state = State.IDLE
        private set

    private fun log(msg: String) { Log.i("TP_TEST", "GAMES|$msg"); DiagLog.add("GAMES|$msg"); EventFile.add("GAMES|$msg") }

    private fun file(c: Context) = File(c.filesDir, FILE)

    /** Při spuštění aplikace: uložený stažený seznam, když je novější než vestavěný a jde přečíst. */
    fun load(c: Context) {
        try {
            val f = file(c)
            if (!f.exists() || f.length() > GameListJson.MAX_SIZE * 3L) return
            val r = GameListJson.parse(f.readText(Charsets.UTF_8), BuildInfo.VERSION_CODE)
            val cat = r.catalog
            if (cat != null && cat.version > GameProfiles.BUILTIN.version) GameProfiles.use(cat)
            else log("ulozeny nepouzit: ${r.problem ?: "verze ${r.version}"}")
        } catch (t: Throwable) { log("load ${t.javaClass.simpleName}") }
    }

    /** Automatická kontrola (po otevření aplikace): jen se zapnutou volbou, se sítí a nejvýš jednou za 6 h. */
    fun maybeAuto(c: Context, done: (Boolean) -> Unit) {
        if (!Prefs.gamesAuto(c) || state == State.CHECKING) return
        if (System.currentTimeMillis() - Prefs.gamesChecked(c) in 0 until AUTO_EVERY_MS) return
        if (!online(c)) return
        check(c, done)
    }

    /**
     * Zjistí verzi na stránkách a novější seznam stáhne, ověří, uloží a začne používat. Běží na pozadí;
     * [done] dostane true, když se seznam změnil (volá se mimo hlavní vlákno).
     */
    fun check(c: Context, done: (Boolean) -> Unit) {
        if (state == State.CHECKING) return
        state = State.CHECKING
        val app = c.applicationContext
        Thread({
            var changed = false
            try {
                Prefs.setGamesChecked(app, System.currentTimeMillis())
                val remote = get(HelpLinks.BASE + DIR + "verze.txt", 100)?.let { GameListJson.versionOf(it) }
                val current = GameProfiles.catalog.version
                if (remote == null) { state = State.FAILED; log("verze nezjistena") }
                else if (remote <= current) { state = State.CURRENT; log("aktualni $current") }
                else {
                    val text = get(HelpLinks.BASE + DIR + "profily.json", GameListJson.MAX_SIZE)
                    val r = text?.let { GameListJson.parse(it, BuildInfo.VERSION_CODE) }
                    val cat = r?.catalog
                    if (text == null || cat == null) { state = State.FAILED; log("seznam neplatny: ${r?.problem ?: "stazeni"}") }
                    else if (cat.version <= current) { state = State.CURRENT; log("aktualni $current") }
                    else {
                        save(app, text)
                        GameProfiles.use(cat)
                        state = State.UPDATED; changed = true
                        log("novy ${cat.version} (${cat.games.size} her)")
                    }
                }
            } catch (t: Throwable) { state = State.FAILED; log("chyba ${t.javaClass.simpleName}") }
            try { done(changed) } catch (_: Throwable) {}
        }, "seznam-her").start()
    }

    /** Uloží seznam přes dočasný soubor – rozepsaný soubor nikdy nezůstane jako platný. */
    private fun save(c: Context, text: String) {
        val f = file(c)
        val tmp = File(c.filesDir, "$FILE.part")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw java.io.IOException("rename") }
    }

    /** Text z adresy (nejvýš [max] znaků), null = nejde. Volat mimo hlavní vlákno. */
    private fun get(url: String, max: Int): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        return try {
            if (c.responseCode != 200) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val b = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    b.append(buf, 0, n)
                    if (b.length > max) return null
                }
                b.toString()
            }
        } finally { c.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun online(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetworkInfo?.isConnected == true
    } catch (_: Throwable) { true }
}
