package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvSettingsTest {
    @Test fun zpravaSVolbami() {
        assertEquals("{\"typ\":\"nastaveni\",\"pruhlednost\":90,\"velikost\":22,\"podleHry\":true,\"poloha\":\"tesne\",\"original\":false}",
            TvSettings.message(90, 22, true, ScreenLayout.Position.ABOVE_TEXT, false))
        assertEquals(true, TvSettings.message(100, 22, false, ScreenLayout.Position.CUSTOM, true).contains("\"poloha\":\"vlastni\""))
    }

    @Test fun povoleneZmeny() {
        assertEquals(TvSettings.Change.Opacity(80), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":80}"))
        assertEquals(TvSettings.Change.TextSize(0), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":0}"))
        assertEquals(TvSettings.Change.TextSize(32), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":32}"))
        assertEquals(TvSettings.Change.Position(ScreenLayout.Position.TOP), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"poloha\",\"hodnota\":\"nahore\"}"))
        assertEquals(TvSettings.Change.Original(true), TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"original\",\"hodnota\":true}"))
    }

    @Test fun nepovoleneSeZahodi() {
        // jiné hodnoty, než nabízí telefon, jiné volby a jiné zprávy televize změnit nemůže
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":5}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"velikost\",\"hodnota\":500}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"poloha\",\"hodnota\":\"vlastni\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"lang\",\"hodnota\":\"en\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"original\",\"hodnota\":\"ano\"}"))
        assertNull(TvSettings.parse("{\"typ\":\"hotovo\",\"id\":3}"))
        assertNull(TvSettings.parse("nesmysl"))
        assertNull(TvSettings.parse("{\"typ\":\"zmen\",\"klic\":\"pruhlednost\",\"hodnota\":80,\"x\":\"" + "a".repeat(300) + "\"}"))
    }

    @Test fun hodnotyJakoVTelefonu() {
        assertEquals(TvSettings.OPACITIES, listOf(60, 80, 90, 100))
        assertEquals(TvSettings.TEXT_SIZES, listOf(12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48))
        assertEquals(TvSettings.TEXT_SIZES, cz.playlingo.Prefs.TEXT_SIZES.toList())
    }
}
