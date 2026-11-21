package cz.playlingo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import cz.playlingo.core.DiagLog
import cz.playlingo.core.LoginClient
import cz.playlingo.core.PhoneLogin
import cz.playlingo.core.RemoteSetup
import cz.playlingo.core.RemoteSync
import java.util.concurrent.Executors

/**
 * Nastavení Android TV z telefonu – strana telefonu, režim „Nastavuji TV“ (3. díl návrhu
 * `navrhy/nastaveni-z-telefonu.md`; televize = [RemoteHost]).
 *
 * Odkaz `playlingo://tv?…` ze stránky z TV otevře pomocníka ([PhoneHelperActivity]), ten zavolá [connect]:
 * telefon stáhne celé nastavení TV (TV je hlavní) do vlastního souboru [TV_FILE] a dokud je režim zapnutý,
 * čte i zapisuje [Prefs] jen tam ([on]) – vlastní nastavení telefonu (čtečka pro LG/Samsung) zůstává
 * nedotčené v `test_preklad`. Každá změna přenášené volby ([RemoteSetup.transfers]) odejde po
 * [RemoteSync.DEBOUNCE_MS] do TV; když TV není k zastižení, zkusí se to s další změnou a po [RemoteSync.RETRY_MS].
 * Klíč DeepL a zapnutí DeepL jdou zvlášť ([deeplKey], [deeplOn]); klíč se v telefonu ukládá do [DEEPL_FILE].
 * Režim trvá i po zavření aplikace, dokud hráč nestiskne Odpojit ([disconnect]).
 */
object RemoteClient {
    /** Nastavení TV v telefonu (místo `test_preklad`). */
    const val TV_FILE = "dalkove_tv"
    /** DeepL v režimu TV (místo `deepl`). */
    const val DEEPL_FILE = "deepl_dalkove"
    /** Kam se telefon připojuje: adresa, port, klíč, jméno TV, zapnuto, co TV naposledy potvrdila. */
    private const val FILE = "dalkove"

    enum class State { OFF, OK, SENDING, UNREACHABLE, VERSION, BAD, DEEPL_REJECTED }

    /** Režim „Nastavuji TV“ zapnutý. Čte se při každém [Prefs]…, proto proměnná, ne soubor. */
    @Volatile var active = false
        private set
    @Volatile private var loaded = false
    @Volatile var state = State.OFF
        private set
    @Volatile var tvName = ""
        private set
    /** TV má uložený klíč DeepL, který telefon nezná (klíč z TV se do telefonu nikdy neposílá). */
    @Volatile var tvHasDeeplKey = false
        private set
    /** Obrazovka se má překreslit (stav spojení se změnil); volá se na hlavním vlákně. */
    @Volatile var onState: (() -> Unit)? = null

    private var app: Context? = null
    private var target: PhoneLogin.Target? = null
    private var sync: RemoteSync? = null
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tp-dalkove").apply { isDaemon = true } }
    /** Klíč DeepL a zapnutí čekající na odeslání (jen v paměti – klíč se nikam neukládá nešifrovaný). */
    @Volatile private var pendingKey: String? = null
    @Volatile private var pendingOn: Boolean? = null

    private fun log(m: String) { Log.i("TP_TEST", "REMOTE|$m"); DiagLog.add("REMOTE|$m") }

    /** Je zapnutý režim „Nastavuji TV“? Poprvé si stav přečte ze souboru (jednou za běh aplikace). */
    fun on(c: Context): Boolean {
        if (!loaded) load(c)
        return active
    }

    @Synchronized fun load(c: Context) {
        if (loaded) return
        val a = c.applicationContext
        app = a
        val sp = a.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (sp.getBoolean("aktivni", false)) {
            val t = PhoneLogin.Target(sp.getString("h", "") ?: "", sp.getInt("p", 0), sp.getString("k", "") ?: "")
            if (PhoneLogin.isHomeAddress(t.host) && t.port in 1..65535 && PhoneLogin.validKey(t.key)) {
                target = t
                tvName = sp.getString("jmeno", "") ?: ""
                tvHasDeeplKey = sp.getBoolean("tv_deepl", false)
                sync = RemoteSync(RemoteSetup.decode(sp.getString("potvrzeno", "") ?: "") ?: emptyMap<String, Any?>())
                active = true
                state = State.OK
                listen(a)
                // co se před zavřením aplikace nestihlo poslat
                schedule(0)
            }
        }
        loaded = true
    }

    /** Krátký stav k zobrazení („připojeno“, „TV nedostupná“…) a jestli je to potíž (oranžově). */
    fun stateText(c: Context): Pair<String, Boolean> = when (state) {
        State.SENDING -> c.tx("rs_st_sending") to false
        State.UNREACHABLE -> c.tx("rs_st_off") to true
        State.VERSION -> c.tx("rs_st_version") to true
        State.BAD -> c.tx("rs_st_bad") to true
        State.DEEPL_REJECTED -> c.tx("rs_st_deepl") to true
        else -> c.tx("rs_st_ok") to false
    }

    /** Výsledek spojení pro pomocníka. */
    enum class Connect { OK, UNREACHABLE, VERSION, BAD }

    /**
     * Spojí se s TV z odkazu: stáhne nastavení TV a zapne režim. Volat mimo hlavní vlákno.
     * Při neúspěchu se nic nemění (telefon zůstane ve svém nastavení).
     */
    fun connect(c: Context, link: RemoteSetup.Link): Connect {
        val a = c.applicationContext
        val t = link.target
        val (status, body) = try {
            LoginClient.request(t.host, t.port, "POST", PhoneLogin.path(t.key),
                PhoneLogin.form(mapOf("v" to RemoteSetup.VERSION.toString(), "op" to RemoteSetup.OP_SNAPSHOT)))
        } catch (_: Throwable) { log("connect unreachable"); return Connect.UNREACHABLE }
        if (status == 200 && body.trim() == RemoteSetup.ERR_VERSION) return Connect.VERSION
        val snap = (if (status == 200) RemoteSetup.parseSnapshot(body) else null) ?: run { log("connect bad $status"); return Connect.BAD }
        if (snap.version > RemoteSetup.VERSION) return Connect.VERSION
        synchronized(this) {
            // předchozí spojení (jiná TV) ukončit, ať posluchač nepíše do nové
            stopListening(a)
            // TV je hlavní: nastavení pro TV v telefonu = přesně to, co má TV
            val tv = a.getSharedPreferences(TV_FILE, Context.MODE_PRIVATE).edit().clear()
            put(tv, RemoteSync.localValues(snap))
            tv.commit()
            a.getSharedPreferences(DEEPL_FILE, Context.MODE_PRIVATE).edit().clear().putBoolean("on", snap.deeplOn).commit()
            val name = RemoteSync.tvName(link.tvName, snap.tvName)
            val s = RemoteSync(snap.values)
            a.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean("aktivni", true).putString("h", t.host).putInt("p", t.port).putString("k", t.key)
                .putString("jmeno", name).putBoolean("tv_deepl", snap.hasDeeplKey)
                .putString("potvrzeno", RemoteSetup.encode(s.confirmed())).commit()
            app = a; target = t; sync = s; tvName = name; tvHasDeeplKey = snap.hasDeeplKey
            pendingKey = null; pendingOn = null
            active = true; loaded = true
            listen(a)
        }
        setState(State.OK)
        log("connect ok " + snap.values.size)
        // telefon v tomhle režimu nepřekládá: překlad pro LG/Samsung, kdyby běžel, zastavit
        if (TranslatorService.running) try { a.stopService(Intent(a, TranslatorService::class.java)) } catch (_: Exception) {}
        return Connect.OK
    }

    /** Konec režimu: telefon se vrátí ke svému nastavení. Neodeslané změny se ještě zkusí poslat. */
    fun disconnect(c: Context) {
        val a = c.applicationContext
        // poslední pokus o neodeslané změny (čte soubor TV přímo, režim už zapnutý být nemusí)
        worker.execute { flush() }
        synchronized(this) {
            stopListening(a)
            a.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("aktivni", false).commit()
            active = false; loaded = true
        }
        setState(State.OFF)
        log("disconnect")
    }

    /** Klíč DeepL uložený v telefonu v režimu TV → poslat do TV (ověří si ho sama). */
    fun deeplKey(key: String) { if (!active) return; pendingKey = key; pendingOn = null; tvHasDeeplKey = true; schedule(0) }

    /** DeepL zapnutý / vypnutý v telefonu v režimu TV → do TV. */
    fun deeplOn(v: Boolean) { if (!active) return; pendingOn = v; schedule(RemoteSync.DEBOUNCE_MS) }

    // ---- průběžné posílání ----

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // null = soubor smazán celý (Android 11+); jinak jen přenášené volby
        if (key == null || RemoteSetup.transfers(key)) schedule(RemoteSync.DEBOUNCE_MS)
    }

    /** Posluchač drží tenhle objekt (SharedPreferences si ho pamatuje jen slabě). */
    private fun listen(a: Context) = a.getSharedPreferences(TV_FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)

    private fun stopListening(a: Context) {
        a.getSharedPreferences(TV_FILE, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        main.removeCallbacks(flushLater)
    }

    private val flushLater = Runnable { worker.execute { flush() } }

    private fun schedule(delayMs: Long) {
        main.removeCallbacks(flushLater)
        main.postDelayed(flushLater, delayMs)
    }

    /** Pošle do TV, co jí chybí (klíč DeepL, zapnutí DeepL, změněné volby). Jen na vlákně [worker] (jedno, po řadě). */
    private fun flush() {
        val a = app ?: return
        val t = target ?: return
        val s = sync ?: return
        fun post(form: Map<String, String>): RemoteSync.Reply = try {
            val (status, body) = LoginClient.request(t.host, t.port, "POST", PhoneLogin.path(t.key),
                PhoneLogin.form(form + ("v" to RemoteSetup.VERSION.toString())))
            RemoteSync.reply(status, body)
        } catch (_: Throwable) { RemoteSync.reply(0, null) }
        fun failed(r: RemoteSync.Reply): Boolean {
            if (r == RemoteSync.Reply.OK) return false
            log("send $r")
            setState(when (r) {
                RemoteSync.Reply.UNREACHABLE -> State.UNREACHABLE
                RemoteSync.Reply.VERSION -> State.VERSION
                else -> State.BAD
            })
            if (r == RemoteSync.Reply.UNREACHABLE && active) schedule(RemoteSync.RETRY_MS)
            return true
        }
        var sentSomething = false
        pendingKey?.let { key ->
            setState(State.SENDING)
            val r = post(mapOf("op" to RemoteSetup.OP_DEEPL, "key" to key))
            if (r == RemoteSync.Reply.REJECTED) { pendingKey = null; log("deepl rejected"); setState(State.DEEPL_REJECTED); return }
            if (failed(r)) return
            if (pendingKey == key) pendingKey = null
            sentSomething = true
        }
        pendingOn?.let { on ->
            setState(State.SENDING)
            if (failed(post(mapOf("op" to RemoteSetup.OP_DEEPL, "on" to if (on) "1" else "0")))) return
            if (pendingOn == on) pendingOn = null
            sentSomething = true
        }
        val changes = s.pending(a.getSharedPreferences(TV_FILE, Context.MODE_PRIVATE).all)
        if (changes.isNotEmpty()) {
            setState(State.SENDING)
            if (failed(post(mapOf("op" to RemoteSetup.OP_SET, "data" to RemoteSetup.encode(changes))))) return
            s.confirm(changes)
            a.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("potvrzeno", RemoteSetup.encode(s.confirmed())).apply()
            log("sent " + changes.keys.joinToString(","))
            sentSomething = true
        }
        // odmítnutý klíč DeepL zůstává vidět, dokud se znovu nepošle jiný
        if (sentSomething || state == State.UNREACHABLE || state == State.SENDING || state == State.BAD) setState(State.OK)
    }

    private fun setState(s: State) {
        // po Odpojit už poslední pokus o odeslání stav nemění
        if (state == s || (!active && s != State.OFF)) return
        state = s
        main.post { onState?.invoke() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun put(e: SharedPreferences.Editor, values: Map<String, Any?>) {
        for ((k, v) in values) when (v) {
            null -> e.remove(k)
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> e.putStringSet(k, v as Set<String>)
        }
    }
}
