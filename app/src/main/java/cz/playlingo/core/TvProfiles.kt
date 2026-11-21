package cz.playlingo.core

/**
 * Vzhled pruhu a oblasti titulků zvlášť pro každou televizi LG / Samsung (bez Androidu, s testy).
 *
 * Nic se nevybírá: aktivní je vždy televize, která se naposledy ohlásila (platforma + model).
 * Při přechodu na jinou televizi se dosavadní hodnoty uloží k té předchozí a načtou se hodnoty
 * nové; televize, která ještě nic uloženého nemá, začne s kopií posledního nastavení.
 * Ukládání do SharedPreferences dělá `TvProfile` v Android části.
 */
object TvProfiles {
    /** Klíče nastavení, která patří k televizi (vzhled pruhu, oblasti, co se čte). Ostatní jsou společná. */
    val KEYS = listOf(
        "text_color", "bold_text", "text_size", "match_size", "opacity", "show_original", "orig_size", "obj_on", "obj_size", "obj_pos",
        "colored_speaker", "hold_ms", "layout", "auto_zone", "only_subs", "bar_pad",
    )

    /** Rozpoznání televize: „webos:OLED55B7“. Bez modelu se televize nerozliší → null (vše společné). */
    fun id(a: TvAnnounce): String? {
        val platform = a.platform ?: return null
        val model = a.model?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return platform + ":" + model.replace(Regex("\\s+"), " ").take(60)
    }

    /** Název pro hráče: „LG OLED55B7“, „Samsung QE55Q80“. */
    fun display(id: String): String {
        val platform = id.substringBefore(':')
        val model = id.substringAfter(':', "")
        val brand = when (platform) { TvAnnounce.WEBOS -> "LG"; TvAnnounce.TIZEN -> "Samsung"; else -> platform }
        return if (model.isEmpty()) brand else "$brand $model"
    }

    /**
     * Co udělat, když se ohlásila televize [next] a aktivní je [active]:
     * null = nic (stejná televize), jinak [Switch] – uložit dosavadní hodnoty k [Switch.saveTo]
     * (null = nebylo kam, první televize) a načíst uložené hodnoty nové televize, pokud nějaké má.
     */
    fun decide(active: String?, next: String?): Switch? {
        if (next == null || next == active) return null
        return Switch(saveTo = active, loadFrom = next)
    }

    data class Switch(val saveTo: String?, val loadFrom: String)
}
