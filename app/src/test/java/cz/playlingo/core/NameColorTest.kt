package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Barva jména z obrazu: světlé body písma, bílé jméno = bez barvy, naučí se až po 2 shodách. */
class NameColorTest {
    private fun yuv(rgb: Int): Triple<Int, Int, Int> {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
        return Triple(y, u, v)
    }

    /** Písmo dané barvy (20 bodů) na tmavém pozadí (40 bodů). */
    private fun sample(text: Int): Int? {
        val n = 60
        val ys = IntArray(n); val us = IntArray(n); val vs = IntArray(n)
        for (i in 0 until n) {
            val (y, u, v) = if (i % 3 == 0) yuv(text) else yuv(0x202020)
            ys[i] = y; us[i] = u; vs[i] = v
        }
        return NameColor.fromSamples(ys, us, vs, n)
    }

    @Test fun yellowNameIsYellow() {
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(sample(0xF2C14E)!!))
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(sample(0x6FB8F0)!!))
    }

    @Test fun paleReadingsKeepTheirHue() {
        // skutečná čtení z GTA VI (záznam majitele 20. 11. 2026): Jason a „Locals“ žlutě vyhlazené do bílé
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(0xFFDBD899.toInt()))
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(0xFFD7D79E.toInt()))
        // bledě modrá (Lucia), bledě zelená (Franklin), oranžová (Trevor) a růžová zůstanou svou barvou
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(0xFFB4D2EB.toInt()))
        assertEquals(SpeakerColors.PALETTE[2], NameColor.nearest(0xFFB0E0B0.toInt()))
        assertEquals(SpeakerColors.PALETTE[5], NameColor.nearest(0xFFF5A55A.toInt()))
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(0xFFF0A0A8.toInt()))
    }

    @Test fun whiteNameHasNoColor() {
        assertNull(sample(0xF0F0F0))
    }

    @Test fun learnedAfterTwo() {
        NameColor.reset()
        NameColor.record("Aviator", 0xF5A060, 120)
        assertNull(NameColor.learned("AVIATOR"))
        NameColor.record("Aviator", 0xF0A55A, 90)
        assertEquals(SpeakerColors.PALETTE[5], NameColor.learned("aviator"))
        assertEquals(true, NameColor.report().contains("2×, průměr 105 µs, nejdéle 120 µs"))
        NameColor.reset()
    }

    @Test fun screenBeatsProfileAndReportsMismatch() {
        // GTA VI po „aktualizaci“: Jason ve hře zeleně → pruh zeleně, v záznamu neshoda s profilem
        NameColor.reset()
        NameColor.profile = GameProfiles.of("gta6").colorsByKey
        val sc = SpeakerColors(GameProfiles.of("gta6").colorsByKey)
        assertEquals(SpeakerColors.PALETTE[0], sc.colorOf("Jason"))
        NameColor.record("Jason", 0x8EDB8A, 50); NameColor.record("Jason", 0x8ADB88, 50)
        sc.learn("Jason", NameColor.learned("Jason")!!)
        assertEquals(SpeakerColors.PALETTE[2], sc.colorOf("JASON"))
        assertEquals(true, NameColor.report().contains("NESHODA: profil #F2C14E, ve hře #8EDB8A"))
        NameColor.profile = emptyMap(); NameColor.reset()
    }

    // ---- přesnější čtení: jen výplň písma, převažující odstín, medián ----

    /** Písmo [text] s bílým vyhlazením okrajů ([aa] bodů), tmavým obrysem a pozadím [bg]. */
    private fun glyphs(text: Int, fill: Int = 30, aa: Int = 12, outline: Int = 20, bg: Int = 0x303840, bgN: Int = 40): Int? {
        val cols = ArrayList<Int>()
        repeat(fill) { cols.add(text) }
        // vyhlazení do bílé: půl na půl s bílou a skoro bílé
        repeat(aa) { i -> cols.add(if (i % 2 == 0) mix(text, 0xFFFFFF) else 0xF4F4F4) }
        repeat(outline) { cols.add(0x101010) }
        repeat(bgN) { cols.add(bg) }
        val n = cols.size
        val ys = IntArray(n); val us = IntArray(n); val vs = IntArray(n)
        cols.forEachIndexed { i, c -> val (y, u, v) = yuv(c); ys[i] = y; us[i] = u; vs[i] = v }
        return NameColor.fromSamples(ys, us, vs, n)
    }

    private fun mix(a: Int, b: Int): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((b shr s) and 0xFF)) / 2
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    @Test fun pinkWithWhiteEdgesAndOutlineStaysPink() {
        // růžové jméno (GTA VI) s bílým vyhlazením a tmavým obrysem – průměr dřív táhl k bledé žluté / modré
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(glyphs(0xF07890)!!))
        // i s kouskem modrého nebe mezi písmeny (rámeček slova bez okrajů je hlavně písmo)
        assertEquals(SpeakerColors.PALETTE[3], NameColor.nearest(glyphs(0xF07890, bg = 0x4080D0, bgN = 15)!!))
    }

    @Test fun yellowAndBlueGlyphs() {
        assertEquals(SpeakerColors.PALETTE[0], NameColor.nearest(glyphs(0xF2C84A)!!))
        assertEquals(SpeakerColors.PALETTE[1], NameColor.nearest(glyphs(0x70B8F4)!!))
    }

    @Test fun whiteNameOverColourfulSceneHasNoColor() {
        // bílé jméno, za ním pár světlých barevných bodů scény
        assertNull(glyphs(0xF8F8F8, fill = 40, bg = 0xE0B040, bgN = 6))
        // málo barevných bodů vůbec
        assertNull(glyphs(0xF07890, fill = 2, aa = 0, bgN = 200))
    }

    @Test fun nameBoxFromWords() {
        val line = OcrLine("Cal Hampton: Hey", 100, 50, 400, 80, listOf(
            OcrLine("Cal", 100, 50, 140, 80), OcrLine("Hampton:", 150, 50, 270, 80), OcrLine("Hey", 290, 50, 340, 80)))
        val b = NameColor.nameBox(line, "Cal Hampton")!!
        // bez dvojtečky (1/8 šířky posledního slova) a bez okrajů
        assertEquals(listOf(103, 55, 252, 75), b.toList())
        // slova nesedí na jméno nebo chybí → null (odhad podle znaků)
        assertNull(NameColor.nameBox(line, "Jason"))
        assertNull(NameColor.nameBox(OcrLine("Jason: hi", 0, 0, 100, 30), "Jason"))
    }

    // ---- stálá naučená barva ----

    @Test fun learnedColorDoesNotFlipOnOddReadings() {
        NameColor.reset()
        val y = 0xF2C14E; val p = 0xF07890
        NameColor.record("Lucia", y, 1); NameColor.record("Lucia", y, 1)
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Lucia"))
        // dvě divná čtení, pak zase žlutá, pak tři divná – pořád žlutá
        NameColor.record("Lucia", p, 1); NameColor.record("Lucia", p, 1)
        NameColor.record("Lucia", y, 1)
        NameColor.record("Lucia", p, 1); NameColor.record("Lucia", p, 1); NameColor.record("Lucia", p, 1)
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Lucia"))
        // čtvrté za sebou → vymění se
        NameColor.record("Lucia", p, 1)
        assertEquals(SpeakerColors.PALETTE[3], NameColor.learned("Lucia"))
        NameColor.reset()
    }

    @Test fun firstLearningNeedsClearWinnerAndSettles() {
        NameColor.reset()
        val y = 0xF2C14E; val b = 0x70B8F4
        NameColor.record("Jason", y, 1); NameColor.record("Jason", b, 1); NameColor.record("Jason", y, 1)
        // 2:1 stačí
        assertEquals(SpeakerColors.PALETTE[0], NameColor.learned("Jason"))
        NameColor.reset()
        NameColor.record("Jason", y, 1); NameColor.record("Jason", b, 1)
        NameColor.record("Jason", y, 1); NameColor.record("Jason", b, 1); NameColor.record("Jason", b, 1)
        assertEquals(false, NameColor.settled("Jason"))
        repeat(6) { NameColor.record("Jason", b, 1) }
        assertEquals(SpeakerColors.PALETTE[1], NameColor.learned("Jason"))
        assertEquals(true, NameColor.settled("Jason"))
        NameColor.reset()
    }
}
