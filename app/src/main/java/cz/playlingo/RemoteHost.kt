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
import cz.playlingo.core.Ps5
import cz.playlingo.core.RemoteSetup
import cz.playlingo.core.SetupPage
import cz.playlingo.core.UpdateCheck

/**
 * Nastavení Android TV z telefonu – strana televize (návrh `navrhy/nastaveni-z-telefonu.md`).
 *
 * Televize vystaví v domácí síti stránku se stálým klíčem ([Prefs.remoteKey], ve čtverečku v ⚙ → Obecné
 * → „Nastavit z telefonu“). PlayLingo v telefonu přes ni:
 *  - [RemoteSetup.OP_SNAPSHOT] – stáhne celé nastavení TV,
 *  - [RemoteSetup.OP_SET] – pošle změněné volby (uloží se a služba si je hned načte),
 *  - [RemoteSetup.OP_DEEPL] – pošle klíč DeepL (ověří se a uloží zašifrovaný jako dosud).
 * Prohlížeč (GET) dostane od 0.97.63 stránku „Nastavení TV“ ([SetupPage], iPhone i Android): 🔵 účet pro PlayLingo
 * ([SetupPage.OP_ACCOUNT] – adresa „redirect“; z PlayLinga v telefonu pole `account`), osmimístné číslo z PS5
 * ([SetupPage.OP_CODE] – TV spáruje sama, [pair]), DeepL a stav ([SetupPage.OP_INFO]).
 * Běží jen na Android TV / boxu, ne v telefonu.
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
            override fun get(path: String) = page(app)
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

    /** Stránka pro telefon (texty v jazyce aplikace v TV). */
    private fun page(c: Context): String = SetupPage.page(Prefs.uiLang(c), tvName(), target(c),
        UpdateCheck.url(if (Prefs.testChannel(c)) UpdateCheck.Channel.TEST else UpdateCheck.Channel.STABLE, "PlayLingo.apk"),
        PsnAuth.LOGIN_URL, Deepl.SIGNUP_URL, Deepl.KEYS_URL, cz.playlingo.core.HelpLinks.IPHONE_SHORTCUT)

    /** Spárování spuštěné z telefonu (číslo z PS5) – stav pro stránku i pro obrazovku v TV. */
    @Volatile var pair = SetupPage.Pair.IDLE
        private set
    /** Proč spárování z telefonu nevyšlo (pro TV, ne pro stránku). */
    @Volatile var pairError: String? = null
        private set

    /** Stav pro stránku v telefonu. */
    fun status(c: Context): SetupPage.Status {
        val id = Prefs.psnId(c)
        val host = Prefs.ps5Host(c)
        return SetupPage.Status(
            account = if (Ps5.parseAccountId(id) != null) Prefs.psnName(c) else null,
            pair = if (host != null && pair != SetupPage.Pair.BUSY) SetupPage.Pair.OK else pair,
            console = host?.name,
            deepl = DeeplKey.has(c),
        )
    }

    /**
     * Osmimístné číslo z telefonu → TV spáruje sama (adresu PS5 zná z dřívějška, jinak ji najde v síti). Spárovat
     * jde jen s ověřeným 🔵 účtem; číslo musí na PS5 vyvolat týž uživatel (jinak CE-110032-7 – stránka to řekne).
     */
    private fun startPair(c: Context, pin: Int): String {
        val account = Ps5.parseAccountId(Prefs.psnId(c)) ?: return "NOACC"
        synchronized(this) {
            if (pair == SetupPage.Pair.BUSY) return "OK"
            pair = SetupPage.Pair.BUSY; pairError = null
        }
        changed(c)
        Thread({
            fun fail(why: String) { pairError = why; pair = SetupPage.Pair.FAIL; changed(c) }
            try {
                var ip = Prefs.ps5Ip(c)
                if (!Ps5.validHost(ip)) ip = Ps5Pairing.discover().firstOrNull()?.ip ?: ""
                if (!Ps5.validHost(ip)) { EventFile.add("PAIR|z telefonu: PS5 v síti nenalezena"); fail("ps5_notfound"); return@Thread }
                Prefs.setPs5Ip(c, ip)
                val name = Prefs.psnName(c).ifEmpty { "?" }
                Ps5Pairing.pair(ip, account, pin) { h ->
                    EventFile.add("PAIR|z telefonu: " + (if (h != null) "spárováno ${h.name} ${h.ip}" else "nepovedlo se ($ip)") +
                        " · účet $name" + (Ps5Pairing.lastError?.takeIf { h == null }?.let { " · $it" } ?: ""))
                    if (h != null) { Prefs.setPs5Host(c, h); pair = SetupPage.Pair.OK; changed(c) }
                    else fail(Ps5Pairing.lastError ?: "")
                }
            } catch (e: Throwable) { fail(e.javaClass.simpleName) }
        }, "tp-remote-pair").start()
        return "OK"
    }

    private fun handle(c: Context, form: Map<String, String>): String {
        // PlayLingo v telefonu (pomocník přihlášení) posílá ověřený účet stejně jako stránce přihlášení
        if (form.containsKey("account")) {
            val acc = PhoneLogin.cleanAccount(form["account"], form["name"]) ?: return RemoteSetup.ERR_BAD
            useAccount(c, acc)
            return "OK"
        }
        val v = form["v"]?.toIntOrNull() ?: 0
        if (v > RemoteSetup.VERSION) return RemoteSetup.ERR_VERSION
        return when (form["op"]) {
            SetupPage.OP_INFO -> SetupPage.status(status(c))
            SetupPage.OP_ACCOUNT -> {
                val code = Ps5.authCodeFromTyped(form["url"] ?: "") ?: return RemoteSetup.ERR_BAD
                val acc = try { PsnAuth.accountForCode(code) } catch (_: Throwable) { null } ?: return RemoteSetup.ERR_BAD
                useAccount(c, acc)
                "OK"
            }
            SetupPage.OP_CODE -> {
                val pin = SetupPage.pin(form["pin"]) ?: return RemoteSetup.ERR_BAD
                startPair(c, pin)
            }
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

    /** Ověřený 🔵 účet z telefonu: uložit s jménem; jiný účet = staré spárování z telefonu neplatí. */
    private fun useAccount(c: Context, a: PhoneLogin.Account) {
        Prefs.setPsnAccount(c, a.id, PhoneLogin.cleanName(a.name))
        if (pair == SetupPage.Pair.FAIL) pair = SetupPage.Pair.IDLE
        Log.i("TP_TEST", "REMOTE|account")
        EventFile.add("LOGIN|účet z telefonu (nastavení TV)")
        changed(c)
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
