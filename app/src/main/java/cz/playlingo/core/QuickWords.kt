package cz.playlingo.core

/**
 * Krátké repliky o jednom dvou slovech ("Wait", "Come on!"). Překladač je bez souvislosti
 * překládá špatně, proto mají pro češtinu a slovenštinu pevný překlad. Ostatní jazyky
 * a všechno ostatní jde běžným překladem.
 */
object QuickWords {
    private val CS = mapOf(
        "wait" to "Počkej", "run" to "Utíkej", "go" to "Běž", "yes" to "Ano", "no" to "Ne", "okay" to "Dobře",
        "ok" to "Dobře", "thanks" to "Díky", "thank you" to "Děkuju", "sorry" to "Promiň", "what" to "Cože",
        "hello" to "Ahoj", "hi" to "Ahoj", "hey" to "Hej", "help" to "Pomoc", "stop" to "Stůj", "look" to "Podívej",
        "come on" to "No tak", "hurry" to "Pospěš si", "hurry up" to "Pospěš si", "please" to "Prosím",
        "never" to "Nikdy", "maybe" to "Možná", "why" to "Proč", "where" to "Kde", "now" to "Teď", "here" to "Tady",
        "listen" to "Poslouchej", "careful" to "Opatrně", "quiet" to "Ticho", "move" to "Pohyb", "jump" to "Skoč",
        "enough" to "Dost", "really" to "Vážně", "sure" to "Jasně", "right" to "Dobře", "fine" to "Fajn",
        "great" to "Skvěle", "good" to "Dobře", "let's go" to "Jdeme", "watch out" to "Pozor", "look out" to "Pozor",
        "of course" to "Samozřejmě", "i know" to "Já vím", "got it" to "Rozumím", "goodbye" to "Sbohem", "bye" to "Ahoj",
    )
    private val SK = mapOf(
        "wait" to "Počkaj", "run" to "Utekaj", "go" to "Choď", "yes" to "Áno", "no" to "Nie", "okay" to "Dobre",
        "ok" to "Dobre", "thanks" to "Vďaka", "thank you" to "Ďakujem", "sorry" to "Prepáč", "what" to "Čože",
        "hello" to "Ahoj", "hi" to "Ahoj", "hey" to "Hej", "help" to "Pomoc", "stop" to "Stoj", "look" to "Pozri",
        "come on" to "No tak", "hurry" to "Ponáhľaj sa", "hurry up" to "Ponáhľaj sa", "please" to "Prosím",
        "never" to "Nikdy", "maybe" to "Možno", "why" to "Prečo", "where" to "Kde", "now" to "Teraz", "here" to "Tu",
        "listen" to "Počúvaj", "careful" to "Opatrne", "quiet" to "Ticho", "move" to "Pohyb", "jump" to "Skoč",
        "enough" to "Dosť", "really" to "Vážne", "sure" to "Jasné", "right" to "Dobre", "fine" to "Fajn",
        "great" to "Skvelé", "good" to "Dobre", "let's go" to "Ideme", "watch out" to "Pozor", "look out" to "Pozor",
        "of course" to "Samozrejme", "i know" to "Ja viem", "got it" to "Rozumiem", "goodbye" to "Zbohom", "bye" to "Ahoj",
    )

    /** Je to běžná krátká replika ("wait", "come on")? Podle toho se pozná replika od nápisu nebo šumu. */
    fun isKnown(text: String): Boolean = text.trim().trimEnd('.', '!', '?', '…', ',').lowercase() in CS

    /** Pevný překlad krátké repliky, nebo null. Interpunkce na konci ("Wait!") se zachová. */
    fun lookup(langCode: String, text: String): String? {
        val table = when (langCode) { "cs" -> CS; "sk" -> SK; else -> return null }
        val t = text.trim()
        val core = t.trimEnd('.', '!', '?', '…', ',')
        if (core.isEmpty() || core.length > 12) return null
        val hit = table[core.lowercase()] ?: return null
        return hit + t.substring(core.length)
    }
}
