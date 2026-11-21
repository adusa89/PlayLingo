package cz.playlingo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import cz.playlingo.core.Deepl
import cz.playlingo.core.LoginServer
import cz.playlingo.core.PhoneLogin
import cz.playlingo.core.RemoteSetup

/**
 * Nastavení Android TV z telefonu – strana televize (návrh `navrhy/nastaveni-z-telefonu.md`).
 *
 * Televize vystaví v domácí síti stránku se stálým klíčem ([Prefs.remoteKey], ve čtverečku v ⚙ → Obecné
 * → „Nastavit z telefonu“). PlayLingo v telefonu přes ni:
 *  - [RemoteSetup.OP_SNAPSHOT] – stáhne celé nastavení TV,
 *  - [RemoteSetup.OP_SET] – pošle změněné volby (uloží se a služba si je hned načte),
 *  - [RemoteSetup.OP_DEEPL] – pošle klíč DeepL (ověří se a uloží zašifrovaný jako dosud).
 * Prohlížeč (GET) dostane stránku s odkazem do aplikace. Běží jen na Android TV / boxu, ne v telefonu.
 */
object RemoteHost {
    private const val PORT = 8766
    private var server: LoginServer? = null
    private val main = Handler(Looper.getMainLooper())

    /** Obrazovka aplikace v TV se po změně z telefonu překreslí (nastavuje MainActivity). */
    @Volatile var onChanged: (() -> Unit)? = null

    /** Smí toto zařízení přijímat nastavení z telefonu? Jen televize nebo box, ne telefon/tablet. */
    fun eligible(c: Context): Boolean = !NetInfo.isHandheld(c) && !Prefs.forceTv(c)

    @Synchronized fun start(c: Context): Boolean {
        val app = c.applicationContext
        if (!eligible(app)) return false
        server?.takeIf { it.running }?.let { return true }
        val s = LoginServer.forPage({ Prefs.uiLang(app) }, object : LoginServer.Custom {
            override fun get(path: String) = RemoteSetup.page(Prefs.uiLang(app), link(app) ?: "")
            override fun gone() = RemoteSetup.page(Prefs.uiLang(app), "")
            override fun post(path: String, form: Map<String, String>): String = handle(app, form)
        }, log = { Log.i("TP_TEST", "REMOTE|$it") }, fixedKey = Prefs.remoteKey(app))
        if (!s.start(PORT)) return false
        server = s
        return true
    }

    @Synchronized fun stop() { server?.stop(); server = null }

    /** Adresa stránky pro čtvereček; null = televize není v domácí síti nebo server neběží. */
    fun pageUrl(c: Context): String? = target(c)?.let { PhoneLogin.pageUrl(it) }

    /** Odkaz do aplikace v telefonu (na stránce z TV). */
    fun link(c: Context): String? = target(c)?.let { RemoteSetup.appLink(it, tvName()) }

    private fun target(c: Context): PhoneLogin.Target? {
        val s = server?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return PhoneLogin.Target(ip, s.port, s.key)
    }

    fun tvName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }
        .joinToString(" ").replaceFirstChar { it.uppercase() }.take(60)

    private fun handle(c: Context, form: Map<String, String>): String {
        val v = form["v"]?.toIntOrNull() ?: 0
        if (v > RemoteSetup.VERSION) return RemoteSetup.ERR_VERSION
        return when (form["op"]) {
            RemoteSetup.OP_SNAPSHOT -> RemoteSetup.snapshot(tvName(), Prefs.remoteValues(c), DeeplKey.has(c), DeeplKey.enabled(c))
            RemoteSetup.OP_SET -> {
                val values = RemoteSetup.decode(form["data"] ?: "") ?: return RemoteSetup.ERR_BAD
                Prefs.applyRemote(c, values)
                Log.i("TP_TEST", "REMOTE|set " + values.keys.joinToString(","))
                changed(c)
                "OK"
            }
            RemoteSetup.OP_DEEPL -> {
                // jen zapnout / vypnout DeepL (Titulky → Kvalita překladu v telefonu); zapnout jde jen s klíčem
                if (form["key"] == null && form["on"] != null) {
                    DeeplKey.setEnabled(c, form["on"] == "1" && DeeplKey.has(c))
                    changed(c)
                    return "OK"
                }
                val key = Deepl.cleanKey(form["key"])
                if (Deepl.keyKind(key) == null) return RemoteSetup.ERR_BAD
                val r = DeeplTranslator.check(key)
                if (r.problem == Deepl.Problem.BAD_KEY) return "REJECTED"
                if (DeeplKey.get(c) != key) {
                    val first = !DeeplKey.has(c)
                    DeeplKey.save(c, key)
                    if (first) DeeplKey.setEnabled(c, true)
                }
                DeeplKey.saveCheck(c, r)
                Log.i("TP_TEST", "REMOTE|deepl")
                changed(c)
                "OK"
            }
            else -> RemoteSetup.ERR_BAD
        }
    }

    /** Služba si nastavení načte znovu a obrazovka v TV se překreslí. */
    private fun changed(c: Context) {
        // jen do běžící služby – jinak by ji změna nastavení sama spustila
        if (TranslatorService.running) try {
            c.startService(Intent(c, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        } catch (_: Exception) {}
        main.post { onChanged?.invoke() }
    }
}
