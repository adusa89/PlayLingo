package cz.playlingo.core

/*
 * Profily her přidané 20. 11. 2026 (GTA V, RDR2 navíc, Alan Wake 2, Resident Evil Requiem, 007 First Light, Diablo IV).
 * Data z wiki, oficiálních manuálů a průvodců (sbírali pomocníci; zdroje v historii sezení) – nápisy a menu
 * jsou nejméně jisté, ladí se podle záznamu ze hry (ci/sbirej-hru.ps1). Hry s oficiální češtinou
 * (Cyberpunk 2077, Ghost of Yōtei, Marvel's Wolverine) profil nepotřebují.
 *
 * COMMON = jména, která jsou zároveň běžná anglická slova nebo jména: v profilu hry zůstávají v originále,
 * ale jdou i do [GameProfiles.AMBIGUOUS], takže je univerzální profil a odhad hry vynechají.
 * Do jmen nepatří slova, která začínají běžnou větu („Return“, „Open“) – zůstala by anglicky i v replice.
 */
private fun m(name: String, vararg aliases: String) = GameCharacter(name, Gender.M, aliases.toList())
private fun f(name: String, vararg aliases: String) = GameCharacter(name, Gender.F, aliases.toList())

internal object Gta5 {
    val COMMON = listOf(
        "Michael", "Franklin", "Clinton", "Amanda", "Jimmy", "Chop", "Wade", "Floyd", "Debra", "Ron",
        "Denise", "Stretch", "Dave", "Steve", "Karen", "Andreas", "Martin", "Patricia", "Solomon", "Molly", "Tanisha", "Tao",
        "Brad", "Mary-Ann", "Cletus", "Maude", "Tonya", "Josh", "Beverly", "Dom", "Packie", "Gustavo", "Karim", "Rickie",
        "Milton", "Rocco", "Anton", "Hao", "Omega", "Barry", "Nigel", "Ursula", "Families", "The Lost", "Triads", "Altruists",
        "Davis", "Rancho", "Terminal", "Alta", "Burton", "Hawick", "Richman", "Harmony", "Grapeseed", "Morningwood",
        "Strawberry", "Chumash", "Zancudo", "Mission Row", "Mirror Park", "Little Seoul", "Textile City", "Paradise",
        "Facade", "Xero", "Suburban", "Bean Machine", "Channel X", "Rebel Radio", "Blue Ark",
        "Adder", "Bullet", "Comet", "Jester", "Sultan", "Buffalo", "Premier", "Primo", "Regina", "Surge", "Washington",
        "Exemplar", "Felon", "Jackal", "Oracle", "Sentinel", "Zion", "Dominator", "Gauntlet", "Phoenix", "Picador", "Ruiner",
        "Voodoo", "Blade", "Buccaneer", "Stinger", "Monroe", "Tornado", "Baller", "Granger", "Mesa", "Patriot", "Radius",
        "Seminole", "Bison", "Burrito", "Minivan", "Pony", "Speedo", "Surfer", "Journey", "Camper", "Rebel", "Injection",
        "Blazer", "Mule", "Phantom", "Hauler", "Packer", "Rubble", "Tipper", "Dozer", "Flatbed", "Rhino", "Barracks",
        "Crusader", "Akuma", "Ruffian", "Vader", "Nemesis", "Daemon", "Hexer", "Bagger", "Innovation", "Sovereign",
        "Thrust", "Buzzard", "Annihilator", "Frogger", "Maverick", "Valkyrie", "Savage", "Titan", "Duster", "Mallard",
        "Luxor", "Hydra", "Marquis", "Tropic", "Speeder", "Dinghy", "Submersible", "Toro", "Predator", "Stockade",
        "Brute", "Emperor", "Obey", "Coil", "Western", "Mammoth", "Stanley", "Principe", "Chariot", "Canis", "Willard",
        "Benefactor", "Ocelot", "Cheetah", "Infernus", "Banshee",
    )
    val NAMES = COMMON + listOf(
        // postavy
        "Trevor", "Tracey", "Michael De Santa", "De Santa", "Michael Townley", "Townley", "Franklin Clinton", "Trevor Philips", "Philips",
        "Lamar Davis", "Lamar", "Lester Crest", "Lester", "Amanda De Santa", "Tracey De Santa", "Jimmy De Santa",
        "Wade Hebert", "Floyd Hebert", "Ron Jakowski", "Nervous Ron", "Simeon Yetarian", "Simeon", "Devin Weston",
        "Dave Norton", "Steve Haines", "Karen Daniels", "Andreas Sanchez", "Martin Madrazo", "Madrazo", "Patricia Madrazo",
        "Javier Madrazo", "Solomon Richards", "Molly Schultz", "Tanisha Jackson", "Tao Cheng", "Wei Cheng",
        "Denise Clinton", "Harold Joseph", "Brad Snider", "Lazlow", "Fabien LaRouche", "Dr. Friedlander",
        "Isiah Friedlander", "Mary-Ann Quinn", "Cletus Ewing", "Maude Eccles", "Tonya Wiggins", "Josh Bernstein",
        "Beverly Felton", "Dom Beasley", "Packie McReary", "Gustavo Mota", "Karim Denz", "Taliana Martinez", "Rickie Lukens",
        "Ferdinand Kerimov", "Mr. K", "Milton McIlroy", "Anton Beaudelaire", "Rocco Pelosi", "Lenny Avery",
        "Abigail Mathers", "Jay Norris", "Lacey Jonas", "Don Percival", "Mrs. Thornhill", "Ortega",
        // gangy a organizace
        "Ballas", "Vagos", "Los Santos Vagos", "Lost MC", "Aztecas", "Varrios Los Aztecas", "Marabunta Grande",
        "O'Neils", "Epsilon", "Epsilon Program", "Merryweather", "FIB", "IAA", "LSPD", "NOOSE", "Kifflom",
        // kraj, čtvrti, místa
        "Los Santos", "San Andreas", "Blaine County", "North Yankton", "Ludendorff", "Liberty City", "Vinewood",
        "Vinewood Hills", "West Vinewood", "Downtown Vinewood", "East Vinewood", "Rockford Hills", "Richman Glen",
        "Del Perro", "Vespucci", "Vespucci Beach", "Vespucci Canals", "Pacific Bluffs", "Banham Canyon", "Tongva Hills",
        "Pillbox Hill", "La Puerta", "Chamberlain Hills", "Grove Street", "South Los Santos", "La Mesa",
        "Murrieta Heights", "El Burro Heights", "Cypress Flats", "Elysian Island", "Port of Los Santos", "Los Santos River",
        "Sandy Shores", "Paleto Bay", "Paleto Forest", "Procopio Beach", "Stab City", "Alamo Sea", "Grand Senora Desert",
        "Mount Chiliad", "Mount Josiah", "Mount Gordo", "Raton Canyon", "Cassidy Creek", "Great Chaparral",
        "Tataviam Mountains", "Palomino Highlands", "Fort Zancudo", "Lago Zancudo", "Land Act Dam",
        "Palmer-Taylor Power Station", "Humane Labs", "Bolingbroke Penitentiary", "Galileo Observatory", "Del Perro Pier",
        "Kortz Center", "Vinewood Bowl", "Richards Majestic", "Maze Bank Tower", "Maze Bank Arena", "Union Depository",
        "Pillbox Hill Medical Center", "Mount Zonah", "LSIA", "Los Santos International Airport", "Sandy Shores Airfield",
        "McKenzie Field", "Cayo Perico", "Great Ocean Highway", "Route 68",
        // podniky a značky
        "Vanilla Unicorn", "Bahama Mamas", "Tequi-la-la", "Split Sides", "Yellow Jack Inn", "Hookies", "Cluckin' Bell",
        "Burger Shot", "Up-n-Atom", "Pisswasser", "Cherenkov", "Sprunk", "eCola", "Ammu-Nation", "Los Santos Customs",
        "Premium Deluxe Motorsport", "Legendary Motorsport", "Southern San Andreas Super Autos", "Weazel", "Weazel News",
        "Lifeinvader", "Bleeter", "iFruit", "Snapmatic", "Eyefind", "Maze Bank", "Fleeca", "Pacific Standard",
        "Blaine County Savings", "Lombank", "Gruppe Sechs", "Ponsonbys", "Binco", "Sub Urban", "LTD Gasoline",
        "Rob's Liquor", "Globe Oil", "Herr Kutz", "Beachcombover", "BAWSAQ", "Dynasty 8", "Elitás Travel",
        // rádia
        "Los Santos Rock Radio", "Non-Stop-Pop FM", "Radio Los Santos", "West Coast Talk Radio", "Soulwax FM",
        "East Los FM", "West Coast Classics", "Worldwide FM", "FlyLo FM", "The Lowdown 91.1", "Radio Mirror Park",
        "Space 103.2", "Vinewood Boulevard Radio", "Blaine County Radio", "Blonded Los Santos 97.8 FM",
        "Los Santos Underground Radio", "Self-Esteem Radio", "Still Slipping Los Santos", "Kult FM 99.1",
        "The Music Locker", "MOTOMAMI Los Santos",
        // značky a modely aut (nezaměnitelné)
        "Albany", "Annis", "Bollokan", "Bravado", "Buckingham", "Cheval", "Declasse", "Dewbauchee", "Dinka", "Dundreary",
        "Enus", "Fathom", "Gallivanter", "Grotti", "Hijak", "HVY", "Imponte", "Invetero", "Jacksheepe", "JoBuilt", "Karin",
        "Lampadati", "Maibatsu", "MTL", "Nagasaki", "Overflod", "Pegassi", "Pfister", "Progen", "Schyster", "Shitzu",
        "Speedophile", "Truffade", "Übermacht", "Vapid", "Vulcar", "Vysser", "Weeny", "Zirconium",
        "Entity XF", "Zentorno", "Vacca", "Turismo R", "Osiris", "T20", "Voltic", "Carbonizzare", "Coquette", "Elegy RH8",
        "Feltzer", "Fusilade", "Futo", "Khamelion", "Massacro", "Rapid GT", "Schwartzer", "Sultan RS", "Surano", "Penumbra",
        "Blista", "Asea", "Asterope", "Fugitive", "Glendale", "Ingot", "Intruder", "Schafter", "Stanier", "Stratum",
        "Super Diamond", "Tailgater", "Warrener", "Sabre Turbo", "Vigero", "Hotknife", "Chino", "Z-Type", "JB 700",
        "Peyote", "Manana", "Pigalle", "BeeJay XL", "Cavalcade", "Dubsta", "FQ 2", "Gresley", "Habanero", "Huntley S",
        "Landstalker", "Rocoto", "Serrano", "Bobcat XL", "Rumpo", "Youga", "Sadler", "Sandking", "Bodhi", "Dune Buggy",
        "Kalahari", "Bifta", "Benson", "Pounder", "Biff", "Bati 801", "Hakuchou", "PCJ 600", "Double-T", "Carbon RS",
        "Sanchez", "Faggio", "Cargobob", "Skylift", "Lazer", "P-996 LAZER", "Cuban 800", "Shamal", "Vestra", "Velum",
        "Mammatus", "Dodo", "Besra", "Squalo", "Jetmax", "Suntrap", "Seashark", "Securicar",
    )
    val CAST = listOf(
        m("Michael", "Michael De Santa", "Michael Townley", "De Santa", "Mr. De Santa", "Mikey", "Mike"),
        m("Franklin", "Franklin Clinton", "Frank"), m("Trevor", "Trevor Philips", "Philips", "Mr. Philips"),
        m("Lamar", "Lamar Davis"), m("Lester", "Lester Crest"), f("Amanda", "Amanda De Santa"), f("Tracey", "Tracey De Santa"),
        m("Jimmy", "Jimmy De Santa"), m("Chop"), m("Wade", "Wade Hebert"), m("Floyd", "Floyd Hebert"), f("Debra"),
        m("Ron", "Nervous Ron", "Ron Jakowski"), f("Denise", "Denise Clinton"), m("Stretch", "Harold Joseph"),
        m("Simeon", "Simeon Yetarian"), m("Devin", "Devin Weston", "Mr. Weston"), m("Dave", "Dave Norton", "Agent Norton"),
        m("Steve", "Steve Haines", "Agent Haines"), f("Karen", "Karen Daniels"), m("Andreas", "Andreas Sanchez"),
        m("Martin", "Martin Madrazo", "Madrazo", "Mr. Madrazo"), f("Patricia", "Patricia Madrazo", "Mrs. Madrazo"),
        m("Javier Madrazo"), m("Solomon", "Solomon Richards"), f("Molly", "Molly Schultz"), f("Tanisha", "Tanisha Jackson"),
        m("Tao", "Tao Cheng"), m("Wei Cheng", "Mr. Cheng"), m("Brad", "Brad Snider"), m("Lazlow"), m("Fabien"),
        m("Dr. Friedlander", "Friedlander"), f("Mary-Ann", "Mary-Ann Quinn"), m("Cletus", "Cletus Ewing"),
        f("Maude", "Maude Eccles"), f("Tonya", "Tonya Wiggins"), m("Josh", "Josh Bernstein"), m("Beverly", "Beverly Felton"),
        m("Dom", "Dom Beasley"), m("Packie", "Packie McReary"), m("Gustavo", "Gustavo Mota"), m("Karim", "Karim Denz"),
        f("Taliana", "Taliana Martinez"), m("Rickie", "Rickie Lukens"), m("Ferdinand", "Mr. K"), m("Milton", "Milton McIlroy"),
        m("Rocco", "Rocco Pelosi"), m("Anton"), m("Lenny Avery"), f("Abigail Mathers"), m("Jay Norris"), f("Lacey Jonas"),
        m("Hao"), m("Omega"), m("Barry"), m("Nigel"), f("Mrs. Thornhill"), f("Ursula"), m("Ortega"), m("Don Percival"),
    )
    val TERMS = mapOf(
        "cs" to mapOf("wanted level" to "hledanost", "heist" to "loupež", "safehouse" to "úkryt", "safe house" to "úkryt",
            "hideout" to "úkryt", "getaway car" to "auto na útěk", "getaway vehicle" to "vůz na útěk",
            "stunt jump" to "kaskadérský skok", "armored truck" to "obrněný vůz", "strip club" to "striptýzový klub"),
        "sk" to mapOf("wanted level" to "hľadanosť", "heist" to "lúpež", "safehouse" to "úkryt", "safe house" to "úkryt",
            "hideout" to "úkryt", "getaway car" to "auto na útek", "getaway vehicle" to "voz na útek",
            "stunt jump" to "kaskadérsky skok", "armored truck" to "obrnený voz", "strip club" to "striptízový klub"),
    )
    val HUD = listOf("WASTED", "BUSTED", "MISSION PASSED", "MISSION FAILED", "STUNT JUMP COMPLETED", "UNIQUE STUNT JUMP COMPLETED")
    val MENU = listOf("Map", "Brief", "Stats", "Settings", "Game", "Online", "Friends", "Gallery", "Store", "Mission", "Help",
        "Dialogue", "Notifications", "Newswire", "Skills", "General", "Crimes", "Vehicles", "Cash", "Combat", "Weapons",
        "100% Completion", "Checklist", "Replay Mission", "Replay Strangers and Freaks", "Load Game", "New Game",
        "Upload Saved Game", "Controls", "Audio", "Camera", "First Person", "Display", "Graphics", "Saving and Startup",
        "Rockstar Editor", "Legend")
}

internal object Rdr2 {
    val COMMON = listOf(
        "Valentine", "Rhodes", "Blackwater", "Armadillo", "Tumbleweed", "Strawberry", "Colter", "Grizzlies", "Heartlands",
        "Great Plains", "Big Valley", "Tall Trees", "Night Folk", "Plainview", "Twin Rocks", "Wapiti", "Mary", "Jenny",
        "Davey", "Mac", "Jamie", "Beau", "Penelope", "Charlotte", "Edith", "Hercule", "Hamish", "Algernon", "Leonard",
        "Reverend", "Pearson", "Strauss", "Swanson", "Cornwall", 
        "Appaloosa", "Ardennes", "Turkoman",
    )
    /** Doplněk k [OfflinePrep.NAMES] (ten má hlavní jména RDR2 odjakživa). */
    val NAMES = COMMON + listOf(
        "Arthur", "Dutch", "Hosea", "Micah", "Lenny", "Abigail", "Javier", "Sadie", "Tilly", "Josiah", "Kieran",
        "Arthur Morgan", "Dutch van der Linde", "Hosea Matthews", "John Marston", "Abigail Roberts", "Abigail Marston",
        "Jack Marston", "Sadie Adler", "Mrs. Adler", "Jake Adler", "Charles Smith", "Javier Escuella", "Lenny Summers",
        "Bill Williamson", "Micah Bell", "Mary-Beth Gaskill", "Gaskill", "Tilly Jackson", "Karen Jones", "Molly O'Shea",
        "O'Shea", "Susan Grimshaw", "Grimshaw", "Simon Pearson", "Leopold Strauss", "Orville Swanson", "Reverend Swanson",
        "Kieran Duffy", "Sean Macguire", "Josiah Trelawny", "Trelawny", "Jenny Kirk", "Davey Callender", "Mac Callender",
        "Callender", "Colm O'Driscoll", "Colm", "Andrew Milton", "Agent Milton", "Edgar Ross", "Agent Ross",
        "Leviticus Cornwall", "Mary Linton", "Mary Gillis", "Jamie Gillis", "Angelo Bronte", "Bronte", "Guido Martelli",
        "Catherine Braithwaite", "Penelope Braithwaite", "Tavish Gray", "Leigh Gray", "Beau Gray", "Hamish Sinclair",
        "Charlotte Balfour", "Algernon Wasp", "Albert Mason", "Henri Lemieux", "Archibald MacGregor", "Hercule Fontaine",
        "Alberto Fussar", "Herbert Moon", "Edith Downes", "Thomas Downes", "Jim Milton", "Rains Fall", "Eagle Flies", "Paytah",
        "Ambarino", "New Hanover", "Lemoyne", "West Elizabeth", "New Austin", "Guarma", "Grizzlies East", "Grizzlies West",
        "Cumberland Forest", "Roanoke Ridge", "Bayou Nwa", "Bluewater Marsh", "Scarlett Meadows", "Hennigan's Stead",
        "Cholla Springs", "Rio Bravo", "Gaptooth Ridge", "Saint Denis", "Annesburg", "Van Horn", "Emerald Ranch", "Lagras",
        "Butcher Creek", "Thieves' Landing", "Manzanita Post", "Sisika Penitentiary", "Sisika", "Fort Wallace", "Fort Mercer",
        "Fort Brennand", "MacFarlane's Ranch", "Benedict Point", "Riggs Station", "Flatneck Station", "Wallace Station",
        "Bacchus Station", "Horseshoe Overlook", "Clemens Point", "Shady Belle", "Beaver Hollow", "Lakay", "Flat Iron Lake",
        "Lake Isabella", "Owanjila", "O'Creagh's Run", "Lannahechee River", "Kamassa River", "Dakota River",
        "San Luis River", "Upper Montana River", "Lower Montana River", "Little Creek River", "Bacchus Bridge",
        "Braithwaite Manor", "Caliga Hall", "Catfish Jackson's", "Bolger Glade", "Copperhead Landing", "Mount Hagen",
        "Mount Shann", "Cotorra Springs", "Granite Pass", "Calumet Ravine", "Donner Falls", "Bard's Crossing", "Doverhill",
        "Hanging Dog Ranch", "Adler Ranch", "Beecher's Hope", "Pronghorn Ranch", "Downes Ranch", "Cornwall Kerosene & Tar",
        "Lemoyne National Bank", "Keane's Saloon", "Smithfield's Saloon", "Bastille Saloon", "Doyle's Tavern",
        "Ojo del Diablo", "O'Driscolls", "O'Driscoll Boys", "Lemoyne Raiders", "Del Lobo", "Del Lobos", "Murfree Brood",
        "Skinner Brothers", "Laramie Gang", "Pinkertons", "Pinkerton Agency", "Braithwaites",
        "Cattleman Revolver", "Schofield Revolver", "LeMat Revolver", "Mauser Pistol", "Volcanic Pistol",
        "Lancaster Repeater", "Litchfield Repeater", "Evans Repeater", "Springfield Rifle", "Carcano Rifle",
        "Tennessee Walker", "Missouri Fox Trotter", "Kentucky Saddler", "American Standardbred", "Nokota",
        "Dead Eye", "Eagle Eye",
    )
    val CAST = listOf(
        m("Arthur", "Arthur Morgan", "Morgan", "Mr. Morgan"), m("Dutch", "Dutch van der Linde", "Van der Linde", "Mr. van der Linde"),
        m("Hosea", "Hosea Matthews"), m("John", "John Marston", "Marston", "Mr. Marston", "Jim Milton"),
        f("Abigail", "Abigail Roberts", "Abigail Marston"), m("Jack", "Jack Marston"), f("Sadie", "Sadie Adler", "Mrs. Adler"),
        m("Charles", "Charles Smith"), m("Javier", "Javier Escuella"), m("Lenny", "Lenny Summers", "Leonard"),
        m("Bill", "Bill Williamson", "Williamson"), m("Micah", "Micah Bell"), f("Mary-Beth", "Mary-Beth Gaskill"),
        f("Tilly", "Tilly Jackson"), f("Karen", "Karen Jones"), f("Molly", "Molly O'Shea", "Miss O'Shea"),
        f("Susan", "Susan Grimshaw", "Miss Grimshaw", "Grimshaw"), m("Pearson", "Simon Pearson", "Simon", "Mr. Pearson"),
        m("Strauss", "Leopold Strauss", "Leopold", "Mr. Strauss"), m("Swanson", "Reverend Swanson", "Orville Swanson", "Reverend"),
        m("Kieran", "Kieran Duffy"), m("Sean", "Sean Macguire"), m("Trelawny", "Josiah Trelawny", "Josiah"), m("Uncle"),
        f("Jenny", "Jenny Kirk"), m("Davey", "Davey Callender"), m("Mac", "Mac Callender"), m("Colm", "Colm O'Driscoll"),
        m("Milton", "Andrew Milton", "Agent Milton"), m("Ross", "Edgar Ross", "Agent Ross"), m("Cornwall", "Leviticus Cornwall"),
        f("Mary", "Mary Linton", "Mary Gillis"), m("Jamie", "Jamie Gillis"), m("Bronte", "Angelo Bronte"), m("Guido Martelli", "Guido"),
        f("Catherine Braithwaite", "Mrs. Braithwaite"), f("Penelope", "Penelope Braithwaite"), m("Tavish Gray"),
        m("Leigh Gray", "Sheriff Gray"), m("Beau", "Beau Gray"), m("Hamish", "Hamish Sinclair"), f("Charlotte", "Charlotte Balfour"),
        m("Algernon", "Algernon Wasp"), m("Albert Mason", "Mr. Mason"), m("Henri Lemieux"), m("Archibald MacGregor"),
        m("Hercule", "Hercule Fontaine"), m("Fussar", "Alberto Fussar"), m("Herbert Moon"), f("Edith", "Edith Downes", "Mrs. Downes"),
        m("Thomas Downes"), m("Jake Adler"), m("Rains Fall"), m("Eagle Flies"),
    )
    val TERMS = mapOf(
        "cs" to mapOf("hideout" to "úkryt", "stagecoach" to "dostavník", "general store" to "obchod se smíšeným zbožím", "tonic" to "tonikum"),
        "sk" to mapOf("hideout" to "úkryt", "stagecoach" to "dostavník", "general store" to "obchod so zmiešaným tovarom", "tonic" to "tonikum"),
    )
    val HUD = listOf("MISSION FAILED", "Retry Checkpoint", "Restart Mission", "Quit Mission",
        "Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4", "Chapter 5", "Chapter 6", "Epilogue")
    val MENU = listOf("Map", "Player", "Progress", "Story", "Compendium", "Total Completion", "Social Club", "Settings", "Online",
        "Save Game", "Load Game", "Challenges", "Stats", "Rewards", "Gameplay", "Camera", "Display", "Graphics", "Audio",
        "Controls", "Controller", "Language")
}

/** Alan Wake 2 (i Night Springs a The Lake House) – oficiální češtinu nemá. */
internal object AlanWake2 {
    val COMMON = listOf(
        "Overlap", "Clicker", "Manuscript", "Lunch Box", "Nursery Rhyme", "Word of Power", "Words of Power", "North Star",
        "General Store", "Sheriff's Station", "Lighthouse Trailer Park", "Hunting Shack", "Witch's Hut", "Wellness Center",
        "Lake House", "Dark Place", "Dark Presence", "Room 665", "Final Draft", "Number One Fan", "Time Breaker",
        "Mind Place", "Writer's Room", "Case Board", "Plot Board", "Angel Lamp", "Cult Stash",
        "Logan", "Tim", "Rose", "Door", "Ed", "Donna", "Diana", "Dylan", "Jesse", "Sarah", "Alice", "Barry", "Charlie",
        "Norman", "Pat", "Tammy", "Jules", "Darling", "Casey", "Anderson", "Scratch", "Saga", "Alan", "Wake", "Breaker",
        "Tor", "Setter", "Thornton",
    )
    val NAMES = COMMON + listOf(
        "Zane", "Ahti", "Odin", "Bright Falls", "Watery", "Cauldron Lake", "Oldest House", "Night Springs", "Oh Deer Diner", "Coffee World",
        "Elderwood Palace Lodge", "Valhalla Nursing Home", "Valhalla Manor", "Cauldron Lake Lodge", "Suomi Hall",
        "Kalevala Knights", "Kalevala Knights Workshop", "Witchfinder's Station", "Bunker Woods", "Huotari Well",
        "Teapot Cove", "Mirror Peak", "Oceanview Hotel", "Oceanview Motel", "Espresso Express", "Deerfest", "FBC",
        "Federal Bureau of Control", "Cult of the Tree", "Ripple Effect", "Poison Pill Entertainment", "KBF-FM",
        "Old Gods of Asgard", "Poets of the Fall", "Herald of Darkness", "Lady of the Light", "Koskela Brothers",
        "Saga Anderson", "Alan Wake", "Alex Casey", "Tim Breaker", "Warlin Door", "Mr. Door", "Mr. Scratch", "Alice Wake",
        "Rose Marigold", "Odin Anderson", "Tor Anderson", "Logan Anderson", "Ilmo Koskela", "Jaakko Koskela", "Koskela",
        "Robert Nightingale", "Nightingale", "Cynthia Weaver", "Thomas Zane", "Kiran Estevez", "Estevez", "Pat Maine",
        "Casper Darling", "Jesse Faden", "Jesbet", "Dylan Faden", "Jules Marmont", "Diana Marmont", "Rudolf Lane",
        "Emil Hartman", "Hartman", "Barry Wheeler", "Sarah Breaker", "Barbara Jagger", "Bob Balder", "Fat Bob", "Mulligan",
        "Ed Booker", "Tammy Booker", "Norman MacDonald", "Vladimir Blum", "Charline Koskela", "Charlie Koskela",
        "Jim Figamore", "Gil Davis", "Mayor Setter", "Baba Jakala", "Tapio Annala", "Mandy-May", "Steven Lin", "David Woods",
    )
    val CAST = listOf(
        f("Saga Anderson", "Saga", "Agent Anderson"), m("Alan Wake", "Alan", "Wake"), m("Alex Casey", "Casey", "Agent Casey"),
        m("Tim Breaker", "Tim", "Breaker", "Sheriff Breaker"), m("Warlin Door", "Mr. Door", "Door"), m("Scratch", "Mr. Scratch"),
        f("Alice Wake", "Alice"), f("Rose Marigold", "Rose"), m("Odin Anderson", "Odin"), m("Tor Anderson", "Tor"),
        f("Logan Anderson", "Logan"), m("Ilmo Koskela", "Ilmo"), m("Jaakko Koskela", "Jaakko"),
        m("Robert Nightingale", "Nightingale", "Agent Nightingale"), f("Cynthia Weaver", "Cynthia", "Lady of the Light"),
        m("Thomas Zane", "Zane"), m("Ahti"), f("Kiran Estevez", "Kiran", "Estevez", "Agent Estevez"), m("Pat Maine", "Pat"),
        m("Casper Darling", "Dr. Darling", "Darling"), f("Jesse Faden", "Jesse", "Jesbet"), m("Dylan Faden", "Dylan"),
        m("Jules Marmont", "Jules"), f("Diana Marmont", "Diana"), m("Rudolf Lane", "Rudolf"), m("Emil Hartman", "Hartman", "Dr. Hartman"),
        m("Barry Wheeler", "Barry"), f("Sarah Breaker", "Sarah"), f("Barbara Jagger", "Jagger"), m("Bob Balder", "Fat Bob"),
        m("Mulligan"), m("Ed Booker", "Ed"), f("Tammy Booker", "Tammy"), m("Norman MacDonald", "Norman"), m("Vladimir Blum", "Blum"),
        f("Charline Koskela", "Charline"), m("Charlie Koskela", "Charlie"),
    )
    val HUD = listOf("Prologue: The Cult", "Return 0: The Cult", "Return 1: Invitation", "Return 2: The Heart",
        "Return 3: Local Girl", "Return 4: No Chance", "Return 5: Old Gods", "Return 6: Scratch", "Return 7: The Summoning",
        "Return 7: Summoning", "Return 8: Deerfest", "Return 9: Come Home", "Initiation 1: Late Night", "Initiation 2: Casey",
        "Initiation 3: Haunting", "Initiation 4: We Sing", "Initiation 5: Room 665", "Initiation 6: Return",
        "Initiation 7: Masks", "Initiation 8: Zane's Film", "Initiation 9: Gone", "Return 1", "Return 2", "Return 3",
        "Return 4", "Return 5", "Return 6", "Return 7", "Return 8", "Return 9", "Initiation 1", "Initiation 2",
        "Initiation 3", "Initiation 4", "Initiation 5", "Initiation 6", "Initiation 7", "Initiation 8", "Initiation 9",
        "Manuscript Page", "Game Saved", "Checkpoint")
    val MENU = listOf("Resume", "Mind Place", "Writer's Room", "Inventory", "Map", "Settings", "Options", "Load Game",
        "New Game", "Expansions", "Extras", "Credits", "Quit to Main Menu", "Main Menu", "Case Board", "Profiling", "Plot Board")
}

/** Resident Evil Requiem (2026) – oficiální češtinu nemá; menu z oficiálního manuálu Capcomu. */
internal object ReRequiem {
    val COMMON = listOf(
        "Grace", "Victor", "Emily", "Leon", "Chris", "Sherry", "Nathan", "Norman", "Harry", "Reed", "Cole", "Kennedy",
        "Spencer", "Redfield", "Birkin", "Hunk", "Licker", "Nemesis", "Mr. X", "Super Tyrant", "Umbrella", "Raccoon City",
        "Raccoon Police Department", "BSAA", "DSO", "T-virus", "Connections", "ARK", "Ark", "Requiem", "Redemption",
        "Classic 70", "Matilda IMP", "W870 Police", "RPG-7", "Chunk", "The Girl", "The Chef", "The Commander",
        "Care Center", "Central Hall", "West Wing", "East Wing", "Isolation Ward", "Green Herb", "Red Herb", "Mixed Herb",
        "Ink Ribbon", "Hand Grenade", "Antique Coin", "Mr. Raccoon", "Med Injector", "Water Treatment Facility", "Plant 43",
        "Alyssa", "Dempsey", "Gideon",
    )
    val NAMES = COMMON + listOf(
        "Zeno", "Grace Ashcroft", "Ashcroft", "Alyssa Ashcroft", "Leon S. Kennedy", "Victor Gideon", "Sherry Birkin",
        "Nathan Dempsey", "Dempsy", "Norman Cole", "Harry Reed", "Oswell E. Spencer", "Ozwell E. Spencer", "Chris Redfield",
        "Wrenwood Hotel", "Wrenwood", "Rhodes Hill", "Rhodes Hill Chronic Care Center", "Blackthorn Station",
        "East Raccoon City", "NF Building", "Hound Wolf Squad", "Elpis", "RCS", "Raccoon City Syndrome",
        "Blister Head", "Blister Borne", "Titan Spinner", "Garmr", "S&S M232", "B934", "MSBG 500", "Alligator Snapper",
        "Marksman 1A", "Ghost Grudge", "Mortal Edge", "Freya's Needle", "Kotetsu", "Silencer 9", "990-TAC", "Stiri REVO3 A1",
        "Ink Ribbon Tin", "Hemolytic Injector", "Blood Collector", "Infected Blood Microsamples", "Makeshift Knife",
        "Moon Quartz", "Sun Quartz", "Star Quartz", "Cherub Key", "Leon Must Die Forever", "LMDF",
    )
    val CAST = listOf(
        f("Grace Ashcroft", "Grace", "Ashcroft"), f("Alyssa Ashcroft", "Alyssa"), m("Leon S. Kennedy", "Leon", "Kennedy", "Leon Kennedy"),
        m("Victor Gideon", "Victor", "Gideon", "Dr. Gideon"), f("Sherry Birkin", "Sherry"), f("Emily"), m("Zeno"),
        m("Nathan Dempsey", "Nathan", "Dempsey", "Dempsy"), m("Oswell E. Spencer", "Spencer", "Ozwell"), m("Chris Redfield", "Chris"),
    )
    val TERMS = mapOf(
        "cs" to mapOf("typewriter" to "psací stroj", "safe room" to "úkryt", "save room" to "úkryt"),
        "sk" to mapOf("typewriter" to "písací stroj", "safe room" to "úkryt", "save room" to "úkryt"),
    )
    val HUD = listOf("AUTO 01", "AUTO 02")
    // běžná slova z menu (Use, Open, Fine, Danger…) záměrně ne – jednoslovný řádek repliky by vypadal jako menu
    val MENU = listOf("Main Story", "Load Game", "New Game", "Bonuses", "Options", "Extra Games", "Standard (Modern)",
        "Standard (Classic)", "Insanity", "Unequip", "Attach Parts", "Register Shortcut", "Crafting", "Files", "Map", "Items",
        "Objectives", "Change Floor", "Zoom Out/In", "Current Location", "Unchecked", "Special Content", "Concept Art",
        "Bonus Settings", "Accessibility", "Perspective and Camera", "Photo Mode")
}

/** 007 First Light (IO Interactive, 2026) – oficiální češtinu nemá. Města a země se překládají normálně. */
internal object Bond007 {
    val COMMON = listOf(
        "Bond", "00", "Webb", "Tan", "Roth", "Monroe", "Isola", "Damien", "Valhalla", "Omega", "Aston Martin", "Hyperion",
        "Theia", "Laser Strap", "Smoke Pod", "Smoke Pods", "Flash Mine", "Dart Phone", "Missile Pen", "Shockwave Camera",
        "Whitehall", "Beckett", "Lorca", "Cressida", "007",
    )
    val NAMES = COMMON + listOf(
        "James Bond", "Moneypenny", "Greenway", "John Greenway", "Selina Tan", "Dr. Tan", "Charlotte Roth", "Miss Roth",
        "Ms. Roth", "Bawma", "Theresa Lorca", "Cressida Bright", "Lennox Monroe", "Sir Nicholas Webb", "Sir Nicholas",
        "Nicholas Webb", "Damien Webb", "MI6", "Q Branch", "Q-Branch", "TacSim", "Double-O", "Webb Industries",
        "Grand Carpathian Hotel", "Grand Carpathian", "Pearl Resort", "Aleph", "THEIA", "HYPERION", "Q-Lens", "Q-Watch",
        "Seamaster",
    )
    val CAST = listOf(
        m("James Bond", "Bond", "007", "James"), f("M"), m("Q"), f("Moneypenny"), m("John Greenway", "Greenway"),
        f("Charlotte Roth", "Miss Roth", "Ms. Roth", "Roth", "Isola"), m("Bawma"), f("Theresa Lorca", "Lorca"),
        f("Cressida Bright", "Cressida"), m("Lennox Monroe", "Monroe"), m("Sir Nicholas Webb", "Sir Nicholas", "Webb"),
        m("Damien Webb", "Damien"), m("009"),
    )
}

/** Diablo IV (+ Vessel of Hatred, Lord of Hatred) – oficiální češtinu nemá (PS Store: jen polština aj.). */
internal object Diablo4 {
    val COMMON = listOf(
        "Dry Steppes", "Fractured Peaks", "Tree of Whispers", "Cathedral of Light", "Church of the Light", "Knights Penitent",
        "Burning Hells", "High Heavens", "Pools of Creation", "Sightless Eye", "Cosmic Archives", "Saltwind", "Celestia",
        "Imperial Library", "Firebreak Manor", "Backwater", "Helltide", "Infernal Hordes", "Nightmare Dungeon",
        "Echoing Hatred", "Dark Citadel", "Undercity", "Fell Council", "Prime Evil", "Prime Evils", "Lesser Evils",
        "Hollows", "Wanderer", "Beast in the Ice", "Wandering Death", "Butcher", "Avarice", "Hateborn Abomination",
        "Harlequin Crest", "Shako", "Doombringer", "The Grandfather", "Ring of Starless Skies", "Melted Heart",
        "Mother's Embrace", "Heir of Perdition", "Murmuring Obols", "Red Dust", "Aberrant Cinders", "Horadric Cube",
        "Spiritborn", "Oracle Queen", "Amazon Queen", "Codex of Power",
        "Diablo", "Baal", "Belial", "Azmodan", "Tyrael", "Margrave", "Elias", "Vigo", "Prava", "Eru", "Iosef", "Donan",
        "Amazons", "Westmarch", "Tristram", "Sanctuary", "Lycander", "Philios",
    )
    val NAMES = COMMON + listOf(
        "Kyovashad", "Scosglen", "Kehjistan", "Hawezar", "Nahantu", "Skovos", "Skovos Isles", "Temis", "Cetos", "Athulua",
        "Xanderos", "Atanos", "Skartara", "Ked Bardu", "Cerrigar", "Gea Kul", "Zarbinzet", "Caldeum", "Menestad", "Yelesna",
        "Nevesk", "Braestaig", "Tarsarak", "Jirandai", "Kor Valar", "Kor Dragan", "Tur Dulra", "Alzuuda", "Vyeresz",
        "Khanduras", "Kurast", "Kurast Bazaar", "Kurast Undercity", "Travincal", "Akarat's Tomb", "Arreat", "Mount Arreat",
        "Lilith", "Inarius", "Lorath", "Lorath Nahr", "Neyrelle", "Taissa", "Yorin", "Vhenard", "Nafain", "Timue", "Rathma",
        "Mephisto", "Astaroth", "Duriel", "Andariel", "Varshan", "Grigoire", "Lord Zir", "Ashava", "Urivar", "Akarat",
        "Sankekur", "Adreona", "Shi Yugong", "Zoltun Kulle", "Raheir", "Horadrim", "Zakarum", "Que-Hegan", "Triune",
        "Askari", "Nephalem", "Lilith's Altar", "Tyrael's Might", "Andariel's Visage", "Ahavarion", "Selig", "Nesekem",
        "Godslayer Crown", "Daughter of Hatred", "Lord of Hatred",
    )
    // rod jen u ověřených postav (Vigo, Prava, Vhenard, Nafain, Timue jen jako jména)
    val CAST = listOf(
        f("Lilith", "Daughter of Hatred", "Queen of the Succubi"), m("Inarius"), m("Lorath", "Lorath Nahr"), f("Neyrelle"),
        m("Donan"), m("Iosef"), m("Elias"), m("Rathma"), m("Mephisto", "Lord of Hatred"), m("Astaroth"),
        m("Duriel", "Lord of Pain"), f("Andariel", "Maiden of Anguish"), m("Tyrael"), m("Akarat"), m("Eru"), m("Shi Yugong"),
        f("Adreona", "Amazon Queen"), f("Taissa"), m("Yorin"),
    )
    val TERMS = mapOf(
        "cs" to mapOf("soulstone" to "kámen duší", "waypoint" to "cestovní bod", "stronghold" to "pevnost", "sigil" to "pečeť",
            "elixir" to "elixír"),
        "sk" to mapOf("soulstone" to "kameň duší", "waypoint" to "cestovný bod", "stronghold" to "pevnosť", "sigil" to "pečať",
            "elixir" to "elixír"),
    )
    val HUD = listOf("Stronghold Reclaimed", "Level Up", "Quest Completed", "Dungeon Completed", "Waypoint Discovered",
        "You Died", "Respawn at Checkpoint")
    /** Chat (záznam majitele 20. 11. 2026: „[Trade] TITUS: wtb Greater Lair Key“, OCR i „Trade)“) – nepřekládá se. */
    val SKIP = listOf(Regex("^\\W{0,2}(trade|general|party|clan|whisper|local|group|lfg|warband)\\s*[\\])}|:]", RegexOption.IGNORE_CASE))
    val MENU = listOf("Inventory", "Skill Tree", "Paragon", "Codex of Power", "Collections", "Journal", "Quests",
        "Achievements", "Battle Pass", "Wardrobe", "Options", "Gameplay", "Graphics", "Accessibility", "Controls",
        "Leave Game", "Exit Game", "Change Character", "Create New", "Online Play", "Campaign Mode", "Seasonal Realm",
        "Eternal Realm", "Customer Support")
}
