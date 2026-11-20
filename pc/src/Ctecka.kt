package cz.playlingo.pc

import cz.playlingo.core.Deepl
import cz.playlingo.core.DemoGame
import cz.playlingo.core.Lang
import cz.playlingo.core.Natural
import cz.playlingo.core.OcrBlock
import cz.playlingo.core.OcrLine
import cz.playlingo.core.Picked
import cz.playlingo.core.QuickWords
import cz.playlingo.core.Segment
import cz.playlingo.core.SpeakerColors
import cz.playlingo.core.SubtitleTracker
import cz.playlingo.core.TextLogic
import cz.playlingo.core.TvSender
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Čtení a překlad titulků – totéž co překlad v aplikaci pro Android TV, jen na počítači.
 *
 * Výběr titulků, rozhodování, kdy větu ukázat a schovat, i posílání do televize jsou tytéž soubory
 * ze složky `core` – nic se tu neopisuje. Liší se jen to, co Android dělá svými knihovnami: obraz,
 * čtení textu a překlad obstarává pomocník `cteni.py`.
 *
 * Spouští ji okno aplikace ([main] v `Aplikace.kt`) i příkazová řádka (`Main.kt`).
 */

private const val STALE_MS = 15_000L      // text, který stojí beze změny déle, je nápis nebo menu
private const val HOLD_MS = 1_000L        // překlad zůstane chvíli po zmizení angličtiny, ať se dá dočíst
private const val INTERVAL_MS = 120L

/** Kde leží nástroje z přípravy (`priprava.ps1`) a soubory čtečky. */
object Nastroje {
    val slozka = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "PlayLingoTools")
    val python = File(slozka, "venv/Scripts/python.exe")
    /** Složka `pc` v repozitáři – nastavuje ji spouštěcí skript. */
    val pc = File(System.getProperty("playlingo.pc", "."))
    val pomocnik = File(pc, "cteni.py")
    val priprava = File(pc, "priprava.ps1")
    val chyby = File(slozka, "cteni-chyby.txt")

    /** Záznamy pro ladění: `zaznamy\pc-<čas>\` v repozitáři (v .gitignore). */
    fun zaznamy(): File = File(pc.absoluteFile.parentFile ?: pc.absoluteFile, "zaznamy")

    fun model(jazyk: String): File? =
        File(slozka, "modely/en_$jazyk").listFiles()?.firstOrNull { it.isDirectory }

    /** Je všechno připravené pro tento jazyk? */
    fun pripraveno(jazyk: String) = python.exists() && model(jazyk) != null && pomocnik.exists()
}

/** Spojení s pomocníkem `cteni.py` (viz popis řádků tam). */
private class Helper(python: String, script: File, model: String, lang: String, window: String, errors: File?, record: File?) {
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
        if (record != null) { cmd += "--zaznam"; cmd += record.absolutePath }
        val pb = ProcessBuilder(cmd)
        if (errors != null) {
            errors.parentFile?.mkdirs()
            pb.redirectError(ProcessBuilder.Redirect.to(errors))
        } else pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        process = pb.start()
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

/** Co se s čtením právě děje – pro stránku Domů. */
data class Stav(
    val faze: Faze,
    /** Doba posledního čtení textu v ms (0 = zatím nic). */
    val ms: Long = 0,
    val televize: Int = 0,
    val original: String = "",
    val preklad: String = "",
    val chyba: String = "",
    /** Jak dlouho odpovídal DeepL naposledy (0 = zatím ne). */
    val deeplMs: Long = 0,
    /** Proč DeepL naposledy nepomohl (null = v pořádku). */
    val deeplChyba: Deepl.Problem? = null,
) {
    enum class Faze { STOJI, NACITANI, BEZI, BEZ_OKNA, CHYBA }
}

class Ctecka(
    private val jazyk: String,
    /** Část titulku okna s hrou; prázdné = ukázka bez PlayStationu. */
    private val okno: String,
    /** pc | tv | oboje */
    private val vystup: String,
    private val pruh: Pruh?,
    private val log: (String) -> Unit,
    private val zmena: (Stav) -> Unit = {},
    private val port: Int = TvSender.PORT,
    private val python: String = Nastroje.python.path,
    private val pomocnik: File = Nastroje.pomocnik,
    private val chyby: File? = null,
    /** Překládat přes DeepL (klíč v [DeeplPc]); offline zůstává jako záloha. */
    private val deepl: Boolean = false,
    /**
     * Záznam pro ladění: každé spuštění čtení dostane vlastní složku `pc-<čas>` s `zaznam.txt`
     * (co se přečetlo a kde, co se vzalo, překlady) a barevnými snímky celé hry (`cteni.py`).
     */
    private val zaznamy: File? = null,
) {
    private var zapisovac: java.io.Writer? = null
    private val casZaznamu = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Řádek do `zaznam.txt` (když záznam běží). */
    private fun zapis(m: String) {
        val w = zapisovac ?: return
        runCatching { w.write("${java.time.LocalTime.now().format(casZaznamu)} $m\n"); w.flush() }
    }

    private fun hlas(m: String) { log(m); zapis(m) }

    @Volatile private var konec = false
    private var stav = Stav(Stav.Faze.NACITANI)
        set(v) { if (v != field) { field = v; zmena(v) } }

    fun zastav() { konec = true }

    /** Běží, dokud se nezavolá [zastav]. Vrací se po úklidu (pruh schovaný, port zavřený). */
    fun bez() {
        val demo = okno.isEmpty()
        var sender: TvSender? = null
        var helper: Helper? = null
        try {
            zmena(stav)
            val model = Nastroje.model(jazyk) ?: error("chybí překladový model pro jazyk $jazyk")
            if (vystup != "pc") {
                sender = TvSender { log("televize: $it") }
                if (!sender.start(port)) error("port pro televizi je obsazený – neběží už jiná čtečka?")
            }
            val slozka = zaznamy?.let { File(it, "pc-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))) }
            if (slozka != null) {
                slozka.mkdirs()
                zapisovac = File(slozka, "zaznam.txt").bufferedWriter(Charsets.UTF_8)
                log("Záznam do ${slozka.path}")
                zapis("PlayLingo pro PC · jazyk $jazyk · okno „$okno“ · výstup $vystup · DeepL ${if (deepl) "ano" else "ne"}")
                zapis("CTENI = text ze spodní třetiny obrazu, souřadnice v celém obraze [vlevo,nahoře-vpravo,dole]; snímky celé hry jsou snimek-*.jpg")
            }
            log("Načítám čtení textu a překladač…")
            helper = Helper(python, pomocnik, model.path, jazyk, okno, chyby, slozka)
            if (konec) return
            hlas((if (demo) "Běží ukázka (bez PlayStationu)." else "Čtu okno „$okno“.") +
                (if (deepl) " Překlad DeepL online + offline." else " Překlad offline."))
            stav = Stav(Stav.Faze.BEZI)
            smycka(helper, sender, demo)
            stav = Stav(Stav.Faze.STOJI)
        } catch (e: Throwable) {
            hlas("Chyba: ${e.message}")
            stav = Stav(Stav.Faze.CHYBA, chyba = e.message ?: e.toString())
        } finally {
            pruh?.schovej()
            sender?.hide(); sender?.stop()
            helper?.stop()
            runCatching { zapisovac?.close() }
            zapisovac = null
            if (stav.faze != Stav.Faze.CHYBA) stav = Stav(Stav.Faze.STOJI)
        }
    }

    private fun smycka(helper: Helper, sender: TvSender?, demo: Boolean) {
        val tracker = SubtitleTracker(hideAfterEmpty = 3, fastComplete = true)
        val start = System.currentTimeMillis()
        var hideAt = 0L
        var shown = false
        var steadyText = ""
        var steadySince = 0L
        var lastError = ""
        var lastTvs = -1
        // každý mluvčí má svou barvu jména
        val speakerColors = SpeakerColors()
        // kde na snímku byl anglický titulek (poměry v obraze) – podle toho se položí pruh
        var spot = Misto(0.5f, -1f, -1f, 0f)
        var lastRead = ""

        // Jako na Androidu od 0.97.2: s DeepL se počká na jeho překlad (nejvýš ~2,5 s) a ukáže se jen
        // jedna varianta; offline jen když DeepL nepomůže. Krátká slova ze slovníčku zůstávají offline.
        fun translateSegments(text: String): List<Segment>? {
            val parts = TextLogic.segments(text)
            val bodies = parts.map { TextLogic.normalizeCase(it.body) }
            val quick = bodies.map { QuickWords.lookup(jazyk, it) }
            val ask = bodies.indices.filter { quick[it] == null }
            val online = if (deepl && ask.isNotEmpty()) {
                val t0 = System.currentTimeMillis()
                DeeplPc.preloz(Lang.of(jazyk), ask.map { TextLogic.forTranslator(bodies[it]) })?.also {
                    stav = stav.copy(deeplMs = System.currentTimeMillis() - t0, deeplChyba = null)
                } ?: run {
                    stav = stav.copy(deeplChyba = DeeplPc.backoff.lastProblem); null
                }
            } else null
            val result = ArrayList<Segment>()
            parts.forEachIndexed { i, s ->
                val body = bodies[i]
                val fromDeepl = online?.getOrNull(ask.indexOf(i))?.let { if (jazyk == "cs") Natural.czech(body, it) else it }
                val tr = quick[i] ?: fromDeepl ?: helper.translate(TextLogic.forTranslator(body)) ?: return null
                result.add(Segment(s.speaker?.let { TextLogic.normalizeName(it) }, TextLogic.finishTranslation(body, tr), s.choice))
            }
            return result
        }

        fun hide() { if (shown) { shown = false; sender?.hide(); pruh?.schovej() } }

        while (!konec) {
            val now = System.currentTimeMillis()
            if (sender != null && sender.connected != lastTvs) {
                lastTvs = sender.connected
                log("připojených televizí: $lastTvs")
                stav = stav.copy(televize = lastTvs)
            }
            val demoText = if (demo) DemoGame.subtitleAt(now - start) else null
            when (val r = helper.read(force = !tracker.isSettled, demoText = demoText)) {
                Helper.Read.Same -> {}
                is Helper.Read.Error -> {
                    if (r.text != lastError) { lastError = r.text; hlas("obraz není: ${r.text}") }
                    stav = stav.copy(faze = Stav.Faze.BEZ_OKNA, chyba = r.text)
                    hideAt = 0; hide(); tracker.reset()
                }
                is Helper.Read.Frame -> {
                    if (lastError.isNotEmpty()) { lastError = ""; hlas("obraz je zpět (${r.w}×${r.h})") }
                    stav = stav.copy(faze = Stav.Faze.BEZI, chyba = "", ms = r.ms)
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
                    if (zapisovac != null) {
                        val read = r.lines.joinToString(" ; ") { "[${r.x0 + it.left},${r.y0 + it.top}-${r.x0 + it.right},${r.y0 + it.bottom}] ${it.text}" }
                        val line = "CTENI ${r.ms} ms ${r.w}×${r.h} | " + read.ifEmpty { "–" } +
                            " | vzato: " + picked.text.replace('\n', ' ').ifEmpty { "–" }
                        if (read != lastRead) { lastRead = read; zapis(line) }
                    }
                    if (picked.top >= 0 && picked.left >= 0 && r.w > 0 && r.h > 0) {
                        spot = Misto(
                            (r.x0 + (picked.left + picked.right) / 2f) / r.w,
                            (r.y0 + picked.top).toFloat() / r.h,
                            (r.y0 + picked.bottom).toFloat() / r.h,
                            picked.lineHeight.toFloat() / r.h,
                        )
                    }
                    if (picked.text.isEmpty() || !TextLogic.looksForeign(picked.text)) {
                        if (picked.text.isEmpty() || !TextLogic.similar(picked.text, steadyText)) { steadyText = picked.text; steadySince = now }
                        // volby odpovědí smí stát déle – hráč se rozhoduje
                        val staleMs = if (TextLogic.hasChoices(picked.text)) TextLogic.CHOICES_STALE_MS else STALE_MS
                        if (picked.text.isNotEmpty() && now - steadySince > staleMs) {
                            if (shown) { hideAt = 0; hide(); hlas("schováno (text stojí dlouho – není to titulek)") }
                        } else when (val action = tracker.onOcr(picked.text, slowDevice = r.ms > 500)) {
                            is SubtitleTracker.Action.Show -> translateSegments(action.text)?.let { segs ->
                                tracker.confirmShown(action.text)
                                hideAt = 0
                                shown = true
                                val text = TextLogic.joinSegments(segs)
                                val colors = speakerColors.colorsFor(segs)
                                sender?.show(text, TvSender.partsJson(segs, colors))
                                pruh?.ukaz(segs, colors, spot)
                                val orig = action.text.replace('\n', ' ')
                                val prel = text.replace('\n', ' ')
                                stav = stav.copy(original = orig, preklad = prel)
                                hlas("${r.ms} ms  $orig  →  $prel")
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
}
