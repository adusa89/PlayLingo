package cz.playlingo.core

/**
 * Nastavení Android TV z telefonu (návrh `navrhy/nastaveni-z-telefonu.md`): co se mezi telefonem
 * a televizí přenáší a v jakém tvaru. Bez Androidu, aby šlo vyzkoušet v unit testech.
 *
 * Přenáší se jen nastavení hry a titulků z hlavního souboru nastavení (`Prefs`), nikdy věci vázané
 * na zařízení (typ zařízení, statistiky, licence, tlačítko vzhledu z ovladače TV…). Klíč DeepL jde
 * zvlášť ([OP_DEEPL]) – v televizi se ukládá zašifrovaný jako dosud.
 *
 * Hodnoty nesou typ, aby se v televizi uložily stejně jako v telefonu (SharedPreferences rozlišuje
 * Int / Long / Boolean / Float / String / sadu textů): `{"text_size":["i",22],"bold_text":["b",true]}`.
 * Smazaná volba = `["x",null]`.
 */
object RemoteSetup {
    /** Verze zpráv; televize se starší verzí odpoví [ERR_VERSION] a telefon poradí aktualizaci. */
    const val VERSION = 1

    /** Operace (pole `op` ve formuláři na stránku z televize). */
    const val OP_SNAPSHOT = "stav"      // telefon chce celé nastavení TV
    const val OP_SET = "zmen"           // telefon posílá změněné volby (pole `data`)
    const val OP_DEEPL = "deepl"        // telefon posílá klíč DeepL (pole `key`)
    const val ERR_VERSION = "VERZE"
    const val ERR_BAD = "BAD"

    /** Volby, které se přenášejí přesně podle jména. */
    private val KEYS = setOf(
        // jazyky
        "lang", "src_lang", "ui_lang",
        // konzole a spárování (spárování PS5 se přenáší celé – viz návrh, rozhodnutí 1)
        "console", "ps5_host", "ps5_ip", "ps5_login_pin", "ps5_wake2", "psn_id", "psn_name",
        // hra
        "game", "game_menu", "game_menu_volba", "game_prompts", "game_texts", "game_guess_no", "hry_auto",
        // písmo, pruh, oblasti, čtení
        "text_size", "text_color", "bold_text", "opacity", "orig_sp", "bar_pad", "show_original",
        "colored_speaker", "cover_all", "only_subs", "match_size", "min_show_ms", "hold_ms",
        "auto_zone", "layout", "read_quality", "fast_read", "stav_pres_hru", "stav_rezim",
        // průvodce je hotový (projitý v telefonu)
        "wizard_done",
    )

    /** Skupiny voleb podle začátku jména (úkol pod titulky `obj_…`, vlastní slovník `dict_<jazyk>`). */
    private val PREFIXES = listOf("obj_", "dict_")

    /** Patří volba mezi přenášené? Vše ostatní zůstává jen v zařízení, kde vzniklo. */
    fun transfers(key: String): Boolean = key in KEYS || PREFIXES.any { key.startsWith(it) && key.length > it.length }

    /** Jen přenášené volby (ze všech uložených v zařízení). */
    fun filter(all: Map<String, *>): Map<String, Any?> = all.filterKeys(::transfers)

    /** Co se změnilo z [before] na [after] (jen přenášené volby; smazaná volba = null). */
    fun diff(before: Map<String, *>, after: Map<String, *>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in after) if (transfers(k) && !same(before[k], v)) out[k] = v
        for (k in before.keys) if (transfers(k) && k !in after) out[k] = null
        return out
    }

    private fun same(a: Any?, b: Any?): Boolean = a == b

    // ---- zápis a čtení ----

    fun encode(values: Map<String, Any?>): String = buildString {
        append('{')
        var first = true
        for ((k, v) in values) {
            if (!transfers(k)) continue
            val typed = typed(v) ?: continue
            if (!first) append(',')
            first = false
            str(k); append(":["); str(typed.first); append(','); append(typed.second); append(']')
        }
        append('}')
    }

    /** Typ a hodnota jako text JSON; null = typ, který se nepřenáší. */
    private fun typed(v: Any?): Pair<String, String>? = when (v) {
        null -> "x" to "null"
        is Boolean -> "b" to v.toString()
        is Int -> "i" to v.toString()
        is Long -> "l" to v.toString()
        is Float -> if (v.isFinite()) "f" to v.toString() else null
        is String -> "s" to json(v)
        is Set<*> -> "t" to v.filterIsInstance<String>().sorted().joinToString(",", "[", "]") { json(it) }
        else -> null
    }

    /** Zpráva z druhé strany → volby s typem; null, když zpráva není platná. Nepřenášené volby se zahodí. */
    fun decode(text: String): Map<String, Any?>? {
        val obj = MiniJson.obj(text) ?: return null
        val out = LinkedHashMap<String, Any?>()
        for ((k, raw) in obj) {
            if (!transfers(k)) continue
            val pair = raw as? List<*> ?: return null
            if (pair.size != 2) return null
            val t = pair[0] as? String ?: return null
            val v = pair[1]
            out[k] = when (t) {
                "x" -> null
                "b" -> v as? Boolean ?: return null
                "i" -> (v as? Number)?.toLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: return null
                "l" -> (v as? Number)?.toLong() ?: return null
                "f" -> (v as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: return null
                "s" -> v as? String ?: return null
                "t" -> (v as? List<*>)?.map { it as? String ?: return null }?.toSet() ?: return null
                else -> return null
            }
        }
        return out
    }

    /** Celé nastavení televize pro telefon: verze, jméno TV, volby a jestli má TV klíč DeepL. */
    fun snapshot(tvName: String, values: Map<String, *>, hasDeeplKey: Boolean): String =
        """{"v":$VERSION,"name":${json(tvName)},"deepl":$hasDeeplKey,"prefs":${encode(filter(values))}}"""

    data class Snapshot(val version: Int, val tvName: String, val hasDeeplKey: Boolean, val values: Map<String, Any?>)

    fun parseSnapshot(text: String): Snapshot? {
        val obj = MiniJson.obj(text) ?: return null
        val v = (obj["v"] as? Number)?.toInt() ?: return null
        val prefs = obj["prefs"] as? Map<*, *> ?: return null
        val values = decode(reencode(prefs)) ?: return null
        return Snapshot(v, obj["name"] as? String ?: "", obj["deepl"] == true, values)
    }

    /** Vnořený objekt z [MiniJson] zpět na text, ať ho přečte [decode] (jediné místo s kontrolou typů). */
    private fun reencode(m: Map<*, *>): String = buildString {
        append('{')
        m.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            str(k.toString()); append(':'); append(any(v))
        }
        append('}')
    }

    private fun any(v: Any?): String = when (v) {
        null -> "null"
        is String -> json(v)
        is Boolean, is Number -> v.toString()
        is List<*> -> v.joinToString(",", "[", "]") { any(it) }
        is Map<*, *> -> reencode(v)
        else -> json(v.toString())
    }

    private fun StringBuilder.str(s: String) { append(json(s)) }

    fun json(s: String): String = buildString {
        append('"')
        for (ch in s) when (ch) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (ch < ' ') append(String.format("\\u%04x", ch.code)) else append(ch)
        }
        append('"')
    }
}
