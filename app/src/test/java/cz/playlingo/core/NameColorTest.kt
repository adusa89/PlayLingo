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
}
