package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rozložení podle snímků GTA VI majitele (20. 11. 2026, snímek 960 × 540). */
class GameMenuTest {
    private val spec = GameProfiles.of("gta6").menuSpec!!
    private val w = 960
    private val h = 540

    private fun line(t: String, left: Int, top: Int, right: Int = left + 60, bright: Int = 60) = MenuLine(t, left, top, right, top + 12, bright)

    @Test fun pauseMenuFromLeft() {
        // snímek 175534: vybraná „Store“ (bílý pruh)
        val lines = listOf(
            line("VI", 10, 15, 40),
            line("[] Map", 22, 135), line("Game", 22, 153), line("Store", 22, 171, bright = 205),
            line("© Help", 22, 189), line("Photo Studio", 22, 207), line("Settings", 22, 225),
        )
        val v = GameMenu.read(lines, w, h, spec)!!
        assertEquals(listOf("Map", "Game", "Store", "Help", "Photo Studio", "Settings"), v.items)
        assertEquals(2, v.selected)
        // pod sebou, vybraná se značkou »; místo hned vpravo od menu
        assertEquals("Mapa\nHra\n» Obchod\nNápověda\nFotostudio\nNastavení",
            GameMenu.text(v.kind, v.items.map { spec.word(Lang.CS, it)!! }, v.selected, null, null))
        assertEquals(0.3f, v.box!!.first, 0.01f)
    }

    @Test fun settingsTabsAndDescription() {
        // snímek 175541: lišta nahoře (vybraný Controller), vpravo nadpis a popis vybrané volby
        val lines = listOf(
            line("CONTROL SCHEME", 22, 20, 80), line("CONTROLLER", 85, 20, 125, bright = 210), line("GAMEPLAY", 130, 20),
            line("DISPLAY", 165, 20), line("UI", 200, 20, 210), line("CAMERA", 215, 20), line("AUDIO", 250, 20),
            line("AIM & LOOK SENSITIVITY", 8, 48, 120),
            line("Horizontal Look Sensitivity", 12, 66, 140, bright = 200),
            line("HORIZONTAL LOOK SENSITIVITY", 775, 48, 870),
            line("The sensitivity of the horizontal camera speed when", 775, 68, 940),
            line("using © to look around.", 775, 80, 850),
        )
        val v = GameMenu.read(lines, w, h, spec)!!
        assertEquals(listOf("Control Scheme", "Controller", "Gameplay", "Display", "UI", "Camera", "Audio"), v.items)
        assertEquals(1, v.selected)
        assertEquals("Horizontal look sensitivity", v.title)
        assertEquals("The sensitivity of the horizontal camera speed when using © to look around.", v.description)
    }

    @Test fun tabsReadAsOneLine() {
        val lines = listOf(line("CONTROL SCHEME CONTROLLER GAMEPLAY DISPLAY UI CAMERA AUDIO", 20, 20, 400))
        val v = GameMenu.read(lines, w, h, spec)!!
        assertEquals(7, v.items.size)
        assertEquals(-1, v.selected)   // jeden řádek = vybraná záložka se nepozná
    }

    @Test fun tabsInOneLineWithWordBoxes() {
        // skutečná poloha lišty na snímku 175541 (text 41–53 bodů z 540, tj. kolem 8 %), vybraný Controller bílý
        fun wd(t: String, l: Int, r: Int, b: Int = 70) = MenuWord(t, l, 41, r, 53, b)
        val words = listOf(wd("CONTROL", 49, 92), wd("SCHEME", 95, 128), wd("CONTROLLER", 146, 203, 215), wd("GAMEPLAY", 222, 268),
            wd("DISPLAY", 286, 323), wd("UI", 341, 350), wd("CAMERA", 369, 405), wd("AUDIO", 423, 451))
        val l = MenuLine(words.joinToString(" ") { it.text }, 49, 41, 451, 53, 90, words)
        val head = MenuLine("HORIZONTAL LOOK SENSITIVITY", 591, 96, 777, 110, 90)
        val b1 = MenuLine("The sensitivity of the horizontal camera speed when", 591, 134, 914, 147, 90)
        val b2 = MenuLine("using R to look around.", 591, 153, 742, 166, 90)
        val v = GameMenu.read(listOf(l, head, b1, b2), w, h, spec)!!
        assertEquals("settings", v.kind)
        assertEquals(7, v.items.size)
        assertEquals(1, v.selected)   // s rámečky slov se pozná i ve spojeném řádku
        assertEquals(146f / w, v.rects[1][0], 0.001f)
        // vybraná záložka těsně nad lištou: spodní okraj pruhu nad horní hranou záložky, levý okraj u záložky
        val (box, step) = GameMenu.tabPlace(v.rects[1])!!
        val top = box.second - step * 0.25f
        assertEquals(41f / h - 0.004f, top + step, 0.001f)
        assertTrue(top >= 0f)
        assertEquals(146f / w - 0.006f, box.first, 0.001f)
        // popis přes anglický popis vpravo: od nadpisu, do pravého okraje sloupce, pod poslední řádek
        val d = v.descBox!!
        assertEquals(591f / w - 0.006f, d[0], 0.001f)
        assertEquals(96f / h - 0.008f, d[1], 0.001f)
        assertTrue(d[2] >= 0.97f)
        assertEquals(166f / h + 0.008f, d[3], 0.001f)
        assertEquals(13f / h, v.descLine, 0.001f)
        assertEquals("The sensitivity of the horizontal camera speed when using R to look around.", v.description)
    }

    @Test fun pauseRectIsRightOfText() {
        // „Settings“ končí na 96 bodech z 960 → měří se kousek bílého pruhu vpravo za textem (pruh je do ~27 %)
        val r = GameMenu.pauseRect(MenuLine("Settings", 45, 297, 96, 311, 200), w, h)
        assertTrue(r[0] > 96f / w && r[2] <= 0.25f)
        assertEquals(297f / h, r[1], 0.001f)
    }

    @Test fun fastSelectionFromBrightness() {
        val t = MenuTracker(spec)
        val items = listOf("Map", "Game", "Store", "Help", "Photo Studio", "Settings")
        fun read(sel: Int) = MenuView("pause", items, sel, tops = items.indices.map { 124 + it * 36 },
            rects = items.indices.map { floatArrayOf(0.12f, (124f + it * 36) / h, 0.17f, (138f + it * 36) / h) })
        t.update(read(5), grabbedAt = 1000L)
        assertEquals(6, t.sampleRects().size)
        // na novém snímku je bílý pruh u „Photo Studio“ → výběr hned, bez čtení
        val r = t.fast(intArrayOf(45, 46, 44, 45, 210, 50), 1100L)
        assertTrue(r.changed)
        assertEquals(4, r.view!!.selected)
        // nejasné měření (přechod) nic nemění
        assertEquals(false, t.fast(intArrayOf(120, 110, 44, 45, 100, 50), 1150L).changed)
        // čtení snímku pořízeného před měřením (výběr ještě na Settings) novější výběr nepřebije
        assertEquals(4, t.update(read(5), grabbedAt = 1050L).view!!.selected)
        // čtení novějšího snímku ano
        assertEquals(3, t.update(read(3), grabbedAt = 1200L).view!!.selected)
    }

    @Test fun watchBandsDetectMovedBar() {
        val a = IntArray(GameMenu.WATCH.size) { 60 }
        val b = a.copyOf().also { it[3] = 200; it[4] = 60 }
        assertTrue(GameMenu.watchChanged(a, b))
        assertEquals(false, GameMenu.watchChanged(a, a.copyOf().also { it[2] = 80 }))
        assertEquals(false, GameMenu.watchChanged(null, a))
    }

    @Test fun gameplayIsNotMenu() {
        // obyčejná hra: titulek dole, „Pickups 7 / 17“ vpravo, „KARIN“ u minimapy
        val lines = listOf(line("Pickups 7 / 17", 835, 140), line("KARIN", 78, 370), line("Local: Hey! Open your nictitating membrane", 300, 470, 700))
        assertNull(GameMenu.read(lines, w, h, spec))
        // jedno slovo z menu ve hře nestačí
        assertNull(GameMenu.read(listOf(line("Map", 22, 200), line("Store", 22, 230)), w, h, spec))
    }

    @Test fun boxNeverMovesDownWhileMenuIsOpen() {
        val t = MenuTracker(spec)
        fun read(y: Float, vararg items: String) = MenuView("pause", items.toList(), -1, box = 0.3f to y)
        // vybraná horní „Map“ se přečetla → seznam začíná u ní
        assertEquals(0.25f, t.update(read(0.25f, "Map", "Game", "Store")).view!!.box!!.second, 0.001f)
        // další čtení ji vynechá (začíná u Game níž) → pozice zůstane
        assertEquals(0.25f, t.update(read(0.28f, "Game", "Store", "Help")).view!!.box!!.second, 0.001f)
    }

    @Test fun rowStepFromMenuItems() {
        // položky po 18 px na snímku 540 px; chybějící (dvojnásobný rozdíl) rozestup nezkreslí
        assertEquals(18f / h, GameMenu.rowStep(listOf(135, 153, 189, 207), h), 0.0001f)
        assertEquals(0f, GameMenu.rowStep(listOf(135), h), 0f)
    }

    @Test fun selectedNeedsClearlyBrighterRow() {
        assertEquals(-1, GameMenu.selected(listOf(60, 70, 80)))
        assertEquals(1, GameMenu.selected(listOf(60, 200, 70)))
    }

    @Test fun trackerFillsGapsAndFindsUnreadSelection() {
        val t = MenuTracker(spec)
        fun read(vararg items: String) = MenuView("pause", items.toList(), -1, tops = items.map { spec.pause.indexOf(it) * 18 + 135 })
        // 1. čtení: vše kromě „Map“ (vybraná – tmavé písmo na bílém pruhu se nepřečetlo) a „Photo Studio“
        var r = t.update(read("Game", "Store", "Help", "Settings"))
        assertEquals(listOf("Game", "Store", "Help", "Settings"), r.view!!.items)
        // 2. čtení: Photo Studio přibude, Map pořád chybí → zůstane bez výběru, dokud chybí víc než jedna
        r = t.update(read("Game", "Store", "Help", "Photo Studio", "Settings"))
        assertEquals(5, r.view!!.items.size)
        // 3. čtení: Map přečtená (výběr se posunul na Game, ta teď chybí) → doplněno a vybraná = jediná chybějící
        r = t.update(read("Map", "Store", "Help", "Photo Studio", "Settings"))
        assertEquals(listOf("Map", "Game", "Store", "Help", "Photo Studio", "Settings"), r.view!!.items)
        assertEquals(1, r.view!!.selected)
        // čtení bez menu → nic se nemění (neblikne), až po MISSES za sebou → menu zavřené
        repeat(MenuTracker.MISSES - 1) { assertEquals(false, t.update(null).changed) }
        assertNull(t.update(null).view)
    }
}
