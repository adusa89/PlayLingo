package cz.playlingo.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Záznam pro vývojáře v domácí síti (Nastavení → Experimentální → „Zpřístupnit záznam v domácí síti“, výchozí
 * vypnuto, samo se vypne po [DURATION_MS]). Zařízení pak na portu [PORT] vydá celé hlášení o stavu
 * (stejné jako obrazovka hlášení + záznam událostí, záznam z televize a pády), vždy přes [Redact]:
 *  - `GET /zaznam?k=KÓD` → text,
 *  - `GET /zaznam.json?k=KÓD` → totéž po oddílech v JSON.
 * Kód je krátký a náhodný, ukazuje se na obrazovce. Jen čtení, jen adresy domácí sítě.
 * Rozhodování je tady bez Androidu (testy `LogShareTest`); server je [LogServer].
 */
object LogShare {
    const val PORT = 8789
    /** Jak dlouho zůstane zapnuté (pak se samo vypne). */
    const val DURATION_MS = 2 * 60 * 60 * 1000L
    /** Po tolika požadavcích se špatným kódem se server zavře (někdo kód zkouší). */
    const val MAX_WRONG = 30

    /** Bez znaků, které jde splést (0/o, 1/l/i). */
    private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

    fun newCode(random: java.util.Random = java.security.SecureRandom()): String =
        (1..6).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")

    fun validCode(k: String?) = k != null && k.length in 6..16 && k.all { it in CODE_CHARS }

    /** Porovnání kódu bez prozrazení, kolik znaků sedělo. */
    fun sameCode(a: String?, b: String?): Boolean {
        if (a == null || b == null || a.length != b.length) return false
        var d = 0
        for (i in a.indices) d = d or (a[i].code xor b[i].code)
        return d == 0
    }

    enum class Route { TEXT, JSON, BAD_CODE, NOT_FOUND, BAD_METHOD, BAD_REQUEST }

    /** Co udělat s požadavkem (první řádek HTTP) při platném kódu [code]. */
    fun route(requestLine: String, code: String): Route {
        val p = requestLine.trim().split(' ')
        if (p.size != 3 || !p[2].startsWith("HTTP/")) return Route.BAD_REQUEST
        if (p[0] != "GET") return Route.BAD_METHOD
        val target = p[1]
        val path = target.substringBefore('?').substringBefore('#')
        val kind = when (path) {
            "/zaznam", "/zaznam.txt" -> Route.TEXT
            "/zaznam.json" -> Route.JSON
            else -> return Route.NOT_FOUND
        }
        val k = query(target)["k"]
        return if (sameCode(k, code)) kind else Route.BAD_CODE
    }

    /** Parametry za otazníkem (jen jednoduché hodnoty; %xx a + se převedou). */
    fun query(target: String): Map<String, String> {
        val q = target.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { kv ->
            val k = kv.substringBefore('=')
            if (k.isEmpty()) null else k to (runCatching { java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: "")
        }.toMap()
    }

    /** Smí se tahle adresa ptát? Jen zařízení sám (loopback) a domácí síť. */
    fun allowedPeer(host: String?): Boolean =
        host != null && (host == "127.0.0.1" || host == "::1" || host == "0:0:0:0:0:0:0:1" || PhoneLogin.isHomeAddress(host.removePrefix("::ffff:")))

    /** Kolik zbývá do vypnutí (0 = už mělo být vypnuté). */
    fun remainingMs(until: Long, now: Long): Long = (until - now).coerceAtLeast(0)

    /** Platí zapnutí? (Hodiny posunuté o víc než celou dobu dopředu = neplatí.) */
    fun active(until: Long, now: Long): Boolean = until > now && until - now <= DURATION_MS

    /** Zbývající minuty pro obrazovku (zaokrouhleno nahoru). */
    fun minutesLeft(until: Long, now: Long): Long = (remainingMs(until, now) + 59_999) / 60_000

    fun url(ip: String, port: Int, code: String, json: Boolean = false) =
        "http://$ip:$port/zaznam" + (if (json) ".json" else "") + "?k=$code"

    /** Celý text z oddílů (název, obsah); prochází [Redact]. */
    fun text(parts: List<Pair<String, String>>, secrets: Collection<String> = emptyList()): String =
        Redact.clean(parts.joinToString("\n\n") { (t, s) -> "=== $t ===\n${s.trimEnd()}" } + "\n", secrets)

    /** Totéž v JSON: {"oddily":[{"nazev":"…","text":"…"}]}; prochází [Redact]. */
    fun json(parts: List<Pair<String, String>>, secrets: Collection<String> = emptyList()): String =
        parts.joinToString(",", "{\"oddily\":[", "]}") { (t, s) ->
            "{\"nazev\":${TvSender.json(t)},\"text\":${TvSender.json(Redact.clean(s, secrets))}}"
        }

    /** Odpověď HTTP (text nebo JSON). */
    fun response(status: Int, body: String, json: Boolean = false): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: " + (if (json) "application/json" else "text/plain") + "; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + bytes
    }
}

/**
 * Server záznamu pro vývojáře (viz [LogShare]). Jen GET, jen domácí síť, jen s kódem.
 * [parts] sestaví oddíly hlášení (volá se mimo hlavní vlákno), [secrets] tajné hodnoty pro [Redact],
 * [open] = smí ještě běžet (po vypršení se sám zavře), [onStop] = zavřel se sám.
 */
class LogServer(
    val code: String,
    private val parts: () -> List<Pair<String, String>>,
    private val secrets: () -> Collection<String> = { emptyList() },
    private val open: () -> Boolean = { true },
    private val onStop: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    @Volatile var port = 0
        private set
    @Volatile private var server: ServerSocket? = null
    @Volatile private var pool: ExecutorService? = null
    @Volatile private var wrong = 0

    val running: Boolean get() = server?.isClosed == false

    @Synchronized fun start(preferredPort: Int = LogShare.PORT, bind: InetAddress? = null): Boolean {
        if (running) return true
        val s = open(preferredPort, bind) ?: open(0, bind) ?: run { log("log cannot listen"); return false }
        server = s
        port = s.localPort
        val p = Executors.newFixedThreadPool(2) { r -> Thread(r, "tp-zaznam").apply { isDaemon = true } }
        pool = p
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                try { p.execute { handle(c) } } catch (_: Throwable) { close(c) }
            }
        }, "tp-zaznam-accept").apply { isDaemon = true; start() }
        log("log on port $port")
        return true
    }

    private fun open(p: Int, bind: InetAddress?): ServerSocket? = try {
        ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, p), 4) }
    } catch (_: Throwable) { null }

    @Synchronized fun stop() {
        val s = server ?: return
        server = null
        try { s.close() } catch (_: Throwable) {}
        pool?.shutdown(); pool = null
        log("log closed")
    }

    private fun close(c: Socket) { try { c.close() } catch (_: Throwable) {} }

    private fun handle(c: Socket) {
        try {
            if (!LogShare.allowedPeer(c.inetAddress?.hostAddress)) return
            if (!open()) { send(c, 404, "?"); stopSelf(); return }
            c.soTimeout = 8000
            val head = readHead(c.getInputStream()) ?: return
            when (val r = LogShare.route(head.substringBefore("\r\n"), code)) {
                LogShare.Route.TEXT, LogShare.Route.JSON -> {
                    val p = parts()
                    val sec = secrets()
                    if (r == LogShare.Route.JSON) send(c, 200, LogShare.json(p, sec), json = true)
                    else send(c, 200, LogShare.text(p, sec))
                    log("log served " + (if (r == LogShare.Route.JSON) "json" else "text"))
                }
                LogShare.Route.BAD_CODE -> {
                    send(c, 403, "?")
                    if (++wrong > LogShare.MAX_WRONG) { log("log too many wrong codes"); stopSelf() }
                }
                LogShare.Route.BAD_METHOD -> send(c, 405, "?")
                LogShare.Route.NOT_FOUND -> send(c, 404, "?")
                LogShare.Route.BAD_REQUEST -> send(c, 400, "?")
            }
        } catch (_: Throwable) {
        } finally { close(c) }
    }

    private fun stopSelf() { stop(); onStop() }

    private fun send(c: Socket, status: Int, body: String, json: Boolean = false) {
        c.getOutputStream().apply { write(LogShare.response(status, body, json)); flush() }
    }

    private fun readHead(input: InputStream): String? {
        val end = "\r\n\r\n"
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < 8 * 1024) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            match = if (b == end[match].code) match + 1 else if (b == end[0].code) 1 else 0
            if (match == end.length) return String(out.toByteArray(), 0, out.size() - end.length, Charsets.ISO_8859_1)
        }
        return null
    }
}
