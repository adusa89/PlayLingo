package cz.playlingo.core

/** Texty aplikace v češtině a slovenštině. */
object I18n {

    private val S: Map<String, Pair<String, String>> = mapOf(
        // ---- menu ----
        "m_home" to ("Domů" to "Domov"),
        "m_zones" to ("Oblasti titulků" to "Oblasti titulkov"),
        "m_look" to ("Titulky" to "Titulky"),
        "m_translate" to ("Překlad" to "Preklad"),
        "m_settings" to ("Nastavení" to "Nastavenia"),
        "m_history" to ("Historie" to "História"),
        "wz_title" to ("Průvodce nastavením" to "Sprievodca nastavením"),
        "wz_s1" to ("Zobrazení titulků" to "Zobrazenie titulkov"),
        "wz_s2" to ("Offline překlad" to "Offline preklad"),
        "wz_s3" to ("Připojení PlayStationu" to "Pripojenie PlayStationu"),
        "wz_s4" to ("Hotovo" to "Hotovo"),
        "wz_perm" to ("Aby aplikace mohla ukázat titulky přes hru, je potřeba ji zapnout v nastavení televize: Usnadnění → PlayLingo → Zapnout. Pak se tlačítkem Zpět vrátit sem." to "Aby aplikácia mohla ukázať titulky cez hru, treba ju zapnúť v nastaveniach televízora: Zjednodušenie ovládania → PlayLingo → Zapnúť. Potom sa tlačidlom Späť vrátiť sem."),
        "wz_perm_open" to ("Otevřít nastavení TV" to "Otvoriť nastavenia TV"),
        "wz_model" to ("Překlad běží přímo v televizi, zdarma a bez internetu. Jen teď jednou stáhnu jazykový model (asi 30 MB)." to "Preklad beží priamo v televízore, zadarmo a bez internetu. Len teraz raz stiahnem jazykový model (asi 30 MB)."),
        "wz_done" to ("Všechno je připravené. Stačí spustit překlad, ve hře zapnout titulky a hrát." to "Všetko je pripravené. Stačí spustiť preklad, v hre zapnúť titulky a hrať."),
        "wz_done_auto" to ("Titulky si aplikace na obrazovce najde sama – nic dalšího se nastavovat nemusí." to "Titulky si aplikácia na obrazovke nájde sama – nič ďalšie netreba nastavovať."),
        "wz_next" to ("Pokračovat" to "Pokračovať"),
        "wz_back" to ("Zpět" to "Späť"),
        "wz_skip" to ("Přeskočit průvodce" to "Preskočiť sprievodcu"),
        "wz_skip_step" to ("Pokračovat bez toho" to "Pokračovať bez toho"),
        "wz_finish" to ("Dokončit" to "Dokončiť"),
        "wz_again" to ("Spustit průvodce nastavením znovu" to "Spustiť sprievodcu nastavením znova"),
        "z_auto" to ("Najít titulky automaticky" to "Nájsť titulky automaticky"),
        "z_auto_desc" to ("Aplikace sama pozná, kde na obrazovce titulky jsou, a zapamatuje si to. Ruční nastavení oblasti automatiku vypne." to "Aplikácia sama spozná, kde na obrazovke titulky sú, a zapamätá si to. Ručné nastavenie oblasti automatiku vypne."),
        "m_license" to ("Licence" to "Licencia"),
        "lic_trial" to ("Zkušební doba: zbývá %d dní" to "Skúšobná doba: zostáva %d dní"),
        "lic_trial_sub" to ("Aplikace je 3 dny zdarma. Potom bude potřeba předplatné nebo odemykací kód." to "Aplikácia je 3 dni zadarmo. Potom bude potrebné predplatné alebo odomykací kód."),
        "lic_unlocked" to ("Aplikace je odemčená" to "Aplikácia je odomknutá"),
        "lic_unlocked_sub" to ("Plná verze bez omezení. Díky!" to "Plná verzia bez obmedzení. Vďaka!"),
        "lic_expired" to ("Zkušební doba skončila" to "Skúšobná doba sa skončila"),
        "lic_expired_sub" to ("Pro další používání je potřeba předplatné nebo odemykací kód." to "Na ďalšie používanie je potrebné predplatné alebo odomykací kód."),
        "lic_open" to ("Odemknout aplikaci" to "Odomknúť aplikáciu"),
        "lic_pay" to ("Předplatné" to "Predplatné"),
        "lic_pay_soon" to ("Předplatné 49 Kč měsíčně – platba bude brzy k dispozici." to "Predplatné 49 Kč mesačne – platba bude čoskoro k dispozícii."),
        "lic_code" to ("Odemykací kód" to "Odomykací kód"),
        "lic_code_hint" to ("Zadat kód" to "Zadať kód"),
        "lic_enter" to ("Odemknout" to "Odomknúť"),
        "lic_ok" to ("Odemčeno ✔" to "Odomknuté ✔"),
        "lic_bad" to ("Tento kód neplatí." to "Tento kód neplatí."),
        "m_help" to ("Instalace" to "Inštalácia"),

        // ---- domů ----
        "st_running" to ("Překlad běží" to "Preklad beží"),
        "st_paused" to ("Pozastaveno" to "Pozastavené"),
        "st_paused_sub" to ("Překlad čeká. Pokračování tlačítkem níže." to "Preklad čaká. Pokračovanie tlačidlom nižšie."),
        "st_ready" to ("Připraveno" to "Pripravené"),
        "st_setup" to ("Ještě je potřeba nastavit" to "Ešte treba nastaviť"),
        "b_go_game" to ("Zpět do hry (HDMI)" to "Späť do hry (HDMI)"),
        "b_testcard" to ("Zkouška na ukázce (bez hry)" to "Skúška na ukážke (bez hry)"),
        "t_game" to ("Hra" to "Hra"),
        "game_universal" to ("Univerzální (všechny hry)" to "Univerzálne (všetky hry)"),
        "t_game_desc" to ("Jména z vybrané hry se nepřekládají. Univerzální profil zná jména z nejhranějších her; profil konkrétní hry je přesnější." to "Mená z vybranej hry sa neprekladajú. Univerzálny profil pozná mená z najhranejších hier; profil konkrétnej hry je presnejší."),
        "t_dict" to ("Vlastní slovník" to "Vlastný slovník"),
        "t_dict_desc" to ("Když se něco překládá špatně, stačí sem zadat anglický výraz nebo celou větu a správný překlad. Aplikace ho pak použije vždy." to "Keď sa niečo prekladá zle, stačí sem zadať anglický výraz alebo celú vetu a správny preklad. Aplikácia ho potom použije vždy."),
        "t_dict_en" to ("Anglicky (výraz nebo věta)" to "Anglicky (výraz alebo veta)"),
        "t_dict_tr" to ("Správný překlad" to "Správny preklad"),
        "t_dict_add" to ("Přidat do slovníku" to "Pridať do slovníka"),
        "t_dict_del" to ("Kliknutím na záznam se smaže." to "Kliknutím na záznam sa zmaže."),
        "upd_new" to ("Je k dispozici nová verze" to "K dispozícii je nová verzia"),
        "upd_btn" to ("Aktualizovat" to "Aktualizovať"),
        "upd_down" to ("Stahuji novou verzi" to "Sťahujem novú verziu"),
        "upd_inst" to ("Staženo. Instalaci je potřeba potvrdit v okně, které televize ukázala." to "Stiahnuté. Inštaláciu treba potvrdiť v okne, ktoré televízor zobrazil."),
        "upd_net" to ("Novou verzi se nepodařilo stáhnout. Je potřeba zkontrolovat připojení televize k internetu a zkusit to znovu." to "Novú verziu sa nepodarilo stiahnuť. Treba skontrolovať pripojenie televízora k internetu a skúsiť to znova."),
        "upd_space" to ("V televizi není dost místa. Je potřeba uvolnit aspoň 100 MB a zkusit to znovu." to "V televízore nie je dosť miesta. Treba uvoľniť aspoň 100 MB a skúsiť to znova."),
        "upd_sig" to ("Tato verze má jiný podpis než ta nainstalovaná, proto ji televize přes starou nepustí. Starou verzi je potřeba jednou odinstalovat a novou nahrát ručně z adresy níže; spárování s PlayStationem bude potřeba zopakovat. Další aktualizace už půjdou odsud." to "Táto verzia má iný podpis ako tá nainštalovaná, preto ju televízor cez starú nepustí. Starú verziu treba raz odinštalovať a novú nahrať ručne z adresy nižšie; spárovanie s PlayStationom bude treba zopakovať. Ďalšie aktualizácie už pôjdu odtiaľto."),
        "upd_cancel" to ("Instalace byla zrušena. Stačí zvolit Aktualizovat a v okně televize ji potvrdit." to "Inštalácia bola zrušená. Stačí zvoliť Aktualizovať a v okne televízora ju potvrdiť."),
        "upd_fail" to ("Aktualizace se nepovedla. Dá se to zkusit znovu, nebo novou verzi nahrát ručně z adresy níže. Když televize instalaci nedovolí: Nastavení → Aplikace → Zabezpečení a omezení → Neznámé zdroje → povolit PlayLingo." to "Aktualizácia sa nepodarila. Dá sa to skúsiť znova, alebo novú verziu nahrať ručne z adresy nižšie. Keď televízor inštaláciu nedovolí: Nastavenia → Aplikácie → Zabezpečenie a obmedzenia → Neznáme zdroje → povoliť PlayLingo."),
        "upd_test" to ("zkušební" to "skúšobná"),
        "upd_done" to ("Aktualizováno na verzi" to "Aktualizované na verziu"),
        "rep_btn" to ("Vytvořit hlášení chyby" to "Vytvoriť hlásenie chyby"),
        "rep_saved" to ("Hlášení uloženo do souboru:" to "Hlásenie uložené do súboru:"),
        "b_start" to ("Spustit překlad" to "Spustiť preklad"),
        "b_pause" to ("Pozastavit" to "Pozastaviť"),
        "b_resume" to ("Pokračovat" to "Pokračovať"),
        "b_stop" to ("Zastavit" to "Zastaviť"),
        "chk_overlay" to ("Zobrazení titulků přes jiné aplikace" to "Zobrazenie titulkov cez iné aplikácie"),
        "chk_model" to ("Jazykový model pro offline překlad" to "Jazykový model pre offline preklad"),
        "fix" to ("Opravit" to "Opraviť"),
        "download" to ("Stáhnout" to "Stiahnuť"),
        "set_up" to ("Nastavit" to "Nastaviť"),
        "ok" to ("V pořádku" to "V poriadku"),

        // ---- průběh spuštění ----
        "prep_model" to ("Připravuji jazykový model (poprvé cca 30 MB)…" to "Pripravujem jazykový model (prvýkrát cca 30 MB)…"),
        "model_fail" to ("Jazykový model se nepodařilo stáhnout. Je potřeba zkontrolovat internet." to "Jazykový model sa nepodarilo stiahnuť. Treba skontrolovať internet."),
        "need_overlay" to ("Nejdřív je potřeba povolit zobrazení titulků (Nastavení → Povolení)." to "Najprv treba povoliť zobrazenie titulkov (Nastavenia → Povolenia)."),

        // ---- oblasti ----
        "z_calibrate" to ("Nastavit oblasti nad snímkem ze hry" to "Nastaviť oblasti nad snímkou z hry"),
        "z_only_subs" to ("Číst jen titulky" to "Čítať len titulky"),
        "z_reset" to ("Obnovit výchozí oblasti" to "Obnoviť predvolené oblasti"),
        "z_reset_done" to ("Oblasti vráceny na výchozí." to "Oblasti vrátené na predvolené."),
        "z_saved" to ("Oblasti uloženy ✔" to "Oblasti uložené ✔"),

        // ---- kalibrace ----
        "cal_read_move" to ("Modrý rámeček – POSUN" to "Modrý rámček – POSUN"),
        "cal_read_move_h" to ("Šipkami posunout modrý rámeček na anglické titulky ve hře." to "Šípkami posunúť modrý rámček na anglické titulky v hre."),
        "cal_read_size" to ("Modrý rámeček – VELIKOST" to "Modrý rámček – VEĽKOSŤ"),
        "cal_read_size_h" to ("←/→ šířka, ↑/↓ výška. Má zakrýt jen titulky, ne celý obraz." to "←/→ šírka, ↑/↓ výška. Má zakryť len titulky, nie celý obraz."),
        "cal_bar_move" to ("Pruh s překladem – POLOHA" to "Pruh s prekladom – POLOHA"),
        "cal_bar_move_h" to ("↑/↓ posune pruh. Nikdy nepřekryje modrý rámeček." to "↑/↓ posunie pruh. Nikdy neprekryje modrý rámček."),
        "cal_bar_width" to ("Pruh s překladem – ŠÍŘKA" to "Pruh s prekladom – ŠÍRKA"),
        "cal_bar_width_h" to ("←/→ změní šířku pruhu." to "←/→ zmení šírku pruhu."),
        "cal_keys" to ("OK = další úprava   •   ZPĚT = uložit a odejít" to "OK = ďalšia úprava   •   SPÄŤ = uložiť a odísť"),
        "cal_no_snapshot" to ("Snímek ze hry zatím není – stačí spustit překlad a hru a pak se sem vrátit." to "Snímka z hry zatiaľ nie je – stačí spustiť preklad a hru a potom sa sem vrátiť."),
        "cal_read_label" to ("Titulky hry (čtení)" to "Titulky hry (čítanie)"),
        "cal_bar_label" to ("Tady bude překlad" to "Tu bude preklad"),
        "cal_cover_note" to ("Pruh se položí přímo přes anglické titulky." to "Pruh sa položí priamo cez anglické titulky."),

        // ---- vzhled ----
        "l_size_auto" to ("Písmo stejně velké jako originál" to "Písmo rovnako veľké ako originál"),
        "l_size" to ("Velikost písma" to "Veľkosť písma"),
        "l_opacity" to ("Neprůhlednost pruhu" to "Nepriehľadnosť pruhu"),
        "l_original" to ("Zobrazit i anglický originál" to "Zobraziť aj anglický originál"),
        "l_orig_size" to ("Velikost originálu" to "Veľkosť originálu"),
        "l_speaker" to ("Jména mluvčích barevně" to "Mená hovoriacich farebne"),
        "l_hold" to ("Nechat překlad déle na obrazovce" to "Nechať preklad dlhšie na obrazovke"),
        "l_pad" to ("Okraj pruhu" to "Okraj pruhu"),
        "l_lang" to ("Jazyk překladu" to "Jazyk prekladu"),
        "l_ui_lang" to ("Jazyk aplikace" to "Jazyk aplikácie"),
        "l_pos" to ("Poloha titulků" to "Poloha titulkov"),
        "pos_over" to ("Přes anglické titulky" to "Cez anglické titulky"),
        "pos_above_text" to ("Těsně nad anglickým titulkem" to "Tesne nad anglickým titulkom"),
        "pos_above" to ("Nad oblastí čtení (pruh stojí na místě)" to "Nad oblasťou čítania (pruh stojí na mieste)"),
        "pos_top" to ("Nahoře obrazovky" to "Hore na obrazovke"),
        "pos_custom" to ("Vlastní (nastavená ručně)" to "Vlastná (nastavená ručne)"),
        "l_width" to ("Šířka pruhu" to "Šírka pruhu"),
        "l_color" to ("Barva textu" to "Farba textu"),
        "color_0" to ("Bílá" to "Biela"), "color_1" to ("Žlutá" to "Žltá"), "color_2" to ("Zelená" to "Zelená"), "color_3" to ("Modrá" to "Modrá"),
        "l_bold" to ("Tučné písmo" to "Tučné písmo"),
        "l_fine" to ("Jemné doladění polohy nad snímkem ze hry" to "Jemné doladenie polohy nad snímkou z hry"),
        "l_reset" to ("Obnovit výchozí vzhled titulků" to "Obnoviť predvolený vzhľad titulkov"),
        "demo_line" to ("Potřebujeme peníze, Jonasi. Hodně peněz. A rychle." to "Potrebujeme peniaze, Jonas. Veľa peňazí. A rýchlo."),

        // ---- překlad ----
        "t_mode" to ("Režim překladu" to "Režim prekladu"),
        "t_offline_desc" to ("Překládá se přímo v televizi, nic se neplatí a nic se neodesílá. Jména postav a míst zůstávají v originále a westernový slang se před překladem převede do spisovné angličtiny." to
            "Prekladá sa priamo v televízore, nič sa neplatí a nič sa neodosiela. Mená postáv a miest zostávajú v origináli a westernový slang sa pred prekladom prevedie do spisovnej angličtiny."),
        "t_model_missing" to ("chybí – stáhnout (jen jednou, cca 30 MB)" to "chýba – stiahnuť (len raz, cca 30 MB)"),
        "t_stats" to ("Statistika" to "Štatistika"),
        "t_lines" to ("Přeložené titulky" to "Preložené titulky"),
        "t_hits" to ("Z paměti (zdarma, okamžitě)" to "Z pamäte (zadarmo, okamžite)"),
        "t_reset_stats" to ("Vynulovat statistiku" to "Vynulovať štatistiku"),
        "t_clear_memory" to ("Smazat paměť překladů" to "Vymazať pamäť prekladov"),
        "t_cleared" to ("Hotovo." to "Hotovo."),

        // ---- nastavení ----
        "s_a11y" to ("Zobrazení přes Usnadnění (doporučeno pro Google TV a Chromecast)" to "Zobrazenie cez Zjednodušenie ovládania (odporúčané pre Google TV a Chromecast)"),
        "s_overlay" to ("Zobrazení přes jiné aplikace" to "Zobrazenie cez iné aplikácie"),
        "s_behavior" to ("Chování" to "Správanie"),
        "s_debug" to ("Diagnostický režim" to "Diagnostický režim"),
        "s_test" to ("Zkušební verze" to "Skúšobné verzie"),
        "s_test_desc" to ("Aplikace nabídne i verze, které se teprve zkoušejí. Mohou obsahovat chyby." to "Aplikácia ponúkne aj verzie, ktoré sa ešte len skúšajú. Môžu obsahovať chyby."),
        "on" to ("ZAP" to "ZAP"),
        "off" to ("VYP" to "VYP"),
        "a11y_hint" to ("V nastavení TV najít Usnadnění → PlayLingo a zapnout." to "V nastaveniach TV nájsť Zjednodušenie ovládania → PlayLingo a zapnúť."),
        "overlay_hint" to ("Zapnout „Zobrazení přes jiné aplikace“ a vrátit se zpět." to "Zapnúť „Zobrazenie cez iné aplikácie“ a vrátiť sa späť."),
        "adb_title" to ("Povolení přes počítač" to "Povolenie cez počítač"),
        "adb_text" to ("Tahle televize nemá obrazovku pro toto povolení. Pomůže Usnadnění, nebo povolení přes ADB:" to "Tento televízor nemá obrazovku pre toto povolenie. Pomôže Zjednodušenie ovládania, alebo povolenie cez ADB:"),

        // ---- historie ----
        "h_empty" to ("Zatím tu nic není. Titulky se sem ukládají během hraní." to "Zatiaľ tu nič nie je. Titulky sa sem ukladajú počas hrania."),
        "h_clear" to ("Smazat historii" to "Vymazať históriu"),

        // ---- nápověda ----
        "inst_text" to ("1. Když se objeví okno Play Protect, zvolit „Další podrobnosti“ → „Přesto nainstalovat“.\n2. Když tam ta možnost není: otevřít Google Play → ikona účtu → Play Protect → ozubené kolo → vypnout „Kontrolovat aplikace pomocí Play Protect“, nainstalovat a pak kontrolu zase zapnout.\n3. Když televize hlásí „instalace z neznámých zdrojů“: Nastavení → Aplikace → Zabezpečení a omezení → Neznámé zdroje → povolit aplikaci, ze které se instaluje (Downloader nebo správce souborů).\n4. Když instalace skončí chybou: uvolnit místo v televizi, případně starou verzi odinstalovat a nainstalovat znovu." to "1. Keď sa zobrazí okno Play Protect, zvoliť „Ďalšie podrobnosti“ → „Napriek tomu nainštalovať“.\n2. Keď tam tá možnosť nie je: otvoriť Google Play → ikona účtu → Play Protect → ozubené koleso → vypnúť „Kontrolovať aplikácie pomocou Play Protect“, nainštalovať a potom kontrolu znova zapnúť.\n3. Keď televízor hlási „inštalácia z neznámych zdrojov“: Nastavenia → Aplikácie → Zabezpečenie a obmedzenia → Neznáme zdroje → povoliť aplikáciu, z ktorej sa inštaluje (Downloader alebo správca súborov).\n4. Keď inštalácia skončí chybou: uvoľniť miesto v televízore, prípadne starú verziu odinštalovať a nainštalovať znova."),

        // ---- notifikace ----
        "n_running" to ("PlayLingo běží" to "PlayLingo beží"),
        "n_paused" to ("PlayLingo – pozastaveno" to "PlayLingo – pozastavené"),
        "n_text" to ("Anglické titulky se překládají." to "Anglické titulky sa prekladajú."),
    )

    private val EXTRA: Map<Lang, Map<String, String>> = mapOf(
        Lang.PL to I18N_PL, Lang.DE to I18N_DE, Lang.HU to I18N_HU, Lang.UK to I18N_UK,
        Lang.ES to I18N_ES, Lang.FR to I18N_FR, Lang.IT to I18N_IT, Lang.EN to I18N_EN,
    )

    /**
     * Texty kolem PlayStationu, přihlášení přes telefon, hlášení o stavu (core/I18nDiag.kt)
     * nahrání do televize LG (core/I18nLg.kt), Xbox (core/I18nXbox.kt) a typ televize s průvodcem
     * pro Samsung (core/I18nSamsung.kt) – mají přednost před obecnými se stejným klíčem.
     */
    private val PS: Map<String, Pair<String, String>> = PS5_S + LOGIN_S + DIAG_S + LG_S + XBOX_S + SS_S + DEEPL_S + UI_S + ZAZNAM_S + KAMERA_S + HELP_S + GAME_S + QUICK_S + REMOTE_S + SETUP_S + ATV_S + MULTI_S + SEND_S
    private val PX: Map<Lang, Map<String, String>> =
        Lang.values().associateWith { (PS5_X[it] ?: emptyMap()) + (LOGIN_X[it] ?: emptyMap()) + (DIAG_X[it] ?: emptyMap()) + (LG_X[it] ?: emptyMap()) +
            (XBOX_X[it] ?: emptyMap()) + (SS_X[it] ?: emptyMap()) + (DEEPL_X[it] ?: emptyMap()) + (UI_X[it] ?: emptyMap()) + (ZAZNAM_X[it] ?: emptyMap()) +
            (KAMERA_X[it] ?: emptyMap()) + (HELP_X[it] ?: emptyMap()) + (GAME_X[it] ?: emptyMap()) + (QUICK_X[it] ?: emptyMap()) + (REMOTE_X[it] ?: emptyMap()) + (SETUP_X[it] ?: emptyMap()) + (ATV_X[it] ?: emptyMap()) + (MULTI_X[it] ?: emptyMap()) + (SEND_X[it] ?: emptyMap()) }

    fun t(lang: Lang, key: String): String {
        val p = PS[key] ?: S[key] ?: return key
        return when (lang) {
            Lang.CS -> p.first
            Lang.SK -> p.second
            else -> PX[lang]?.get(key) ?: (if (PS.containsKey(key)) null else EXTRA[lang]?.get(key)) ?: p.first
        }
    }

    /** Pro testy: má jazyk vlastní překlad daného textu? */
    fun hasOwn(lang: Lang, key: String): Boolean =
        if (PS.containsKey(key)) PX[lang]?.containsKey(key) == true else EXTRA[lang]?.containsKey(key) == true
    fun ownKeys(lang: Lang): Set<String> = (EXTRA[lang]?.keys ?: emptySet()) + (PX[lang]?.keys ?: emptySet())

    fun has(key: String): Boolean = PS.containsKey(key) || S.containsKey(key)

    /** Pro testy: všechny klíče. */
    val keys: Set<String> get() = S.keys + PS.keys
}
