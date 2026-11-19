# PlayLingo – pokyny pro Claude Code

Aplikace pro Android TV. Po domácí síti se připojí k PlayStationu 5 (stejně jako Remote Play),
z kopie obrazu čte anglické titulky, offline je přeloží a ukáže v černém pruhu přes obraz.
Hra sama jde do televize kabelem HDMI. Návod pro uživatele je v `NAVOD.md`.

## Jak se tu píše

- Všechno česky: commity, komentáře v kódu, texty aplikace, návod.
- Commit popisuje, co se změnilo z pohledu hráče, ne jak je to naprogramované
  (např. „0.45: pokyny hry (Press to Start, Select…) nejsou repliky“).
- Uživateli odpovídej česky a bez programátorského žargonu.

## Kde co je

- `app/src/main/java/cz/playlingo/core` – logika bez Androidu (výběr a skládání titulků, oblasti,
  slovník, paměť překladů, texty aplikace, licence). Má unit testy v `app/src/test`.
- `app/src/main/java/cz/playlingo` – Android část:
  - `TranslatorService.kt` – srdce aplikace (čtení → OCR → překlad → pruh)
  - `StreamReader.kt` – dekódování videa z konzole
  - `Ps5Link.kt`, `Ps5Pairing.kt`, `PsnAuth.kt` – spojení, párování, přihlášení účtem (přes telefon)
  - `PhoneHelperActivity.kt` – PlayLingo v telefonu: pomocník pro přihlášení (viz Přihlášení přes telefon)
  - `SubtitleOverlay.kt`, `TitulkyAccessibility.kt` – pruh s překladem přes Usnadnění
  - `MainActivity.kt` – všechny obrazovky a průvodce (rozložení viz Obrazovky a průvodce)
  - `DiagActivity.kt` – hlášení o stavu aplikace přes celou obrazovku (k vyfocení); vše v něm
    prochází `core/CrashReport.kt` → `Redact` (žádné klíče, čísla účtu ani PIN)
  - `App.kt`, `CrashLog.kt` – zápis pádů do `pady.txt` (posledních pár) a upozornění po dalším spuštění
  - `Updater.kt` – aktualizace z aplikace (stažení nové verze a předání systému k instalaci)
  - `LgInstallActivity.kt`, `LgInstaller.kt`, `LgVault.kt`, `LgDevMode.kt` – nahrání aplikace
    do televize LG ve vývojářském režimu a zbývající dny režimu (viz Televize LG);
    `LgScanActivity.kt` – adresa a Passphrase z obrazovky televize kamerou telefonu
  - `TvChoice.kt` – typ televize (Android TV / LG / Samsung): nabídka, televize v souhrnném řádku na Domů, nahrání do televize
  - `SamsungInstallActivity.kt` – průvodce PlayLingem pro televizi Samsung (viz Televize Samsung)
  - `DemoFeeder.kt`, `TestCardActivity.kt` – ukázková „hra“ pro zkoušku bez PlayStationu
  - `ConsoleLink.kt` – společné rozhraní zdroje obrazu (PlayStation `Ps5Link`, Xbox `XboxLink`)
  - `XboxAccount.kt`, `XboxLink.kt`, `XboxForm.kt` – Xbox: přihlášení účtem Microsoft, spojení
    přes vzdálené hraní (WebRTC) a stránka konzole pro Xbox (viz Xbox)
  - `DeeplTranslator.kt`, `DeeplSection.kt` – pokus DeepL (viz Pokus: DeepL)
  - `NetLogShare.kt` – záznam pro vývojáře v domácí síti, Kopírovat/Sdílet hlášení (viz Záznam ze zařízení)
- `app/src/main/cpp`, `app/src/main/java/com/metallic/chiaki/lib` – naše upravená vrstva nad
  knihovnou chiaki-ng (bez zvuku a nativního dekodéru).
- `third_party/chiaki-ng` – submodul. Needituj ho; před sestavením se do něj kopíruje
  `app/src/main/cpp` (úloha `patchChiaki` v `app/build.gradle.kts`).
- `.github/workflows/build.yml`, `ci/emulator-test.sh` – sestavení, zkouška, vydání.
- `ci/mistni-kontrola` – překlad a unit testy bez Android SDK (viz níže).
- `buildSrc` – jen zpřístupní `core/LgIpk.kt` a `core/SamsungWgt.kt` sestavení (úlohy `lgBalicek`
  a `samsungBalicek`, viz Televize LG a Televize Samsung).
- `webos` – PlayLingo pro televize LG (viz Televize LG): `webos/obchod` je aplikace pro televizi,
  `webos/zkouska` diagnostika. Do APK jde jen balíček z `webos/obchod/app` (úloha `lgBalicek`).
- `tizen` – PlayLingo pro televize Samsung (viz Televize Samsung): `tizen/app` se společným kódem
  s `webos/obchod/app`, do APK jako nepodepsaný `.wgt` (úloha `samsungBalicek`).
- `pc` – zkušební čtečka pro počítač s Windows (viz `pc/CTI-ME.md`): čte titulky z okna s obrazem
  hry, přeloží je a pošle do televize LG. Překládá se proti téže složce `core` jako aplikace,
  takže změna v `core` se týká i jí – po úpravě `core` ověř i `gradlew -p pc installDist`.
  Do APK se nedostane a sestavení na GitHubu o ní neví.

## Sestavení a zkouška

Každý push (na jakoukoli větev) spustí GitHub Actions:

1. unit testy (`./gradlew testDebugUnitTest`),
2. sestavení APK pro televize (ARM) a zkušebního APK pro emulátor (x86),
3. zkoušku v emulátoru Android TV na Androidu 9, 11, 13 a 14 (`ci/emulator-test.sh`),
4. vydání:
   - z `main` → `latest` (nabídne se všem) a vydání pod číslem verze – **jen když zkouška prošla**,
   - z větví `zaloha-…` → jen vydání pod číslem verze, také až po zkoušce,
   - z ostatních větví → zkušební kanál `zkusebni` (nabídne se jen tomu, kdo má v aplikaci zapnuté
     Zkušební verze). Sem jde APK **hned po sestavení a kontrole podpisu**, asi 5 minut od odeslání,
     a na emulátory nečeká (krok „Zkušební kanál hned po sestavení“ v úloze `build`). Až zkoušky
     projdou, doplní se to do popisu vydání. Ve zkušebním kanálu tedy může být i verze, která
     zkouškou neprošla – než ji uživateli doporučíš, podívej se na výsledek běhu.

Když v sezení není Android SDK (`ANDROID_HOME` je prázdné), nejde APK sestavit místně. Kód ale
jde přeložit a unit testy pustit: `cd ci/mistni-kontrola && gradle test` (asi minuta). Dělej to
před každým pushem – odhalí překlepy a chyby v typech dřív než zkouška na GitHubu. Nezná zdroje
(`res/`), manifest ani nativní část; nový zdroj `R.…` nebo novou funkci knihoven Google použitou
v kódu je potřeba doplnit do `ci/mistni-kontrola/stub`.

**Sestavení na PC uživatele (Windows)**: `.\sestavit-na-pc.ps1` (s `-Instalovat` nahraje APK přes
`adb`). První běh asi 1,5 minuty, další po změně kódu kolem 15 s. Výsledek `PlayLingo-pc.apk`
je podepsaný stálým klíčem, takže jde instalovat přes verze z GitHubu a naopak. Klíč a heslo leží
mimo repozitář v `%USERPROFILE%\.playlingo\klic` – nikam je nekopíruj ani nevypisuj. Nativní část
(chiaki-ng) se na Windows přeložit nedá (OpenSSL chce linuxové nástroje), proto ji skript bere
hotovou z vydání `latest` (`-PnativniZ=…` v `app/build.gradle.kts`). Když se změní
`app/src/main/cpp`, `com/metallic` nebo `third_party`, skript varuje: taková změna se ověří jen
sestavením na GitHubu. Nástroje: Java 17 (Temurin), Android SDK v `%LOCALAPPDATA%\Android\Sdk`.

Od 0.68 běží vedle toho i **zkouška v emulátoru telefonu** (`ci/phone-test.sh`, Android 11 a 14):
pomocník, celá aplikace v telefonu v šesti velikostech displeje a písma (malý, běžný, velké písmo,
velký, na šířku, tablet) a překlad ukázkové hry. Výsledky a snímky jsou ve větvích
`test-results-phone30` a `-phone34` (snímky `p1_…` pomocník, `p2_<velikost>_<stránka>` celá
aplikace). Zatím vydání nezdržuje. Po každé změně rozložení si ty snímky prohlédni.
Přihlašovací stránka Sony uvnitř pomocníka se v telefonu zkouší jen na Androidu 11 a v Android TV
jen do Androidu 13 (snímek `12p`): emulátor Androidu 14 při jejím vykreslení zamrzne celý (telefon
dvakrát ze dvou běhů, Android TV 19. 11. 2026 třikrát ze čtyř – zkouška pak stála do limitu 50 minut). Když emulátor během zkoušky přestane
odpovídat, je v `result.txt` řádek `EMULATOR SPADL` s částí, před kterou se to stalo – to je
chyba emulátoru, ne aplikace, a stačí běh pustit znovu.

Přirozenost překladu (od 0.69): `core/Natural.kt` sjednocuje v češtině oslovení na tykání
(překladač střídá tykání a vykání) a `IDIOMS` v `core/OfflinePrep.kt` nahrazuje obraty, které
překladač překládá doslova. Obojí se ladí jen podle skutečných výstupů v `preklad.txt`, ne odhadem.

Zkouška na Android TV navíc nechá přeložit typické herní repliky (`core/TranslationProbe.kt`)
a uloží je do `preklad.txt` ve větvích s výsledky – podle něj se ladí přirozenost překladu.

Výsledky zkoušky se ukládají do větví `test-results-api28`, `-api30`, `-api33`, `-api34`
(přepisují se při každém běhu): `result.txt`, `crash.txt`, `log.txt` a snímky obrazovky.
Když zkouška spadne nebo jde o vzhled, stáhni si větev a na snímky se podívej:
`git fetch origin test-results-api34`.

Změny jen v dokumentaci commituj s `[skip ci]` v názvu, ať zbytečně neběží hodinová zkouška.

Emulátor Androidu 9 se občas zasekne sám od sebe (limit 50 minut). Jedno selhání může být
náhoda, opakované selhání po změně kódu ne.

## Nová verze

- Při každé změně aplikace zvyš v `app/build.gradle.kts` obojí: `versionCode` (54 → 55)
  i `versionName` ("0.54" → "0.55"), a stejná čísla v `BuildInfo` na konci `MainActivity.kt`.
  Název commitu začíná číslem verze: `0.55: …`.
- Vydání pod číslem verze (`v0.55`) vznikne jen jednou a už se nepřepisuje. Push na `main`
  bez zvýšení čísla by tedy nechal pod starým číslem starý soubor.
- Vydání `latest` z `main` se nabídne všem uživatelům jako aktualizace (aplikace čte
  `version.txt`). Co je v `main` a projde zkouškou, jde rovnou k lidem.
- Záloha verze, ke které se má jít vrátit: větev `zaloha-<číslo>`.
- Aplikace nabízí novou verzi tlačítkem „Aktualizovat“ na stránce Domů (`Updater.kt`). Bere
  vyšší číslo z `latest` a (se zapnutým zkušebním kanálem) ze `zkusebni`.
  Od 0.65 nabízí aktualizaci i pomocník v telefonu (`PhoneHelperActivity`), kde jde zapnout
  i zkušební kanál. Odpověď systému na instalaci chodí vždy do `MainActivity`; v telefonu ji jen
  vyřídí a zmizí.

## Podpis

- Všechny verze musí být podepsané stejným klíčem. S jiným podpisem televize novou verzi přes
  starou nenainstaluje a uživatel přijde o spárování i nastavení. Verze do 0.55 měly každá
  jiný (dočasný) podpis – proto se musely pokaždé odinstalovat.
- Klíč v repozitáři není a nesmí být (repozitář je veřejný a co se jednou dostane do historie,
  tam zůstane). Je jen v tajných údajích repozitáře na GitHubu: `PLAYLINGO_KEY_BASE64` (soubor
  p12 v base64) a `PLAYLINGO_KEY_PASSWORD`. Sestavení ho při běhu rozbalí do dočasného souboru
  a po sobě smaže. Zálohu klíče a hesla má uživatel na svém PC (`%USERPROFILE%\.playlingo\klic`,
obnovená z tajných údajů 19. 11. 2026). Klíč ani heslo nikam nezapisuj,
  nevypisuj je do logu a neukládej do repozitáře; `*.p12`, `*.jks`, `*.keystore` jsou v `.gitignore`.
- Krok „Kontrola podpisu“ porovná otisk certifikátu s otiskem stálého klíče zapsaným přímo
  v `build.yml` (otisk je veřejný údaj). Verze s jiným podpisem se **nevydá**: ve zkušebním
  kanálu zůstane předchozí verze a na `main` sestavení skončí chybou.
- Klíč se nemění. Po výměně si každý musí aplikaci odinstalovat a znovu spárovat. Jediná výměna
  proběhla ve verzi 0.61: první stálý klíč (0.56–0.60) ležel zašifrovaný v repozitáři, proto byl
  nahrazen novým, který v repozitáři nikdy nebyl. Starý soubor `podpis/playlingo.p12` zůstal
  v historii větví, ale nic se jím už nepodepisuje.
- K tajným údajům se při sestavení dostane každý, kdo může do repozitáře zapisovat (stačí upravit
  `build.yml` ve své větvi). Právo zápisu proto dávej jen lidem, kterým bys svěřil i klíč.

## Přihlášení přes telefon

Aplikace z přihlášení potřebuje jen číslo účtu PlayStation (nemění se). Od 0.57 se nic neopisuje:

- **Stránka z televize.** Po dobu kroků přihlášení (kroky 2–4 průvodce spárováním) televize
  vystaví v domácí síti malou stránku (`core/LoginServer.kt`, text stránky `core/PhoneLogin.kt`).
  Čtvereček na obrazovce vede na ni, ne na Sony. Uživatel se přihlásí u Sony na nové kartě,
  zkopíruje adresu bílé stránky „redirect“ a vloží ji na stránku; televize si z ní vezme kód
  a pokračuje sama. Funguje i na iPhonu. Stránka přijme jen požadavky s jednorázovým klíčem
  z čtverečku a mimo tyto kroky neběží.
- **Co stránka v prohlížeči umí a neumí** (`SCRIPT` v `core/PhoneLogin.kt`, od 0.67): přihlášení
  Sony otevírá sama na nové kartě a po úspěchu ji zavře; vloženou adresu odešle sama na pozadí.
  Adresu z karty Sony si přečíst nemůže (cizí stránka) a schránku prohlížeč stránce z domácí sítě
  bez zabezpečeného spojení nevydá. Zkopírovat adresu a vrátit se na kartu PlayLingo proto musí
  člověk – na iPhonu to jinak nejde, nezkoušej to obcházet. Úplně bez kopírování je jen pomocník
  v telefonu s Androidem.
- **PlayLingo v telefonu.** Stejné APK nainstalované v telefonu (pozná se podle mobilní sítě,
  `NetInfo.isPhone`) ukáže místo aplikace jen pomocníka. Otevírá ho odkaz `playlingo://login?…`
  ze stránky televize. Přihlášení Sony běží v něm, kód si vezme sám, číslo účtu pošle televizi
  (`LoginClient`) a zapamatuje si ho (`Prefs.phoneAccounts`; jen číslo a jméno, žádné heslo).
  Účet posílá jen na adresy domácí sítě. Záloha: přihlášení v prohlížeči a Sdílet → PlayLingo.
- Ruční opsání kódu (krok 5 průvodce) zůstává jako poslední záloha a od 0.66 je úplné samo
  o sobě: má vlastní čtvereček, který vede rovnou na přihlášení Sony (jako před 0.57), a z kroku
  se čtverečkem na něj vede tlačítko „Stránka se neotevřela“. Je pro případ, že se stránka
  z televize v telefonu neotevře (telefon na jiné Wi-Fi nebo na mobilních datech). Tuhle cestu
  neodstraňuj. Pořadí a počet kroků průvodce se nezměnily.
- Zkouška v emulátoru umí ověřit jen stránku a posílání účtu, ne přihlášení u Sony. Jestli Sony
  pustí přihlášení uvnitř pomocníka na skutečném telefonu, umí ověřit jen uživatel.

## Televize LG (webOS)

Přehled pro lidi je ve `webos/CTI-ME.md`; tady jen to, co je potřeba vědět při úpravách.

- Televize LG si obraz hry nepřečte (ověřeno na OLED55B7: aplikace vidí jen černou a snímek
  obrazovky jí systém nedovolí; výsledky jsou ve `webos/zkouska/CTI-ME.md`). Na LG proto PlayLingo
  běží jen jako obrazovka – hra z HDMI a přes ni pruh. Čte a překládá PlayLingo na Androidu
  (telefon nebo box) a televizi posílá hotové věty po domácí síti.
- **`webos/obchod` je jediná aplikace PlayLingo pro televizi** (id `cz.playlingo.tv`, česky
  a anglicky, podává se do obchodu LG; postup podání je ve `webos/obchod/CTI-ME.md`).
  `webos/zkouska` je jen diagnostika, co která televize dovolí, a po síti nic nepřijímá.
- Zprávy mezi Androidem a televizí jsou popsané na jednom místě, v `core/TvSender.kt`. V televizi
  je obsluhuje jen `webos/obchod/app/app.js` (`hledej`, `zkus`, `prevezmi`). Televize si zařízení
  najde sama: projde adresy své sítě na portu 8787. Spojení je WebSocket obsloužený ručně na obou
  stranách. Novinky ve zprávách přidávej jen jako nepovinná pole (tak přibylo `casti` s barvami jmen
  a volbami odpovědí) – starší aplikace v televizi musí dál ukázat aspoň obyčejný `text`.
- Android: **Typ televize** (`Prefs.tvType`: Android TV / LG / Samsung, `core/TvReceiver.kt` →
  `TvType`). Kdo ho nezvolil, řídí se starým klíčem `tv_send` (zapnuto = LG), takže dřívější
  uživatelé LG o nic nepřijdou; `setTvType` klíč `tv_send` drží v souladu. `Prefs.tvSend` = typ
  LG nebo Samsung. Volí se v průvodci (krok Televize, `TvChoice.set`), v Nastavení a ze souhrnného
  řádku na Domů – nabídka přes `TvChoice.show(activity) { … }`. Bývalý přepínač „Posílat titulky do
  televize LG“ není. Souhrnný řádek na Domů ukazuje pro LG/Samsung stav spojení (`TvChoice.short`);
  upozornění s tlačítkem k nahrání aplikace do televize je na Domů jen do chvíle, než se z ní aplikace
  poprvé ohlásí (`TvChoice.needsInstall`, `Prefs.tvSeen`). Krok Televize v průvodci a Nastavení
  nabízejí nahrání (i aktualizaci) vždy. Pro Android TV se na Domů upozorňuje na chybějící povolení
  pruhu. `SubtitleOverlay` předává `TvSender` každou změnu pruhu. U Android TV se nic
  neotevírá ani neposílá a aplikace se chová jako dřív. U LG/Samsungu se čte i tehdy, když je
  vidět aplikace, a nevyžaduje se povolení pruhu.
- Televize po připojení pošle `pripojeno` a od verze 1.1.0 v něm i `platforma` („webos“/„tizen“)
  a `verze`. `TvSender` zprávy od televize čte (maskované rámce WebSocketu, `readFrame`) a poslední
  ohlášení drží v `TvSender.lastSeen` (`TvAnnounce`); podle něj Domů a průvodce Samsung poznají,
  co v televizi běží.
- Telefon: pomocník (`PhoneHelperActivity`) má od 0.63 tlačítko „Použít telefon pro televizi LG“
  a vedle něj „… Samsung“. Zapne `Prefs.forceTv` a nastaví typ televize, předá už ověřený účet do párování a otevře celou
  aplikaci. Jestli se celá aplikace dá na telefonu pohodlně ovládat, umí ověřit jen uživatel.
- **Nahrání do televize z Androidu** (přehled ve `webos/CTI-ME.md`): tlačítko „Nahrát PlayLingo do
  televize LG“ v průvodci (krok Televize), v Nastavení, upozornění na Domů (typ LG, dokud se televize
  neohlásila), v pomocníkovi v telefonu a v Nápovědě otevře
  `LgInstallActivity`. APK nese `assets/lg/receiver.ipk` + `receiver-manifest.json`; skládá je při
  sestavení úloha `lgBalicek` (`app/build.gradle.kts`) kódem `core/LgIpk.kt`, který `buildSrc`
  sdílí se sestavením – žádný Node ani Python. Rozložení odpovídá `ares-package` (ověřeno), datum
  je pevně 1. 1. 2000 (webOS 5 odmítá 1970). Instalace (`LgInstaller`, JSch): Key Server
  `:9991/webos_rsa` + Passphrase (neukládá se) → SSH `prisoner@:9922` → verze v
  `/media/developer/apps/usr/palm/applications/cz.playlingo.tv/appinfo.json` → stejná/novější jen
  spustit, **nikdy nesnižovat verzi** → jinak SFTP, `appInstallService/dev/install`, kontrola, spuštění.
  Otisk klíče televize se pamatuje, změnu musí potvrdit uživatel („Spárovat znovu“); klíč SSH
  a klíč relace jsou zašifrované přes Android Keystore (`LgVault`). Zbývající dny režimu
  (`LgDevMode`) se ptají služby LG nejvýš jednou za hodinu; **nic se neprodlužuje automaticky**.
  Rozhodování a čtení odpovědí je v `core/LgInstall.kt` s testy. Skutečnou televizi zkouška
  v emulátoru nemá – instalaci ověří jen uživatel. Při změně `webos/obchod/app` zvyš verzi
  v `appinfo.json` i `platforma.js`, jinak Android televizi neaktualizuje (stejná verze = jen spustit).
  Převzato podle PlayCaption (AGPL-3.0) – hlavičky souborů zachovej.
- **Načtení adresy a Passphrase kamerou** (telefon): tlačítko „Načíst z obrazovky televize kamerou“
  v `LgInstallActivity` jen tam, kde je kamera a nejde o televizi/box (`LgScanActivity.available`:
  bez `leanback` a UI televize). Povolení CAMERA se žádá až po stisku tlačítka. `LgScanActivity` =
  Camera2 ze systému (náhled TextureView + snímky YUV do ML Kitu) – záměrně bez CameraX, ať APK
  nepřibírá knihovny a nepotřebuje AndroidX. Čtení řádků (`core/LgScan.kt`: IPv4 v domácí síti,
  Passphrase jen u popisku „Passphrase“, případně pod „Key Server“; víc kandidátů = nic) a hlasování
  (`LgScanVote`: údaj platí po dvou stejných čteních) mají testy `LgScanTest` s ukázkami textu
  obrazovky. O/I v Passphrase se opraví na 0/1 jen u šestnáctkového tvaru (ostatní písmena A–F),
  jinak upozornění `km_unsure`. Výsledek jde zpět průvodci k potvrzení, uživatel stiskne „Nahrát“.
  Nic se neukládá ani nevypisuje do logu; `Redact` skrývá i `passphrase=…`. V manifestu musí
  zůstat `uses-feature` kamery s `required="false"` – jinak by povolení CAMERA vyloučilo
  televize a boxy bez kamery (ověř `aapt dump badging`: kamera jen `uses-feature-not-required`).
  Texty `km_…` v `core/I18nKamera.kt`. Na skutečné televizi LG čtení ověří jen uživatel.
- `webos/obchod/app/app.js`, `style.css` a `index.html` jsou **společné s aplikací pro Samsung**
  (kopie v `tizen/app`); co je jen pro LG, je v `platforma.js` (`window.PlayLingoTv`). Při změně
  společného souboru ho zkopíruj do `tizen/app` – jinak spadne sestavení APK (`samsungBalicek`)
  i unit test `SamsungTest`. Zkouška obou bez televize: `node tizen/test/prijimac.test.js`.
- Televize uživatele má prohlížeč Chromium 38. Kód pro televizi proto musí zůstat ve starém zápisu
  JavaScriptu: žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce.
- Zkoušení bez Androidu: `node webos/posilac.js` posílá z počítače zkušební věty, `pc/` čte
  a překládá na počítači. Nástroj LG (`ares-package`, `ares-install`, `ares-launch`, `ares-inspect`
  z `@webos-tools/cli`) nahraje aplikaci do televize ve vývojářském režimu; `ares-inspect` dá živý
  přístup do běžící aplikace.
- Změny jen ve složkách `webos`, `tizen` a `pc` nemění verzi aplikace pro Android TV a commitují se
  s `[skip ci]`. Balíčky `.ipk` a `.wgt` se necommitují. (Jdou ale do APK – nová verze aplikace
  pro televizi se k lidem dostane až s další verzí pro Android.)

## Televize Samsung (Tizen)

Přehled a zjištění jsou v `tizen/CTI-ME.md`; tady jen to, co je potřeba vědět při úpravách.

- Stejný princip jako LG: televize ukazuje hru z HDMI a pruh, věty dostává z Androidu stejnými
  zprávami (`core/TvSender.kt`). `tizen/app` = kopie `app.js`, `style.css`, `index.html` z
  `webos/obchod/app` + vlastní `platforma.js` (obraz `tizen.tvwindow` pod průhlednou stránkou,
  `tizen.systeminfo`, Zpět = 10009) a `config.xml` (balíček `PlayLingo1`, id `PlayLingo1.PlayLingo`).
  Verze v `config.xml` a `platforma.js` se musí shodovat (hlídá sestavení). Starý zápis JS (ES5).
- APK nese `assets/samsung/receiver.wgt` + `receiver-manifest.json` (`signed: false`); skládá je
  úloha `samsungBalicek` kódem `core/SamsungWgt.kt` (ZIP s pevným datem, přes `buildSrc`).
- **Přímá instalace z Androidu není a záměrně**: Samsung přijme jen balíček podepsaný
  distribučním certifikátem vystaveným na účet Samsung pro DUID konkrétní televize; podpis
  a nahrání (SDB, port 26101) se bez skutečné televize nedají ověřit. Průvodce
  `SamsungInstallActivity` vysvětlí vývojářský režim (Apps → 12345 → Host PC IP), uloží .wgt
  přes systémový dialog, pojmenuje Tizen Studio / komunitní instalátory a **úspěch hlásí jen
  podle ohlášení aplikace z televize** (`TvCheck`: stejná/novější verze = hotovo). Když neběží
  překlad, otevře si port 8787 sám (vlastní `TvSender`) a po úspěchu pošle zkušební větu.
- Rozhodování a čtení zpráv: `core/TvReceiver.kt` (`TvType`, `TvAnnounce`, `TvCheck`,
  `SamsungInstall`), testy `SamsungTest`. Skutečná televize Samsung zatím nikdy nebyla vyzkoušená.
- Převzato podle přijímače PlayCaption pro Samsung (AGPL-3.0) – hlavičky souborů zachovej.

## Xbox

- Druhá konzole vedle PlayStationu (`Prefs.console`, výchozí PlayStation – dosavadní uživatelé si
  nic nevšimnou). Volba: krok Konzole v průvodci, řádek „Konzole“ v Nastavení (`consoleRow`, výběr
  `chooseConsole` → `setConsole` v MainActivity) a souhrnný řádek na Domů. Položka menu se jmenuje
  Konzole; nadpis stránky je PlayStation nebo Xbox a u Xboxu ukazuje `XboxForm` místo párování PS5.
- Přihlášení: kód na microsoft.com/link (device code; čtvereček na stránku, v telefonu tlačítko
  „Otevřít stránku“) → token Microsoft → Xbox Live → XSTS → streamovací token xhome. Ukládá se jen
  obnovovací token, zašifrovaný klíčem z Android Keystore (`playlingo-xbox-v1`, stejně jako
  `LgVault`), jméno hráče a vybraný Xbox. Tokeny se nevypisují; `Redact` skrývá i tokeny Microsoft
  a hlášení dostává obnovovací token mezi tajné hodnoty. Odhlásit při chybě sítě se nesmí – jen
  když Microsoft řekne invalid_grant (`XboxAuth.refreshOutcome`).
- Spojení (`XboxLink`): seznam konzolí → spící Xbox se **nebudí** (jen se každých 20 s zeptá) →
  relace `/v5/sessions/home/play` → WebRTC jen příjem obrazu, H.264 napřed a `b=AS:3000`
  (`XboxSession.preferH264`), kandidáti Teredo rozbalení na IPv4. Zvuk se nepřehrává, ovládání se
  neposílá (jen „ovladač přidán a hned odebrán“ jako webový přehrávač, jinak Xbox po minutě spojení
  ukončí). Snímky I420 jdou do `StreamReader.feedI420` jen tehdy, když o ně čtečka stojí – dál
  stejná cesta jako u PS5 (`grab` → `Crop`, `FrameSignature`, OCR, překlad). Televize dekóduje na
  GPU, telefon do paměti. Opakování řídí `XboxRetry` (core, s testy); stav je `Ps5.Link`, takže
  `LinkStatus`, stránka Domů i hlášení fungují stejně (texty `xb_ls_…`).
- Logika bez Androidu je v `core/Xbox.kt` s testy (`XboxTest.kt`). Knihovna WebRTC
  (`io.getstream:stream-webrtc-android`) přidává do APK asi 10 MB (jen ARM); místní kontrola ji
  překládá proti `classes.jar` z .aar (`ci/mistni-kontrola/build.gradle.kts`).
- Emulátor Xbox nemá: zkouška ukáže jen stránku Xboxu (snímek `11b_stranka_xbox`). Přihlášení,
  spojení a čtení z obrazu ze skutečného Xboxu **umí ověřit jen uživatel**. Převzato podle PlayCaption
  (AGPL-3.0; ten podle xbox-xcloud-player, MIT, a XStreaming, AGPL-3.0) – hlavičky souborů zachovej.

## Pokus: DeepL (experimentální, výchozí vypnuto)

- Nastavení (⚙) → úplně dole část **Experimentální** (`DeeplSection.kt`, volá ji jediná funkce
  `experimentalSection()` v `MainActivity`): volba „Lepší překlad přes internet (DeepL)“, klíč,
  postup „Jak získat klíč“, ověření a měření tohoto běhu. Vypnuté = služba se chová přesně jako dřív
  (žádný dotaz do internetu), zkouška v emulátoru se tím nemění. Pod DeepL je v téže části ještě
  blok „Zpřístupnit záznam v domácí síti“ (viz Záznam ze zařízení).
- **Klíč patří jen hráči.** V repozitáři, testech, APK ani CI žádný klíč není a nesmí být (ani náš
  „sdílený“). Ukládá se zašifrovaný přes Android Keystore (`DeeplKey`, stejný postup jako `LgVault`),
  nikam se nevypisuje; `Redact` maže tvar klíče DeepL (UUID s `:fx`) a `DiagReport.secrets` i uložený
  klíč. Majitel má pro ruční zkoušku klíč mimo repozitář v `%USERPROFILE%\.playlingo\deepl.txt` –
  nikam ho nekopíruj ani nevypisuj; jen pár ručních dotazů, žádné opakované zkoušení.
- Vložení klíče z telefonu: stejný server jako přihlášení (`LoginServer.forPage` + `LoginServer.Custom`,
  jednorázový klíč ve čtverečku), stránka `DeeplPage` v `core/Deepl.kt` (postup, odkaz na DeepL na nové
  kartě, políčko pro vložení – schránku stránka číst nesmí). Běží jen, dokud je vidět její čtvereček.
- Překlad (`TranslatorService` → `improveWithDeepl`): offline překlad se ukáže hned, souběžně jde
  `POST /v2/translate` (původní anglický text bez `IDIOMS`, jména ze slovníku ve značce `<k>` s
  `ignore_tags`, `formality=prefer_less`). Odpověď do 1,5 s, jiná a k témuž zobrazenému řádku
  (`SubtitleTracker.revision` + pořadí zobrazení `showSeq`) pruh potichu vymění, i v televizi LG.
  Čeština dál prochází `Natural.czech` (DeepL v češtině tykání nastavit neumí a občas vyká).
  Vlastní paměť výsledků DeepL (jen v paměti procesu), offline paměť se nemíchá. Chyby (403, 456,
  429, síť) → offline a přestávka (`DeeplBackoff`). Rozhodování, čtení odpovědí a přestávky jsou
  v `core/Deepl.kt` s testy (`DeeplTest`). Měření v logu: `TP_TEST` řádky `DEEPL|ms=…|swap=yes/no/late/stale/cache|chars=…`
  (bez textu a klíče); souhrn v Nastavení a v hlášení o stavu.
- Limit znaků se nebere napevno: ukazuje se to, co vrátí `/v2/usage` (účet majitele má 1 000 000).
- Texty `dl_…` jsou v `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`).

## Záznam ze zařízení (pro Claude na PC majitele)

Když je potřeba vidět, co se na zařízení majitele děje (Android i aplikace v televizi LG/Samsung):

1. Majitel na zařízení s Androidem zapne Nastavení → úplně dole Experimentální → **„Zpřístupnit záznam v domácí síti“**
   (vlastní blok pod DeepL, `logShareRow()` volané z `experimentalSection()`; výchozí vypnuto, samo se vypne po 2 hodinách) a řekne adresu,
   která se ukáže pod řádkem (i nahoře na obrazovce hlášení), např. `http://192.168.1.23:8789/zaznam?k=ab3k7m`.
2. Claude ji stáhne: `.\ci\stahni-zaznam.ps1 http://192.168.1.23:8789/zaznam?k=ab3k7m`
   (nebo `… 192.168.1.23 ab3k7m`, s `-Json` po oddílech). Uloží se do `zaznamy\zaznam-<čas>.txt`
   a `zaznamy\posledni.txt` – složka je v `.gitignore`, záznamy do repozitáře nepatří.
   403 = špatný kód (po 30 špatných se server zavře), 404 / nedostupné = vypnuto nebo vypršelo.

Obsah = celé hlášení o stavu (`DiagReport.build` → `parts`): aplikace a zařízení, spojení, pády
(`pady.txt`), záznam událostí `udalosti.txt`, události z paměti, spojení s PlayStationem a záznam
z televize. Vše prochází `Redact` s `DiagReport.secrets` (i v JSON). Server (`core/LogShare.kt`:
`LogShare` rozhodování s testy `LogShareTest`, `LogServer` sockety podle `LoginServer`) umí jen
`GET /zaznam?k=…` a `/zaznam.json?k=…`, odpovídá jen loopbacku a privátním adresám, nic nepřijímá.
Konec platnosti a kód jsou uložené (`NetLogShare`, vlastní SharedPreferences `zaznam_sit`), takže po
pádu a novém spuštění běží dál se stejným kódem, ale jen do konce 2 hodin.

Záznam z televize: `app.js` drží posledních 300 řádků (`zapis`: start s platformou a verzí, televize,
obrazovky, hledání v síti, připojení/ztráta, u titulků jen id a délka – **nikdy text**, `window.onerror`;
opakované hledání se sloučí do počtu). Po připojení pošle neodeslané řádky zprávou
`{"typ":"zaznam","radky":[…]}` (po 25) a dál každých 10 s. `TvSender` je předá `core/TvLog.kt` →
`filesDir/tv-zaznam.txt` (nejvýš 60 000 znaků). V hlášení je oddíl Televize (posledních 25 řádků).
Záznam z televize umí aplikace v televizi od verze 1.2.0.

Bez sítě: obrazovka hlášení má tlačítka **Kopírovat** (schránka) a **Sdílet** (soubor přes
`ReportProvider`, jediný soubor v `cacheDir/sdileni`, jen ke čtení).

## Texty aplikace a jazyky

Aplikace má dvě na sobě nezávislá nastavení jazyka (`core/Lang.kt`):

- **jazyk překladu** (`Prefs.lang`, stránka Titulky a průvodce) – do čeho se titulky překládají;
  jen `Lang.targets`, tedy bez angličtiny,
- **jazyk aplikace** (`Prefs.uiLang`, Nastavení a pomocník v telefonu) – nabídky a nápověda;
  všech deset jazyků včetně angličtiny. Texty na obrazovce ber vždy přes `tx()` / `Prefs.uiLang`,
  nikdy podle jazyka překladu.

Kdo si jazyk aplikace nezvolil, má ji v jazyce překladu (tak to bylo do 0.63); nová instalace se
řídí jazykem zařízení a pro jazyk, který aplikace neumí, je anglicky. Další jazyk překladu, pro
který aplikace texty nemá, se přidá jen do `Lang.targets`.

Každý nový text musí být ve všech deseti jazycích, jinak neprojde test `AllLanguagesTest`:

- obecné texty: čeština + slovenština v `core/I18n.kt`, angličtina v `core/I18nEn.kt`, ostatní
  v `core/I18nPl.kt`, `I18nDe.kt`, `I18nHu.kt`, `I18nUk.kt`, `I18nEs.kt`, `I18nFr.kt`, `I18nIt.kt`;
- texty kolem PlayStationu: `core/I18nPs5.kt` (`PS5_S` pro češtinu a slovenštinu, `PS5_X` pro ostatní
  včetně angličtiny);
- texty přihlášení přes telefon (kroky `pa_…`, stránka z televize `wp_…`, pomocník v telefonu
  `hp_…`): `core/I18nLogin.kt` (`LOGIN_S`, `LOGIN_X`);
- stav spojení (`ls_…`, `lv_…`, logika v `core/LinkStatus.kt`), hlášení o stavu (`dg_…`) a upozornění
  na pád (`cr_…`): `core/I18nDiag.kt` (`DIAG_S`, `DIAG_X`).
- texty nahrání do televize LG (`lg_…`; chyby `lg_e_<LgError>`, kroky `lg_p_<LgPhase>`):
  `core/I18nLg.kt` (`LG_S`, `LG_X`).
- texty Xboxu a volby konzole (`c_…`, `xb_…`; chyby `xb_e_<XboxFail>`): `core/I18nXbox.kt`
  (`XBOX_S`, `XBOX_X`). Text, který se u Xboxu liší jen jménem konzole, má klíč `xb_<klíč PS>`
  (např. `xb_st_failed`, `xb_ls_image`) a bere se přes `Console.key()` / `ct()` v MainActivity.
- typ televize (`tv_…`, společné `s_tv_desc`, `st_tv_…`, `hp_lg_title`/`hp_lg_text`) a průvodce
  Samsung (`ss_…`): `core/I18nSamsung.kt` (`SS_S`, `SS_X`).
- texty pokusu DeepL (`dl_…`; důvody `dl_p_<Deepl.Problem>`): `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`).
- texty rozložení od přestavby (průvodce `wz_dev…`, `dev_…`, souhrnný řádek a upozornění na Domů
  `sum_…`, `w_…`, části Nastavení `s_setup`, `s_diag`): `core/I18nUi.kt` (`UI_S`, `UI_X`).
- záznam pro vývojáře, oddíl Televize a Kopírovat/Sdílet v hlášení (`zz_…`): `core/I18nZaznam.kt`
  (`ZAZNAM_S`, `ZAZNAM_X`).
- načtení údajů televize LG kamerou (`km_…`): `core/I18nKamera.kt` (`KAMERA_S`, `KAMERA_X`).

Texty, které se přestaly používat, smaž ze všech jazyků. Když se mění postup pro uživatele,
uprav i `NAVOD.md` a stránku Nápověda v aplikaci.

Aplikace pro televizi LG (`webos/obchod`) a Samsung (`tizen/app`, týž `app.js`) má vlastní texty
česky a anglicky přímo v `app.js` a tlačítko Jazyk na úvodní obrazovce; do obchodu LG se podává
s anglickými podklady. Texty v televizi nejmenují značku (aplikace pro LG nemá zmiňovat Samsung).

## Obrazovky a průvodce

- **Menu** (`Section` v MainActivity, v tomto pořadí): Domů, Konzole (nadpis stránky PlayStation /
  Xbox), Titulky (vzhled, jazyk překladu, oblasti), Nápověda a jako poslední **⚙ Nastavení**. Samostatná
  stránka Licence není – je součástí Nastavení. Historie se otevírá z Nastavení.
- **Průvodce prvním spuštěním** (`wizardSteps`, indexy `W_…`): Zařízení (Android TV/box, nebo
  telefon/tablet jako čtečka pro LG/Samsung, `Prefs.readerDevice`; výchozí podle
  `NetInfo.isHandheld`; samo nic nepřepíná – pomocník v telefonu se dál řídí `Prefs.forceTv`) →
  Konzole → Televize (u LG/Samsungu tlačítko nahrání) → Jazyk překladu → Zobrazení titulků
  (povolení; u LG/Samsungu jen vysvětlení) → Připojení konzole → Hotovo.
- **Domů**: velký stav + Spustit/Zastavit (+ Zkouška bez PlayStationu), jeden souhrnný řádek
  „PS5 (jméno) → LG model · připojeno“ (OK = malá nabídka Konzole / Typ televize) a karta upozornění
  jen na to, co chybí (povolení pruhu, model, spárování, aplikace v televizi, vývojářský režim LG,
  zkušební doba ≤ 3 dny). U LG/Samsungu je pod souhrnným řádkem tlačítko nahrání/aktualizace aplikace do
  zvolené televize (`htvinstall`), jakmile se televize jednou ohlásila (do té doby je v upozornění).
  Jednorázové volby na Domů nepatří.
- **Nápověda** ukazuje jen část pro zvolený typ televize (LG, Samsung, nebo povolení přes počítač u Android TV).
- **Nastavení (⚙)**: Konzole a televize (konzole, typ televize + nahrání, zařízení, jazyk aplikace),
  Chování (zkušební verze, kvalita čtení), Povolení, Ladění a hlášení (diagnostický režim, hlášení),
  Licence (po skončení zkušební doby nahoře), Překlad (hra, slovník, statistika, historie) a úplně
  dole Experimentální (DeepL).

## Na co zkouška spoléhá (neměnit jen tak)

- Řádky v logu se značkou `TP_TEST` (`SHOW|`, `VIDEO|`, `LINK|`, `PAIR|`, `START|`…) –
  podle nich `ci/emulator-test.sh` počítá výsledek.
- Věty ukázkové hry v `core/DemoGame.kt` – skript hledá 15 z nich podle textu. Měníš-li věty,
  uprav i skript.
- Parametry spuštění `nowizard`, `autostart`, `ps5test`, `phonelogin`, `pairstep`, `updtest`,
  `updself`, `logintest`, `helperlogin`, `fullapp`, `page` (jména `Section`: HOME, PS5, LOOK, HELP,
  SETTINGS; staré LICENSE = SETTINGS), `wizard` (číslo kroku průvodce, snímky `p2_<velikost>_pruvodce_<krok>`),
  `trtest`, `console` (ps / xbox, snímek `11b_stranka_xbox`) a `TestCardActivity`, `PhoneHelperActivity`.
- Zkouška přihlášení přes telefon (řádky `LOGIN|`, `HELPER|`, snímky `12n`–`12p`) se do výsledku
  nepočítá. V `result.txt` má být `ucet_z_telefonu` aspoň 1 (stránka z televize přijala účet)
  a `pomocnik` aspoň 1 (pomocník přijal odkaz ze stránky televize); na snímku `12n` má být
  „Účet ověřen“ a na `12o` „Televize čeká na účet“.
- Kroky průvodce spárováním se mají vejít na obrazovku i s tlačítky (snímky `12j`–`12l`).
  Podrobný postup proto patří na stránku pro telefon, v televizi jen krátce.
- Zkouška aktualizace (řádky `UPD|`, snímky `15a`–`15d`) se do výsledku nepočítá. `updtest`
  stáhne vydanou verzi (závisí na internetu; emulátor ji nenainstaluje). `updself` nainstaluje
  aplikaci samu přes sebe – na snímku `15c_dotaz` má být dotaz systému na instalaci a v
  `result.txt` má být `aktualizace_nahrazeno` aspoň 1.
- Pořadí položek menu a kroků průvodce – skript se jimi prokliká naslepo šipkami (viz Obrazovky
  a průvodce). Na každém kroku průvodce je po vykreslení vybrané tlačítko Pokračovat (`wnext`, u párování
  `pnext`), takže OK posune dál. Snímky průvodce: `01_pruvodce_zarizeni`, `01b_…konzole`,
  `01c_…televize`, `03_…jazyk`, `03b_…po_stazeni`, `02_…povoleni`, `04_…ps5_krok1`, `04b_…hledani`;
  stránky menu `07_menu_1`–`4` (Konzole, Titulky, Nápověda, Nastavení).
- Zbytky starého názvu „TestPreklad“ (značka logu, `rootProject.name`, názvy souborů
  v sestavování, `TestPreklad.apk` ve vydání, sůl odemykacího kódu v `License.kt`).
  Nepřejmenovávej je: visí na nich zkouška, odkaz ve starších verzích a platnost kódů.

## Poučení z historie (co už se zkoušelo)

- **Spojení s konzolí neměň bez výslovného zadání.** Emulátor PlayStation nemá, takže zkouška
  projde, i když to na skutečné konzoli nefunguje. Ověřit to umí jen uživatel na své televizi.
  Dva pokusy nehlásit se konzoli jako ovladač (0.34 „jen se dívat“, 0.38 „tichý režim“)
  se musely vrátit.
- **Druhý účet PlayStation je záměr.** Když se aplikace připojí herním účtem, konzole hráči
  vypne ovladač a zhasne obraz. Průvodce proto vede na druhý účet.
- **Obraz 1080p jen pro televize s 3 GB paměti a víc** (`core/Perf.kt`). S 1080p na slabších
  televizích se od 0.29 odpojovaly ovladače; od 0.32 berou zase 720p.
- **Opakované připojování ruší ovladače u konzole.** Proto se pokusy po čtyřech krátkých
  spojeních zastaví (0.32) a další pokus se odkládá aspoň o 15 s (0.37).
- **Zvětšený výřez pro drobné titulky nekresli do jedné znovupoužívané bitmapy.** Ve 0.53 se
  to na Androidu 9 třikrát po sobě zaseklo, 0.54 to vrátila.
- **Otisk snímku se počítá ze všech pixelů výřezu** (0.31). Ze vzorků se drobný titulek
  na klidné tmavé scéně přehlédl.
- **Pruh se neschovává podle toho, co je na obrazovce** (0.20) – při HDMI to televize nehlásí.
- **Překládají se jen repliky**, ne menu hry, pokyny k ovládání ani text v jiném jazyce
  (0.33, 0.43, 0.45). Čte se jen spodní třetina obrazu.
- **Přihlášení účtem PlayStation jde jen přes telefon** (QR kód, od 0.19). Přihlášení přímo
  v televizi v přibaleném prohlížeči GeckoView ochrana Sony odmítala a prohlížeč dělal z APK
  176 MB; ve 0.55 bylo obojí odstraněno. Nevracej to. Od 0.57 se v telefonu nic neopisuje
  (viz Přihlášení přes telefon).
- **Velikost výřezu čtení nezrychlí.** Čas jednoho čtení (ML Kit) je ve zkoušce stejný při
  zvětšení 1× i 1,5× i při menší oblasti (měřeno na 0.54, řádky `READ|ocr=…` a `SCALE|`
  ve větvích `test-results-api…`). Menší výřez ani vypnuté zvětšování proto čtení nezrychlí.
- Úpravy výkonu ověřuj na snímcích a časech ze zkoušky, ne odhadem. Emulátor čte jeden snímek
  3–6 s, skutečná televize zlomek toho – z emulátoru jde brát jen porovnání, ne skutečné časy.

## Licence

- chiaki-ng je pod AGPL-3.0, proto musí zdrojový kód aplikace zůstat veřejný. Hlavičky
  převzatých souborů s popisem našich úprav zachovej.
- Věta „Aplikace není produktem společnosti Sony Interactive Entertainment…“ v README zůstává.
