package cz.playlingo.core

import java.net.URLEncoder

/**
 * Průvodce PlayLingem pro televizi Samsung přes Apps2Samsung (od 0.97.52, SamsungInstallActivity):
 * aplikace Apps2Samsung (zdarma, Android; https://github.com/Apps2Samsung/Apps2Samsung) sama najde televizi,
 * jedním přihlášením účtem Samsung vyrobí podpis pro tu televizi a nahraje do ní libovolný soubor .wgt.
 * PlayLingo jí předá zdroj = naše vydání `latest` na GitHubu, ve kterém je `PlayLingo-Samsung.wgt`
 * (přidává ho `.github/workflows/build.yml` z APK). Odkaz `apps2samsung://source?url=…` (a webová podoba na
 * apps2samsung.com, která ho předá aplikaci) umí Apps2Samsung od 3.3.1; sám nic neinstaluje, hráč potvrdí.
 *
 * Kroky se odškrtávají samy, kde to jde (vývojářský režim = otevřený port SDB 26101 na nalezené TV Samsung,
 * Apps2Samsung v tomto telefonu, PlayLingo z TV se ohlásilo), jinak tlačítkem „Hotovo“.
 */
object SamsungSetup {
    enum class Step { DEV_MODE, INSTALLER, SOURCE, INSTALL, READY }

    /** Port, na kterém TV Samsung ve vývojářském režimu přijímá nástroje (sdb). */
    const val SDB_PORT = 26101

    /** Balíček aplikace Apps2Samsung v Androidu. */
    const val INSTALLER_PACKAGE = "nl.madebypatrick.apps2samsung"

    /** Zdroj pro Apps2Samsung: jen vydání `latest` (ne zkušební kanál ani starší čísla verzí). */
    const val SOURCE_URL = "https://github.com/adusa89/PlayLingo/releases/tag/latest"

    /** Název souboru ve vydání (Apps2Samsung bere soubory .wgt). */
    const val WGT_ASSET = "PlayLingo-Samsung.wgt"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Odkaz, který otevře Apps2Samsung s předvyplněným zdrojem PlayLingo. */
    fun sourceLink(): String = "apps2samsung://source?url=" + enc(SOURCE_URL)

    /** Totéž přes web (čtvereček pro telefon; stránka předá odkaz aplikaci, jinak nabídne její stažení). */
    fun sourceWebLink(): String = "https://apps2samsung.com/source?url=" + enc(SOURCE_URL)

    /** Dotaz SSDP na televize Samsung (ovladač Tizen) – kvůli adrese TV a zkoušce vývojářského režimu. */
    fun searchMessage(): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
            "ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"

    /** Odpověď SSDP od televize Samsung → její adresa (odesílatel [from]), jen v domácí síti; jinak null. */
    fun parseResponse(text: String, from: String): String? {
        if (text.length > 4096 || !text.startsWith("HTTP/1.1 200")) return null
        if (!text.lowercase().contains("samsung")) return null
        return runCatching { LgInstall.address(from) }.getOrNull()
    }

    /** Co se o přípravě ví. Ručně potvrzené kroky se počítají jako hotové. */
    data class Probe(
        /** Vývojářský režim v TV (port SDB otevřený, nebo potvrzeno). */
        val devMode: Boolean,
        /** Apps2Samsung je v tomto telefonu (nebo potvrzeno). */
        val installer: Boolean,
        /** Zdroj PlayLingo byl předán Apps2Samsungu (nebo potvrzeno). */
        val source: Boolean,
        /** Co se ohlásilo z televize. */
        val check: TvCheck,
    )

    /** První krok, který ještě není hotový. Starší PlayLingo v TV = jen aktualizovat v Apps2Samsungu. */
    fun step(p: Probe): Step = when {
        p.check.ok -> Step.READY
        p.check == TvCheck.OLDER || p.check == TvCheck.NO_VERSION -> Step.INSTALL
        !p.devMode -> Step.DEV_MODE
        !p.installer -> Step.INSTALLER
        !p.source -> Step.SOURCE
        else -> Step.INSTALL
    }

    /** Je krok [s] hotový? (Pro odškrtnutí v seznamu.) */
    fun done(s: Step, p: Probe): Boolean = step(p).ordinal > s.ordinal
}
