package cz.playlingo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.playlingo.core.DiagLog
import cz.playlingo.core.Lang
import cz.playlingo.core.LoginClient
import cz.playlingo.core.PhoneLogin
import cz.playlingo.core.Ps5
import cz.playlingo.core.UpdateCheck
import cz.playlingo.core.TvType
import java.util.Locale

/**
 * PlayLingo v telefonu: pomocník pro přihlášení účtem PlayStation (v telefonu se nepřekládá).
 *
 * Otevře se odkazem ze stránky, kterou televize vystavuje (čtvereček na obrazovce) – tím se dozví,
 * kde televize čeká. Uživatel se přihlásí na stránce Sony přímo tady, aplikace si z adresy, na
 * kterou Sony po přihlášení přesměruje, vezme jednorázový kód, zjistí číslo účtu a pošle ho
 * televizi po domácí síti. Číslo účtu si zapamatuje: příště (nová televize, nové párování) se
 * pošle jedním klepnutím bez přihlašování. Heslo se nikam neukládá.
 *
 * Záloha, kdyby Sony přihlášení uvnitř aplikace nepustila: přihlásit se v běžném prohlížeči
 * a prázdnou stránku „redirect“ nasdílet do PlayLinga.
 */
class PhoneHelperActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var root: View
    private lateinit var content: LinearLayout
    /** Kde televize čeká na účet (z odkazu na stránce televize); null = zatím nevíme. */
    private var target: PhoneLogin.Target? = null
    private var msg: String? = null
    private var msgColor = Theme.MUTED
    private var busy = false
    /** Účet už je v televizi – není co dalšího posílat, dokud nepřijde nový odkaz. */
    private var sent = false
    /** Otevřená přihlašovací stránka Sony (null = je vidět hlavní obrazovka pomocníka). */
    private var web: WebView? = null
    /** Novější verze aplikace, která je ke stažení (null = žádná, nebo se to ještě nezjistilo). */
    private var update: UpdateCheck.Offer? = null

    private fun t(key: String) = tx(key)
    private fun log(m: String) { Log.i("TP_TEST", "HELPER|$m"); DiagLog.add("HELPER|$m") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        // jazyk: poprvé podle telefonu (později ho může přepsat jazyk televize z odkazu, nebo uživatel)
        if (!Prefs.uiLangChosen(this)) Prefs.setUiLang(this, Lang.ui(Locale.getDefault().language) ?: Lang.EN)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply { setPadding(ui.dp(20), ui.dp(28), ui.dp(20), ui.dp(36)) }
        scroll.addView(content)
        root = scroll
        setContentView(root)
        log("start")
        Updater.cleanUp(this)
        render()
        if (savedInstanceState == null) take(intent)
        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        // po návratu z dotazu systému na instalaci ukázat, jak aktualizace dopadla
        if (web == null) render()
        // vývojářský režim televize LG: nejvýš jednou za hodinu se zeptat, kolik zbývá
        LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && web == null) render() } }
    }

    /** Na pozadí zjistí, jestli není ke stažení novější verze (se zapnutým zkušebním kanálem i v něm). */
    private fun checkForUpdate() {
        val test = Prefs.testChannel(this)
        Thread({
            val offer = Updater.offer(test)
            runOnUiThread { if (!isFinishing && offer != update && !Updater.busy) { update = offer; if (web == null) render() } }
        }, "tp-helper-update").start()
    }

    /** Stáhne nabídnutou verzi a předá ji systému k instalaci – stejně jako v televizi. */
    private fun startUpdate(o: UpdateCheck.Offer) {
        Updater.start(this, UpdateCheck.url(o.channel, "PlayLingo.apk"), false) {
            runOnUiThread { if (!isFinishing && web == null) render() }
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    override fun onDestroy() {
        web?.destroy(); web = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        @Suppress("DEPRECATION")
        when {
            w == null -> super.onBackPressed()
            w.canGoBack() -> w.goBack()
            else -> { closeLogin(); render() }
        }
    }

    /** Čím byla aplikace otevřena: odkazem ze stránky televize, nebo sdílením z prohlížeče. */
    private fun take(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_VIEW -> {
                val link = i.dataString
                val tg = PhoneLogin.parseAppLink(link)
                log("link " + if (tg != null) "ok" else "bad")
                if (tg != null) {
                    target = tg; sent = false; msg = null
                    if (!Prefs.uiLangChosen(this)) PhoneLogin.langFromAppLink(link)?.let { Prefs.setUiLang(this, it) }
                }
                render()
            }
            Intent.ACTION_SEND -> shared(i.getStringExtra(Intent.EXTRA_TEXT) ?: "")
        }
        // jen ve zkušebním sestavení: rovnou otevřít přihlášení (snímek ze zkoušky v emulátoru)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (i?.getBooleanExtra("helperlogin", false) == true && debuggable) openLogin()
        // jen ve zkušebním sestavení: celá aplikace v telefonu (stejně jako tlačítko pro televizi LG)
        if (i?.getBooleanExtra("fullapp", false) == true && debuggable) useForTv(TvType.LG)
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun note(text: String?, color: Int = Theme.MUTED) { msg = text; msgColor = color; if (web == null) render() }

    private fun render() {
        content.removeAllViews()
        val head = ui.hbox()
        head.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
        val names = ui.vbox().apply { setPadding(ui.dp(12), 0, 0, 0) }
        names.addView(ui.text("PlayLingo", 22f, Theme.TEXT, bold = true))
        names.addView(ui.text(t("hp_title"), 14f, Theme.ACCENT, bold = true))
        head.addView(names)
        content.addView(head)

        // nová verze: stejná nabídka jako na stránce Domů v televizi
        update?.let { o ->
            val st = Updater.state
            add(ui.card().apply {
                addView(ui.text(t("upd_new") + " (${o.remote.name}" + (if (o.channel == UpdateCheck.Channel.TEST) " · " + t("upd_test") else "") + ")", 17f, Theme.ACCENT, bold = true))
                val line = when {
                    st == Updater.State.DOWNLOADING -> t("upd_down") + (if (Updater.percent >= 0) " ${Updater.percent} %" else "…")
                    st == Updater.State.INSTALLING -> t("upd_inst")
                    st == Updater.State.FAILED -> t(Updater.problem ?: "upd_fail")
                    else -> null
                }
                line?.let { addView(ui.text(it, 14f, if (st == Updater.State.FAILED) Theme.BAD else Theme.MUTED), ui.lp(top = 4)) }
                // pruh: kolik je staženo (instalace = čekání na systém, pruh jen běhá)
                if (st == Updater.State.DOWNLOADING) addView(ui.progress(if (Updater.percent >= 0) Updater.percent / 100f else null), ui.lp(h = ui.dp(10), top = 8))
                if (st == Updater.State.INSTALLING) addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
                if (st == Updater.State.FAILED) addView(ui.text(UpdateCheck.url(o.channel, "PlayLingo.apk"), 13f, Theme.MUTED), ui.lp(top = 6))
                if (!Updater.busy) addView(ui.button(t("upd_btn"), primary = true) { startUpdate(o) }, ui.lp(top = 10))
            }, 14)
        }

        // Televize LG a Samsung si titulky samy nepřečtou: číst, překládat a posílat je do nich může
        // tento telefon. Pak tu místo pomocníka běží celá aplikace (viz core/TvSender).
        add(ui.card().apply {
            addView(ui.text(t("hp_lg_title"), 18f, Theme.TEXT, bold = true))
            addView(ui.text(t("hp_lg_text"), 15f, Theme.MUTED), ui.lp(top = 6))
            addView(ui.button(t("hp_lg_btn"), primary = true) { if (!busy) useForTv(TvType.LG) }, ui.lp(top = 12))
            addView(ui.button(t("hp_ss_btn"), primary = true) { if (!busy) useForTv(TvType.SAMSUNG) }, ui.lp(top = 8))
            // nahrát PlayLingo do televize LG přímo z telefonu (vývojářský režim LG)
            addView(ui.button(t("lg_open")) { if (!busy) startActivity(Intent(this@PhoneHelperActivity, LgInstallActivity::class.java)) }, ui.lp(top = 8))
            LgInstallActivity.shortLine(this@PhoneHelperActivity)?.let { (s, warn) ->
                addView(ui.text(s, 14f, if (warn) Theme.WARN else Theme.MUTED, bold = warn), ui.lp(top = 6))
            }
            // Samsung: návod, uložení balíčku a ověření, že se televize připojila
            addView(ui.button(t("ss_open")) { if (!busy) startActivity(Intent(this@PhoneHelperActivity, SamsungInstallActivity::class.java)) }, ui.lp(top = 8))
        }, 18)
        add(ui.card().apply { addView(ui.text(t("hp_intro"), 15f, Theme.MUTED)) }, 10)
        val tg = target
        if (!sent) add(ui.card().apply {
            addView(ui.text(t(if (tg != null) "hp_tv_ready" else "hp_scan"), 16f, if (tg != null) Theme.OK else Theme.TEXT, bold = tg != null))
        }, 10)
        msg?.let { add(ui.text(it, 17f, msgColor, bold = true), 14) }

        val accounts = Prefs.phoneAccounts(this)
        if (accounts.isNotEmpty()) {
            content.addView(ui.section(t("hp_saved")))
            accounts.forEachIndexed { i, a ->
                add(ui.card().apply {
                    addView(ui.text(a.name.ifEmpty { t("hp_acc") }, 18f, Theme.TEXT, bold = true))
                    addView(ui.button(t("wp_send"), primary = tg != null && !sent) { if (!busy) send(a) }, ui.lp(top = 12))
                    addView(ui.button(t("hp_forget")) {
                        if (!busy) { Prefs.setPhoneAccounts(this@PhoneHelperActivity, PhoneLogin.withoutAccount(Prefs.phoneAccounts(this@PhoneHelperActivity), a.id)); note(null) }
                    }, ui.lp(top = 8))
                }, if (i == 0) 0 else 10)
            }
        }
        add(ui.button(t("wp_login_btn"), primary = accounts.isEmpty()) { if (!busy) openLogin() }, 22)
        add(ui.button(t("hp_browser")) { if (!busy) openBrowser() }, 8)

        val lang = Prefs.uiLang(this)
        add(ui.settingRow(t("l_ui_lang"), lang.label) { Prefs.setUiLang(this, lang.next()); render() }, 26)
        // zkušební verze (dřív než ostatní) jdou zapnout i tady, bez celé aplikace
        add(ui.settingRow(t("s_test"), t(if (Prefs.testChannel(this)) "on" else "off")) {
            Prefs.setTestChannel(this, !Prefs.testChannel(this)); render(); checkForUpdate()
        }, 8)
        add(ui.button(t("hp_is_tv")) {
            Prefs.setForceTv(this, true)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }.apply { textSize = 14f; setTextColor(Theme.MUTED) }, 8)
        add(ui.text("PlayLingo · ${BuildInfo.VERSION}", 13f, Theme.MUTED), 20)
    }

    /**
     * Telefon bude číst a překládat pro televizi LG: otevře se celá aplikace se zapnutým posíláním
     * titulků. Účet, který je tu už ověřený, si vezme rovnou – párování pak přeskočí přihlášení.
     */
    /** Telefon bude číst a překládat pro televizi LG nebo Samsung (typ televize se nastaví na Domů). */
    private fun useForTv(type: TvType) {
        Prefs.setForceTv(this, true)
        Prefs.setTvType(this, type)
        if (Ps5.parseAccountId(Prefs.psnId(this)) == null) Prefs.phoneAccounts(this).firstOrNull()?.let { Prefs.setPsnId(this, it.id) }
        log(if (type == TvType.LG) "lg mode" else "samsung mode")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ---------------- přihlášení u Sony ----------------

    /** Přihlašovací stránka Sony přímo v aplikaci; po přihlášení si aplikace vezme kód sama. */
    private fun openLogin() {
        if (web != null) return
        val w = try { WebView(this) } catch (e: Throwable) {
            // telefon bez součásti pro zobrazení stránek – zbývá běžný prohlížeč
            log("webview missing " + e.javaClass.simpleName); openBrowser(); return
        }
        msg = null
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        // stránka Sony se má chovat stejně jako v běžném prohlížeči telefonu
        w.settings.userAgentString = w.settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = caught(request.url.toString())
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { caught(url) }
            override fun onPageFinished(view: WebView, url: String) { log("page " + (Uri.parse(url).host ?: "?")) }
        }
        val box = ui.vbox().apply { setBackgroundColor(Theme.BG) }
        box.addView(ui.text(t("hp_login_top"), 14f, Theme.ACCENT).apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10)) })
        box.addView(w, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = ui.hbox().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12)) }
        row.addView(ui.button(t("wz_back")) { closeLogin(); render() }.apply { textSize = 15f })
        row.addView(ui.button(t("hp_browser")) { closeLogin(); openBrowser() }.apply { textSize = 15f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(10) })
        box.addView(row)
        web = w
        setContentView(box)
        log("login open")
        w.loadUrl(PsnAuth.LOGIN_URL)
    }

    private fun closeLogin() {
        val w = web ?: return
        web = null
        try { w.stopLoading(); (w.parent as? ViewGroup)?.removeView(w); w.destroy() } catch (_: Throwable) {}
        setContentView(root)
    }

    /** Sony po přihlášení přesměruje na adresu s jednorázovým kódem – tu aplikace zachytí. */
    private fun caught(url: String): Boolean {
        if (!url.startsWith(PsnAuth.REDIRECT)) return false
        if (web == null) return true
        closeLogin()
        val code = Ps5.authCodeFromUrl(url)
        log("redirect code=" + (code != null))
        if (code == null) note(t("wp_bad"), Theme.BAD) else exchange(code)
        return true
    }

    /** Záloha: přihlášení v běžném prohlížeči, prázdná stránka se pak nasdílí do PlayLinga. */
    private fun openBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PsnAuth.LOGIN_URL)))
            log("browser open")
        } catch (e: Throwable) { log("browser missing " + e.javaClass.simpleName) }
        note(t("hp_browser_text"), Theme.TEXT)
    }

    /** Text nasdílený z prohlížeče: adresa prázdné stránky „redirect“ s kódem. */
    private fun shared(text: String) {
        val url = text.split(Regex("\\s+")).firstOrNull { it.contains("code=") } ?: text
        val code = Ps5.authCodeFromTyped(url)
        log("shared code=" + (code != null))
        if (code == null) note(t("wp_bad"), Theme.BAD) else exchange(code)
    }

    /** Jednorázový kód → číslo účtu (dotaz na Sony), uložit a rovnou poslat televizi, když čeká. */
    private fun exchange(code: String) {
        if (busy) return
        busy = true
        note(t("ps5_acc_wait"))
        Thread({
            val acc = PsnAuth.accountForCode(code)
            runOnUiThread {
                busy = false
                if (isFinishing) return@runOnUiThread
                if (acc == null) { log("account failed"); note(t("wp_bad"), Theme.BAD); return@runOnUiThread }
                Prefs.setPhoneAccounts(this, PhoneLogin.withAccount(Prefs.phoneAccounts(this), acc))
                log("account saved named=" + acc.name.isNotEmpty())
                if (target != null && !sent) send(acc) else note(t("ps5_acc_done"), Theme.OK)
            }
        }, "tp-helper-code").start()
    }

    /** Pošle účet televizi po domácí síti. */
    private fun send(a: PhoneLogin.Account) {
        val tg = target
        if (tg == null || sent) { note(t("hp_scan"), Theme.WARN); return }
        if (busy) return
        busy = true
        note(t("wp_send") + "…")
        Thread({
            val r = LoginClient.send(tg, a)
            runOnUiThread {
                busy = false
                log("send $r")
                if (isFinishing) return@runOnUiThread
                if (r == LoginClient.Result.OK) { sent = true; note(t("hp_sent"), Theme.OK) }
                else note(t("hp_send_fail"), Theme.BAD)
            }
        }, "tp-helper-send").start()
    }
}
