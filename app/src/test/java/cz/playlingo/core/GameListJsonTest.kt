package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

class GameListJsonTest {
    private val builtin = GameProfiles.BUILTIN
    private fun read(text: String, app: Int = GameProfiles.MIN_APP) = GameListJson.parse(text, app)

    private fun same(a: GameProfile, b: GameProfile) {
        assertEquals(a.id, b.id)
        assertEquals(a.title, b.title)
        assertEquals(a.id, a.names, b.names)
        assertEquals(a.id, a.common, b.common)
        assertEquals(a.id, a.characters, b.characters)
        assertEquals(a.id, a.terms, b.terms)
        assertEquals(a.id, a.hud, b.hud)
        assertEquals(a.id, a.menu, b.menu)
        assertEquals(a.id, a.skip.map { it.pattern to it.options }, b.skip.map { it.pattern to it.options })
        assertEquals(a.id, a.prompts, b.prompts)
        assertEquals(a.id, a.colors, b.colors)
        assertEquals(a.id, a.colorsByKey, b.colorsByKey)
        assertEquals(a.id, a.hint, b.hint)
        assertEquals(a.id, a.menuSpec?.pause, b.menuSpec?.pause)
        assertEquals(a.id, a.menuSpec?.tabs, b.menuSpec?.tabs)
        assertEquals(a.id, a.menuSpec?.words, b.menuSpec?.words)
    }

    @Test fun roundTripOfBuiltinProfiles() {
        val text = GameListJson.write(builtin)
        val r = read(text)
        val c = r.catalog
        assertNotNull(r.problem, c)
        c!!
        assertEquals(GameProfiles.VERSION, c.version)
        assertTrue(c.downloaded)
        assertEquals(builtin.games.size, c.games.size)
        builtin.games.zip(c.games).forEach { (a, b) -> same(a, b) }
        same(builtin.universal, c.universal)
        assertEquals(builtin.ambiguous, c.ambiguous)
        assertEquals(builtin.all.map { it.id }, c.all.map { it.id })
        // stejné chování: rod, nápisy, menu
        assertEquals(Gender.F, c.of("gta6").cast.genderOf("ANDY"))
        assertTrue(c.of("diablo4").screen.isHud("[Trade] WTS sword"))
        assertEquals("Mapa", c.of("gta6").menuSpec!!.word(Lang.CS, "Map"))
        // zápis přečteného dá tentýž text
        assertEquals(text, GameListJson.write(c))
    }

    @Test fun universalAndGuessStayAsBefore() {
        assertFalse("Cheetah" in builtin.universal.names)
        assertTrue("Port Gellhorn" in builtin.universal.names)
        assertTrue("Jason" in builtin.ambiguous)
        assertTrue("Map" in builtin.ambiguous)
    }

    @Test fun badInputNeverCrashes() {
        for (bad in listOf("", "x", "[]", "{}", "{\"format\":2,\"verze\":5,\"hry\":[]}", "{\"format\":1,\"verze\":0,\"hry\":[]}",
            "{\"format\":1,\"verze\":3,\"hry\":[]}", "{\"format\":1,\"verze\":3,\"hry\":[{\"id\":\"all\",\"nazev\":\"x\"}]}",
            "{\"format\":1,\"verze\":3,\"hry\":\"x\"}", "{".repeat(100))) {
            assertNull(bad, read(bad).catalog)
        }
        assertNull(read("{\"format\":1,\"verze\":3,\"hry\":[]}" + " ".repeat(GameListJson.MAX_SIZE)).catalog)
    }

    @Test fun invalidEntriesAreSkipped() {
        val text = """{"format":1,"verze":7,"minAppVerze":1,"hry":[
            {"id":"Bad Id","nazev":"x"},
            {"id":"nova","nazev":"Nová hra","jmena":["Zorg",5,"","${"x".repeat(200)}"],
             "postavy":[{"jmeno":"Zorg","rod":"f","prezdivky":["Zo"]},{"jmeno":"Kdo","rod":"x"}],
             "pojmy":{"cs":{"heist":"loupež"},"xx":{"a":"b"}},
             "preskocit":[{"vzor":"(unclosed"},{"vzor":"^\\[chat\\]"}],
             "barvy":{"Zorg":"#FF00FF00","Kdo":"red"},
             "rada":"neexistuje"},
            {"id":"nova","nazev":"Duplicitní"}]}"""
        val c = read(text).catalog!!
        assertEquals(listOf("nova"), c.games.map { it.id })
        val g = c.of("nova")
        assertEquals("Nová hra", g.title)
        assertEquals(listOf("Zorg"), g.names)
        assertEquals(listOf(GameCharacter("Zorg", Gender.F, listOf("Zo"))), g.characters)
        assertEquals(mapOf("cs" to mapOf("heist" to "loupež")), g.terms)
        assertEquals(1, g.skip.size)
        assertTrue(g.screen.isHud("[CHAT] ahoj"))
        assertEquals(mapOf("Zorg" to 0xFF00FF00.toInt()), g.colors)
        assertNull(g.hint)
        assertEquals(7, c.version)
    }

    @Test fun listForNewerAppIsSkipped() {
        val text = "{\"format\":1,\"verze\":9,\"minAppVerze\":999,\"hry\":[{\"id\":\"a\",\"nazev\":\"A\"}]}"
        assertNull(read(text, 500).catalog)
        assertNotNull(read(text, 999).catalog)
        assertEquals(9, read(text, 500).version)
    }

    @Test fun switchingCatalogKeepsUnknownIdOnUniversal() {
        val c = read("{\"format\":1,\"verze\":9,\"hry\":[{\"id\":\"jina\",\"nazev\":\"Jiná\",\"jmena\":[\"Qwerty\"]}]}").catalog!!
        try {
            GameProfiles.use(c)
            assertEquals("all", GameProfiles.of("gta6").id)       // hra v seznamu chybí → univerzální, volba se nemění
            assertEquals("jina", GameProfiles.of("jina").id)
            assertTrue("Qwerty" in GameProfiles.UNIVERSAL.names)
        } finally { GameProfiles.use(null) }
        assertEquals("gta6", GameProfiles.of("gta6").id)
    }

    @Test fun versionFile() {
        assertEquals(12, GameListJson.versionOf(" 12\n"))
        assertNull(GameListJson.versionOf("<html>"))
        assertNull(GameListJson.versionOf("0"))
    }

    /** Soubor na stránkách (pomoc/hry) musí odpovídat profilům v kódu – jinak `gradlew -p ci/mistni-kontrola seznamHer`. */
    @Test fun publishedListMatchesBuiltinProfiles() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "pomoc/hry/profily.json").exists()) dir = dir.parentFile
        Assume.assumeTrue("pomoc/hry/profily.json nenalezen", dir != null)
        val json = File(dir, "pomoc/hry/profily.json").readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertEquals("pomoc/hry/profily.json neodpovídá profilům v kódu – zvyš GameProfiles.VERSION a spusť " +
            "gradlew -p ci/mistni-kontrola seznamHer", GameListJson.write(builtin), json)
        assertEquals(GameProfiles.VERSION, GameListJson.versionOf(File(dir, "pomoc/hry/verze.txt").readText()))
    }
}
