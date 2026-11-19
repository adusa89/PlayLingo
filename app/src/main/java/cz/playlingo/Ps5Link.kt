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
        exec.execute { connect() }
    }

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
                log("quit ${e.reason} ${e.reasonString ?: ""}")
                reason = e.reasonString?.takeIf { it.isNotBlank() } ?: e.reason.toString()
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
            if (!everConnected && failures <= 2) wake()
            if (failures == 2 || failures % 5 == 0) relocate()
            connect()
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
