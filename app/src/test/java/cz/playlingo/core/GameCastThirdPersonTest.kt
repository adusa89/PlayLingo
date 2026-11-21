package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Třetí osoba u postav zmíněných v textu (GTA VI: Andy je žena – přání majitele 21. 11. 2026). */
class GameCastThirdPersonTest {
    private val gta6 = GameProfiles.of("gta6").cast
    private fun t(en: String, cs: String, speaker: String? = null) = CzechGender.fix(gta6, en, cs, speaker)

    @Test fun nameThenVerbOverAdverbs() {
        assertEquals("Andy už šla.", t("Andy already went.", "Andy už šel."))
        assertEquals("Andy byla pryč.", t("Andy was gone.", "Andy byl pryč."))
        assertEquals("Andy je připravená.", t("Andy is ready.", "Andy je připravený."))
        assertEquals("Andy se vrátila.", t("Andy came back.", "Andy se vrátil."))
        assertEquals("Andy se pak zase vrátila domů.", t("Then Andy came back home.", "Andy se pak zase vrátil domů."))
        assertEquals("Andy mi včera zavolala.", t("Andy called me yesterday.", "Andy mi včera zavolal."))
        assertEquals("Andy prý odjela do Port Gellhorn.", t("Andy apparently drove to Port Gellhorn.", "Andy prý odjel do Port Gellhorn."))
        assertEquals("Andy to vzala.", t("Andy took it.", "Andy to vzal."))
        assertEquals("Andy přišla sama.", t("Andy came alone.", "Andy přišel sám."))
        assertEquals("Andy sama přišla.", t("Andy came by herself.", "Andy sám přišel."))
        assertEquals("Andy by to neudělala.", t("Andy wouldn't do that.", "Andy by to neudělal."))
        assertEquals("Andy je viděla.", t("Andy saw them.", "Andy je viděl."))
        assertEquals("Andy už byla zraněná.", t("Andy was already hurt.", "Andy už byl zraněný."))
    }

    @Test fun sameSubjectContinues() {
        assertEquals("Andy přišla a řekla, že je čas.", t("Andy came and said it's time.", "Andy přišel a řekl, že je čas."))
        assertEquals("Andy vzala auto a odjela.", t("Andy took the car and drove off.", "Andy vzal auto a odjel."))
        assertEquals("Andy přišla, ale hned odešla.", t("Andy came, but left right away.", "Andy přišel, ale hned odešel."))
        assertEquals("Andy byla pryč a pak se vrátila.", t("Andy was gone and then came back.", "Andy byl pryč a pak se vrátil."))
        // „a“ s jiným podmětem
        assertEquals("Andy přišla a Jason odešel.", t("Andy came and Jason left.", "Andy přišel a Jason odešel."))
        // vedlejší věta s jiným podmětem
        assertEquals("Andy viděla, jak Jason odešel.", t("Andy saw Jason leave.", "Andy viděl, jak Jason odešel."))
        // nová věta – podmět neznámý (bez zájmena v originálu)
        assertEquals("Andy přišla. Pak odešel.", t("Andy came. Then he left.", "Andy přišel. Pak odešel."))
    }

    @Test fun subordinateClauses() {
        assertEquals("Myslím, že Andy šla domů.", t("I think Andy went home.", "Myslím, že Andy šel domů."))
        assertEquals("Jason řekl, že Andy byla připravená.", t("Jason said Andy was ready.", "Jason řekl, že Andy byl připravený."))
        assertEquals("Nevím, protože Andy nic neřekla.", t("I don't know, because Andy didn't say anything.", "Nevím, protože Andy nic neřekl."))
        assertEquals("Když Andy přišla, bylo pozdě.", t("When Andy came, it was late.", "Když Andy přišel, bylo pozdě."))
        assertEquals("Andy řekla, že Jason přišel.", t("Andy said Jason came.", "Andy řekl, že Jason přišel."))
    }

    @Test fun verbBeforeName() {
        assertEquals("Zavolala Andy.", t("Andy called.", "Zavolal Andy."))
        assertEquals("Pak přišla Andy.", t("Then Andy showed up.", "Pak přišel Andy."))
        assertEquals("Včera tu byla Andy.", t("Andy was here yesterday.", "Včera tu byl Andy."))
        // Andy jako předmět – podmět je někdo jiný
        assertEquals("Viděl jsem Andy.", t("I saw Andy.", "Viděl jsem Andy."))
        assertEquals("Pak zavolal Andy.", t("Then he called Andy.", "Pak zavolal Andy."))
        assertEquals("Jason zavolal Andy.", t("Jason called Andy.", "Jason zavolal Andy."))
        assertEquals("Kdo zavolal Andy?", t("Who called Andy?", "Kdo zavolal Andy?"))
        assertEquals("Zavolal jsi Andy?", t("Did you call Andy?", "Zavolal jsi Andy?"))
    }

    @Test fun pronounFollowUp() {
        assertEquals("Zavolala Andy. Řekla, že přijde.", t("Andy called. She said she's coming.", "Zavolal Andy. Řekl, že přijde."))
        assertEquals("Viděl jsem Andy, byla unavená.", t("I saw Andy, she was tired.", "Viděl jsem Andy, byl unavený."))
        assertEquals("Mluvil jsem s Andy. Řekla, že by přišla.", t("I talked to Andy. She said she would come.", "Mluvil jsem s Andy. Řekl, že by přišel."))
        assertEquals("Andy? Odešla před hodinou.", t("Andy? She left an hour ago.", "Andy? Odešel před hodinou."))
        assertEquals("Andy řekla, že je připravená.", t("Andy said she's ready.", "Andy řekl, že je připravený."))
        assertEquals("Andy řekla, že plán je připravený.", t("Andy said the plan is ready.", "Andy řekl, že plán je připravený."))
        assertEquals("Andy řekla, že je to hotové.", t("Andy said it's done.", "Andy řekl, že je to hotové."))
        assertEquals("Ptal jsem se Andy a řekla ne.", t("I asked Andy and she said no.", "Ptal jsem se Andy a řekl ne.", "JASON"))
    }

    @Test fun pronounFollowUpStaysWhenUnsure() {
        // muž v originálu
        assertEquals("Zavolal Andy. Řekl, že přijde.", t("Andy called him. He said he's coming.", "Zavolal Andy. Řekl, že přijde."))
        // muž i žena (postavy)
        assertEquals("Andy volala Jasonovi. Řekl, že přijde.",
            t("Andy called Jason. She said he's coming.", "Andy volala Jasonovi. Řekl, že přijde."))
        // jiný podmět v překladu
        assertEquals("Andy volala. Policajt řekl, že utekla.", t("Andy called. The cop said she ran.", "Andy volala. Policajt řekl, že utekla."))
        assertEquals("Andy volala. Šéf řekl, že odešla.", t("Andy called. The boss said she left.", "Andy volala. Šéf řekl, že odešla."))
        // „it“ jako podmět (mužský rod věci)
        assertEquals("Andy volala. Řekla, že padl.", t("Andy called. She said it failed.", "Andy volala. Řekla, že padl."))
        // „byl to“ bez „she was“
        assertEquals("Andy volala. Byl to dlouhý den.", t("Andy called. It was a long day for her.", "Andy volala. Byl to dlouhý den."))
        // bez jména postavy v originálu nevíme, o kom je řeč
        assertEquals("Řekl, že přijde.", t("She said she's coming.", "Řekl, že přijde."))
        // první a druhá osoba zůstávají podle mluvčího / oslovení
        assertEquals("Andy volala, viděl jsem ji.", t("Andy called, I saw her.", "Andy volala, viděl jsem ji.", "JASON"))
        assertEquals("Andy volala. Řekl jsi jí to?", t("Andy called. Did you tell her?", "Andy volala. Řekl jsi jí to?"))
    }

    @Test fun maleAndObjectStay() {
        assertEquals("Jason šel domů.", t("Jason went home.", "Jason šel domů."))
        assertEquals("Jason šel domů.", t("Jason went home.", "Jason šla domů."))
        assertEquals("Jason už byl pryč.", t("Jason was gone.", "Jason už byla pryč."))
        assertEquals("Přišel Jason.", t("Jason came.", "Přišla Jason."))
        assertEquals("Viděl jsem Andy včera.", t("I saw Andy yesterday.", "Viděl jsem Andy včera."))
        assertEquals("Dal jsem Andy klíče a odešel jsem.", t("I gave Andy the keys and left.", "Dal jsem Andy klíče a odešel jsem."))
        assertEquals("Andy a Jason přišli.", t("Andy and Jason came.", "Andy a Jason přišli."))
        // přídavné jméno u jiného podstatného jména se nemění
        // (spal za přídavným jménem se raději nechá – nevíme, jestli „celý“ nezačíná podmět)
        assertEquals("Andy celý den spal.", t("Andy slept all day.", "Andy celý den spal."))
        assertEquals("Lucia celý den spal.", t("Lucia slept all day.", "Lucia celý den spal."))
        assertEquals("Andy hraje fotbal.", t("Andy plays football.", "Andy hraje fotbal."))
        assertEquals("Andy, viděl jsem to.", t("Andy, I saw it.", "Andy, viděl jsem to.", "JASON"))
        assertEquals("Jason řekl Andy, že přijde.", t("Jason told Andy he'd come.", "Jason řekl Andy, že přijde."))
    }
}
