package cz.playlingo.pc

import java.awt.Color
import java.io.File
import java.util.Properties

/** Nastavení aplikace pro počítač – soubor `aplikace.properties` ve složce nástrojů. */
class Nastaveni(private val soubor: File = File(Nastroje.slozka, "aplikace.properties")) {
    private val p = Properties().apply {
        runCatching { soubor.inputStream().use { load(it.reader(Charsets.UTF_8)) } }
    }

    private fun uloz() {
        runCatching {
            soubor.parentFile?.mkdirs()
            soubor.outputStream().use { p.store(it.writer(Charsets.UTF_8), "PlayLingo pro počítač") }
        }
    }

    private fun text(k: String, d: String) = p.getProperty(k) ?: d
    private fun cislo(k: String, d: Int) = p.getProperty(k)?.toIntOrNull() ?: d
    private fun nastav(k: String, v: Any) { p.setProperty(k, v.toString()); uloz() }

    /** Část titulku okna s hrou. */
    var okno: String get() = text("okno", "chiaki"); set(v) = nastav("okno", v)
    /** Ukázková hra místo okna (zkouška bez PlayStationu). */
    var ukazka: Boolean get() = text("ukazka", "false") == "true"; set(v) = nastav("ukazka", v)
    var jazyk: String get() = text("jazyk", "cs"); set(v) = nastav("jazyk", v)
    /** pc | tv | oboje */
    var vystup: String get() = text("vystup", "pc"); set(v) = nastav("vystup", v)

    var velikost: Int get() = cislo("velikost", 100); set(v) = nastav("velikost", v)
    var pozadi: Int get() = cislo("pozadi", 90); set(v) = nastav("pozadi", v)
    var barva: Int get() = cislo("barva", 0).coerceIn(0, BARVY.size - 1); set(v) = nastav("barva", v)
    var tucne: Boolean get() = text("tucne", "true") == "true"; set(v) = nastav("tucne", v)
    var poloha: Poloha
        get() = runCatching { Poloha.valueOf(text("poloha", "PRES")) }.getOrDefault(Poloha.PRES)
        set(v) = nastav("poloha", v.name)

    fun vzhled() = Vzhled(velikost, pozadi, Color(BARVY[barva].second), tucne, poloha)

    companion object {
        val VELIKOSTI = listOf(70, 85, 100, 125, 150)
        /** Stejné hodnoty jako na Android TV (Prefs.OPACITIES). */
        val POZADI = listOf(60, 80, 90, 100)
        /** Stejné barvy jako na Android TV (Prefs.TEXT_COLORS). */
        val BARVY = listOf("Bílá" to 0xFFFFFF, "Žlutá" to 0xFFE066, "Zelená" to 0xA8F0A8, "Modrá" to 0x9FD8FF)
        val VYSTUPY = listOf("pc" to "Pruh na PC", "tv" to "TV LG / Samsung", "oboje" to "PC i TV")
    }
}
