package cz.playlingo

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import cz.playlingo.core.DiagLog
import cz.playlingo.core.Redact
import cz.playlingo.core.ScreenReads
import cz.playlingo.core.SnapBatches
import cz.playlingo.core.SnapPacer
import java.io.File

/**
 * Záznam snímků pro ladění (⚙ → Experimentální → Ladění a hlášení → „Záznam snímků“, výchozí vypnuto; i tlačítkem
 * v notifikaci, když je zapnutý záznam v domácí síti). Start…Stop = jedna dávka (viz [SnapBatches]): vlastní vlákno
 * každých [SnapBatches.POLL_MS] porovná zmenšený obraz a při změně uloží celý snímek (barevně, když ho dekodér dá,
 * jinak šedě) a řádek do `index.txt`. Čtení titulků se nedotkne; vypnuté = nic neběží ani za snímek.
 * Jen při běžícím překladu (bez něj nejsou snímky); po [SnapBatches.MAX_MS] a se zastavením překladu se zastaví samo.
 */
object SnapRecorder {
    @Volatile var active = false
        private set
    /** Číslo a složka právě nahrávané (nebo naposledy nahrané) dávky a počet snímků v ní. */
    @Volatile var batch = 0
        private set
    @Volatile var count = 0
        private set
    @Volatile private var current: String? = null
    @Volatile private var thread: Thread? = null

    fun root(c: Context) = File(c.applicationContext.filesDir, SnapBatches.DIR)

    /** Název právě nahrávané dávky (pro seznam `/davky`), nebo null. */
    val recording: String? get() = if (active) current else null

    /** Start / Stop; false = nejde spustit (překlad neběží). */
    fun toggle(c: Context): Boolean = if (active) { stop("tlačítkem"); true } else start(c)

    @Synchronized fun start(c: Context): Boolean {
        if (active) return true
        if (!TranslatorService.running) return false
        val ctx = c.applicationContext
        val root = root(ctx)
        root.mkdirs()
        val now = System.currentTimeMillis()
        val n = SnapBatches.next((root.list() ?: emptyArray()).toList())
        val dir = File(root, SnapBatches.dirName(n, now))
        if (!dir.mkdirs()) return false
        SnapBatches.prune(root, SnapBatches.CAP_BYTES, dir.name)
        // tajné hodnoty jednou na začátku (čtení klíčů něco stojí), řádky indexu jdou přes Redact
        val secrets = try { DiagReport.secrets(ctx) } catch (_: Throwable) { emptyList() }
        val head = listOf(
            "PlayLingo ${BuildInfo.VERSION} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
            "zdroj: " + (if (TranslatorService.demo) "ukázka" else if (TranslatorService.xbox) "Xbox" else "PlayStation") +
                " · hra: ${Prefs.gameId(ctx)}",
        )
        try { File(dir, SnapBatches.INDEX).writeText(Redact.clean(SnapBatches.header(n, now, head), secrets), Charsets.UTF_8) } catch (_: Throwable) {}
        batch = n; count = 0; current = dir.name; active = true
        DiagLog.add("SNAP|start ${dir.name}")
        thread = Thread({ loop(ctx, root, dir, now, secrets) }, "tp-snimky").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        TranslatorService.refreshNotification()
        return true
    }

    @Synchronized fun stop(why: String) {
        if (!active) return
        active = false
        thread?.interrupt(); thread = null
        DiagLog.add("SNAP|stop $current · $count snímků · $why")
        TranslatorService.refreshNotification()
    }

    private fun loop(ctx: Context, root: File, dir: File, startWall: Long, secrets: Collection<String>) {
        val pacer = SnapPacer()
        val startUp = SystemClock.uptimeMillis()
        var mark = DiagLog.mark()
        var readsAt = startWall - 1
        val index = File(dir, SnapBatches.INDEX)
        try {
            while (active) {
                try { Thread.sleep(SnapBatches.POLL_MS) } catch (_: InterruptedException) { break }
                if (!active) break
                val up = SystemClock.uptimeMillis()
                if (up - startUp > SnapBatches.MAX_MS) { stop("10 min"); break }
                if (!TranslatorService.running) { stop("překlad zastaven"); break }
                val r = TranslatorService.frameReader ?: continue
                val th = r.thumb() ?: continue
                val why = pacer.offer(th, up) ?: continue
                val bmp: Bitmap = r.colorStill(SnapBatches.MAX_W, 600) ?: r.still(SnapBatches.MAX_W) ?: continue
                val i = count + 1
                val name = SnapBatches.snapName(i)
                val w = bmp.width; val h = bmp.height
                try {
                    val tmp = File(dir, "$name.tmp")
                    tmp.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, SnapBatches.JPEG_QUALITY, it) }
                    if (!tmp.renameTo(File(dir, name))) { tmp.delete(); continue }
                } catch (_: Throwable) { continue } finally { bmp.recycle() }
                count = i
                val wall = System.currentTimeMillis()
                val events = DiagLog.since(mark).filterNot { it.contains("ZAZNAM|") }
                mark = DiagLog.mark()
                val reads = ScreenReads.all().filter { it.time > readsAt }.map { it.raw + (if (it.count > 1) " (${it.count}×)" else "") to it.verdict }
                readsAt = wall
                try {
                    index.appendText(Redact.clean(SnapBatches.entry(i, wall, up - startUp, name, w, h, SnapBatches.whyText(why, i == 1), events, reads), secrets), Charsets.UTF_8)
                } catch (_: Throwable) {}
                // místo: po každých 20 snímcích smazat nejstarší dávky; když nestačí ani to, zastavit
                if (i % 20 == 0) {
                    SnapBatches.prune(root, SnapBatches.CAP_BYTES, dir.name)
                    if (SnapBatches.scan(root).sumOf { it.bytes } > SnapBatches.CAP_BYTES) { stop("plné místo"); break }
                }
            }
        } catch (_: Throwable) {
            stop("chyba")
        }
    }
}
