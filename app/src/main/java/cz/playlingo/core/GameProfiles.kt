package cz.playlingo.core

/**
 * Profil hry: jména postav a míst, která se nemají překládat, a postavy s rodem
 * (podle něj se opraví tvary v překladu, viz [CzechGender]).
 */
data class GameProfile(
    val id: String, val title: String, val names: List<String>, val characters: List<GameCharacter> = emptyList(),
    /** Herní pojmy s pevným překladem: kód jazyka → (anglicky → překlad). Vlastní slovník má přednost. */
    val terms: Map<String, Map<String, String>> = emptyMap(),
    /** Hlášky na obrazovce, které nejsou replika (MISSION PASSED, WASTED…) – nečtou se. */
    val hud: List<String> = emptyList(),
    /** Nápisy z menu hry – obrazovka s aspoň dvěma z nich je menu a nečte se. */
    val menu: List<String> = emptyList(),
    /** Řádky, které se nečtou podle tvaru (chat v Diablu IV: „[Trade] …“). */
    val skip: List<Regex> = emptyList(),
    /** Hra píše pod repliku pokyny pro hráče (GTA) – poslední krátký řádek se ukáže zvlášť ([TextLogic.markPrompt]). */
    val prompts: Boolean = false,
    /** Barvy jmen jako v titulcích hry (jméno postavy → barva 0xAARRGGBB); platí i pro její přezdívky. */
    val colors: Map<String, Int> = emptyMap(),
    /** Základní menu hry k překladu (menu zleva, lišta Nastavení, popis volby – [GameMenu]); null = menu se nepřekládá. */
    val menuSpec: MenuSpec? = null,
    /**
     * Rada, jak hru nastavit, aby se titulky četly (od 0.97.58): klíč textu – `<hint>` popisek řádku v Titulky → Hra,
     * `<hint>_val` hodnota, `<hint>_desc` delší vysvětlení (dialog po OK a Nápověda → Hraní). null = žádná rada.
     */
    val hint: String? = null,
    /**
     * Jména z [names], která jsou zároveň běžná anglická slova (od seznamu her ke stažení): v profilu hry zůstávají
     * v originále, ale univerzální profil a odhad hry je vynechají ([GameCatalog.ambiguous]).
     */
    val common: List<String> = emptyList(),
) {
    /** Barvy pro [SpeakerColors]: zjednodušené jméno i všechny přezdívky postavy → barva. */
    val colorsByKey: Map<String, Int> by lazy {
        val out = HashMap<String, Int>()
        for ((name, color) in colors) {
            val ch = characters.firstOrNull { it.name == name }
            (ch?.allNames ?: listOf(name)).forEach { out[SpeakerColors.key(it)] = color }
        }
        out
    }

    val cast: Cast by lazy { Cast(characters) }
    val screen: GameScreen by lazy { GameScreen(hud, menu, skip) }
    fun termsFor(lang: Lang): Map<String, String> = terms[lang.code] ?: emptyMap()
}

/** Co hra ukazuje mimo titulky: hlášky (jednotlivě se přeskočí) a nápisy menu (celá obrazovka se přeskočí). */
class GameScreen(hud: List<String>, menu: List<String>, private val skip: List<Regex> = emptyList()) {
    private val hudKeys = hud.map { TextLogic.simplify(it) }.filter { it.isNotEmpty() }.toSet()
    private val menuKeys = menu.map { TextLogic.simplify(it) }.filter { it.isNotEmpty() }.toSet()
    val isEmpty: Boolean get() = hudKeys.isEmpty() && menuKeys.isEmpty() && skip.isEmpty()
    fun isHud(line: String): Boolean = (hudKeys.isNotEmpty() && TextLogic.simplify(line) in hudKeys) || skip.any { it.containsMatchIn(line) }
    fun isMenu(lines: List<String>): Boolean = menuKeys.isNotEmpty() && lines.count { TextLogic.simplify(it) in menuKeys } >= 2

    companion object { val EMPTY = GameScreen(emptyList(), emptyList()) }
}

/**
 * Odhad hry podle jmen v titulcích: jména, která má jen jedna hra (Caminos, Port Gellhorn…).
 * Nic nepřepíná – jen nabídne řádek v Nastavení („Grand Theft Auto VI?“ s Přepnout).
 * Hra platí, když se objevila aspoň [MIN_NAMES] různá její jména a žádná jiná hra jich nemá ani polovinu.
 */
class GameGuess(games: List<GameProfile> = GameProfiles.GAMES, ambiguous: Set<String> = GameProfiles.AMBIGUOUS) {
    private val owner: Map<String, GameProfile>
    private val longest: Int
    init {
        val m = HashMap<String, GameProfile?>()
        for (g in games) for (n in (g.names + g.characters.flatMap { it.allNames }).distinct()) {
            if (n in ambiguous) continue
            val k = TextLogic.simplify(n)
            if (k.length < 4) continue          // krátké přezdívky (Lu, Jay) jsou moc náhodné
            m[k] = if (m.containsKey(k) && m[k]?.id != g.id) null else g
        }
        owner = m.filterValues { it != null }.mapValues { it.value!! }
        longest = owner.keys.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1
    }
    private val seen = HashMap<String, MutableSet<String>>()

    /** Přečtený titulek (anglicky, i se jménem mluvčího). */
    @Synchronized fun onText(text: String) {
        val words = TextLogic.simplify(text).split(' ').filter { it.isNotEmpty() }
        for (i in words.indices) for (len in 1..longest) {
            if (i + len > words.size) break
            val k = words.subList(i, i + len).joinToString(" ")
            val g = owner[k] ?: continue
            seen.getOrPut(g.id) { HashSet() }.add(k)
        }
    }

    /** Odhadnutá hra, nebo null (zatím málo jmen, nebo se to plete). */
    @Synchronized fun guess(): GameProfile? {
        val ranked = seen.entries.sortedByDescending { it.value.size }
        val best = ranked.firstOrNull() ?: return null
        if (best.value.size < MIN_NAMES) return null
        val second = ranked.getOrNull(1)?.value?.size ?: 0
        if (second * 2 >= best.value.size) return null
        return GameProfiles.of(best.key)
    }

    @Synchronized fun reset() = seen.clear()

    companion object { const val MIN_NAMES = 3 }
}

/**
 * Seznam her: profily a běžná slova, která se do univerzálního profilu nedávají. Vestavěný je v APK
 * ([GameProfiles.BUILTIN]), novější se dá stáhnout ([GameListJson], aplikace `GameListUpdate`).
 * [version] = verze seznamu, [downloaded] = stažený (ne vestavěný).
 */
class GameCatalog(val games: List<GameProfile>, extraAmbiguous: Collection<String>, val version: Int, val downloaded: Boolean = false) {
    /** Běžná slova mimo profily her (pro zápis seznamu). */
    val extraAmbiguous: List<String> = extraAmbiguous.distinct()
    /** Slova, která jsou zároveň běžná anglická slova – do univerzálního profilu nepatří. */
    val ambiguous: Set<String> = extraAmbiguous.toSet() + games.flatMap { it.common }

    /** Univerzální profil: nezaměnitelná jména ze všech her dohromady (bez běžných slov). */
    val universal = GameProfile("all", "*", games.flatMap { it.names }.filter { it !in ambiguous }.distinct(),
        // rod postav platí i v univerzálním profilu (Jason je muž v každé hře); jména s různým rodem vynechá [Cast]
        games.flatMap { it.characters },
        // hlášky a menu všech her (přesná shoda celého řádku – v replice se neobjeví)
        hud = games.flatMap { it.hud }.distinct(), menu = games.flatMap { it.menu }.distinct())

    val all: List<GameProfile> = listOf(universal) + games
    private val byId = all.associateBy { it.id }

    fun of(id: String?): GameProfile = byId[id] ?: universal
}

object GameProfiles {
    /**
     * Verze vestavěného seznamu her. Po každé změně profilů ji zvyš a přegeneruj `pomoc/hry/profily.json`
     * (`gradlew -p ci/mistni-kontrola seznamHer`) – jinak aplikace novější seznam ze stránek nestáhnou
     * a test `GameListJsonTest` neprojde.
     */
    const val VERSION = 1
    /** Nejnižší versionCode aplikace, která seznam v tomto tvaru umí přečíst (pole `minAppVerze`). */
    const val MIN_APP = 156

    private val RDR2 = (OfflinePrep.NAMES + Rdr2.NAMES).distinct()

    /**
     * Jména z GTA VI, která jsou zároveň běžná anglická slova nebo jména (modely aut, čtvrti, ulice). Přání majitele:
     * v GTA VI se nepřekládají ani ta – přeložená by nesouhlasila s nápisy v telefonu a na mapě. Patří proto do
     * profilu GTA VI, ale zároveň do [AMBIGUOUS], takže je univerzální profil (a odhad hry) vynechá.
     * Zdroje: gta.wiki (Vehicles in GTA VI, Leonida, 20. 11. 2026), gtabase, wikily.gg (mapa, 19. 11. 2026).
     */
    private val GTA6_COMMON = listOf(
        // čtvrti, města a ulice
        "Downtown", "Downtown Vice City", "Southside", "Bayside", "Crosstown", "Stockyard", "Hamlet", "Bluegrass",
        "Sundown", "Bonanza", "Capri", "Grayson", "Yorktown", "Bohemia", "Buckskin", "Domed Hills", "North Beaches",
        "Sandbars", "East Key", "Shore Drive",
        // modely aut, motorek, lodí a letadel
        "Lasso", "Mamba", "Mamba GT", "Sentinel", "Sentinel Classic", "Dominator", "Sirius", "Animus", "Bogan", "Greta",
        "Grouse", "Impaler", "Lowline", "Parkland", "Transient", "Woodland", "Granger", "Bison", "Blast", "Bobcat",
        "Crawler", "Reclaimer", "Moocher", "Shifter", "Shuttle", "Sojourn", "Spritz", "Trotter", "Iota", "Garda", "Crake",
        "Maverick", "Pumper", "Rhode", "Erode", "Azure", "Clarion", "Dingus", "Pontoon", "Tao", "Comet", "Furia",
        "Growler", "Locust", "Neo", "Paragon", "Zion", "Alpha", "Buccaneer", "Chino", "Deviant", "Faction", "Gauntlet",
        "Hermes", "Hustler", "Phoenix", "Stallion", "Tulip", "Vamos", "Virgo", "Cypher", "Buffalo", "Emperor", "Ingot",
        "Intruder", "Jugular", "Neon", "Primo", "Regina", "Stratum", "Sultan", "Windsor", "Astron", "Baller", "Cavalcade",
        "Dorado", "Jubilee", "Marshall", "Mesa", "Novak", "Terminus", "Blazer", "Caddy", "Contender", "Outlaw", "Patriot",
        "Rancher", "Rebel", "Injection", "Walton", "Wastelander", "Yosemite", "Burrito", "Journey", "Speedo", "Benson",
        "Biff", "Hauler", "Mule", "Packer", "Phantom", "Rubble", "Stockade", "Yankee", "Ripley", "Cliffhanger",
        "Double T", "Enduro", "Sanchez", "Sovereign", "Scorcher", "Dinghy", "Marquis", "Dodo", "Duster", "Luxor",
        "Mallard", "Nimbus", "Buzzard", "Swift", "Valkyrie", "Sea Sparrow", "Brigade", "Predator", "Guardian", "Golfer",
        "Surfer", "Hemming", "Furrow",
        // výrobci
        "Brute", "Stanley", "Lure", "Principe", "Lapwing", "Interstate", "Buckingham", "Mammoth",
    )
    /**
     * GTA VI (vyšlo 19. 11. 2026) – zvlášť od starších dílů, ať se neplete. Seznam z wiki a průvodců po vydání
     * (gta.wiki 20. 11. 2026, gtabase, beebom, playday.one, allthings.how, wikily.gg), oficiální je jen část – ladí
     * se podle záznamu ze hry (ci/sbirej-hru.ps1). Jména, která jsou i běžná slova, jsou v [GTA6_COMMON].
     * Nepodložená jména z doby před vydáním (Kordell, Raina, Cecilia, Abe Gonzalez) byla vyřazena.
     */
    private val GTA6 = GTA6_COMMON + listOf(
        // postavy
        "Jason", "Duval", "Lucia", "Caminos", "Lu", "Jay", "Jay Dog", "J-dog", "J-Dog", "Jas", "Loony", "Cal Hampton", "Boobie Ike", "Dre'Quan",
        "Dre'Quan Priest", "Real Dimez", "Bae-Luxe", "Roxy", "Raul Bautista", "Brian Heder", "Lori Heder", "Heder",
        "Kenton Rees", "Mattia", "Mattia Tauro", "Katerina Rosselli", "Mia Caminos", "Tomás Caminos", "Frankie Delight",
        "Yasmani", "Yasmani Cabrera", "Yago Jiménez", "Bret Vogel", "Eddie Acosta", "Pilot Pete", "Wyman",
        "Benedict Halifax", "Evelyn Andrews", "Nicole Park", "Andrés de León", "Petra Navarro", "Valentina", "Méndez",
        "Aldo Méndez", "Stefanie Gates", "Moses Watkins", "Sir Todd Bancroft", "Sparky Malarky",
        // moderátoři rádií a postavičky z televize
        "El Martillo", "La Gata", "Mud Dog Mike", "Delta Dawne", "Dr. Nita", "Madame Brigitte", "Skipper McD",
        "Richard Bastion", "Macca the Gator", "Chunkee the Manatee",
        // kraj, okresy, města
        "Leonida", "Vice City", "Vice-Dale County", "Mariana County", "Kelly County", "Leonard County", "Lummox County",
        "Leonida Keys", "Grassrivers", "Port Gellhorn", "Gellhorn", "Ambrosia", "Mount Kalaga", "Little Cuba", "Ocean Beach",
        "Vice Beach", "Washington Beach", "Peacock Bay", "Tequesta", "Salton", "Rockridge", "La Perle", "Balsero",
        "Leaf Links", "Starfish Island", "Rialto Islands", "Waxman Island", "Dalton Island", "Catalan Key", "Gloriana Key",
        "Key Lento", "Southwind", "Pelican Valley", "Watson Bay", "Belpoint", "Riquezas Keys", "Permit Keys", "Maroon Key",
        "Abismo Key", "Frogswater", "Draper Island", "Belville", "Waning Sands", "Millport", "Moreau", "Zephyr Islands",
        "Featherstone Islands", "Saint Attaway Islands", "Manifest Canyon", "Gulf of Leonida", "Lake Leonida",
        "Cottonmouth Lake", "Nehrling River", "Buckskin Swamp", "Lake Tallaloko", "Kalaga Reservoir",
        // doplněno podle gta.wiki (Leonida, 20. 11. 2026); „Key“ a „Island“ by překladač přeložil doslova
        "Vinewood Beach", "Redhill", "Ekanfinaka", "Peregrine Bay", "Bobcat Key", "Corsair Key", "Goose Key",
        "Hyde Island", "Little Hardin Island", "Frampton Islands", "Crude Islands", "Catalan Bay", "Gambit Bay",
        "Straits of Leonida", "Tequesta Retreat", "Mount Kalaga National Park", "Fairyland Forest", "Vice Decor District",
        "Sundown Beach",
        // ulice (wikily.gg: cedule v záběrech; Shorefront Ave a Seaview Blvd jen z jednoho zdroje)
        "Huevo St", "Huevo", "Storefront Ave", "Shorefront Ave", "Seaview Blvd",
        // místa a podniky
        "VCIA", "Vice City International Airport", "Leonida Penitentiary", "Leonida State Prison", "Hamlet Correctional",
        "Hamlet Correctional Facility", "Breakwater Quarry", "Emerald Springs", "Goliath Global", "Wyman's World",
        "Gellhorn International Raceway", "Boone Speedway",
        "MacAlister Gold Mine", "Allied Crystal", "Tisha-Wocka", "Starlet Motel", "Vercetti Estate", "McKittrick Farm",
        "Thrillbilly Mud Club", "Jack of Hearts", "Only Raw", "Only Raw Records", "Delights", "Rusty Anchor",
        "Barnacle Beauties", "Ammu-Nation", "Pay 'n' Spray", "Coastal Auto Care", "Rideout Customs", "One-Eyed Willie's",
        "Electric Fang", "Sinfrontera", "Sinfrontera National Bank", "Megamundo", "Navitrak", "Dale's Shopwell",
        "Bunko Jack's", "Kitty Litter",
        // gangy, organizace, rádia, aplikace v telefonu
        "Kingfishers", "Kolt and Associates", "M7", "La Polilla Negra", "Final Chapter", "Final Chapter MC", "San4San",
        "PTT Youngin\$", "Guardia Brothers", "Cellerica", "VCPD",
        // rádia (gta.wiki, Radio Stations in GTA VI)
        "Cocoteo FM", "Back Country Radio", "AfroBank FM", "The Chamber", "The Chamber 106.6", "Flash FM",
        "Dirty South Classics", "V-Rock", "Vice City FM", "Vice City Rap Radio", "WKTT", "WKTT Talk Radio",
        "Emotion 98.3", "Honey FM", "Kaleidoscope FM", "La Ola", "Leonida Jazz Radio", "Radio On-U", "Stockyard FM",
        "Symphony FM", "CircoLoco Records Radio", "Gloriana Blues Radio", "WorldWide FM",
        // aplikace v telefonu (beebom: potvrzené; WhatUp, Bleeter, LifeInvader zatím jen dohady)
        "WAINK", "BuckMe", "Ryde Me", "RydeMe", "Scooter Bros", "Snapmatic",
        "Skunk Ape", "Mr. Mangrove",
        // značky a modely aut, motorek, lodí a letadel (nezaměnitelné; běžná slova jsou v GTA6_COMMON)
        "Rugger", "Hauners", "MTL", "HVY", "BF", "Arcturus", "DeVanity", "Sungrazer", "Chavos XS", "Montagne",
        "Riata", "Gresley", "Swamp Blaster", "Vivanite", "Verus", "Alvino", "Nimbe", "Boomazoom", "LomBike", "Fan-E",
        "Delmar", "Dibnah", "Flatdeck", "Seacab", "Sea Enchantress", "Sedna", "Shasa", "Torlan", "Yakuruna",
        "Transgressor", "8F Drafter", "Bestia GTS", "Omnis", "Thrax", "Ruiner", "Sabre GT", "Vigero", "DF8-90",
        "Raiden", "V-STR", "Toros", "Slamvan", "Securicar", "Airtug", "Dashound", "Fieldmaster", "Lifta", "Avarus",
        "Lectro", "Manchez", "Nightblade", "Faggio", "Seashark", "Hydroblast", "Mammatus", "Shamal", "SuperVolito",
        "Volatus", "Esskey", "Venturas", "Rhino Tank", "P-996 LAZER", "DH-7 Iron Mule", "Zombie Chopper",
        "Albany", "Annis", "Benefactor", "Bravado", "Declasse", "Dinka", "Dundreary", "Gallivanter", "Grotti", "Imponte",
        "Invetero", "JoBuilt", "Karin", "Maibatsu", "Mer-Can", "Nagasaki", "Ocelot", "Pegassi", "Pfister", "Progen",
        "Schyster", "Shitzu", "Übermacht", "Vapid", "Zirconium", "Ganado", "Stanier", "Schafter", "Squalo", "Zorrusso",
        "Infernus", "Itali RSX", "Itali GTO", "Coquette", "Banshee", "Elegy", "Futo", "Blista", "Asterope", "Feroci",
        "Penumbra", "Tempesta", "Vitello", "Emerus", "Carbonizzare", "Dubsta", "Kamacho", "Landstalker", "Rebla",
        "Sandking", "Caracara", "Akane", "Schutzen", "Vectre", "Picador", "Manana", "Tailgater", "Sugoi", "Aleutian",
        "Allander", "Hellion", "Youga", "Boxville", "Rumpo", "Bandito", "Cheetah",
    )
    private val TLOU = listOf(
        "Joel", "Ellie", "Tommy", "Tess", "Marlene", "Sarah", "Henry", "Sam", "Abby", "Lev", "Yara", "Dina", "Jesse",
        "Owen", "Mel", "Manny", "Isaac", "Jackson", "Seattle", "Boston", "Pittsburgh", "Fireflies", "Seraphites",
        "Wolves", "FEDRA", "WLF", "Clickers", "Cordyceps",
    )
    private val GOW = listOf(
        "Kratos", "Atreus", "Freya", "Mimir", "Baldur", "Thor", "Odin", "Brok", "Sindri", "Tyr", "Faye", "Laufey",
        "Loki", "Angrboda", "Thrud", "Heimdall", "Fenrir", "Jormungandr", "Midgard", "Asgard", "Alfheim", "Helheim",
        "Jotunheim", "Vanaheim", "Svartalfheim", "Niflheim", "Muspelheim", "Ragnarok", "Leviathan", "Mjolnir",
    )
    private val SPIDERMAN = listOf(
        "Peter", "Parker", "Miles", "Morales", "Mary Jane", "MJ", "Aunt May", "Harry", "Osborn", "Norman", "Otto",
        "Octavius", "Kraven", "Venom", "Martin Li", "Yuri", "Ganke", "Rio", "Oscorp", "Harlem", "Queens", "Manhattan",
        "Spider-Man", "Kingpin", "Wilson Fisk", "Rhino", "Scorpion", "Electro", "Vulture",
    )
    private val HORIZON = listOf(
        "Aloy", "Rost", "Varl", "Erend", "Sylens", "Elisabet", "Sobeck", "Ted Faro", "Zo", "Kotallo", "Alva", "Beta",
        "Tilda", "Nora", "Carja", "Oseram", "Banuk", "Tenakth", "Utaru", "Quen", "Meridian", "GAIA", "HADES",
        "HEPHAESTUS", "MINERVA", "Zenith", "Thunderjaw", "Watcher",
    )
    private val TSUSHIMA = listOf(
        "Jin", "Sakai", "Shimura", "Yuna", "Taka", "Ishikawa", "Masako", "Norio", "Kenji", "Ryuzo", "Khotun Khan",
        "Tomoe", "Yuriko", "Tsushima", "Izuhara", "Toyotama", "Kamiagata", "Komoda", "Atsu", "Ezo",
    )
    private val CYBERPUNK = listOf(
        "Johnny", "Silverhand", "Jackie", "Welles", "Judy", "Alvarez", "Panam", "Palmer", "Takemura", "Hanako",
        "Yorinobu", "Saburo", "Arasaka", "Militech", "Dexter DeShawn", "Evelyn", "Rogue", "Kerry", "Misty", "Viktor",
        "Night City", "Watson", "Westbrook", "Pacifica", "Badlands", "Afterlife", "Songbird", "Reed", "Dogtown",
    )
    private val WITCHER = listOf(
        "Geralt", "Yennefer", "Triss", "Ciri", "Cirilla", "Vesemir", "Dandelion", "Jaskier", "Zoltan", "Eskel",
        "Lambert", "Emhyr", "Radovid", "Dijkstra", "Keira", "Roach", "Novigrad", "Velen", "Skellige", "Oxenfurt",
        "Kaer Morhen", "Vizima", "Toussaint", "Nilfgaard", "Redania", "Temeria", "Rivia", "Crach", "Eredin",
    )
    private val UNCHARTED = listOf(
        "Nathan", "Nate", "Drake", "Sully", "Sullivan", "Elena", "Chloe", "Nadine", "Rafe", "Avery", "Libertalia",
        "Shambhala", "Lazarevic", "Cutter",
    )
    private val HOGWARTS = listOf(
        "Hogwarts", "Hogsmeade", "Gryffindor", "Slytherin", "Ravenclaw", "Hufflepuff", "Sebastian", "Sallow",
        "Ominis", "Gaunt", "Poppy", "Natsai", "Ranrok", "Rookwood", "Fig", "Weasley", "Deek", "Azkaban",
    )
    private val ELDEN = listOf(
        "Ranni", "Melina", "Radahn", "Malenia", "Godrick", "Rennala", "Morgott", "Margit", "Radagon", "Marika",
        "Godfrey", "Miquella", "Blaidd", "Limgrave", "Liurnia", "Caelid", "Leyndell", "Erdtree", "Torrent", "Tarnished",
    )

    private fun m(name: String, vararg aliases: String) = GameCharacter(name, Gender.M, aliases.toList())
    private fun f(name: String, vararg aliases: String) = GameCharacter(name, Gender.F, aliases.toList())

    // postavy s rodem (hlavní a časté vedlejší; přezdívky, jak je hra používá). Rod podle zájmen na gta.wiki
    // a gtabase; Jasona volá Lucia „Jay“, Andrés „Jay Dog“; Lucii Jason „Lu“, Dre'Quan „Loony“ (gta.wiki).
    // Mia Caminos jen podle gtabase – gta.wiki má Luciinu matku bez jména.
    private val GTA6_CAST = listOf(
        m("Jason", "Jason Duval", "Jay", "Jay Dog", "J-dog", "J-Dog", "Jas", "Duval"), f("Lucia", "Lucia Caminos", "Lu", "Loony", "Caminos"),
        m("Cal Hampton"), m("Boobie Ike", "Boobie"), m("Dre'Quan", "Dre'Quan Priest", "Dre"), f("Bae-Luxe"), f("Roxy"),
        m("Raul Bautista", "Raul"), m("Brian Heder", "Brian"), f("Lori Heder", "Lori"), m("Donnie"), m("Arnold"),
        f("Mandy"), m("Kenton Rees", "Kenton"), m("Mattia", "Mattia Tauro"), f("Katerina Rosselli", "Kat"), m("Joey"),
        f("Shirley"), f("Mia Caminos", "Mia"), m("Tomás", "Tomás Caminos"), f("Linda"), f("Vicki"),
        m("Frankie Delight", "Frankie"), m("Moses", "Moses Watkins"), m("Dunk"), m("Tyrone"), m("Randy"), f("Andrea"),
        f("Nicole Park", "Nicole"), m("Andrés de León", "Andrés"), f("Petra Navarro", "Petra"), f("Valentina"),
        m("Méndez", "Aldo Méndez", "Mendez"), f("Stefanie Gates", "Stefanie"), m("Manuel"), m("Kirk"), m("Kyle"),
        m("Pilot Pete", "Pete"), m("Wyman"), m("Benedict Halifax", "Benedict"), f("Evelyn Andrews", "Evelyn"),
        m("El Martillo"), f("La Gata"), m("Mud Dog Mike"), f("Andy"),   // Andy je žena (majitel ve hře, 20. 11. 2026)
    )
    /** Herní pojmy GTA VI s pevným překladem – jen ty, které se v 1. a 4. pádě nemění. */
    private val GTA6_TERMS = mapOf(
        "cs" to mapOf("wanted level" to "hledanost", "safehouse" to "úkryt", "safe house" to "úkryt", "stash house" to "skrýš",
            "heist" to "loupež", "rap sheet" to "trestní rejstřík", "burner phone" to "jednorázový telefon",
            "getaway car" to "auto na útěk"),
        "sk" to mapOf("wanted level" to "hľadanosť", "safehouse" to "úkryt", "safe house" to "úkryt", "stash house" to "skrýš",
            "heist" to "lúpež", "rap sheet" to "register trestov", "burner phone" to "jednorazový telefón",
            "getaway car" to "auto na útek"),
    )
    /**
     * Nápisy GTA VI mimo titulky. Podle průvodců po vydání (allthings.how 19. 11. 2026): obrazovka neúspěchu má
     * nadpis FAILED a tlačítka RETRY CHECKPOINT / SKIP CHECKPOINT / RESTART MISSION; WASTED je červený nápis
     * uprostřed. MISSION PASSED, MISSION FAILED a BUSTED jsou jen zvyk série – ve hře zatím neověřené.
     */
    private val GTA6_HUD = listOf("WASTED", "BUSTED", "FAILED", "MISSION PASSED", "MISSION FAILED", "Retry Checkpoint",
        "Skip Checkpoint", "Restart Mission")
    /**
     * Pauza a menu – aspoň dva z nich na obrazovce = menu. Záložky pauzy Jason & Lucia / Map / Brief / Progress
     * a hlavní menu (beebom), filtry záznamu v Brief (Help Text, Objective Text) a záložky a řádky Nastavení
     * (allthings.how, Best GTA 6 Settings). Jednoslovné běžné řádky (Music, Dialogue, Media) záměrně ne.
     */
    private val GTA6_MENU = listOf("Jason & Lucia", "Map", "Brief", "Progress", "Game", "Store", "Help", "Photo Studio",
        "Settings", "Save", "Retry Checkpoint", "Restart Mission", "Help Text", "Objective Text",
        "Control Scheme", "Controller", "Gameplay", "Display", "UI", "Camera", "Audio",
        "Screen Narration", "Navigation Experience", "UI Text Size", "HDR Calibration", "Safe Zone", "Subtitles",
        "Speaker Names", "Closed Captions", "Aim Assist Strength", "Wanted Stars", "Ticker Feed", "Master Volume",
        "Mute Licensed Music", "GPS Voice", "Vehicle Radio", "Vibration & Haptics Strength", "Adaptive Triggers Resistance")
    /** Základní menu GTA VI ke překladu (snímky majitele 20. 11. 2026): menu zleva a lišta Nastavení. */
    private val GTA6_MENU_SPEC = MenuSpec(
        pause = listOf("Map", "Brief", "Progress", "Game", "Store", "Help", "Photo Studio", "Settings"),
        tabs = listOf("Control Scheme", "Controller", "Gameplay", "Display", "UI", "Camera", "Audio", "Account"),
        words = mapOf(
            "cs" to mapOf("Map" to "Mapa", "Brief" to "Přehled", "Progress" to "Postup", "Game" to "Hra", "Store" to "Obchod",
                "Help" to "Nápověda", "Photo Studio" to "Fotostudio", "Settings" to "Nastavení",
                "Control Scheme" to "Schéma ovládání", "Controller" to "Ovladač", "Gameplay" to "Hraní", "Display" to "Obraz",
                "UI" to "Rozhraní", "Camera" to "Kamera", "Audio" to "Zvuk", "Account" to "Účet"),
            "sk" to mapOf("Map" to "Mapa", "Brief" to "Prehľad", "Progress" to "Postup", "Game" to "Hra", "Store" to "Obchod",
                "Help" to "Pomoc", "Photo Studio" to "Fotoštúdio", "Settings" to "Nastavenia",
                "Control Scheme" to "Schéma ovládania", "Controller" to "Ovládač", "Gameplay" to "Hranie", "Display" to "Obraz",
                "UI" to "Rozhranie", "Camera" to "Kamera", "Audio" to "Zvuk", "Account" to "Účet"),
        ),
    )
    private val TLOU_CAST = listOf(
        m("Joel"), f("Ellie"), m("Tommy"), f("Tess"), f("Marlene"), f("Sarah"), m("Henry"), m("Sam"), f("Abby"),
        m("Lev"), f("Yara"), f("Dina"), m("Jesse"), m("Owen"), f("Mel"), m("Manny"), m("Isaac"),
    )
    private val GOW_CAST = listOf(
        m("Kratos"), m("Atreus"), f("Freya"), m("Mimir"), m("Baldur"), m("Thor"), m("Odin"), m("Brok"), m("Sindri"),
        m("Tyr"), f("Faye"), m("Loki"), f("Angrboda"), f("Thrud"), m("Heimdall"),
    )
    private val SPIDERMAN_CAST = listOf(
        m("Peter", "Peter Parker"), m("Miles", "Miles Morales"), f("Mary Jane", "MJ"), m("Harry"), m("Norman"),
        m("Otto"), m("Kraven"), m("Martin Li"), f("Rio"), m("Ganke"),
    )
    private val HORIZON_CAST = listOf(
        f("Aloy"), m("Rost"), m("Varl"), m("Erend"), m("Sylens"), f("Elisabet"), m("Ted Faro"), f("Zo"), m("Kotallo"),
        f("Alva"), f("Beta"), f("Tilda"),
    )
    private val TSUSHIMA_CAST = listOf(
        m("Jin", "Jin Sakai"), m("Shimura"), f("Yuna"), m("Taka"), m("Ishikawa"), f("Masako"), m("Norio"), m("Kenji"),
        m("Ryuzo"), m("Khotun Khan"), f("Tomoe"), f("Atsu"),
    )
    private val CYBERPUNK_CAST = listOf(
        m("Johnny", "Silverhand"), m("Jackie"), f("Judy"), f("Panam"), m("Takemura"), f("Hanako"), m("Yorinobu"),
        m("Saburo"), m("Dexter DeShawn", "Dex"), f("Evelyn"), f("Rogue"), m("Kerry"), f("Misty"), m("Viktor"), f("Songbird"),
    )
    private val WITCHER_CAST = listOf(
        m("Geralt"), f("Yennefer", "Yen"), f("Triss"), f("Ciri", "Cirilla"), m("Vesemir"), m("Dandelion", "Jaskier"),
        m("Zoltan"), m("Eskel"), m("Lambert"), m("Emhyr"), m("Radovid"), m("Dijkstra"), f("Keira"),
    )
    private val UNCHARTED_CAST = listOf(
        m("Nathan", "Nate", "Drake"), m("Sully", "Sullivan"), f("Elena"), f("Chloe"), f("Nadine"), m("Rafe"),
    )
    private val HOGWARTS_CAST = listOf(
        m("Sebastian", "Sallow"), m("Ominis"), f("Poppy"), f("Natsai"), m("Ranrok"), m("Rookwood"), m("Fig"),
    )
    private val ELDEN_CAST = listOf(
        f("Ranni"), f("Melina"), m("Radahn"), f("Malenia"), m("Godrick"), f("Rennala"), m("Morgott"), m("Margit"),
        m("Radagon"), f("Marika"), m("Godfrey"), m("Blaidd"),
    )

    /** Slova, která jsou zároveň běžná anglická slova a nepatří k jedné hře – do univerzálního profilu nepatří. */
    private val EXTRA_AMBIGUOUS = listOf(
        "Gray", "Grays", "Bell", "Bill", "Smith", "Jack", "John", "Uncle", "Sam", "Wolves", "Beta", "Rio", "Reed",
        "Rogue", "Fig", "Watcher", "Chop", "Roach", "Zenith", "Torrent", "Tarnished", "Jackson", "Summers", "Wade",
        "Drake", "Queens", "Watson", "Roman", "Jason", "Nora", "Zo", "Taka", "Jin", "Tess", "Mel", "Lev", "Faye",
        "Peter", "Harry", "Miles", "Michael", "Henry", "Sarah", "Jesse", "Owen", "Isaac", "Tommy", "Jimmy", "Amanda",
        "Karen", "Simon", "Susan", "Charles", "Sean", "Molly", "Elena", "Nathan", "Kerry", "Misty", "Evelyn", "Judy",
        "Johnny", "Jackie", "Sebastian", "Poppy", "Otto", "Norman", "Yuri", "Franklin", "Clinton", "Philips",
        "Morgan", "Milton", "Ross", "Leviathan", "Afterlife", "Badlands", "Cutter", "Avery", "Scorpion", "Rhino",
        "Vulture", "Electro", "Kingpin", "Venom", "Boston", "Seattle", "Pittsburgh", "Manhattan", "Harlem",
        "Mandy", "Linda", "Joey", "Randy", "Kirk", "Kyle", "Moses", "Dunk", "Pete",
        "Andy", "Jay", "Loony", "BF", "Huevo", "Tomás", "Stefanie", "Méndez", "Mendez", "La Ola", "Lori", "Mia", "Kat", "Arnold",
        "Donnie", "Shirley", "Vicki", "Frankie", "Andrea", "Nicole", "Petra", "Valentina", "Manuel", "Benedict",
        "Delights", "Cheetah", "Elegy", "Moreau", "Salton", "Southwind", "Millport", "M7", "Kingfishers", "Final Chapter",
        "The Chamber", "Game", "Map", "Help", "Save", "Store", "Progress", "Brief", "Settings",
    )

    private val BUILTIN_GAMES: List<GameProfile> = listOf(
        GameProfile("rdr2", "Red Dead Redemption 2", RDR2, Rdr2.CAST, Rdr2.TERMS, Rdr2.HUD, Rdr2.MENU, common = Rdr2.COMMON),
        // barvy jmen jako v titulcích GTA VI (majitel ve hře 20. 11. 2026): Jason žlutě, Lucia modře
        GameProfile("gta6", "Grand Theft Auto VI", GTA6, GTA6_CAST, GTA6_TERMS, GTA6_HUD, GTA6_MENU, prompts = true,
            colors = mapOf("Jason" to SpeakerColors.PALETTE[0], "Lucia" to SpeakerColors.PALETTE[1]), menuSpec = GTA6_MENU_SPEC,
            hint = "gp_bg_gta6", common = GTA6_COMMON),
        // GTA V: Michael modře, Franklin zeleně, Trevor oranžově (barvy postav v titulcích hry)
        GameProfile("gta", "Grand Theft Auto V", Gta5.NAMES.distinct(), Gta5.CAST, Gta5.TERMS, Gta5.HUD, Gta5.MENU, prompts = true,
            colors = mapOf("Michael" to SpeakerColors.PALETTE[1], "Franklin" to SpeakerColors.PALETTE[2], "Trevor" to SpeakerColors.PALETTE[5]), common = Gta5.COMMON),
        GameProfile("alanwake2", "Alan Wake 2", AlanWake2.NAMES.distinct(), AlanWake2.CAST, hud = AlanWake2.HUD, menu = AlanWake2.MENU, common = AlanWake2.COMMON),
        GameProfile("re9", "Resident Evil Requiem", ReRequiem.NAMES.distinct(), ReRequiem.CAST, ReRequiem.TERMS, ReRequiem.HUD, ReRequiem.MENU, common = ReRequiem.COMMON),
        GameProfile("bond", "007 First Light", Bond007.NAMES.distinct(), Bond007.CAST, common = Bond007.COMMON),
        GameProfile("diablo4", "Diablo IV", Diablo4.NAMES.distinct(), Diablo4.CAST, Diablo4.TERMS, Diablo4.HUD, Diablo4.MENU,
            skip = Diablo4.SKIP, common = Diablo4.COMMON),
        GameProfile("tlou", "The Last of Us", TLOU, TLOU_CAST),
        GameProfile("gow", "God of War", GOW, GOW_CAST),
        GameProfile("spiderman", "Spider-Man", SPIDERMAN, SPIDERMAN_CAST),
        GameProfile("horizon", "Horizon", HORIZON, HORIZON_CAST),
        GameProfile("tsushima", "Ghost of Tsushima / Yōtei", TSUSHIMA, TSUSHIMA_CAST),
        GameProfile("cyberpunk", "Cyberpunk 2077", CYBERPUNK, CYBERPUNK_CAST),
        GameProfile("witcher", "The Witcher 3", WITCHER, WITCHER_CAST),
        GameProfile("uncharted", "Uncharted", UNCHARTED, UNCHARTED_CAST),
        GameProfile("hogwarts", "Hogwarts Legacy", HOGWARTS, HOGWARTS_CAST),
        GameProfile("elden", "Elden Ring", ELDEN, ELDEN_CAST),
    )

    /** Vestavěný seznam (v APK) – platí, dokud se nestáhne novější, a jako záloha, když stažený nejde přečíst. */
    val BUILTIN = GameCatalog(BUILTIN_GAMES, EXTRA_AMBIGUOUS, VERSION)

    /** Seznam, podle kterého aplikace právě jede (vestavěný nebo stažený). */
    @Volatile var catalog: GameCatalog = BUILTIN
        private set

    /** Přepne na jiný seznam (stažený); null = zpět na vestavěný. */
    fun use(c: GameCatalog?) { catalog = c ?: BUILTIN }

    val GAMES: List<GameProfile> get() = catalog.games
    val AMBIGUOUS: Set<String> get() = catalog.ambiguous
    val UNIVERSAL: GameProfile get() = catalog.universal
    val ALL: List<GameProfile> get() = catalog.all

    fun of(id: String?): GameProfile = catalog.of(id)
    fun next(id: String?): GameProfile = ALL.let { all -> all[(all.indexOf(of(id)) + 1) % all.size] }
}

/** Předpřipravené překlady krátkých herních hlášek, které offline překladač často kazí. */
object BuiltinPhrases {
    private val CS = mapOf(
        "Get on your horse." to "Nasedni na koně.",
        "Get on the horse." to "Nasedni na koně.",
        "Mount up!" to "Na koně!",
        "Mount up." to "Na koně.",
        "Let's go!" to "Jdeme!",
        "Let's go." to "Jdeme.",
        "Let's ride!" to "Jedeme!",
        "Let's ride." to "Jedeme.",
        "Come on!" to "No tak!",
        "Come on." to "No tak.",
        "Hold on!" to "Vydrž!",
        "Hold on." to "Vydrž.",
        "Get down!" to "K zemi!",
        // běžné repliky, které offline překladač překládá doslova (ověřeno ve zkoušce překladu)
        "He's lying." to "Lže.", "He is lying." to "Lže.", "Don't move!" to "Ani hnout!", "Don't move." to "Ani hnout.",
        "Mind your own business." to "Starej se o sebe.", "Run for the gate!" to "Utíkej k bráně!", "Stand still!" to "Stůj!",
        "Everyone out!" to "Všichni ven!", "All of you, get out!" to "Všichni ven!", "Give it back." to "Vrať to.",
        "Give it back!" to "Vrať to!", "I owe you one." to "Máš to u mě.", "You can count on me." to "Můžeš se na mě spolehnout.",
        "Nice shot!" to "Dobrá rána!", "I've got a bad feeling about this." to "Mám z toho špatný pocit.",
        "What the hell is wrong with you?" to "Co to s tebou sakra je?", "Are you out of your mind?" to "Zbláznil ses?",
        "Are you crazy?" to "Zbláznil ses?", "Something's wrong." to "Něco tu nehraje.", "See you later." to "Tak zatím.",
        "Take care of yourself." to "Dávej na sebe pozor.", "It's a long story." to "To je na dlouhé vyprávění.",
        "You've got to be kidding me." to "To si děláš srandu.", "I need a drink." to "Potřebuju se napít.",
        "That's enough!" to "To stačí!", "You were right about him." to "Měl jsi o něm pravdu.",
        "You guys wait here." to "Vy počkejte tady.", "Boys, get on your horses." to "Chlapci, na koně.",
        "Take cover!" to "Kryjte se!",
        "Watch out!" to "Pozor!",
        "Look out!" to "Pozor!",
        "Follow me." to "Pojď za mnou.",
        "Follow me!" to "Za mnou!",
        "Stay here." to "Zůstaň tady.",
        "Stay close." to "Drž se u mě.",
        "Hands up!" to "Ruce vzhůru!",
        "Drop your weapon!" to "Zahoď zbraň!",
        "Hold your fire!" to "Nestřílejte!",
        "Open fire!" to "Palte!",
        "Fall back!" to "Ústup!",
        "Move out!" to "Vyrážíme!",
        "Cover me!" to "Kryj mě!",
        "I'm out of ammo!" to "Došly mi náboje!",
        "Never mind." to "To je jedno.",
        "Fair enough." to "Dobrá.",
        "You're welcome." to "Není zač.",
        "What the hell?" to "Co to sakra je?",
        "Son of a bitch!" to "Ty hajzle!",
        "Goddammit!" to "Sakra!",
        "Damn it!" to "Sakra!",
        "Howdy." to "Zdravím.",
        "Howdy, partner." to "Zdravím, parťáku.",
        "Much obliged." to "Mockrát děkuju.",
        "Sure thing." to "Jasně.",
        "You bet." to "To si piš.",
    )
    private val SK = mapOf(
        "Get on your horse." to "Nasadni na koňa.",
        "Get on the horse." to "Nasadni na koňa.",
        "Mount up!" to "Na kone!",
        "Mount up." to "Na kone.",
        "Let's go!" to "Ideme!",
        "Let's go." to "Ideme.",
        "Let's ride!" to "Ideme!",
        "Let's ride." to "Ideme.",
        "Come on!" to "No tak!",
        "Come on." to "No tak.",
        "Hold on!" to "Vydrž!",
        "Hold on." to "Vydrž.",
        "Get down!" to "K zemi!",
        // bežné repliky, ktoré offline prekladač prekladá doslova
        "He's lying." to "Klame.", "He is lying." to "Klame.", "Don't move!" to "Ani hnúť!", "Don't move." to "Ani hnúť.",
        "Mind your own business." to "Staraj sa o seba.", "Run for the gate!" to "Utekaj k bráne!", "Stand still!" to "Stoj!",
        "Everyone out!" to "Všetci von!", "All of you, get out!" to "Všetci von!", "Give it back." to "Vráť to.",
        "Give it back!" to "Vráť to!", "I owe you one." to "Máš to u mňa.", "You can count on me." to "Môžeš sa na mňa spoľahnúť.",
        "Nice shot!" to "Dobrá rana!", "I've got a bad feeling about this." to "Mám z toho zlý pocit.",
        "What the hell is wrong with you?" to "Čo to s tebou dočerta je?", "Are you out of your mind?" to "Zbláznil si sa?",
        "Are you crazy?" to "Zbláznil si sa?", "Something's wrong." to "Niečo tu nesedí.", "See you later." to "Tak zatiaľ.",
        "Take care of yourself." to "Dávaj na seba pozor.", "It's a long story." to "To je na dlhé rozprávanie.",
        "You've got to be kidding me." to "To si robíš srandu.", "I need a drink." to "Potrebujem sa napiť.",
        "That's enough!" to "To stačí!", "You were right about him." to "Mal si o ňom pravdu.",
        "You guys wait here." to "Vy počkajte tu.", "Boys, get on your horses." to "Chlapci, na kone.",
        "Take cover!" to "Kryte sa!",
        "Watch out!" to "Pozor!",
        "Look out!" to "Pozor!",
        "Follow me." to "Poď za mnou.",
        "Follow me!" to "Za mnou!",
        "Stay here." to "Zostaň tu.",
        "Stay close." to "Drž sa pri mne.",
        "Hands up!" to "Ruky hore!",
        "Drop your weapon!" to "Zahoď zbraň!",
        "Hold your fire!" to "Nestrieľajte!",
        "Open fire!" to "Páľte!",
        "Fall back!" to "Ústup!",
        "Move out!" to "Vyrážame!",
        "Cover me!" to "Kry ma!",
        "I'm out of ammo!" to "Došli mi náboje!",
        "Never mind." to "To je jedno.",
        "Fair enough." to "Dobre.",
        "You're welcome." to "Nie je za čo.",
        "What the hell?" to "Čo to dopekla je?",
        "Son of a bitch!" to "Ty sviňa!",
        "Goddammit!" to "Dopekla!",
        "Damn it!" to "Dopekla!",
        "Howdy." to "Zdravím.",
        "Howdy, partner." to "Zdravím, parťák.",
        "Much obliged." to "Veľmi pekne ďakujem.",
        "Sure thing." to "Jasné.",
        "You bet." to "To si píš.",
    )

    fun forLang(lang: Lang): Map<String, String> = when (lang) {
        Lang.CS -> CS
        Lang.SK -> SK
        else -> emptyMap()
    }
}
