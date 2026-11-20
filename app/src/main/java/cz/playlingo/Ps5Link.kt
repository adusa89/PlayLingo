package cz.playlingo

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.ConnectedEvent
import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.DiscoveryService
import com.metallic.chiaki.lib.Event
import com.metallic.chiaki.lib.LoginPinRequestEvent
import com.metallic.chiaki.lib.QuitEvent
import com.metallic.chiaki.lib.Session
import com.metallic.chiaki.lib.VideoFPSPreset
import com.metallic.chiaki.lib.VideoResolutionPreset
import cz.playlingo.core.DiagLog
import cz.playlingo.core.Ps5
import cz.playlingo.core.Ps5Host
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Spojení s PlayStationem: aplikace se připojí stejně jako Remote Play, ale obraz nezobrazuje –
 * jen ho předává čtečce titulků. Neposílá žádné ovládání, hraje se dál ovladačem u konzole
 * a obraz i zvuk jdou do televize kabelem HDMI.
 * Když spojení spadne (konzole usnula, výpadek Wi-Fi), samo se zkouší obnovit.
 */
class Ps5Link(
    private val ctx: Context,
    private var host: Ps5Host,
    /** Brát obraz 1080p (drobné titulky se čtou spolehlivěji), jinak 720p. */
    highRes: Boolean,
    private val onVideo: (ByteArray) -> Boolean,
) : ConsoleLink {
    companion object {
        private const val TAG = "TestPreklad"

        /** Stav spojení pro obrazovku aplikace. */
        @Volatile var state: Ps5.Link = Ps5.Link.OFF
            private set
        /** Poslední důvod odpojení (text z konzole), nebo null. */
        @Volatile var reason: String? = null
            private set
        /** PlayStation chtěl přihlašovací kód účtu (drží se i přes další pokusy, dokud se nespojí). */
        @Volatile var needsLoginPin = false
            private set
        /**
         * Důvod, se kterým spojení naposledy ukončil sám PlayStation (null = neukončil, nebo se od té
         * doby zase spojilo). Jen pro zobrazení na Domů – na opakování spojení nemá vliv.
         */
        @Volatile var endedByConsole: String? = null
            private set
        /**
         * PlayStation spí a PlayLingo ho nebudí (volba „Probouzet PlayStation“ vypnutá, výchozí od 0.97.18):
         * čeká se, až ho hráč zapne ovladačem na svém účtu. Pro Domů a stav v TV („PS5 · spí – zapni ovladačem“).
         */
        @Volatile var asleep = false
            private set
        /** Remote Play na PS5 teď používá jiné zařízení (stejný účet připojený jinde) – pro hlášku na Domů. */
        @Volatile var otherDevice = false
            private set
        /** Proč PS5 odmítla spojení ještě před obrazem (ze záznamu knihovny, [Ps5.refusal]); null = neodmítla. */
        @Volatile var refusal: Ps5.Refusal? = null
            private set
        private val endings = ArrayDeque<String>()

        /** Posledních pár ukončení ze strany PlayStationu (čas, důvod, jak dlouho spojení vydrželo) pro hlášení. */
        fun recentEndings(): List<String> = synchronized(endings) { endings.toList() }

        private fun noteEnding(line: String) = synchronized(endings) {
            endings.addLast(line)
            while (endings.size > 8) endings.removeFirst()
        }

        /** Kdy se navázalo právě běžící spojení (uptime, 0 = nespojeno) – pro stav „obraz přichází“. */
        @Volatile var connectedSince = 0L
            private set

        fun logFile(ctx: Context) = File(ctx.cacheDir, "ps5-spojeni.txt")

        /** Je nativní knihovna pro spojení v pořádku? (zkouší se i v emulátoru) */
        fun selfTest(): String = try {
            val p = ConnectVideoProfile.preset(VideoResolutionPreset.RES_720P, VideoFPSPreset.FPS_30, Codec.CODEC_H264)
            "ok ${p.width}x${p.height}@${p.maxFPS} ${p.bitrate}kb"
        } catch (e: Throwable) { "FAIL " + e.javaClass.simpleName + ": " + e.message }
    }

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tp-ps5") }
    private var session: Session? = null
    private var failures = 0
    /** Obraz 1080p; když s ním spojení nedrží, přejde se natrvalo na 720p. */
    private var useHigh = highRes
    /** Kolikrát po sobě se spojení navázalo a hned zase spadlo (nebo nepřišlo video). */
    private var shortSessions = 0
    /** Kdy se právě běžící relace připojila (0 = ještě ne). */
    @Volatile private var sessionConnectedAt = 0L
    private var retry: ScheduledFuture<*>? = null
    @Volatile private var stopped = false
    @Volatile private var connectedAt = 0L
    @Volatile private var everConnected = false

    private fun log(msg: String) { Log.i("TP_TEST", "PS5|$msg"); DiagLog.add("PS5|$msg"); EventFile.add("PS5|$msg") }

    override fun start() {
        stopped = false
        endedByConsole = null
        otherDevice = false
        refusal = null
        asleep = false
        exec.execute { connectWhenAwake() }
    }

    /**
     * Budit se smí jen s volbou [Prefs.ps5Wake] – probuzení přes síť zapne konzoli na účtu, se kterým je
     * PlayLingo spárované (druhý účet), a ne na výběr účtu. Bez volby: dokud konzole spí, jen se každých 8 s
     * podívá (hledání v síti hlásí klidový režim) a připojí se, až ji hráč zapne.
     */
    /** Konzole v síti už je v záznamu za tohle spuštění (vypisuje se jen jednou, při prvním pokusu). */
    private var consolesLogged = false

    /**
     * Pro hlášení (od 0.97.32): které konzole aplikace v síti vidí a jestli mezi nimi je ta spárovaná (podle MAC
     * i adresy), v jakém je stavu, a jakým účtem se párovalo. Bez klíčů; MAC jen poslední 4 znaky.
     */
    private fun logConsoles() {
        consolesLogged = true
        try {
            val mac = Ps5.hex(host.mac)
            val list = Ps5Pairing.discover(1500)
            val mine = list.firstOrNull { mac.isNotEmpty() && it.id.equals(mac, ignoreCase = true) }
            log("v síti: " + (if (list.isEmpty()) "žádná konzole" else list.joinToString("; ") {
                "${it.name} ${it.ip} …${it.id.takeLast(4)} " + (if (it.standby) "klid" else "zapnutá") + (if (it.ps5) "" else " (PS4)")
            }))
            log("spárováno: ${host.name} ${host.ip} …${mac.takeLast(4)} · účet ${Prefs.psnName(ctx).ifEmpty { "?" }} · " + when {
                mine == null && list.any { it.ip == host.ip } -> "na adrese ${host.ip} je JINÁ konzole"
                mine == null -> "spárovaná konzole v síti nenalezena"
                mine.ip != host.ip -> "konzole má novou adresu ${mine.ip}"
                else -> "nalezena, adresa sedí"
            })
        } catch (e: Throwable) { log("v síti: chyba hledání ${e.javaClass.simpleName}") }
    }

    private fun connectWhenAwake() {
        if (stopped || session != null) return
        if (!consolesLogged) logConsoles()
        // spící konzoli nebudit: pokus o spojení s PS5 v klidovém režimu ji zapne. Do 0.97.36 se tohle hlídalo jen
        // před prvním spojením – když hráč PS5 během překladu vypnul, aplikace se připojovala dál a PS5 se sama
        // zapnula (testeři majitele, 20. 11. 2026). Volba „Probouzet“ budí jen při spuštění překladu, ne potom.
        if ((everConnected || !Prefs.ps5Wake(ctx)) && sleeping()) {
            if (!asleep) log("console in rest mode, waiting (wake off)")
            asleep = true
            state = Ps5.Link.WAITING
            retry?.cancel(false)
            retry = exec.schedule({ connectWhenAwake() }, 8000, TimeUnit.MILLISECONDS)
            return
        }
        asleep = false
        connect()
    }

    /** Konzole se v síti hlásí v klidovém režimu? (Nenalezená = nevíme → zkusí se připojit jako dřív.) */
    private fun sleeping(): Boolean = try {
        val mac = Ps5.hex(host.mac)
        Ps5Pairing.discover(1500).firstOrNull { it.ip == host.ip || (mac.isNotEmpty() && it.id.equals(mac, ignoreCase = true)) }?.standby == true
    } catch (_: Throwable) { false }

    private fun connect() {
        if (stopped || session != null) return
        state = Ps5.Link.CONNECTING
        connectedSince = 0
        sessionConnectedAt = 0
        try {
            val f = logFile(ctx)
            try { if (f.length() > 400_000) f.delete() } catch (_: Exception) {}
            // Na Wi-Fi 2,4 GHz jen menší obraz (stejné pásmo používají ovladače konzole).
            val net = NetInfo.kind(ctx)
            val high = useHigh && Ps5.allowHighRes(net)
            val profile = ConnectVideoProfile.preset(
                if (high) VideoResolutionPreset.RES_1080P else VideoResolutionPreset.RES_720P, VideoFPSPreset.FPS_30, Codec.CODEC_H264)
            log("profile ${profile.width}x${profile.height} ${profile.bitrate}kb net=$net")
            val s = Session(ConnectInfo(true, host.ip, host.registKey, host.morning, profile), f.absolutePath, false)
            s.eventCallback = { e -> onEvent(s, e) }
            s.videoCallback = onVideo
            session = s
            val err = s.start()
            log("connect ${host.ip} → $err")
            if (!err.isSuccess) { reason = err.toString(); drop(s); failed() }
        } catch (e: CreateError) {
            reason = e.errorCode.toString()
            log("create error ${e.errorCode}")
            failed()
        } catch (e: Throwable) {
            reason = e.javaClass.simpleName
            log("error $e")
            failed()
        }
    }

    /** Volá se z vlákna knihovny – nic těžkého tady, jen předat dál. */
    private fun onEvent(s: Session, e: Event) {
        when (e) {
            is ConnectedEvent -> {
                log("connected")
                state = Ps5.Link.CONNECTED
                reason = null
                endedByConsole = null
                otherDevice = false
                refusal = null
                needsLoginPin = false
                connectedAt = SystemClock.uptimeMillis()
                sessionConnectedAt = connectedAt
                connectedSince = connectedAt
                everConnected = true
                // počítadlo neúspěchů se nuluje až poté, co spojení chvíli vydrží (viz failed())
            }
            is LoginPinRequestEvent -> {
                log("login pin requested")
                needsLoginPin = true
                val pin = Prefs.ps5LoginPin(ctx)
                if (pin.length == 4 && !e.pinIncorrect) s.setLoginPin(pin)
                else exec.execute { reason = "login-pin"; drop(s); state = Ps5.Link.FAILED }
            }
            is QuitEvent -> {
                // pro hlášení: jak dlouho spojení drželo a na jaké síti (kvůli rozboru odpojování)
                val heldS = if (sessionConnectedAt > 0) (SystemClock.uptimeMillis() - sessionConnectedAt) / 1000 else -1
                log("quit ${e.reason} ${e.reasonString ?: ""} | trvalo=${heldS}s | síť=${NetInfo.kind(ctx)} | pokus=${failures + 1}")
                reason = e.reasonString?.takeIf { it.isNotBlank() } ?: e.reason.toString()
                if (Ps5.otherDevice(e.reason.value, e.reasonString)) {
                    otherDevice = true
                    log("quit: Remote Play převzalo jiné zařízení (stejný účet připojený jinde)")
                }
                // PS5 odmítla spojení ještě před obrazem: skutečný důvod je jen v záznamu knihovny – vytáhnout ho
                // do záznamu aplikace a na Domů (jen při neúspěchu, čte se konec souboru, žádná práce navíc za běhu)
                if (heldS < 0) {
                    val tail = try {
                        logFile(ctx).takeIf { it.exists() }?.let { f ->
                            java.io.RandomAccessFile(f, "r").use { raf ->
                                val from = maxOf(0L, raf.length() - 6000)
                                raf.seek(from)
                                val b = ByteArray((raf.length() - from).toInt()); raf.readFully(b); String(b, Charsets.UTF_8)
                            }
                        }
                    } catch (_: Exception) { null } ?: ""
                    refusal = Ps5.refusal(tail) ?: Ps5.Refusal.UNKNOWN.takeIf { e.reasonString?.contains("Session Request", ignoreCase = true) == true }
                    // řádky knihovny k odmítnutí (bez klíčů – registrační klíč se do záznamu nepíše celý, i tak přes Redact)
                    tail.lines().filter { l -> listOf("Application Reason", "Session request", "session request", "RP-Version", "Nonce", "Regist")
                        .any { it in l } && "RP-Registkey" !in l }.takeLast(4)
                        .forEach { log("refused: " + it.trim().take(160)) }
                    log("refusal ${refusal ?: "?"} (host ${host.ip}, spárováno ${host.name})")
                }
                if (!stopped) {
                    endedByConsole = reason
                    val held = if (sessionConnectedAt > 0) (SystemClock.uptimeMillis() - sessionConnectedAt) / 1000 else -1
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
                    noteEnding("$time ${e.reason} ${reason}" + (if (held >= 0) " · spojení trvalo $held s" else " · před spojením"))
                }
                exec.execute { drop(s); failed() }
            }
            else -> {}
        }
    }

    /** Ukončí a uvolní relaci (jen z pracovního vlákna – čeká na vlákno knihovny). */
    private fun drop(s: Session) {
        if (session === s) session = null
        try { s.eventCallback = null; s.videoCallback = null; s.stop(); s.dispose() } catch (e: Throwable) { Log.w(TAG, "ps5 drop", e) }
    }

    private fun failed() {
        if (stopped) return
        // Jak dopadla relace, která právě skončila?
        val since = sessionConnectedAt
        sessionConnectedAt = 0
        val dropped = since > 0     // relace byla připojená a rozpadla se
        if (since > 0) {
            if (SystemClock.uptimeMillis() - since >= Ps5.STABLE_SESSION_MS) { failures = 0; shortSessions = 0 }
            else shortSessions++
        }
        // Spojení se naváže a hned spadne: každé takové připojení konzole hlásí na obrazovce a ruší
        // ovladač, proto se to nesmí opakovat donekonečna.
        when (Ps5.afterShortSessions(shortSessions, useHigh)) {
            Ps5.Unstable.LOWER_QUALITY -> {
                log("unstable with 1080p → 720p")
                useHigh = false
                Prefs.setHighResFailed(ctx, true)
            }
            Ps5.Unstable.GIVE_UP -> {
                log("unstable → giving up")
                reason = "unstable"
                state = Ps5.Link.FAILED
                retry?.cancel(false)
                return
            }
            Ps5.Unstable.RETRY -> {}
        }
        failures++
        state = if (failures >= 3) Ps5.Link.FAILED else Ps5.Link.WAITING
        // po rozpadlém spojení se nezkouší hned znovu – rychlé připojování dokola ruší konzoli i ovladače
        val delay = maxOf(Ps5.retryDelayMs(failures), if (dropped) Ps5.AFTER_DROP_MS else 0L)
        retry?.cancel(false)
        retry = exec.schedule({
            if (stopped) return@schedule
            // Budit konzoli jen na začátku (uživatel právě spustil překlad). Jakmile jednou běžela
            // a pak se odmlčela, nejspíš ji uživatel vypnul – a tu už aplikace znovu zapínat nesmí.
            if (!everConnected && failures <= 2 && Prefs.ps5Wake(ctx)) wake()
            if (failures == 2 || failures % 5 == 0) relocate()
            connectWhenAwake()
        }, delay, TimeUnit.MILLISECONDS)
    }

    /** Router mohl dát konzoli jinou adresu → najdi ji v síti znovu (podle jejího čísla MAC). */
    private fun relocate() {
        try {
            val mac = Ps5.hex(host.mac)
            val list = Ps5Pairing.discover(3000)
            val hit = list.firstOrNull { it.id.equals(mac, ignoreCase = true) }
                ?: list.filter { it.ps5 }.singleOrNull()?.takeIf { mac.isEmpty() || it.id.isEmpty() }
            if (hit != null && hit.ip != host.ip) {
                log("address changed ${host.ip} → ${hit.ip}")
                host = host.withIp(hit.ip)
                Prefs.setPs5Host(ctx, host)
            }
        } catch (e: Throwable) { Log.w(TAG, "relocate", e) }
    }

    /** Probudí konzoli z klidového režimu (když to má v nastavení povolené). */
    private fun wake() {
        val cred = Ps5.wakeCredential(host.registKey) ?: return
        try { DiscoveryService.wakeup(null, host.ip, cred, true) } catch (e: Throwable) { Log.w(TAG, "wake", e) }
    }

    /** Spojení „visí“ (připojeno, ale video nechodí) → začni znovu. */
    override fun restart(why: String) {
        exec.execute {
            if (stopped) return@execute
            log("restart: $why")
            session?.let { drop(it) }
            reason = why
            failed()
        }
    }

    override val connectedForMs: Long get() = if (state == Ps5.Link.CONNECTED) SystemClock.uptimeMillis() - connectedAt else 0

    override fun stop() {
        stopped = true
        retry?.cancel(false)
        exec.execute {
            session?.let { drop(it) }
            state = Ps5.Link.OFF
            connectedSince = 0
        }
        exec.shutdown()
    }
}
