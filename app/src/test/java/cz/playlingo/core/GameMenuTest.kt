package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun gameplayIsNotMenu() {
        // obyčejná hra: titulek dole, „Pickups 7 / 17“ vpravo, „KARIN“ u minimapy
        val lines = listOf(line("Pickups 7 / 17", 835, 140), line("KARIN", 78, 370), line("Local: Hey! Open your nictitating membrane", 300, 470, 700))
        assertNull(GameMenu.read(lines, w, h, spec))
        // jedno slovo z menu ve hře nestačí
        assertNull(GameMenu.read(listOf(line("Map", 22, 200), line("Store", 22, 230)), w, h, spec))
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
        // jedno čtení bez menu → nic se nemění (neblikne), druhé → menu zavřené
        assertEquals(false, t.update(null).changed)
        assertNull(t.update(null).view)
    }
}
