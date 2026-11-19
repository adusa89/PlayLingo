package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket

/** Televize Samsung a typ televize: balíček .wgt, ohlášení aplikace z televize, rozhodnutí průvodce. */
class SamsungTest {
    private val config = """<?xml version="1.0" encoding="UTF-8"?>
<widget xmlns="http://www.w3.org/ns/widgets" xmlns:tizen="http://tizen.org/ns/widgets" id="https://x/tizen" version="1.2.3" viewmodes="maximized">
    <tizen:application id="PlayLingo1.PlayLingo" package="PlayLingo1" required_version="2.3"/>
</widget>"""

    private fun files() = mapOf(
        "config.xml" to config.toByteArray(),
        "index.html" to "<html></html>".toByteArray(),
        "app.js" to "var a = 'Řekni ahoj';".toByteArray(),
        "platforma.js" to "var VERZE = '1.2.3';".toByteArray(),
        "obr/icon.png" to ByteArray(300) { it.toByte() },
    )

    // ---------------- typ televize ----------------

    @Test fun tvTypeKeepsOldLgUsers() {
        assertEquals(TvType.ANDROID, TvType.from(null, false))
        assertEquals(TvType.LG, TvType.from(null, true))        // dřív „Posílat titulky do televize LG“
        assertEquals(TvType.SAMSUNG, TvType.from("samsung", true))
        assertEquals(TvType.ANDROID, TvType.from("android", true))
        assertEquals(TvType.LG, TvType.from("nesmysl", true))
        assertFalse(TvType.ANDROID.sends)
        assertTrue(TvType.LG.sends && TvType.SAMSUNG.sends)
        assertEquals("tizen", TvType.SAMSUNG.platform)
        assertEquals("webos", TvType.LG.platform)
    }

    // ---------------- ohlášení z televize ----------------

    @Test fun announceParsing() {
        val a = TvAnnounce.parse("{\"typ\":\"pripojeno\",\"televize\":\"UE55TU7172\",\"system\":\"Tizen 5.5 (T-x)\",\"hdmi\":2,\"aplikace\":\"PlayLingo\",\"platforma\":\"tizen\",\"verze\":\"1.1.0\"}")!!
        assertEquals("tizen", a.platform); assertEquals("1.1.0", a.version); assertEquals("UE55TU7172", a.model); assertEquals(2, a.hdmi)
        // starší aplikace v LG: bez platformy a verze → LG bez verze
        val old = TvAnnounce.parse("{\"typ\":\"pripojeno\",\"televize\":\"OLED55B7\",\"system\":\"webOS 3\",\"hdmi\":1}")!!
        assertEquals("webos", old.platform); assertNull(old.version)
        // model neznámý („?“) a nesmyslná verze se zahodí
        val odd = TvAnnounce.parse("{\"typ\":\"pripojeno\",\"televize\":\"?\",\"platforma\":\"tizen\",\"verze\":\"1.0 beta\",\"hdmi\":77}")!!
        assertNull(odd.model); assertNull(odd.version); assertNull(odd.hdmi); assertEquals("tizen", odd.platform)
        assertNull(TvAnnounce.parse("{\"typ\":\"hotovo\",\"id\":3}"))
        assertNull(TvAnnounce.parse("nesmysl"))
        assertNull(TvAnnounce.parse("{\"typ\":\"pripojeno\",\"x\":\"" + "a".repeat(5000) + "\"}"))
        // řídicí znaky z cizího zařízení se do textu nedostanou
        assertEquals("A B", TvAnnounce.parse("{\"typ\":\"pripojeno\",\"televize\":\"A\\u0007B\"}")!!.model)
    }

    @Test fun checkDecision() {
        fun a(p: String?, v: String?) = TvAnnounce(p, v, null, null, null)
        assertEquals(TvCheck.WAITING, TvCheck.of(null, "tizen", "1.1.0"))
        assertEquals(TvCheck.OTHER, TvCheck.of(a("webos", "1.1.0"), "tizen", "1.1.0"))
        assertEquals(TvCheck.NO_VERSION, TvCheck.of(a("tizen", null), "tizen", "1.1.0"))
        assertEquals(TvCheck.OLDER, TvCheck.of(a("tizen", "1.0.9"), "tizen", "1.1.0"))
        assertEquals(TvCheck.SAME, TvCheck.of(a("tizen", "1.1.0"), "tizen", "1.1.0"))
        assertEquals(TvCheck.NEWER, TvCheck.of(a("tizen", "1.10.0"), "tizen", "1.9.0"))
        assertTrue(TvCheck.SAME.ok && TvCheck.NEWER.ok)
        assertFalse(TvCheck.OLDER.ok || TvCheck.NO_VERSION.ok || TvCheck.WAITING.ok || TvCheck.OTHER.ok)
    }

    // ---------------- balíček .wgt ----------------

    @Test fun wgtBuildIsStableAndReadable() {
        val w1 = SamsungWgt.build(files())
        val w2 = SamsungWgt.build(files().toList().reversed().toMap())
        assertTrue("stejné zdroje = stejný soubor", w1.contentEquals(w2))
        val back = SamsungWgt.read(w1)
        assertEquals(files().keys, back.keys)
        for ((k, v) in files()) assertTrue(k, back.getValue(k).contentEquals(v))
        assertEquals("PlayLingo1.PlayLingo" to "1.2.3", SamsungWgt.verify(w1, files()))
        // čte ho i běžný ZIP (java.util.zip) a datum je pevné
        java.util.zip.ZipInputStream(ByteArrayInputStream(w1)).use { z ->
            val e = z.nextEntry!!
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = e.time }
            assertEquals(1980, cal.get(java.util.Calendar.YEAR))
        }
        // podpis do našeho balíčku nepatří
        assertTrue(runCatching { SamsungWgt.build(files() + ("author-signature.xml" to ByteArray(1))) }.isFailure)
        assertTrue(runCatching { SamsungWgt.build(files() + ("../x" to ByteArray(1))) }.isFailure)
        assertTrue(runCatching { SamsungWgt.verify(w1, files() + ("navic.txt" to ByteArray(1))) }.isFailure)
    }

    @Test fun configAndVersions() {
        assertEquals("PlayLingo1.PlayLingo" to "1.2.3", SamsungWgt.config(config))
        assertTrue(runCatching { SamsungWgt.config(config.replace("package=\"PlayLingo1\"", "package=\"Kratky\"")) }.isFailure)
        assertTrue(runCatching { SamsungWgt.config(config.replace("version=\"1.2.3\"", "version=\"1.2\"")) }.isFailure)
        assertEquals("1.1.0", SamsungWgt.platformVersion("  var VERZE = '1.1.0';"))
        assertEquals("1.0.4", SamsungWgt.platformVersion("    platforma: 'webos',\n    verze: '1.0.4',"))
        assertEquals("PlayLingo-Samsung-1.2.3-nepodepsany.wgt", SamsungWgt.fileName("1.2.3"))
    }

    @Test fun sharedFilesCompareIgnoringLineEnds() {
        val a = mapOf("app.js" to "a\r\nb".toByteArray(), "style.css" to "c".toByteArray(), "index.html" to "i".toByteArray())
        val b = mapOf("app.js" to "a\nb".toByteArray(), "style.css" to "c".toByteArray(), "index.html" to "i".toByteArray())
        assertEquals(emptyList<String>(), SamsungWgt.sharedDiff(a, b))
        assertEquals(listOf("style.css"), SamsungWgt.sharedDiff(a, b + ("style.css" to "d".toByteArray())))
        assertEquals(listOf("index.html"), SamsungWgt.sharedDiff(a, b - "index.html"))
    }

    @Test fun bundledPackageIsChecked() {
        val wgt = SamsungWgt.build(files())
        val manifest = SamsungWgt.manifest("PlayLingo1.PlayLingo", "1.2.3", wgt)
        assertTrue(manifest.contains("\"signed\": false"))
        val b = SamsungInstall.bundle(manifest, wgt)!!
        assertEquals("1.2.3", b.version); assertEquals(SamsungWgt.fileName("1.2.3"), b.fileName)
        // jiný obsah, jiná velikost, jiné id, podepsaný → odmítnout
        val bad = wgt.copyOf().also { it[it.size - 30] = (it[it.size - 30] + 1).toByte() }
        assertNull(SamsungInstall.bundle(manifest, bad))
        assertNull(SamsungInstall.bundle(manifest, wgt + ByteArray(1)))
        assertNull(SamsungInstall.bundle(manifest.replace("PlayLingo1.PlayLingo", "Jiny000000.X"), wgt))
        assertNull(SamsungInstall.bundle(manifest.replace("\"signed\": false", "\"signed\": true"), wgt))
        assertNull(SamsungInstall.bundle("{}", wgt))
    }

    /** Skutečné soubory v repozitáři: společné soubory sedí a verze v platforma.js odpovídají. */
    @Test fun repositoryTvAppsInSync() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "tizen/app/config.xml").isFile) dir = dir.parentFile
        val root = dir ?: return   // mimo repozitář (nemělo by nastat) – nic ke kontrole
        fun read(d: File) = d.walkTopDown().filter { it.isFile }.associate { it.relativeTo(d).invariantSeparatorsPath to it.readBytes() }
        val tizen = read(File(root, "tizen/app"))
        val lg = read(File(root, "webos/obchod/app"))
        assertEquals("zkopíruj soubory z webos/obchod/app do tizen/app", emptyList<String>(), SamsungWgt.sharedDiff(lg, tizen))
        val (id, version) = SamsungWgt.config(String(tizen.getValue("config.xml")))
        assertEquals(SamsungWgt.APP_ID, id)
        assertEquals(version, SamsungWgt.platformVersion(String(tizen.getValue("platforma.js"))))
        assertEquals(LgIpk.appInfo(String(lg.getValue("appinfo.json"))).second, SamsungWgt.platformVersion(String(lg.getValue("platforma.js"))))
        val wgt = SamsungWgt.build(tizen)
        assertEquals(id to version, SamsungWgt.verify(wgt, tizen))
    }

    // ---------------- zprávy od televize (TvSender) ----------------

    /** Zpráva od prohlížeče: vždy maskovaná. */
    private fun clientFrame(text: String, opcode: Int = 1): ByteArray {
        val data = text.toByteArray(Charsets.UTF_8)
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val out = ByteArrayOutputStream()
        out.write(0x80 or opcode)
        if (data.size < 126) out.write(0x80 or data.size) else { out.write(0x80 or 126); out.write(data.size shr 8); out.write(data.size and 0xff) }
        out.write(mask)
        for (i in data.indices) out.write(data[i].toInt() xor mask[i % 4].toInt())
        return out.toByteArray()
    }

    @Test fun readsMaskedFrames() {
        val long = "x".repeat(300)
        val input = ByteArrayInputStream(clientFrame("ahoj") + clientFrame(long) + clientFrame("", 9) + clientFrame("", 8))
        assertEquals("ahoj", TvSender.readFrame(input))
        assertEquals(long, TvSender.readFrame(input))
        assertEquals("", TvSender.readFrame(input))     // ping a jiné se přeskočí
        assertNull(TvSender.readFrame(input))           // zavření spojení
        assertNull(TvSender.readFrame(ByteArrayInputStream(byteArrayOf(0x81.toByte(), 0xFF.toByte()))))   // 64bitová délka
    }

    @Test fun tvAnnouncementIsRemembered() {
        val local = InetAddress.getByName("127.0.0.1")
        val sender = TvSender()
        assertTrue(sender.start(0, local))
        try {
            TvSender.lastSeen = null
            Socket(local, sender.port).use { s ->
                s.soTimeout = 5000
                s.getOutputStream().apply {
                    write(("GET / HTTP/1.1\r\nHost: tv\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                    write(clientFrame("{\"typ\":\"pripojeno\",\"televize\":\"QE65\",\"platforma\":\"tizen\",\"verze\":\"1.1.0\",\"hdmi\":3}"))
                    flush()
                }
                val end = System.currentTimeMillis() + 5000
                while (TvSender.lastSeen == null && System.currentTimeMillis() < end) Thread.sleep(10)
                val seen = TvSender.lastSeen
                assertNotNull(seen)
                assertEquals("1.1.0", seen!!.announce.version)
                assertEquals("QE65", seen.announce.model)
            }
        } finally { sender.stop(); TvSender.lastSeen = null }
    }

    // ---------------- texty ----------------

    @Test fun placeholdersInAllLanguages() {
        for (lang in Lang.values()) {
            for (k in listOf("ss_ok", "ss_ok_newer", "ss_older", "ss_ip")) assertTrue("$lang/$k", I18n.t(lang, k).contains("%s"))
            assertTrue("$lang/ss_s2", I18n.t(lang, "ss_s2").contains("%s") && I18n.t(lang, "ss_s2").contains("%v"))
            for (t in TvType.values()) assertTrue("$lang/${t.key}", I18n.hasOwn(lang, "tv_type_" + t.key) || lang == Lang.CS || lang == Lang.SK)
        }
    }
}
