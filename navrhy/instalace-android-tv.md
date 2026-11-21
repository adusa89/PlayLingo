# Nahrání PlayLinga do Android TV z telefonu (zjištění 21. 11. 2026)

Otázka majitele: „Potřebujeme vůbec fire app? Nemůžeme to nakódovat?“ – tj. dostat PlayLingo do
Android TV / Google TV / boxu bez cizí aplikace v televizi (Downloader, Send Files to TV…).

## Jak to jde

Bez aplikace v televizi jde APK do televize dostat jen přes **ADB po síti** (totéž, co dělá
`adb install` z počítače). Telefon má v sobě přesně to APK, které je v něm nainstalované
(`applicationInfo.sourceDir`), podepsané stálým klíčem – v televizi pak funguje i aktualizace z aplikace.

Dvě podoby ADB po síti:

| | Starší / běžná Android TV, Fire TV, Shield, boxy | Android 11+ „Bezdrátové ladění“ |
|---|---|---|
| Zapnutí | Možnosti pro vývojáře → Ladění USB / Ladění přes síť / ADB debugging | Možnosti pro vývojáře → Bezdrátové ladění |
| Port | pevný **5555** | náhodný (mDNS `_adb-tls-connect._tcp`), párování zvlášť (`_adb-tls-pairing._tcp`) |
| Ověření | klíč RSA, televize se zeptá „Povolit ladění?“ (Vždy povolit) | šestimístný párovací kód (SPAKE2) + TLS |

Většina televizí a boxů s Android TV / Google TV (Sony, Philips, TCL, Chromecast s Google TV, Shield,
Fire TV) s zapnutým laděním poslouchá na 5555 – stejně se to dělá z počítače (`adb connect <ip>`).
Zda to platí i pro nejnovější Google TV Streamer (Android 14), ověří jen majitel na skutečné TV.

## Knihovna

**`dev.mobile:dadb:2.0.0`** (mobile.dev, Maven Central, vydáno 22. 6. 2026, Apache-2.0 – s AGPL-3.0 slučitelná).

- čisté Kotlin/JVM, bez nativní části; jar 132 kB + `com.squareup.okio:okio:2.10.0` (asi 250 kB),
  do APK přibude zhruba 0,4 MB,
- umí TCP spojení, ověření klíčem RSA (podpis `RSA/ECB/NoPadding`, na Androidu přes Conscrypt jde),
  `shell`, `install` (přes `cmd package install -S`, na Androidu 7+), `installMultiple`, `push`,
- **neumí párování s kódem ani TLS** (Android 11+ Bezdrátové ladění).

Párování s kódem umí `libadb-android` (MuntashirAkon, GPL-3.0-or-later OR Apache-2.0), ale:
jen přes JitPack (ne Maven Central), táhne Conscrypt (nativní knihovna, MB navíc na každý procesor),
BouncyCastle, nativní SPAKE2 a AndroidX – PlayLingo AndroidX záměrně nepoužívá. Zatím **ne**.

## Rozhodnutí

1. **Hotovo teď:** port 5555 s klíčem RSA přes dadb (`AtvInstaller.kt`, `AtvInstallActivity.kt`,
   `core/AtvInstall.kt`). Telefon si jednou vytvoří vlastní klíč ADB, uloží ho zašifrovaný přes
   Android Keystore (jako `LgVault`), televize se jednou zeptá „Povolit ladění?“.
2. **Další krok (až bude potřeba):** Bezdrátové ladění s párovacím kódem – buď libadb-android
   (velikost APK, JitPack), nebo vlastní SPAKE2 + TLS 1.3 s exportem klíče (Conscrypt). Párovací kód
   a porty ukazuje televize v Možnostech pro vývojáře → Bezdrátové ladění. Potřeba jen pro TV, která
   Ladění přes síť na portu 5555 nenabízí.

## Rizika

- Ladění přes síť nechává televizi otevřenou pro každého v domácí síti, kdo má od TV povolený klíč.
  Po nahrání stačí ladění v TV vypnout; aktualizace pak chodí z aplikace samotné.
- Verze do 0.55 měly jiný podpis – instalace přes ně selže (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`),
  aplikace řekne, ať se stará verze v TV nejdřív odinstaluje. Sama nic neodinstalovává.
- Starší verzi přes novější nikdy nedáváme (stejná/novější v TV = jen spustit).
- Emulátor zkoušky tohle neověří – spojení s televizí, dotaz „Povolit ladění?“ a instalaci
  ověří jen majitel.
