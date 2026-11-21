package cz.playlingo.core

/**
 * Seznam her jako data (třetí aktualizace vedle aplikace a aplikace v TV): zápis vestavěných profilů do JSON
 * (`pomoc/hry/profily.json` na stránkách s pomocí) a přísné čtení staženého seznamu zpět na [GameCatalog].
 *
 * Tvar (format 1):
 * ```
 * {"format":1,"verze":N,"minAppVerze":156,"bezneSlova":[…],
 *  "hry":[{"id":"gta6","nazev":"Grand Theft Auto VI",
 *          "jmena":[…],                       // nepřekládají se
 *          "bezne":[…],                       // z jmen ta, co jsou i běžná slova (univerzální profil je vynechá)
 *          "postavy":[{"jmeno":"Jason","rod":"m","prezdivky":["Jay",…]}],
 *          "pojmy":{"cs":{"heist":"loupež"}},  // pevný překlad po jazycích
 *          "napisy":[…], "menu":[…],           // hlášky a nápisy menu (nečtou se)
 *          "preskocit":[{"vzor":"^…","velikost":false}],   // regulární výrazy řádků, které se nečtou
 *          "pokyny":true,                      // pokyny pod titulky (GTA)
 *          "barvy":{"Jason":"#FFFFD54F"},
 *          "menuHry":{"polozky":[…],"zalozky":[…],"preklady":{"cs":{…}}},
 *          "rada":"gp_bg_gta6"}]}              // klíč textu rady v aplikaci (jen existující texty)
 * ```
 * Logika (rod, menu, úkol…) zůstává v kódu, seznam nese jen data a příznaky, které aplikace už umí.
 * Čtení nikdy nespadne: neplatná položka se vynechá, neplatný celek = null.
 */
object GameListJson {
    const val FORMAT = 1
    /** Největší seznam, který se přijme (znaků). */
    const val MAX_SIZE = 2_000_000
    private const val MAX_GAMES = 300
    private const val MAX_ITEMS = 5000
    private const val MAX_TEXT = 120
    private const val MAX_REGEX = 200
    private const val MAX_REGEXES = 20
    private val ID = Regex("^[a-z0-9_]{1,32}$")
    private val COLOR = Regex("^#[0-9A-Fa-f]{8}$")

    // ---------- zápis ----------

    /** Celý seznam jako JSON (jedna hra na víc řádků, ať jsou změny v gitu čitelné). */
    fun write(c: GameCatalog, minApp: Int = GameProfiles.MIN_APP): String {
        val b = StringBuilder()
        b.append("{\"format\":").append(FORMAT).append(",\"verze\":").append(c.version)
            .append(",\"minAppVerze\":").append(minApp).append(",\n")
        b.append("\"bezneSlova\":").append(arr(c.extraAmbiguous)).append(",\n")
        b.append("\"hry\":[\n")
        c.games.forEachIndexed { i, g ->
            if (i > 0) b.append(",\n")
            b.append(game(g))
        }
        b.append("\n]}\n")
        return b.toString()
    }

    private fun game(g: GameProfile): String {
        val f = ArrayList<String>()
        f += "\"id\":" + str(g.id)
        f += "\"nazev\":" + str(g.title)
        f += "\"jmena\":" + arr(g.names)
        if (g.common.isNotEmpty()) f += "\"bezne\":" + arr(g.common)
        if (g.characters.isNotEmpty()) f += "\"postavy\":[" + g.characters.joinToString(",") { ch ->
            "{\"jmeno\":" + str(ch.name) + ",\"rod\":" + str(if (ch.gender == Gender.F) "f" else "m") +
                (if (ch.aliases.isNotEmpty()) ",\"prezdivky\":" + arr(ch.aliases) else "") + "}"
        } + "]"
        if (g.terms.isNotEmpty()) f += "\"pojmy\":" + langMap(g.terms)
        if (g.hud.isNotEmpty()) f += "\"napisy\":" + arr(g.hud)
        if (g.menu.isNotEmpty()) f += "\"menu\":" + arr(g.menu)
        if (g.skip.isNotEmpty()) f += "\"preskocit\":[" + g.skip.joinToString(",") { r ->
            "{\"vzor\":" + str(r.pattern) + ",\"velikost\":" + (RegexOption.IGNORE_CASE !in r.options) + "}"
        } + "]"
        if (g.prompts) f += "\"pokyny\":true"
        if (g.colors.isNotEmpty()) f += "\"barvy\":{" + g.colors.entries.joinToString(",") { (k, v) ->
            str(k) + ":" + str("#" + String.format(java.util.Locale.ROOT, "%08X", v))
        } + "}"
        g.menuSpec?.let { m ->
            f += "\"menuHry\":{\"polozky\":" + arr(m.pause) + ",\"zalozky\":" + arr(m.tabs) +
                (if (m.words.isNotEmpty()) ",\"preklady\":" + langMap(m.words) else "") + "}"
        }
        g.hint?.let { f += "\"rada\":" + str(it) }
        return "{" + f.joinToString(",\n ") + "}"
    }

    private fun langMap(m: Map<String, Map<String, String>>): String = "{" + m.entries.joinToString(",") { (lang, words) ->
        str(lang) + ":{" + words.entries.joinToString(",") { (k, v) -> str(k) + ":" + str(v) } + "}"
    } + "}"

    private fun arr(l: Collection<String>): String = l.joinToString(",", "[", "]") { str(it) }

    private fun str(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\t' -> b.append("\\t")
            c == '\r' -> b.append("\\r")
            c < ' ' -> b.append(String.format("\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    // ---------- čtení ----------

    /** Výsledek čtení: seznam, nebo důvod, proč ne (do záznamu, hráči se neukazuje). */
    class Result(val catalog: GameCatalog?, val problem: String?, val version: Int = 0)

    /** Jen verze z `verze.txt` (jedno číslo), nebo null. */
    fun versionOf(text: String): Int? = text.trim().takeIf { it.length <= 9 }?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * Přečte stažený seznam. [appVersion] = versionCode aplikace (seznam pro novější aplikaci se nevezme),
     * [hasText] = existuje text aplikace s tímto klíčem (rada u hry); null = rady se neověřují.
     */
    fun parse(text: String, appVersion: Int, hasText: ((String) -> Boolean)? = { I18n.has(it) }): Result {
        if (text.length > MAX_SIZE) return Result(null, "size")
        val root = try { MiniJson.parse(text) as? Map<*, *> } catch (_: Throwable) { null } ?: return Result(null, "json")
        val format = (root["format"] as? Long)?.toInt() ?: return Result(null, "format")
        if (format != FORMAT) return Result(null, "format $format")
        val version = (root["verze"] as? Long)?.takeIf { it in 1..999_999_999 }?.toInt() ?: return Result(null, "verze")
        val minApp = (root["minAppVerze"] as? Long)?.toInt() ?: 0
        if (minApp > appVersion) return Result(null, "app $minApp", version)
        val list = root["hry"] as? List<*> ?: return Result(null, "hry", version)
        val games = ArrayList<GameProfile>()
        val ids = HashSet<String>()
        for (item in list.take(MAX_GAMES)) {
            val g = try { game(item as? Map<*, *> ?: continue, hasText) } catch (_: Throwable) { null } ?: continue
            if (ids.add(g.id)) games += g
        }
        if (games.isEmpty()) return Result(null, "prazdny", version)
        val extra = texts(root["bezneSlova"])
        return try { Result(GameCatalog(games, extra, version, downloaded = true), null, version) }
        catch (_: Throwable) { Result(null, "katalog", version) }
    }

    private fun game(o: Map<*, *>, hasText: ((String) -> Boolean)?): GameProfile? {
        val id = (o["id"] as? String)?.takeIf { ID.matches(it) && it != "all" } ?: return null
        val title = text(o["nazev"]) ?: return null
        val names = texts(o["jmena"])
        val chars = (o["postavy"] as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { c ->
            val m = c as? Map<*, *> ?: return@mapNotNull null
            val name = text(m["jmeno"]) ?: return@mapNotNull null
            val gender = when (m["rod"]) { "m" -> Gender.M; "f" -> Gender.F; else -> return@mapNotNull null }
            GameCharacter(name, gender, texts(m["prezdivky"]).take(30))
        }
        val skip = (o["preskocit"] as? List<*>).orEmpty().take(MAX_REGEXES).mapNotNull { r ->
            val m = r as? Map<*, *> ?: return@mapNotNull null
            val p = (m["vzor"] as? String)?.takeIf { it.isNotEmpty() && it.length <= MAX_REGEX } ?: return@mapNotNull null
            try { if (m["velikost"] == true) Regex(p) else Regex(p, RegexOption.IGNORE_CASE) } catch (_: Throwable) { null }
        }
        val colors = LinkedHashMap<String, Int>()
        (o["barvy"] as? Map<*, *>)?.entries?.take(100)?.forEach { (k, v) ->
            val name = text(k) ?: return@forEach
            val hex = (v as? String)?.takeIf { COLOR.matches(it) } ?: return@forEach
            colors[name] = hex.substring(1).toLong(16).toInt()
        }
        val menuSpec = (o["menuHry"] as? Map<*, *>)?.let { m ->
            val pause = texts(m["polozky"]).take(40)
            val tabs = texts(m["zalozky"]).take(40)
            if (pause.isEmpty() && tabs.isEmpty()) null else MenuSpec(pause, tabs, langMap(m["preklady"]))
        }
        val hint = (o["rada"] as? String)?.takeIf { h ->
            ID.matches(h) && (hasText == null || (hasText(h) && hasText(h + "_val") && hasText(h + "_desc")))
        }
        return GameProfile(id, title, names, chars, langMap(o["pojmy"]), texts(o["napisy"]), texts(o["menu"]), skip,
            prompts = o["pokyny"] == true, colors = colors, menuSpec = menuSpec, hint = hint,
            common = texts(o["bezne"]).filter { it in names })
    }

    private val LANGS = Lang.values().map { it.code }.toSet()

    private fun langMap(v: Any?): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        (v as? Map<*, *>)?.forEach { (lang, words) ->
            if (lang !in LANGS) return@forEach
            val m = LinkedHashMap<String, String>()
            (words as? Map<*, *>)?.entries?.take(MAX_ITEMS)?.forEach { (k, t) ->
                val en = text(k) ?: return@forEach
                m[en] = text(t) ?: return@forEach
            }
            if (m.isNotEmpty()) out[lang as String] = m
        }
        return out
    }

    private fun text(v: Any?): String? = (v as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT && it.none { c -> c < ' ' } }

    private fun texts(v: Any?): List<String> = (v as? List<*>).orEmpty().take(MAX_ITEMS).mapNotNull { text(it) }
}
