# Televize Samsung

Samsung pustí do televize vlastní aplikaci jen ve vývojářském režimu a jen podepsanou pro tvou
televizi. Obojí zařídí zdarma aplikace **Apps2Samsung** v telefonu s Androidem – bez počítače,
asi 10 minut. Průvodce v PlayLingu (Typ TV Samsung → „Nahrát PlayLingo do TV Samsung“) tě provede
a sám pozná, až se PlayLingo z televize ozve.

<a id="vyvojarsky"></a>
## 1. Vývojářský režim v televizi

Aplikace (Apps) → na ovladači **1 2 3 4 5** (ovladač bez čísel: tlačítko **123**) → Developer mode
**On** → do **Host PC IP** napiš adresu **telefonu**, ve kterém bude Apps2Samsung (ne počítače).
Adresu ukáže průvodce PlayLinga v telefonu, jinak ji najdeš v telefonu: Nastavení → Wi‑Fi →
podrobnosti sítě. Potvrď a televizi restartuj: podrž vypínač, dokud nezhasne a znovu nenaběhne.

<a id="apps2samsung"></a>
## 2. Apps2Samsung do telefonu (Android)

👉 [Stáhnout Apps2Samsung (stránka vydání, soubor `…-android.apk`)](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)

Záložní cesty:
- [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Apps2Samsung/Apps2Samsung) – sám hlídá aktualizace,
- F-Droid / Droid-ify / Neo Store: přidej repozitář `https://apps2samsung.com/fdroid/repo`,
- domovská stránka: [apps2samsung.com](https://apps2samsung.com) · zdroj: [github.com/Apps2Samsung/Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung).

Potřebuješ verzi **3.3.1 nebo novější** (umí odkaz pro přidání PlayLinga). Na iPhonu jde Apps2Samsung
jen přes SideStore (jednou s počítačem a kabelem) – návod je na stránce Apps2Samsung.

<a id="zdroj"></a>
## 3. PlayLingo do Apps2Samsungu

V průvodci PlayLinga stiskni **Přidat PlayLingo do Apps2Samsungu** (na televizi načti čtvereček
telefonem) a v Apps2Samsungu přidání potvrď. Nebo tento odkaz:

👉 [Přidat PlayLingo do Apps2Samsungu](https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FPlayLingo%2Freleases%2Ftag%2Flatest)

Ručně: v Apps2Samsungu **⋯ → Add a source** a vlož adresu
`https://github.com/adusa89/PlayLingo/releases/tag/latest`

<a id="instalace"></a>
## 4. Nainstalovat

V Apps2Samsungu vyber svou televizi a u PlayLinga dej **Install**. Napoprvé se přihlásíš účtem
Samsung (zdarma) – Apps2Samsung tím vyrobí podpis pro tvou televizi a příště ho použije znovu.
Pak v televizi spusť PlayLingo; průvodce v PlayLingu ukáže „hotovo“.

**Aktualizace:** když PlayLingo hlásí starší aplikaci v televizi, dej v Apps2Samsungu u PlayLinga
**Update** (stejný účet Samsung jako minule).

Když něco nejde, Apps2Samsung sám napíše, co v televizi chybí (vývojářský režim, Host PC IP…).

<a id="pocitac"></a>
## Bez telefonu s Androidem: počítač (Tizen Studio)

1. Vývojářský režim jako v bodu 1, do **Host PC IP** ale adresu **počítače**.
2. V PlayLingu (průvodce Samsung → „Bez telefonu s Androidem: počítač“) ulož soubor aplikace, nebo ho
   stáhni: [PlayLingo-Samsung.wgt](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo-Samsung.wgt).
3. 👉 [Instalace Tizen Studia (Samsung)](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html)
   – když odkaz nefunguje, hledej **„Tizen Studio install TV SDK“**. V Tizen Studiu soubor podepiš
   certifikátem Samsung a nahraj do televize (počítej s 30–60 minutami poprvé). Jde to i
   [Apps2Samsung pro počítač](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest) (Windows, macOS, Linux)
   se zdrojem PlayLingo jako v bodu 3.

---
*English: Samsung only accepts an app signed for your TV in developer mode. The free **Apps2Samsung**
app on an Android phone does both, no computer needed: Developer mode (Apps → 12345 → On, Host PC IP =
**the phone's** address, restart the TV) → [get Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung/releases/latest)
(3.3.1 or newer) → [add PlayLingo to Apps2Samsung](https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FPlayLingo%2Freleases%2Ftag%2Flatest)
(or ⋯ → Add a source: `https://github.com/adusa89/PlayLingo/releases/tag/latest`) → pick the TV →
Install (first time: sign in with a Samsung account). Without an Android phone: [Tizen Studio](https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html)
on a computer with [PlayLingo-Samsung.wgt](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo-Samsung.wgt).*
