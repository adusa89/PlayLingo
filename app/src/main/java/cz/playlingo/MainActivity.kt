package cz.playlingo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import cz.playlingo.core.ScreenLayout
import cz.playlingo.core.Segment
import cz.playlingo.core.DiagLog
import cz.playlingo.core.GameProfiles
import cz.playlingo.core.LoginClient
import cz.playlingo.core.LoginServer
import cz.playlingo.core.PhoneLogin
import cz.playlingo.core.Ps5
import cz.playlingo.core.Ps5Host
import cz.playlingo.core.UpdateCheck
import cz.playlingo.core.SubtitleHistory
import cz.playlingo.core.TranslationProbe
import cz.playlingo.core.TvSender
import cz.playlingo.core.TvType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hlavní obrazovka: vlevo menu s logem, vpravo obsah vybrané sekce.
 * Vše se ovládá šipkami a tlačítkem OK na ovladači od TV.
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 1002
    }

    /** Stránky aplikace. Historie se otevírá z Nastavení, v menu není. */
    private enum class Section(val key: String) {
        HOME("m_home"), PS5("m_ps5"), LOOK("m_look"),
        SETTINGS("m_settings"), HISTORY("m_history"), LICENSE("m_license"), HELP("m_help")
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var menu: LinearLayout
    private lateinit var content: LinearLayout
    private var section = Section.HOME
    private var demo: SubtitleOverlay? = null
    private var testResult: String? = null
    private var busyText: String? = null
    private var lastRunning = false
    private var lastPaused = false
    /** Krok průvodce prvním spuštěním; -1 = průvodce neběží. */
    private var wizard = -1
    private var draftEn = ""
    private var draftTr = ""
    /** Nabídka nové verze (null = žádná není). */
    private var update: UpdateCheck.Offer? = null
    /** Aplikace minule spadla → na stránce Domů upozornění (jen do zavření aplikace). */
    private var crashNote = false
    private var draftCode = ""
    private var codeError = false
    // párování s PlayStationem
    private var draftIp = ""
    private var draftPsn = ""
    private var draftPin = ""
    private var draftLogin = ""
    private var pairMsg: String? = null
    private var pairMsgColor = Theme.MUTED
    private var pairBusy = false
    private var pairStep = 0
    private var draftAuth = ""
    private var skipDraftSave = false
    /** Stránka pro telefon; běží jen po dobu kroků přihlášení účtem (null = neběží). */
    private var loginServer: LoginServer? = null
    private var lastLink = Ps5.Link.OFF
    private var lastFrames = 0L
    private var lastStage = cz.playlingo.core.LinkStatus.Stage.OFF
    private var ticks = 0
    /** Kolik televizí LG / Samsung bylo připojeno a kdy se naposledy ohlásila (pro obnovu stránky Domů). */
    private var lastTv = -1
    private var lastTvSeen = 0L

    private val ticker = object : Runnable {
        override fun run() {
            // obnov obrazovku, když se změnil stav překladu (např. zastavení z notifikace)
            ticks++
            val typing = currentFocus is EditText
            val changed = lastRunning != TranslatorService.running || lastPaused != TranslatorService.paused ||
                lastLink != Ps5Link.state || (lastFrames == 0L) != (TranslatorService.framesDecoded == 0L) ||
                lastStage != TranslatorService.linkStatus().stage ||
                (section == Section.HOME && (lastTv != TranslatorService.tvConnected || lastTvSeen != (TvSender.lastSeen?.at ?: 0L)))
            // náhled obrazu z PlayStationu se obnovuje každé 3 s (ne když uživatel zrovna píše)
            val preview = TranslatorService.running && ticks % 3 == 0 && wizard < 0 &&
                (section == Section.HOME || section == Section.PS5)
            if ((changed || preview) && !typing && !pairBusy) render(keepFocus = true)
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v telefonu aplikace nepřekládá – je tam jen pomocník pro přihlášení účtem PlayStation
        if (NetInfo.isPhone(this)) {
            // Odpověď systému na aktualizaci spuštěnou z pomocníka přijde sem: jen ji vyřídit (ukázat
            // dotaz na instalaci) a zmizet – pomocník zůstává pod tím.
            if (!Updater.onStatus(this, intent)) startActivity(Intent(this, PhoneHelperActivity::class.java))
            finish()
            return
        }
        EventFile.init(this)
        crashNote = CrashLog.takeUnseen(this)
        ui = Ui(this)
        buildShell()
        if (!Prefs.wizardDone(this)) wizard = 0
        Updater.cleanUp(this)
        // odpověď systému na aktualizaci (po úspěšné instalaci otevře už novou verzi)
        if (savedInstanceState == null && Updater.onStatus(this, intent)) setIntent(Intent(this, MainActivity::class.java))
        // po aktualizaci dát vědět, že běží nová verze (na Androidu 12+ proběhne bez jediného okna)
        val seen = Prefs.seenVersion(this)
        if (seen != BuildInfo.VERSION_CODE) {
            Prefs.setSeenVersion(this, BuildInfo.VERSION_CODE)
            if (seen in 1 until BuildInfo.VERSION_CODE) toast(t("upd_done") + " " + BuildInfo.VERSION.substringAfter(' '))
        }
        // pro automatický test: přeskočit průvodce a rovnou spustit překlad
        val auto = intent?.getBooleanExtra("autostart", false) == true
        if (auto) { Prefs.setWizardDone(this, true); wizard = -1 }
        render()
        if (auto) main.postDelayed({ startDemo() }, 1500)
        ps5SelfTest(intent)
        checkForUpdate()
        OfflineTranslator.ensureModel(Prefs.lang(this)) { runOnUiThread { if (section == Section.HOME || wizard >= 0) render(true) } }
        // Android 13+: povolení notifikací (tlačítka Pozastavit / Zastavit v notifikaci)
        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQ_NOTIF) } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Updater.onStatus(this, intent)) { render(true); return }
        ps5SelfTest(intent)
    }

    /**
     * Jen ve zkušebním sestavení (automatická zkouška v emulátoru): vyzkouší hledání konzole,
     * párování a spojení proti adrese, kde žádný PlayStation není – nic nesmí spadnout.
     */
    private fun ps5SelfTest(i: Intent?) {
        if (i?.getBooleanExtra("nowizard", false) == true) { Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME; render() }
        if (i?.getBooleanExtra("phonelogin", false) == true) {
            Prefs.setWizardDone(this, true); wizard = -1
            Prefs.setPs5Host(this, null)
            section = Section.PS5; pairStep = i?.getIntExtra("pairstep", 2) ?: 2; render(); return
        }
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Zkouška rozložení (snímky v různých velikostech displeje): rovnou na danou stránku.
        i?.getStringExtra("page")?.let { p ->
            if (debuggable) Section.values().firstOrNull { it.name == p }?.let {
                Prefs.setWizardDone(this, true); wizard = -1; section = it; render(); return
            }
        }
        // Zkouška překladu: typické herní repliky projdou stejnou cestou jako titulky ze hry
        // a výsledek jde do záznamu (řádky TR|anglicky|překlad).
        if (i?.getBooleanExtra("trtest", false) == true && debuggable) {
            Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME; render()
            val lang = Prefs.lang(this)
            val glossary = Prefs.glossary(this)
            scope.launch(Dispatchers.Default) {
                fun log(m: String) { android.util.Log.i("TP_TEST", "TR|$m") }
                var ready = OfflineTranslator.isReady(lang)
                for (n in 0 until 90) { if (ready) break; kotlinx.coroutines.delay(1000); ready = OfflineTranslator.isReady(lang) }
                log("start ${lang.code} model=$ready")
                for (line in TranslationProbe.LINES) {
                    val out = try { OfflineTranslator.translateSubtitle(lang, line, glossary) } catch (e: Exception) { "!" + e.javaClass.simpleName }
                    log("$line|$out")
                }
                log("done ${TranslationProbe.LINES.size}")
            }
            return
        }
        // Zkouška přihlášení přes telefon: stránka z televize odpovídá, špatný klíč odmítne a účet
        // poslaný „z telefonu“ posune průvodce na krok s číslem z PS5.
        if (i?.getBooleanExtra("logintest", false) == true && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Prefs.setWizardDone(this, true); wizard = -1
            Prefs.setPs5Host(this, null); Prefs.setPsnId(this, ""); draftPsn = ""
            section = Section.PS5; pairStep = 2; render()
            val s = loginServer
            Thread({
                fun log(m: String) { android.util.Log.i("TP_TEST", "LOGIN|$m") }
                if (s == null || !s.running) { log("test no page"); return@Thread }
                try {
                    val page = LoginClient.request("127.0.0.1", s.port, "GET", PhoneLogin.path(s.key))
                    log("test page ${page.first} form=${page.second.contains("name=\"url\"")}")
                    log("test wrong key " + LoginClient.request("127.0.0.1", s.port, "GET", PhoneLogin.path("spatnyklic")).first)
                    val acc = PhoneLogin.Account(Ps5.accountIdToBase64(ByteArray(8) { (it + 1).toByte() }), "Zkouška")
                    log("test app " + LoginClient.send(PhoneLogin.Target("127.0.0.1", s.port, s.key), acc))
                } catch (e: Throwable) { log("test error $e") }
            }, "tp-logintest").start()
            return
        }
        // Zkouška aktualizace. „updtest“: stáhne běžnou vydanou verzi a předá ji systému.
        // „updself“: aplikace nainstaluje sama sebe (stejná verze i podpis) – projde celá instalace.
        val updSelf = i?.getBooleanExtra("updself", false) == true
        if ((updSelf || i?.getBooleanExtra("updtest", false) == true) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Prefs.setWizardDone(this, true); wizard = -1; section = Section.HOME
            val o = UpdateCheck.Offer(UpdateCheck.Remote(BuildInfo.VERSION_CODE + 1, "zkouška"), UpdateCheck.Channel.STABLE)
            update = o
            startUpdate(o, force = true, source = if (updSelf) packageCodePath else null)
            return
        }
        val ip = i?.getStringExtra("ps5test") ?: return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Prefs.setWizardDone(this, true); wizard = -1
        Thread({
            Ps5Pairing.discover(2000)
            Ps5Pairing.pair(ip, ByteArray(8) { (it + 1).toByte() }, 12345678) {
                val key = "1a2b3c4d".toByteArray() + ByteArray(8)
                Prefs.setPs5Host(this, Ps5Host(ip, "Zkouška", key, ByteArray(16) { 7 }, ByteArray(6)))
                runOnUiThread { section = Section.HOME; startFlow() }
            }
        }, "tp-selftest").start()
    }

    override fun onResume() {
        super.onResume()
        UiState.shown()
        render(keepFocus = true)
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, 1000)
    }

    override fun onPause() {
        UiState.hidden()
        main.removeCallbacks(ticker)
        Prefs.setStats(this, TranslatorService.statsFor(this))
        super.onPause()
    }

    override fun onDestroy() {
        loginServer?.stop(); loginServer = null
        demo?.destroy(); demo = null
        scope.cancel()
        super.onDestroy()
    }

    private fun t(key: String) = tx(key)

    // ======================= kostra: menu + obsah =======================

    /**
     * Úzká obrazovka (telefon na výšku). Aplikace je kreslená pro televizi – menu vlevo, obsah vpravo.
     * Na úzké obrazovce by na obsah zbyl proužek, proto je tam menu nahoře jako řada a obsah pod ním
     * přes celou šířku. Na televizi se nic nemění.
     */
    private val narrow: Boolean get() = resources.configuration.screenWidthDp in 1 until 600

    /** Řada tlačítek; na úzké obrazovce jsou tlačítka pod sebou, aby se vešla. */
    private fun row(): LinearLayout = if (narrow) ui.vbox() else ui.hbox()

    private fun menuLp() = if (narrow)
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(6) }
        else ui.lp(top = 4)

    private fun buildNarrowShell() {
        val root = ui.vbox().apply { setBackgroundColor(Theme.BG) }
        val top = ui.vbox().apply {
            setBackgroundColor(Theme.SIDEBAR)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(8))
        }
        val logoRow = ui.hbox()
        logoRow.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
        logoRow.addView(ui.text("PlayLingo", 19f, Theme.TEXT, bold = true).apply { setPadding(ui.dp(10), 0, ui.dp(10), 0) })
        logoRow.addView(ui.text(Prefs.lang(this).label, 13f, Theme.ACCENT, bold = true))
        top.addView(logoRow)
        menu = ui.hbox()
        top.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(menu) }, ui.lp(top = 8))
        root.addView(top, ui.lp())

        val scroll = ScrollView(this).apply { isFillViewport = true; clipChildren = false; clipToPadding = false }
        content = ui.vbox().apply { setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(32)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    /** Zpět k pomocníkovi pro přihlášení (telefon, na kterém byla omylem nebo dočasně otevřená celá aplikace). */
    private fun backToPhoneHelper() {
        if (TranslatorService.running) stopService(Intent(this, TranslatorService::class.java))
        Prefs.setForceTv(this, false)
        startActivity(Intent(this, PhoneHelperActivity::class.java))
        finish()
    }

    private fun buildShell() {
        if (narrow) { buildNarrowShell(); return }
        val root = ui.hbox().apply {
            setBackgroundColor(Theme.BG)
            gravity = Gravity.TOP
        }

        val side = ui.vbox().apply {
            setBackgroundColor(Theme.SIDEBAR)
            setPadding(ui.dp(26), ui.dp(30), ui.dp(18), ui.dp(24))
        }
        val logoRow = ui.hbox()
        logoRow.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
        val names = ui.vbox().apply { setPadding(ui.dp(12), 0, 0, 0) }
        names.addView(ui.text("PlayLingo", 21f, Theme.TEXT, bold = true))
        names.addView(ui.text(Prefs.lang(this).label, 13f, Theme.ACCENT, bold = true))
        logoRow.addView(names)
        side.addView(logoRow)

        menu = ui.vbox()
        side.addView(menu, ui.lp(top = 18))
        root.addView(side, LinearLayout.LayoutParams(ui.dp(280), LinearLayout.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(this).apply { isFillViewport = true; clipChildren = false; clipToPadding = false }
        content = ui.vbox().apply { setPadding(ui.dp(48), ui.dp(34), ui.dp(64), ui.dp(48)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        setContentView(root)
    }

    /** Pod kterou položkou menu právě zobrazená stránka patří. */
    private fun menuSection(): Section = if (section == Section.HISTORY) Section.SETTINGS else section

    private fun renderMenu() {
        menu.removeAllViews()
        for (s in Section.values()) {
            if (s == Section.HISTORY) continue
            val active = s == menuSection()
            val item = ui.hbox().apply {
                isFocusable = true
                isClickable = true
                background = ui.focusBg(if (active) Theme.CARD else Theme.SIDEBAR, radius = 10)
                ui.pop(this)
                setPadding(ui.dp(14), ui.dp(9), ui.dp(14), ui.dp(9))
                addView(View(this@MainActivity).apply {
                    background = GradientDrawable().apply {
                        setColor(if (active) Theme.ACCENT else Theme.SIDEBAR)
                        cornerRadius = ui.dp(2).toFloat()
                    }
                }, LinearLayout.LayoutParams(ui.dp(4), ui.dp(22)).apply { rightMargin = ui.dp(12) })
                addView(ui.text(t(s.key), 17f, if (active) Theme.ACCENT else Theme.TEXT, bold = active))
                setOnClickListener { section = s; render(keepFocus = true) }
                tag = "menu_${s.name}"
            }
            menu.addView(item, menuLp())
        }
    }

    /** Překreslí menu i obsah (po každé změně – je to rychlé a nic se nerozsynchronizuje). */
    private fun render(keepFocus: Boolean = false) {
        val focusedTag = if (keepFocus) currentFocus?.tag else null
        if (!skipDraftSave) saveDrafts()
        syncLoginPage()
        lastRunning = TranslatorService.running
        lastPaused = TranslatorService.paused
        lastLink = Ps5Link.state
        lastFrames = TranslatorService.framesDecoded
        lastStage = TranslatorService.linkStatus().stage
        lastTv = TranslatorService.tvConnected
        lastTvSeen = TvSender.lastSeen?.at ?: 0L
        if (wizard >= 0) { renderWizard(focusedTag); return }
        renderMenu()
        content.removeAllViews()
        content.addView(ui.text(t(if (section == Section.HISTORY) section.key else menuSection().key), 30f, Theme.TEXT, bold = true))
        when (section) {
            Section.HOME -> home()
            Section.PS5 -> ps5()
            // Titulky: nejdřív vzhled, pod ním kde se čtou
            Section.LOOK -> { look(); content.addView(ui.section(t("m_zones"))); zones() }
            // Nastavení: nejdřív povolení a chování, pod ním překlad
            Section.SETTINGS -> { settings(); content.addView(ui.section(t("m_translate"))); translate() }
            Section.HISTORY -> history()
            Section.LICENSE -> license()
            Section.HELP -> help()
        }
        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: menu.findViewWithTag<View>("menu_${menuSection().name}")
        target?.requestFocus()
    }

    // ======================= PRŮVODCE PRVNÍM SPUŠTĚNÍM =======================

    private val wizardSteps = listOf("wz_s0", "wz_s1", "wz_s2", "wz_s3", "wz_s4")

    private fun renderWizard(focusedTag: Any?) {
        menu.removeAllViews()
        wizardSteps.forEachIndexed { i, key ->
            val active = i == wizard
            menu.addView(ui.hbox().apply {
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                addView(ui.text("${i + 1}", 15f, if (i <= wizard) Theme.ACCENT else Theme.MUTED, bold = true),
                    LinearLayout.LayoutParams(ui.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ui.text(t(key), 16f, if (active) Theme.ACCENT else if (i < wizard) Theme.TEXT else Theme.MUTED, bold = active))
            }, menuLp())
        }
        content.removeAllViews()
        content.addView(ui.text(t("wz_title"), 15f, Theme.ACCENT, bold = true))
        content.addView(ui.text(t(wizardSteps[wizard]), 30f, Theme.TEXT, bold = true), ui.lp(top = 4))

        val lang = Prefs.lang(this)
        var nextLabel = t("wz_next")
        when (wizard) {
            0 -> {
                add(ui.text(t("wz_welcome"), 17f, Theme.MUTED), 12)
                add(tagged(ui.settingRow(t("l_lang"), lang.label) {
                    val langs = cz.playlingo.core.Lang.targets
                    choose(t("l_lang"), langs.map { it.label }, langs.indexOf(lang)) { i ->
                        Prefs.setLang(this, langs[i])
                        OfflineTranslator.ensureModel(langs[i]) { runOnUiThread { if (wizard >= 0) render(true) } }
                        render(true)
                    }
                }, "wlang"), 20)
            }
            // titulky jdou do televize LG → povolení pruhu na tomto zařízení není potřeba
            1 -> if (Prefs.tvSend(this)) add(ui.text(t("s_tv_desc"), 17f, Theme.MUTED), 12) else {
                val ok = SubtitleOverlay.canShow(this)
                add(ui.text(t("wz_perm"), 17f, Theme.MUTED), 12)
                add(ui.card().apply { addView(ui.checkRow(ok, false, t("chk_overlay"), t("ok"), null, null)) }, 16)
                if (!ok) {
                    add(tagged(ui.button(t("wz_perm_open"), primary = true) { openAccessibility() }, "wperm"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            2 -> {
                val ok = OfflineTranslator.isReady(lang)
                add(ui.text(t("wz_model"), 17f, Theme.MUTED), 12)
                add(ui.card().apply {
                    addView(ui.checkRow(ok, false, t("chk_model") + " (${lang.label})", t("ok"), null, null))
                    if (!ok) addView(ui.text(busyText ?: t("prep_model"), 15f, Theme.WARN), ui.lp(top = 6))
                }, 16)
                if (!ok) {
                    add(tagged(ui.button(t("download")) { downloadModel() }, "wdl"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            3 -> {
                add(ui.text(t("wz_ps5"), 17f, Theme.MUTED), 12)
                ps5Form()
                if (Prefs.ps5Host(this) == null) nextLabel = t("wz_skip_step")
            }
            else -> {
                add(ui.text(t("wz_done"), 17f, Theme.MUTED), 12)
                add(ui.card().apply {
                    for (i in intArrayOf(4, 5, 3)) addView(ui.text(t("help_$i").substringAfter(". "), 16f), ui.lp(top = if (i == 4) 0 else 8))
                }, 12)
                add(ui.text(t("wz_done_auto"), 15f, Theme.MUTED), 12)
                if (Prefs.ps5Host(this) != null) nextLabel = t("b_start")
            }
        }

        val row = row()
        val pairing = wizard == 3 && Prefs.ps5Host(this) == null
        row.addView(tagged(ui.button(nextLabel, primary = !pairing) {
            if (wizard < wizardSteps.size - 1) { wizard++; render() }
            else {
                val paired = Prefs.ps5Host(this) != null
                finishWizard()
                if (paired) startFlow()
            }
        }, "wnext"))
        if (wizard > 0) row.addView(tagged(ui.button(t("wz_back")) { wizard--; render() }, "wback"), wrapLp())
        row.addView(tagged(ui.button(t(if (wizard == wizardSteps.size - 1) "wz_finish" else "wz_skip")) { finishWizard() }, "wskip"), wrapLp())
        add(row, 28)
        if (NetInfo.isHandheld(this)) add(tagged(ui.button(t("hp_back")) { backToPhoneHelper() }, "tophone"), 16)

        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>(if (pairing) "pnext" else "wnext")
        target?.requestFocus()
    }

    private fun finishWizard() {
        Prefs.setWizardDone(this, true)
        wizard = -1
        section = Section.HOME
        render()
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    private fun onOff(b: Boolean) = if (b) t("on") else t("off")

    private fun wrapLp(left: Int = 12) =
        if (narrow) ui.lp(top = 8)   // tlačítka pod sebou přes celou šířku
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(left) }

    // ======================= DOMŮ =======================

    private fun home() {
        val running = TranslatorService.running
        val paused = TranslatorService.paused
        val demoRun = running && TranslatorService.demo
        // titulky jdou do televize LG → pruh na tomto zařízení není potřeba
        val overlayOk = SubtitleOverlay.canShow(this) || Prefs.tvSend(this)
        val lang = Prefs.lang(this)
        val modelOk = OfflineTranslator.isReady(lang)
        val host = Prefs.ps5Host(this)
        val ready = overlayOk && modelOk && host != null
        val lic = Prefs.license(this)
        val link = Ps5Link.state

        val title: String
        var sub: String
        val color: Int
        when {
            !lic.canRun -> { title = t("lic_expired"); sub = t("lic_expired_sub"); color = Theme.BAD }
            running && paused -> { title = t("st_paused"); sub = t("st_paused_sub"); color = Theme.WARN }
            demoRun -> { title = t("st_running"); sub = t("st_demo"); color = Theme.OK }
            running && link == Ps5.Link.CONNECTED -> {
                title = t("st_running"); color = Theme.OK
                // rychlost čtení titulků na tomto zařízení (číslo je srozumitelné ve všech jazycích)
                sub = t("st_running_sub") + netLine() + (TranslatorService.readMs.takeIf { it > 0 }?.let {
                    "\n⏱ $it ms · ${TranslatorService.videoSize}" + (TranslatorService.lastRead.takeIf { r -> r.isNotEmpty() }?.let { r -> "\n„$r“" } ?: "")
                } ?: "")
            }
            running && link == Ps5.Link.FAILED -> {
                title = t("m_ps5")
                sub = t(if (Ps5Link.needsLoginPin) "ps5_login_fail" else if (Ps5Link.reason == "unstable") "st_unstable" else "st_failed")
                color = Theme.BAD
            }
            running && link == Ps5.Link.WAITING -> { title = t("m_ps5"); sub = t("st_waiting"); color = Theme.WARN }
            running -> { title = t("m_ps5"); sub = t("st_connecting"); color = Theme.WARN }
            ready -> { title = t("st_ready"); sub = t("st_ready_sub"); color = Theme.ACCENT }
            host == null && overlayOk && modelOk -> { title = t("st_setup"); sub = t("st_nops5"); color = Theme.WARN }
            else -> { title = t("st_setup"); sub = t("st_setup_sub"); color = Theme.WARN }
        }
        if (running && !demoRun && link != Ps5.Link.CONNECTED) Ps5Link.reason?.let { sub += "\n($it)" }
        // posílání titulků do televize LG: je televize připojená?
        if (running && Prefs.tvSend(this)) sub += "\n" + t(if (TranslatorService.tvConnected > 0) "st_tv_on" else "st_tv_wait")
        val status = ui.card()
        val head = ui.hbox()
        head.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
            LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)).apply { rightMargin = ui.dp(14) })
        head.addView(ui.text(title, 24f, Theme.TEXT, bold = true))
        status.addView(head)
        status.addView(ui.text(busyText ?: sub, 16f, Theme.MUTED), ui.lp(top = 6))
        // stav spojení v jednom řádku: překlad běží / spojeno / obraz přichází
        if (running) {
            val ls = TranslatorService.linkStatus()
            status.addView(ui.text("● " + cz.playlingo.core.LinkStatus.line(Prefs.uiLang(this), ls), 15f,
                if (ls.warning) Theme.WARN else if (ls.image) Theme.OK else Theme.MUTED).apply { tag = "linkline" }, ui.lp(top = 8))
        }
        add(status, 18)
        if (crashNote) add(ui.card().apply {
            addView(ui.text(t("cr_note") + " " + t("m_help") + " → " + t("rep_btn"), 15f, Theme.WARN))
        }, 12)

        update?.let { o ->
            val st = Updater.state
            val test = if (o.channel == UpdateCheck.Channel.TEST) " · " + t("upd_test") else ""
            add(ui.card().apply {
                addView(ui.text(t("upd_new") + " (${o.remote.name}$test)", 17f, Theme.ACCENT, bold = true))
                val line = when {
                    st == Updater.State.DOWNLOADING -> t("upd_down") + (if (Updater.percent >= 0) " ${Updater.percent} %" else "…")
                    st == Updater.State.INSTALLING -> t("upd_inst")
                    st == Updater.State.FAILED -> t(Updater.problem ?: "upd_fail")
                    else -> t("upd_how")
                }
                addView(ui.text(line, 14f, if (st == Updater.State.FAILED) Theme.BAD else Theme.MUTED), ui.lp(top = 4))
                // když to z aplikace nejde, zbývá ruční cesta – adresa souboru
                if (st == Updater.State.FAILED) addView(ui.text(UpdateCheck.url(o.channel, "PlayLingo.apk"), 13f, Theme.MUTED), ui.lp(top = 6))
                if (!Updater.busy) addView(tagged(ui.button(t("upd_btn"), primary = true) { startUpdate(o) }, "update"),
                    ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
            }, 12)
        }
        val buttons = row()
        if (!lic.canRun) {
            buttons.addView(tagged(ui.button(t("lic_open"), primary = true) { section = Section.LICENSE; render() }, "start"))
        } else if (!running) {
            buttons.addView(tagged(ui.button(t(if (host == null) "ps5_pair" else "b_start"), primary = true) { startFlow() }, "start"))
            buttons.addView(tagged(ui.button(t("b_demo")) { startDemo() }, "demo"), wrapLp())
        } else {
            if (demoRun) {
                buttons.addView(tagged(ui.button(t("b_testcard"), primary = true) {
                    startActivity(Intent(this, TestCardActivity::class.java))
                }, "start"))
            } else {
                buttons.addView(tagged(ui.button(t("b_go_game"), primary = true) { moveTaskToBack(true) }, "start"))
                buttons.addView(tagged(ui.button(t(if (paused) "b_resume" else "b_pause")) { togglePause() }, "pause"), wrapLp())
            }
            buttons.addView(tagged(ui.button(t("b_stop")) { stopTranslation() }, "stop"), wrapLp())
        }
        add(buttons, 18)
        if (lic.kind == cz.playlingo.core.License.Kind.TRIAL) {
            add(ui.text(t("lic_trial").replace("%d", lic.daysLeft.toString()), 14f, Theme.ACCENT), 10)
        }

        if (running && !demoRun) preview()

        // jazyk překladu hned na první stránce
        langRow("homelang", 18)
        // kde se překlad ukazuje: toto zařízení (Android TV), nebo televize LG / Samsung po síti
        tvTypeRow()

        content.addView(ui.section(t("check_title")))
        val check = ui.card()
        // pruh na tomto zařízení je potřeba jen pro Android TV; LG a Samsung ho ukazují samy
        if (!Prefs.tvSend(this)) check.addView(ui.checkRow(overlayOk, false, t("chk_overlay"), t("ok"), t("fix")) { section = Section.SETTINGS; render() })
        check.addView(ui.checkRow(modelOk, false, t("chk_model") + " (${lang.label})", t("ok"), t("download")) { downloadModel() })
        check.addView(ui.checkRow(host != null, false, t("chk_ps5") + (host?.let { " (${it.name})" } ?: ""), t("ok"), t("set_up")) { section = Section.PS5; render() })
        add(check, 0)
        add(ui.text(t("tip_ps5"), 14f, Theme.MUTED), 16)
        // telefon, na kterém běží celá aplikace: cesta zpět k pomocníkovi pro přihlášení
        if (NetInfo.isHandheld(this)) add(tagged(ui.button(t("hp_back")) { backToPhoneHelper() }, "tophone"), 20)
    }

    /**
     * Typ televize na Domů jen jedním řádkem („Typ televize: LG (webOS) – připojeno“); OK na něm otevře
     * nabídku typů ([TvChoice.show]). U LG a Samsungu se pod ním nabízí nahrání aplikace do televize,
     * jen dokud se z ní aplikace ještě nikdy neohlásila ([TvChoice.needsInstall]).
     */
    private fun tvTypeRow() {
        val type = Prefs.tvType(this)
        val (line, connected) = TvChoice.line(this)
        add(tagged(ui.text(line, 16f, if (connected) Theme.OK else Theme.TEXT, bold = connected).apply {
            isFocusable = true
            isClickable = true
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
            setOnClickListener { TvChoice.show(this@MainActivity) { render(true) } }
        }, "tvtype"), 10)
        if (TvChoice.needsInstall(this, type)) {
            add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type) }, "tvinstall"), 8)
        }
        // LG: když vývojářský režim brzy vyprší, aplikace z televize zmizí – upozornit
        if (type == TvType.LG) {
            LgInstallActivity.shortLine(this)?.takeIf { it.second }?.let { (s, _) -> add(ui.text(s, 14f, Theme.WARN, bold = true), 6) }
            LgDevMode.refresh(this, false) {
                runOnUiThread { if (!isFinishing && wizard < 0 && section == Section.HOME && currentFocus !is EditText) render(true) }
            }
        }
    }

    /** Náhled: co aplikace z PlayStationu právě vidí (důkaz, že spojení funguje). */
    private fun preview() {
        val snap = TranslatorService.snapshot ?: return
        content.addView(ui.section(t("ps5_preview")))
        val w = ui.dp(420)
        val h = w * snap.height / maxOf(1, snap.width)
        content.addView(ImageView(this).apply {
            setImageBitmap(snap)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LinearLayout.LayoutParams(w, h))
    }

    // ======================= PLAYSTATION =======================

    private fun ps5() {
        ps5Form()
        if (TranslatorService.running && !TranslatorService.demo) preview()
    }

    private fun input(hintKey: String, value: String, tagName: String, number: Boolean) = EditText(this).apply {
        setText(value)
        hint = t(hintKey)
        setSingleLine(true)
        inputType = if (number) InputType.TYPE_CLASS_PHONE
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(Theme.TEXT)
        setHintTextColor(Theme.MUTED)
        textSize = 17f
        background = ui.focusBg(Theme.CARD)
        setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
        tag = tagName
    }

    /** Uloží rozepsané údaje z políček, než se obrazovka překreslí. */
    private fun saveDrafts() {
        fun read(tagName: String): String? = window.decorView.findViewWithTag<View>(tagName)?.let { (it as? EditText)?.text?.toString() }
        read("pip")?.let { draftIp = it.trim() }
        read("ppin")?.let { draftPin = it.trim() }
        read("pauth")?.let { draftAuth = it.trim() }
        read("plogin")?.let {
            draftLogin = it.trim()
            if (draftLogin.isEmpty() || (draftLogin.length == 4 && draftLogin.all(Char::isDigit))) Prefs.setPs5LoginPin(this, draftLogin)
        }
        read("dicten")?.let { draftEn = it }
        read("dicttr")?.let { draftTr = it }
        read("code")?.let { draftCode = it }
    }

    /** Spárování s PlayStationem (stránka PlayStation i krok průvodce). */
    private fun ps5Form() {
        val host = Prefs.ps5Host(this)
        if (draftLogin.isEmpty()) draftLogin = Prefs.ps5LoginPin(this)
        if (host != null) {
            val c = ui.card()
            c.addView(ui.text(t("ps5_paired") + ": " + host.name, 20f, Theme.OK, bold = true))
            c.addView(ui.text(host.ip, 15f, Theme.MUTED), ui.lp(top = 4))
            add(c, 16)
            pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
            if (wizard < 0 && !TranslatorService.running) add(tagged(ui.button(t("b_start"), primary = true) { section = Section.HOME; startFlow() }, "pstart"), 12)
            add(input("ps5_login", draftLogin, "plogin", number = true), 12)
            add(tagged(ui.button(t("ps5_forget")) {
                if (TranslatorService.running && !TranslatorService.demo) stopService(Intent(this, TranslatorService::class.java))
                Prefs.setPs5Host(this, null); pairMsg = null; render(true)
            }, "pforget"), 12)
            return
        }
        if (draftIp.isEmpty()) draftIp = Prefs.ps5Ip(this)
        if (draftPsn.isEmpty()) draftPsn = Prefs.psnId(this)

        // ---- průvodce spárováním: šest malých kroků, na každé obrazovce jedna věc ----
        val step = pairStep.coerceIn(0, 5)
        val have = Ps5.parseAccountId(draftPsn) != null
        val key = when (step) { 0 -> "pg_1"; 1 -> "pg_2"; 2 -> "pa_1"; 3 -> "pa_2"; 4 -> "pa_3"; else -> "pg_4" }
        // text „krok X ze 4“ se používá i pro šest kroků – číslo celkem se dosadí
        val label = t("pg_step").let { it.substringBefore("%d") + (step + 1) + it.substringAfter("%d").replace("4", "6") }
        add(ui.text(label, 14f, Theme.ACCENT, bold = true), 16)
        add(ui.text(t("${key}_title"), 22f, Theme.TEXT, bold = true), 2)
        var nextOk = true
        when (step) {
            0 -> add(ui.card().apply { addView(ui.text(t("pg_1_text"), 17f, Theme.TEXT)) }, 10)
            1 -> {
                add(ui.card().apply { addView(ui.text(t("pg_2_text"), 17f, Theme.TEXT)) }, 10)
                add(input("ps5_ip", draftIp, "pip", number = true), 10)
                add(tagged(ui.button(t("ps5_find")) { findPs5() }, "pfind"), 8)
            }
            2 -> {
                // telefon: velký QR kód s adresou stránky, kterou televize vystavuje v domácí síti
                // (když se ji vystavit nepodařilo, vede kód rovnou na přihlášení Sony)
                if (have) add(ui.text(t("ps5_acc_done"), 17f, Theme.OK, bold = true), 10)
                val pageUrl = loginPageUrl()
                add(qrRow(pageUrl ?: PsnAuth.LOGIN_URL, t("pa_1_text")), 10)
                // adresa i slovy – pro přihlášení z počítače (stránka funguje v každém prohlížeči
                // ve stejné síti) a pro telefon, kterému se čtvereček nedaří přečíst
                if (pageUrl != null) {
                    // adresa na vlastním řádku, aby se nelámala uprostřed
                    val s = android.text.SpannableStringBuilder(t("pa_pc").trimEnd()).append('\n')
                    val from = s.length
                    s.append(pageUrl)
                    s.setSpan(android.text.style.ForegroundColorSpan(Theme.TEXT), from, s.length, 0)
                    s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, s.length, 0)
                    add(tagged(ui.text(s, 15f, Theme.MUTED), "pcurl"), 6)
                }
                else add(ui.text(t("pa_nolan"), 15f, Theme.WARN), 6)
            }
            3 -> {
                add(ui.card().apply { addView(ui.text(t("acc2"), 16f, Theme.ACCENT)) }, 10)
                add(ui.card().apply { addView(ui.text(t("pa_2_text"), 17f, Theme.TEXT)) }, 10)
            }
            4 -> {
                // záloha, když se stránka z televize v telefonu neotevře (jiná Wi-Fi, mobilní data):
                // čtvereček rovnou na přihlášení Sony a kód opsaný z adresy prázdné stránky „redirect“
                add(qrRow(PsnAuth.LOGIN_URL, t("pa_3_text")), 10)
                add(ui.text(t("pa_example"), 14f, Theme.MUTED), 8)
                // ukázka adresy se zvýrazněným kódem
                val ex = android.text.SpannableStringBuilder("…playstation.net/remoteplay/redirect?code=")
                val from = ex.length
                ex.append("v3.Ab12Cd")
                ex.setSpan(android.text.style.ForegroundColorSpan(Theme.ACCENT), from, ex.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ex.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), from, ex.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ex.append("&cid=0000…")
                add(ui.text(ex, 16f, Theme.TEXT), 2)
                add(input("ph_code", draftAuth, "pauth", number = false), 8)
                nextOk = false
            }
            else -> {
                if (have) add(ui.text(t("ps5_acc_done"), 15f, Theme.OK, bold = true), 8)
                add(ui.card().apply { addView(ui.text(t("pg_4_text"), 17f, Theme.TEXT)) }, 10)
                add(input("ps5_pin", draftPin, "ppin", number = true), 10)
                add(input("ps5_login", draftLogin, "plogin", number = true), 8)
                nextOk = false
            }
        }
        pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
        val row = row()
        when {
            nextOk -> row.addView(tagged(ui.button(t("wz_next"), primary = true) { pairNext() }, "pnext"))
            step == 4 -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_acc_wait" else "ph_check"), primary = true) { if (!pairBusy) checkPhoneCode() }, "pnext"))
            else -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_pairing" else "ps5_pair"), primary = true) { if (!pairBusy) pairPs5() }, "pnext"))
        }
        if (step > 0) row.addView(tagged(ui.button(t("wz_back")) {
            saveDrafts()
            // z kroku s číslem z PS5 zpět rovnou před přihlášení, když už je účet ověřený
            pairStep = if (step == 5 && have) 1 else step - 1
            pairMsg = null; render()
        }, "pback"), wrapLp())
        // stránka z televize se v telefonu neotevřela → rovnou na záložní krok se čtverečkem na Sony
        if (step == 2) row.addView(tagged(ui.button(t("pa_stuck")) {
            saveDrafts()
            pairStep = 4; pairMsg = null; render()
        }, "pstuck"), wrapLp())
        add(row, 14)
        // účet už je uložený: nabídnout nové přihlášení (jiný účet, nebo když spárování s tímhle nejde)
        if (step == 5 && have && !pairBusy) add(tagged(ui.button(t("ps5_acc_again")) {
            saveDrafts()
            draftPsn = ""; draftAuth = ""; Prefs.setPsnId(this, "")
            pairStep = 2; pairMsg = null; render()
        }, "pagain"), 10)
    }

    /** Zpráva u párování; [change] upraví rozepsané údaje (až po uložení toho, co je v políčkách). */
    private fun pairNote(msg: String?, color: Int = Theme.MUTED, busy: Boolean = false, change: (() -> Unit)? = null) {
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            saveDrafts()
            change?.invoke()
            pairMsg = msg; pairMsgColor = color; pairBusy = busy
            skipDraftSave = true
            try { render(true) } finally { skipDraftSave = false }
        }
    }

    /** Další krok průvodce spárováním (s kontrolou toho, co je v daném kroku potřeba). */
    private fun pairNext() {
        saveDrafts()
        val have = Ps5.parseAccountId(draftPsn) != null
        when (pairStep.coerceIn(0, 5)) {
            0 -> { pairStep = 1; pairMsg = null; render(); if (draftIp.isEmpty()) findPs5() }
            1 -> {
                if (!Ps5.validHost(draftIp)) { pairNote(t("ps5_bad_ip"), Theme.BAD); return }
                Prefs.setPs5Ip(this, draftIp)
                pairStep = if (have) 5 else 2; pairMsg = null; render()
            }
            2 -> { pairStep = if (have) 5 else 3; pairMsg = null; render() }
            3 -> { pairStep = 4; pairMsg = null; render() }
        }
    }

    /** Stránka pro telefon běží jen po dobu, kdy průvodce ukazuje kroky přihlášení účtem (2 až 4). */
    private fun syncLoginPage() {
        val pairing = Prefs.ps5Host(this) == null && (wizard == 3 || (wizard < 0 && section == Section.PS5))
        val want = pairing && pairStep.coerceIn(0, 5) in 2..4
        if (want && loginServer == null) {
            loginServer = LoginServer(
                lang = { Prefs.uiLang(this) },
                loginUrl = PsnAuth.LOGIN_URL,
                apkUrl = { UpdateCheck.url(if (Prefs.testChannel(this)) UpdateCheck.Channel.TEST else UpdateCheck.Channel.STABLE, "PlayLingo.apk") },
                resolveCode = { PsnAuth.accountForCode(it) },
                onAccount = { accountFromPhone(it) },
                log = { android.util.Log.i("TP_TEST", "LOGIN|$it"); DiagLog.add("LOGIN|$it") },
            ).also { it.start() }
        } else if (!want && loginServer != null) {
            loginServer?.stop(); loginServer = null
        }
        // přihlašování v telefonu trvá pár minut – televize mezitím nesmí zhasnout
        if (want) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Adresa stránky pro telefon (do čtverečku); null, když stránka neběží nebo televize nezná svou adresu. */
    private fun loginPageUrl(): String? {
        val s = loginServer?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(this) ?: return null
        return PhoneLogin.pageUrl(PhoneLogin.Target(ip, s.port, s.key))
    }

    /** Telefon poslal ověřený účet (ze stránky nebo z PlayLinga v telefonu) → rovnou na číslo z PS5. */
    private fun accountFromPhone(a: PhoneLogin.Account) {
        Prefs.setPsnId(this, a.id)
        pairNote(t("ps5_acc_ok"), Theme.OK) { draftPsn = a.id; draftAuth = ""; pairStep = 5 }
    }

    /** Čtvereček (QR kód) s adresou a vedle něj text; na úzké obrazovce je text pod čtverečkem. */
    private fun qrRow(url: String, text: String): LinearLayout {
        val row = if (narrow) ui.vbox() else ui.hbox().apply { gravity = Gravity.TOP }
        qrBitmap(url)?.let { qr ->
            row.addView(ImageView(this).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) },
                // velikost tak, aby se kód i s tlačítky vešel na obrazovku celý, bez posouvání
                LinearLayout.LayoutParams(ui.dp(200), ui.dp(200)))
        }
        row.addView(ui.text(text, 16f, Theme.TEXT).apply { if (narrow) setPadding(0, ui.dp(12), 0, 0) else setPadding(ui.dp(20), 0, 0, 0) },
            if (narrow) ui.lp() else LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** QR kód s adresou (černobílý obrázek); null, když se ho nepodaří vytvořit. */
    private fun qrBitmap(text: String): android.graphics.Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val n = m.width
        val scale = maxOf(1, 480 / n)
        val px = IntArray(n * scale * n * scale)
        for (y in 0 until n * scale) for (x in 0 until n * scale)
            px[y * n * scale + x] = if (m.get(x / scale, y / scale)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        android.graphics.Bitmap.createBitmap(px, n * scale, n * scale, android.graphics.Bitmap.Config.ARGB_8888)
    } catch (e: Throwable) { null }

    /** Kód opsaný z telefonu → číslo účtu (dotaz jde přímo na server Sony, ne přes přihlašovací stránku). */
    private fun checkPhoneCode() {
        saveDrafts()
        val code = Ps5.authCodeFromTyped(draftAuth)
        if (code == null) { pairNote(t("ph_bad"), Theme.BAD); return }
        pairNote(t("ps5_acc_wait"), busy = true)
        Thread({
            val id = PsnAuth.accountIdForCode(code)
            if (id != null) {
                Prefs.setPsnId(this, id)
                pairNote(t("ps5_acc_ok"), Theme.OK) { draftPsn = id; draftAuth = ""; pairStep = 5 }
            } else pairNote(t("ph_bad"), Theme.BAD)
        }, "tp-code").start()
    }

    private fun findPs5() {
        if (pairBusy) return
        saveDrafts()
        pairNote(t("ps5_finding"), busy = true)
        Thread({
            val list = Ps5Pairing.discover()
            val first = list.firstOrNull()
            if (first != null) {
                Prefs.setPs5Ip(this, first.ip)
                pairNote(t("ps5_found") + " " + list.joinToString { it.name + " (" + it.ip + ")" }, Theme.OK) { draftIp = first.ip }
            } else pairNote(t("ps5_notfound"), Theme.WARN)
        }, "tp-find").start()
    }

    private fun pairPs5() {
        saveDrafts()
        val ip = draftIp
        if (!Ps5.validHost(ip)) { pairNote(t("ps5_bad_ip"), Theme.BAD); return }
        val pin = Ps5.parsePin(draftPin)
        if (pin == null) { pairNote(t("ps5_bad_pin"), Theme.BAD); return }
        Prefs.setPs5Ip(this, ip)
        val account = Ps5.parseAccountId(draftPsn)
        if (account == null) { pairStep = 2; pairNote(t("pg_need_acc"), Theme.BAD); return }
        pairNote(t("ps5_pairing"), busy = true)
        Thread({
            Ps5Pairing.pair(ip, account, pin) { h: Ps5Host? ->
                if (h != null) {
                    Prefs.setPs5Host(this, h)
                    pairNote(t("ps5_ok"), Theme.OK) { draftPin = ""; pairStep = 0 }
                } else pairNote(t("ps5_fail") + (Ps5Pairing.lastError?.let { "\n($it)" } ?: ""), Theme.BAD)
            }
        }, "tp-pair").start()
    }

    // ======================= OBLASTI =======================

    private fun zones() {
        add(ui.text(t("z_desc"), 16f, Theme.MUTED), 8)
        add(tagged(ui.button(t("z_calibrate"), primary = true) {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }, "calib"), 20)
        val l = Prefs.layout(this)
        content.addView(ui.section(t("z_title")))
        add(tagged(ui.settingRow(t("z_cover"), if (l.cover) t("z_cover_on") else t("z_cover_off")) {
            Prefs.setLayout(this, l.copy(cover = !l.cover)); applyToService(); render(true)
        }, "cover"), 0)
        add(tagged(ui.settingRow(t("z_auto"), onOff(Prefs.autoZone(this))) {
            Prefs.setAutoZone(this, !Prefs.autoZone(this)); applyToService(); render(true)
        }, "zauto"), 8)
        add(ui.text(t("z_auto_desc"), 14f, Theme.MUTED), 6)
        add(tagged(ui.settingRow(t("z_only_subs"), onOff(Prefs.onlySubtitles(this))) {
            Prefs.setOnlySubtitles(this, !Prefs.onlySubtitles(this)); applyToService(); render(true)
        }, "onlysubs"), 8)
        add(tagged(ui.button(t("z_reset")) {
            Prefs.setLayout(this, ScreenLayout()); applyToService()
            toast(t("z_reset_done")); render(true)
        }, "zreset"), 16)
    }

    // ======================= VZHLED =======================

    /** Řádek s výběrem jazyka (je na stránce Domů i v Titulcích, ať ho nikdo nehledá). */
    private fun langRow(tag: String, top: Int) {
        add(tagged(ui.settingRow(t("l_lang"), Prefs.lang(this).label) {
            val langs = cz.playlingo.core.Lang.targets
            choose(t("l_lang"), langs.map { it.label }, langs.indexOf(Prefs.lang(this))) { i ->
                Prefs.setLang(this, langs[i])
                OfflineTranslator.ensureModel(langs[i]) { }
                applyToService(); render(true)
            }
        }, tag), top)
    }

    private fun look() {
        val sizeIdx = Prefs.TEXT_SIZES.indexOf(Prefs.textSize(this)).let { if (it < 0) 2 else it }
        langRow("lang", 16)
        add(tagged(ui.settingRow(t("l_size_auto"), onOff(Prefs.matchSize(this))) {
            Prefs.setMatchSize(this, !Prefs.matchSize(this)); applyToService(); render(true)
        }, "sizeauto"), 8)
        // pevná velikost se nabízí jen tehdy, když se písmo neřídí originálem
        if (!Prefs.matchSize(this)) add(tagged(ui.settingRow(t("l_size"), t("size_$sizeIdx")) {
            choose(t("l_size"), Prefs.TEXT_SIZES.indices.map { t("size_$it") }, sizeIdx) { i ->
                Prefs.setTextSize(this, Prefs.TEXT_SIZES[i]); applyToService(); render(true); showDemo()
            }
        }, "size"), 8)
        val lay = Prefs.layout(this)
        add(tagged(ui.settingRow(t("l_pos"), t("pos_" + lay.position.name.lowercase())) {
            val all = cz.playlingo.core.ScreenLayout.Position.values().filter { it != cz.playlingo.core.ScreenLayout.Position.CUSTOM }
            choose(t("l_pos"), all.map { t("pos_" + it.name.lowercase()) }, all.indexOf(lay.position)) { i ->
                Prefs.setLayout(this, lay.withPosition(all[i])); applyToService(); render(true); showDemo()
            }
        }, "pos"), 8)
        val widthPct = Math.round(lay.barWidth * 100)
        add(tagged(ui.settingRow(t("l_width"), "$widthPct %") {
            choose(t("l_width"), Prefs.BAR_WIDTHS.map { "$it %" }, Prefs.BAR_WIDTHS.indexOf(widthPct)) { i ->
                Prefs.setLayout(this, lay.withBarWidth(Prefs.BAR_WIDTHS[i] / 100f)); applyToService(); render(true); showDemo()
            }
        }, "width"), 8)
        val colorIdx = Prefs.textColorIndex(this)
        add(tagged(ui.settingRow(t("l_color"), t("color_$colorIdx"), Prefs.TEXT_COLORS[colorIdx]) {
            choose(t("l_color"), Prefs.TEXT_COLORS.indices.map { t("color_$it") }, colorIdx) { i ->
                Prefs.setTextColorIndex(this, i); applyToService(); render(true); showDemo()
            }
        }, "color"), 8)
        add(tagged(ui.settingRow(t("l_bold"), onOff(Prefs.boldText(this))) {
            Prefs.setBoldText(this, !Prefs.boldText(this)); applyToService(); render(true); showDemo()
        }, "bold"), 8)
        add(tagged(ui.settingRow(t("l_opacity"), "${Prefs.opacity(this)} %") {
            choose(t("l_opacity"), Prefs.OPACITIES.map { "$it %" }, Prefs.OPACITIES.indexOf(Prefs.opacity(this))) { i ->
                Prefs.setOpacity(this, Prefs.OPACITIES[i]); applyToService(); render(true); showDemo()
            }
        }, "opacity"), 8)
        add(tagged(ui.settingRow(t("l_original"), onOff(Prefs.showOriginal(this))) {
            Prefs.setShowOriginal(this, !Prefs.showOriginal(this)); applyToService(); render(true); showDemo()
        }, "original"), 8)
        add(tagged(ui.settingRow(t("l_speaker"), onOff(Prefs.coloredSpeaker(this))) {
            Prefs.setColoredSpeaker(this, !Prefs.coloredSpeaker(this)); applyToService(); render(true); showDemo()
        }, "speaker"), 8)
        val hold = Prefs.holdMs(this)
        add(tagged(ui.settingRow(t("l_hold"), if (hold == 0) t("off") else "+${hold / 1000} s") {
            choose(t("l_hold"), Prefs.HOLD_TIMES.map { if (it == 0) t("off") else "+${it / 1000} s" }, Prefs.HOLD_TIMES.indexOf(hold)) { i ->
                Prefs.setHoldMs(this, Prefs.HOLD_TIMES[i]); applyToService(); render(true)
            }
        }, "hold"), 8)
        add(tagged(ui.button(t("l_demo")) { showDemo() }, "demo"), 18)
        add(tagged(ui.button(t("l_fine")) { startActivity(Intent(this, CalibrationActivity::class.java)) }, "fine"), 8)
        add(tagged(ui.button(t("l_reset")) {
            Prefs.setTextSize(this, Prefs.DEFAULT_TEXT_SIZE); Prefs.setOpacity(this, 100); Prefs.setTextColorIndex(this, 0)
            Prefs.setBoldText(this, false); Prefs.setHoldMs(this, 1000)
            Prefs.setLayout(this, Prefs.layout(this).withPosition(cz.playlingo.core.ScreenLayout.Position.OVER).withBarWidth(0.84f))
            applyToService(); render(true); showDemo()
        }, "lreset"), 8)
    }

    // ======================= PŘEKLAD =======================

    private fun translate() {
        val lang = Prefs.lang(this)
        val ready = OfflineTranslator.isReady(lang)
        val mode = ui.card()
        mode.addView(ui.text(t("t_mode"), 14f, Theme.MUTED))
        mode.addView(ui.text(t("t_offline"), 19f, Theme.OK, bold = true), ui.lp(top = 4))
        mode.addView(ui.text(t("t_offline_desc"), 15f, Theme.MUTED), ui.lp(top = 8))
        mode.addView(ui.text(t("chk_model") + " (${lang.label}): " + if (ready) t("ok") else t("t_model_missing"),
            15f, if (ready) Theme.OK else Theme.WARN), ui.lp(top = 8))
        add(mode, 16)

        val row = row()
        row.addView(tagged(ui.button(t("t_test")) { testTranslation() }, "testtr"))
        if (!ready) row.addView(tagged(ui.button(t("download")) { downloadModel() }, "dl"), wrapLp())
        add(row, 12)
        testResult?.let { add(ui.card().apply { addView(ui.text(it, 15f)) }, 12) }

        // --- hra: jména postav a míst, která se nepřekládají ---
        content.addView(ui.section(t("t_game")))
        val game = GameProfiles.of(Prefs.gameId(this))
        add(tagged(ui.settingRow(t("t_game_row"), if (game.id == "all") t("game_universal") else game.title) {
            val games = GameProfiles.ALL
            choose(t("t_game_row"), games.map { if (it.id == "all") t("game_universal") else it.title }, games.indexOf(game)) { i ->
                Prefs.setGameId(this, games[i].id); applyToService(); render(true)
            }
        }, "game"), 0)
        add(ui.text(t("t_game_desc"), 14f, Theme.MUTED), 6)

        // --- vlastní slovník ---
        content.addView(ui.section(t("t_dict")))
        add(ui.text(t("t_dict_desc"), 14f, Theme.MUTED), 0)
        val dict = Prefs.userDict(this, lang)
        fun field(hintKey: String, value: String, tagName: String) = EditText(this).apply {
            setText(value)
            hint = t(hintKey)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 16f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            tag = tagName
        }
        val en = field("t_dict_en", draftEn, "dicten")
        val tr = field("t_dict_tr", draftTr, "dicttr")
        add(en, 10); add(tr, 8)
        add(tagged(ui.button(t("t_dict_add")) {
            val k = en.text.toString().trim(); val v = tr.text.toString().trim()
            if (k.length >= 2 && v.isNotEmpty()) {
                dict[k] = v; Prefs.setUserDict(this, lang, dict)
                draftEn = ""; draftTr = ""; applyToService()
            } else { draftEn = k; draftTr = v }
            render(true)
        }, "dictadd"), 10)
        if (dict.isNotEmpty()) add(ui.text(t("t_dict_del"), 13f, Theme.MUTED), 12)
        dict.entries.toList().forEachIndexed { i, e ->
            add(tagged(ui.settingRow(e.key, e.value + "   ✕", Theme.TEXT) {
                dict.remove(e.key); Prefs.setUserDict(this, lang, dict); applyToService(); render(true)
            }, "dict$i"), 6)
        }

        val s = TranslatorService.statsFor(this)
        content.addView(ui.section(t("t_stats")))
        val card = ui.card()
        fun stat(label: String, value: String) = card.addView(ui.hbox().apply {
            setPadding(0, ui.dp(4), 0, ui.dp(4))
            addView(ui.text(label, 16f, Theme.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.text(value, 16f, Theme.TEXT, bold = true))
        })
        stat(t("t_lines"), s.lines.toString())
        stat(t("t_hits"), s.cacheHits.toString())
        add(card, 0)
        val row2 = row()
        row2.addView(tagged(ui.button(t("t_reset_stats")) {
            s.reset(); Prefs.setStats(this, s); toast(t("t_cleared")); render(true)
        }, "rstats"))
        row2.addView(tagged(ui.button(t("t_clear_memory")) { clearMemory() }, "clrmem"), wrapLp())
        add(row2, 12)
        add(tagged(ui.button(t("m_history")) { section = Section.HISTORY; render() }, "hist"), 12)
    }

    // ======================= NASTAVENÍ =======================

    private fun settings() {
        content.addView(ui.section(t("s_perm")))
        val a11yOn = TitulkyAccessibility.instance != null
        add(tagged(ui.settingRow(t("s_a11y"), onOff(a11yOn), if (a11yOn) Theme.OK else Theme.WARN) {
            openAccessibility()
        }, "a11y"), 0)
        val ovOn = Settings.canDrawOverlays(this)
        add(tagged(ui.settingRow(t("s_overlay"), onOff(ovOn), if (ovOn) Theme.OK else Theme.MUTED) {
            openOverlaySettings()
        }, "overlay"), 8)

        content.addView(ui.section(t("s_behavior")))
        val q = Prefs.readQuality(this)
        val qKeys = listOf("auto", "std", "high")
        add(tagged(ui.settingRow(t("s_quality"), t("q_$q")) {
            choose(t("s_quality"), qKeys.map { t("q_$it") }, qKeys.indexOf(q)) { i ->
                Prefs.setReadQuality(this, qKeys[i])
                // jiné rozlišení obrazu z konzole = spojení je potřeba navázat znovu
                if (TranslatorService.running) { stopService(Intent(this, TranslatorService::class.java)); toast(t("q_restart")) }
                render(true)
            }
        }, "quality"), 0)
        add(ui.text(t("q_desc"), 14f, Theme.MUTED), 6)
        add(tagged(ui.settingRow(t("s_debug"), onOff(Prefs.debug(this))) {
            Prefs.setDebug(this, !Prefs.debug(this)); applyToService(); render(true)
        }, "debug"), 8)
        add(tagged(ui.settingRow(t("s_test"), onOff(Prefs.testChannel(this))) {
            Prefs.setTestChannel(this, !Prefs.testChannel(this)); checkForUpdate(); render(true)
        }, "testch"), 8)
        add(ui.text(t("s_test_desc"), 14f, Theme.MUTED), 6)
        // Typ televize (Android TV / LG / Samsung) – stejná nabídka jako z řádku na Domů.
        add(tagged(ui.settingRow(t("tv_type"), TvChoice.label(this, Prefs.tvType(this))) {
            TvChoice.show(this) { render(true) }
        }, "tvtype"), 8)
        // jazyk aplikace je nezávislý na jazyku překladu (ten je na stránce Překlad)
        val uiLang = Prefs.uiLang(this)
        add(tagged(ui.settingRow(t("l_ui_lang"), uiLang.label) {
            val all = cz.playlingo.core.Lang.values().toList()
            choose(t("l_ui_lang"), all.map { it.label }, all.indexOf(uiLang)) { i ->
                Prefs.setUiLang(this, all[i]); applyToService(); buildShell(); render()
            }
        }, "uilang"), 8)
    }

    // ======================= HISTORIE =======================

    private fun history() {
        val items = SubtitleHistory.all()
        add(tagged(ui.button(t("h_clear")) { SubtitleHistory.clear(); render(true) }, "hclear"), 16)
        if (items.isEmpty()) {
            add(ui.text(t("h_empty"), 16f, Theme.MUTED), 16); return
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        items.take(100).forEachIndexed { i, it ->
            val c = ui.card().apply { isFocusable = true; background = ui.focusBg(Theme.CARD); tag = "h$i" }
            c.addView(ui.text(fmt.format(Date(it.time)), 12f, Theme.MUTED))
            c.addView(ui.text(it.translated, 18f, Theme.TEXT), ui.lp(top = 4))
            c.addView(ui.text(it.original, 14f, Theme.MUTED), ui.lp(top = 4))
            add(c, 10)
        }
    }

    // ======================= LICENCE =======================

    private fun license() {
        val lic = Prefs.license(this)
        val card = ui.card()
        val (title, sub, color) = when (lic.kind) {
            cz.playlingo.core.License.Kind.UNLOCKED -> Triple(t("lic_unlocked"), t("lic_unlocked_sub"), Theme.OK)
            cz.playlingo.core.License.Kind.TRIAL ->
                Triple(t("lic_trial").replace("%d", lic.daysLeft.toString()), t("lic_trial_sub"), Theme.ACCENT)
            cz.playlingo.core.License.Kind.EXPIRED -> Triple(t("lic_expired"), t("lic_expired_sub"), Theme.BAD)
        }
        card.addView(ui.text(title, 22f, color, bold = true))
        card.addView(ui.text(sub, 16f, Theme.MUTED), ui.lp(top = 6))
        add(card, 16)
        if (lic.kind == cz.playlingo.core.License.Kind.UNLOCKED) return

        content.addView(ui.section(t("lic_pay")))
        add(ui.card().apply { addView(ui.text(t("lic_pay_soon"), 16f, Theme.MUTED)) }, 0)

        content.addView(ui.section(t("lic_code")))
        val code = EditText(this).apply {
            setText(draftCode)
            hint = t("lic_code_hint")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 18f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
            tag = "code"
        }
        add(code, 0)
        add(tagged(ui.button(t("lic_enter"), primary = true) {
            draftCode = code.text.toString()
            if (cz.playlingo.core.License.checkCode(draftCode)) {
                Prefs.setUnlocked(this, true); draftCode = ""
                toast(t("lic_ok")); render()
            } else {
                codeError = true; render(true)
            }
        }, "codeok"), 12)
        if (codeError) { add(ui.text(t("lic_bad"), 15f, Theme.BAD), 10); codeError = false }
    }

    // ======================= NÁPOVĚDA =======================

    private fun help() {
        val c = ui.card()
        for (i in 1..6) c.addView(ui.text(t("help_$i"), 17f), ui.lp(top = if (i == 1) 0 else 10))
        add(c, 16)
        content.addView(ui.section(t("help_tips")))
        val c2 = ui.card()
        for (i in 1..4) c2.addView(ui.text("•  " + t("help_t$i"), 16f, Theme.MUTED), ui.lp(top = if (i == 1) 0 else 8))
        add(c2, 0)
        content.addView(ui.section(t("inst_title")))
        add(ui.card().apply { addView(ui.text(t("inst_text"), 15f, Theme.MUTED)) }, 0)
        content.addView(ui.section(t("lg_title")))
        add(ui.card().apply { addView(ui.text(t("lg_help"), 15f, Theme.MUTED)) }, 0)
        add(tagged(ui.button(t("lg_open")) { startActivity(Intent(this, LgInstallActivity::class.java)) }, "lghelp"), 8)
        content.addView(ui.section(t("ss_title")))
        add(ui.card().apply { addView(ui.text(t("ss_help"), 15f, Theme.MUTED)) }, 0)
        add(tagged(ui.button(t("ss_open")) { startActivity(Intent(this, SamsungInstallActivity::class.java)) }, "sshelp"), 8)
        content.addView(ui.section(t("adb_title")))
        add(ui.card().apply {
            isFocusable = true
            background = ui.focusBg(Theme.CARD)
            tag = "adb"
            addView(ui.text(t("adb_text"), 15f, Theme.MUTED))
            addView(ui.text("adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow", 14f, Theme.ACCENT), ui.lp(top = 8))
        }, 0)
        add(tagged(ui.button(t("wz_again")) { wizard = 0; render() }, "wagain"), 16)
        add(tagged(ui.button(t("rep_btn")) { makeReport() }, "report"), 8)
        add(ui.text("PlayLingo · ${BuildInfo.VERSION}", 13f, Theme.MUTED), 24)
    }

    // ======================= akce =======================

    /** Nabídka se seznamem možností (místo opakovaného mačkání OK). Vybraná položka je označená. */
    private fun choose(title: String, labels: List<String>, current: Int, onPick: (Int) -> Unit) {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(labels.toTypedArray(), current.coerceAtLeast(0)) { d, i -> d.dismiss(); onPick(i) }
                .show()
        } catch (e: Exception) {
            // nouzově: další možnost v pořadí
            onPick((current + 1).coerceAtLeast(0) % labels.size)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    /** Pošle běžícímu překladu nové nastavení. */
    private fun applyToService() {
        if (TranslatorService.running) {
            startService(Intent(this, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        }
    }

    private fun startFlow() {
        if (!Prefs.license(this).canRun) { section = Section.LICENSE; render(); return }
        if (TranslatorService.running && !TranslatorService.demo) { moveTaskToBack(true); return }
        if (!SubtitleOverlay.canShow(this) && !Prefs.tvSend(this)) { toast(t("need_overlay")); section = Section.SETTINGS; render(); return }
        if (Prefs.ps5Host(this) == null) { section = Section.PS5; render(); return }
        busyText = t("prep_model"); render(true)
        OfflineTranslator.ensureModel(Prefs.lang(this)) { ok ->
            runOnUiThread {
                if (!ok) {
                    busyText = t("model_fail"); render(true); busyText = null
                } else {
                    busyText = null
                    launchService(null)
                }
            }
        }
    }

    /** Zkouška bez PlayStationu: ukázková hra jde do aplikace stejnou cestou jako video z konzole. */
    private fun startDemo() {
        if (!Prefs.license(this).canRun) { section = Section.LICENSE; render(); return }
        if (!SubtitleOverlay.canShow(this) && !Prefs.tvSend(this)) { toast(t("need_overlay")); section = Section.SETTINGS; render(); return }
        OfflineTranslator.ensureModel(Prefs.lang(this)) { }
        launchService(TranslatorService.ACTION_START_DEMO)
        main.postDelayed({
            if (TranslatorService.running) startActivity(Intent(this, TestCardActivity::class.java))
        }, 1200)
    }

    private fun launchService(action: String?) {
        try {
            startForegroundService(Intent(this, TranslatorService::class.java).setAction(action))
        } catch (e: Exception) {
            busyText = e.javaClass.simpleName; render(true); busyText = null; return
        }
        main.postDelayed({
            val err = TranslatorService.startError
            if (!TranslatorService.running && err != null) {
                busyText = if (err.startsWith("video")) t("err_video") + " ($err)" else err
                render(true); busyText = null
            } else render(true)
        }, 1000)
    }

    private fun downloadModel() {
        busyText = t("prep_model"); render(true)
        OfflineTranslator.ensureModel(Prefs.lang(this)) { ok ->
            runOnUiThread { busyText = if (ok) null else t("model_fail"); render(true); busyText = null }
        }
    }

    /** Číslo nejnovější verze v daném kanálu; null, když se nedá zjistit. Volat mimo hlavní vlákno. */
    private fun remoteVersion(channel: UpdateCheck.Channel): UpdateCheck.Remote? = Updater.remoteVersion(channel)

    /** Na pozadí zjistí, jestli není ke stažení novější verze (se zapnutým zkušebním kanálem i v něm). */
    private fun checkForUpdate() {
        val test = Prefs.testChannel(this)
        scope.launch {
            val offer = kotlinx.coroutines.withContext(Dispatchers.IO) {
                UpdateCheck.pick(remoteVersion(UpdateCheck.Channel.STABLE),
                    if (test) remoteVersion(UpdateCheck.Channel.TEST) else null, BuildInfo.VERSION_CODE)
            }
            // rozběhnutou aktualizaci (nebo zkoušku aktualizace) nová kontrola nepřepíše
            if (offer != update && !Updater.busy) {
                update = offer
                if (section == Section.HOME && wizard < 0) render(true)
            }
        }
    }

    /** Stáhne nabídnutou verzi a předá ji systému k instalaci; obrazovka ukazuje průběh. */
    private fun startUpdate(o: UpdateCheck.Offer, force: Boolean = false, source: String? = null) {
        Updater.start(this, source ?: UpdateCheck.url(o.channel, "PlayLingo.apk"), force) {
            runOnUiThread { if (!isFinishing && !isDestroyed && section == Section.HOME && wizard < 0 && currentFocus !is EditText) render(true) }
        }
        render(true)
    }

    /** Řádek o připojení televize k síti; u Wi-Fi s radou, protože slabá síť je nejčastější příčina potíží. */
    private fun netLine(): String = when (NetInfo.kind(this)) {
        Ps5.Net.CABLE -> "\n" + t("net_cable")
        Ps5.Net.WIFI_5 -> "\n" + t("net_wifi5")
        Ps5.Net.WIFI_24 -> "\n" + t("net_wifi24")
        Ps5.Net.UNKNOWN -> ""
    }

    /** Hlášení o stavu aplikace přes celou obrazovku (uloží ho i do souboru), viz DiagActivity. */
    private fun makeReport() {
        crashNote = false
        startActivity(Intent(this, DiagActivity::class.java))
    }

    private fun togglePause() {
        startService(Intent(this, TranslatorService::class.java).setAction(TranslatorService.ACTION_PAUSE))
        main.postDelayed({ render(true) }, 300)
    }

    private fun stopTranslation() {
        stopService(Intent(this, TranslatorService::class.java))
        main.postDelayed({ render(true) }, 400)
    }

    private fun openAccessibility() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast(t("a11y_hint"))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)); toast(t("a11y_hint")) } catch (_: Exception) {}
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast(t("overlay_hint"))
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle(t("adb_title"))
                .setMessage(t("adb_text") + "\n\nadb connect IP\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /** Přeloží ukázkovou větu ze hry – ověří, že je jazykový model připravený. */
    private fun testTranslation() {
        val sample = "Well, I reckon we ought to ride out to Greyford before the marshals show up, Marek."
        val lang = Prefs.lang(this)
        testResult = t("t_testing"); render(true)
        scope.launch {
            val off = try { OfflineTranslator.translate(lang, sample) } catch (e: Exception) { null }
            testResult = "${t("t_original")}: $sample\n\n" + (if (off != null) "${t("t_off_ok")}: $off" else t("t_off_fail"))
            if (section == Section.SETTINGS) render(true)
        }
    }

    private fun clearMemory() {
        filesDir.listFiles()?.filter { it.name.startsWith("preklady_") }?.forEach { it.delete() }
        TranslatorService.requestCacheClear()
        toast(t("t_cleared"))
    }

    /** Ukázka pruhu přes obrazovku – nastavíš si vzhled bez hry. */
    private fun showDemo() {
        if (!SubtitleOverlay.canShow(this)) { toast(t("need_overlay")); return }
        demo?.destroy()
        val (w, h) = screenSize()
        val o = SubtitleOverlay(
            this, w, h, Prefs.layout(this), Prefs.textSize(this), Prefs.opacity(this),
            false, Prefs.coloredSpeaker(this),
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this)
        )
        demo = o
        o.show(
            listOf(Segment("Marek", t("demo_line"))),
            if (Prefs.showOriginal(this)) "Marek: We need money, Jonas. A lot of money. And fast." else null,
            -1f, -1f
        )
        main.postDelayed({ if (demo === o) { o.destroy(); demo = null } }, 6000)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }
}

object BuildInfo {
    const val VERSION = "verze 0.73"
    const val VERSION_CODE = 73
}
