package cz.playlingo.pc

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import cz.playlingo.core.Ps5
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.util.zip.ZipInputStream

/**
 * chiaki-ng – program pro Remote Play, ve kterém se na počítači hraje. PlayLingo ho obsluhuje
 * samo: stáhne přenosnou verzi z GitHubu (do složky nástrojů, nic se neinstaluje), najde PS5
 * v síti, probudí ji a spustí hru rovnou přes celou obrazovku (`chiaki stream <konzole> <adresa>
 * --fullscreen --exit-app-on-stream-exit`). Hráč tak vidí jen hru s pruhem PlayLinga.
 *
 * Spárování s PS5 dělá PlayLingo samo ([Registrace], průvodce v `Parovani.kt`) a výsledek zapíše
 * tam, kam si ho ukládá chiaki-ng: do registru Windows (`HKCU\Software\Chiaki\Chiaki\registered_hosts`,
 * [uloz]). Odtud ho chiaki-ng při `chiaki stream` vezme a PlayLingo odtud bere jméno konzole a číslo
 * pro probuzení. Klíče z registru se nikam nevypisují.
 */
object Chiaki {
    val slozka = File(Nastroje.slozka, "chiaki-ng")
    private const val REPO = "streetpea/chiaki-ng"
    private const val REG = "Software\\Chiaki\\Chiaki"

    /** chiaki.exe – stažený do složky nástrojů, nebo nainstalovaný. */
    fun exe(): File? {
        val mista = listOf(
            slozka,
            File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "chiaki-ng"),
            File(System.getenv("LOCALAPPDATA") ?: "", "Programs\\chiaki-ng"),
        )
        for (m in mista) {
            if (!m.isDirectory) continue
            m.walkTopDown().maxDepth(3).firstOrNull {
                it.isFile && it.name.equals("chiaki.exe", ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    // ---- stažení ----

    /** Přenosná verze pro tento počítač (x64 / ARM) z posledního vydání. Vrací chybu, nebo null = hotovo. */
    fun stahni(postup: (Int) -> Unit, log: (String) -> Unit): String? = try {
        val arch = if (System.getProperty("os.arch").contains("aarch64")) "win_arm64" else "win_x64"
        val vydani = http("https://api.github.com/repos/$REPO/releases/latest").readText()
        // jen adresy příloh: …/releases/download/<verze>/chiaki-ng-win_x64-MSYS2-portable-<verze>.zip
        val adresa = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"").findAll(vydani).map { it.groupValues[1] }
            .firstOrNull { it.contains(arch) && it.contains("portable") && it.endsWith(".zip") }
            ?: error("ve vydání chiaki-ng není přenosná verze pro $arch")
        log("Stahuji ${adresa.substringAfterLast('/')}…")
        val zip = File(Nastroje.slozka, "chiaki-ng.zip")
        val c = http(adresa)
        val celkem = c.contentLengthLong
        var hotovo = 0L
        var procent = -1
        c.inputStream.use { vstup ->
            zip.outputStream().use { vystup ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = vstup.read(buf)
                    if (n < 0) break
                    vystup.write(buf, 0, n)
                    hotovo += n
                    if (celkem > 0) {
                        val p = (hotovo * 100 / celkem).toInt()
                        if (p != procent) { procent = p; postup(p) }
                    }
                }
            }
        }
        log("Rozbaluji…")
        slozka.deleteRecursively()
        rozbal(zip, slozka)
        zip.delete()
        if (exe() == null) "v balíčku chiaki-ng chybí chiaki.exe" else null
    } catch (e: Exception) { e.message ?: e.toString() }

    private fun http(url: String): HttpURLConnection {
        var u = url
        repeat(5) {
            val c = URI(u).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "PlayLingo")
            val s = c.responseCode
            if (s in 300..399) { u = c.getHeaderField("Location") ?: error("přesměrování bez adresy"); c.disconnect(); return@repeat }
            if (s != 200) error("GitHub odpověděl $s")
            return c
        }
        error("moc přesměrování")
    }

    private fun HttpURLConnection.readText() = inputStream.use { String(it.readBytes(), Charsets.UTF_8) }

    private fun rozbal(zip: File, kam: File) {
        val koren = kam.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val cil = File(koren, e.name).canonicalFile
                // nic mimo cílovou složku (cesty s „..“ v balíčku)
                if (!cil.path.startsWith(koren.path + File.separator)) continue
                if (e.isDirectory) cil.mkdirs() else {
                    cil.parentFile.mkdirs()
                    cil.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }

    // ---- spárované konzole (z nastavení chiaki-ng) ----

    data class Konzole(val jmeno: String, val probuzeni: ULong?)

    fun konzole(): List<Konzole> = runCatching {
        val k = "$REG\\registered_hosts"
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, k)) return emptyList()
        Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, k).sortedBy { it.toIntOrNull() ?: 0 }.mapNotNull { i ->
            val h = "$k\\$i"
            val jmeno = hodnota(h, "server_nickname")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Konzole(jmeno, hodnota(h, "rp_regist_key")?.let { probuzeni(it) })
        }
    }.getOrDefault(emptyList())

    /** Hodnota z registru jako text – chiaki-ng (Qt) ukládá bajty jako „@ByteArray(…)“, případně v UTF-16. */
    private fun hodnota(klic: String, nazev: String): String? {
        if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, klic, nazev)) return null
        return when (val v = Advapi32Util.registryGetValue(WinReg.HKEY_CURRENT_USER, klic, nazev)) {
            is String -> v
            is ByteArray -> String(v, Charsets.UTF_16LE)
            else -> v?.toString()
        }
    }

    /** Číslo pro probuzení z klíče registrace (šestnáctkový text na začátku, jako [Ps5.wakeCredential]). */
    private fun probuzeni(ulozeno: String): ULong? {
        val text = ulozeno.removePrefix("@ByteArray(")
        val hex = text.takeWhile { it.isLetterOrDigit() }
        return hex.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.toULongOrNull(16)
    }

    // ---- PS5 v síti ----

    data class Nalez(val adresa: String, val jmeno: String, val pripravena: Boolean, val klid: Boolean)

    /** Hledání PlayStationů v domácí síti (stejná zpráva jako v aplikaci, `core/Ps5.kt`). */
    fun hledej(ms: Int = 1500): List<Nalez> {
        val vysledek = LinkedHashMap<String, Nalez>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 250
            val zprava = Ps5.searchPacket(true)
            val cile = vysilani()
            val konec = System.currentTimeMillis() + ms
            var dalsi = 0L
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < konec) {
                if (System.currentTimeMillis() >= dalsi) {
                    for (c in cile) runCatching { s.send(DatagramPacket(zprava, zprava.size, c, Ps5.DISCOVERY_PORT_PS5)) }
                    dalsi = System.currentTimeMillis() + 500
                }
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val r = Ps5.parseDiscoveryReply(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                    if (!r.type.contains("PS5", ignoreCase = true) && r.type.isNotEmpty()) continue
                    val a = p.address.hostAddress
                    vysledek[a] = Nalez(a, r.name, r.ready, r.standby)
                } catch (_: SocketTimeoutException) {}
            }
        }
        return vysledek.values.toList()
    }

    private fun vysilani(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        out += InetAddress.getByName("255.255.255.255")
        runCatching {
            for (n in NetworkInterface.getNetworkInterfaces()) {
                if (!n.isUp || n.isLoopback || n.isVirtual) continue
                n.interfaceAddresses.mapNotNullTo(out) { it.broadcast }
            }
        }
        return out.toList()
    }

    /** Probuzení PS5 z klidového režimu (formát zprávy jako v chiaki-ng, `lib/src/discovery.c`). */
    fun probud(adresa: String, cislo: ULong) {
        val zprava = ("WAKEUP * HTTP/1.1\nclient-type:vr\nauth-type:R\nmodel:w\napp-type:r\n" +
            "user-credential:$cislo\ndevice-discovery-protocol-version:00030010\n").toByteArray()
        DatagramSocket().use { s ->
            repeat(3) { s.send(DatagramPacket(zprava, zprava.size, InetAddress.getByName(adresa), Ps5.DISCOVERY_PORT_PS5)) }
        }
    }

    // ---- spuštění ----

    /** Hra rovnou přes celou obrazovku; chiaki-ng se po konci hraní sám zavře. */
    fun hraj(exe: File, konzole: String, adresa: String): Process =
        ProcessBuilder(exe.path, "stream", konzole, adresa, "--fullscreen", "--exit-app-on-stream-exit")
            .directory(exe.parentFile).redirectErrorStream(true)
            .redirectOutput(File(Nastroje.slozka, "chiaki-vystup.txt")).start()

    /** Běží chiaki-ng? Při zavření by si přepsal seznam konzolí tím, co měl v paměti. */
    fun bezi(): List<ProcessHandle> = ProcessHandle.allProcesses().filter { p ->
        p.info().command().map { File(it).name.equals("chiaki.exe", ignoreCase = true) }.orElse(false)
    }.toList()

    // ---- zápis spárované konzole (tvar jako QSettings v chiaki-ng, `RegisteredHost::SaveToSettings`) ----

    /**
     * Uloží spárovanou konzoli mezi `registered_hosts` chiaki-ng. Stejná konzole (podle MAC) se
     * přepíše, jinak se přidá na konec. Qt ukládá pole jako podklíče 1…N a hodnotu `size`, čísla
     * jako REG_DWORD, text jako REG_SZ a bajty jako text „@ByteArray(…)“ (Latin-1) v UTF-16 –
     * s nulami uvnitř jako REG_BINARY.
     */
    fun uloz(k: Registrace.Konzole) {
        val hk = WinReg.HKEY_CURRENT_USER
        val pole = "$REG\\registered_hosts"
        if (!Advapi32Util.registryKeyExists(hk, pole)) Advapi32Util.registryCreateKey(hk, pole)
        val indexy = Advapi32Util.registryGetKeys(hk, pole).mapNotNull { it.toIntOrNull() }
        val stejny = indexy.firstOrNull { i -> bajty(hodnota("$pole\\$i", "server_mac"))?.contentEquals(k.mac) == true }
        val i = stejny ?: ((indexy.maxOrNull() ?: 0) + 1)
        val h = "$pole\\$i"
        if (!Advapi32Util.registryKeyExists(hk, h)) Advapi32Util.registryCreateKey(hk, h)
        Advapi32Util.registrySetIntValue(hk, h, "target", 1000100) // CHIAKI_TARGET_PS5_1
        for ((n, v) in k.ap) Advapi32Util.registrySetStringValue(hk, h, n, qtText(v))
        Advapi32Util.registrySetStringValue(hk, h, "server_nickname", qtText(k.jmeno))
        Advapi32Util.registrySetBinaryValue(hk, h, "server_mac", qtBajty(k.mac))
        Advapi32Util.registrySetBinaryValue(hk, h, "rp_regist_key", qtBajty(k.klicRegistrace))
        Advapi32Util.registrySetIntValue(hk, h, "rp_key_type", k.typKlice)
        Advapi32Util.registrySetBinaryValue(hk, h, "rp_key", qtBajty(k.klicRelace))
        Advapi32Util.registrySetStringValue(hk, h, "console_pin", "")
        Advapi32Util.registrySetIntValue(hk, pole, "size", maxOf(i, indexy.maxOrNull() ?: 0))
    }

    /** Text tak, jak ho zapisuje Qt (začátek „@“ se zdvojí). */
    private fun qtText(s: String) = if (s.startsWith("@")) "@$s" else s

    private fun qtBajty(b: ByteArray): ByteArray =
        ("@ByteArray(" + String(b, Charsets.ISO_8859_1) + ")").toByteArray(Charsets.UTF_16LE)

    /** „@ByteArray(…)“ z registru zpět na bajty. */
    private fun bajty(ulozeno: String?): ByteArray? {
        val s = ulozeno ?: return null
        if (!s.startsWith("@ByteArray(") || !s.endsWith(")")) return null
        return s.substring(11, s.length - 1).toByteArray(Charsets.ISO_8859_1)
    }
}
