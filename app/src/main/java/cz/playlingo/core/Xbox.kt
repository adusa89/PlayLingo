package cz.playlingo.core

/*
 * Xbox (Series X|S / One) jako druhá konzole: přihlášení účtem Microsoft a spojení „vzdálené hraní“
 * (Xbox Remote Play, xHome, WebRTC) – jen kvůli kopii obrazu pro čtení titulků.
 *
 * Tady je jen logika bez Androidu a bez sítě (čtení odpovědí, rozhodování, úpravy SDP), s testy
 * v app/src/test. Síť a WebRTC jsou v XboxAccount.kt a XboxLink.kt.
 *
 * Postup podle projektu PlayCaption (XboxUcet.kt, XboxSpojeni.kt; AGPL-3.0), který vychází
 * z otevřených projektů xbox-xcloud-player (unknownskl, MIT) a XStreaming (Geocld, AGPL-3.0).
 */

/** Na které konzoli se hraje. Výchozí je PlayStation (dosavadní uživatelé o nic nepřijdou). */
enum class Console(val code: String) {
    PLAYSTATION("ps"), XBOX("xbox");

    /** Klíč textu pro tuto konzoli: u Xboxu vlastní znění „xb_<klíč>“, pokud existuje (core/I18nXbox.kt). */
    fun key(k: String): String = if (this == XBOX && I18n.has("xb_$k")) "xb_$k" else k

    companion object {
        fun of(code: String?): Console = values().firstOrNull { it.code == code } ?: PLAYSTATION
    }
}

/** Čtení odpovědí přihlášení Microsoft / Xbox Live a rozhodování o platnosti tokenů. */
object XboxAuth {
    /** Veřejné id aplikace Xbox (stejné používají xbox-xcloud-player i XStreaming). */
    const val CLIENT_ID = "1f907974-e22b-4810-a9de-d9647380c97e"
    const val SCOPE = "xboxlive.signin openid profile offline_access"
    const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    const val USER_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
    const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    const val XHOME_LOGIN_URL = "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user"
    const val TRANSFER_URL = "https://login.live.com/oauth20_token.srf"
    const val TRANSFER_SCOPE = "service::http://Passport.NET/purpose::PURPOSE_XBOX_CLOUD_CONSOLE_TRANSFER_TOKEN"
    const val DEFAULT_LINK = "https://www.microsoft.com/link"

    /** Token se považuje za neplatný už tolik před koncem platnosti (ať nevyprší uprostřed požadavku). */
    const val EXPIRY_MARGIN_MS = 2 * 60_000L

    /** Kód pro stránku microsoft.com/link. [expiresAt] a časy jsou v ms (čas dodává volající). */
    data class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val intervalS: Int,
        val expiresAt: Long,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.str(k: String): String? = (this[k] as? String)?.takeIf { it.isNotEmpty() }
    private fun Map<String, Any?>.long(k: String): Long? = when (val v = this[k]) {
        is Number -> v.toLong(); is String -> v.toLongOrNull(); else -> null
    }

    fun parseDeviceCode(json: String, now: Long): DeviceCode? {
        val o = MiniJson.obj(json) ?: return null
        val user = o.str("user_code") ?: return null
        val device = o.str("device_code") ?: return null
        val uri = o.str("verification_uri")?.takeIf { it.startsWith("https://") } ?: DEFAULT_LINK
        return DeviceCode(
            userCode = user, verificationUri = uri, deviceCode = device,
            intervalS = (o.long("interval") ?: 5L).toInt().coerceIn(1, 15),
            expiresAt = now + (o.long("expires_in") ?: 900L).coerceIn(60L, 3600L) * 1000,
        )
    }

    /** Adresa stránky bez „https://www.“ – kratší na obrazovku. */
    fun shortLink(uri: String): String = uri.removePrefix("https://").removePrefix("www.").trimEnd('/')

    enum class Poll { PENDING, SLOW_DOWN, EXPIRED, DENIED, FAILED }

    /** Co znamená odpověď „ještě ne“ při čekání na zadání kódu. */
    fun pollState(json: String): Poll = when (MiniJson.obj(json)?.str("error")) {
        null, "authorization_pending" -> Poll.PENDING
        "slow_down" -> Poll.SLOW_DOWN
        "expired_token", "code_expired" -> Poll.EXPIRED
        "authorization_declined", "access_denied" -> Poll.DENIED
        else -> Poll.FAILED
    }

    /** Tokeny Microsoft. [refresh] může chybět (pak platí dosavadní). */
    data class MsTokens(val access: String, val refresh: String?, val expiresAt: Long)

    fun parseTokens(json: String, now: Long): MsTokens? {
        val o = MiniJson.obj(json) ?: return null
        val access = o.str("access_token") ?: return null
        return MsTokens(access, o.str("refresh_token"), now + (o.long("expires_in") ?: 3600L).coerceAtLeast(60L) * 1000)
    }

    /** Platí token ještě (s rezervou [EXPIRY_MARGIN_MS])? */
    fun valid(expiresAt: Long, now: Long): Boolean = expiresAt > 0 && now < expiresAt - EXPIRY_MARGIN_MS

    enum class Refresh { OK, SIGNED_OUT, RETRY }

    /**
     * Výsledek obnovy tokenu. Odhlásit se smí jen tehdy, když Microsoft výslovně řekne, že přihlášení
     * neplatí (invalid_grant) – výpadek sítě nebo chyba serveru uživatele odhlásit nesmí.
     */
    fun refreshOutcome(status: Int, json: String): Refresh = when {
        status == 200 && MiniJson.obj(json)?.str("access_token") != null -> Refresh.OK
        status in 400..401 && MiniJson.obj(json)?.str("error") in setOf("invalid_grant", "interaction_required", "unauthorized_client") -> Refresh.SIGNED_OUT
        else -> Refresh.RETRY
    }

    /** Token uživatele Xbox Live (user/authenticate). */
    fun parseUserToken(json: String): String? = MiniJson.obj(json)?.str("Token")

    data class Xsts(val token: String, val gamertag: String?)

    @Suppress("UNCHECKED_CAST")
    fun parseXsts(json: String): Xsts? {
        val o = MiniJson.obj(json) ?: return null
        val token = o.str("Token") ?: return null
        val xui = ((o["DisplayClaims"] as? Map<String, Any?>)?.get("xui") as? List<Any?>)?.firstOrNull() as? Map<String, Any?>
        return Xsts(token, xui?.str("gtg"))
    }

    /** Účet Microsoft bez profilu Xbox (Xbox Live vrací 401 s tímto číslem chyby). */
    const val XERR_NO_PROFILE = 2148916233L
    /** Dětský účet bez souhlasu dospělého. */
    const val XERR_CHILD = 2148916238L

    fun xstsError(status: Int, json: String): XboxFail = when (MiniJson.obj(json)?.long("XErr")) {
        XERR_NO_PROFILE, XERR_CHILD -> XboxFail.PROFILE
        else -> if (status == 401 || status == 403) XboxFail.AUTH else XboxFail.NETWORK
    }

    /** Přístup ke streamování z vlastní konzole: token a adresa regionu. */
    data class Stream(val gsToken: String, val baseUri: String, val expiresAt: Long)

    @Suppress("UNCHECKED_CAST")
    fun parseStream(json: String, now: Long): Stream? {
        val o = MiniJson.obj(json) ?: return null
        val token = o.str("gsToken") ?: return null
        val regions = ((o["offeringSettings"] as? Map<String, Any?>)?.get("regions") as? List<Any?>)
            ?.mapNotNull { it as? Map<String, Any?> } ?: return null
        val pick = regions.firstOrNull { it["isDefault"] == true } ?: regions.firstOrNull() ?: return null
        val base = pick.str("baseUri")?.trimEnd('/')?.takeIf { it.startsWith("https://") } ?: return null
        return Stream(token, base, now + (o.long("durationInSeconds") ?: 3600L).coerceAtLeast(60L) * 1000)
    }

    data class XboxConsole(val id: String, val name: String, val powerState: String) {
        /** Konzole je zapnutá (ve spánku se spojení nezačíná – spuštění relace by ji probudilo). */
        val isOn: Boolean get() = powerState.equals("On", ignoreCase = true)
    }

    @Suppress("UNCHECKED_CAST")
    fun parseConsoles(json: String): List<XboxConsole>? {
        val o = MiniJson.obj(json) ?: return null
        val list = o["results"] as? List<Any?> ?: return emptyList()
        return list.mapNotNull { it as? Map<String, Any?> }.mapNotNull { m ->
            val id = m.str("serverId") ?: return@mapNotNull null
            XboxConsole(id, m.str("deviceName") ?: "Xbox", m.str("powerState") ?: "")
        }
    }
}

/** Co se při spojení s Xboxem může pokazit (podle toho se volí text pro hráče a další pokus). */
enum class XboxFail {
    /** Přihlášení už neplatí – je potřeba se přihlásit znovu. */
    AUTH,
    /** Účet Microsoft nemá profil Xbox (nebo je to dětský účet bez souhlasu). */
    PROFILE,
    /** Vybraný Xbox na účtu není. */
    NOT_FOUND,
    /** Xbox je vypnutý nebo spí. */
    OFFLINE,
    /** Xbox nemá zapnuté Vzdálené funkce. */
    NOT_READY,
    REFUSED,
    TIMEOUT,
    NETWORK,
    /** Spojení obrazu po domácí síti se nepodařilo (ICE). */
    ICE,
    /** Xbox vzdálené hraní sám ukončil. */
    KICKED,
    /** Spojeno, ale obraz nechodí. */
    NO_VIDEO;

    /** Klíč textu pro hráče (core/I18nXbox.kt). */
    val textKey: String get() = "xb_e_" + name.lowercase()
}

/** Relace vzdáleného hraní: stavy z /state, výměna SDP a ICE. */
object XboxSession {
    enum class Step { WAIT, CONNECT, READY, FAILED }

    data class State(val step: Step, val error: String?)

    @Suppress("UNCHECKED_CAST")
    fun state(json: String): State {
        val o = MiniJson.obj(json) ?: return State(Step.WAIT, null)
        return when (o["state"] as? String) {
            "Provisioned" -> State(Step.READY, null)
            "ReadyToConnect" -> State(Step.CONNECT, null)
            "Failed" -> State(Step.FAILED, ((o["errorDetails"] as? Map<String, Any?>)?.get("message") as? String) ?: "")
            else -> State(Step.WAIT, null)
        }
    }

    /** Chyba relace z textu Xboxu. */
    fun failOf(message: String?): XboxFail {
        val m = (message ?: "").lowercase()
        return when {
            "register" in m -> XboxFail.NOT_READY
            "offline" in m || "poweredoff" in m || "standby" in m -> XboxFail.OFFLINE
            "auth" in m || "token" in m -> XboxFail.AUTH
            else -> XboxFail.REFUSED
        }
    }

    /** Odpověď na výměnu (sdp / ice): null = ještě není. */
    fun exchangeResponse(json: String): String? = (MiniJson.obj(json)?.get("exchangeResponse") as? String)?.takeIf { it.isNotEmpty() }

    @Suppress("UNCHECKED_CAST")
    fun exchangeError(json: String): String? = ((MiniJson.obj(json)?.get("errorDetails") as? Map<String, Any?>)?.get("message") as? String)

    data class SdpAnswer(val ok: Boolean, val sdp: String?)

    fun sdpAnswer(exchange: String): SdpAnswer {
        val o = MiniJson.obj(exchange) ?: return SdpAnswer(false, null)
        val status = o["status"] as? String ?: "success"
        val sdp = (o["sdp"] as? String)?.takeIf { it.isNotEmpty() }
        return SdpAnswer(status == "success" && sdp != null, sdp)
    }

    fun keepAliveSeconds(json: String): Int {
        val v = MiniJson.obj(json)?.get("keepAlivePulseInSeconds")
        return ((v as? Number)?.toInt() ?: 20).coerceIn(10, 120)
    }

    data class Candidate(val mid: String, val index: Int, val text: String)

    /** Kandidáti ICE od Xboxu; k adresám Teredo přidá i adresy IPv4, které v nich jsou schované. */
    @Suppress("UNCHECKED_CAST")
    fun remoteCandidates(exchange: String): List<Candidate> {
        val list = runCatching { MiniJson.parse(exchange) as? List<Any?> }.getOrNull() ?: return emptyList()
        val out = ArrayList<Candidate>()
        for (m in list.mapNotNull { it as? Map<String, Any?> }) {
            val text = (m["candidate"] as? String)?.trim() ?: continue
            if (text.isEmpty() || text == "a=end-of-candidates") continue
            val mid = (m["sdpMid"] as? String) ?: m["sdpMid"]?.toString() ?: "0"
            val idx = when (val v = m["sdpMLineIndex"]) {
                is Number -> v.toInt(); is String -> v.toDoubleOrNull()?.toInt() ?: 0; else -> 0
            }
            for (t in teredo(text)) out.add(Candidate(mid, idx, t))
            out.add(Candidate(mid, idx, text))
        }
        return out
    }

    /**
     * Xbox často hlásí adresu Teredo (IPv6 2001:0::/32). Je v ní schovaná adresa serveru Teredo
     * a (bitově převrácená) veřejná adresa a port Xboxu – jako XStreaming z ní uděláme kandidáty IPv4.
     */
    fun teredo(candidate: String): List<String> {
        val t = candidate.trim().split(Regex("\\s+"))
        if (t.size < 6) return emptyList()
        val b = ipv6(t[4]) ?: return emptyList()
        if (b[0] != 0x20 || b[1] != 0x01 || b[2] != 0 || b[3] != 0) return emptyList()
        val server = "${b[4]}.${b[5]}.${b[6]}.${b[7]}"
        val client = "${b[12] xor 255}.${b[13] xor 255}.${b[14] xor 255}.${b[15] xor 255}"
        val clientPort = ((b[10] shl 8) or b[11]) xor 0xFFFF
        val port = t[5].toIntOrNull() ?: 9002
        return listOf(
            "a=candidate:10 1 UDP 100 $client $clientPort typ host ",
            "a=candidate:11 1 UDP 100 $client $port typ host ",
            "a=candidate:12 1 UDP 100 $server $port typ host ",
        )
    }

    /** Adresa IPv6 zapsaná textem → 16 bajtů (0..255); null, když to adresa IPv6 není. */
    fun ipv6(s: String): IntArray? {
        if (':' !in s || s.count { it == ':' } > 7 || "%" in s) return null
        val halves = s.split("::")
        if (halves.size > 2) return null
        fun groups(p: String) = if (p.isEmpty()) emptyList() else p.split(":")
        val head = groups(halves[0])
        val tail = if (halves.size == 2) groups(halves[1]) else emptyList()
        val missing = 8 - head.size - tail.size
        if (halves.size == 1 && missing != 0 || missing < 0) return null
        val all = head + List(if (halves.size == 2) missing else 0) { "0" } + tail
        if (all.size != 8) return null
        val out = IntArray(16)
        for ((i, g) in all.withIndex()) {
            if (g.isEmpty() || g.length > 4) return null
            val v = g.toIntOrNull(16) ?: return null
            out[i * 2] = v shr 8; out[i * 2 + 1] = v and 255
        }
        return out
    }

    /** Datový tok obrazu (kb/s): titulkům stačí málo a slabší televize pak víc stíhá číst. */
    const val BITRATE_KBPS = 3000

    /**
     * Nabídka SDP: H.264 na první místo (Xbox posílá H.264, high → baseline) a nižší datový tok
     * obrazu (řádek b=AS v části m=video). Ostatní části se nemění.
     */
    fun preferH264(sdp: String, bitrateKbps: Int = BITRATE_KBPS): String {
        val nl = if ("\r\n" in sdp) "\r\n" else "\n"
        val lines = sdp.split(nl).toMutableList()
        val m = lines.indexOfFirst { it.startsWith("m=video") }
        if (m < 0) return sdp
        val parts = lines[m].split(" ")
        if (parts.size <= 3) return sdp
        fun rank(pt: String): Int {
            val codec = lines.firstOrNull { it.startsWith("a=rtpmap:$pt ") } ?: return 9
            if (!codec.contains("H264", ignoreCase = true)) return 5
            val fmtp = lines.firstOrNull { it.startsWith("a=fmtp:$pt ") }?.lowercase() ?: ""
            return when {
                "profile-level-id=4d" in fmtp -> 0
                "profile-level-id=42e" in fmtp -> 1
                "profile-level-id=420" in fmtp -> 2
                else -> 3
            }
        }
        lines[m] = (parts.take(3) + parts.drop(3).sortedBy { rank(it) }).joinToString(" ")
        var at = m + 1
        while (at < lines.size && (lines[at].startsWith("i=") || lines[at].startsWith("c="))) at++
        if (at < lines.size && lines[at].startsWith("b=")) lines[at] = "b=AS:$bitrateKbps"
        else lines.add(at, "b=AS:$bitrateKbps")
        return lines.joinToString(nl)
    }
}

/**
 * Kdy a jestli se k Xboxu připojit znovu. Stav je stejný jako u PlayStationu ([Ps5.Link]),
 * takže stav spojení ([LinkStatus]) i stránka Domů fungují pro obě konzole stejně.
 */
class XboxRetry {
    companion object {
        /** Spojení, které vydrželo aspoň takhle dlouho, se počítá jako zdařilé (počítadlo se nuluje). */
        const val STABLE_MS = 60_000L
        /** Po tolika neúspěších po sobě se další pokusy zastaví (hráč spustí překlad znovu). */
        const val GIVE_UP_AFTER = 5
        /** Xbox spí nebo je vypnutý: jak často se zeptat, jestli už je zapnutý (spánek nebudíme). */
        const val OFFLINE_CHECK_MS = 20_000L
        /** Xbox se právě zapnul: chvíli počkat, ať se nejdřív připojí ovladač hráče. */
        const val AFTER_WAKE_MS = 6_000L

        fun delayMs(failures: Int): Long = when {
            failures <= 1 -> 5_000L
            failures == 2 -> 15_000L
            failures == 3 -> 30_000L
            else -> 60_000L
        }
    }

    data class Decision(val state: Ps5.Link, val retryInMs: Long?)

    var failures = 0
        private set
    private var connectedAt = 0L

    fun connected(now: Long) { connectedAt = now }

    fun failed(f: XboxFail, now: Long): Decision {
        val since = connectedAt
        connectedAt = 0
        if (since > 0 && now - since >= STABLE_MS) failures = 0
        return when (f) {
            // bez nového přihlášení nebo jiného účtu to nepůjde – nezkoušet dokola
            XboxFail.AUTH, XboxFail.PROFILE, XboxFail.NOT_FOUND -> Decision(Ps5.Link.FAILED, null)
            // vypnutý Xbox: jen se občas zeptat, jestli už je zapnutý (nepočítá se jako neúspěch)
            XboxFail.OFFLINE -> Decision(Ps5.Link.WAITING, OFFLINE_CHECK_MS)
            else -> {
                failures++
                if (failures >= GIVE_UP_AFTER) Decision(Ps5.Link.FAILED, null)
                else Decision(Ps5.Link.WAITING, maxOf(delayMs(failures), if (since > 0) 15_000L else 0L))
            }
        }
    }
}
