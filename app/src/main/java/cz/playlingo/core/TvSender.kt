package cz.playlingo.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Posílání přeložených titulků do televize LG (webOS) nebo Samsung (Tizen) po domácí síti.
 *
 * Televize LG a Samsung si obraz hry samy přečíst neumí, takže tam PlayLingo běží jen jako
 * obrazovka (`webos/obchod/app` a `tizen/app` se společným app.js): ukazuje hru ze vstupu HDMI
 * a přes ni pruh. Číst a překládat musí jiné
 * zařízení – toto. Aplikace v televizi si ho v síti najde sama (zkouší port [PORT]) a připojí se;
 * odsud jí pak chodí hotové věty.
 *
 * Spojení je WebSocket (starý prohlížeč televize nic jiného pro trvalé spojení nemá) a obslouží
 * se tu ručně, bez knihoven. Zprávy jsou krátké texty:
 *  - `{"typ":"vitej"}` hned po připojení (podle toho televize pozná PlayLingo),
 *  - `{"typ":"ukaz","id":1,"text":"…"}` a `{"typ":"skryj","id":2}`.
 *    Od 0.86 může nést i `"vzhled":{…}` – průhlednost, písmo a polohu pruhu podle Nastavení ([TvLook]).
 *    Od verze s barevnými jmény a volbami může `ukaz` nést navíc `"casti":[…]` – řádky pruhu zvlášť:
 *    `{"jmeno":"Mira","barva":"#F2C14E","text":"…"}` (replika s mluvčím a jeho barvou; barva chybí,
 *    když má uživatel jména bez barvy), `{"volba":true,"text":"…"}` (jedna z voleb odpovědi)
 *    a `{"text":"…"}` (replika bez jména). `text` zůstává celý jako dřív („Jméno: …“, volby s „• “),
 *    takže starší aplikace v televizi, která `casti` nezná, ukáže všechno jako obyčejný text.
 * Televize po připojení pošle `{"typ":"pripojeno","televize":"…","system":"…","hdmi":1}`, od verze 1.1.0
 * navíc `"aplikace":"PlayLingo","platforma":"webos"|"tizen","verze":"1.1.0"` ([TvAnnounce]). Podle toho
 * průvodce nahráním do televize a stránka Domů poznají, že je aplikace v televizi nainstalovaná
 * a v jaké verzi ([lastSeen]). Od verze 1.2.0 televize posílá i svůj záznam událostí
 * `{"typ":"zaznam","radky":["…"]}` (po připojení a pak po dávkách) – ukládá ho [TvLog].
 * Od 0.89 Android posílá `{"typ":"nastaveni",…}` a televize (od 1.4.0) smí poslat `{"typ":"zmen",…}` –
 * pár voleb vzhledu z ovladače, viz [TvSettings].
 * Ostatní zprávy od televize (`hotovo`) se jen přečtou a zahodí.
 * Bez Androidu, aby šlo vyzkoušet v unit testech.
 */
class TvSender(private val log: (String) -> Unit = {}) {

    /** Volá se po každém ohlášení televize (z vlákna spojení) – např. přepnutí nastavení pro tu televizi. */
    @Volatile var onAnnounce: ((TvAnnounce) -> Unit)? = null

    companion object {
        const val PORT = 8787
        private const val MAX_CLIENTS = 4
        private const val MAX_HEAD = 8 * 1024
        private const val MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        /** Delší zprávy od televize se nečtou (ohlášení má pár set bajtů). */
        private const val MAX_IN = 16 * 1024

        /** Poslední ohlášení aplikace z televize (od kteréhokoli TvSender v procesu) a kdy přišlo. */
        class Seen(val announce: TvAnnounce, val at: Long)
        @Volatile var lastSeen: Seen? = null

        /**
         * Přečte jednu zprávu od televize (od prohlížeče jsou vždy maskované). Vrací text textové
         * zprávy, "" pro jinou (ping, binární…), null = konec spojení nebo nesmyslná délka.
         */
        fun readFrame(input: InputStream): String? {
            val b0 = input.read(); val b1 = input.read()
            if (b0 < 0 || b1 < 0) return null
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                val hi = input.read(); val lo = input.read()
                if (hi < 0 || lo < 0) return null
                len = ((hi shl 8) or lo).toLong()
            } else if (len == 127L) return null   // takhle dlouhou zprávu televize neposílá
            if (len > MAX_IN) return null
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { if (!readFully(input, it)) return null } else null
            val data = ByteArray(len.toInt())
            if (!readFully(input, data)) return null
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            return when (opcode) {
                0x8 -> null
                0x1 -> String(data, Charsets.UTF_8)
                else -> ""
            }
        }

        private fun readFully(input: InputStream, buf: ByteArray): Boolean {
            var got = 0
            while (got < buf.size) { val n = input.read(buf, got, buf.size - got); if (n < 0) return false; got += n }
            return true
        }

        const val HELLO = "{\"typ\":\"vitej\"}"
        fun showMessage(id: Int, text: String, parts: String? = null, look: String? = null) =
            "{\"typ\":\"ukaz\",\"id\":$id,\"text\":${json(text)}" + (if (parts != null) ",\"casti\":$parts" else "") +
                (if (look != null) ",\"vzhled\":$look" else "") + "}"

        /** Řádky pruhu pro pole `casti` (JSON); [colors] = barvy jmen, bez jména v ní se barva nepošle. */
        fun partsJson(segments: List<Segment>, colors: Map<String, Int> = emptyMap()): String =
            segments.joinToString(",", "[", "]") { s ->
                when {
                    s.choice -> "{\"volba\":true,\"text\":${json(s.body)}}"
                    s.prompt -> "{\"pokyn\":true,\"text\":${json(s.body)}}"
                    s.speaker != null -> "{\"jmeno\":${json(s.speaker)}" +
                        (colors[s.speaker]?.let { ",\"barva\":\"${SpeakerColors.hex(it)}\"" } ?: "") +
                        ",\"text\":${json(s.body)}}"
                    else -> "{\"text\":${json(s.body)}}"
                }
            }
        fun hideMessage(id: Int) = "{\"typ\":\"skryj\",\"id\":$id}"

        /**
         * Řádek s úkolem pod titulky (od 0.97.43, aplikace v TV 1.5.11) – samostatný pruh, nezávislý na titulku:
         * `{"typ":"ukol","text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":px,"pozadi":0–100,"barva":"#RRGGBB"}`,
         * schování `{"typ":"ukol","text":""}`. [misto] = kde byl anglický úkol (zlomky obrazu 0..1). Starší TV zprávu
         * neznají a nic neukážou. [casti] = barevná slova úkolu (pokus, [ObjectiveColors]):
         * `"casti":[{"text":…,"barva":"#RRGGBB"|null}]` na konci zprávy; bez nich je zpráva stejná jako dřív.
         */
        fun objectiveMessage(text: String?, misto: FloatArray? = null, pismo: Int = 0, pozadi: Int = 100, barva: String = "#FFFFFF",
                             casti: List<ObjectivePart>? = null): String {
            if (text.isNullOrBlank() || misto == null || misto.size < 4) return "{\"typ\":\"ukol\",\"text\":\"\"}"
            val m = misto.joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            val c = casti?.takeIf { p -> p.any { it.color != null } }?.joinToString(",", ",\"casti\":[", "]") { p ->
                "{\"text\":${json(p.text)},\"barva\":" + (p.color?.let { "\"${SpeakerColors.hex(it)}\"" } ?: "null") + "}"
            } ?: ""
            return "{\"typ\":\"ukol\",\"text\":${json(text)},\"misto\":[$m],\"pismo\":${pismo.coerceIn(16, 120)}," +
                "\"pozadi\":${pozadi.coerceIn(0, 100)},\"barva\":\"$barva\"$c}"
        }

        /**
         * Nadpis a popis vybrané volby Nastavení hry (od 0.97.58, aplikace v TV 1.5.13) – vlastní okno přes anglický popis,
         * nezávislé na pruhu (v pruhu je mezitím vybraná záložka): `{"typ":"menupopis","nadpis":…,"text":…,
         * "misto":[vlevo,nahoře,vpravo,dole],"pismo":px,"pozadi":0–100,"barva":"#RRGGBB"}`, text zarovnaný vlevo
         * od levého horního rohu `misto`, šířka `misto`. Schování `{"typ":"menupopis","text":""}`. Starší TV zprávu
         * přeskočí (neznámý typ) a popis neukážou.
         */
        fun menuDescMessage(title: String?, text: String?, misto: FloatArray? = null, pismo: Int = 0, pozadi: Int = 100,
                            barva: String = "#FFFFFF"): String {
            if ((text.isNullOrBlank() && title.isNullOrBlank()) || misto == null || misto.size < 4) return "{\"typ\":\"menupopis\",\"text\":\"\"}"
            val m = misto.joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            return "{\"typ\":\"menupopis\",\"nadpis\":${json(title ?: "")},\"text\":${json(text ?: "")},\"misto\":[$m]," +
                "\"pismo\":${pismo.coerceIn(16, 120)},\"pozadi\":${pozadi.coerceIn(0, 100)},\"barva\":\"$barva\"}"
        }

        /**
         * Stav pro televizi jako Domů v aplikaci: řádky (barva „ok“ / „pozor“ / „info“, text).
         * Aplikace v televizi od 1.5.0 je ukáže pod svými řádky (HDMI, spojení); starší zprávu přeskočí.
         */
        fun statusMessage(rows: List<Pair<String, String>>): String =
            rows.joinToString(",", "{\"typ\":\"stav\",\"radky\":[", "]}") { (b, t) -> "{\"barva\":${json(b)},\"text\":${json(t)}}" }

        /** Text jako řetězec JSON (uvozovky, zpětná lomítka a řídicí znaky se zapíšou bezpečně). */
        fun json(s: String): String {
            val sb = StringBuilder(s.length + 2).append('"')
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> {}
                c == '\t' -> sb.append(' ')
                c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
            return sb.append('"').toString()
        }

        /** Odpověď na klíč z žádosti o WebSocket (podle ní prohlížeč pozná, že mluví se správným protějškem). */
        fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key.trim() + MAGIC).toByteArray(Charsets.US_ASCII)))

        /** Jedna textová zpráva WebSocketu směrem k televizi. */
        fun frame(text: String): ByteArray {
            val data = text.toByteArray(Charsets.UTF_8)
            val out = ByteArrayOutputStream(data.size + 4)
            out.write(0x81)
            if (data.size < 126) out.write(data.size)
            else { out.write(126); out.write(data.size shr 8 and 0xFF); out.write(data.size and 0xFF) }
            out.write(data)
            return out.toByteArray()
        }

        /** Klíč z hlaviček žádosti o WebSocket; null = obyčejný požadavek. */
        fun keyFromHead(head: String): String? = head.split("\r\n").drop(1)
            .firstOrNull { it.substringBefore(':').trim().equals("Sec-WebSocket-Key", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() && it.length < 100 }
    }

    @Volatile var port = 0
        private set

    @Volatile private var server: ServerSocket? = null
    @Volatile private var acceptor: Thread? = null
    @Volatile private var pool: ExecutorService? = null
    /** Zprávy odcházejí z jednoho vlákna, aby pomalá televize nezdržela čtení titulků. */
    @Volatile private var sendQueue: ExecutorService? = null
    private val clients = CopyOnWriteArrayList<Socket>()
    private var nextId = 0
    /** Co je právě na obrazovce (null = pruh schovaný) – nově připojená televize to dostane hned. */
    private var shown: String? = null
    private var shownParts: String? = null
    private var shownLook: String? = null
    /** Zpráva `nastaveni` s volbami, které jdou měnit i v televizi ([TvSettings]); null = neposílat. */
    @Volatile private var settings: String? = null
    /** Poslední stav pro televizi (body s tečkou, aplikace v televizi od 1.5.0); nové televize ho dostanou po připojení. */
    @Volatile private var status: String? = null
    /** Právě zobrazený úkol ([objectiveMessage]); null = žádný. */
    @Volatile private var objective: String? = null
    /** Právě zobrazený popis volby Nastavení hry ([menuDescMessage]); null = žádný. */
    @Volatile private var menuDesc: String? = null
    /** Televize požádala o změnu vzhledu ([TvSettings.parse] už ověřil volbu i hodnotu). */
    @Volatile var onChange: ((TvSettings.Change) -> Unit)? = null

    val running: Boolean get() = server?.isClosed == false

    /** Kolik televizí je právě připojeno. */
    val connected: Int get() = clients.size

    /** Začne čekat na televizi. Vrací false, když port nejde otevřít (už ho drží něco jiného). */
    /**
     * [retry] = když je port zrovna obsazený, zkoušet ho otevřít každé 2 s znovu, dokud se nepovede nebo se nezavolá [stop].
     * Služba překladu to potřebuje (od 0.97.35): po aktualizaci aplikace si port na okamžik vzala obrazovka aplikace
     * (TvIdle) a služba ho už nikdy neotevřela – televize pak jen „hledala v síti“, i když telefon překládal.
     */
    @Synchronized fun start(preferredPort: Int = PORT, bind: InetAddress? = null, retry: Boolean = false): Boolean {
        if (running) return true
        wanted = true
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, preferredPort), 8) }
        } catch (_: Throwable) {
            log("tv cannot listen" + if (retry) " – port $preferredPort obsazený, zkusím znovu" else "")
            if (retry && retrier == null) retrier = Thread({
                while (wanted && !running) {
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                    synchronized(this) { if (wanted && !running && tryOpen(preferredPort, bind)) log("tv port otevřen napodruhé") }
                }
                synchronized(this) { retrier = null }
            }, "tp-tv-retry").apply { isDaemon = true; start() }
            return false
        }
        listen(s)
        return true
    }

    /** Chce se poslouchat (start bez stop) – opakované pokusy o port běží jen do [stop]. */
    @Volatile private var wanted = false
    private var retrier: Thread? = null

    private fun tryOpen(port: Int, bind: InetAddress?): Boolean = try {
        listen(ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, port), 8) }); true
    } catch (_: Throwable) { false }

    private fun listen(s: ServerSocket) {
        server = s
        port = s.localPort
        val p = Executors.newCachedThreadPool { r -> Thread(r, "tp-tv").apply { isDaemon = true } }
        pool = p
        sendQueue = Executors.newSingleThreadExecutor { r -> Thread(r, "tp-tv-send").apply { isDaemon = true } }
        acceptor = Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                try { p.execute { handle(c) } } catch (_: Throwable) { close(c) }
            }
        }, "tp-tv-accept").apply { isDaemon = true; start() }
        log("tv on port $port")
    }

    @Synchronized fun stop() {
        wanted = false
        retrier?.interrupt()
        val s = server ?: return
        server = null
        try { s.close() } catch (_: Throwable) {}
        clients.forEach { close(it) }
        clients.clear()
        pool?.shutdownNow(); pool = null
        sendQueue?.shutdownNow(); sendQueue = null
        acceptor = null
        shown = null
        shownParts = null
        shownLook = null
        log("tv closed")
    }

    /**
     * Ukáže na televizi přeložený titulek. Stejný text podruhé se neposílá.
     * [parts] = řádky zvlášť s barvami jmen a volbami (viz [partsJson]); null = jen obyčejný text.
     * [look] = vzhled a poloha pruhu podle Nastavení (viz [TvLook]); null = televize kreslí po svém.
     */
    @Synchronized fun show(text: String, parts: String? = null, look: String? = null) {
        if (text == shown && parts == shownParts && look == shownLook) return
        shown = text
        shownParts = parts
        shownLook = look
        broadcast(showMessage(++nextId, text, parts, look))
    }

    /** Pošle televizím aktuální volby vzhledu (po spuštění a po každé změně); nové televize je dostanou po připojení. */
    fun sendSettings(message: String) {
        if (message == settings) return
        settings = message
        broadcast(message)
    }

    /** Pošle televizím stav (jen když se změnil); nové televize ho dostanou po připojení. */
    fun sendStatus(message: String) {
        if (message == status) return
        status = message
        broadcast(message)
    }

    /** Úkol pod titulky ([objectiveMessage]) – posílá se jen změna; nové televize ho dostanou po připojení. */
    @Synchronized fun sendObjective(message: String) {
        val m = if (message.contains("\"text\":\"\"")) null else message
        if (m == objective) return
        objective = m
        broadcast(message)
    }

    /** Popis volby Nastavení hry ([menuDescMessage]) – posílá se jen změna; nové televize ho dostanou po připojení. */
    @Synchronized fun sendMenuDesc(message: String) {
        val m = if (message == menuDescMessage(null, null)) null else message
        if (m == menuDesc) return
        menuDesc = m
        broadcast(message)
    }

    /** Schová pruh na televizi. */
    @Synchronized fun hide() {
        if (shown == null) return
        shown = null
        shownParts = null
        shownLook = null
        broadcast(hideMessage(++nextId))
    }

    private fun broadcast(message: String) {
        if (clients.isEmpty()) return
        val bytes = frame(message)
        try { sendQueue?.execute { clients.forEach { write(it, bytes) } } } catch (_: Throwable) {}
    }

    private fun write(c: Socket, bytes: ByteArray) {
        try {
            synchronized(c) { c.getOutputStream().apply { write(bytes); flush() } }
        } catch (_: Throwable) { drop(c) }
    }

    private fun drop(c: Socket) {
        if (clients.remove(c)) log("tv gone, left ${clients.size}")
        close(c)
    }

    private fun close(c: Socket) { try { c.close() } catch (_: Throwable) {} }

    private fun handle(c: Socket) {
        try {
            // titulky patří jen do domácí sítě
            val from = c.inetAddress
            if (from == null || !(from.isLoopbackAddress || PhoneLogin.isHomeAddress(from.hostAddress ?: ""))) { close(c); return }
            c.soTimeout = 8000
            c.tcpNoDelay = true
            val input = c.getInputStream()
            val head = readHead(input) ?: run { close(c); return }
            val key = keyFromHead(head)
            if (key == null) {
                // obyčejný dotaz (např. z prohlížeče): jen dát vědět, že tu PlayLingo je
                c.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 9\r\nConnection: close\r\n\r\nPlayLingo".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                close(c); return
            }
            if (clients.size >= MAX_CLIENTS) { close(c); return }
            c.getOutputStream().apply {
                write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n").toByteArray(Charsets.US_ASCII))
                flush()
            }
            c.soTimeout = 0
            write(c, frame(HELLO))
            // přidání a poslání právě zobrazeného titulku jsou pod stejným zámkem jako show/hide,
            // aby se nová televize o žádnou změnu nepřipravila
            synchronized(this) {
                clients.add(c)
                settings?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                status?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                shown?.let { t -> val b = frame(showMessage(++nextId, t, shownParts, shownLook)); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                objective?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                menuDesc?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
            }
            log("tv connected, now ${clients.size}")
            // Od televize je zajímavé jen ohlášení (platforma a verze); čte se i proto, aby se
            // poznalo zavření spojení.
            while (true) {
                val msg = readFrame(input) ?: break
                TvAnnounce.parse(msg)?.let { a ->
                    lastSeen = Seen(a, System.currentTimeMillis())
                    log("tv says ${a.platform ?: "?"} ${a.version ?: "?"}")
                    try { onAnnounce?.invoke(a) } catch (_: Throwable) {}
                }
                // záznam událostí z televize (od verze 1.2.0) – do souboru pro hlášení
                TvLog.parse(msg)?.let { TvLog.add(it) }
                // změna vzhledu z ovladače televize (od aplikace v televizi 1.4.0) – jen povolené volby a hodnoty
                TvSettings.parse(msg)?.let { ch ->
                    log("tv change ${ch.javaClass.simpleName}")
                    try { onChange?.invoke(ch) } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
        } finally { drop(c) }
    }

    /** Hlavičky žádosti (po prázdný řádek); null, když jsou podezřele dlouhé nebo spojení skončilo. */
    private fun readHead(input: InputStream): String? {
        val end = "\r\n\r\n"
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            match = if (b == end[match].code) match + 1 else if (b == end[0].code) 1 else 0
            if (match == end.length) return String(out.toByteArray(), 0, out.size() - end.length, Charsets.ISO_8859_1)
        }
        return null
    }
}
