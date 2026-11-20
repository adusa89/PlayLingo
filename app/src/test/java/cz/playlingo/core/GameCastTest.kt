package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rod postav v českém překladu (GTA VI: Lucia/Lu = žena, Jason/Jas = muž). */
class GameCastTest {
    private val gta6 = GameProfiles.of("gta6").cast
    private val all = GameProfiles.UNIVERSAL.cast
    private fun t(en: String, cs: String, speaker: String? = null, cast: Cast = gta6) = CzechGender.fix(cast, en, cs, speaker)

    @Test fun speakerGender() {
        assertEquals(Gender.F, gta6.genderOf("LUCIA"))
        assertEquals(Gender.F, gta6.genderOf("Lu"))
        assertEquals(Gender.M, gta6.genderOf("JASON"))
        assertEquals(Gender.M, gta6.genderOf("Jay"))
        assertEquals(Gender.M, gta6.genderOf("Jas"))
        assertEquals(Gender.M, gta6.genderOf("CAL"))          // první slovo jména Cal Hampton
        assertEquals(Gender.F, gta6.genderOf("BAE-LUXE"))
        assertNull(gta6.genderOf("STRANGER"))
        // univerzální profil zná postavy všech her
        assertEquals(Gender.F, all.genderOf("Lucia"))
        assertEquals(Gender.M, all.genderOf("Jason"))
        assertEquals(Gender.F, all.genderOf("ELLIE"))
    }

    @Test fun firstPersonFollowsSpeaker() {
        assertEquals("Byla jsem připravená.", t("I was ready.", "Byl jsem připravený.", "LUCIA"))
        assertEquals("Řekla jsem ti to.", t("I told you.", "Řekl jsem ti to.", "Lucia"))
        assertEquals("Nevím, co jsem udělala.", t("I don't know what I did.", "Nevím, co jsem udělal.", "LU"))
        assertEquals("Kdybych to věděla, šla bych s tebou.", t("If I knew, I would go with you.", "Kdybych to věděl, šel bych s tebou.", "LUCIA"))
        assertEquals("Jsem ráda, že jsi tady.", t("I'm glad you're here.", "Jsem rád, že jsi tady.", "LUCIA"))
        assertEquals("Přišla jsem sama.", t("I came alone.", "Přišel jsem sám.", "LUCIA"))
        assertEquals("Jsem si jistá.", t("I'm sure.", "Jsem si jistý.", "LUCIA"))
        // muž zůstává, jak překladač napsal; ženský tvar u muže se opraví
        assertEquals("Byl jsem připravený.", t("I was ready.", "Byl jsem připravený.", "JASON"))
        assertEquals("Byl jsem připravený.", t("I was ready.", "Byla jsem připravená.", "JASON"))
        // neznámý mluvčí ani bez mluvčího se nic nemění
        assertEquals("Byl jsem připravený.", t("I was ready.", "Byl jsem připravený.", "COP"))
        assertEquals("Byl jsem připravený.", t("I was ready.", "Byl jsem připravený."))
    }

    @Test fun secondPersonFollowsAddressee() {
        assertEquals("Lu, měla jsi pravdu.", t("Lu, you were right.", "Lu, měl jsi pravdu.", "JASON"))
        assertEquals("Zbláznila ses, Lucia?", t("Are you crazy, Lucia?", "Zbláznil ses, Lucia?", "JASON"))
        assertEquals("Buď opatrná, Lu.", t("Be careful, Lu.", "Buď opatrný, Lu.", "JASON"))
        assertEquals("Hej, Jasi, kde jsi byl?", t("Hey Jas, where were you?", "Hej, Jasi, kde jsi byl?", "LUCIA"))
        // mluvčí a oslovený zároveň
        assertEquals("Myslela jsem, že jsi byl mrtvý, Jasone.",
            t("I thought you were dead, Jason.", "Myslel jsem, že jsi byl mrtvý, Jasone.", "LUCIA"))
        // bez oslovení se druhá osoba nemění (nevíme, ke komu mluví)
        assertEquals("Měl jsi pravdu.", t("You were right.", "Měl jsi pravdu.", "LUCIA"))
    }

    @Test fun thirdPersonAfterName() {
        assertEquals("Lucia šla domů.", t("Lucia went home.", "Lucia šel domů."))
        assertEquals("Lucia už odešla.", t("Lucia already left.", "Lucia už odešel."))
        assertEquals("Lucia je připravená.", t("Lucia is ready.", "Lucia je připravený."))
        assertEquals("Lucia byla zraněná.", t("Lucia was hurt.", "Lucia byl zraněný."))
        assertEquals("Jason řekl, že přijde.", t("Jason said he'd come.", "Jason řekl, že přijde."))
        // čárka za jménem – dál už jde o něco jiného
        assertEquals("Lucia, viděl jsem to.", t("Lucia, I saw it.", "Lucia, viděl jsem to.", "JASON"))
    }

    @Test fun leavesOtherWordsAlone() {
        assertEquals("Jsem tvůj přítel.", t("I'm your friend.", "Jsem tvůj přítel.", "LUCIA"))
        assertEquals("Jsem Daniel.", t("I'm Daniel.", "Jsem Daniel.", "LUCIA"))
        assertEquals("Šla jsem dál.", t("I kept going.", "Šel jsem dál.", "LUCIA"))
        assertEquals("Jsem tady.", t("I'm here.", "Jsem tady.", "LUCIA"))
        // jiný jazyk než čeština se nemění
        val g = Glossary(cast = gta6)
        assertEquals("Bol som pripravený.", g.genders(Lang.SK, "I was ready.", "Bol som pripravený.", "LUCIA"))
        assertEquals("Byla jsem připravená.", g.genders(Lang.CS, "I was ready.", "Byl jsem připravený.", "LUCIA"))
    }

    @Test fun guessesGameFromNames() {
        val g = GameGuess()
        g.onText("LUCIA: We should lay low in Key Lento.")
        assertNull(g.guess())                          // jedno jméno nestačí
        g.onText("JASON: Brian Heder wants the boat back.")
        g.onText("Meet me at the Jack of Hearts in Little Cuba.")
        assertEquals("gta6", g.guess()?.id)
        // jména z jiné hry stejně často → nejisté
        val mix = GameGuess()
        mix.onText("Arthur Morgan rode with Dutch and Sadie.")
        mix.onText("Key Lento, Port Gellhorn and Little Cuba.")
        assertNull(mix.guess())
        // běžná slova a krátké přezdívky hru neprozradí
        val common = GameGuess()
        repeat(5) { common.onText("Jason, Lu, Michael and Peter went downtown.") }
        assertNull(common.guess())
    }

    @Test fun skipsHudAndMenu() {
        val s = GameProfiles.of("gta6").screen
        assertEquals(true, s.isHud("WASTED"))
        assertEquals(true, s.isHud("Mission passed"))
        assertEquals(false, s.isHud("We wasted the whole day."))
        assertEquals(true, s.isMenu(listOf("Map", "Brief", "Progress")))
        assertEquals(false, s.isMenu(listOf("JASON: Check the map.", "Map")))
        // menu se nečte vůbec, hláška se vynechá a titulek zůstane
        fun line(t: String, top: Int) = OcrLine(t, 300, top, 900, top + 30)
        val menu = TextLogic.assemble(listOf(OcrBlock(listOf(line("Map", 10), line("Brief", 60), line("Settings", 110)))), 10, screen = s)
        assertEquals("", menu.text)
        val hud = TextLogic.assemble(listOf(OcrBlock(listOf(line("MISSION PASSED", 10))), OcrBlock(listOf(line("LUCIA: Let's go home.", 400)))), 10, screen = s)
        assertEquals(false, hud.text.contains("MISSION"))
        // univerzální profil zná nápisy všech her
        assertEquals(true, GameProfiles.UNIVERSAL.screen.isHud("WASTED"))
    }

    @Test fun gameTermsAndNames() {
        val p = GameProfiles.of("gta6")
        assertEquals("hledanost", p.termsFor(Lang.CS)["wanted level"])
        assertEquals(emptyMap<String, String>(), p.termsFor(Lang.DE))
        assertEquals(Gender.F, p.cast.genderOf("KAT"))
        assertEquals(Gender.M, p.cast.genderOf("Dre"))
        // běžná slova nejsou v univerzálním profilu
        assertEquals(false, "Cheetah" in GameProfiles.UNIVERSAL.names)
        assertEquals(true, "Port Gellhorn" in GameProfiles.UNIVERSAL.names)
    }

    @Test fun newProfiles() {
        assertEquals("Grand Theft Auto V", GameProfiles.of("gta").title)
        assertEquals(Gender.F, GameProfiles.of("alanwake2").cast.genderOf("SAGA"))
        assertEquals(Gender.F, GameProfiles.of("re9").cast.genderOf("Grace"))
        assertEquals(Gender.M, GameProfiles.of("rdr2").cast.genderOf("ARTHUR"))
        assertEquals(Gender.F, GameProfiles.of("bond").cast.genderOf("M"))   // M jako mluvčí ano…
        // …ale samotné písmeno v textu se za postavu nepovažuje
        assertEquals("M šel domů.", CzechGender.fix(GameProfiles.of("bond").cast, "M went home.", "M šel domů.", null))
        // univerzální profil: Arthur zůstává chráněný, běžná slova z nových her ne
        assertEquals(true, "Arthur" in GameProfiles.UNIVERSAL.names)
        assertEquals(false, "Grace" in GameProfiles.UNIVERSAL.names)
        assertEquals(true, "Bright Falls" in GameProfiles.UNIVERSAL.names)
        // odhad hry pozná Alan Wake 2
        val g = GameGuess()
        g.onText("SAGA: The Oh Deer Diner in Bright Falls.")
        g.onText("We should go back to Cauldron Lake.")
        assertEquals("alanwake2", g.guess()?.id)
        assertEquals(Gender.F, GameProfiles.of("diablo4").cast.genderOf("LILITH"))
        assertEquals(Gender.F, GameProfiles.of("gta6").cast.genderOf("ANDY"))
        // slang „sick“ = skvělé, ne nemocný
        assertEquals(true, OfflinePrep.standardize("You're looking sick, Lu.").contains("looking great"))
        assertEquals(true, OfflinePrep.standardize("That's sick!").contains("awesome"))
        assertEquals(true, OfflinePrep.standardize("I'm sick of this.").contains("sick of"))
        assertEquals("pevnost", GameProfiles.of("diablo4").termsFor(Lang.CS)["stronghold"])
    }
}
