package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Řádek s úkolem pod titulky (GTA) – vyčlenění z titulků a sledování (od 0.97.43). */
class ObjectiveTest {
    private fun line(t: String, top: Int, h: Int = 30) = OcrLine(t, 400, top, 1500, top + h)
    private fun block(vararg l: OcrLine) = OcrBlock(l.toList())

    @Test fun ukolPodTitulkemSVolnymRadkem() {
        // titulek 600–630, volný řádek, úkol 670–700
        val p = TextLogic.assemble(
            listOf(block(line("JASON: We need to get out of here.", 600)), block(line("Go to the safehouse.", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertEquals("JASON: We need to get out of here.", p.text)
        assertEquals("Go to the safehouse.", p.objective?.text)
    }

    /** Skutečné případy ze záznamu majitele (21. 11. 2026, GTA VI): druhá část titulku se brala jako úkol. */
    @Test fun pokracovaniTitulkuNeniUkol() {
        fun obj(a: String, b: String) = TextLogic.assemble(
            listOf(block(line(a, 600)), block(line(b, 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        ).objective
        assertNull(obj("Protester: Anyone who will join the fight for our Mother Earth and", "all her children,"))
        assertNull(obj("Protester: Our sister, the orca, is trapped inside this aquarium,", "and we're gonna bring her back to the great Mother."))
        assertNull(obj("Protester: we have a special fund just for these kinds of", "emergencies,"))
        assertNull(obj("Security Guard: Get down from there now,", "or I'm calling the cops!"))
        // úkol pod ukončenou větou zůstává úkolem
        assertEquals("Follow Lucia", obj("JASON: Let's go.", "Follow Lucia")?.text)
        // na naučeném místě úkolu: mluvčí (i s chybou čtení) a zvuk v závorkách úkol nejsou
        val at = { t: String -> TextLogic.assemble(listOf(block(line(t, 670))), 10, true, 950, 300, objectives = true, objectiveTop = 670).objective }
        assertNull(at("Protester: [pained] Ugh, my skin is starting to hurt!"))
        assertNull(at("[thuddingl"))
        assertEquals("Go to the safehouse.", at("Go to the safehouse.")?.text)
    }

    @Test fun ukolJenUplneDole() {
        // stejné dva řádky, úkol by byl na 670 px – s pásem pro úkol od 680 px (výš = titulek) úkol není
        fun obj(minTop: Int) = TextLogic.assemble(
            listOf(block(line("JASON: Let's go.", 600)), block(line("Follow Lucia", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
            objectiveMinTop = minTop,
        ).objective
        assertNull(obj(680))
        assertEquals("Follow Lucia", obj(650)?.text)
    }

    @Test fun tvarUkolu() {
        assertTrue(TextLogic.objectiveShape("Take your shot"))
        assertTrue(!TextLogic.objectiveShape("emergencies,"))
        assertTrue(!TextLogic.objectiveShape("Or I'm calling the cops!"))
        assertTrue(!TextLogic.objectiveShape("Security Guard: Get down"))
        assertTrue(TextLogic.endsSentence("We need money."))
        assertTrue(!TextLogic.endsSentence("for our Mother Earth and"))
    }

    @Test fun dvouradkovyTitulekBezMezeryNeniUkol() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out", 600), line("of here right now.", 636))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertNull(p.objective)
        assertEquals("We need to get out of here right now.", p.text)
    }

    @Test fun bezVolbyVProfiluSeNevycleni() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out of here.", 600)), block(line("Go to the safehouse.", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300,
        )
        assertNull(p.objective)
    }

    @Test fun samotnyUkolNaNaucenemMiste() {
        val alone = listOf(block(line("Go to the safehouse.", 672)))
        // místo ještě neznámé → běžný řádek (titulek)
        assertNull(TextLogic.assemble(alone, 10, true, 950, 300, objectives = true).objective)
        // na naučeném místě → úkol, titulek prázdný
        val p = TextLogic.assemble(alone, 10, true, 950, 300, objectives = true, objectiveTop = 670)
        assertEquals("Go to the safehouse.", p.objective?.text)
        assertEquals("", p.text)
        // jinde → titulek
        assertNull(TextLogic.assemble(listOf(block(line("Where are you going?", 600))), 10, true, 950, 300,
            objectives = true, objectiveTop = 670).objective)
    }

    @Test fun replikaSMluvcimNeniUkol() {
        val p = TextLogic.assemble(
            listOf(block(line("We need to get out of here.", 600)), block(line("LUCIA: Hurry up!", 670))),
            minLineHeight = 10, subtitlesOnly = true, screenCenterX = 950, centerTolerance = 300, objectives = true,
        )
        assertNull(p.objective)
    }

    @Test fun sledovaniUkolu() {
        val t = ObjectiveTracker(misses = 2)
        assertTrue(t.update("Go to the safehouse.", 0.9f) is ObjectiveTracker.Change.Show)
        // drobná chyba čtení = stejný úkol
        assertEquals(ObjectiveTracker.Change.Keep, t.update("Go to the safehcuse.", 0.9f))
        // jedno vynechané čtení úkol neschová, druhé ano
        assertEquals(ObjectiveTracker.Change.Keep, t.update(null))
        assertEquals(ObjectiveTracker.Change.Hide, t.update(null))
        assertEquals(ObjectiveTracker.Change.Keep, t.update(null))
        // nový úkol
        assertTrue(t.update("Follow Lucia.", 0.9f) is ObjectiveTracker.Change.Show)
        assertTrue(t.update("Take the car.", 0.9f) is ObjectiveTracker.Change.Show)
        assertTrue(t.top in 0.89f..0.91f)
    }

    @Test fun zpravaProTelevizi() {
        val m = TvSender.objectiveMessage("Jdi do úkrytu.", floatArrayOf(0.2f, 0.9f, 0.8f, 0.93f), 40, 90, "#FFFFFF")
        assertEquals("{\"typ\":\"ukol\",\"text\":\"Jdi do úkrytu.\",\"misto\":[0.2000,0.9000,0.8000,0.9300],\"pismo\":40,\"pozadi\":90,\"barva\":\"#FFFFFF\"}", m)
        assertEquals("{\"typ\":\"ukol\",\"text\":\"\"}", TvSender.objectiveMessage(null))
    }
}
