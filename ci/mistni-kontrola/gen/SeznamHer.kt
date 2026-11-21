package cz.playlingo.gen

import cz.playlingo.core.GameListJson
import cz.playlingo.core.GameProfiles
import cz.playlingo.core.MiniJson
import java.io.File

/**
 * Přegeneruje seznam her pro stránky s pomocí z vestavěných profilů (`core/GameProfiles*.kt`):
 * `pomoc/hry/profily.json` a `pomoc/hry/verze.txt`. Spouští úloha `seznamHer`:
 *
 *   gradlew -p ci/mistni-kontrola seznamHer
 *
 * Když se obsah změnil, ale `GameProfiles.VERSION` ne, skončí chybou – aplikace by novější seznam nestáhly.
 */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "../../pomoc/hry" })
    dir.mkdirs()
    val json = File(dir, "profily.json")
    val text = GameListJson.write(GameProfiles.BUILTIN)
    val old = if (json.exists()) json.readText(Charsets.UTF_8).replace("\r\n", "\n") else null   // git na Windows může dát CRLF
    if (old == text) { println("Seznam her je aktuální (verze ${GameProfiles.VERSION})."); return }
    val oldVersion = old?.let { (MiniJson.obj(it)?.get("verze") as? Long)?.toInt() }
    if (oldVersion != null && oldVersion >= GameProfiles.VERSION) {
        System.err.println("Profily her se změnily, ale GameProfiles.VERSION je pořád $oldVersion – zvyš ji (core/GameProfiles.kt) a spusť znovu.")
        kotlin.system.exitProcess(1)
    }
    json.writeText(text, Charsets.UTF_8)
    File(dir, "verze.txt").writeText(GameProfiles.VERSION.toString() + "\n", Charsets.UTF_8)
    println("Zapsáno: ${json.path} (${text.length / 1024} kB, verze ${GameProfiles.VERSION}).")
}
