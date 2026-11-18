package cz.playlingo.pc

import cz.playlingo.core.DemoGame
import cz.playlingo.core.OcrBlock
import cz.playlingo.core.OcrLine
import cz.playlingo.core.Picked
import cz.playlingo.core.QuickWords
import cz.playlingo.core.Segment
import cz.playlingo.core.SubtitleTracker
import cz.playlingo.core.TextLogic
import cz.playlingo.core.TvSender
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Čtečka pro počítač: čte anglické titulky z obrazu hry, přeloží je a pošle do televize LG.
 *
 * Je to totéž co překlad v aplikaci pro Android TV, jen na počítači. Výběr titulků, rozhodování,
 * kdy větu ukázat a schovat, i posílání do televize jsou tytéž soubory ze složky `core` – nic se
 * tu neopisuje. Liší se jen to, co Android dělá svými knihovnami: obraz, čtení textu a překlad
 * obstarává pomocník `cteni.py`.
 *
 * Bez `--okno` běží ukázka: věty ukázkové hry se kreslí na umělý obrázek (zkouška bez PlayStationu).
 */

private const val STALE_MS = 15_000L      // text, který stojí beze změny déle, je nápis nebo menu
private const val HOLD_MS = 1_000L        // překlad zůstane chvíli po zmizení angličtiny, ať se dá dočíst
private const val INTERVAL_MS = 120L

private fun cas() = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
private fun log(m: String) = println("${cas()} $m")

/** Spojení s pomocníkem `cteni.py` (viz popis řádků tam). */
private class Helper(python: String, script: File, model: String, lang: String, window: String) {
    sealed class Read {
        object Same : Read()
        data class Error(val text: String) : Read()
        data class Frame(val lines: List<OcrLine>, val w: Int, val h: Int, val x0: Int, val y0: Int, val ms: Long) : Read()
    }

    private val process: Process
    private val out: BufferedWriter
    private val input: BufferedReader

    init {
        val cmd = mutableListOf(python, script.absolutePath, "--model", model, "--jazyk", lang)
        if (window.isNotEmpty()) { cmd += "--okno"; cmd += window }
        process = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        out = process.outputStream.bufferedWriter(Charsets.UTF_8)
        input = process.inputStream.bufferedReader(Charsets.UTF_8)
        // první řádek přijde, až jsou načtené modely
        while (true) {
            val l = input.readLine() ?: error("pomocník cteni.py se nespustil (chybí knihovny Pythonu nebo model?)")
            if (l == "PRIPRAVENO") break
        }
    }

    private fun ask(vararg parts: String): String {
        out.write(parts.joinToString("\t")); out.write("\n"); out.flush()
        return input.readLine() ?: error("pomocník cteni.py skončil")
    }

    fun read(force: Boolean, demoText: String?): Read {
        val head = (if (demoText == null) ask("CTI", if (force) "1" else "0")
            else ask("CTI", if (force) "1" else "0", demoText.replace("\n", "\\n"))).split('\t')
        return when (head[0]) {
            "STEJNE" -> Read.Same
            "OK" -> {
                val n = head[1].toInt()
                val lines = (0 until n).mapNotNull {
                    val p = (input.readLine() ?: error("pomocník cteni.py skončil")).split('\t', limit = 5)
                    if (p.size < 5) null else OcrLine(p[4], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
                }
                Read.Frame(lines, head[2].toInt(), head[3].toInt(), head[4].toInt(), head[5].toInt(), head[6].toLong())
            }
            else -> Read.Error(head.getOrElse(1) { "?" })
        }
    }

    fun translate(text: String): String? {
        val r = ask("PRELOZ", text.replace('\t', ' ').replace('\n', ' ')).split('\t', limit = 2)
        return if (r[0] == "OK") r.getOrElse(1) { "" }.takeIf { it.isNotBlank() } else null
    }

    fun stop() { try { process.destroy() } catch (_: Throwable) {} }
}

/**
 * Řádky, které k sobě patří (dvouřádkový titulek), spojí do bloků: leží těsně pod sebou
 * a vodorovně se překrývají. Android dostává bloky hotové od své čtečky textu.
 */
internal fun toBlocks(lines: List<OcrLine>): List<OcrBlock> {
    val blocks = ArrayList<MutableList<OcrLine>>()
    for (l in lines.sortedBy { it.top }) {
        val home = blocks.firstOrNull { b ->
            val last = b.last()
            val gap = l.top - last.bottom
            val overlap = min(l.right, last.right) - max(l.left, last.left)
            gap < 0.8f * max(l.height, last.height) && gap > -0.5f * l.height && overlap > 0
        }
        if (home != null) home.add(l) else blocks.add(mutableListOf(l))
    }
    return blocks.map { OcrBlock(it) }
}

fun main(args: Array<String>) {
    fun arg(name: String, default: String = ""): String {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else default
    }
    val window = arg("--okno")
    val demo = window.isEmpty()
    val lang = arg("--jazyk", "cs")
    val model = arg("--model")
    if (model.isEmpty()) {
        println("Použití: --model SLOŽKA_MODELU [--python CESTA] [--okno NÁZEV_OKNA] [--jazyk cs] [--port 8787]")
        return
    }

    val sender = TvSender { log("televize: $it") }
    if (!sender.start(arg("--port", TvSender.PORT.toString()).toInt())) {
        log("Port pro televizi je obsazený – neběží už jiná čtečka nebo zkušební posílač?")
        return
    }
    log("Načítám čtení textu a překladač…")
    val helper = Helper(arg("--python", "python"), File(arg("--pomocnik", "cteni.py")), model, lang, window)
    Runtime.getRuntime().addShutdownHook(Thread { sender.hide(); sender.stop(); helper.stop() })
    log(if (demo) "Běží ukázka (bez PlayStationu). Na televizi LG spusť PlayLingo a vyber vstup."
        else "Čtu okno „$window“. Na televizi LG spusť PlayLingo a vyber vstup.")

    val tracker = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)
    val start = System.currentTimeMillis()
    var hideAt = 0L
    var shown = false
    var steadyText = ""
    var steadySince = 0L
    var lastError = ""
    var lastTvs = -1

    fun translateSegments(text: String): List<Segment>? {
        val result = ArrayList<Segment>()
        for (s in TextLogic.segments(text)) {
            val body = TextLogic.normalizeCase(s.body)
            val tr = QuickWords.lookup(lang, body) ?: helper.translate(TextLogic.forTranslator(body)) ?: return null
            result.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, TextLogic.finishTranslation(body, tr)))
        }
        return result
    }

    fun hide() { if (shown) { shown = false; sender.hide() } }

    while (true) {
        val now = System.currentTimeMillis()
        if (sender.connected != lastTvs) { lastTvs = sender.connected; log("připojených televizí: $lastTvs") }
        val demoText = if (demo) DemoGame.subtitleAt(now - start) else null
        when (val r = helper.read(force = !tracker.isSettled, demoText = demoText)) {
            Helper.Read.Same -> {}
            is Helper.Read.Error -> {
                if (r.text != lastError) { lastError = r.text; log("obraz není: ${r.text}") }
                hideAt = 0; hide(); tracker.reset()
            }
            is Helper.Read.Frame -> {
                if (lastError.isNotEmpty()) { lastError = ""; log("obraz je zpět (${r.w}×${r.h})") }
                // Stejné hranice jako v aplikaci pro Android TV (TranslatorService.process).
                val foreign = TextLogic.looksForeign(r.lines.joinToString("\n") { it.text })
                val picked = if (foreign) Picked("", 0) else TextLogic.assemble(
                    toBlocks(r.lines),
                    minLineHeight = (r.h * 0.013f).toInt(),
                    subtitlesOnly = true,
                    screenCenterX = r.w / 2 - r.x0,
                    centerTolerance = (r.w * 0.14f).toInt(),
                    maxLineHeight = (r.h * 0.085f).toInt(),
                )
                if (picked.text.isEmpty() || !TextLogic.looksForeign(picked.text)) {
                    if (picked.text.isEmpty() || !TextLogic.similar(picked.text, steadyText)) { steadyText = picked.text; steadySince = now }
                    if (picked.text.isNotEmpty() && now - steadySince > STALE_MS) {
                        if (shown) { hideAt = 0; hide(); log("schováno (text stojí dlouho – není to titulek)") }
                    } else when (val action = tracker.onOcr(picked.text, slowDevice = r.ms > 500)) {
                        is SubtitleTracker.Action.Show -> translateSegments(action.text)?.let { segs ->
                            tracker.confirmShown(action.text)
                            hideAt = 0
                            shown = true
                            val text = segs.joinToString("\n") { s -> if (s.speaker != null) "${s.speaker}: ${s.body}" else s.body }
                            sender.show(text)
                            log("${r.ms} ms  ${action.text.replace('\n', ' ')}  →  ${text.replace('\n', ' ')}")
                        }
                        SubtitleTracker.Action.Hide -> hideAt = now + HOLD_MS
                        SubtitleTracker.Action.None ->
                            // na obrazovce je už jiný titulek → starý překlad hned schovej
                            if (tracker.isChanging && picked.text.isNotEmpty() && shown) { hideAt = 0; hide() }
                    }
                }
            }
        }
        if (hideAt in 1..System.currentTimeMillis()) { hideAt = 0; hide() }
        Thread.sleep(max(20L, INTERVAL_MS - (System.currentTimeMillis() - now)))
    }
}
