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
    /**
     * Odkud je obraz hry: chiaki (PlayLingo samo spustí hru v chiaki-ng), okno (hra běží v jiném
     * programu, čte se okno podle [okno]), ukazka (ukázková hra bez PlayStationu).
     */
    var zdroj: String
        get() = p.getProperty("zdroj") ?: if (text("ukazka", "false") == "true") "ukazka" else "chiaki"
        set(v) = nastav("zdroj", v)
    val ukazka: Boolean get() = zdroj == "ukazka"
    /** Jméno spárované konzole v chiaki-ng (prázdné = první). */
    var konzole: String get() = text("konzole", ""); set(v) = nastav("konzole", v)
    var jazyk: String get() = text("jazyk", "cs"); set(v) = nastav("jazyk", v)
    /** pc | tv | oboje */
    var vystup: String get() = text("vystup", "pc"); set(v) = nastav("vystup", v)
    /** Překlad přes DeepL (klíč je zvlášť a zašifrovaný, viz DeeplPc). */
    var deepl: Boolean get() = text("deepl", "false") == "true"; set(v) = nastav("deepl", v)
    /** Záznam hry pro ladění (snímky + co se četlo) do `zaznamy\pc-<čas>\`. */
    var zaznamHry: Boolean get() = text("zaznamHry", "false") == "true"; set(v) = nastav("zaznamHry", v)
    /** Účet PlayStation pro spárování (zakódované číslo účtu a jméno; heslo se nikdy neukládá). */
    var ucetId: String get() = text("ucetId", ""); set(v) = nastav("ucetId", v)
    var ucetJmeno: String get() = text("ucetJmeno", ""); set(v) = nastav("ucetJmeno", v)

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
        val ZDROJE = listOf("chiaki" to "chiaki-ng v PlayLingu", "okno" to "Jiný program", "ukazka" to "Ukázková hra")
        val VYSTUPY = listOf("pc" to "Pruh na PC", "tv" to "TV LG / Samsung", "oboje" to "PC i TV")
    }
}
