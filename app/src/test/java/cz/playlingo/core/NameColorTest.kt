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
}
