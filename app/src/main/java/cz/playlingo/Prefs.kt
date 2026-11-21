package cz.playlingo

import android.content.Context
import android.content.SharedPreferences
import cz.playlingo.core.BuiltinPhrases
import cz.playlingo.core.Console
import cz.playlingo.core.GameProfiles
import cz.playlingo.core.Glossary
import cz.playlingo.core.I18n
import cz.playlingo.core.Lang
import cz.playlingo.core.License
import cz.playlingo.core.PhoneLogin
import cz.playlingo.core.ScreenLayout
import cz.playlingo.core.SourceLang
import cz.playlingo.core.Ps5Host
import cz.playlingo.core.TvType
import cz.playlingo.core.UsageStats

/** Nastavení aplikace. */
object Prefs {
    /** Stejné hodnoty nabízí i televize (core/TvSettings, app.js) – měň všude najednou. */
    val TEXT_SIZES = intArrayOf(12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48)
    const val DEFAULT_TEXT_SIZE = 22
    /** Stejné hodnoty nabízí i televize (core/TvSettings, app.js) – měň všude najednou. */
    val OPACITIES = intArrayOf(0, 20, 40, 60, 80, 90, 100)
    val BAR_WIDTHS = intArrayOf(50, 60, 70, 84, 100)

    /** Barvy textu titulků: bílá, žlutá, světle zelená, světle modrá. */
    val TEXT_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFFA8F0A8.toInt(), 0xFF9FD8FF.toInt())
    fun textColorIndex(c: Context): Int = p(c).getInt("text_color", 0).coerceIn(0, TEXT_COLORS.size - 1)
    fun setTextColorIndex(c: Context, v: Int) = p(c).edit().putInt("text_color", v).apply()

    fun boldText(c: Context) = bool(c, "bold_text", false)
    fun setBoldText(c: Context, v: Boolean) = setBool(c, "bold_text", v)

    /**
     * Nastavení, se kterým aplikace pracuje. V telefonu v režimu „Nastavuji TV“ ([RemoteClient]) je to nastavení
     * té TV (`dalkove_tv`, každá změna odejde do TV) – vlastní nastavení telefonu zůstává nedotčené.
     */
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(if (RemoteClient.on(c)) RemoteClient.TV_FILE else "test_preklad", Context.MODE_PRIVATE)

    /** Vlastní nastavení tohoto zařízení i v režimu „Nastavuji TV“ (účty v telefonu, zkušební verze, licence…). */
    private fun own(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("test_preklad", Context.MODE_PRIVATE)

    private fun bool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    private fun setBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()

    /**
     * Jazyk překladu. Dokud ho uživatel nezvolil, bere se jazyk aplikace (je-li to jazyk, do kterého
     * se překládá), jinak čeština.
     */
    fun lang(c: Context): Lang = p(c).getString("lang", null)?.let { Lang.of(it) }
        ?: Lang.ui(p(c).getString("ui_lang", null))?.takeIf { it.isTarget } ?: Lang.CS
    fun setLang(c: Context, v: Lang) {
        if (v.isTarget) p(c).edit().putString("lang", v.code).apply()
        // stejný jazyk titulků jako jazyk překladu nejde – pak se překládá z angličtiny ([SourceLang.effective])
        OfflineTranslator.source = sourceLang(c)
    }

    /**
     * Z čeho se překládá (jazyk titulků ve hře, průvodce krok Jazyky a Titulky). Nový klíč `src_lang`,
     * bez něj angličtina – dosavadní hráči nic nepoznají.
     */
    fun sourceLang(c: Context): SourceLang = SourceLang.effective(p(c).getString("src_lang", null), lang(c))
    /** Jazyk překladu k zobrazení; s jiným jazykem titulků než angličtinou dvojice „Français → Čeština“. */
    fun langPair(c: Context): String = sourceLang(c).let { if (it.english) lang(c).label else it.label + " → " + lang(c).label }
    fun setSourceLang(c: Context, v: SourceLang) {
        p(c).edit().putString("src_lang", v.code).apply()
        OfflineTranslator.source = sourceLang(c)
    }

    /**
     * Jazyk aplikace (nabídky, nápověda). Dokud ho uživatel nezvolil: kdo má zvolený jazyk překladu,
     * má aplikaci v něm (tak to bylo do verze 0.63); nová instalace se řídí jazykem zařízení
     * a pro jazyk, který aplikace neumí, je anglicky.
     */
    fun uiLang(c: Context): Lang = Lang.ui(p(c).getString("ui_lang", null))
        ?: p(c).getString("lang", null)?.let { Lang.of(it) }
        ?: Lang.ui(java.util.Locale.getDefault().language) ?: Lang.EN
    fun setUiLang(c: Context, v: Lang) = p(c).edit().putString("ui_lang", v.code).apply()
    fun uiLangChosen(c: Context) = p(c).contains("ui_lang") || p(c).contains("lang")
    /** Vybral si už uživatel jazyk (nebo se jen bere výchozí)? */
    fun langChosen(c: Context) = p(c).contains("lang")

    fun textSize(c: Context): Int = p(c).getInt("text_size", DEFAULT_TEXT_SIZE)
    fun setTextSize(c: Context, v: Int) = p(c).edit().putInt("text_size", v).apply()
    /** Písmo překladu stejně velké jako anglický originál (výchozí). Vypnuto = pevná velikost. */
    fun matchSize(c: Context) = bool(c, "match_size", true)
    fun setMatchSize(c: Context, v: Boolean) = setBool(c, "match_size", v)

    fun opacity(c: Context): Int = p(c).getInt("opacity", 100)
    fun setOpacity(c: Context, v: Int) = p(c).edit().putInt("opacity", v).apply()

    fun debug(c: Context) = bool(c, "debug", false)
    fun setDebug(c: Context, v: Boolean) = setBool(c, "debug", v)
    /** Brát i zkušební verze (sestavené z rozpracované větve, ještě neschválené pro všechny). */
    fun testChannel(c: Context) = own(c).getBoolean("test_channel", false)
    fun setTestChannel(c: Context, v: Boolean) = own(c).edit().putBoolean("test_channel", v).apply()
    /**
     * Typ televize (průvodce, Nastavení, souhrnný řádek na Domů): Android TV ukazuje pruh samo, LG a Samsung dostávají
     * hotové věty po síti. Kdo ho nezvolil, řídí se starou volbou „Posílat titulky do televize LG“
     * (`tv_send`) – zapnutá = LG.
     */
    fun tvType(c: Context): TvType = TvType.from(p(c).getString("tv_type", null), bool(c, "tv_send", false))
    fun setTvType(c: Context, v: TvType) = p(c).edit().putString("tv_type", v.key).putBoolean("tv_send", v.sends).apply()
    /** Posílat přeložené titulky do televize LG nebo Samsung, kde PlayLingo běží jen jako obrazovka (výchozí: ne). */
    fun tvSend(c: Context) = tvType(c).sends
    /** Z televize s danou platformou („webos“, „tizen“) se už ohlásila aplikace PlayLingo: „model · verze“, jinak null. */
    fun tvSeen(c: Context, platform: String): String? = p(c).getString("tv_seen_$platform", null)
    fun setTvSeen(c: Context, platform: String, v: String) = p(c).edit().putString("tv_seen_$platform", v).apply()
    /** Číslo verze, která běžela naposledy (0 = první spuštění) – aby šlo po aktualizaci říct, že proběhla. */
    fun seenVersion(c: Context): Int = own(c).getInt("seen_version", 0)
    fun setSeenVersion(c: Context, v: Int) = own(c).edit().putInt("seen_version", v).apply()

    fun onlySubtitles(c: Context) = bool(c, "only_subs", true)
    fun setOnlySubtitles(c: Context, v: Boolean) = setBool(c, "only_subs", v)

    fun showOriginal(c: Context) = bool(c, "show_original", false)
    fun setShowOriginal(c: Context, v: Boolean) = setBool(c, "show_original", v)
    /**
     * Velikost písma anglického originálu jako u překladu (12–48; 0 = automaticky, 62 % písma překladu jako dřív).
     * Od 0.97.50 na přání majitele velikost písma místo procent (0.97.44–0.97.49 klíč `orig_size` v %, už se nečte).
     */
    fun origSp(c: Context): Int = p(c).getInt("orig_sp", 0).let { v -> if (v <= 0) 0 else TEXT_SIZES.minByOrNull { kotlin.math.abs(it - v) } ?: 0 }
    fun setOrigSp(c: Context, v: Int) = p(c).edit().putInt("orig_sp", v).apply()
    /**
     * Texty hry navíc k titulkům (Titulky → Hra, od 0.97.59): každý má vlastní přepínač, žádná obecná volba ho nepřebije.
     * Kdo měl do 0.97.57 „Rozsah čtení: jen titulky“ (`game_texts` = false), má je vypnuté, dokud některý sám nepřepne
     * (značky `game_prompts` uložená / `obj_volba` / `game_menu_volba`) – viz [cz.playlingo.core.GameScope].
     * Klíče jsou společné pro všechny hry; přepínače se ukazují jen u her, jejichž profil daný text zná.
     */
    private fun legacyTexts(c: Context): Boolean? = if (p(c).contains("game_texts")) bool(c, "game_texts", true) else null
    /** Pokyny pro hráče pod titulky (GTA: „Take your shot“) – menší šedý řádek; výchozí zapnuto (jako dřív). */
    fun gamePrompts(c: Context) = cz.playlingo.core.GameScope.resolve(legacyTexts(c), p(c).contains("game_prompts"), bool(c, "game_prompts", true))
    fun setGamePrompts(c: Context, v: Boolean) = setBool(c, "game_prompts", v)
    /** Překlad menu hry (GTA VI: Mapa, Hra, Nastavení…), od 0.97.57 volba – výchozí vypnuto (přání majitele). */
    fun gameMenu(c: Context) = cz.playlingo.core.GameScope.resolve(legacyTexts(c), bool(c, "game_menu_volba", false), bool(c, "game_menu", false))
    fun setGameMenu(c: Context, v: Boolean) { setBool(c, "game_menu", v); setBool(c, "game_menu_volba", true) }
    /** Úkol pod titulky zapnutý (`obj_on` je zvlášť pro každou TV, značka `obj_volba` společná). */
    fun objectiveOn(c: Context) = cz.playlingo.core.GameScope.resolve(legacyTexts(c), bool(c, "obj_volba", false), bool(c, "obj_on", true))
    fun setObjectiveOn(c: Context, v: Boolean) { setBool(c, "obj_on", v); setBool(c, "obj_volba", true) }
    /** Úkol pod titulky (GTA): písmo (0 = podle hry), poloha (-1 = na místě úkolu ve hře, jinak 0–100 % výšky). */
    fun objSize(c: Context): Int = p(c).getInt("obj_size", 0)
    fun setObjSize(c: Context, v: Int) = p(c).edit().putInt("obj_size", v).apply()
    /** -1 na místě ve hře, -2 těsně nad úkolem, -3 nad pruhem s titulky, -4 nahoře (od 0.97.50), jinak střed 0–100 %. */
    fun objPos(c: Context): Int = p(c).getInt("obj_pos", -1).let { if (it < 0) it.coerceIn(-4, -1) else it.coerceIn(0, 100) }
    /** Pozadí úkolu 0–100 % (-1 = jako pruh s titulky, výchozí) a barevná slova úkolu (pokus, výchozí vypnuto), od 0.97.50. */
    fun objBg(c: Context): Int = p(c).getInt("obj_bg", -1).let { v -> if (v < 0) -1 else OPACITIES.minByOrNull { kotlin.math.abs(it - v) } ?: -1 }
    fun setObjBg(c: Context, v: Int) = p(c).edit().putInt("obj_bg", v).apply()
    fun objColors(c: Context) = bool(c, "obj_colors", false)
    fun setObjColors(c: Context, v: Boolean) = setBool(c, "obj_colors", v)
    fun setObjPos(c: Context, v: Int) = p(c).edit().putInt("obj_pos", v).apply()
    /** Vlastní výška pruhu / úkolu: střed v % výšky obrazovky. */
    val HEIGHTS = (0..100 step 5).toList()

    fun coloredSpeaker(c: Context) = bool(c, "colored_speaker", true)
    fun setColoredSpeaker(c: Context, v: Boolean) = setBool(c, "colored_speaker", v)

    /** Jak dlouho nechat překlad na obrazovce po zmizení anglického titulku (ms). */
    val HOLD_TIMES = intArrayOf(0, 1000, 2000)
    fun holdMs(c: Context): Int = p(c).getInt("hold_ms", 1000)
    fun setHoldMs(c: Context, v: Int) = p(c).edit().putInt("hold_ms", v).apply()
    /** Pokus „Rychlejší čtení“: čte se jen pás s textem (core/TextBand, od 0.97.47), výchozí vypnuto. */
    fun fastRead(c: Context) = bool(c, "fast_read", false)
    fun setFastRead(c: Context, v: Boolean) = setBool(c, "fast_read", v)
    /** Nejkratší doba titulku v pruhu (fronta rychlých replik, od 0.97.46); 0 = vypnuto. */
    val MIN_SHOW_TIMES = intArrayOf(0, 500, 1000, 1500, 2000, 3000)
    fun minShowMs(c: Context): Int = p(c).getInt("min_show_ms", 0).let { v -> MIN_SHOW_TIMES.minByOrNull { kotlin.math.abs(it - v) } ?: 0 }
    fun setMinShowMs(c: Context, v: Int) = p(c).edit().putInt("min_show_ms", v).apply()

    fun layout(c: Context): ScreenLayout = ScreenLayout.parse(p(c).getString("layout", null))
    fun setLayout(c: Context, v: ScreenLayout) = p(c).edit().putString("layout", v.serialize()).apply()

    fun stats(c: Context): UsageStats = UsageStats.parse(p(c).getString("stats", null))
    fun setStats(c: Context, v: UsageStats) = p(c).edit().putString("stats", v.serialize()).apply()

    /** Hledat titulky na obrazovce automaticky (bez ručního nastavování oblasti). */
    fun autoZone(c: Context) = bool(c, "auto_zone", true)
    fun setAutoZone(c: Context, v: Boolean) = setBool(c, "auto_zone", v)

    /** Průvodce prvním spuštěním už proběhl. */
    fun wizardDone(c: Context) = bool(c, "wizard_done", false)
    fun setWizardDone(c: Context, v: Boolean) = setBool(c, "wizard_done", v)

    // ---- konzole ----
    /** Na které konzoli se hraje (výchozí PlayStation; dosavadní uživatelé to nemají uložené). */
    fun console(c: Context): Console = Console.of(p(c).getString("console", null))
    fun setConsole(c: Context, v: Console) = p(c).edit().putString("console", v.code).apply()

    /** Je vybraná konzole připravená ke spojení (PS5 spárovaný / Xbox přihlášený a vybraný)? */
    fun consoleReady(c: Context): Boolean = when (console(c)) {
        Console.PLAYSTATION -> ps5Host(c) != null
        Console.XBOX -> XboxAccount.ready(c)
    }

    // ---- PlayStation ----
    /** Spárovaná konzole (null = ještě nespárováno). */
    fun ps5Host(c: Context): Ps5Host? = Ps5Host.parse(p(c).getString("ps5_host", null))
    fun setPs5Host(c: Context, v: Ps5Host?) = p(c).edit().putString("ps5_host", v?.serialize()).apply()

    /** Naposledy zadané údaje pro párování (ať se nemusí psát znovu). */
    fun ps5Ip(c: Context): String = p(c).getString("ps5_ip", "") ?: ""
    fun setPs5Ip(c: Context, v: String) = p(c).edit().putString("ps5_ip", v).apply()
    fun psnId(c: Context): String = p(c).getString("psn_id", "") ?: ""
    fun setPsnId(c: Context, v: String) = p(c).edit().putString("psn_id", v).apply()
    /** Jméno účtu PlayStation (Online ID) k uloženému číslu účtu; prázdné = neznámé (účty uložené před 0.97.28). */
    fun psnName(c: Context): String = p(c).getString("psn_name", "") ?: ""
    /** Uloží ověřený účet (číslo + jméno) najednou; prázdné hodnoty = účet zapomenout. */
    fun setPsnAccount(c: Context, id: String, name: String) =
        p(c).edit().putString("psn_id", id).putString("psn_name", name).apply()

    /** S obrazem 1080p spojení na tomto zařízení nedrželo → automatika bere 720p. */
    fun highResFailed(c: Context) = bool(c, "high_res_failed", false)
    fun setHighResFailed(c: Context, v: Boolean) = setBool(c, "high_res_failed", v)

    /** Přesnost čtení titulků: "auto" (podle zařízení), "std" (720p), "high" (1080p). */
    fun readQuality(c: Context): String = p(c).getString("read_quality", "auto") ?: "auto"
    fun setReadQuality(c: Context, v: String) = p(c).edit().putString("read_quality", v).apply()

    /** Čtyřmístný přihlašovací kód účtu na konzoli (jen pokud ho uživatel na PS5 má zapnutý). */
    fun ps5LoginPin(c: Context): String = p(c).getString("ps5_login_pin", "") ?: ""
    fun setPs5LoginPin(c: Context, v: String) = p(c).edit().putString("ps5_login_pin", v).apply()

    /** Je to televize s vestavěným Androidem (ne samostatný box nebo Chromecast)? */
    fun isTv(c: Context): Boolean = try {
        c.packageManager.hasSystemFeature("android.software.live_tv")
    } catch (_: Exception) { false }

    // ---- PlayLingo v telefonu (pomocník pro přihlášení účtem PlayStation) ----
    /** Účty, které si telefon pamatuje: jen číslo účtu a jméno, žádné heslo. */
    fun phoneAccounts(c: Context): List<PhoneLogin.Account> = PhoneLogin.parseAccounts(own(c).getString("phone_accounts", null))
    fun setPhoneAccounts(c: Context, v: List<PhoneLogin.Account>) =
        own(c).edit().putString("phone_accounts", PhoneLogin.serializeAccounts(v)).apply()

    /** Uživatel řekl, že tohle zařízení je televize nebo box, i když se tváří jako telefon. */
    fun forceTv(c: Context) = own(c).getBoolean("force_tv", false)
    fun setForceTv(c: Context, v: Boolean) = own(c).edit().putBoolean("force_tv", v).apply()

    /**
     * Na čem PlayLingo běží (průvodce, Nastavení → Zařízení): true = telefon nebo tablet, který jen čte
     * a posílá titulky do televize LG / Samsung; false = Android TV nebo box. Kdo nevolil, odhadne se
     * podle zařízení. Sama volba nic nepřepíná – pomocník v telefonu se dál řídí [forceTv].
     */
    fun readerDevice(c: Context): Boolean =
        if (p(c).contains("reader_device")) bool(c, "reader_device", false) else NetInfo.isHandheld(c)
    fun setReaderDevice(c: Context, v: Boolean) = setBool(c, "reader_device", v)

    // ---- hra a slovník ----
    // `game_texts` (Rozsah čtení do 0.97.57) se už nenastavuje, čte se jen pro přechod – viz [gamePrompts].

    /** Okraj pruhu nad a pod textem 0–3 (násobek velikosti písma, viz [BAR_PADS]); výchozí 1. */
    val BAR_PADS = floatArrayOf(0.04f, 0.16f, 0.32f, 0.5f)
    /** „Zakrýt celou oblast titulků“ v poloze Přes titulky (od 0.97.59, výchozí vypnuto – nové chování). */
    fun coverAll(c: Context) = bool(c, "cover_all", false)
    fun setCoverAll(c: Context, v: Boolean) = setBool(c, "cover_all", v)
    fun barPad(c: Context): Int = p(c).getInt("bar_pad", 1).coerceIn(0, 3)
    fun setBarPad(c: Context, v: Int) = p(c).edit().putInt("bar_pad", v.coerceIn(0, 3)).apply()

    /** Budit PlayStation z klidového režimu při Spustit (zapne ho na spárovaném druhém účtu); výchozí ne (od 0.97.18). */
    /** Od 0.97.41 nový klíč (dřív `ps5_wake`): po aktualizaci je probouzení u všech vypnuté, zapnout jde znovu v Nastavení. */
    fun ps5Wake(c: Context): Boolean = bool(c, "ps5_wake2", false)
    fun setPs5Wake(c: Context, v: Boolean) = setBool(c, "ps5_wake2", v)
    /** Stav (konzole, překlad, hra) vlevo nahoře přes hru na Android TV – výchozí vypnuto (od 0.97.40), stav je na Domů. */
    /**
     * Stav přes hru (Android TV): 0 vypnuto, 1 dynamicky (výchozí od 0.97.51, přání majitele) (ukáže se při změně a po chvíli zmizí; zůstane jen
     * při potížích se spojením nebo chybějícím jazykovém balíčku), 2 vždy (jako do 0.97.49: oranžový zůstává).
     * Kdo měl do 0.97.49 zapnutý přepínač `stav_pres_hru`, má „vždy“ (uložené nastavení se nemění).
     */
    fun statusMode(c: Context): Int = p(c).getInt("stav_rezim", -1).let { if (it in 0..2) it else if (bool(c, "stav_pres_hru", false)) 2 else 1 }
    fun setStatusMode(c: Context, v: Int) = p(c).edit().putInt("stav_rezim", v.coerceIn(0, 2)).apply()
    fun statusOverlay(c: Context): Boolean = statusMode(c) != 0

    /** Tlačítko ovladače pro rychlou nabídku vzhledu na Android TV ([QuickLook]); 0 = nenastaveno. */
    fun quickKey(c: Context): Int = p(c).getInt("quick_key", 0)
    fun setQuickKey(c: Context, v: Int) = p(c).edit().putInt("quick_key", v).apply()

    fun gameId(c: Context): String = p(c).getString("game", "all") ?: "all"
    fun setGameId(c: Context, v: String) = p(c).edit().putString("game", v).remove("game_guess").apply()
    /** Hra odhadnutá podle titulků (nabídka „Přepnout“ v Nastavení); null = nic. */
    fun gameGuess(c: Context): String? = p(c).getString("game_guess", null)?.takeIf { it != gameId(c) && it != gameGuessNo(c) }
    /** Hráč nabídku odmítl („Nechat“) – stejná hra se už nenabízí. */
    fun gameGuessNo(c: Context): String? = p(c).getString("game_guess_no", null)
    fun dismissGameGuess(c: Context) = p(c).edit().putString("game_guess_no", p(c).getString("game_guess", null)).remove("game_guess").apply()
    fun setGameGuess(c: Context, v: String?) = p(c).edit().apply { if (v == null) remove("game_guess") else putString("game_guess", v) }.apply()

    /** Seznam her ze stránek: sám se ptá na novou verzi (nejvýš jednou za 6 h). Výchozí vypnuto – stáhne se jen po OK na řádku. */
    fun gamesAuto(c: Context) = bool(c, "hry_auto", false)
    fun setGamesAuto(c: Context, v: Boolean) = setBool(c, "hry_auto", v)
    /** Kdy se naposledy zjišťovala verze seznamu her (ms, System.currentTimeMillis). */
    fun gamesChecked(c: Context): Long = p(c).getLong("hry_kontrola", 0L)
    fun setGamesChecked(c: Context, v: Long) = p(c).edit().putLong("hry_kontrola", v).apply()

    /** Vlastní slovník uživatele pro daný jazyk (anglicky → překlad). */
    fun userDict(c: Context, lang: Lang): LinkedHashMap<String, String> =
        Glossary.parseUser(p(c).getString("dict_${lang.code}", null))
    fun setUserDict(c: Context, lang: Lang, m: Map<String, String>) =
        p(c).edit().putString("dict_${lang.code}", Glossary.serializeUser(m)).apply()

    /** Slovník pro překlad: jména podle hry + předpřipravené hlášky + vlastní slovník. */
    fun glossary(c: Context): Glossary {
        val lang = lang(c)
        val user = userDict(c, lang)
        val sentences = LinkedHashMap(BuiltinPhrases.forLang(lang))
        val game = GameProfiles.of(gameId(c))
        val terms = LinkedHashMap(game.termsFor(lang))     // pojmy hry; vlastní slovník je přepíše
        for ((k, v) in user) if (Glossary.isSentence(k)) sentences[k] = v else terms[k] = v
        return Glossary(game.names, terms, sentences, game.cast, user.keys.filterNot { Glossary.isSentence(it) })
    }

    /** Otisk nastavení slovníku – když se změní, paměť překladů se smaže. */
    fun glossarySignature(c: Context): String =
        gameId(c) + "|" + lang(c).code + "|" + userDict(c, lang(c)).hashCode() + "|" +
            GameProfiles.of(gameId(c)).let { it.names.hashCode() + 31 * it.termsFor(lang(c)).hashCode() } +
            // stažený seznam her (postavy a rod se mohly změnit); s vestavěným podpis beze změny
            GameProfiles.catalog.let { if (it.downloaded) "|hry" + it.version else "" } +
            // jiný jazyk titulků → paměť překladů neplatí (u angličtiny podpis beze změny, paměť hráčům zůstane)
            sourceLang(c).let { if (it.english) "" else "|" + it.code }
    fun savedGlossarySignature(c: Context): String = p(c).getString("gloss_sig", "") ?: ""
    fun setSavedGlossarySignature(c: Context, v: String) = p(c).edit().putString("gloss_sig", v).apply()

    // ---- nastavení Android TV z telefonu (core/RemoteSetup, RemoteHost) ----
    /** Přenášené volby tohoto zařízení (pro telefon, který TV nastavuje). */
    fun remoteValues(c: Context): Map<String, Any?> = cz.playlingo.core.RemoteSetup.filter(p(c).all)

    /** Uloží volby poslané z telefonu (jen přenášené; null = volbu smazat). */
    fun applyRemote(c: Context, values: Map<String, Any?>) {
        val e = p(c).edit()
        for ((k, v) in values) {
            if (!cz.playlingo.core.RemoteSetup.transfers(k)) continue
            @Suppress("UNCHECKED_CAST")
            when (v) {
                null -> e.remove(k)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
                is Set<*> -> e.putStringSet(k, v as Set<String>)
            }
        }
        e.commit()
        OfflineTranslator.source = sourceLang(c)
    }

    /** Stálý klíč stránky nastavení v TV (ve čtverečku; telefon se s ním připojuje i později). */
    fun remoteKey(c: Context): String {
        val sp = c.applicationContext.getSharedPreferences("nastaveni_z_telefonu", Context.MODE_PRIVATE)
        sp.getString("klic", null)?.takeIf { PhoneLogin.validKey(it) }?.let { return it }
        val k = PhoneLogin.newKey() + PhoneLogin.newKey()
        sp.edit().putString("klic", k).apply()
        return k
    }

    // ---- licence ----
    fun unlocked(c: Context) = own(c).getBoolean("unlocked", false)
    fun setUnlocked(c: Context, v: Boolean) = own(c).edit().putBoolean("unlocked", v).apply()

    /** Stav licence; při prvním spuštění si zapamatuje začátek zkušební doby. */
    fun license(c: Context): License.State {
        val now = System.currentTimeMillis()
        val saved = own(c).getLong("first_run", 0L)
        val first = License.firstRun(saved, now)
        if (first != saved && first != 0L) own(c).edit().putLong("first_run", first).apply()
        return License.state(unlocked(c), first, now)
    }
}

/** Zkratka pro texty v jazyce, který si uživatel vybral. */
fun Context.tx(key: String): String = I18n.t(Prefs.uiLang(this), key).let {
    // značka herního účtu 🟢 je až v Androidu 10 – na starších TV a boxech zelené srdce, ať není prázdný čtvereček
    if (android.os.Build.VERSION.SDK_INT < 29) it.replace(cz.playlingo.core.AccountMarks.GAME, cz.playlingo.core.AccountMarks.GAME_OLD) else it
}
