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
    /**
     * Použití: pc = hraje se na PC v chiaki-ng herním účtem (dosavadní, výchozí), ctecka = čtečka pro TV
     * LG / Samsung (hra běží na PS5 v TV, PC se připojí účtem pro PlayLingo a posílá překlad do TV).
     */
    var pouziti: String get() = text("pouziti", "pc").let { v -> if (POUZITI.any { it.first == v }) v else "pc" }; set(v) = nastav("pouziti", v)
    val ctecka: Boolean get() = pouziti == "ctecka"
    /** Konzole čtečky pro TV (spárovaná zvlášť v profilu chiaki-ng [Chiaki.PROFIL_CTECKY]); prázdné = první. */
    var konzoleCtecka: String get() = text("konzoleCtecka", ""); set(v) = nastav("konzoleCtecka", v)
    /** Účet pro PlayLingo (druhý uživatel na PS5) pro čtečku pro TV – jen číslo a jméno, jako [ucetId]. */
    var ucetCteckaId: String get() = text("ucetCteckaId", ""); set(v) = nastav("ucetCteckaId", v)
    var ucetCteckaJmeno: String get() = text("ucetCteckaJmeno", ""); set(v) = nastav("ucetCteckaJmeno", v)
    /** Kterým účtem byla konzole spárovaná ([rezim] pc / ctecka) – pozná se tak spárování herním účtem. */
    fun ucetParovani(rezim: String, konzole: String): String = text("parovaniUcet.$rezim.$konzole", "")
    fun ucetParovani(rezim: String, konzole: String, ucet: String) = nastav("parovaniUcet.$rezim.$konzole", ucet)
    /** Poslední ohlášená TV („LG OLED55B7“) – na Domů i před spuštěním. */
    var tvNazev: String get() = text("tvNazev", ""); set(v) = nastav("tvNazev", v)
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

    /** Velikost písma v pruhu jako na Androidu (12–48, jednotky jako sp na TV); 0 = podle titulků ve hře. */
    var pismo: Int get() = cislo("pismo", 0).let { if (it == 0 || it in PISMA) it else 0 }; set(v) = nastav("pismo", v)
    /** Neprůhlednost pozadí pruhu v % (0 = jen písmo se stínem). */
    var pozadi: Int get() = cislo("pozadi", 90).let { v -> POZADI.minByOrNull { Math.abs(it - v) } ?: 90 }; set(v) = nastav("pozadi", v)
    var barva: Int get() = cislo("barva", 0).coerceIn(0, BARVY.size - 1); set(v) = nastav("barva", v)
    var tucne: Boolean get() = text("tucne", "true") == "true"; set(v) = nastav("tucne", v)
    var poloha: Poloha
        get() = runCatching { Poloha.valueOf(text("poloha", "PRES")) }.getOrDefault(Poloha.PRES)
        set(v) = nastav("poloha", v.name)
    /** Okraj pruhu nad a pod textem 0–3 (jako na Androidu). */
    var okraj: Int get() = cislo("okraj", 1).coerceIn(0, 3); set(v) = nastav("okraj", v)
    /** Anglický originál pod překladem. */
    var original: Boolean get() = text("original", "false") == "true"; set(v) = nastav("original", v)

    /** Profil hry (`core/GameProfiles.kt`); „all“ = univerzální. */
    var hra: String get() = text("hra", "all"); set(v) = nastav("hra", v)
    /** Rozsah čtení: true = titulky a texty hry (pokyny GTA, menu), false = jen titulky. */
    var textyHry: Boolean get() = text("textyHry", "true") == "true"; set(v) = nastav("textyHry", v)

    fun vzhled() = Vzhled(pismo, pozadi, Color(BARVY[barva].second), tucne, poloha, okraj, original, textyHry)

    companion object {
        /** Stejné hodnoty jako na Androidu a v TV (`TvSettings.TEXT_SIZES`, `TvSettings.OPACITIES`). */
        val PISMA = cz.playlingo.core.TvSettings.TEXT_SIZES
        val POZADI = cz.playlingo.core.TvSettings.OPACITIES
        /** Stejné barvy jako na Android TV (Prefs.TEXT_COLORS). */
        val BARVY = listOf("Bílá" to 0xFFFFFF, "Žlutá" to 0xFFE066, "Zelená" to 0xA8F0A8, "Modrá" to 0x9FD8FF)
        val ZDROJE = listOf("chiaki" to "chiaki-ng v PlayLingu", "okno" to "Jiný program", "ukazka" to "Ukázková hra")
        val VYSTUPY = listOf("pc" to "Pruh na PC", "tv" to "TV LG / Samsung", "oboje" to "PC i TV")
        val POUZITI = listOf("pc" to "Hraji na PC", "ctecka" to "Čtečka pro TV (hra na PS5 v TV)")
    }
}
