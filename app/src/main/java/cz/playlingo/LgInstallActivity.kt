package cz.playlingo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.playlingo.core.LgAction
import cz.playlingo.core.LgError
import cz.playlingo.core.LgException
import cz.playlingo.core.LgInstall
import cz.playlingo.core.LgPhase
import cz.playlingo.core.LgSetup

/**
 * Průvodce: nahrát PlayLingo do televize LG z tohoto zařízení (telefon nebo box) přes vývojářský
 * režim LG, a kolik dní režimu zbývá. Otevírá se z Nastavení (u volby „Posílat titulky do
 * televize LG“) a z pomocníka v telefonu. Ovládá se ovladačem (šipky + OK) i dotykem.
 * Ruční cesta (počítač s nástrojem LG, obchod LG) zůstává popsaná dole.
 */
class LgInstallActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private lateinit var vault: LgVault
    private var ip = ""
    /** Passphrase jen v paměti, po úspěchu se smaže. */
    private var pass = ""
    private var running = false
    private var phase: LgPhase? = null
    private var installer: LgInstaller? = null
    private var resultText: String? = null
    private var resultOk = false
    private var error: LgError? = null
    private var errorDetail: String? = null
    /** Zpráva po čtení kamerou (text, upozornit?) – jen do dalšího spuštění instalace. */
    private var scanNote: Pair<String, Boolean>? = null

    /** U spárované televize je políčko Passphrase schované, dokud hráč nechce zadat jinou. */
    private var askPass = false

    // ---- pomocník krok za krokem (core/LgSetup.kt): co se o televizi právě ví ----
    private var probe = LgSetup.Probe(tvFound = false, devMode = false, keyServer = false, paired = false, passphrase = false)
    /** Televize LG nalezené v síti (pro řádek „Nalezena televize“). */
    private var foundTvs: List<String> = emptyList()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var watching = false
    @Volatile private var checking = false
    /** Stav se změnil, ale hráč zrovna píše do políčka – překreslit, až z něj odejde. */
    private var needsRender = false
    private var lastSearch = 0L
    private var lastScan = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            if (needsRender && currentFocus !is EditText && !running) { needsRender = false; render(true) }
            check()
            main.postDelayed(this, 3000)
        }
    }

    /** Na pozadí: najdi televizi (když adresa chybí) a zkus porty vývojářského režimu. */
    private fun check() {
        if (checking || running) return
        checking = true
        val ipNow = ip
        val passOk = LgSetup.passphraseLooksOk(pass)
        Thread({
            try {
                var found = foundTvs
                var target = runCatching { LgInstall.address(ipNow) }.getOrNull()
                val now = System.currentTimeMillis()
                if (target == null && now - lastSearch > 10_000) {
                    lastSearch = now
                    found = LgFinder.ssdp(this)
                    if (found.isEmpty() && now - lastScan > 30_000) { lastScan = now; found = LgFinder.scan(this) }
                    target = found.firstOrNull()
                }
                val t = target
                val dev = t != null && LgFinder.open(t, LgSetup.SSH_PORT)
                val key = t != null && LgFinder.open(t, LgSetup.KEY_PORT)
                val tvOn = t != null && (dev || found.contains(t) || LgFinder.open(t, LgSetup.WEBOS_PORT) || LgFinder.open(t, LgSetup.WEBOS_SSL_PORT))
                val paired = t != null && runCatching { vault.paired(t) }.getOrDefault(false)
                val next = LgSetup.Probe(tvOn, dev, key, paired, passOk)
                runOnUiThread {
                    if (isFinishing || running) return@runOnUiThread
                    val fill = ip.isEmpty() && t != null && found.contains(t)
                    if (fill) ip = t!!
                    if (next != probe || found != foundTvs || fill) {
                        probe = next; foundTvs = found
                        // při psaní do políčka nepřekreslovat (zavřela by se klávesnice a skočil kurzor) – až potom
                        if (currentFocus is EditText && !fill) needsRender = true else render(true)
                    }
                }
            } finally { checking = false }
        }, "tp-lg-check").start()
    }

    override fun onResume() {
        super.onResume()
        watching = true
        main.removeCallbacks(tick)
        main.post(tick)
    }

    override fun onPause() {
        watching = false
        main.removeCallbacks(tick)
        super.onPause()
    }

    /** Seznam kroků s fajfkami; rozepsaný je jen ten, který je na řadě. */
    private fun stepsCard(): View = ui.card().apply {
        addView(ui.text(t("lg_steps_h"), 18f, Theme.ACCENT, bold = true))
        val p = probe.copy(passphrase = LgSetup.passphraseLooksOk(pass))
        val now = LgSetup.step(p)
        // kolik kroků zbývá: 4 kroky přípravy + nahrání (READY = poslední)
        val total = LgSetup.Step.values().size
        addView(ui.steps(now.ordinal + 1, total, tx("steps_of").replace("%1", (now.ordinal + 1).toString()).replace("%2", total.toString())), ui.lp(top = 8))
        val steps = listOf(LgSetup.Step.FIND_TV to "lg_s_find", LgSetup.Step.DEV_MODE to "lg_s_dev",
            LgSetup.Step.KEY_SERVER to "lg_s_key", LgSetup.Step.PASSPHRASE to "lg_s_pass")
        for ((s, key) in steps) {
            val done = LgSetup.done(s, p)
            val cur = s == now
            val mark = if (done) "✔ " else if (cur) "➜ " else "○ "
            // spárovaná televize: Passphrase se nepíše, klíč už tohle zařízení má
            val label = if (s == LgSetup.Step.PASSPHRASE && p.paired) t("lg_pass_paired") else t(key)
            addView(ui.text(mark + label, 16f, if (done) Theme.OK else if (cur) Theme.TEXT else Theme.MUTED, bold = cur), ui.lp(top = 8))
            if (cur) {
                addView(ui.text(t(key + "_how"), 15f, Theme.TEXT), ui.lp(top = 4))
                if (s == LgSetup.Step.DEV_MODE) addView(accountLink(), ui.lp(top = 10))
            }
        }
        // v televizi už je stejná nebo novější verze: nic se nenahrává, jen se spustí
        val current = TvChoice.upToDate(this@LgInstallActivity, cz.playlingo.core.TvType.LG)
        if (now == LgSetup.Step.READY) addView(ui.text(current?.let { t("lg_s_current").replace("%s", it) } ?: t("lg_s_ready"), 16f, Theme.OK, bold = true), ui.lp(top = 10))
        foundTvs.takeIf { it.isNotEmpty() }?.let { addView(ui.text(t("lg_s_found").replace("%s", it.joinToString(", ")), 14f, Theme.MUTED), ui.lp(top = 8)) }
    }

    /** Účet LG pro vývojáře: v telefonu tlačítko do prohlížeče, v televizi čtvereček pro telefon. */
    private fun accountLink(): View {
        // naše stránka s pomocí: odkaz na registraci LG + záložní odkazy, kdyby LG stránku přesunulo
        val url = cz.playlingo.core.HelpLinks.LG_ACCOUNT
        if (LgScanActivity.available(this)) return tagged(ui.button(t("lg_s_account")) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        }, "lgacc")
        val row = ui.hbox()
        QrImage.bitmap(url)?.let { qr ->
            row.addView(android.widget.ImageView(this).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6)) },
                LinearLayout.LayoutParams(ui.dp(150), ui.dp(150)))
        }
        row.addView(ui.text(t("lg_s_account") + "\n" + t("help_qr_note"), 15f, Theme.MUTED).apply { setPadding(ui.dp(16), 0, 0, 0) })
        return row
    }

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        vault = LgVault(this)
        ip = vault.lastIp
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply {
            val side = if (resources.configuration.screenWidthDp >= 900) ui.dp(80) else ui.dp(20)
            setPadding(side, ui.dp(28), side, ui.dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
        LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && !running) render(true) } }
    }

    override fun onDestroy() {
        installer?.cancel()
        pass = ""
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (running) { installer?.cancel(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun field(value: String, hintKey: String, tagName: String, secret: Boolean, onChange: (String) -> Unit) = EditText(this).apply {
        setText(value)
        setSelection(value.length)
        hint = t(hintKey)
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            else InputType.TYPE_CLASS_PHONE
        if (!secret) IpField.setup(this)
        imeOptions = EditorInfo.IME_ACTION_DONE
        // Passphrase se nesmí dostat do zálohy ani do automatického vyplňování
        if (secret) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        isSaveEnabled = false
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 17f
        background = ui.focusBg(Theme.CARD)
        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
        tag = tagName
        isEnabled = !running
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
        })
    }

    private fun render(keepFocus: Boolean = false) {
        val focused = if (keepFocus) currentFocus?.tag else null
        content.removeAllViews()
        add(ui.text(t("lg_title"), 26f, Theme.TEXT, bold = true), 0)
        add(ui.text(t("lg_intro"), 16f, Theme.MUTED), 8)

        // krok za krokem: kroky, které jde poznat, se odškrtávají samy (televize v síti, vývojářský režim, Key Server)
        add(stepsCard(), 18)

        // telefon s kamerou: adresu a Passphrase přečíst z obrazovky televize (LgScanActivity) – napřed,
        // ať se nic nepíše ručně
        if (!running && LgScanActivity.available(this)) {
            add(tagged(ui.button(t("km_scan"), primary = ip.isEmpty()) { scan() }, "lgscan"), 20)
            add(ui.text(t("km_why"), 14f, Theme.MUTED), 6)
        }
        scanNote?.let { add(ui.text(it.first, 15f, if (it.second) Theme.WARN else Theme.OK, bold = true), 10) }

        add(ui.text(t("lg_ip"), 16f, Theme.TEXT, bold = true), 20)
        add(field(ip, "lg_ip_hint", "lgip", false) { ip = it.trim() }, 6)
        val paired = runCatching { vault.paired(LgInstall.address(ip)) }.getOrDefault(false)
        // spárovaná televize: prázdné políčko by mátlo („mám něco vyplnit?“) – schované, nabídne se až pod tlačítkem
        if (!paired || askPass) {
            add(ui.text(t("lg_pass"), 16f, Theme.TEXT, bold = true), 16)
            add(field(pass, "lg_pass_hint", "lgpass", true) { val was = LgSetup.passphraseLooksOk(pass); pass = it; if (was != LgSetup.passphraseLooksOk(it)) needsRender = true }, 6)
        }

        if (running) {
            add(ui.text(t("lg_p_" + (phase ?: LgPhase.PACKAGE).name.lowercase()), 17f, Theme.ACCENT, bold = true), 18)
            // nahrávání má pevné kroky (balíček → klíč → spojení → … → spuštění): pruh podle kroku
            val ph = phase ?: LgPhase.PACKAGE
            add(ui.progress((ph.ordinal + 1f) / LgPhase.values().size), 8)
            add(tagged(ui.button(t("lg_cancel")) { installer?.cancel() }, "lgcancel"), 10)
        } else {
            // aktuální verze v televizi: LgInstaller ji stejně jen spustí – tak to i říct
            val current = TvChoice.upToDate(this, cz.playlingo.core.TvType.LG)
            add(tagged(ui.button(t(if (current != null) "lg_launch" else "lg_go"), primary = true) { start(false) }, "lggo"), 18)
            if (paired && !askPass) add(tagged(ui.button(t("lg_pass_other")) { askPass = true; render() }, "lgpassother"), 8)
            resultText?.let { add(ui.text(it, 17f, if (resultOk) Theme.OK else Theme.WARN, bold = true), 14) }
            error?.let { e ->
                add(ui.text(t("lg_e_" + e.name.lowercase()) + (errorDetail?.let { "\n($it)" } ?: ""), 16f, Theme.BAD, bold = true), 14)
                if (e == LgError.HOST_CHANGED) add(tagged(ui.button(t("lg_repair")) { start(true) }, "lgrepair"), 10)
            }
        }

        // vývojářský režim: kolik zbývá
        add(ui.card().apply {
            addView(ui.text(t("lg_dev_h"), 18f, Theme.ACCENT, bold = true))
            val line = devLine(this@LgInstallActivity)
            addView(ui.text(line.first, 17f, if (line.second) Theme.WARN else Theme.TEXT, bold = line.second), ui.lp(top = 8))
            if (line.second) addView(ui.text(t("lg_dev_warn"), 15f, Theme.WARN), ui.lp(top = 6))
            if (LgDevMode.failed) addView(ui.text(t("lg_dev_fail"), 14f, Theme.MUTED), ui.lp(top = 6))
            addView(ui.text(t("lg_dev_note"), 14f, Theme.MUTED), ui.lp(top = 8))
            if (LgDevMode.hasSession(this@LgInstallActivity) && !running)
                addView(tagged(ui.button(t("lg_dev_check")) {
                    LgDevMode.refresh(this@LgInstallActivity, true) { runOnUiThread { if (!isFinishing && !running) render(true) } }
                }, "lgdev"), ui.lp(top = 12))
        }, 22)

        add(ui.card().apply {
            addView(ui.text(t("lg_manual_h"), 18f, Theme.ACCENT, bold = true))
            addView(ui.text(t("lg_manual"), 15f, Theme.MUTED), ui.lp(top = 8))
        }, 14)

        add(tagged(ui.button(t("wz_back")) { if (running) installer?.cancel(); finish() }, "lgback"), 22)

        val target = focused?.let { content.findViewWithTag<View>(it) }
            ?: content.findViewWithTag<View>(if (running) "lgcancel" else if (ip.isEmpty()) "lgip" else "lggo")
        ui.restoringFocus()   // po překreslení bez „pulzu“ vybraného tlačítka
        target?.requestFocus()
    }

    private fun scan() {
        if (running) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION") startActivityForResult(Intent(this, LgScanActivity::class.java), REQ_SCAN)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
        else { scanNote = t("km_denied") to true; render(true) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SCAN) return
        if (resultCode == RESULT_OK && data != null) {
            data.getStringExtra(LgScanActivity.EXTRA_IP)?.let { ip = it }
            data.getStringExtra(LgScanActivity.EXTRA_PASS)?.let { pass = it }
            val unsure = data.getBooleanExtra(LgScanActivity.EXTRA_UNSURE, false)
            scanNote = (t("km_filled") + if (unsure) "\n" + t("km_unsure") else "") to unsure
            error = null; errorDetail = null; resultText = null
            render()
            content.findViewWithTag<View>("lggo")?.requestFocus()
        } else if (data?.getBooleanExtra(LgScanActivity.EXTRA_FAILED, false) == true) {
            scanNote = t("km_cam_fail") to true
            render(true)
        }
    }

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun start(repair: Boolean) {
        if (running) return
        val inst = LgInstaller(this)
        installer = inst
        running = true; phase = LgPhase.PACKAGE; error = null; errorDetail = null; resultText = null; scanNote = null
        render()
        val ipNow = ip; val passNow = pass
        Thread({
            try {
                val r = inst.run(ipNow, passNow, repair) { p -> runOnUiThread { phase = p; if (!isFinishing) render(true) } }
                runOnUiThread { done(r, null) }
            } catch (e: LgException) {
                runOnUiThread { done(null, e) }
            } catch (e: Exception) {
                runOnUiThread { done(null, LgException(LgError.SSH)) }
            }
        }, "tp-lg-install").start()
    }

    private fun done(r: LgInstaller.Result?, e: LgException?) {
        running = false; installer = null
        if (isFinishing || isDestroyed) return
        if (r != null) {
            pass = ""
            resultOk = true
            val key = when (r.action) {
                LgAction.INSTALL -> "lg_r_install"
                LgAction.UPDATE -> "lg_r_update"
                LgAction.LAUNCH -> "lg_r_same"
                LgAction.LAUNCH_NEWER -> "lg_r_newer"
            }
            resultText = t(key).replace("%s", r.version) + "\n" + t(if (r.launched) "lg_r_launched" else "lg_r_nolaunch")
            LgDevMode.refresh(this, false) { runOnUiThread { if (!isFinishing && !running) render(true) } }
            // verze v TV je teď ta nahraná: Domů ji ví hned, i když se aplikace v TV ještě znovu nepřipojila
            // (do 0.97.21 tam zůstávalo staré ohlášení a Domů dál hlásilo „nová …“)
            if (r.action == LgAction.INSTALL || r.action == LgAction.UPDATE) {
                val old = cz.playlingo.core.TvSender.lastSeen?.announce?.takeIf { it.platform == cz.playlingo.core.TvAnnounce.WEBOS }
                if (old != null) cz.playlingo.core.TvSender.lastSeen = cz.playlingo.core.TvSender.Companion.Seen(old.copy(version = r.version), System.currentTimeMillis())
                Prefs.setTvSeen(this, cz.playlingo.core.TvAnnounce.WEBOS, listOfNotNull(old?.model, r.version).joinToString(" · "))
            }
            // zřetelné potvrzení (dřív jen řádek textu – působilo to „tiše“)
            val doneText = resultText
            try {
                android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("✔ PlayLingo " + r.version + " · TV LG")
                    .setMessage(doneText)
                    .setPositiveButton(t("ok")) { d, _ -> d.dismiss(); finish() }
                    .show()
            } catch (_: Exception) {}
        } else if (e != null && e.code != LgError.CANCELLED) {
            error = e.code; errorDetail = e.detail
        }
        render()
    }

    companion object {
        private const val REQ_CAMERA = 41
        private const val REQ_SCAN = 42

        /** Řádek o vývojářském režimu (text, upozornit?) – i pro Nastavení a pomocníka v telefonu. */
        fun devLine(c: Context): Pair<String, Boolean> {
            val time = LgDevMode.known(c) ?: return c.tx("lg_dev_unknown") to false
            val now = System.currentTimeMillis()
            val days = time.days(now)
            val text = if (days >= 1) c.tx("lg_dev_days").replace("%d", days.toString())
                else c.tx("lg_dev_hours").replace("%d", time.hours(now).toString())
            return text to time.warn(now)
        }

        /** Krátký řádek pro Nastavení; null = o televizi zatím nic nevíme. */
        fun shortLine(c: Context): Pair<String, Boolean>? {
            val time = LgDevMode.known(c) ?: return null
            val l = devLine(c)
            return (c.tx("lg_dev_h") + ": " + l.first) to time.warn(System.currentTimeMillis())
        }
    }
}

