package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jazyk aplikace a jazyk překladu jsou dvě různá nastavení; angličtina je jen jazyk aplikace. */
class LangTest {
    @Test fun englishIsNotATranslationTarget() {
        assertFalse(Lang.EN.isTarget)
        assertFalse(Lang.EN in Lang.targets)
        assertEquals(Lang.values().size - 1, Lang.targets.size)
        // uložený kód „en“ ani neznámý kód nesmí z jazyka překladu udělat angličtinu
        assertEquals(Lang.CS, Lang.of("en"))
        assertEquals(Lang.CS, Lang.of(null))
        assertEquals(Lang.CS, Lang.of("xx"))
        assertEquals(Lang.DE, Lang.of("de"))
    }

    @Test fun appLanguageIncludesEnglish() {
        assertEquals(Lang.EN, Lang.ui("en"))
        assertEquals(Lang.UK, Lang.ui("uk"))
        assertNull(Lang.ui("ja"))      // pro japonštinu aplikace texty nemá → volající zvolí angličtinu
        assertNull(Lang.ui(null))
    }

    @Test fun switchingGoesThroughAllAppLanguages() {
        var l = Lang.CS
        val seen = HashSet<Lang>()
        repeat(Lang.values().size) { seen.add(l); l = l.next() }
        assertEquals(Lang.values().toSet(), seen)
        assertEquals(Lang.CS, l)
    }

    @Test fun englishTextsAreReallyEnglish() {
        assertEquals("Settings", I18n.t(Lang.EN, "m_settings"))
        assertEquals("Translation language", I18n.t(Lang.EN, "l_lang"))
        assertEquals("App language", I18n.t(Lang.EN, "l_ui_lang"))
        // žádný anglický text nesmí zůstat česky (poznalo by se podle háčků a čárek)
        val czech = Regex("[ěščřžýáíéúůďťňĚŠČŘŽÝÁÍÉÚŮĎŤŇ]")
        for (k in I18n.keys) assertFalse("$k: " + I18n.t(Lang.EN, k), czech.containsMatchIn(I18n.t(Lang.EN, k)))
        // obě nastavení mají v každém jazyce jiný název, ať se nepletou
        for (lang in Lang.values()) assertTrue("$lang", I18n.t(lang, "l_lang") != I18n.t(lang, "l_ui_lang"))
    }
}
