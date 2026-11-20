package cz.playlingo.core

/**
 * Profil hry: jména postav a míst, která se nemají překládat, a postavy s rodem
 * (podle něj se opraví tvary v překladu, viz [CzechGender]).
 */
data class GameProfile(val id: String, val title: String, val names: List<String>, val characters: List<GameCharacter> = emptyList()) {
    val cast: Cast by lazy { Cast(characters) }
}

object GameProfiles {

    private val RDR2 = OfflinePrep.NAMES

    private val GTA = listOf(
        "Los Santos", "San Andreas", "Vice City", "Liberty City", "Blaine County", "Sandy Shores", "Paleto Bay",
        "Vinewood", "Leonida", "Grove Street", "Michael", "De Santa", "Franklin", "Clinton", "Trevor", "Philips",
        "Lamar", "Lester", "Amanda", "Tracey", "Jimmy", "Chop", "Wade", "Simeon", "Devin Weston", "Merryweather",
        "Ballas", "Vagos", "Niko", "Bellic", "Roman", "Tommy Vercetti", "Carl Johnson", "Big Smoke", "Jason", "Lucia",
    )
    /** GTA VI: postavy a místa (Vice City, Leonida) – zvlášť, ať se neplete se staršími díly. */
    private val GTA6 = listOf(
        "Jason", "Duval", "Lucia", "Caminos", "Vice City", "Leonida", "Leonida Keys", "Port Gellhorn", "Ambrosia",
        "Grassrivers", "Mount Kalaga", "Cal Hampton", "Boobie Ike", "Dre'Quan", "Real Dimez", "Bae-Luxe", "Roxy",
        "Raul Bautista", "Brian Heder", "Only Raw", "Lu", "Jas",
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

    // postavy s rodem (hlavní a časté vedlejší; přezdívky, jak je hra používá)
    private val GTA6_CAST = listOf(
        m("Jason", "Jason Duval", "Jas", "Duval"), f("Lucia", "Lucia Caminos", "Lu", "Caminos"),
        m("Cal Hampton"), m("Boobie Ike", "Boobie"), m("Dre'Quan"), f("Bae-Luxe"), f("Roxy"),
        m("Raul Bautista", "Raul"), m("Brian Heder", "Brian"),
    )
    private val GTA_CAST = listOf(
        m("Michael", "De Santa"), m("Franklin"), m("Trevor"), m("Lamar"), m("Lester"), f("Amanda"), f("Tracey"),
        m("Jimmy"), m("Simeon"), m("Niko"), m("Roman"), m("Tommy Vercetti"), m("Carl Johnson", "CJ"), m("Big Smoke"),
    )
    private val RDR2_CAST = listOf(
        m("Arthur", "Arthur Morgan"), m("Dutch"), m("John Marston"), m("Hosea"), m("Micah"), m("Javier"), m("Charles"),
        m("Lenny"), m("Bill"), m("Kieran"), m("Uncle"), f("Sadie"), f("Abigail"), f("Mary-Beth"), f("Tilly"),
        f("Karen"), f("Molly"), f("Susan"), f("Mary"),
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

    /** Slova, která jsou zároveň běžná anglická slova – do univerzálního profilu nepatří. */
    private val AMBIGUOUS = setOf(
        "Gray", "Grays", "Bell", "Bill", "Smith", "Jack", "John", "Uncle", "Sam", "Wolves", "Beta", "Rio", "Reed",
        "Rogue", "Fig", "Watcher", "Chop", "Roach", "Zenith", "Torrent", "Tarnished", "Jackson", "Summers", "Wade",
        "Drake", "Queens", "Watson", "Roman", "Jason", "Nora", "Zo", "Taka", "Jin", "Tess", "Mel", "Lev", "Faye",
        "Peter", "Harry", "Miles", "Michael", "Henry", "Sarah", "Jesse", "Owen", "Isaac", "Tommy", "Jimmy", "Amanda",
        "Karen", "Simon", "Susan", "Charles", "Sean", "Molly", "Elena", "Nathan", "Kerry", "Misty", "Evelyn", "Judy",
        "Johnny", "Jackie", "Sebastian", "Poppy", "Otto", "Norman", "Yuri", "Franklin", "Clinton", "Philips",
        "Morgan", "Milton", "Ross", "Leviathan", "Afterlife", "Badlands", "Cutter", "Avery", "Scorpion", "Rhino",
        "Vulture", "Electro", "Kingpin", "Venom", "Boston", "Seattle", "Pittsburgh", "Manhattan", "Harlem",
    )

    private val GAMES: List<GameProfile> = listOf(
        GameProfile("rdr2", "Red Dead Redemption 2", RDR2, RDR2_CAST),
        GameProfile("gta6", "Grand Theft Auto VI", GTA6, GTA6_CAST),
        GameProfile("gta", "Grand Theft Auto V / IV / San Andreas", GTA, GTA_CAST),
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

    /** Univerzální profil: nezaměnitelná jména ze všech her dohromady (bez běžných slov). */
    val UNIVERSAL = GameProfile("all", "*", GAMES.flatMap { it.names }.filter { it !in AMBIGUOUS }.distinct(),
        // rod postav platí i v univerzálním profilu (Jason je muž v každé hře); jména s různým rodem vynechá [Cast]
        GAMES.flatMap { it.characters })

    val ALL: List<GameProfile> = listOf(UNIVERSAL) + GAMES

    fun of(id: String?): GameProfile = ALL.firstOrNull { it.id == id } ?: UNIVERSAL
    fun next(id: String?): GameProfile = ALL[(ALL.indexOf(of(id)) + 1) % ALL.size]
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
