package cz.playlingo.core

/**
 * Jazyk. Slouží dvěma nastavením, která jsou na sobě nezávislá:
 *  - **jazyk překladu** – do čeho se titulky překládají ([targets]; kód = kód jazyka offline
 *    překladače Google ML Kit),
 *  - **jazyk aplikace** – v čem jsou nabídky a nápověda (všechny hodnoty včetně angličtiny).
 * Angličtina je jen jazyk aplikace: titulky jsou anglicky, takže do ní se nepřekládá.
 * Nové jazyky překladu, pro které aplikace ještě nemá texty, patří jen do [targets] – jazyk
 * aplikace pak pro ně zůstane anglicky.
 */
enum class Lang(val code: String, val label: String) {
    CS("cs", "Čeština"),
    SK("sk", "Slovenčina"),
    PL("pl", "Polski"),
    DE("de", "Deutsch"),
    HU("hu", "Magyar"),
    UK("uk", "Українська"),
    ES("es", "Español"),
    FR("fr", "Français"),
    IT("it", "Italiano"),
    EN("en", "English");

    /** Dá se do tohoto jazyka překládat? */
    val isTarget: Boolean get() = this != EN

    /** Další jazyk aplikace v pořadí (přepínání jedním tlačítkem). */
    fun next(): Lang = values()[(ordinal + 1) % values().size]

    companion object {
        /** Jazyky, do kterých se překládá. */
        val targets: List<Lang> get() = values().filter { it.isTarget }

        /** Jazyk překladu podle kódu; neznámý kód (i angličtina) = čeština. */
        fun of(code: String?): Lang = targets.firstOrNull { it.code == code } ?: CS

        /** Jazyk aplikace podle kódu; null, když pro něj aplikace texty nemá. */
        fun ui(code: String?): Lang? = values().firstOrNull { it.code == code }
    }
}
