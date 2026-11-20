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
import cz.playlingo.core.Console
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
        private const val REQ_PS5_SCAN = 1003
        private const val REQ_PS5_CAMERA = 1004
        private const val REQ_PS5_IP_SCAN = 1005
        // kroky průvodce prvním spuštěním (pořadí = pořadí v menu průvodce; zkouška jím prochází naslepo)
        private const val W_DEVICE = 0
        private const val W_CONSOLE = 1
        /** Licence a platby v aplikaci: zatím skryté, odkryjí se při vydání (true). */
        private const val SHOW_LICENSE = false
        private const val W_TV = 2
        private const val W_LANG = 3
        private const val W_PERM = 4
        private const val W_PAIR = 5
    }

    /**
     * Stránky aplikace v pořadí menu (zkouška jím prochází naslepo šipkami). [key] = nadpis stránky,
     * [menuKey] = text v menu. Nastavení je poslední, s ozubeným kolem. Historie se otevírá
     * z Nastavení, v menu není.
     */
    private enum class Section(val key: String, val menuKey: String = key) {
        HOME("m_home"), PS5("m_ps5", "c_console"), LOOK("m_look"), HELP("m_help"),
        SETTINGS("m_settings"), HISTORY("m_history")
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var ui: Ui
    private lateinit var menu: LinearLayout
    /** Místo pro ozubené kolo (Nastavení): v telefonu vpravo nahoře, na televizi dole v levém sloupci. */
    private lateinit var gear: LinearLayout
    /** Telefon: vodorovně posuvná řada záložek menu (na televizi null). */
    private var menuScroll: HorizontalScrollView? = null

    /** Posune vodorovnou řadu záložek tak, aby vybraná byla celá vidět (doprostřed, když to jde). */
    private fun scrollTabIntoView(scroll: HorizontalScrollView?, tab: View?, smooth: Boolean = true) {
        if (scroll == null || tab == null) return
        scroll.post {
            val x = maxOf(0, tab.left - (scroll.width - tab.width) / 2)
            if (smooth) scroll.smoothScrollTo(x, 0) else scroll.scrollTo(x, 0)
        }
    }
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
                lastLink != TranslatorService.linkState || (lastFrames == 0L) != (TranslatorService.framesDecoded == 0L) ||
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
        initXbox()
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
        // Zkouška: volba konzole (ps / xbox) a stránka konzole – bez sítě, jen vzhled.
        i?.getStringExtra("console")?.let { c ->
            if (debuggable) {
                Prefs.setConsole(this, Console.of(c))
                Prefs.setWizardDone(this, true); wizard = -1; section = Section.PS5; render(); return
            }
        }
        // Zkouška rozložení (snímky v různých velikostech displeje): rovnou na danou stránku.
        // Bývalá stránka Licence je teď součástí Nastavení.
        i?.getStringExtra("page")?.let { p ->
            val name = if (p == "LICENSE") "SETTINGS" else p
            if (debuggable) Section.values().firstOrNull { it.name == name }?.let {
                Prefs.setWizardDone(this, true); wizard = -1; section = it; render(); return
            }
        }
        // Zkouška rozložení průvodce: rovnou na daný krok (0 = Zařízení).
        if (debuggable && i?.hasExtra("wizard") == true) {
            wizard = i.getIntExtra("wizard", 0).coerceIn(0, wizardSteps.size - 1); render(); return
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
        if (::xbox.isInitialized) xbox.cancel()
        loginServer?.stop(); loginServer = null
        deeplUi?.stopPage()
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
        // clipChildren: posuvná část se nesmí kreslit přes lištu (ui.vbox() má ořez vypnutý kvůli zvětšení vybraného prvku)
        val root = ui.vbox().apply { setBackgroundColor(Theme.BG); clipChildren = true }
        val top = ui.vbox().apply {
            setBackgroundColor(Theme.SIDEBAR)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(8))
        }
        val logoRow = ui.hbox()
        logoRow.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)))
        logoRow.addView(ui.text("PlayLingo", 19f, Theme.TEXT, bold = true).apply { setPadding(ui.dp(10), 0, ui.dp(10), 0) })
        logoRow.addView(ui.text(Prefs.lang(this).label, 13f, Theme.ACCENT, bold = true))
        logoRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        gear = ui.hbox()
        logoRow.addView(gear)
        top.addView(logoRow)
        menu = ui.hbox()
        menuScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(menu) }
        top.addView(menuScroll, ui.lp(top = 8))
        root.addView(top, ui.lp())

        val scroll = ScrollView(this).apply { isFillViewport = true }
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
        menuScroll = null
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
        side.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        gear = ui.vbox()
        side.addView(gear, ui.lp())
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
        gear.removeAllViews()
        for (s in Section.values()) {
            if (s == Section.HISTORY) continue
            val active = s == menuSection()
            if (s == Section.SETTINGS && narrow) {
                // telefon: jen ozubené kolo v rohu lišty, vidět pořád
                gear.addView(ui.text("⚙", 26f, if (active) Theme.ACCENT else Theme.TEXT, bold = true).apply {
                    isFocusable = true
                    isClickable = true
                    gravity = Gravity.CENTER
                    background = ui.focusBg(if (active) Theme.CARD else Theme.SIDEBAR, radius = 10)
                    contentDescription = t(s.menuKey)
                    setPadding(ui.dp(12), ui.dp(2), ui.dp(12), ui.dp(4))
                    setOnClickListener { section = s; render(keepFocus = true) }
                    tag = "menu_${s.name}"
                })
                continue
            }
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
                val label = if (s == Section.SETTINGS) "⚙  " + t(s.menuKey) else ct(s.menuKey)
                addView(ui.text(label, 17f, if (active) Theme.ACCENT else Theme.TEXT, bold = active))
                setOnClickListener { section = s; render(keepFocus = true) }
                tag = "menu_${s.name}"
            }
            if (s == Section.SETTINGS) gear.addView(item, ui.lp()) else menu.addView(item, menuLp())
            if (active && narrow) scrollTabIntoView(menuScroll, item)
        }
    }

    /** Překreslí menu i obsah (po každé změně – je to rychlé a nic se nerozsynchronizuje). */
    private fun render(keepFocus: Boolean = false) {
        val focusedTag = if (keepFocus) currentFocus?.tag else null
        if (!skipDraftSave) saveDrafts()
        syncLoginPage()
        if (wizard >= 0 || section != Section.SETTINGS) deeplUi?.stopPage()
        lastRunning = TranslatorService.running
        lastPaused = TranslatorService.paused
        lastLink = TranslatorService.linkState
        lastFrames = TranslatorService.framesDecoded
        lastStage = TranslatorService.linkStatus().stage
        lastTv = TranslatorService.tvConnected
        lastTvSeen = TvSender.lastSeen?.at ?: 0L
        if (wizard >= 0) { renderWizard(focusedTag); return }
        renderMenu()
        content.removeAllViews()
        content.addView(ui.text(ct(if (section == Section.HISTORY) section.key else menuSection().key), 30f, Theme.TEXT, bold = true))
        when (section) {
            Section.HOME -> home()
            Section.PS5 -> ps5()
            // Titulky: nejdřív vzhled, pod ním kde se čtou
            Section.LOOK -> { look(); content.addView(ui.section(t("m_zones"))); zones() }
            // Nastavení (⚙): záložky Obecné / Čtení a překlad / Ladění a hlášení / Experimentální
            Section.SETTINGS -> settings()
            Section.HISTORY -> history()
            Section.HELP -> help()
        }
        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>("menu_${menuSection().name}")
        target?.requestFocus()
    }

    // ======================= PRŮVODCE PRVNÍM SPUŠTĚNÍM =======================

    /**
     * Názvy kroků průvodce: zařízení, konzole, televize, jazyk překladu, povolení pruhu, spárování
     * konzole, hotovo (indexy W_…). Jednorázové volby jdou později změnit v Nastavení.
     */
    private val wizardSteps = listOf("wz_dev", "c_console", "wz_tv", "wz_s2", "wz_s1", "wz_s3", "wz_s4")

    /** Volba v průvodci: řádek s názvem a popisem; vybraná je zlatá a s fajfkou. */
    private fun option(title: String, desc: String?, selected: Boolean, tag: String, onPick: () -> Unit) {
        val row = ui.vbox().apply {
            isFocusable = true
            isClickable = true
            background = ui.focusBg(Theme.CARD)
            ui.pop(this)
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
            addView(ui.text((if (selected) "✓  " else "○  ") + title, 18f, if (selected) Theme.ACCENT else Theme.TEXT, bold = selected))
            desc?.let { addView(ui.text(it, 14f, Theme.MUTED).apply { setPadding(ui.dp(28), 0, 0, 0) }, ui.lp(top = 4)) }
            setOnClickListener { onPick() }
        }
        add(tagged(row, tag), 10)
    }

    /**
     * Na čem PlayLingo běží. Telefon/tablet jen čte a posílá do televize LG / Samsung, proto se mu
     * typ Android TV přepne na LG (v kroku Televize jde změnit na Samsung).
     */
    private fun setReader(reader: Boolean) {
        Prefs.setReaderDevice(this, reader)
        if (reader && !Prefs.tvSend(this)) TvChoice.set(this, TvType.LG)
    }

    private fun renderWizard(focusedTag: Any?) {
        menu.removeAllViews()
        wizardSteps.forEachIndexed { i, key ->
            val active = i == wizard
            menu.addView(ui.hbox().apply {
                setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                addView(ui.text("${i + 1}", 15f, if (i <= wizard) Theme.ACCENT else Theme.MUTED, bold = true),
                    LinearLayout.LayoutParams(ui.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(ui.text(ct(key), 16f, if (active) Theme.ACCENT else if (i < wizard) Theme.TEXT else Theme.MUTED, bold = active))
            }, menuLp())
        }
        content.removeAllViews()
        content.addView(ui.text(t("wz_title"), 15f, Theme.ACCENT, bold = true))
        content.addView(ui.text(ct(wizardSteps[wizard]), 30f, Theme.TEXT, bold = true), ui.lp(top = 4))

        val lang = Prefs.lang(this)
        var nextLabel = t("wz_next")
        when (wizard) {
            W_DEVICE -> {
                add(ui.text(t("wz_intro"), 17f, Theme.MUTED), 12)
                add(ui.text(t("wz_dev_q"), 20f, Theme.TEXT, bold = true), 18)
                val reader = Prefs.readerDevice(this)
                option(t("dev_tv"), t("dev_tv_d"), !reader, "wdevtv") { setReader(false); render(true) }
                option(t("dev_phone"), t("dev_phone_d"), reader, "wdevphone") { setReader(true); render(true) }
            }
            W_CONSOLE -> {
                add(ui.text(t("wz_con_q"), 20f, Theme.TEXT, bold = true), 18)
                val cur = Prefs.console(this)
                option(t("c_ps"), null, cur == Console.PLAYSTATION, "wconps") { setConsole(Console.PLAYSTATION); render(true) }
                option(t("c_xbox"), null, cur == Console.XBOX, "wconxbox") { setConsole(Console.XBOX); render(true) }
                // příprava PlayStationu patří na začátek: bez druhého účtu a Remote Play se párování na konci nepovede
                if (cur == Console.PLAYSTATION) add(ui.card().apply {
                    addView(ui.text(t("wz_ps_prep"), 15f, Theme.ACCENT))
                    // hláška „Remote Play je připojena“ by jinak svítila přes hru
                    addView(ui.text(t("rp_hide"), 15f, Theme.MUTED), ui.lp(top = 10))
                }, 14)
            }
            W_TV -> {
                add(ui.text(t("wz_tv_q"), 20f, Theme.TEXT, bold = true), 18)
                val type = Prefs.tvType(this)
                // telefon pruh přes hru neukáže – jen posílá do televize LG / Samsung
                for (tt in TvType.values()) if (tt != TvType.ANDROID || !Prefs.readerDevice(this)) {
                    option(TvChoice.label(this, tt), if (tt == TvType.ANDROID) t("tv_android_d") else null, tt == type, "wtv_${tt.key}") {
                        TvChoice.set(this, tt); render(true)
                    }
                }
                if (type.sends) {
                    add(ui.text(t("s_tv_desc"), 15f, Theme.MUTED), 14)
                    add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type) }, "wtvinstall"), 10)
                    add(ui.text(t("wz_tv_later"), 14f, Theme.MUTED), 8)
                }
            }
            W_LANG -> {
                val ok = OfflineTranslator.isReady(lang)
                add(ui.text(t("wz_model"), 17f, Theme.MUTED), 12)
                add(tagged(ui.settingRow(t("l_lang"), lang.label) {
                    val langs = cz.playlingo.core.Lang.targets
                    choose(t("l_lang"), langs.map { it.label }, langs.indexOf(lang)) { i ->
                        Prefs.setLang(this, langs[i])
                        OfflineTranslator.ensureModel(langs[i]) { runOnUiThread { if (wizard >= 0) render(true) } }
                        render(true)
                    }
                }, "wlang"), 16)
                add(ui.card().apply {
                    addView(ui.checkRow(ok, false, t("chk_model") + " (${lang.label})", t("ok"), null, null))
                    if (!ok) addView(ui.text(busyText ?: t("prep_model"), 15f, Theme.WARN), ui.lp(top = 6))
                }, 12)
                if (!ok) {
                    add(tagged(ui.button(t("download")) { downloadModel() }, "wdl"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            // titulky jdou do televize LG / Samsung → povolení pruhu na tomto zařízení není potřeba
            W_PERM -> if (Prefs.tvSend(this)) add(ui.text(t("s_tv_desc"), 17f, Theme.MUTED), 12) else {
                val ok = SubtitleOverlay.canShow(this)
                add(ui.text(t("wz_perm"), 17f, Theme.MUTED), 12)
                add(ui.card().apply { addView(ui.checkRow(ok, false, t("chk_overlay"), t("ok"), null, null)) }, 16)
                if (!ok) {
                    add(tagged(ui.button(t("wz_perm_open"), primary = true) { openAccessibility() }, "wperm"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            W_PAIR -> {
                add(ui.text(ct("wz_ps5"), 17f, Theme.MUTED), 12)
                ps5Form()
                if (!Prefs.consoleReady(this)) nextLabel = t("wz_skip_step")
            }
            else -> {
                add(ui.text(t("wz_done"), 17f, Theme.MUTED), 12)
                add(ui.card().apply {
                    for (i in intArrayOf(4, 5, 3)) addView(ui.text(t("help_$i").substringAfter(". "), 16f), ui.lp(top = if (i == 4) 0 else 8))
                }, 12)
                add(ui.text(t("wz_done_auto"), 15f, Theme.MUTED), 12)
                if (Prefs.consoleReady(this)) nextLabel = t("b_start")
            }
        }

        val row = row()
        val pairing = wizard == W_PAIR && !Prefs.consoleReady(this)
        row.addView(tagged(ui.button(nextLabel, primary = !pairing) {
            if (wizard < wizardSteps.size - 1) { wizard++; render() }
            else {
                val paired = Prefs.consoleReady(this)
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
        val onXbox = Prefs.console(this) == Console.XBOX
        val paired = Prefs.consoleReady(this)
        val ready = overlayOk && modelOk && paired
        val lic = Prefs.license(this)
        val link = TranslatorService.linkState

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
                sub = ct("st_running_sub") + netLine() + (TranslatorService.readMs.takeIf { it > 0 }?.let {
                    "\n⏱ $it ms · ${TranslatorService.videoSize}" + (TranslatorService.lastRead.takeIf { r -> r.isNotEmpty() }?.let { r -> "\n„$r“" } ?: "")
                } ?: "")
            }
            running && link == Ps5.Link.FAILED -> {
                title = ct("m_ps5")
                sub = if (onXbox) t(TranslatorService.linkReason ?: "xb_st_failed")
                    else t(if (Ps5Link.needsLoginPin) "ps5_login_fail" else if (Ps5Link.reason == "unstable") "st_unstable" else "st_failed")
                color = Theme.BAD
            }
            running && link == Ps5.Link.WAITING -> { title = ct("m_ps5"); sub = ct("st_waiting"); color = Theme.WARN }
            running -> { title = ct("m_ps5"); sub = ct("st_connecting"); color = Theme.WARN }
            ready -> { title = t("st_ready"); sub = ct("st_ready_sub"); color = Theme.ACCENT }
            !paired && overlayOk && modelOk -> { title = t("st_setup"); sub = ct("st_nops5"); color = Theme.WARN }
            else -> { title = t("st_setup"); sub = t("st_setup_sub"); color = Theme.WARN }
        }
        if (running && !demoRun && link != Ps5.Link.CONNECTED && !(onXbox && link == Ps5.Link.FAILED))
            TranslatorService.linkReason?.let { sub += "\n(" + (if (onXbox) t(it) else it) + ")" }
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
            status.addView(ui.text("● " + cz.playlingo.core.LinkStatus.line(Prefs.uiLang(this), ls, Prefs.console(this)), 15f,
                if (ls.warning) Theme.WARN else if (ls.image) Theme.OK else Theme.MUTED).apply { tag = "linkline" }, ui.lp(top = 8))
            // spojení ukončil sám PlayStation: ukázat jeho důvod, ať je vidět, že nejde o výpadek sítě
            if (!ls.console && Prefs.console(this) == Console.PLAYSTATION) Ps5Link.endedByConsole?.let { why ->
                status.addView(ui.text(t("ls_ended").replace("%s", why), 14f, Theme.WARN), ui.lp(top = 4))
            }
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
            buttons.addView(tagged(ui.button(t("lic_open"), primary = true) { section = Section.SETTINGS; render() }, "start"))
        } else if (!running) {
            buttons.addView(tagged(ui.button(t(if (!paired && !onXbox) "ps5_pair" else "b_start"), primary = true) { startFlow() }, "start"))
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

        // jedním řádkem, s čím aplikace pracuje (konzole → televize); OK otevře malou nabídku změn
        summaryRow()
        // upozornění jen na to, co chybí
        warnings(overlayOk, modelOk, paired, running)

        if (running && !demoRun) preview()
    }

    /**
     * Souhrnný řádek na Domů: „PS5 (Obývák) → LG OLED55B7 · připojeno“. Zeleně, když je konzole
     * připravená a televize LG/Samsung připojená (u Android TV stačí konzole). OK otevře nabídku
     * Konzole / Typ televize ([chooseSetup]).
     */
    private fun summaryRow() {
        val ready = Prefs.consoleReady(this)
        val con = (if (Prefs.console(this) == Console.XBOX) "Xbox" else "PS5") +
            (consoleName()?.let { " ($it)" } ?: if (!ready) " (" + t("sum_nopair") + ")" else "")
        val (tv, connected) = TvChoice.short(this)
        val ok = ready && (connected || !Prefs.tvSend(this))
        add(tagged(ui.text("$con  →  $tv", 17f, if (ok) Theme.OK else Theme.TEXT, bold = true).apply {
            isFocusable = true
            isClickable = true
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            setOnClickListener { chooseSetup() }
        }, "summary"), 18)
        // LG / Samsung: nahrání (i aktualizace) aplikace do zvolené televize vždy po ruce. Dokud se
        // televize ještě neohlásila, nabízí ho karta upozornění níž – tady by bylo dvakrát.
        val type = Prefs.tvType(this)
        if (type.sends && !TvChoice.needsInstall(this, type)) {
            add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type) }, "htvinstall"), 10)
        }
    }

    /** Malá nabídka ze souhrnného řádku: změnit konzoli, nebo typ televize. */
    private fun chooseSetup() {
        val c = Prefs.console(this)
        val items = arrayOf(
            t("c_console") + ": " + t(if (c == Console.XBOX) "c_xbox" else "c_ps"),
            t("tv_type") + ": " + TvChoice.label(this, Prefs.tvType(this)),
        )
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("sum_q"))
                .setItems(items) { d, i -> d.dismiss(); if (i == 0) chooseConsole() else TvChoice.show(this) { render(true) } }
                .show()
        } catch (_: Exception) { section = Section.SETTINGS; render() }
    }

    /**
     * Upozornění na Domů – jen když něco chybí: povolení pruhu (Android TV), jazykový model,
     * nespárovaná konzole, aplikace v televizi LG/Samsung (nenahraná, nebo nepřipojená za běhu),
     * končící vývojářský režim LG a zkušební doba (3 dny a méně). Každé jde rovnou opravit.
     */
    private fun warnings(overlayOk: Boolean, modelOk: Boolean, paired: Boolean, running: Boolean) {
        val card = ui.card()
        fun warn(v: View) = card.addView(v, ui.lp(top = if (card.childCount == 0) 0 else 4))
        fun note(s: String) = warn(ui.text(s, 15f, Theme.WARN, bold = true).apply { setPadding(0, ui.dp(8), 0, ui.dp(8)) })
        val type = Prefs.tvType(this)
        if (!overlayOk) warn(ui.checkRow(false, false, t("w_overlay"), t("ok"), t("fix")) { section = Section.SETTINGS; render() })
        if (!modelOk) warn(ui.checkRow(false, false, t("w_model").replace("%s", Prefs.lang(this).label), t("ok"), t("download")) { downloadModel() })
        // nespárovaný PlayStation říká už velký stav a tlačítko Spárovat nahoře – tady jen u Xboxu
        if (!paired && Prefs.console(this) == Console.XBOX) warn(ui.checkRow(false, false, ct("chk_ps5_missing"), t("ok"), t("set_up")) { section = Section.PS5; render() })
        if (TvChoice.needsInstall(this, type)) {
            warn(ui.checkRow(false, false, TvChoice.label(this, type) + ": " + t("tv_st_never"), t("ok"), t("w_install")) { TvChoice.openInstall(this, type) })
        } else {
            // v televizi je starší PlayLingo, než nese tahle aplikace – nové volby (vzhled pruhu…) by tam chyběly
            TvChoice.outdated(this, type)?.let { (old, new) ->
                warn(ui.checkRow(false, false, t("w_tv_old").replace("%1", old).replace("%2", new), t("ok"), t("w_install")) { TvChoice.openInstall(this, type) })
            }
            if (running && type.sends && TranslatorService.tvConnected == 0) note(t("st_tv_wait"))
        }
        // LG: když vývojářský režim brzy vyprší, aplikace z televize zmizí – upozornit
        if (type == TvType.LG) {
            LgInstallActivity.shortLine(this)?.takeIf { it.second }?.let { (s, _) -> note(s) }
            LgDevMode.refresh(this, false) {
                runOnUiThread { if (!isFinishing && wizard < 0 && section == Section.HOME && currentFocus !is EditText) render(true) }
            }
        }
        // licence a platby jsou zatím skryté (odkryjí se při vydání) – upozornění na konec zkušební doby taky
        val lic = Prefs.license(this)
        if (SHOW_LICENSE && lic.kind == cz.playlingo.core.License.Kind.TRIAL && lic.daysLeft <= 3) {
            warn(ui.checkRow(false, false, t("lic_trial").replace("%d", lic.daysLeft.toString()), t("ok"), t("lic_open")) {
                section = Section.SETTINGS; render()
            })
        }
        if (card.childCount > 0) add(card, 12)
    }

    /** Náhled: co aplikace z PlayStationu právě vidí (důkaz, že spojení funguje). */
    private fun preview() {
        val snap = TranslatorService.snapshot ?: return
        content.addView(ui.section(ct("ps5_preview")))
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
        add(ui.text(ct("tip_ps5"), 14f, Theme.MUTED), 20)
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

    /**
     * Políčko pro přihlašovací kód účtu na PS5 (4 číslice): skoro nikdo ho nepotřebuje, takže se ukáže,
     * jen když o kód PlayStation při spojení požádal, nebo když už nějaký je uložený.
     */
    private fun loginPinWanted() = Ps5Link.needsLoginPin || draftLogin.isNotEmpty() || Prefs.ps5LoginPin(this).isNotEmpty()

    /** Hledání PlayStationu v síti nic nenašlo → nabídnout načtení adresy kamerou. */
    private var ps5NotFound = false
    /** Na co se čeká po povolení kamery: adresa (true) nebo číslo pro spárování. */
    private var scanIpPending = false

    private fun scanPs5Code(ip: Boolean = false) {
        saveDrafts()
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION") startActivityForResult(Intent(this, LgScanActivity::class.java)
                .putExtra(LgScanActivity.EXTRA_MODE, if (ip) LgScanActivity.MODE_PS5_IP else LgScanActivity.MODE_PS5), if (ip) REQ_PS5_IP_SCAN else REQ_PS5_SCAN)
        } else { scanIpPending = ip; requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_PS5_CAMERA) }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION") super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PS5_CAMERA) return
        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) scanPs5Code(scanIpPending)
        else toast(t("km_denied"))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PS5_SCAN && requestCode != REQ_PS5_IP_SCAN) return
        val code = data?.getStringExtra(LgScanActivity.EXTRA_CODE)
        if (resultCode == RESULT_OK && code != null) {
            saveDrafts()
            if (requestCode == REQ_PS5_IP_SCAN) { draftIp = code; Prefs.setPs5Ip(this, code); pairMsg = null } else draftPin = code
            // políčko s číslem ještě drží starý text – nepřepsat jím přečtené číslo
            skipDraftSave = true
            try { render(true) } finally { skipDraftSave = false }
        } else if (data?.getBooleanExtra(LgScanActivity.EXTRA_FAILED, false) == true) toast(t("km_cam_fail"))
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
        if (Prefs.console(this) == Console.XBOX) {
            xbox.render(if (wizard < 0) ({ section = Section.HOME; startFlow() }) else null); return
        }
        val host = Prefs.ps5Host(this)
        if (draftLogin.isEmpty()) draftLogin = Prefs.ps5LoginPin(this)
        if (host != null) {
            val c = ui.card()
            c.addView(ui.text(t("ps5_paired") + ": " + host.name, 20f, Theme.OK, bold = true))
            c.addView(ui.text(host.ip, 15f, Theme.MUTED), ui.lp(top = 4))
            add(c, 16)
            pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
            if (wizard < 0 && !TranslatorService.running) add(tagged(ui.button(t("b_start"), primary = true) { section = Section.HOME; startFlow() }, "pstart"), 12)
            // přihlašovací kód účtu na PS5 jen tehdy, když ho PlayStation chtěl (nebo už je zadaný)
            if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 12)
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
                add(input("ps5_ip", draftIp, "pip", number = true).also { IpField.setup(it) }, 10)
                add(tagged(ui.button(t("ps5_find")) { findPs5() }, "pfind"), 8)
                // adresu aplikace většinou najde sama; kamera jen když hledání neuspělo (telefon s kamerou)
                if (ps5NotFound && LgScanActivity.available(this)) add(tagged(ui.button(t("km_scan_ps5ip")) { scanPs5Code(true) }, "pscanip"), 8)
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
                // telefon s kamerou: osmimístné číslo přečíst z obrazovky PlayStationu
                if (LgScanActivity.available(this)) add(tagged(ui.button(t("km_scan_ps5")) { scanPs5Code() }, "pscan"), 8)
                if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 8)
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
        val pairing = Prefs.console(this) == Console.PLAYSTATION && Prefs.ps5Host(this) == null && (wizard == W_PAIR || (wizard < 0 && section == Section.PS5))
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
        else if (!(::xbox.isInitialized && xbox.waiting)) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    private fun qrBitmap(text: String): android.graphics.Bitmap? = QrImage.bitmap(text)

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
                pairNote(t("ps5_found") + " " + list.joinToString { it.name + " (" + it.ip + ")" }, Theme.OK) { draftIp = first.ip; ps5NotFound = false }
            } else pairNote(t("ps5_notfound"), Theme.WARN) { ps5NotFound = true }
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

    /** Řádek s výběrem jazyka překladu (stránka Titulky; v průvodci krok Jazyk překladu). */
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
        // LG / Samsung: vzhled a oblasti jsou zvlášť pro každou televizi (TvProfile) – pro kterou teď
        TvProfile.activeName(this)?.let { add(ui.text(t("tv_profile_for").replace("%s", it), 14f, Theme.MUTED), 10) }
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
        // pokus DeepL zapnutý v Experimentálních: tady to musí být vidět, jinak by „jen offline“ mátlo
        val deepl = DeeplTranslator.activeKey(this) != null
        mode.addView(ui.text(t(if (deepl) "dl_mode_on" else "t_offline"), 19f, Theme.OK, bold = true), ui.lp(top = 4))
        if (deepl) mode.addView(ui.text(t("dl_mode_on_desc"), 15f, Theme.MUTED), ui.lp(top = 4))
        else if (DeeplKey.enabled(this)) mode.addView(ui.text(t("dl_mode_idle"), 15f, Theme.WARN), ui.lp(top = 4))
        mode.addView(ui.text(t("chk_model") + " (${lang.label}): " + if (ready) t("ok") else t("t_model_missing"),
            15f, if (ready) Theme.OK else Theme.WARN), ui.lp(top = 8))
        add(mode, 16)

        if (!ready) add(tagged(ui.button(t("download")) { downloadModel() }, "dl"), 12)

        // --- hra: jména postav a míst, která se nepřekládají ---
        content.addView(ui.section(t("t_game")))
        val game = GameProfiles.of(Prefs.gameId(this))
        add(tagged(ui.settingRow(t("t_game_row"), if (game.id == "all") t("game_universal") else game.title) {
            val games = GameProfiles.ALL
            choose(t("t_game_row"), games.map { if (it.id == "all") t("game_universal") else it.title }, games.indexOf(game)) { i ->
                Prefs.setGameId(this, games[i].id); applyToService(); render(true)
            }
        }, "game"), 0)

        // --- vlastní slovník ---
        content.addView(ui.section(t("t_dict")))
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
        add(en, 0); add(tr, 8)
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

    /** Záložky Nastavení (⚙) v tomto pořadí; [key] = název záložky. */
    private enum class SettingsTab(val key: String) { GENERAL("st_general"), READ("st_read"), DEMO("st_demo"), DIAG("s_diag"), EXPERIMENTAL("dl_section") }

    private var settingsTab = SettingsTab.GENERAL

    /**
     * Nastavení (⚙, vidět pořád v rohu): nahoře záložky, pod nimi jen obsah vybrané záložky – ať se
     * nemusí dlouho posouvat. Dlouhé popisy voleb jsou v Nápovědě (část help_settings), tady jen názvy.
     * Licence a platby jsou zatím skryté; ukážou se jen tehdy, když zkušební doba skončila (jinak by
     * hráč nevěděl, proč aplikace nejde spustit).
     */
    private fun settings() {
        val lic = Prefs.license(this)
        if (!lic.canRun) { content.addView(ui.section(t("m_license"))); license() }

        val tabs = ui.hbox()
        var activeTab: View? = null
        for (tab in SettingsTab.values()) {
            val active = tab == settingsTab
            tabs.addView(ui.text(t(tab.key), 16f, if (active) Theme.BG else Theme.TEXT, bold = true).apply {
                isFocusable = true
                isClickable = true
                background = ui.focusBg(if (active) Theme.ACCENT else Theme.CARD, radius = 20)
                ui.pop(this)
                setPadding(ui.dp(18), ui.dp(9), ui.dp(18), ui.dp(9))
                setOnClickListener { settingsTab = tab; render(keepFocus = true) }
                tag = "stab_${tab.name}"
                if (active) activeTab = this
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(8) })
        }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(tabs) }
        add(tabScroll, 14)
        // vybraná záložka celá na obrazovce (řada se překresluje, takže by jinak začínala vždy zleva)
        scrollTabIntoView(tabScroll, activeTab, smooth = false)

        when (settingsTab) {
            SettingsTab.GENERAL -> settingsGeneral()
            SettingsTab.READ -> { settingsRead(); translate() }
            SettingsTab.DEMO -> settingsDemo()
            SettingsTab.DIAG -> settingsDiag()
            SettingsTab.EXPERIMENTAL -> experimentalSection()
        }
        add(ui.text(t("st_more"), 14f, Theme.MUTED), 24)
    }

    /** Obecné: konzole, televize (+ nahrání), jazyk aplikace, zařízení, zkušební verze, povolení (jen Android TV). */
    private fun settingsGeneral() {
        content.addView(ui.section(t("s_setup")))
        consoleRow("sconsole", 0)
        // Typ televize (Android TV / LG / Samsung) – stejná nabídka jako ze souhrnného řádku na Domů.
        val type = Prefs.tvType(this)
        add(tagged(ui.settingRow(t("tv_type"), TvChoice.label(this, type)) {
            TvChoice.show(this) { render(true) }
        }, "tvtype"), 8)
        if (type.sends) add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type) }, "stvinstall"), 8)
        // jazyk aplikace je nezávislý na jazyku překladu (ten je na stránce Titulky)
        val uiLang = Prefs.uiLang(this)
        add(tagged(ui.settingRow(t("l_ui_lang"), uiLang.label) {
            val all = cz.playlingo.core.Lang.values().toList()
            choose(t("l_ui_lang"), all.map { it.label }, all.indexOf(uiLang)) { i ->
                Prefs.setUiLang(this, all[i]); applyToService(); buildShell(); render()
            }
        }, "uilang"), 8)
        val reader = Prefs.readerDevice(this)
        add(tagged(ui.settingRow(t("wz_dev"), t(if (reader) "dev_phone" else "dev_tv")) {
            choose(t("wz_dev_q"), listOf(t("dev_tv"), t("dev_phone")), if (reader) 1 else 0) { i -> setReader(i == 1); render(true) }
        }, "device"), 8)
        add(tagged(ui.settingRow(t("s_test"), onOff(Prefs.testChannel(this))) {
            Prefs.setTestChannel(this, !Prefs.testChannel(this)); checkForUpdate(); render(true)
        }, "testch"), 8)
        // telefon, na kterém běží celá aplikace: cesta zpět k pomocníkovi pro přihlášení
        if (NetInfo.isHandheld(this)) add(tagged(ui.button(t("hp_back")) { backToPhoneHelper() }, "tophone"), 8)

        // povolení pruhu potřebuje jen Android TV (u LG/Samsungu ukazuje titulky televize)
        if (type == TvType.ANDROID) {
            content.addView(ui.section(t("s_perm")))
            val a11yOn = TitulkyAccessibility.instance != null
            add(tagged(ui.settingRow(t("s_a11y"), onOff(a11yOn), if (a11yOn) Theme.OK else Theme.WARN) {
                openAccessibility()
            }, "a11y"), 0)
            val ovOn = Settings.canDrawOverlays(this)
            add(tagged(ui.settingRow(t("s_overlay"), onOff(ovOn), if (ovOn) Theme.OK else Theme.MUTED) {
                openOverlaySettings()
            }, "overlay"), 8)
        }
        if (SHOW_LICENSE && Prefs.license(this).canRun) { content.addView(ui.section(t("m_license"))); license() }
    }

    /** Čtení a překlad: kvalita čtení (nad tím, co dělá [translate]). */
    private fun settingsRead() {
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
    }

    /**
     * Demo režim: všechno na vyzkoušení bez konzole na jednom místě (ukázková hra, ukázka pruhu,
     * zkouška překladu) – po správném nastavení to hráč nepotřebuje, proto to není na Domů ani v Titulcích.
     */
    private fun settingsDemo() {
        add(ui.text(t("st_demo_d"), 15f, Theme.MUTED), 0)
        if (TranslatorService.running && TranslatorService.demo) {
            add(tagged(ui.button(t("b_testcard"), primary = true) { startActivity(Intent(this, TestCardActivity::class.java)) }, "demogame"), 12)
            add(tagged(ui.button(t("b_stop")) { stopTranslation() }, "demostop"), 8)
        } else if (!TranslatorService.running) {
            add(tagged(ui.button(t("b_demo"), primary = true) { startDemo() }, "demo"), 12)
        }
        add(tagged(ui.button(t("l_demo")) { showDemo() }, "demobar"), 8)
        add(tagged(ui.button(t("t_test")) { testTranslation() }, "testtr"), 8)
        testResult?.let { add(ui.card().apply { addView(ui.text(it, 15f)) }, 12) }
    }

    /** Ladění a hlášení: diagnostický režim a hlášení o stavu (k vyfocení). */
    private fun settingsDiag() {
        add(tagged(ui.settingRow(t("s_debug"), onOff(Prefs.debug(this))) {
            Prefs.setDebug(this, !Prefs.debug(this)); applyToService(); render(true)
        }, "debug"), 0)
        add(tagged(ui.button(t("rep_btn")) { makeReport() }, "sreport"), 8)
    }

    /**
     * „Zpřístupnit záznam v domácí síti“ pro vývojáře (výchozí vypnuto, samo se vypne), viz NetLogShare.
     * Vlastní blok v části Experimentální pod DeepL.
     */
    private fun logShareRow() = add(NetLogShare.row(this, ui) { render(true) }, 18)

    // ======================= EXPERIMENTÁLNÍ (pokus DeepL, výchozí vypnuto) =======================

    private var deeplUi: DeeplSection? = null

    /** Až na konci Nastavení, ať se nezmění nic nad tím (zkouška se tam prokliká naslepo). */
    private fun experimentalSection() {
        val d = deeplUi ?: DeeplSection(this, ui, { applyToService() }, { render(true) }) { url, text -> qrRow(url, text) }.also { deeplUi = it }
        d.build(content)
        logShareRow()
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
        add(card, 0)
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
        c.addView(ui.text(t("xb_help"), 16f, Theme.MUTED), ui.lp(top = 14))
        add(c, 16)
        content.addView(ui.section(t("help_tips")))
        val c2 = ui.card()
        for (i in 1..4) c2.addView(ui.text("•  " + t("help_t$i"), 16f, Theme.MUTED), ui.lp(top = if (i == 1) 0 else 8))
        if (Prefs.console(this) == Console.PLAYSTATION) c2.addView(ui.text("•  " + t("rp_hide"), 16f, Theme.MUTED), ui.lp(top = 8))
        add(c2, 0)
        // jen část pro zvolený typ televize (ostatní typy jdou zvolit na Domů / v Nastavení)
        when (Prefs.tvType(this)) {
            TvType.LG -> {
                content.addView(ui.section(t("lg_title")))
                add(ui.card().apply { addView(ui.text(t("lg_help"), 15f, Theme.MUTED)) }, 0)
                add(tagged(ui.button(t("lg_open")) { startActivity(Intent(this, LgInstallActivity::class.java)) }, "lghelp"), 8)
            }
            TvType.SAMSUNG -> {
                content.addView(ui.section(t("ss_title")))
                add(ui.card().apply { addView(ui.text(t("ss_help"), 15f, Theme.MUTED)) }, 0)
                add(tagged(ui.button(t("ss_open")) { startActivity(Intent(this, SamsungInstallActivity::class.java)) }, "sshelp"), 8)
            }
            TvType.ANDROID -> {
                content.addView(ui.section(t("adb_title")))
                add(ui.card().apply {
                    isFocusable = true
                    background = ui.focusBg(Theme.CARD)
                    tag = "adb"
                    addView(ui.text(t("adb_text"), 15f, Theme.MUTED))
                    addView(ui.text("adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow", 14f, Theme.ACCENT), ui.lp(top = 8))
                }, 0)
            }
        }
        content.addView(ui.section(t("inst_title")))
        add(ui.card().apply { addView(ui.text(t("inst_text"), 15f, Theme.MUTED)) }, 0)
        // podrobný popis voleb z Nastavení (tam jsou jen názvy)
        content.addView(ui.section(t("help_settings")))
        val c3 = ui.card()
        listOf("s_test" to "s_test_desc", "s_quality" to "q_desc", "t_game" to "t_game_desc",
            "t_dict" to "t_dict_desc", "t_mode" to "t_offline_desc", "dl_toggle" to "dl_desc").forEachIndexed { i, (k, d) ->
            c3.addView(ui.text(t(k), 16f, Theme.TEXT, bold = true), ui.lp(top = if (i == 0) 0 else 14))
            c3.addView(ui.text(t(d), 15f, Theme.MUTED), ui.lp(top = 4))
        }
        add(c3, 0)
        add(tagged(ui.button(t("wz_again")) { wizard = 0; render() }, "wagain"), 16)
        add(tagged(ui.button(t("rep_btn")) { makeReport() }, "report"), 8)
        add(ui.text("PlayLingo · ${BuildInfo.VERSION}", 13f, Theme.MUTED), 24)
    }

    // ======================= KONZOLE (PlayStation / Xbox) =======================

    /** Stránka konzole pro Xbox (přihlášení, výběr Xboxu); stav drží mezi překresleními. */
    private lateinit var xbox: XboxForm

    private fun initXbox() {
        if (!::xbox.isInitialized) xbox = XboxForm(this, ui, { v, top -> add(v, top) }, { row() }, { wrapLp() },
            { url, text -> qrRow(url, text) }, { render(keepFocus = true) })
    }

    /** Text podle vybrané konzole: u Xboxu „xb_<klíč>“, pokud existuje (core/I18nXbox.kt). */
    private fun ct(key: String) = t(Prefs.console(this).key(key))

    /** Jméno spárovaného PlayStationu / vybraného Xboxu, nebo null. */
    private fun consoleName(): String? = when (Prefs.console(this)) {
        Console.PLAYSTATION -> Prefs.ps5Host(this)?.name
        Console.XBOX -> if (XboxAccount.ready(this)) XboxAccount.consoleName(this) else null
    }

    /**
     * Řádek „Konzole: Xbox Series X|S / One · Obývák“ (zeleně, když je připravená); kliknutím se
     * otevře výběr ([chooseConsole]). V Nastavení; na Domů je konzole v souhrnném řádku.
     */
    private fun consoleRow(tag: String, top: Int) {
        val c = Prefs.console(this)
        val value = t(if (c == Console.XBOX) "c_xbox" else "c_ps") + consoleName()?.let { " · $it" }.orEmpty()
        add(tagged(ui.settingRow(t("c_console"), value, if (Prefs.consoleReady(this)) Theme.OK else Theme.WARN) { chooseConsole() }, tag), top)
    }

    /**
     * Výběr konzole (PlayStation 5 / Xbox) v nabídce se seznamem. Po změně se zastaví překlad,
     * který běží s druhou konzolí, a (mimo průvodce) otevře se stránka konzole, když ještě není
     * nastavená. [after] = co udělat po výběru místo překreslení (např. krok průvodce).
     */
    private fun chooseConsole(after: (() -> Unit)? = null) {
        val all = listOf(Console.PLAYSTATION, Console.XBOX)
        val cur = Prefs.console(this)
        choose(t("c_console"), all.map { t(if (it == Console.XBOX) "c_xbox" else "c_ps") }, all.indexOf(cur)) { i ->
            setConsole(all[i])
            if (after != null) after() else render(true)
        }
    }

    /**
     * Uloží vybranou konzoli (nabídka i volba v průvodci). Překlad, který běží s druhou konzolí,
     * se zastaví; mimo průvodce se otevře stránka konzole, když ještě není nastavená.
     */
    private fun setConsole(pick: Console) {
        if (pick == Prefs.console(this)) return
        Prefs.setConsole(this, pick)
        if (TranslatorService.running && !TranslatorService.demo) stopService(Intent(this, TranslatorService::class.java))
        if (wizard < 0 && !Prefs.consoleReady(this)) section = Section.PS5
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
        if (!Prefs.license(this).canRun) { section = Section.SETTINGS; render(); return }
        if (TranslatorService.running && !TranslatorService.demo) { moveTaskToBack(true); return }
        if (!SubtitleOverlay.canShow(this) && !Prefs.tvSend(this)) { toast(t("need_overlay")); section = Section.SETTINGS; render(); return }
        if (!Prefs.consoleReady(this)) { section = Section.PS5; render(); return }
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
        if (!Prefs.license(this).canRun) { section = Section.SETTINGS; render(); return }
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
    const val VERSION = "verze 0.90"
    const val VERSION_CODE = 90
}
