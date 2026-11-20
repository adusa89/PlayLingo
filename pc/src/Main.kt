package cz.playlingo.pc

import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Čtečka z příkazové řádky (`spustit.ps1`) – pro ladění, výpis jde do okna konzole.
 * Běžně se spouští okno aplikace (`Aplikace.kt`, `PlayLingo.cmd`).
 *
 * Bez `--okno` běží ukázka: věty ukázkové hry se kreslí na umělý obrázek (zkouška bez PlayStationu).
 */
fun main(args: Array<String>) {
    fun arg(name: String, default: String = ""): String {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else default
    }
    val window = arg("--okno")
    val lang = arg("--jazyk", "cs")
    // kam s překladem: pruh na obrazovce počítače (pc), do televize LG/Samsung (tv), nebo obojí
    val output = arg("--vystup", "pc")
    if (output !in listOf("pc", "tv", "oboje")) {
        println("Použití: [--python CESTA] [--pomocnik cteni.py] [--okno NÁZEV_OKNA] [--jazyk cs] [--vystup pc|tv|oboje] [--port 8787]")
        return
    }
    fun log(m: String) = println("${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} $m")

    val bar = if (output == "tv") null else {
        val game = if (window.isEmpty()) null else OknoHry(window)
        Pruh { game?.oblast() ?: if (window.isEmpty()) celaObrazovka() else null }
    }
    val reader = Ctecka(
        jazyk = lang, okno = window, vystup = output, pruh = bar, log = ::log,
        port = arg("--port", "8787").toInt(),
        python = arg("--python", Nastroje.python.path),
        pomocnik = File(arg("--pomocnik", Nastroje.pomocnik.path)),
    )
    Runtime.getRuntime().addShutdownHook(Thread { reader.zastav() })
    reader.bez()
    System.exit(0)
}
