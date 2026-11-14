package cz.testpreklad.core

import org.junit.Assert.*
import org.junit.Test

class TranslationCacheTest {

    @Test fun roundTripWithSpecialChars() {
        val c = TranslationCache()
        c.put("Hello\tthere\nfriend \\ ok", "Ahoj\ttam\nkámo \\ jo")
        c.put("Dutch: \"Faith!\"", "Dutch: „Víru!“ ěščřžýáíé")
        val c2 = TranslationCache()
        c2.load(c.dump())
        assertEquals("Ahoj\ttam\nkámo \\ jo", c2.get("Hello\tthere\nfriend \\ ok".let { TranslationCache.normalize(it) }))
        assertEquals("Dutch: „Víru!“ ěščřžýáíé", c2.get("Dutch: \"Faith!\""))
        assertEquals(2, c2.size())
    }

    @Test fun normalizesWhitespaceInKeys() {
        val c = TranslationCache()
        c.put("  We   need money. ", "Potřebujeme peníze.")
        assertEquals("Potřebujeme peníze.", c.get("We need money."))
    }

    @Test fun lruEvictsOldest() {
        val c = TranslationCache(maxSize = 3)
        c.put("a1", "1"); c.put("a2", "2"); c.put("a3", "3")
        c.get("a1")             // a1 je teď nejčerstvější
        c.put("a4", "4")
        assertNull(c.get("a2"))
        assertNotNull(c.get("a1"))
        assertEquals(3, c.size())
    }

    @Test fun dirtyCounter() {
        val c = TranslationCache()
        c.put("x", "y"); c.put("x", "y")
        assertEquals(1, c.dirty)
        c.dump()
        assertEquals(0, c.dirty)
    }

    @Test fun ignoresEmptyAndCorruptLines() {
        val c = TranslationCache()
        c.put("", "něco"); c.put("něco", " ")
        assertEquals(0, c.size())
        c.load("bez tabulatoru\n\t prazdny klic\nok\tdobře\n\n")
        assertEquals(1, c.size())
        assertEquals("dobře", c.get("ok"))
    }
}


class FrameSignatureTest {
    private val cell = 500

    @Test fun sameFrameIsNotAChange() {
        val a = IntArray(FrameSignature.SIZE) { 40 * cell }
        assertFalse(FrameSignature.changed(a, a.copyOf(), cell))
    }

    @Test fun videoNoiseIsNotAChange() {
        val a = IntArray(FrameSignature.SIZE) { 40 * cell }
        val b = IntArray(FrameSignature.SIZE) { 40 * cell + (if (it % 2 == 0) 2 else -2) * cell }
        assertFalse(FrameSignature.changed(a, b, cell))
    }

    @Test fun smallSubtitleInAFewCellsIsAChange() {
        // drobný bílý titulek: v pěti buňkách přibude 15 % světlých pixelů, zbytek obrazu stejný
        val a = IntArray(FrameSignature.SIZE) { 40 * cell }
        val b = a.copyOf()
        for (i in 700 until 705) b[i] += (cell * 0.15 * 180).toInt()
        assertTrue(FrameSignature.changed(a, b, cell))
    }

    @Test fun darkTextInALightBoxIsAChange() {
        val a = IntArray(FrameSignature.SIZE) { 230 * cell }
        val b = a.copyOf()
        b[800] -= (cell * 0.1 * 200).toInt()
        assertTrue(FrameSignature.changed(a, b, cell))
    }

    // --- celá cesta: snímek → výřez → otisk ---
    private val fw = 1920; private val fh = 1080
    private fun frame(bg: Int, noise: Int = 0, seed: Int = 1): ByteArray {
        val r = java.util.Random(seed.toLong())
        return ByteArray(fw * fh) { (bg + if (noise > 0) r.nextInt(2 * noise + 1) - noise else 0).coerceIn(0, 255).toByte() }
    }
    /** "Titulek": řádek svislých tahů tlustých 2 px, vysoký [size] px, široký [width] px, dole uprostřed. */
    private fun subtitle(f: ByteArray, size: Int, width: Int, value: Int) {
        val top = (fh * 0.88f).toInt(); val left = (fw - width) / 2
        for (yy in top until top + size) for (x in left until left + width) if (x % 9 < 2) f[yy * fw + x] = value.toByte()
    }
    private fun sigOf(f: ByteArray): Pair<IntArray, Int> {
        val x0 = (fw * 0.05f).toInt(); val x1 = (fw * 0.95f).toInt(); val y0 = (fh * 0.72f).toInt(); val y1 = fh
        val w = x1 - x0; val h = y1 - y0
        val sig = IntArray(FrameSignature.SIZE)
        FrameSignature.cropGray(f, fw, x0, y0, w, h, IntArray(w * h), sig)
        return sig to w * h / FrameSignature.SIZE
    }

    @Test fun cropGrayCountsEveryPixelOnce() {
        val f = frame(100)
        val (sig, _) = sigOf(f)
        val w = (fw * 0.95f).toInt() - (fw * 0.05f).toInt(); val h = fh - (fh * 0.72f).toInt()
        assertEquals(100L * w * h, sig.sumOf { it.toLong() })
    }

    @Test fun tinySubtitleAppearingOnADarkStillSceneIsNoticed() {
        // nejhorší případ: tmavá nehybná scéna, drobný titulek (1,6 % výšky), jen pár slov
        val empty = frame(30, noise = 2, seed = 1)
        val withText = frame(30, noise = 2, seed = 2).also { subtitle(it, 17, 220, 235) }
        val (a, cell) = sigOf(empty); val (b, _) = sigOf(withText)
        assertTrue(FrameSignature.changed(a, b, cell))
    }

    @Test fun stillSceneWithVideoNoiseIsSkipped() {
        val (a, cell) = sigOf(frame(30, noise = 3, seed = 1)); val (b, _) = sigOf(frame(30, noise = 3, seed = 2))
        assertFalse(FrameSignature.changed(a, b, cell))
    }

    @Test fun subtitleReplacedByAnotherOfSimilarLengthIsNoticed() {
        val one = frame(30).also { subtitle(it, 24, 600, 235) }
        val two = frame(30).also { subtitle(it, 24, 600, 235) }
        // jiná slova = tahy jinde: posun o 4 px v polovině řádku
        val top = (fh * 0.88f).toInt()
        for (yy in top until top + 24) for (x in 960 until 1260) two[yy * fw + x] = (if ((x + 4) % 9 < 2) 235 else 30).toByte()
        val (a, cell) = sigOf(one); val (b, _) = sigOf(two)
        assertTrue(FrameSignature.changed(a, b, cell))
    }

    @Test fun greyOrDarkSubtitlesAreNoticedToo() {
        val (a, cell) = sigOf(frame(60))
        val (grey, _) = sigOf(frame(60).also { subtitle(it, 22, 400, 170) })          // šedý text
        assertTrue(FrameSignature.changed(a, grey, cell))
        val (light, _) = sigOf(frame(225))
        val (dark, _) = sigOf(frame(225).also { subtitle(it, 22, 400, 20) })          // tmavý text ve světlém okně
        assertTrue(FrameSignature.changed(light, dark, cell))
    }

    @Test fun missingOrDifferentSizeMeansChanged() {
        assertTrue(FrameSignature.changed(null, IntArray(4), cell))
        assertTrue(FrameSignature.changed(IntArray(3), IntArray(4), cell))
    }
}

class SubtitleHistoryTest {
    @Test fun newestFirstAndDedup() {
        SubtitleHistory.clear()
        SubtitleHistory.add("Hello.", "Ahoj.", 1)
        SubtitleHistory.add("Hello.", "Ahoj.", 2)
        SubtitleHistory.add("Bye now.", "Zatím.", 3)
        val all = SubtitleHistory.all()
        assertEquals(2, all.size)
        assertEquals("Bye now.", all[0].original)
        SubtitleHistory.clear()
    }
}
