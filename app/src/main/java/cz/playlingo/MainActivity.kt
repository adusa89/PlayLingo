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
        /** Kvalita překladu: jen offline, nebo DeepL s vlastním klíčem (od 0.94). */
        private const val W_TRANSLATE = 4
        private const val W_PERM = 5
        private const val W_PAIR = 6
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
    /**
     * Pevná hlavička nad posuvnou částí (Nastavení: nadpis a záložky zůstávají nahoře, posouvá se jen obsah).
     * Jinde je prázdná a schovaná.
     */
    private lateinit var pinned: LinearLayout
    private var pageScroll: ScrollView? = null
    private var contentTop = 0
    private var section = Section.HOME
    private var demo: SubtitleOverlay? = null
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
    /** Odhad hry z titulků při posledním vykreslení – nový odhad překreslí Domů (oranžový řádek Hra). */
    private var lastGuess: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            // obnov obrazovku, když se změnil stav překladu (např. zastavení z notifikace)
            ticks++
            // stav pro aplikaci v TV (jen LG/Samsung, jen když překlad neběží; posílá se jen změna)
            if (ticks % 2 == 0) TvIdle.ensure(this@MainActivity)
            val typing = currentFocus is EditText
            val changed = lastRunning != TranslatorService.running || lastPaused != TranslatorService.paused ||
                lastLink != TranslatorService.linkState || (lastFrames == 0L) != (TranslatorService.framesDecoded == 0L) ||
                lastStage != TranslatorService.linkStatus().stage ||
                (section == Section.HOME && (lastTv != TranslatorService.tvConnected || lastTvSeen != (TvSender.lastSeen?.at ?: 0L) ||
                    lastGuess != Prefs.gameGuess(this@MainActivity)))
            // náhled obrazu z PlayStationu se obnovuje každé 3 s (ne když uživatel zrovna píše)
            // (jen na stránce Konzole – Domů náhled nemá a překreslovat ho každé 3 s bylo zbytečné)
            val preview = TranslatorService.running && ticks % 3 == 0 && wizard < 0 && section == Section.PS5 && "preview" in openGroups
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
            Prefs.setPs5Host(this, null); Prefs.setPsnAccount(this, "", ""); draftPsn = ""
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
        // po návratu do aplikace se zeptá i na novou verzi (dřív jen při spuštění – nová verze se pak
        // ukázala až po zavření a otevření aplikace); nejvýš jednou za 5 minut
        if (android.os.SystemClock.elapsedRealtime() - lastUpdateCheck > 5 * 60_000L) checkForUpdate()
    }

    override fun onPause() {
        TvIdle.stop()
        UiState.previewWanted = false
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

        pinned = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(10)); visibility = View.GONE }
        root.addView(pinned, ui.lp())
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = ui.vbox().apply { setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(32)) }
        contentTop = ui.dp(18)
        scroll.addView(content)
        pageScroll = scroll
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

        val column = ui.vbox().apply { clipChildren = true }
        pinned = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(48), ui.dp(34), ui.dp(64), ui.dp(10)); visibility = View.GONE }
        column.addView(pinned, ui.lp())
        val scroll = ScrollView(this).apply { isFillViewport = true; clipChildren = false; clipToPadding = false }
        content = ui.vbox().apply { setPadding(ui.dp(48), ui.dp(34), ui.dp(64), ui.dp(48)) }
        contentTop = ui.dp(34)
        scroll.addView(content)
        pageScroll = scroll
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
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
        // stránka pro vložení klíče DeepL z telefonu běží jen, dokud je vidět (Titulky s otevřenou skupinou Kvalita
        // překladu, nebo krok průvodce Lepší překlad)
        if (if (wizard >= 0) wizard != W_TRANSLATE else section != Section.LOOK || lookOpen != LOOK_DEEPL) deeplUi?.stopPage()
        lastRunning = TranslatorService.running
        lastPaused = TranslatorService.paused
        lastLink = TranslatorService.linkState
        lastFrames = TranslatorService.framesDecoded
        lastStage = TranslatorService.linkStatus().stage
        lastTv = TranslatorService.tvConnected
        lastTvSeen = TvSender.lastSeen?.at ?: 0L
        lastGuess = Prefs.gameGuess(this)
        // aplikace v televizi LG/Samsung se může připojit i před Spustit (skutečný stav na Domů)
        TvIdle.ensure(this)
        // barevný náhled hry jen pro stránku Konzole (jinde by se zbytečně počítal každé 1,5 s)
        UiState.previewWanted = wizard < 0 && section == Section.PS5 && "preview" in openGroups
        // pevná hlavička jen v Nastavení (nadpis a záložky), jinde schovaná a obsah s běžným odsazením
        pinned.removeAllViews()
        val pin = wizard < 0 && section == Section.SETTINGS
        pinned.visibility = if (pin) View.VISIBLE else View.GONE
        content.setPadding(content.paddingLeft, if (pin) ui.dp(8) else contentTop, content.paddingRight, content.paddingBottom)
        if (wizard >= 0) { renderWizard(focusedTag); return }
        renderMenu()
        content.removeAllViews()
        // nadpis stránky jen u Historie – ostatní stránky mají název v menu (přání majitele, od 0.97.6)
        if (section == Section.HISTORY) content.addView(ui.text(ct(section.key), 30f, Theme.TEXT, bold = true))
        when (section) {
            Section.HOME -> home()
            Section.PS5 -> ps5()
            // Titulky: nejdřív vzhled, pod ním kde se čtou
            Section.LOOK -> subtitlesPage()
            // Nastavení (⚙): záložky Obecné / Čtení a překlad / Ladění a hlášení / Experimentální
            Section.SETTINGS -> settings()
            Section.HISTORY -> history()
            Section.HELP -> help()
        }
        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>("menu_${menuSection().name}")
        ui.restoringFocus()   // stejné tlačítko znovu vybrané – bez „pulzu“
        target?.requestFocus()
    }

    // ======================= PRŮVODCE PRVNÍM SPUŠTĚNÍM =======================

    /**
     * Názvy kroků průvodce: zařízení, konzole, televize, jazyk překladu, povolení pruhu, spárování
     * konzole, hotovo (indexy W_…). Jednorázové volby jdou později změnit v Nastavení.
     */
    private val wizardSteps = listOf("wz_dev", "c_console", "wz_tv", "wz_s2", "wz_tr_step", "wz_s1", "wz_s3", "wz_s4")

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
        // kolik kroků zbývá (v telefonu není vidět levý seznam kroků)
        content.addView(ui.steps(wizard + 1, wizardSteps.size, steps(wizard + 1, wizardSteps.size)), ui.lp(top = 8))
        content.addView(ui.text(ct(wizardSteps[wizard]), 30f, Theme.TEXT, bold = true), ui.lp(top = 10))

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
                // příprava PlayStationu (druhý účet, Remote Play…) je až v kroku Připojení konzole, kde se hodí
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
                    // v televizi už je aktuální PlayLingo: nic nenabízet (nahrát / opravit jde v ⚙ Nastavení)
                    val current = TvChoice.upToDate(this, type)
                    if (current != null) add(ui.text("✔ " + t("tv_current").replace("%s", current), 16f, Theme.OK, bold = true), 12)
                    else {
                        add(tagged(ui.button(TvChoice.installLabel(this, type)) { TvChoice.openInstall(this, type) }, "wtvinstall"), 10)
                        add(ui.text(t("wz_tv_later"), 14f, Theme.MUTED), 8)
                    }
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
                    // stahování jazykového balíčku (asi 30 MB): pruh běhá, dokud ML Kit neskončí
                    if (!ok && OfflineTranslator.isDownloading(lang)) addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
                }, 12)
                if (!ok) {
                    add(tagged(ui.button(t("download")) { downloadModel() }, "wdl"), 16)
                    nextLabel = t("wz_skip_step")
                }
            }
            // Lepší překlad (od 0.97.28 bez volby DeepL / jen offline): rovnou jedna věta proč, cesta ke klíči
            // (televize: čtverečky, telefon: tlačítka a políčko) a „Čekám na klíč z telefonu…“. Bez klíče je hlavní
            // tlačítko „Přeskočit – zůstat u offline překladu“, po přijetí klíče „✔ DeepL je nastavený“ a Pokračovat.
            W_TRANSLATE -> {
                add(ui.text(t("wz_tr_why"), 17f, Theme.MUTED), 12)
                // klíč uložený z telefonu nebo políčka → DeepL rovnou zapnout
                if (DeeplKey.has(this) && !DeeplKey.enabled(this)) { DeeplKey.setEnabled(this, true); applyToService() }
                deeplSection().build(content, wizard = true)
                if (!DeeplKey.has(this)) nextLabel = t("wz_tr_skip")
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
            }
            else -> {
                add(ui.text(t("wz_done"), 17f, Theme.MUTED), 12)
                add(ui.card().apply {
                    // jak se hraje – jinak u Android TV (přepnout HDMI) a u LG/Samsungu (aplikace v televizi)
                    addView(ui.text(t(if (Prefs.tvType(this@MainActivity) == TvType.ANDROID) "hc6_android" else "hc6_tv"), 16f))
                }, 12)
                add(ui.text(t("wz_done_auto"), 15f, Theme.MUTED), 12)
                if (Prefs.consoleReady(this)) nextLabel = t("b_start")
            }
        }

        val pairing = wizard == W_PAIR && !Prefs.consoleReady(this)
        if (pairing) {
            // Připojení konzole: jen jedna řada tlačítek – ta spárování (Pokračovat / Zpět, u Xboxu Přihlásit)
            // a pod ní malé „Spárovat později“ (přeskočí na Hotovo). U Xboxu, který vlastní Zpět nemá, i malé Zpět.
            val later = row()
            if (Prefs.console(this) == Console.XBOX) later.addView(tagged(ui.smallButton(t("wz_back")) { wizard--; render() }, "wback"))
            later.addView(tagged(ui.smallButton(t("wz_pair_later")) { wizard = wizardSteps.size - 1; render() }, "wlater"),
                if (later.childCount > 0) wrapLp() else ui.lp())
            add(later, 20)
        } else {
            val row = row()
            row.addView(tagged(ui.button(nextLabel, primary = true) {
                if (wizard < wizardSteps.size - 1) { wizard++; render() }
                else {
                    val paired = Prefs.consoleReady(this)
                    finishWizard()
                    if (paired) startFlow()
                }
            }, "wnext"))
            if (wizard > 0) row.addView(tagged(ui.button(t("wz_back")) { wizard--; render() }, "wback"), wrapLp())
            // Lepší překlad: jen Přeskočit/Pokračovat a Zpět (průvodce se tu nepřeskakuje celý)
            if (wizard != W_TRANSLATE) row.addView(tagged(ui.button(t(if (wizard == wizardSteps.size - 1) "wz_finish" else "wz_skip")) { finishWizard() }, "wskip"), wrapLp())
            add(row, 28)
        }
        if (NetInfo.isHandheld(this)) add(tagged(ui.button(t("hp_back")) { backToPhoneHelper() }, "tophone"), 16)

        val target = focusedTag?.let { window.decorView.findViewWithTag<View>(it) }
            ?: window.decorView.findViewWithTag<View>(if (pairing) "pnext" else "wnext")
        ui.restoringFocus()   // stejné tlačítko znovu vybrané – bez „pulzu“
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
            // podrobnosti (spojení, obraz, síť, rychlost…) jsou v bodech pod nadpisem, ne v textu
            running && link == Ps5.Link.CONNECTED -> { title = t("st_running"); sub = ""; color = Theme.OK }
            running && link == Ps5.Link.FAILED -> {
                title = ct("m_ps5")
                sub = if (onXbox) t(TranslatorService.linkReason ?: "xb_st_failed")
                    else t(if (Ps5Link.needsLoginPin) "ps5_login_fail" else if (Ps5Link.reason == "unstable") "st_unstable" else "st_failed")
                color = Theme.BAD
            }
            running && link == Ps5.Link.WAITING -> { title = ct("m_ps5"); sub = ct("st_waiting"); color = Theme.WARN }
            running -> { title = ct("m_ps5"); sub = ct("st_connecting"); color = Theme.WARN }
            // LG / Samsung: připraveno je až s připojenou aplikací v televizi (spojení drží TvIdle i před Spustit)
            ready && Prefs.tvSend(this) && TranslatorService.tvConnected <= 0 -> { title = t("st_wait_tv"); sub = ""; color = Theme.WARN }
            ready -> { title = t("st_ready"); sub = ""; color = Theme.ACCENT }
            // co chybí, ukazují body pod nadpisem – bez dalšího textu
            else -> { title = t("st_setup"); sub = ""; color = Theme.WARN }
        }
        if (running && !demoRun && link != Ps5.Link.CONNECTED && !(onXbox && link == Ps5.Link.FAILED))
            TranslatorService.linkReason?.let { sub += "\n(" + (if (onXbox) t(it) else it) + ")" }
        val status = ui.card()
        val head = ui.hbox()
        head.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
            LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)).apply { rightMargin = ui.dp(14) })
        head.addView(ui.text(title, 24f, Theme.TEXT, bold = true))
        status.addView(head)
        (busyText ?: sub).takeIf { it.isNotEmpty() }?.let { status.addView(ui.text(it, 16f, Theme.MUTED), ui.lp(top = 6)) }
        if (busyText != null && OfflineTranslator.isDownloading(Prefs.lang(this))) status.addView(ui.progress(null), ui.lp(h = ui.dp(10), top = 8))
        // přehled v bodech: zelená tečka = jede, oranžová = potřebuje pozornost, šedá = jen informace
        statusRows(status, running, demoRun, overlayOk, modelOk, paired)
        add(status, 18)
        if (crashNote) add(ui.card().apply {
            addView(ui.text(t("cr_note") + " " + t("m_help") + " → " + t("rep_btn"), 15f, Theme.WARN))
        }, 12)

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
                // „Zpět do hry (HDMI)“ dává smysl jen na Android TV / boxu – telefon jako čtečka do hry nepřepíná
                if (Prefs.isTv(this)) {
                    buttons.addView(tagged(ui.button(t("b_go_game"), primary = true) { moveTaskToBack(true) }, "start"))
                    buttons.addView(tagged(ui.button(t(if (paused) "b_resume" else "b_pause")) { togglePause() }, "pause"), wrapLp())
                } else {
                    buttons.addView(tagged(ui.button(t(if (paused) "b_resume" else "b_pause"), primary = true) { togglePause() }, "start"))
                }
            }
            buttons.addView(tagged(ui.button(t("b_stop")) { stopTranslation() }, "stop"), wrapLp())
        }
        add(buttons, 18)

        // upozornění, která nejsou v přehledu (končící vývojářský režim LG, licence)
        warnings()
    }

    /**
     * Přehled na Domů v bodech (od 0.97, místo textu s návody a samostatného řádku „PS5 → televize“):
     * konzole a obraz, televize (u Android TV pruh přes obraz), síť, rychlost čtení, překlad, šetření
     * baterie (telefon jako čtečka), verze. Řádek, který potřebuje zásah, má rovnou tlačítko.
     * Konzole a televize jdou změnit OK na jejich řádku ([chooseConsole], [TvChoice.show]).
     */
    private fun statusRows(card: LinearLayout, running: Boolean, demoRun: Boolean, overlayOk: Boolean, modelOk: Boolean, paired: Boolean) {
        val lang = Prefs.uiLang(this)
        val type = Prefs.tvType(this)
        var rows = 0
        fun row(color: Int, text: String, tag: String, action: String? = null, onAction: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
            val r = ui.hbox().apply {
                setPadding(ui.dp(6), ui.dp(7), ui.dp(6), ui.dp(7))
                if (onClick != null) {
                    isFocusable = true; isClickable = true
                    background = ui.focusBg(Theme.CARD, radius = 10)
                    setOnClickListener { onClick() }
                }
                addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
                    LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)).apply { rightMargin = ui.dp(12) })
                addView(ui.text(text, 16f, if (color == Theme.MUTED) Theme.MUTED else Theme.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            card.addView(tagged(r, tag), ui.lp(top = if (rows++ == 0) 10 else 0))
            if (action != null && onAction != null) {
                // malé žluté tlačítko (běžné tlačítko má barvu karty a v kartě splývalo s textem)
                card.addView(tagged(ui.smallButton(action) { onAction() }, tag + "_fix"),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = ui.dp(30); bottomMargin = ui.dp(4) })
            }
        }
        // konzole a obraz
        val console = Prefs.console(this)
        // krátce jako ostatní řádky: „PS5-151 · připojeno“ (jméno konzole, jinak PS5 / Xbox)
        val conName = consoleName() ?: if (console == Console.XBOX) "Xbox" else "PS5"
        if (running && !demoRun) {
            val ls = TranslatorService.linkStatus()
            val (color, state) = if (console == Console.PLAYSTATION && Ps5Link.asleep) Theme.WARN to t("h_con_sleep") else when (ls.stage) {
                cz.playlingo.core.LinkStatus.Stage.IMAGE -> Theme.OK to t("tv_st_on")
                cz.playlingo.core.LinkStatus.Stage.STALLED, cz.playlingo.core.LinkStatus.Stage.NO_IMAGE -> Theme.WARN to t("h_con_noimg")
                cz.playlingo.core.LinkStatus.Stage.FAILED -> Theme.WARN to t("h_con_fail")
                cz.playlingo.core.LinkStatus.Stage.PAUSED -> Theme.MUTED to t("st_paused").lowercase()
                else -> Theme.MUTED to t("h_con_wait")
            }
            row(color, "$conName · $state", "linkline", onClick = { chooseConsole() })
            if (ls.stage == cz.playlingo.core.LinkStatus.Stage.CONNECTING || ls.stage == cz.playlingo.core.LinkStatus.Stage.RETRYING ||
                ls.stage == cz.playlingo.core.LinkStatus.Stage.WAIT_IMAGE) card.addView(ui.progress(null), ui.lp(h = ui.dp(8), top = 2))
            // spojení ukončil sám PlayStation: jiné zařízení se stejným účtem, nebo jeho vlastní důvod
            if (!ls.console && console == Console.PLAYSTATION) {
                val refusal = Ps5Link.refusal
                if (Ps5Link.otherDevice || refusal == Ps5.Refusal.IN_USE) card.addView(ui.text(t("ls_other_device"), 14f, Theme.WARN), ui.lp(top = 2))
                // PS5 odmítla spojení ještě před obrazem: místo anglické hlášky knihovny co s tím (od 0.97.31)
                // i neznámý důvod má radu: nejčastěji chybí „Povolit Remote Play“ pod účtem pro PlayLingo (kolega majitele,
                // 20. 11. 2026 – PS5 to hlásí obecným kódem, knihovna jen „Unknown Session Request Error“)
                else if (refusal != null) {
                    card.addView(ui.text(t("ps5_ref_" + refusal.name.lowercase()).replace("%s", accName()), 14f, Theme.WARN), ui.lp(top = 2))
                    if (refusal == Ps5.Refusal.ACCOUNT) card.addView(tagged(ui.smallButton(t("ps5_ref_repair")) { section = Section.PS5; render() }, "hrepair"), ui.lp(top = 6))
                }
                else Ps5Link.endedByConsole?.let { why -> card.addView(ui.text(t("ls_ended").replace("%s", why), 14f, Theme.WARN), ui.lp(top = 2)) }
            }
        } else {
            row(if (paired) Theme.OK else Theme.WARN, conName + " · " + t(if (paired) "h_con_ready" else "sum_nopair"), "hcon", onClick = { chooseConsole() })
        }
        // televize
        if (type == TvType.ANDROID) {
            if (overlayOk) row(Theme.OK, t("h_bar_ok"), "htv", onClick = { TvChoice.show(this) { render(true) } })
            else row(Theme.WARN, t("w_overlay"), "htv", t("fix"), { settingsTab = SettingsTab.GENERAL; section = Section.SETTINGS; render() })
        } else {
            // jen spojení: „LG OLED55B7 · připojeno“ zeleně, jinak „nepřipojeno“ oranžově (verze aplikace má vlastní řádek)
            val (tvLine, connected) = TvChoice.short(this)
            row(if (connected) Theme.OK else Theme.WARN, tvLine, "htv", onClick = { TvChoice.show(this) { render(true) } })
        }
        // síť tohoto zařízení – jen věcně, bez varování (vysvětlení k výpadkům je v Nápovědě)
        when (NetInfo.kind(this)) {
            Ps5.Net.CABLE -> row(Theme.OK, t("h_net_cable"), "hnet")
            Ps5.Net.WIFI_5 -> row(Theme.OK, t("h_net_5"), "hnet")
            Ps5.Net.WIFI_24 -> row(Theme.OK, t("h_net_24"), "hnet")
            Ps5.Net.UNKNOWN -> {}
        }
        // rychlost čtení (o kolik se titulek opozdí za obrazem)
        if (running) TranslatorService.readMs.takeIf { it > 0 }?.let { ms ->
            row(if (ms < 400) Theme.OK else Theme.WARN, t("h_speed").replace("%d", ms.toString()) +
                TranslatorService.videoSize.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty(), "hspeed")
        }
        // překlad: jazyk, offline / DeepL, chybějící balíček
        val tl = Prefs.lang(this)
        if (!modelOk) row(Theme.WARN, t("w_model").replace("%s", tl.label), "hmodel", t("download"), { downloadModel() })
        else row(Theme.OK, tl.label + " · " + t(if (DeeplTranslator.active(this)) "dl_mode_on" else "h_offline"), "hmodel", onClick = { openQuality() })
        // hra: vybraný profil zeleně; titulky ukazují na jinou hru → oranžově s Přepnout (GameGuess, nic se nepřepíná samo).
        // S univerzálním profilem a bez odhadu řádek není.
        val game = GameProfiles.of(Prefs.gameId(this))
        val guess = Prefs.gameGuess(this)?.let { GameProfiles.of(it) }
        // vybraná hra zůstává vidět, odhad je za ní s otazníkem: „Call of Duty · Grand Theft Auto VI?“
        if (guess != null) row(Theme.WARN, (if (game.id != "all") game.title + " · " else "") + guess.title + "?", "hgame", t("gp_switch"),
            { Prefs.setGameId(this, guess.id); applyToService(); render(true) }, onClick = { pickGame() })
        else if (game.id != "all") row(Theme.OK, game.title, "hgame", onClick = { pickGame() })
        // verze PlayLinga: aktuální zeleně, novější ke stažení oranžově s tlačítkem Aktualizovat v řádku
        val me = "PlayLingo ${BuildInfo.VERSION.removePrefix("verze ")}"
        val o = update
        if (o == null) row(Theme.OK, me, "hver")
        else {
            val st = Updater.state
            row(Theme.WARN, me + " · " + t("h_ver_new").replace("%s", o.remote.name), "hver",
                if (Updater.busy) null else t("upd_btn"), { startUpdate(o) })
            val line = when (st) {
                Updater.State.DOWNLOADING -> t("upd_down") + (if (Updater.percent >= 0) " ${Updater.percent} %" else "…")
                Updater.State.INSTALLING -> t("upd_inst")
                Updater.State.FAILED -> t(Updater.problem ?: "upd_fail")
                else -> null
            }
            line?.let { card.addView(ui.text(it, 14f, if (st == Updater.State.FAILED) Theme.BAD else Theme.MUTED), ui.lp(top = 2)) }
            if (st == Updater.State.DOWNLOADING) card.addView(ui.progress(if (Updater.percent >= 0) Updater.percent / 100f else null), ui.lp(h = ui.dp(8), top = 4))
            if (st == Updater.State.INSTALLING) card.addView(ui.progress(null), ui.lp(h = ui.dp(8), top = 4))
            // když to z aplikace nejde, zbývá ruční cesta – adresa souboru
            if (st == Updater.State.FAILED) card.addView(ui.text(UpdateCheck.url(o.channel, "PlayLingo.apk"), 13f, Theme.MUTED), ui.lp(top = 4))
        }
        // aplikace v televizi LG/Samsung: stejně – chybí / starší oranžově s tlačítkem nahrání, aktuální zeleně
        if (type.sends) {
            val tvName = if (type == TvType.LG) "LG" else "Samsung"
            val bundled = TvChoice.bundledVersion(this, type)
            val old = TvChoice.outdated(this, type)
            val current = TvChoice.upToDate(this, type)
            when {
                TvChoice.needsInstall(this, type) -> row(Theme.WARN, t("h_tv_app_none").replace("%s", tvName), "htvapp", t("w_install"), { TvChoice.openInstall(this, type) })
                // neznámá verze v TV (aplikace před 1.1.0) = bez čísla, ne „?“ (přání majitele, 0.97.30)
                old != null -> row(Theme.WARN, "PlayLingo " + t("h_ver_tv").replace("%s", if (old.first == "?") tvName else "$tvName ${old.first}") +
                    " · " + t("h_ver_new").replace("%s", old.second), "htvapp", t("w_install"), { TvChoice.openInstall(this, type) })
                current != null -> row(Theme.OK, "PlayLingo " + t("h_ver_tv").replace("%s", "$tvName $current"), "htvapp")
                bundled != null -> row(Theme.MUTED, "PlayLingo " + t("h_ver_tv").replace("%s", tvName), "htvapp")
            }
        }
    }

    /** Smí PlayLingo běžet i při zhasnutém displeji (bez omezení šetřením baterie)? */
    private fun batteryUnrestricted(): Boolean = try {
        (getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Throwable) { true }

    /** Systémový dotaz „Povolit běh na pozadí bez omezení?“; když ho systém nemá, seznam aplikací. */
    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryUnrestricted() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
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
    private fun warnings() {
        val card = ui.card()
        fun warn(v: View) = card.addView(v, ui.lp(top = if (card.childCount == 0) 0 else 4))
        fun note(s: String) = warn(ui.text(s, 15f, Theme.WARN, bold = true).apply { setPadding(0, ui.dp(8), 0, ui.dp(8)) })
        val type = Prefs.tvType(this)
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
        val snap = TranslatorService.snapshot ?: run { add(ui.progress(null), 8); return }
        val w = ui.dp(420)
        val h = w * snap.height / maxOf(1, snap.width)
        content.addView(ImageView(this).apply {
            setImageBitmap(snap)
            scaleType = ImageView.ScaleType.FIT_XY
        }, LinearLayout.LayoutParams(w, h))
        // náhled je poslední snímek – když obraz zrovna nechodí, je starý (nebo černý); říct proč
        val ls = TranslatorService.linkStatus()
        if (!ls.image) add(ui.text(t("pv_stale") + " " + cz.playlingo.core.LinkStatus.line(Prefs.uiLang(this), ls, Prefs.console(this)), 14f, Theme.WARN), 6)
        // Android TV: z náhledu rovnou zpět do hry (kolega majitele zůstal v aplikaci a nevěděl jak ven, 20. 11. 2026);
        // náhled se počítá jen při rozbalené skupině a vidět aplikaci – sbalením nebo odchodem se zastaví
        if (Prefs.isTv(this)) add(tagged(ui.smallButton(t("b_go_game")) { moveTaskToBack(true) }, "pvgame"), 10)
    }

    // ======================= PLAYSTATION =======================

    private fun ps5() {
        ps5Form()
        // náhled obrazu jen po rozbalení – jinak se snímek z konzole vůbec nepočítá (od 0.97.15)
        if (TranslatorService.running && !TranslatorService.demo && group("preview", ct("ps5_preview"), 18)) preview()
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
            xbox.render(null); return   // Spustit jen na Domů
        }
        val host = Prefs.ps5Host(this)
        if (draftLogin.isEmpty()) draftLogin = Prefs.ps5LoginPin(this)
        if (host != null) {
            val c = ui.card()
            c.addView(ui.text(t("ps5_paired") + ": " + host.name, 20f, Theme.OK, bold = true))
            c.addView(ui.text(host.ip, 15f, Theme.MUTED), ui.lp(top = 4))
            add(c, 16)
            pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
        // hledání PS5, ověřování účtu, párování: pruh běhá, ať je vidět, že se něco děje
        if (pairBusy) add(ui.progress(null), 8)
            // Spustit je jen na Domů (telefon i Android TV) – na stránce Konzole bylo podruhé (od 0.97.13)
            // přihlašovací kód účtu na PS5 jen tehdy, když ho PlayStation chtěl (nebo už je zadaný)
            if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 12)
            add(tagged(ui.button(t("ps5_forget")) {
                if (TranslatorService.running && !TranslatorService.demo) stopService(Intent(this, TranslatorService::class.java))
                EventFile.add("PAIR|zrušeno spárování " + host.name); Prefs.setPs5Host(this, null); pairStep = 0; pairMsg = null; render(true)
            }, "pforget"), 12)
            return
        }
        if (draftIp.isEmpty()) draftIp = Prefs.ps5Ip(this)
        if (draftPsn.isEmpty()) draftPsn = Prefs.psnId(this)

        // ---- průvodce spárováním: šest malých kroků, na každé obrazovce jedna věc (pořadí od 0.97.28) ----
        // Index → pořadí na obrazovce: 0 zapnout PS5 (1), 1 najít PS5 (2), 7 účet pro PlayLingo (3), 2–4 přihlášení
        // tím účtem (4; 4 = záloha opsáním kódu), 6 nastavení na PS5 pod tím uživatelem (5), 5 kód z PS5 (6).
        // Účet se volí PŘED nastavením na PS5 a jeho jméno je vidět až do konce: PS5 spáruje jen, když osmimístné
        // číslo vyvolá stejný uživatel, jakým se aplikace přihlásila (jinak CE-110032-7). Indexy 0–6 drží zkouška.
        val step = pairStep.coerceIn(0, 7)
        if (step != 5) pairFailed = false
        val have = Ps5.parseAccountId(draftPsn) != null
        val name = accName()
        val key = when (step) { 0 -> "pg_0"; 1 -> "pg_2"; 2 -> "pa_1"; 3 -> "pa_2"; 4 -> "pa_3"; 6 -> "pg_1"; 7 -> "pg_acc"; else -> "pg_4" }
        val pos = when (step) { 0 -> 1; 1 -> 2; 7 -> 3; 2, 3, 4 -> 4; 6 -> 5; else -> 6 }
        // kolikátý krok spárování: v průvodci jen malý podtitul (nahoře už je „Krok 7 z 8“), jinde pruh na dílky
        if (wizard == W_PAIR) add(tagged(ui.text(t("pg_sub").replace("%1", pos.toString()).replace("%2", "6"), 14f, Theme.MUTED), "psub"), 10)
        else add(ui.steps(pos, 6, steps(pos, 6)), 16)
        add(ui.text(t("${key}_title"), 22f, Theme.TEXT, bold = true), 2)
        var nextOk = true
        var nextLabel = t("wz_next")
        when (step) {
            0 -> add(ui.card().apply { addView(ui.text(t("pg_0_text"), 17f, Theme.TEXT)) }, 10)
            1 -> {
                add(ui.card().apply { addView(ui.text(t("pg_2_text"), 17f, Theme.TEXT)) }, 10)
                add(input("ps5_ip", draftIp, "pip", number = true).also { IpField.setup(it) }, 10)
                add(tagged(ui.button(t("ps5_find")) { findPs5() }, "pfind"), 8)
                // adresu aplikace většinou najde sama; kamera jen když hledání neuspělo (telefon s kamerou)
                if (ps5NotFound && LgScanActivity.available(this)) add(tagged(ui.button(t("km_scan_ps5ip")) { scanPs5Code(true) }, "pscanip"), 8)
            }
            // účet pro PlayLingo: druhý účet, na kterém se nehraje. Uložený se nikdy nepoužije potichu.
            7 -> {
                add(ui.card().apply { addView(ui.text(t("pg_acc_text"), 17f, Theme.TEXT)) }, 10)
                if (have) nextLabel = t("pg_acc_use").replace("%s", name)
                else {
                    // účty ověřené v tomhle telefonu (PlayLingo v telefonu jako čtečka) – se jmény, k výběru
                    Prefs.phoneAccounts(this).forEachIndexed { i, a ->
                        add(tagged(ui.button(t("pg_acc_use").replace("%s", a.name.ifEmpty { t("pg_acc_unknown") })) {
                            saveDrafts(); useAccount(a.id, a.name); pairStep = 6; pairMsg = null; render()
                        }, "pacc$i"), if (i == 0) 12 else 8)
                    }
                    nextLabel = t("pg_acc_login")
                }
            }
            2 -> {
                // telefon: velký QR kód s adresou stránky, kterou televize vystavuje v domácí síti
                // (když se ji vystavit nepodařilo, vede kód rovnou na přihlášení Sony)
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
            3 -> add(ui.card().apply { addView(ui.text(t("pa_2_text"), 17f, Theme.TEXT)) }, 10)
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
            // předposlední krok: na PS5 přepnout na uživatele s tímhle jménem a v Nastavení → Systém → Remote Play
            6 -> {
                accountLine(name, other = true)
                add(ui.card().apply { addView(ui.text(t("pg_1_text").replace("%s", name), 17f, Theme.TEXT)) }, 10)
            }
            else -> {
                accountLine(name, other = !pairFailed)
                add(ui.card().apply { addView(ui.text(t("pg_4_text").replace("%s", name), 17f, Theme.TEXT)) }, 10)
                add(input("ps5_pin", draftPin, "ppin", number = true), 10)
                // telefon s kamerou: osmimístné číslo přečíst z obrazovky PlayStationu
                if (LgScanActivity.available(this)) add(tagged(ui.button(t("km_scan_ps5")) { scanPs5Code() }, "pscan"), 8)
                if (loginPinWanted()) add(input("ps5_login", draftLogin, "plogin", number = true), 8)
                nextOk = false
            }
        }
        pairMsg?.let { add(ui.text(it, 15f, pairMsgColor), 10) }
        // hledání PS5, ověřování účtu, párování: pruh běhá, ať je vidět, že se něco děje
        if (pairBusy) add(ui.progress(null), 8)
        val row = row()
        when {
            nextOk -> row.addView(tagged(ui.button(nextLabel, primary = true) { pairNext() }, "pnext"))
            step == 4 -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_acc_wait" else "ph_check"), primary = true) { if (!pairBusy) checkPhoneCode() }, "pnext"))
            else -> row.addView(tagged(ui.button(t(if (pairBusy) "ps5_pairing" else if (pairFailed) "pg_retry" else "ps5_pair"), primary = true) { if (!pairBusy) pairPs5() }, "pnext"))
        }
        // jiný účet: v kroku 3 místo uloženého, po chybě spárování hned vedle „Zkusit znovu“
        if ((step == 7 && have) || (step == 5 && pairFailed && !pairBusy)) row.addView(tagged(ui.button(t("pg_acc_other")) { otherAccount(step) }, "pother"), wrapLp())
        if (step > 0) row.addView(tagged(ui.button(t("wz_back")) {
            saveDrafts()
            pairStep = when (step) {
                5 -> 6        // z kódu zpět na nastavení PS5
                6 -> 7        // z nastavení PS5 zpět na volbu účtu (přihlášení se neopakuje)
                7 -> 1        // z volby účtu zpět na hledání PS5
                2 -> 7        // z přihlášení zpět na volbu účtu
                else -> step - 1
            }
            pairMsg = null; render()
        }, "pback"), wrapLp())
        // první krok spárování v průvodci: Zpět vede na předchozí krok průvodce (jedna řada tlačítek)
        else if (wizard == W_PAIR) row.addView(tagged(ui.button(t("wz_back")) { wizard--; render() }, "pback"), wrapLp())
        // stránka z televize se v telefonu neotevřela → rovnou na záložní krok se čtverečkem na Sony
        if (step == 2) row.addView(tagged(ui.button(t("pa_stuck")) {
            saveDrafts()
            pairStep = 4; pairMsg = null; render()
        }, "pstuck"), wrapLp())
        add(row, 14)
        // krok s účtem: jak založit druhý účet (i do iPhonu) – pod tlačítky, ať ta zůstanou vidět
        if (step == 7) add(helpLink(cz.playlingo.core.HelpLinks.PS5_PREP, t("help_ps5")), 14)
    }

    /** Spárování se nepovedlo – nabídnout „Zkusit znovu“ a „Přihlásit jiný účet“ (CE-110032-7). */
    private var pairFailed = false

    /** Jméno ověřeného účtu PlayStation (Online ID); u účtů uložených před 0.97.28 neznámé. */
    private fun accName(): String = Prefs.psnName(this).ifEmpty { t("pg_acc_unknown") }

    /** Ověřený účet: uložit číslo i jméno najednou (jméno se ukazuje v dalších krocích a v hlášce o spárování). */
    private fun useAccount(id: String, name: String) {
        Prefs.setPsnAccount(this, id, PhoneLogin.cleanName(name))
        draftPsn = id; draftAuth = ""
    }

    /** Řádek „Ověřený účet: JMÉNO“ v krocích 5 a 6, s malým tlačítkem „Přihlásit jiný účet“. */
    private fun accountLine(name: String, other: Boolean) {
        val line = row()
        line.addView(ui.text("✔ " + t("pg_acc_done").replace("%s", name), 16f, Theme.OK, bold = true).apply { gravity = Gravity.CENTER_VERTICAL })
        if (other && !pairBusy) line.addView(tagged(ui.smallButton(t("pg_acc_other")) { otherAccount(pairStep) }, "pother"), wrapLp())
        add(line, 10)
    }

    /** Zapomene ověřený účet i jméno a vrátí na volbu účtu (z ní, když v telefonu žádný uložený není, rovnou na přihlášení). */
    private fun otherAccount(from: Int) {
        saveDrafts()
        draftPsn = ""; draftAuth = ""; draftPin = ""
        Prefs.setPsnAccount(this, "", "")
        pairFailed = false
        pairStep = if (from == 7 && Prefs.phoneAccounts(this).isEmpty()) 2 else 7
        pairMsg = null; render()
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
        when (pairStep.coerceIn(0, 7)) {
            0 -> { pairStep = 1; pairMsg = null; render(); if (draftIp.isEmpty()) findPs5() }
            1 -> {
                if (!Ps5.validHost(draftIp)) { pairNote(t("ps5_bad_ip"), Theme.BAD); return }
                Prefs.setPs5Ip(this, draftIp)
                pairStep = 7; pairMsg = null; render()
            }
            // účet pro PlayLingo: uložený potvrzený tlačítkem „Použít účet JMÉNO“, jinak přihlášení
            7 -> { pairStep = if (have) 6 else 2; pairMsg = null; render() }
            2 -> { pairStep = if (have) 6 else 3; pairMsg = null; render() }
            3 -> { pairStep = 4; pairMsg = null; render() }
            6 -> { pairStep = 5; pairMsg = null; render() }
        }
    }

    /** Stránka pro telefon běží jen po dobu, kdy průvodce ukazuje kroky přihlášení účtem (2 až 4). */
    private fun syncLoginPage() {
        val pairing = Prefs.console(this) == Console.PLAYSTATION && Prefs.ps5Host(this) == null && (wizard == W_PAIR || (wizard < 0 && section == Section.PS5))
        val want = pairing && pairStep.coerceIn(0, 7) in 2..4
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

    /**
     * Telefon poslal ověřený účet (ze stránky nebo z PlayLinga v telefonu) → nastavení na PS5 pod tímhle
     * uživatelem (jméno je pak vidět v řádku „Ověřený účet: JMÉNO“ až do spárování).
     */
    private fun accountFromPhone(a: PhoneLogin.Account) {
        pairNote(null, Theme.OK) { useAccount(a.id, a.name); pairStep = 6 }
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

    /**
     * Odkaz na stránku s pomocí ([cz.playlingo.core.HelpLinks]): v telefonu tlačítko do prohlížeče,
     * na televizi čtvereček pro telefon (i iPhone).
     */
    private fun helpLink(url: String, label: String): View {
        if (narrow) return ui.button(label) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        }
        return qrRow(url, label + "\n" + t("help_qr_note"))
    }

    /** Kód opsaný z telefonu → číslo účtu (dotaz jde přímo na server Sony, ne přes přihlašovací stránku). */
    private fun checkPhoneCode() {
        saveDrafts()
        val code = Ps5.authCodeFromTyped(draftAuth)
        if (code == null) { pairNote(t("ph_bad"), Theme.BAD); return }
        pairNote(t("ps5_acc_wait"), busy = true)
        Thread({
            // číslo účtu i jméno (Online ID z odpovědi Sony o účtu)
            val acc = PsnAuth.accountForCode(code)
            if (acc != null) pairNote(null, Theme.OK) { useAccount(acc.id, acc.name); pairStep = 6 }
            else pairNote(t("ph_bad"), Theme.BAD)
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
        if (account == null) { pairStep = 7; pairNote(t("pg_need_acc"), Theme.BAD); return }
        val name = accName()
        pairNote(t("ps5_pairing"), busy = true)
        Thread({
            Ps5Pairing.pair(ip, account, pin) { h: Ps5Host? ->
                // do záznamu (i z minulých spuštění): kdy a jakým účtem se párovalo – opakované párování je vidět v hlášení
                EventFile.add("PAIR|" + (if (h != null) "spárováno ${h.name} ${h.ip} …${Ps5.hex(h.mac).takeLast(4)}" else "nepovedlo se ($ip)") +
                    " · účet $name" + (Ps5Pairing.lastError?.takeIf { h == null }?.let { " · $it" } ?: ""))
                if (h != null) {
                    Prefs.setPs5Host(this, h)
                    pairNote(t("ps5_ok").replace("%s", name), Theme.OK) { draftPin = ""; pairStep = 0; pairFailed = false }
                } else {
                    // nejčastější příčina: číslo vyvolal na PS5 jiný uživatel než účet v aplikaci (CE-110032-7)
                    val why = t("ps5_fail") + "\n" + t("ps5_fail_user").replace("%s", name) + (Ps5Pairing.lastError?.let { "\n($it)" } ?: "")
                    pairNote(why, Theme.BAD) { pairFailed = true; draftPin = "" }
                }
            }
        }, "tp-pair").start()
    }

    // ======================= OBLASTI =======================

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

    private fun gameTitle(g: cz.playlingo.core.GameProfile) = if (g.id == "all") t("game_universal") else g.title

    /** Výběr profilu hry (Nastavení → Hra i řádek Hra na Domů). */
    private fun pickGame() {
        val game = GameProfiles.of(Prefs.gameId(this))
        val games = GameProfiles.ALL
        choose(t("gp_profile"), games.map { gameTitle(it) }, games.indexOf(game)) { i ->
            Prefs.setGameId(this, games[i].id); applyToService(); render(true)
        }
    }

    /** Rozbalený seznam postav, míst a pojmů profilu hry. */
    private var gameListOpen = false

    /**
     * Hra (Nastavení → Čtení a překlad): profil hry, nabídka hry odhadnuté z titulků (nic se nepřepíná samo)
     * a rozbalovací seznam toho, co profil zná – postavy s rodem, jména a místa, pojmy, přeskakované nápisy.
     */
    private fun gameGroup() {
        val game = GameProfiles.of(Prefs.gameId(this))
        add(tagged(ui.settingRow(t("gp_profile"), gameTitle(game)) { pickGame() }, "game"), 8)
        Prefs.gameGuess(this)?.let { id ->
            val g = GameProfiles.of(id)
            add(tagged(ui.settingRow(g.title + "?", t("gp_switch") + " ›", Theme.WARN) {
                try {
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(g.title + "?").setMessage(String.format(t("gp_guess_desc"), g.title))
                        .setPositiveButton(t("gp_switch")) { d, _ -> d.dismiss(); Prefs.setGameId(this, g.id); applyToService(); render(true) }
                        .setNegativeButton(t("gp_keep")) { d, _ -> d.dismiss(); Prefs.dismissGameGuess(this); render(true) }
                        .show()
                } catch (_: Exception) {}
            }, "gameguess"), 8)
        }
        // rozsah čtení: jen titulky, nebo i texty hry z profilu (prompty v GTA…)
        if (game.id != "all") add(tagged(ui.settingRow(t("gp_scope"), t(if (Prefs.gameTexts(this)) "gp_scope_all" else "gp_scope_subs")) {
            Prefs.setGameTexts(this, !Prefs.gameTexts(this)); applyToService(); render(true)
        }, "gamescope"), 8)
        add(tagged(ui.settingRow(t("gp_list"), if (gameListOpen) "▲" else "▼") { gameListOpen = !gameListOpen; render(true) }, "gamelist"), 8)
        if (!gameListOpen) return
        add(ui.text(t("t_game_desc") + " " + t("t_game_deepl"), 14f, Theme.MUTED), 8)
        if (game.id == "all") {
            add(ui.text(String.format(t("gp_universal"), game.names.size, game.characters.size), 15f), 8)
            return
        }
        fun block(head: String, items: List<String>) {
            if (items.isEmpty()) return
            add(ui.text(head + " (" + items.size + ")", 16f, Theme.ACCENT, bold = true), 12)
            add(ui.text(items.joinToString(" · "), 15f), 4)
        }
        block(t("gp_chars"), game.characters.map { c ->
            c.name + (if (c.gender == cz.playlingo.core.Gender.F) " ♀" else " ♂") +
                (c.aliases.filter { !c.name.contains(it) }.takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")") ?: "")
        })
        val inCast = game.characters.flatMap { it.allNames }.toSet()
        block(t("gp_names"), game.names.filter { it !in inCast })
        block(t("gp_terms"), game.termsFor(Prefs.lang(this)).map { (en, tr) -> "$en → $tr" })
        block(t("gp_skip"), game.hud + game.menu)
    }

    /** Zvolení tlačítka rychlé nabídky vzhledu: dialog čeká na stisk tlačítka na ovladači. */
    private fun learnQuickKey() {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("qk_row")).setMessage(t("qk_press"))
                .setNegativeButton(t("wz_back")) { d, _ -> d.dismiss() }
                .setNeutralButton(t("off")) { d, _ -> d.dismiss(); Prefs.setQuickKey(this, 0); TitulkyAccessibility.instance?.updateKeyFilter(false); render(true) }
                .setOnKeyListener { d, code, ev ->
                    // šipky, OK a Zpět ovládají dialog samotný; ostatní zakázaná tlačítka jen upozorní
                    if (code in QuickLook.FORBIDDEN) {
                        val nav = code == android.view.KeyEvent.KEYCODE_BACK || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                            code == android.view.KeyEvent.KEYCODE_ENTER || code in 19..22
                        if (!nav && ev.action == android.view.KeyEvent.ACTION_UP) toast(t("qk_bad"))
                        return@setOnKeyListener !nav
                    }
                    if (ev.action == android.view.KeyEvent.ACTION_UP) {
                        Prefs.setQuickKey(this, code); TitulkyAccessibility.instance?.updateKeyFilter(true); d.dismiss(); render(true)
                    }
                    true
                }
                .show()
        } catch (_: Exception) {}
    }

    /** Otevřené rozbalovací skupiny v Nastavení (klíč skupiny). */
    private val openGroups = HashSet<String>()

    /**
     * Rozbalovací skupina po vzoru Titulků a Nápovědy: řádek s ▼/▲, obsah jen když je otevřená.
     * Vrací true, když je otevřená (volající pak přidá obsah).
     */
    private fun group(id: String, title: String, top: Int = 8): Boolean {
        val open = id in openGroups
        add(tagged(ui.settingRow(title, if (open) "▲" else "▼") {
            if (!openGroups.remove(id)) openGroups.add(id)
            render(true)
        }, "grp_$id"), top)
        return open
    }

    /** Titulky: otevřená skupina (-1 = žádná, [LOOK_DEEPL] = Kvalita překladu). */
    private var lookOpen = -1
    private val LOOK_DEEPL = 100

    /**
     * Stránka Titulky (od 0.97.1) po vzoru Nápovědy: rozbalovací skupiny Jazyk a písmo / Pruh s překladem /
     * Oblasti titulků a dole obnovení vzhledu. Žádné vysvětlující texty mezi řádky – popis volby se ukáže
     * jen v dialogu, když ji hráč zapíná ([confirmOn]); podrobnosti jsou v Nápovědě.
     */
    private fun subtitlesPage() {
        langRow("lang", 16)
        // Kvalita překladu (DeepL) hned pod jazykem překladu (od 0.97.28, dřív Nastavení → Experimentální)
        val dlOpen = lookOpen == LOOK_DEEPL
        add(tagged(ui.settingRow(t("wz_tr"), t(if (DeeplTranslator.active(this)) "dl_mode_on" else "h_offline") + if (dlOpen) "  ▲" else "  ▼") {
            lookOpen = if (dlOpen) -1 else LOOK_DEEPL; render(true)
        }, "lkdl"), 8)
        if (dlOpen) deeplSection().build(content)
        val groups = listOf<Pair<String, () -> Unit>>(
            "lk_text" to { lookText() },
            "lk_bar" to { lookBar() },
            "m_zones" to { lookZones() },
            // hra (profil, odhad z titulků, postavy a jména) patří k titulkům, ne do Nastavení (od 0.97.15)
            "t_game" to { gameGroup() },
        )
        groups.forEachIndexed { i, (key, build) ->
            val open = lookOpen == i
            add(tagged(ui.settingRow(t(key), if (open) "▲" else "▼") { lookOpen = if (open) -1 else i; render(true) }, "lk$i"), if (i == 0) 14 else 8)
            if (open) build()
        }
        add(tagged(ui.settingRow(t("l_reset"), "↺", Theme.MUTED) {
            Prefs.setTextSize(this, Prefs.DEFAULT_TEXT_SIZE); Prefs.setOpacity(this, 100); Prefs.setTextColorIndex(this, 0)
            Prefs.setBoldText(this, false); Prefs.setHoldMs(this, 1000)
            Prefs.setLayout(this, Prefs.layout(this).withPosition(cz.playlingo.core.ScreenLayout.Position.OVER).withBarWidth(0.84f))
            applyToService(); render(true); showDemo()
        }, "lreset"), 20)
    }

    /** Volba s popisem: popis se ukáže v dialogu jen při zapínání; vypnutí je hned. */
    private fun confirmOn(title: String, desc: String, turnOn: () -> Unit) {
        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(desc)
                .setPositiveButton(t("on")) { d, _ -> d.dismiss(); turnOn() }
                .setNegativeButton(t("wz_back")) { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) { turnOn() }
    }

    /** Jazyk a písmo: jazyk překladu, velikost, barva, tučně, originál, barvy jmen. */
    private fun lookText() {
        val sizeIdx = Prefs.TEXT_SIZES.indexOf(Prefs.textSize(this)).let { if (it < 0) Prefs.TEXT_SIZES.indexOf(Prefs.DEFAULT_TEXT_SIZE) else it }
        add(tagged(ui.settingRow(t("l_size_auto"), onOff(Prefs.matchSize(this))) {
            Prefs.setMatchSize(this, !Prefs.matchSize(this)); applyToService(); render(true)
        }, "sizeauto"), 6)
        // pevná velikost se nabízí jen tehdy, když se písmo neřídí originálem
        if (!Prefs.matchSize(this)) add(tagged(ui.settingRow(t("l_size"), Prefs.TEXT_SIZES[sizeIdx].toString()) {
            choose(t("l_size"), Prefs.TEXT_SIZES.map { it.toString() }, sizeIdx) { i ->
                Prefs.setTextSize(this, Prefs.TEXT_SIZES[i]); applyToService(); render(true); showDemo()
            }
        }, "size"), 6)
        val colorIdx = Prefs.textColorIndex(this)
        add(tagged(ui.settingRow(t("l_color"), t("color_$colorIdx"), Prefs.TEXT_COLORS[colorIdx]) {
            choose(t("l_color"), Prefs.TEXT_COLORS.indices.map { t("color_$it") }, colorIdx) { i ->
                Prefs.setTextColorIndex(this, i); applyToService(); render(true); showDemo()
            }
        }, "color"), 6)
        add(tagged(ui.settingRow(t("l_bold"), onOff(Prefs.boldText(this))) {
            Prefs.setBoldText(this, !Prefs.boldText(this)); applyToService(); render(true); showDemo()
        }, "bold"), 6)
        add(tagged(ui.settingRow(t("l_original"), onOff(Prefs.showOriginal(this))) {
            Prefs.setShowOriginal(this, !Prefs.showOriginal(this)); applyToService(); render(true); showDemo()
        }, "original"), 6)
        add(tagged(ui.settingRow(t("l_speaker"), onOff(Prefs.coloredSpeaker(this))) {
            Prefs.setColoredSpeaker(this, !Prefs.coloredSpeaker(this)); applyToService(); render(true); showDemo()
        }, "speaker"), 6)
    }

    /** Pruh s překladem: poloha, šířka, průhlednost, jak dlouho zůstane (u LG/Samsungu pro kterou televizi). */
    private fun lookBar() {
        // LG / Samsung: vzhled a oblasti jsou zvlášť pro každou televizi (TvProfile)
        TvProfile.activeName(this)?.let { add(ui.text(t("tv_profile_for").replace("%s", it), 14f, Theme.MUTED), 6) }
        val lay = Prefs.layout(this)
        add(tagged(ui.settingRow(t("l_pos"), t("pos_" + lay.position.name.lowercase())) {
            val all = cz.playlingo.core.ScreenLayout.Position.values().filter { it != cz.playlingo.core.ScreenLayout.Position.CUSTOM }
            choose(t("l_pos"), all.map { t("pos_" + it.name.lowercase()) }, all.indexOf(lay.position)) { i ->
                Prefs.setLayout(this, lay.withPosition(all[i])); applyToService(); render(true); showDemo()
            }
        }, "pos"), 6)
        val widthPct = Math.round(lay.barWidth * 100)
        add(tagged(ui.settingRow(t("l_width"), "$widthPct %") {
            choose(t("l_width"), Prefs.BAR_WIDTHS.map { "$it %" }, Prefs.BAR_WIDTHS.indexOf(widthPct)) { i ->
                Prefs.setLayout(this, lay.withBarWidth(Prefs.BAR_WIDTHS[i] / 100f)); applyToService(); render(true); showDemo()
            }
        }, "width"), 6)
        add(tagged(ui.settingRow(t("l_opacity"), "${Prefs.opacity(this)} %") {
            choose(t("l_opacity"), Prefs.OPACITIES.map { "$it %" }, Prefs.OPACITIES.indexOf(Prefs.opacity(this))) { i ->
                Prefs.setOpacity(this, Prefs.OPACITIES[i]); applyToService(); render(true); showDemo()
            }
        }, "opacity"), 6)
        // okraj pruhu nad a pod textem (0–3): jak moc pruh přesahuje písmo
        add(tagged(ui.settingRow(t("l_pad"), Prefs.barPad(this).toString()) {
            choose(t("l_pad"), (0..3).map { it.toString() }, Prefs.barPad(this)) { i ->
                Prefs.setBarPad(this, i); applyToService(); render(true); showDemo()
            }
        }, "barpad"), 6)
        val hold = Prefs.holdMs(this)
        add(tagged(ui.settingRow(t("l_hold"), if (hold == 0) t("off") else "+${hold / 1000} s") {
            choose(t("l_hold"), Prefs.HOLD_TIMES.map { if (it == 0) t("off") else "+${it / 1000} s" }, Prefs.HOLD_TIMES.indexOf(hold)) { i ->
                Prefs.setHoldMs(this, Prefs.HOLD_TIMES[i]); applyToService(); render(true)
            }
        }, "hold"), 6)
        add(tagged(ui.settingRow(t("l_fine"), "›") { startActivity(Intent(this, CalibrationActivity::class.java)) }, "fine"), 6)
    }

    /** Oblasti titulků: automatika (popis v dialogu), jen titulky, ruční nastavení nad snímkem, obnovení. */
    private fun lookZones() {
        add(tagged(ui.settingRow(t("z_auto"), onOff(Prefs.autoZone(this))) {
            if (Prefs.autoZone(this)) { Prefs.setAutoZone(this, false); applyToService(); render(true) }
            else confirmOn(t("z_auto"), t("z_auto_desc")) { Prefs.setAutoZone(this, true); applyToService(); render(true) }
        }, "zauto"), 6)
        add(tagged(ui.settingRow(t("z_only_subs"), onOff(Prefs.onlySubtitles(this))) {
            Prefs.setOnlySubtitles(this, !Prefs.onlySubtitles(this)); applyToService(); render(true)
        }, "onlysubs"), 6)
        add(tagged(ui.settingRow(t("z_calibrate"), "›") { startActivity(Intent(this, CalibrationActivity::class.java)) }, "calib"), 6)
        add(tagged(ui.settingRow(t("z_reset"), "↺", Theme.MUTED) {
            Prefs.setLayout(this, ScreenLayout()); applyToService()
            toast(t("z_reset_done")); render(true)
        }, "zreset"), 6)
    }

    // ======================= PŘEKLAD =======================

    private fun translate() {
        val lang = Prefs.lang(this)
        val ready = OfflineTranslator.isReady(lang)
        // překlad: DeepL online + offline / jen offline; DeepL se nastavuje v Titulkách → Kvalita překladu
        val deepl = DeeplTranslator.active(this)
        val idle = !deepl && DeeplKey.enabled(this)
        add(tagged(ui.settingRow(t("t_mode"), t(if (deepl) "dl_mode_on" else if (idle) "dl_mode_idle" else "h_offline"),
            if (idle) Theme.WARN else Theme.ACCENT) {
            openQuality()
        }, "tmode"), 8)
        // jazykový balíček pro offline překlad: chybí → OK ho stáhne
        add(tagged(ui.settingRow(t("chk_model") + " · " + lang.label, if (ready) t("ok") else t("download"),
            if (ready) Theme.OK else Theme.WARN) { if (!ready) downloadModel() }, "dl"), 8)

        // statistika a historie
        if (group("stats", t("t_stats"), 18)) {
            val s = TranslatorService.statsFor(this)
            add(ui.settingRow(t("t_lines"), s.lines.toString(), Theme.TEXT) {}, 8)
            add(ui.settingRow(t("t_hits"), s.cacheHits.toString(), Theme.TEXT) {}, 8)
            add(tagged(ui.settingRow(t("m_history"), "›") { section = Section.HISTORY; render() }, "hist"), 8)
            add(tagged(ui.settingRow(t("t_reset_stats"), "↺", Theme.MUTED) {
                s.reset(); Prefs.setStats(this, s); toast(t("t_cleared")); render(true)
            }, "rstats"), 8)
            add(tagged(ui.settingRow(t("t_clear_memory"), "↺", Theme.MUTED) { clearMemory() }, "clrmem"), 8)
        }

        // vlastní slovník – málo používaný, proto schovaný úplně dole
        val dict = Prefs.userDict(this, lang)
        if (!group("dict", t("t_dict") + if (dict.isNotEmpty()) " · ${dict.size}" else "")) return
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
        add(en, 8); add(tr, 8)
        add(tagged(ui.settingRow(t("t_dict_add"), "+") {
            val k = en.text.toString().trim(); val v = tr.text.toString().trim()
            if (k.length >= 2 && v.isNotEmpty()) {
                dict[k] = v; Prefs.setUserDict(this, lang, dict)
                draftEn = ""; draftTr = ""; applyToService()
            } else { draftEn = k; draftTr = v }
            render(true)
        }, "dictadd"), 8)
        dict.entries.toList().forEachIndexed { i, e ->
            add(tagged(ui.settingRow(e.key, e.value + "   ✕", Theme.TEXT) {
                dict.remove(e.key); Prefs.setUserDict(this, lang, dict); applyToService(); render(true)
            }, "dict$i"), 8)
        }
    }

    // ======================= NASTAVENÍ =======================

    /** Záložky Nastavení (⚙) v tomto pořadí; [key] = název záložky. */
    private enum class SettingsTab(val key: String) { GENERAL("st_general"), READ("st_read"), EXPERIMENTAL("dl_section") }

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
                // jiná záložka = obsah od začátku (hned pod záložkami, ty jsou připnuté nahoře)
                setOnClickListener { settingsTab = tab; render(keepFocus = true); pageScroll?.post { pageScroll?.scrollTo(0, 0) } }
                tag = "stab_${tab.name}"
                if (active) activeTab = this
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = ui.dp(8) })
        }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(tabs) }
        pinned.addView(tabScroll, ui.lp())
        // vybraná záložka celá na obrazovce (řada se překresluje, takže by jinak začínala vždy zleva)
        scrollTabIntoView(tabScroll, activeTab, smooth = false)

        when (settingsTab) {
            SettingsTab.GENERAL -> settingsGeneral()
            SettingsTab.READ -> { settingsRead(); translate() }
            SettingsTab.EXPERIMENTAL -> experimentalSection()
        }
    }

    /** Obecné: konzole, televize (+ nahrání), jazyk aplikace, zařízení, zkušební verze, povolení (jen Android TV). */
    private fun settingsGeneral() {
        consoleRow("sconsole", 8)
        // probouzení PlayStationu: zapne ho na spárovaném (druhém) účtu, proto výchozí vypnuto
        if (Prefs.console(this) == Console.PLAYSTATION) {
            add(tagged(ui.settingRow(t("ps_wake_row"), onOff(Prefs.ps5Wake(this))) {
                if (Prefs.ps5Wake(this)) { Prefs.setPs5Wake(this, false); render(true) }
                else confirmOn(t("ps_wake_row"), t("ps_wake_desc")) { Prefs.setPs5Wake(this, true); render(true) }
            }, "ps5wake"), 8)
        }
        // Typ televize (Android TV / LG / Samsung) – stejná nabídka jako ze souhrnného řádku na Domů.
        val type = Prefs.tvType(this)
        add(tagged(ui.settingRow(t("tv_type"), TvChoice.label(this, type)) {
            TvChoice.show(this) { render(true) }
        }, "tvtype"), 8)
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
        // telefon jako čtečka: běh na pozadí (šetření baterie) – stav a cesta k povolení
        if (!Prefs.isTv(this)) {
            val bg = batteryUnrestricted()
            add(tagged(ui.settingRow(t("h_battery_fix"), onOff(bg), if (bg) Theme.OK else Theme.WARN) { askBatteryUnrestricted() }, "battery"), 8)
        }
        // telefon, na kterém běží celá aplikace: cesta zpět k pomocníkovi pro přihlášení
        if (NetInfo.isHandheld(this)) add(tagged(ui.settingRow(t("hp_back"), "›") { backToPhoneHelper() }, "tophone"), 8)

        // povolení pruhu potřebuje jen Android TV (u LG/Samsungu ukazuje titulky televize)
        if (type == TvType.ANDROID) {
            val a11yOn = TitulkyAccessibility.instance != null
            add(tagged(ui.settingRow(t("s_a11y"), onOff(a11yOn), if (a11yOn) Theme.OK else Theme.WARN) {
                openAccessibility()
            }, "a11y"), 8)
            // tlačítko ovladače pro rychlou nabídku vzhledu při hře (QuickLook, jen Android TV)
            val qk = Prefs.quickKey(this)
            add(tagged(ui.settingRow(t("qk_row"), if (qk == 0) t("qk_none") else QuickLook.keyName(qk), if (qk == 0) Theme.MUTED else Theme.ACCENT) {
                learnQuickKey()
            }, "quickkey"), 8)
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
        }, "quality"), 8)
    }

    /** Ladění a hlášení (rozbalovací skupina v Experimentální): diagnostický režim a hlášení o stavu. */
    private fun settingsDiag() {
        if (!group("diag", t("s_diag"))) return
        add(tagged(ui.settingRow(t("s_debug"), onOff(Prefs.debug(this))) {
            Prefs.setDebug(this, !Prefs.debug(this)); applyToService(); render(true)
        }, "debug"), 8)
        add(tagged(ui.settingRow(t("rep_btn"), "›") { makeReport() }, "sreport"), 8)
        // záznam v domácí síti je taky ladění (od 0.97.15 tady, dřív samostatně v Experimentální)
        logShareRow()
    }

    /**
     * „Zpřístupnit záznam v domácí síti“ pro vývojáře (výchozí vypnuto, samo se vypne), viz NetLogShare.
     * Vlastní blok v části Experimentální pod DeepL.
     */
    private fun logShareRow() = add(NetLogShare.row(this, ui) { render(true) }, 8)

    // ======================= EXPERIMENTÁLNÍ =======================

    /** DeepL (Titulky → Kvalita překladu a krok průvodce Lepší překlad) – jedna instance, ať drží rozepsaný klíč. */
    private var deeplUi: DeeplSection? = null
    private fun deeplSection(): DeeplSection =
        deeplUi ?: DeeplSection(this, ui, { applyToService() }, { render(true) }).also { deeplUi = it }

    /** Titulky → Kvalita překladu s otevřenou skupinou (z řádku jazyka na Domů, Nastavení i Nápovědy). */
    private fun openQuality() {
        wizard = -1; section = Section.LOOK; lookOpen = LOOK_DEEPL; render()
    }

    /** Až na konci Nastavení, ať se nezmění nic nad tím (zkouška se tam prokliká naslepo). */
    private fun experimentalSection() {
        // zkušební verze patří k pokusům (přání majitele, od 0.97.6 tady místo v Obecné)
        add(tagged(ui.settingRow(t("s_test"), onOff(Prefs.testChannel(this))) {
            Prefs.setTestChannel(this, !Prefs.testChannel(this)); checkForUpdate(); render(true)
        }, "testch"), 8)
        // DeepL je od 0.97.28 v Titulkách → Kvalita překladu (už ne pokus)
        settingsDiag()
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

    /** Nápověda: otevřená kapitola (-1 = žádná) a televize, pro kterou se ukazuje (null = zvolená). */
    private var helpOpen = -1
    private var helpTv: TvType? = null

    /**
     * Nápověda jako postup od začátku (od 0.97): rejstřík kapitol v pořadí, jak člověk postupuje.
     * Kapitola se po OK rozbalí; obsah je jen pro vybranou televizi (nahoře „Nápověda pro“, výchozí
     * zvolená) a zvolenou konzoli. ✔ / ○ podle skutečného stavu – jen pro zvolenou televizi.
     */
    private fun help() {
        val chosen = Prefs.tvType(this)
        val tv = helpTv ?: chosen
        val mine = tv == chosen
        val ps = Prefs.console(this) == Console.PLAYSTATION
        val types = TvType.values().toList()
        add(tagged(ui.settingRow(t("help_for"), TvChoice.label(this, tv)) {
            choose(t("help_for"), types.map { TvChoice.label(this, it) }, types.indexOf(tv)) { i -> helpTv = types[i]; render(true) }
        }, "helpfor"), 12)

        fun body(vararg keys: String) = ui.card().apply {
            keys.forEachIndexed { i, k -> addView(ui.text(t(k), 16f, if (i == 0) Theme.TEXT else Theme.MUTED), ui.lp(top = if (i == 0) 0 else 10)) }
        }
        fun LinearLayout.button(label: String, tag: String, onClick: () -> Unit) =
            addView(tagged(ui.smallButton(label) { onClick() }, tag), ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT, top = 12))
        // stránka s návodem: v telefonu tlačítko do prohlížeče, na televizi čtvereček pro telefon
        fun LinearLayout.page(url: String, tag: String) {
            if (!Prefs.isTv(this@MainActivity)) button(t("help_page"), tag) {
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
            } else addView(qrRow(url, t("help_page") + "\n" + t("help_qr_note")), ui.lp(top = 12))
        }

        val chapters = mutableListOf<Triple<String, Boolean?, () -> View>>()
        // 1. instalace
        chapters += Triple("hc1", true, {
            body(if (tv == TvType.ANDROID) "hc1_android" else "hc1_reader", "inst_text").apply {
                page(if (tv == TvType.ANDROID) cz.playlingo.core.HelpLinks.ANDROID_TV else cz.playlingo.core.HelpLinks.PHONE, "hpage1")
            }
        })
        // 2. příprava konzole
        chapters += Triple("hc2", Prefs.consoleReady(this), {
            (if (ps) body("hc2_ps", "rp_hide", "ps5_rest") else body("hc2_xbox")).apply {
                page(if (ps) cz.playlingo.core.HelpLinks.PS5_PREP else cz.playlingo.core.HelpLinks.XBOX, "hpage2")
            }
        })
        // 3. televize
        chapters += Triple("hc3", if (!mine) null else when (tv) {
            TvType.ANDROID -> SubtitleOverlay.canShow(this)
            else -> TvChoice.upToDate(this, tv) != null
        }, {
            when (tv) {
                TvType.ANDROID -> body("hc3_android").apply {
                    addView(ui.text("adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow", 14f, Theme.ACCENT), ui.lp(top = 8))
                    if (mine) button(t("fix"), "hfix3") { settingsTab = SettingsTab.GENERAL; section = Section.SETTINGS; render() }
                }
                TvType.LG -> body("hc3_lg").apply {
                    button(t("lg_open"), "lghelp") { startActivity(Intent(this@MainActivity, LgInstallActivity::class.java)) }
                    page(cz.playlingo.core.HelpLinks.LG_ACCOUNT, "hpage3")
                }
                TvType.SAMSUNG -> body("ss_help").apply {
                    button(t("ss_open"), "sshelp") { startActivity(Intent(this@MainActivity, SamsungInstallActivity::class.java)) }
                    page(cz.playlingo.core.HelpLinks.SAMSUNG, "hpage3")
                }
            }
        })
        // 4. jazyk a kvalita překladu
        chapters += Triple("hc4", OfflineTranslator.isReady(Prefs.lang(this)), {
            body("hc4_text").apply {
                button(t("m_look") + " → " + t("wz_tr"), "hdeepl") { openQuality() }
            }
        })
        // 5. spárování konzole
        chapters += Triple("hc5", Prefs.consoleReady(this), {
            body("hc5_text").apply { button(ct("m_ps5"), "hpair") { section = Section.PS5; render() } }
        })
        // 6. hraní
        chapters += Triple("hc6", null, { body(if (tv == TvType.ANDROID) "hc6_android" else "hc6_tv") })
        // 7. když něco nejde
        chapters += Triple("hc7", null, {
            ui.card().apply {
                val items = listOfNotNull("hc7_qr", "hc7_ps5".takeIf { ps }, "hc7_net",
                    "hc7_battery".takeIf { tv != TvType.ANDROID }, "hc7_other".takeIf { ps }, "hc7_read")
                items.forEachIndexed { i, k -> addView(ui.text("•  " + t(k), 16f, Theme.TEXT), ui.lp(top = if (i == 0) 0 else 10)) }
                button(t("rep_btn"), "report") { makeReport() }
            }
        })
        // 8. volby v Nastavení (v Nastavení jsou jen názvy)
        chapters += Triple("hc8", null, {
            ui.card().apply {
                listOf("s_test" to "s_test_desc", "ps_wake_row" to "ps_wake_desc", "qk_row" to "qk_desc", "s_quality" to "q_desc", "t_game" to "t_game_desc",
                    "t_dict" to "t_dict_desc", "t_mode" to "t_offline_desc").forEachIndexed { i, (k, d) ->
                    addView(ui.text(t(k), 16f, Theme.TEXT, bold = true), ui.lp(top = if (i == 0) 0 else 14))
                    addView(ui.text(t(d), 15f, Theme.MUTED), ui.lp(top = 4))
                }
            }
        })

        chapters.forEachIndexed { i, (key, done, make) ->
            val open = helpOpen == i
            val mark = when (done) { true -> "✔  "; false -> "○  "; null -> "" }
            // šipka: zeleně hotovo, žlutě ještě udělat, šedě kapitola bez stavu (Hraní, Když něco nejde…)
            add(tagged(ui.settingRow(mark + t(key), if (open) "▲" else "▼", when (done) { true -> Theme.OK; false -> Theme.ACCENT; null -> Theme.MUTED }) {
                helpOpen = if (open) -1 else i; render(true)
            }, "hc$i"), if (i == 0) 16 else 8)
            if (open) add(make(), 6)
        }
        add(tagged(ui.button(t("wz_again")) { wizard = 0; render() }, "wagain"), 20)
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

    /** „Krok 3 z 8“. */
    private fun steps(current: Int, total: Int) = t("steps_of").replace("%1", current.toString()).replace("%2", total.toString())

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
        // telefon jako čtečka: jednou se zeptat na běh na pozadí (jinak ho šetření baterie se zhasnutým
        // displejem uspí a spojení s konzolí vypadává); dál jen v ⚙ Nastavení → Obecné, ne na Domů
        if (!Prefs.isTv(this) && !batteryUnrestricted()) {
            val once = getSharedPreferences("ui_jednou", MODE_PRIVATE)
            if (!once.getBoolean("baterie", false)) { once.edit().putBoolean("baterie", true).apply(); askBatteryUnrestricted() }
        }
        busyText = t("prep_model")
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
        if (busyText != null) render(true)   // až po spuštění stahování, ať je vidět pruh
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
        busyText = t("prep_model")
        OfflineTranslator.ensureModel(Prefs.lang(this)) { ok ->
            runOnUiThread { busyText = if (ok) null else t("model_fail"); render(true); busyText = null }
        }
        render(true)   // až po spuštění stahování, ať je vidět pruh
    }

    /** Číslo nejnovější verze v daném kanálu; null, když se nedá zjistit. Volat mimo hlavní vlákno. */
    private fun remoteVersion(channel: UpdateCheck.Channel): UpdateCheck.Remote? = Updater.remoteVersion(channel)

    /** Na pozadí zjistí, jestli není ke stažení novější verze (se zapnutým zkušebním kanálem i v něm). */
    private var lastUpdateCheck = 0L

    private fun checkForUpdate() {
        lastUpdateCheck = android.os.SystemClock.elapsedRealtime()
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
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this), barPad = Prefs.barPad(this)
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
    const val VERSION = "verze 0.97.35"
    const val VERSION_CODE = 132
}
