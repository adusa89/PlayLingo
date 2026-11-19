# PlayLingo a televize LG (webOS)

Televize LG si obraz hry sama přečíst neumí (ověřeno na OLED55B7, viz `zkouska/`). Proto na LG
běží PlayLingo jen jako obrazovka: ukáže hru ze vstupu HDMI a přes ni pruh s překladem. Číst
a překládat musí jiné zařízení v téže domácí síti a televizi posílat hotové věty.

```
PlayStation ──HDMI──────────────────────────▶ televize LG  (aplikace z obchod/)
     │                                             ▲
     └─ obraz po síti ─▶ PlayLingo na Androidu ────┘ hotové věty po domácí síti (port 8787)
                         (telefon nebo box; čte a překládá)
```

## Co je kde

| Složka / soubor | Co to je | Kdy se použije |
|---|---|---|
| `obchod/` | **Aplikace pro televizi** (id `cz.playlingo.tv`), česky a anglicky. Podává se do obchodu LG. | Tohle je PlayLingo pro LG. |
| `zkouska/` | Diagnostická aplikace (id `cz.playlingo.zkouska`): zjistí, co daná televize aplikaci dovolí. | Jen při zkoumání nového modelu televize. |
| `posilac.js` | Zkušební posílač vět z počítače (`node webos/posilac.js`). Nic nečte ani nepřekládá. | Zkouška aplikace v televizi bez Androidu. |
| `../pc/` | Zkušební čtečka pro počítač: čte a překládá jako Android. | Ladění čtení a překladu. |
| `../app/…/core/TvSender.kt` | Strana Androidu: posílání vět do televize a popis zpráv. | Volba „Posílat titulky do televize LG“. |

Jak spolu televize a zařízení mluví, je popsané na jediném místě: v `TvSender.kt`. V televizi to
obsluhuje jediný soubor, `obchod/app/app.js` (funkce `hledej`, `zkus`, `prevezmi`).

## Jak to zprovoznit doma

1. **Telefon:** v PlayLingu (od 0.63) klepnout na „Použít telefon pro televizi LG“. V boxu
   s Android TV: Nastavení → „Posílat titulky do televize LG“.
2. **Televize:** spustit PlayLingo a vybrat vstup HDMI s PlayStationem. Televize si zařízení
   v síti najde sama a zapamatuje si ho.
3. V telefonu spustit překlad (nebo zkoušku bez PlayStationu).

## Nahrání do televize z Androidu (bez počítače)

PlayLingo pro Android má aplikaci pro televizi přibalenou (`assets/lg/receiver.ipk`) a umí ji do
televize nahrát sám: Nastavení → „Nahrát PlayLingo do televize LG“, v telefonu tlačítko pod
„Použít telefon pro televizi LG“, a také na stránce Nápověda. Televize potřebuje vývojářský
režim (příprava níže, bez počítače). Uživatel opíše adresu televize a Passphrase z aplikace
Developer Mode; Passphrase se neukládá.

- **Balíček** skládá při sestavení APK úloha `lgBalicek` v `app/build.gradle.kts` kódem
  `core/LgIpk.kt` (sdíleným přes `buildSrc`, bez Node a Pythonu). Obsah je stejný jako
  z `ares-package` 3.2.6 (ověřeno porovnáním po rozbalení a `ares-package -i`): `debian-binary`,
  `control.tar.gz` (jen `control`), `data.tar.gz` (`usr/palm/applications/cz.playlingo.tv/…`,
  `usr/palm/packages/cz.playlingo.tv/packageinfo.json`). Rozdíl: pevné datum 1. 1. 2000 (webOS 5
  odmítá rok 1970), vlastník 0, práva 644/755. Vedle balíčku je `lg/receiver-manifest.json`
  (id, verze z `appinfo.json`, velikost, sha256) a aplikace ho před instalací ověří.
- **Instalace** (`LgInstaller.kt`): klíč z Key Serveru (`http://<ip>:9991/webos_rsa`) odemčený
  Passphrase → SSH `prisoner@<ip>:9922` (podpis ssh-rsa povolený jen pro toto spojení) → přečte
  `/media/developer/apps/usr/palm/applications/cz.playlingo.tv/appinfo.json`. Stejná nebo novější
  verze v televizi = jen spustit, **nikdy nic staršího**. Jinak SFTP do `/media/developer/temp`,
  kontrola bajtů, `luna-send-pub -i luna://com.webos.appInstallService/dev/install`, kontrola verze
  a spuštění (`com.webos.applicationManager/launch`). Limit 2 minuty, jde zrušit.
- **Spárování**: otisk klíče televize se při prvním spojení zapamatuje; když se změní (jiná
  televize na stejné adrese, tovární nastavení), instalace se odmítne, dokud uživatel nezvolí
  „Spárovat znovu“ a nezadá Passphrase. Soukromý klíč SSH se ukládá jen zašifrovaný klíčem
  z Android Keystore (`LgVault.kt`), takže další aktualizace už Passphrase nepotřebuje.
- **Zbývající čas vývojářského režimu** (`LgDevMode.kt`): při spojení se přečte klíč relace
  z `/var/luna/preferences/devmode_enabled` a aplikace se nejvýš jednou za hodinu zeptá služby LG
  `https://developer.lge.com/secure/CheckDevModeSession.dev` (odpověď `HHH:MM:SS`). Ukazuje zbývající
  dny, pod 3 dny upozorní. **Nic se samo neprodlužuje** – to dělá uživatel v televizi
  (Developer Mode → Extend).
- Logika bez Androidu (rozhodnutí o verzi, odpovědi televize a LG, výpočet času) je v
  `core/LgInstall.kt` s testy v `app/src/test/.../LgInstallTest.kt`. Skutečná televize se
  v emulátoru vyzkoušet nedá – ověřit to umí jen uživatel.
- Postup převzat podle projektu PlayCaption (AGPL-3.0, `webos/build.py`,
  `LgInstalator.kt` a další); hlavičky převzatých souborů to uvádějí.

## Nahrání do televize při vývoji

Televize musí mít zapnutý vývojářský režim (aplikace Developer Mode z LG Content Store, účet
z webostv.developer.lge.com, zapnout Dev Mode Status a Key Server). Na počítači nástroj LG:
`npm install -g @webos-tools/cli`.

```
ares-setup-device -a lgtv -i "host=ADRESA_TELEVIZE" -i "port=9922" -i "username=prisoner"
ares-novacom --device lgtv --getkey --passphrase KOD_Z_TELEVIZE
ares-package --no-minify webos/obchod/app -o webos/obchod
ares-install -d lgtv webos/obchod/cz.playlingo.tv_1.0.0_all.ipk
ares-launch  -d lgtv cz.playlingo.tv
ares-inspect -d lgtv -a cz.playlingo.tv      # živý přístup do běžící aplikace
```

Vývojářský režim se po čase sám vypne a aplikace z televize zmizí; v aplikaci Developer Mode
jde prodloužit (kolik zbývá, ukazuje i PlayLingo na Androidu – viz výše). Stejný balíček jako
v APK vznikne i bez nástroje LG: `gradlew :app:lgBalicek` → `app/build/generated/lgAssets/lg/receiver.ipk`. Trvalá cesta pro uživatele je obchod LG – viz `obchod/CTI-ME.md`.

Televize z roku 2017 má prohlížeč Chromium 38: kód pro televizi musí zůstat ve starém zápisu
JavaScriptu (žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce).
