package cz.playlingo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cz.playlingo.core.AutoZone
import cz.playlingo.core.Console
import cz.playlingo.core.DemoGame
import cz.playlingo.core.QuickWords
import cz.playlingo.core.TextScale
import cz.playlingo.core.DiagLog
import cz.playlingo.core.FrameSignature
import cz.playlingo.core.Glossary
import cz.playlingo.core.I18n
import cz.playlingo.core.Lang
import cz.playlingo.core.LinkStatus
import cz.playlingo.core.OcrBlock
import cz.playlingo.core.OcrLine
import cz.playlingo.core.Perf
import cz.playlingo.core.Picked
import cz.playlingo.core.Ps5
import cz.playlingo.core.ScreenLayout
import cz.playlingo.core.Segment
import cz.playlingo.core.SubtitleHistory
import cz.playlingo.core.SubtitleTracker
import cz.playlingo.core.TextLogic
import cz.playlingo.core.TranslationCache
import cz.playlingo.core.TvSender
import cz.playlingo.core.UsageStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max

/**
 * Srdce aplikace. Připojí se k PlayStationu po domácí síti (stejně jako Remote Play), z kopie
 * obrazu několikrát za sekundu přečte anglické titulky, přeloží je a položí přes obraz
 * televize černý pruh s překladem. Hra samotná běží do televize kabelem HDMI v plné kvalitě.
 */
class TranslatorService : Service() {

    companion object {
        const val ACTION_STOP = "cz.playlingo.STOP"
        const val ACTION_PAUSE = "cz.playlingo.PAUSE"
        const val ACTION_RELOAD = "cz.playlingo.RELOAD"
        /** Spustit nad ukázkovým videem místo PlayStationu (zkouška bez konzole). */
        const val ACTION_START_DEMO = "cz.playlingo.START_DEMO"

        /** Velikost obrazu, se kterou se počítá, než přijde první snímek videa. */
        const val VIDEO_W = 1280
        const val VIDEO_H = 720

        /** Poslední chyba při spuštění – pro obrazovku aplikace. */
        @Volatile var startError: String? = null
            private set

        @Volatile var running = false
            private set
        /** Velikost obrazu, který právě přichází z konzole ("1920×1080"), a co aplikace naposledy přečetla. */
        @Volatile var videoSize = ""
            private set
        @Volatile var lastRead = ""
            private set
        /** Jak dlouho v průměru trvá jedno přečtení titulku (ms); 0 = zatím se nečetlo. */
        @Volatile var readMs = 0L
            private set
        @Volatile var paused = false
            private set
        /** Běží nad ukázkovým videem (ne nad PlayStationem)? */
        @Volatile var demo = false
            private set

        @Volatile private var activeReader: StreamReader? = null

        @Volatile private var activeTv: TvSender? = null

        /** Kolik televizí LG si právě bere titulky; -1 = posílání neběží (vypnuté, nebo nejde otevřít port). */
        val tvConnected: Int get() = activeTv?.takeIf { it.running }?.connected ?: -1

        /** Poslední obrázek ze hry (zmenšený, barevný) – pro náhled a nastavení oblastí. */
        val snapshot: Bitmap? get() = activeReader?.colorSnapshot

        /** Kolik snímků videa už aplikace dostala a dekódovala (pro obrazovku aplikace). */
        val framesDecoded: Long get() = activeReader?.decoded ?: 0

        /** Obraz právě bere z Xboxu (jinak z PlayStationu nebo ukázky). */
        @Volatile var xbox = false
            private set

        /** Stav spojení s konzolí, ze které se právě čte (PlayStation nebo Xbox). */
        val linkState: Ps5.Link get() = if (xbox) XboxLink.state else Ps5Link.state
        /** Důvod odpojení pro člověka (u Xboxu klíč textu xb_e_…), nebo null. */
        val linkReason: String? get() = if (xbox) XboxLink.reason else Ps5Link.reason

        /** Stav ve třech úrovních: překlad běží / spojeno s konzolí / obraz opravdu přichází. */
        fun linkStatus(now: Long = SystemClock.uptimeMillis()): LinkStatus.Status =
            LinkStatus.evaluate(now, running, paused, demo, linkState,
                if (xbox) XboxLink.connectedSince else Ps5Link.connectedSince, activeReader?.lastFrameAt ?: 0)

        /** Statistika sdílená s obrazovkou aplikace. */
        @Volatile private var stats: UsageStats? = null

        fun statsFor(c: Context): UsageStats = stats ?: Prefs.stats(c).also { stats = it }

        @Volatile private var clearCacheRequested = false

        /** Smazání paměti překladů z obrazovky aplikace (provede se v běžící službě). */
        fun requestCacheClear() { clearCacheRequested = true }

        private const val TAG = "TestPreklad"
        private const val CHANNEL = "test_preklad"
        private const val NOTIF_ID = 1

        private const val CACHE_PREFIX = "preklady_"

        /** Jak dlouho smí stejný text stát na obrazovce, než se přestane brát jako titulek. */
        private const val STALE_MS = 15_000L

        /** Události, které se ukládají i do souboru (viz [EventFile]). */
        private val KEEP = listOf("START|", "LINK|", "VIDEO|", "PERF|", "SOURCE|", "STOP|", "SCALE|", "WARM|", "IMAGE|")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reader: StreamReader? = null
    private var link: ConsoleLink? = null
    private var feeder: DemoFeeder? = null
    /** Drží Wi-Fi v plném výkonu, ať video z konzole nevypadává kvůli úspornému režimu. */
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    @Volatile private var overlay: SubtitleOverlay? = null
    /** Posílání titulků do televize LG; null = vypnuté (výchozí). */
    @Volatile private var tv: TvSender? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val cache = TranslationCache()
    // schovat až po třetím prázdném čtení: jeden nepovedený snímek videa nesmí překlad shodit
    private val tracker = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)
    @Volatile private var layout = ScreenLayout()
    @Volatile private var lang = Lang.CS
    private var screenW = 0
    private var screenH = 0
    private var capW = VIDEO_W
    private var capH = VIDEO_H
    @Volatile private var debug = false
    @Volatile private var onlySubtitles = true
    @Volatile private var autoFind = true
    private val autoZone = AutoZone()

    private var lastLicenseCheck = 0L

    /** Výkon podle paměti zařízení – slabší televize čtou méně často. */
    private val perf: Perf by lazy {
        try {
            val am = getSystemService(android.app.ActivityManager::class.java)
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            Perf.forDevice(mi.totalMem / (1024 * 1024), am.isLowRamDevice)
        } catch (_: Exception) { Perf.forDevice(0, false) }.withQuality(
            // automatika: když s obrazem 1080p spojení na tomto zařízení nedrželo, zůstává u 720p
            Prefs.readQuality(this).let { q -> if (q == "auto" && Prefs.highResFailed(this)) "std" else q })
    }

    /** Kdy schovat pruh (překlad zůstane chvíli po zmizení angličtiny, ať se dá dočíst). 0 = nic. */
    private var hideAt = 0L
    @Volatile private var holdMs = 1000L
    @Volatile private var showOriginal = false
    @Volatile private var lastSig: IntArray? = null
    private var hiddenForOtherApp = false

    // diagnostika
    private var frames = 0
    private var ocrRuns = 0
    private var lastOcrText = ""
    private var lastMs = 0L
    private var lastOcrMs = 0L
    private var ticksLogged = 0
    private var lastLinkLogged: Ps5.Link? = null
    private var lastVideoLog = 0L
    private var lastPerfLog = 0L
    private var ocrMsSum = 0L
    private var shows = 0

    /** Slovník: jména podle hry, předpřipravené hlášky a vlastní slovník uživatele. */
    @Volatile private var glossary: Glossary? = null

    private val textScale = TextScale()
    /** Jazyk, jehož překladový model už je načtený v paměti. */
    @Volatile private var warmedLang: Lang? = null
    private var shownW = 0
    private var shownH = 0
    /** Text, který je právě v oblasti, a od kdy tam beze změny stojí (viz [STALE_MS]). */
    private var steadyText = ""
    private var steadySince = 0L

    /** Záznam pro automatickou zkoušku i pro hlášení chyby. */
    private fun tp(msg: String) {
        Log.i("TP_TEST", msg); DiagLog.add(msg)
        if (KEEP.any { msg.startsWith(it) }) EventFile.add(msg)
    }

    @Volatile private var lastError: String? = null

    /** Překlady spuštěné dopředu, hned po prvním přečtení titulku (text → běžící překlad). */
    private val inflight = java.util.concurrent.ConcurrentHashMap<String, Deferred<List<Segment>?>>()
    private var statsStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EventFile.init(this)
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_PAUSE -> {
                if (running) {
                    paused = !paused
                    if (paused) overlay?.hide() else resetReading()
                    updateNotification()
                }
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (running) reloadSettings()
                return START_NOT_STICKY
            }
        }

        val wantDemo = intent?.action == ACTION_START_DEMO
        startForegroundCompat(buildNotification())
        if (running) {
            // už běží → jen případně přepni zdroj (ukázka ↔ PlayStation)
            if (wantDemo != demo) startSource(wantDemo)
            return START_NOT_STICKY
        }
        startError = null
        if (!Prefs.license(this).canRun) { startError = "licence"; stopSelf(); return START_NOT_STICKY }
        val console = Prefs.console(this)
        if (!wantDemo && !Prefs.consoleReady(this)) { startError = if (console == Console.XBOX) "xbox" else "ps5"; stopSelf(); return START_NOT_STICKY }

        setupScreenSize()
        val r = StreamReader(perf.captureWidth, perf.captureHeight)
        // Xbox posílá hotové snímky (WebRTC) – dekodér H.264 se otevře, až by byl potřeba (ukázka)
        if (!r.start(openDecoder = wantDemo || console != Console.XBOX)) {
            startError = "video: " + (r.lastError ?: "?")
            tp("ERROR|decoder|" + r.lastError)
            r.stop(); stopSelf(); return START_NOT_STICKY
        }
        reader = r
        activeReader = r
        try {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TestPreklad").also { it.acquire() }
        } catch (e: Throwable) { Log.w(TAG, "wifi lock", e) }
        beginRunning()
        startSource(wantDemo)
        return START_NOT_STICKY
    }

    /** Zapne zdroj videa: PlayStation po síti, nebo ukázkové video. */
    private fun startSource(wantDemo: Boolean) {
        val r = reader ?: return
        link?.stop(); link = null
        feeder?.stop(); feeder = null
        demo = wantDemo
        xbox = false
        overlay?.hide(); resetReading()
        r.clearFrames()
        if (wantDemo) {
            // ukázka má stejnou velikost obrazu, jakou by toto zařízení bralo z konzole
            feeder = DemoFeeder(perf.captureWidth, perf.captureHeight, { r.feed(it) }, { y, w, h -> r.feedRaw(y, w, h) }, { r.decoded }).also { it.start() }
            tp("SOURCE|demo")
        } else if (Prefs.console(this) == Console.XBOX) {
            val id = XboxAccount.consoleId(this) ?: return
            // telefon: obraz do paměti (některé telefony z grafického čipu vracely jen černou)
            link = XboxLink(applicationContext, id, r, gpu = !NetInfo.isHandheld(this)).also { xbox = true; it.start() }
            tp("SOURCE|xbox")
        } else {
            val host = Prefs.ps5Host(this) ?: return
            link = Ps5Link(applicationContext, host, perf.captureWidth >= 1920) { r.feed(it) }.also { it.start() }
            tp("SOURCE|ps5 ${host.ip}")
        }
        updateNotification()
    }

    private fun beginRunning() {
        lastLicenseCheck = SystemClock.uptimeMillis()
        tp("A11Y|" + (TitulkyAccessibility.instance != null))
        tp("START|video=${perf.captureWidth}x${perf.captureHeight}|interval=${perf.intervalMs}|screen=${screenW}x${screenH}|eco=${perf.eco}|decoder=${reader?.decoderName}|lib=${Ps5Link.selfTest()}")
        lang = Prefs.lang(this)
        statsFor(this)
        loadCache()
        reloadSettings()
        running = true
        paused = false
        statsStart = SystemClock.uptimeMillis()
        scope.launch { loop() }
    }

    /** Načte nastavení (po spuštění i po změně v aplikaci) a znovu vytvoří pruh. */
    private fun reloadSettings() {
        val newLang = Prefs.lang(this)
        if (newLang != lang) {
            saveCache(); cache.clear(); lang = newLang; loadCache()
        }
        layout = Prefs.layout(this)
        debug = Prefs.debug(this)
        onlySubtitles = Prefs.onlySubtitles(this)
        val wasAuto = autoFind
        autoFind = Prefs.autoZone(this)
        if (autoFind != wasAuto) autoZone.reset()
        if (autoFind) layout = layout.copy(read = autoZone.current)
        glossary = Prefs.glossary(this)
        // změna hry nebo slovníku → dřívější překlady už neplatí
        val sig = Prefs.glossarySignature(this)
        if (sig != Prefs.savedGlossarySignature(this)) {
            cache.clear(); try { cacheFile().delete() } catch (_: Exception) {}
            Prefs.setSavedGlossarySignature(this, sig)
        }
        deeplKey = DeeplTranslator.activeKey(this)
        holdMs = Prefs.holdMs(this).toLong()
        showOriginal = Prefs.showOriginal(this)
        hideAt = 0
        overlay?.destroy()
        // Titulky do televize LG: jen když si to uživatel zapnul. Jinak se nic neotevírá ani neposílá.
        if (Prefs.tvSend(this)) {
            if (tv == null) TvSender { tp("TV|$it") }.also {
                // jiná televize než minule → její vzhled pruhu a oblasti (TvProfile)
                it.onAnnounce = { a ->
                    if (TvProfile.onAnnounce(this, a)) android.os.Handler(android.os.Looper.getMainLooper()).post { if (running) reloadSettings() }
                }
                it.start(); tv = it; activeTv = it
            }
        } else {
            tv?.stop(); tv = null; activeTv = null
        }
        sendTvStyle()
        val sender = tv
        overlay = SubtitleOverlay(
            this, screenW, screenH, layout, Prefs.textSize(this), Prefs.opacity(this),
            debug, Prefs.coloredSpeaker(this),
            Prefs.TEXT_COLORS[Prefs.textColorIndex(this)], Prefs.boldText(this), Prefs.matchSize(this),
            onText = sender?.let { s -> { text: String?, parts: String? -> if (text == null) s.hide() else s.show(text, parts) } }
        )
        resetReading()
        val l = lang
        modelTries = 0
        prepareModel(l)
        updateNotification()
    }

    /** Kolikrát se za tohoto běhu zkoušelo stáhnout jazykový balíček (po neúspěchu se zkouší znovu). */
    @Volatile private var modelTries = 0
    @Volatile private var lastModelRetry = 0L

    /**
     * Jazykový balíček pro překlad: stáhnout, když chybí, a připravit. Když stažení selže (výpadek sítě,
     * serveru Googlu), zkusí se za 20 s znovu, nejvýš 6× za běh – jinak by se do restartu nepřekládalo nic.
     */
    private fun prepareModel(l: Lang) {
        modelTries++
        OfflineTranslator.ensureModel(l) { ok ->
            tp("MODEL|${l.code}|${if (ok) "ok" else "fail"}|pokus=$modelTries")
            if (!ok && running && l == lang && modelTries < 6) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (running && l == lang) prepareModel(l) }, 20_000)
            }
            // překladač se připraví hned, ne až s prvním titulkem
            if (ok && warmedLang != l) {
                warmedLang = l
                scope.launch { OfflineTranslator.warmUp(l)?.let { (cold, warm) -> tp("WARM|${l.code}|první=${cold}ms|další=${warm}ms") } }
            }
        }
    }

    // ---------------- notifikace ----------------

    private fun buildNotification(): Notification {
        val l = Prefs.uiLang(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "PlayLingo", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stopPi = PendingIntent.getService(this, 1, Intent(this, TranslatorService::class.java).setAction(ACTION_STOP), flags)
        val pausePi = PendingIntent.getService(this, 2, Intent(this, TranslatorService::class.java).setAction(ACTION_PAUSE), flags)
        val openPi = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), flags)
        val noIcon: android.graphics.drawable.Icon? = null
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(I18n.t(l, if (paused) "n_paused" else "n_running"))
            .setContentText(I18n.t(l, "n_text"))
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(noIcon, I18n.t(l, if (paused) "b_resume" else "b_pause"), pausePi).build())
            .addAction(Notification.Action.Builder(noIcon, I18n.t(l, "b_stop"), stopPi).build())
            .build()
    }

    private fun updateNotification() {
        try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    // ---------------- obrazovka ----------------

    @Suppress("DEPRECATION")
    private fun setupScreenSize() {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            screenW = b.width(); screenH = b.height()
        } else {
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            screenW = dm.widthPixels; screenH = dm.heightPixels
        }
        if (screenW <= 0 || screenH <= 0) { screenW = 1920; screenH = 1080 }
    }


    // ---------------- hlavní smyčka ----------------

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val t0 = SystemClock.uptimeMillis()
            try {
                watchLink(t0)
                if (!paused && appAllowed()) tick(t0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "smyčka", e)
            }
            if (hideAt in 1..SystemClock.uptimeMillis()) { hideAt = 0; overlay?.hide() }
            if (autoFind && !paused && autoZone.onTick(t0)) zoneChanged()
            if (clearCacheRequested) {
                clearCacheRequested = false
                cache.clear(); cacheFile().delete()
            }
            val ov = overlay
            if (debug && ov != null) publishDebug(ov)
            if (cache.dirty >= 20) saveCache()
            // jednou za minutu: neskončila zkušební doba během hraní?
            if (t0 - lastLicenseCheck > 60_000) {
                lastLicenseCheck = t0
                if (!Prefs.license(this).canRun) { stopSelf(); return }
            }
            val spent = SystemClock.uptimeMillis() - t0
            // Po každém čtení krátká pauza úměrná jeho délce: procesor televize musí stíhat i příjem
            // videa a spojení s konzolí, jinak se spojení rozpadá.
            delay(max(minOf(100L, max(20L, spent / 4)), perf.intervalMs - spent))
        }
    }

    /**
     * Čtení začíná znovu (pauza, nové nastavení, spojení, jiný zdroj): zapomene se rozpracovaný
     * titulek i překlady spuštěné dopředu. Čtení nebo překlad, které ještě běží, po návratu poznají
     * podle [SubtitleTracker.revision], že jsou zastaralé, a nic neukážou.
     */
    private fun resetReading() {
        tracker.reset()
        lastSig = null
        // jen zapomenout, nerušit: smyčka může na překlad právě čekat a zrušení by ji ukončilo
        inflight.clear()
    }

    private var lastImageStage = LinkStatus.Stage.OFF
    private val IMAGE_STAGES = setOf(LinkStatus.Stage.IMAGE, LinkStatus.Stage.NO_IMAGE, LinkStatus.Stage.STALLED)

    /** Hlídá spojení s konzolí: zapisuje změny stavu a obnoví spojení, když video přestane chodit. */
    private fun watchLink(now: Long) {
        val r = reader ?: return
        if (now - lastVideoLog > 5000) {
            lastVideoLog = now
            if (ticksLogged < 400) tp("VIDEO|fed=${r.fed}|decoded=${r.decoded}|${r.frameWidth}x${r.frameHeight}|dec=${r.decoderName}|err=${r.lastError}" +
                (feeder?.let { "|demo sent=${it.sent} raw=${it.raw} err=${it.error}" } ?: ""))
        }
        // jednou za půl minuty souhrn rychlosti (pro hlášení chyby)
        if (now - lastPerfLog > 30_000 && ocrRuns > 0) {
            lastPerfLog = now
            tp("PERF|čtení=${ocrRuns}|průměr=${ocrMsSum / ocrRuns}ms|poslední=${lastOcrMs}ms|titulků=${shows}|video=${r.frameWidth}x${r.frameHeight}|oblast=${(layout.read.height * 100).toInt()}%")
        }
        if (demo) return
        val st = linkState
        if (st != lastLinkLogged) {
            lastLinkLogged = st
            tp("LINK|$st|" + (linkReason ?: "") + "|dec=${r.decoderName}|${r.frameWidth}x${r.frameHeight}|fed=${r.fed}|decoded=${r.decoded}")
            // spojení spadlo → starý překlad nenechávej viset na obrazovce
            if (st != Ps5.Link.CONNECTED) { hideAt = 0; overlay?.hide(); resetReading(); r.clearFrames() }
            updateNotification()
        }
        // obraz: přichází / nepřichází (do záznamu jen změny, ať je v hlášení vidět, kdy se zastavil)
        val img = linkStatus(now)
        if (img.stage != lastImageStage) {
            if (img.stage in IMAGE_STAGES || lastImageStage in IMAGE_STAGES) tp("IMAGE|${img.stage}|decoded=${r.decoded}")
            lastImageStage = img.stage
        }
        val l = link ?: return
        if (st == Ps5.Link.CONNECTED && l.connectedForMs > 12_000 && now - r.lastFrameAt > 12_000) {
            l.restart("no video")
        }
    }

    /**
     * Pruh se neukazuje přes obrazovky této aplikace. Jinak běží vždy, když je spojení s konzolí:
     * televize při přepnutí na HDMI nemusí oznámit, co je na obrazovce, a hádat to by znamenalo,
     * že překlad nad hrou občas zmizí.
     */
    private fun appAllowed(): Boolean {
        // S posíláním do televize LG se čte pořád: titulky jdou na jinou obrazovku, takže nevadí,
        // že je na tomto zařízení (typicky telefonu) vidět aplikace.
        val allowed = tv != null || (if (demo) UiState.testCard else !UiState.visible)
        if (!allowed && !hiddenForOtherApp) {
            hideAt = 0; overlay?.hide(); resetReading()
        }
        hiddenForOtherApp = !allowed
        return allowed
    }

    private suspend fun tick(now: Long) {
        val ov = overlay ?: return
        val z = layout.read
        val frame = reader?.grab(z.left, z.top, z.right, z.bottom) ?: return      // žádný nový snímek videa
        capW = frame.fullW
        capH = frame.fullH
        frames++
        process(frame, ov)
        lastMs = SystemClock.uptimeMillis() - now
    }

    private suspend fun process(frame: StreamReader.Crop, ov: SubtitleOverlay) {
        val z = layout.read
        val x0 = frame.x0
        val y0 = frame.y0
        val crop = frame.bmp      // znovupoužívaná bitmapa – nerecyklovat

        // přeskoč čtení, když se oblast od posledního čtení nezměnila
        val sig = frame.sig
        if (tracker.isSettled && !FrameSignature.changed(lastSig, sig, frame.cellPixels)) {
            return
        }
        lastSig = sig
        // výsledek čtení i překladu platí jen pro tento „začátek“ (viz SubtitleTracker.revision)
        val token = tracker.revision

        val ocrStart = SystemClock.uptimeMillis()
        // drobné titulky: výřez se před čtením zvětší (viz TextScale)
        val k = textScale.scale
        val input = if (k > 1f) Bitmap.createScaledBitmap(crop, (crop.width * k).toInt(), (crop.height * k).toInt(), true) else crop
        val result = recognizer.process(InputImage.fromBitmap(input, 0)).await()
        if (input !== crop) input.recycle()
        // mezitím se čtení začalo znovu (pauza, jiná oblast, nové nastavení) → výsledek je zastaralý
        if (!tracker.isCurrent(token)) return
        ocrRuns++
        lastOcrMs = SystemClock.uptimeMillis() - ocrStart
        val blocks = toBlocks(result, k)
        if (textScale.update(blocks.flatMap { b -> b.lines.filter { it.text.count { c -> c.isLetter() } >= 3 }.map { it.height } }, crop.width * crop.height))
            tp("SCALE|${textScale.scale}")

        // V oblasti je text v jiném jazyce než v angličtině (česká nabídka konzole, hra už je přeložená):
        // to není anglický titulek ze hry – nepřekládá se nic, ani anglické nápisy vedle.
        val foreignScreen = (onlySubtitles || autoFind) && TextLogic.looksForeign(result.text)
        val picked = if (foreignScreen) Picked("", 0) else TextLogic.assemble(
            blocks,
            minLineHeight = (capH * 0.013f).toInt(),
            subtitlesOnly = onlySubtitles || autoFind,   // automatika čte velkou plochu → vždy jen titulky
            screenCenterX = capW / 2 - x0,
            centerTolerance = (capW * 0.14f).toInt(),
            maxLineHeight = (capH * 0.085f).toInt(),
        )
        if (picked.text.isNotEmpty()) lastOcrText = picked.text

        // text, který není anglicky (hra už je v jiném jazyce, nebo je to šum), se nepřekládá
        if (picked.text.isNotEmpty() && TextLogic.looksForeign(picked.text)) {
            if (ticksLogged++ < 400) tp("SKIP|" + picked.text.replace('\n', ' ').take(40))
            return
        }
        // záznam pro automatickou zkoušku (jen prvních pár set čtení)
        if (ticksLogged++ < 400) tp("READ|ocr=${lastOcrMs}ms|zone=${(z.height * 100).toInt()}%|" + picked.text.replace('\n', ' ').take(40))

        ocrMsSum += lastOcrMs
        readMs = if (readMs == 0L) lastOcrMs else (readMs * 4 + lastOcrMs) / 5
        if (capW != shownW || capH != shownH) { shownW = capW; shownH = capH; videoSize = "${capW}×${capH}" }
        if (picked.text.isNotEmpty()) lastRead = picked.text.replace('\n', ' ').take(60)
        // Titulek je na obrazovce pár sekund. Text, který stojí beze změny dlouho, je nápis nebo menu:
        // pruh se schová a znovu se ukáže, až se text změní.
        val nowMs = SystemClock.uptimeMillis()
        if (picked.text.isEmpty() || !TextLogic.similar(picked.text, steadyText)) { steadyText = picked.text; steadySince = nowMs }
        // volby odpovědí smí stát déle – hráč se rozhoduje
        val staleMs = if (TextLogic.hasChoices(picked.text)) TextLogic.CHOICES_STALE_MS else STALE_MS
        val stale = (onlySubtitles || autoFind) && picked.text.isNotEmpty() && nowMs - steadySince > staleMs
        if (stale) {
            if (overlay?.isShown == true) { overlay?.hide(); tp("HIDE|stale") }
            return
        }
        // slabé zařízení (jedno čtení trvá déle než půl sekundy): pruh se schová hned po prvním prázdném čtení
        // otisk obrazu: na nezměněném obrazu ochuzené čtení (jen poslední řádek…) nenahradí celý titulek
        when (val action = tracker.onOcr(picked.text, slowDevice = lastOcrMs > 500, sig = sig, cellPixels = frame.cellPixels)) {
            is SubtitleTracker.Action.Show -> {
                // překlad už většinou běží (spuštěný po prvním přečtení) – jen se počká na výsledek
                val ready = inflight.remove(action.text)
                val offline = (if (ready != null) ready.await() else null) ?: translateSegments(action.text) ?: return
                // překlad doběhl až po novém začátku (pauza, změna jazyka, jiná oblast) → neukazuje se
                if (!tracker.confirmShown(action.text, token)) return
                // pokus DeepL (výchozí vypnuto): řádek, který už DeepL překládal, se vezme rovnou z jeho paměti
                val deeplJob = deeplKey?.let { DeeplTranslator.plan(it, lang, action.text, glossary) }
                val fromDeepl = deeplJob?.let { DeeplTranslator.cached(it) }
                val segs = if (deeplJob != null && fromDeepl != null) {
                    DeeplTranslator.stats.fromCache(); tp(cz.playlingo.core.Deepl.logLine(0, "cache", 0))
                    DeeplTranslator.merge(offline, deeplJob, fromDeepl)
                } else offline
                val top = if (picked.top >= 0) (y0 + picked.top).toFloat() / capH else -1f
                val bottom = if (picked.bottom >= 0) (y0 + picked.bottom).toFloat() / capH else -1f
                hideAt = 0
                shows++
                tp("SHOW|" + action.text.replace('\n', ' ') + "|" + segs.joinToString(" ") { (it.speaker?.plus(": ") ?: "") + it.body })
                // ukázka: za jak dlouho po objevení titulku je překlad na obrazovce (z toho samotné čtení)
                if (demo) tp("LAT|${DemoGame.shownFor(SystemClock.uptimeMillis() - DemoFeeder.epoch)}ms|ocr=${lastOcrMs}ms")
                val left = if (picked.left >= 0) (x0 + picked.left).toFloat() / capW else -1f
                val right = if (picked.right >= 0) (x0 + picked.right).toFloat() / capW else -1f
                val original = if (showOriginal) action.text else null
                val lineH = picked.lineHeight.toFloat() / capH
                val seq = synchronized(showLock) {
                    ov.show(segs, original, top, bottom, left, right, lineHeight = lineH)
                    ++showSeq
                }
                if (deeplJob != null && fromDeepl == null) improveWithDeepl(deeplJob, segs, token, seq, ov) { better ->
                    ov.show(better, original, top, bottom, left, right, lineHeight = lineH)
                }
                statsFor(this).line()
                // automatika: zapamatuj si, kde na obrazovce titulky jsou
                if (autoFind && autoZone.onSubtitle(top, bottom, SystemClock.uptimeMillis())) zoneChanged()
                SubtitleHistory.add(action.text, TextLogic.joinSegments(segs))
            }
            // anglický titulek zmizel – překlad nech chvíli dočíst
            SubtitleTracker.Action.Hide -> {
                tp("HIDE")
                if (holdMs <= 0) ov.hide() else hideAt = SystemClock.uptimeMillis() + holdMs
            }
            SubtitleTracker.Action.None -> {
                // na obrazovce je už jiný titulek → starý překlad hned schovej
                if (tracker.isChanging && picked.text.isNotEmpty() && ov.isShown) { hideAt = 0; ov.hide() }
                prefetch(tracker.pendingText)
            }
        }
    }

    /**
     * Vzhled pruhu ze stránky Titulky do televize LG/Samsung (průhlednost, poloha, šířka, písmo).
     * Bez toho televize kreslila vždy neprůhledný pruh dole – přes anglické titulky (do 0.85).
     */
    private fun sendTvStyle() {
        val t = tv ?: return
        val pos = when (layout.position) {
            cz.playlingo.core.ScreenLayout.Position.OVER -> "over"
            cz.playlingo.core.ScreenLayout.Position.ABOVE -> "above"
            cz.playlingo.core.ScreenLayout.Position.TOP -> "top"
            cz.playlingo.core.ScreenLayout.Position.CUSTOM -> "custom"
        }
        val color = String.format("#%06X", Prefs.TEXT_COLORS[Prefs.textColorIndex(this)] and 0xFFFFFF)
        t.setStyle(TvSender.styleMessage(Prefs.opacity(this), pos, layout.read.top, layout.read.bottom, layout.barCenterY,
            layout.barWidth, Prefs.textSize(this).toFloat() / Prefs.DEFAULT_TEXT_SIZE, color, Prefs.boldText(this)))
    }

    /** Automatika změnila oblast čtení (našla titulky, nebo se vrátila k hledání). */
    private fun zoneChanged() {
        tp("ZONE|" + (autoZone.learned?.let { "learned ${it.top}-${it.bottom}" } ?: "scan"))
        layout = layout.copy(read = autoZone.current)
        overlay?.setLayout(layout)
        sendTvStyle()
        lastSig = null
        tracker.regionChanged()
    }

    /** Řádky z čtečky; souřadnice se přepočítají zpět na nezvětšený výřez ([k] = zvětšení). */
    private fun toBlocks(t: Text, k: Float): List<OcrBlock> = t.textBlocks.map { block ->
        OcrBlock(block.lines.mapNotNull { l ->
            val bb = l.boundingBox ?: return@mapNotNull null
            OcrLine(l.text, (bb.left / k).toInt(), (bb.top / k).toInt(), (bb.right / k).toInt(), (bb.bottom / k).toInt())
        })
    }

    // ---------------- překlad ----------------

    /** Začne překládat titulek hned po prvním přečtení, ještě před potvrzením druhým snímkem. */
    private fun prefetch(text: String) {
        if (text.isEmpty() || inflight.containsKey(text)) return
        if (inflight.size > 6) inflight.clear()   // staré nepotřebné (titulek se mezitím změnil)
        inflight[text] = scope.async { translateSegments(text) }
    }

    /** Přeloží text po částech; jména mluvčích se nepřekládají. */
    private suspend fun translateSegments(text: String): List<Segment>? {
        val l = lang   // jazyk se může během překladu změnit – celý text se překládá do jednoho
        val out = ArrayList<Segment>()
        for (s in TextLogic.segments(text)) {
            val body = TextLogic.normalizeCase(s.body)
            val tr = QuickWords.lookup(l.code, body) ?: translate(l, TextLogic.forTranslator(body)) ?: return null
            out.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, TextLogic.finishTranslation(body, tr), s.choice))
        }
        return out
    }

    private suspend fun translate(l: Lang, text: String): String? {
        if (l == lang) cache.get(text)?.let { statsFor(this).cacheHit(); return it }
        val out = try { OfflineTranslator.translate(l, text, glossary) } catch (e: Exception) {
            lastError = e.javaClass.simpleName; Log.w(TAG, "offline", e)
            // balíček chybí (stažení se nepovedlo nebo ho systém smazal) → stáhnout znovu, nejvýš jednou za 30 s
            val now = SystemClock.uptimeMillis()
            if (e.message?.contains("model", ignoreCase = true) == true && now - lastModelRetry > 30_000) {
                lastModelRetry = now
                android.os.Handler(android.os.Looper.getMainLooper()).post { if (running) prepareModel(l) }
            }
            null
        }
        // jazyk se mezitím změnil → do paměti nového jazyka nepatří překlad do starého
        if (out != null) { if (l == lang) cache.put(text, out); lastError = null }
        return out
    }

    // ---------------- pokus: DeepL ----------------

    /** Klíč DeepL, když je pokus zapnutý (jinak null a vše běží jako dřív). Nikam se nevypisuje. */
    @Volatile private var deeplKey: String? = null
    /** Pořadí zobrazení v pruhu: odpověď DeepL smí vyměnit jen ten řádek, ke kterému patří. */
    private val showLock = Any()
    private var showSeq = 0L

    /**
     * Souběžně s offline překladem se zeptá DeepL. Text v pruhu se vymění jen tehdy, když odpověď
     * přišla včas, liší se a v pruhu je pořád týž titulek (stejný začátek čtení i pořadí zobrazení).
     */
    private fun improveWithDeepl(job: DeeplTranslator.Job, offline: List<Segment>, token: Long, seq: Long, ov: SubtitleOverlay,
                                 redraw: (List<Segment>) -> Unit) {
        if (!DeeplTranslator.tryStart()) return
        scope.launch {
            val r = DeeplTranslator.fetch(job)
            val texts = r.texts
            if (texts == null) { tp(cz.playlingo.core.Deepl.logLine(r.ms, "error-" + r.problem?.name?.lowercase(), 0)); return@launch }
            val better = DeeplTranslator.merge(offline, job, texts)
            val swap = synchronized(showLock) {
                val current = tracker.isCurrent(token) && seq == showSeq && ov.isShown && overlay === ov
                cz.playlingo.core.Deepl.decide(TextLogic.joinSegments(offline), TextLogic.joinSegments(better), current, r.ms).also {
                    if (it == cz.playlingo.core.Deepl.Swap.YES) redraw(better)
                }
            }
            DeeplTranslator.stats.answer(r.ms, swap, r.chars)
            val label = when (swap) {
                cz.playlingo.core.Deepl.Swap.YES -> "yes"
                cz.playlingo.core.Deepl.Swap.SAME -> "no"
                cz.playlingo.core.Deepl.Swap.LATE -> "late"
                cz.playlingo.core.Deepl.Swap.STALE -> "stale"
            }
            tp(cz.playlingo.core.Deepl.logLine(r.ms, label, r.chars))
        }
    }

    // ---------------- paměť překladů ----------------

    private fun cacheFile() = File(filesDir, "$CACHE_PREFIX${lang.code}.tsv")

    private fun loadCache() {
        try {
            val f = cacheFile()
            if (f.exists()) cache.load(f.readText(Charsets.UTF_8))
        } catch (e: Exception) { Log.w(TAG, "cache load", e) }
    }

    private fun saveCache() {
        try {
            val f = cacheFile()
            val tmp = File(filesDir, f.name + ".tmp")
            tmp.writeText(cache.dump(), Charsets.UTF_8)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) { Log.w(TAG, "cache save", e) }
        stats?.let { Prefs.setStats(this, it) }
    }

    // ---------------- diagnostika ----------------

    private fun publishDebug(ov: SubtitleOverlay) {
        val secs = max(1L, (SystemClock.uptimeMillis() - statsStart) / 1000)
        val mode = if (OfflineTranslator.isReady(lang)) "offline ${lang.code}" else "offline ${lang.code} – model se stahuje"
        val r = reader
        val src = if (demo) "ukázka" else (if (xbox) "Xbox " else "PS5 ") + linkState + (linkReason?.let { " ($it)" } ?: "")
        val ecoInfo = if (perf.eco) " · úsporný režim" else ""
        val zoneInfo = if (!autoFind) "ručně" else if (autoZone.learned != null) "auto – titulky nalezeny" else "auto – hledám"
        val err = lastError?.let { "\nchyba překladu: ${it.take(60)}" } ?: ""
        ov.debug(
            "zdroj: $src · video ${r?.frameWidth}×${r?.frameHeight} · přijato ${r?.fed} · dekódováno ${r?.decoded}\n" +
                "čtení ${ocrRuns / secs}/s · ${lastMs} ms · překlad: $mode · paměť ${cache.size()}$ecoInfo\n" +
                "oblast: $zoneInfo · na obrazovce: " + (TitulkyAccessibility.foregroundPackage ?: "?") + "\n" +
                "čtu: ${lastOcrText.replace('\n', ' ').take(70)}$err"
        )
    }

    override fun onDestroy() {
        if (running) tp("STOP|čtení=$ocrRuns|průměr=${if (ocrRuns > 0) ocrMsSum / ocrRuns else 0}ms|titulků=$shows")
        running = false
        paused = false
        demo = false
        xbox = false
        scope.cancel()
        saveCache()
        overlay?.destroy(); overlay = null
        tv?.stop(); tv = null; activeTv = null
        link?.stop(); link = null
        feeder?.stop(); feeder = null
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        wifiLock = null
        activeReader = null
        reader?.stop(); reader = null
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
