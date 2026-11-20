# PlayLingo – pokyny pro Claude Code

Aplikace pro Android TV. Po domácí síti se připojí k PlayStationu 5 (stejně jako Remote Play),
z kopie obrazu čte anglické titulky, offline je přeloží a ukáže v černém pruhu přes obraz.
Hra sama jde do televize kabelem HDMI. Návod pro uživatele je v `NAVOD.md`.

## Jak se tu píše

- Všechno česky: commity, komentáře v kódu, texty aplikace, návod.
- Commit popisuje, co se změnilo z pohledu hráče, ne jak je to naprogramované
  (např. „0.45: pokyny hry (Press to Start, Select…) nejsou repliky“).
- Uživateli odpovídej česky a bez programátorského žargonu.
- **Nezávislé úkoly souběžně (přání majitele, 20. 11. 2026):** když majitel napíše víc věcí, které na sobě nezávisí
  (jiné soubory, jiná část aplikace), pusť je paralelně přes subagenty (nástroj Agent, každý s jasným zadáním
  a seznamem souborů, na které smí sahat) a sám dělej jen jednu. Verzi, commit a vydání dělá jen hlavní sezení,
  až jsou všechny části hotové a otestované.

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
  - `TvProfile.kt` (+ `core/TvProfiles.kt`) – vzhled pruhu a oblasti zvlášť pro každou televizi LG/Samsung:
    aktivní je ta, která se naposledy ohlásila (platforma + model z `TvAnnounce`), hodnoty aktivní jsou
    v běžném Prefs (klíče `TvProfiles.KEYS`), ostatní v SharedPreferences `tv_profily`. Přepíná
    `TvSender.onAnnounce` v `TranslatorService`; v Titulkách šedý řádek „Vzhled a oblasti pro televizi …“.
    Nová televize začne s kopií posledního nastavení. Nový klíč vzhledu/oblastí přidej do `KEYS`.
  - `TvChoice.kt` – typ televize (Android TV / LG / Samsung): nabídka, televize v souhrnném řádku na Domů, nahrání do televize
  - `SamsungInstallActivity.kt` – průvodce PlayLingem pro televizi Samsung (viz Televize Samsung)
  - `DemoFeeder.kt`, `TestCardActivity.kt` – ukázková „hra“ pro zkoušku bez PlayStationu
  - `ConsoleLink.kt` – společné rozhraní zdroje obrazu (PlayStation `Ps5Link`, Xbox `XboxLink`)
  - `XboxAccount.kt`, `XboxLink.kt`, `XboxForm.kt` – Xbox: přihlášení účtem Microsoft, spojení
    přes vzdálené hraní (WebRTC) a stránka konzole pro Xbox (viz Xbox)
  - `DeeplTranslator.kt`, `DeeplSection.kt` – DeepL (viz DeepL – Kvalita překladu)
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
- `pc` – PlayLingo pro počítač s Windows (viz `pc/CTI-ME.md`), zkušební: okno aplikace
  (`pc/PlayLingo.cmd`, `src/Aplikace.kt` – Domů / Titulky / ⚙ Nastavení ve vzhledu aplikace pro TV)
  čte titulky z okna s obrazem hry, přeloží je a ukáže v pruhu přes okno hry (`src/Pruh.kt`) nebo
  pošle do televize LG. Základ budoucí aplikace pro Windows / handheld: hráč hraje v chiaki-ng na
  hlavním účtu, PlayLingo se ke konzoli nepřipojuje. Texty zatím jen česky. Překládá se proti téže složce `core` jako aplikace,
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

**Rod postav (od 0.97.3)**: profil hry (`core/GameProfiles.kt`) má vedle jmen i postavy s rodem a přezdívkami
(`GameCharacter`, `m("Jason", "Jas")`, `f("Lucia", "Lu")`); univerzální profil zná postavy všech her (jméno
s různým rodem v různých hrách vynechá `Cast`). Postavy jdou se slovníkem (`Glossary.cast`) a po překladu každé
části titulku `Glossary.genders` → `core/CzechGender` (v `GameCast.kt`, testy `GameCastTest`) opraví mužské tvary:
1. osoba podle mluvčího z titulku („LUCIA: …“), 2. osoba podle oslovení v originálu („Lu, you…“, „…, Lucia?“),
3. osoba hned za jménem („Lucia šel“). Mění se jen slova u pomocného slovesa / jména a jen spolehlivé koncovky.
Běží až po paměti překladů (ta je jen podle textu) – ve službě, v `translateSubtitle` i v `DeeplTranslator.merge`.
Zatím jen čeština. Bez jmen mluvčích v titulcích hry funguje jen 2. a 3. osoba.

**Zásada profilů her (přání majitele 20. 11. 2026):** univerzální chování (čte se jen pás s titulky) zůstává pro
všechny hry, profil hry ho jen vylepšuje. Volba „rozsah čtení“ i u vybrané hry: **Jen titulky** = z profilu jen jména,
rod, pojmy a co v titulcích přeskočit, jiná místa obrazovky se nečtou; **Titulky a texty hry** = navíc oblasti, které
zná profil (GTA VI: prompty pro hráče pod titulky; Diablo IV: úkol, menu, tooltipy – `navrhy/preklad-menu.md`).
Texty hry se nemíchají s replikami v pruhu. Bez profilu (univerzální) jen titulky.

**Barva jména z obrazu (od 0.97.28, `core/NameColor.kt`):** po titulku se jménem služba (`askNameColor`) pošle čtečce
obrazu žádost s obdélníkem jména; čtečka ji vyřídí jen u nejbližšího snímku (`StreamReader.requestColor` – barvy se jinak
nekopírují ani nečtou), vezme nejsvětlejší body a jejich barvu (bílá/šedá = nic). Naučená barva (≥ 2 shody, nejbližší
z palety) se dá pruhu (`SubtitleOverlay.learnColor`; profil hry má přednost). Měření a naučené barvy: řádky `COLOR|…|us=`
a oddíl hlášení „barvy jmen ze hry“ → přepsat do `GameProfile.colors`. Jedno jméno se čte nejvýš jednou za 3 s a po naučení
už ne.

**Barvy jmen z profilu hry (od 0.97.27):** `GameProfile.colors` (jméno → barva jako v titulcích hry; GTA VI Jason
žlutě, Lucia modře; GTA V Michael modře, Franklin zeleně, Trevor oranžově) platí i pro přezdívky (`colorsByKey`) a dostane
je `SpeakerColors(preset)`; ostatní postavy mají barvu z palety bez těchto. Z obrazu se barva zatím nečte.
**Aplikace v TV 1.5.6:** se zapnutým ukazovátkem Magic Remote posílá TV OK jako kliknutí – kliknutí při hře = OK
(nabídka vzhledu); tlačítko Zpět se při hře neukazuje (rušilo).

**Od 0.97.35 (aplikace v TV 1.5.8):** OK v TV otevře s nabídkou vzhledu i stav vlevo nahoře (nezmizí po 8 s, zavře se s nabídkou;
oranžový zůstane), stejně rychlá nabídka na Android TV (`StatusPanel.pin`/`unpin` z `quickPreview`). Náhled s volbou „originál“
ukáže i druhý řádek (ukázková anglická věta `pn_ukazka_orig`, `nahled(true)`). Poloha „přes titulky“ má záměrně vyšší pruh
(zakrývá celou oblast anglických titulků), ostatní polohy jsou úzké. Služba otevírá port pro TV s opakováním
(`TvSender.start(retry = true)`, každé 2 s) – po aktualizaci ho na okamžik držela obrazovka aplikace (`TvIdle`) a TV jen hledala.
Neprůhlednost pruhu 0–100 % (`OPACITIES`: 0, 20, 40, 60, 80, 90, 100 na třech místech, od 0.97.30 / TV 1.5.7).

**Od 0.97.39 (aplikace v TV 1.5.10):** menu zleva řádek proti řádku – `MenuView.step` (rozestup položek ve hře, nejmenší rozdíl
sousedních, `GameMenu.rowStep`) → výška řádku překladu = rozestup, písmo nejvýš 62 % řádku (Android `lineHeight` od API 28,
TV `misto:[x,y,rozestup]`). V menu se čte každých 0,4 s, zavře se až po 4 čteních bez něj (`MenuTracker.MISSES`), `MENU|off|…`
v záznamu ukáže, co se přečetlo.

**Menu hry od 0.97.36 (aplikace v TV 1.5.9):** překlad menu už není v pruhu dole – položky menu zleva **pod sebou hned vedle menu**,
zarovnané vlevo (`MenuView.box` → `SubtitleOverlay.show(box=…)` a `TvLook` poloha `misto` + `misto:[x,y]`; starší TV ho položí jako
pruh), lišta Nastavení v řádku pod anglickým popisem vpravo. Značka vybrané „» “ (šipku ▸ webOS neuměl – čtvereček). `MenuTracker`:
položky se během otevřeného menu doplňují (čtečka občas některou vynechá), vybraná = ta jediná, která v čtení chybí (tmavé písmo na
bílém pruhu se nepřečte), menu se zavře až po 2 čteních bez něj (neblikne); text se posílá jen při změně.

**Menu hry od 0.97.34 (zatím GTA VI, `core/GameMenu.kt`, testy `GameMenuTest`):** základ podle snímků majitele (`zaznamy/hra-…`) –
menu otevřené zleva (Map / Game / Store / Help / Photo Studio / Settings), horní lišta Nastavení (Control Scheme … Account) a nadpis
s popisem vybrané volby vpravo; jednotlivé volby Nastavení záměrně ne. `GameProfile.menuSpec` (`MenuSpec`: položky, záložky, pevné
překlady cs/sk, jiné jazyky přes překladač). Služba (`checkMenu` v `tick`) jen když je „Titulky a texty hry“ a v pruhu není titulek:
jednou za 1,5 s (v menu 0,7 s) `StreamReader.grabMenu` (horních 60 % obrazu, šedě, nejvýš 960 bodů, vlastní bitmapa – nezasahuje
do čtení titulků) → OCR → `GameMenu.read`. Vybraná položka = výrazně světlejší řádek (bílý pruh, `meanGray`). Překlad se ukáže
**v pruhu místo titulku** („Mapa · Hra · ▸ Obchod …“ a pod tím „Nadpis: popis“) – v menu titulky nejsou, takže funguje i v TV
LG / Samsung beze změny aplikace v TV; dokud je menu vidět, titulky se nečtou. V záznamu `MENU|…`. Na skutečné hře neověřeno.

**Texty hry od 0.97.25 (aplikace v TV 1.5.5):** volba Titulky → Hra → „Rozsah čtení“ (`Prefs.gameTexts`, výchozí titulky
a texty hry). GTA (profil `prompts = true`): poslední krátký řádek bez mluvčího a bez interpunkce pod replikou je pokyn
(`TextLogic.markPrompt`, `Segment.prompt`) – překládá se zvlášť, v pruhu menší šedý řádek, do TV `{"pokyn":true}`; s „jen
titulky“ ho `SubtitleOverlay` vynechá. Diablo IV: chat se nečte (`GameProfile.skip`, regex v `Diablo4.SKIP`), mluvčí
„Jachal says:“ (`SPEAKER_SAYS`). Jason má i přezdívku J-dog.

**Profil hry dál (od 0.97.4)**, vše v `core/GameProfiles.kt` (testy `GameCastTest`):
- `terms` – herní pojmy s pevným překladem po jazycích (jdou do `Prefs.glossary` před vlastní slovník, ten má
  přednost); jen pojmy, které mají v 1. a 4. pádě stejný tvar (výraz se vkládá do věty hotový).
- `hud` (přesná shoda řádku se přeskočí: WASTED…) a `menu` (aspoň 2 řádky z něj = menu, nečte se nic) →
  `GameScreen`, předává se do `TextLogic.assemble(screen = …)`. Univerzální profil má sjednocení všech her.
- `GameGuess` – odhad hry z nezaměnitelných jmen v titulcích (aspoň 3 různá, jiná hra méně než polovinu);
  **nic nepřepíná** (přání majitele), jen uloží `Prefs.gameGuess` a v ⚙ → Čtení a překlad → Hra se ukáže
  oranžový řádek „Grand Theft Auto VI?“ s dialogem Přepnout / Nechat (`dismissGameGuess`, pak se stejná hra nenabízí).
- Nastavení → Hra (`gameSection` v MainActivity): Profil hry, případně odhad, rozbalovací „Postavy, místa
  a pojmy“ (postavy ♀/♂, jména, pojmy, nečtené nápisy). Texty `gp_…` v `core/I18nGame.kt`.
- **Názvy aut, ulic, čtvrtí a věcí ve hře se nepřekládají, ani když jsou to běžná anglická slova** (přání
  majitele, od 0.97.5: v telefonu a na mapě hry jsou anglicky, přeložené by mátly). V profilu hry jsou
  (u GTA VI v `GTA6_COMMON`) a zároveň v `AMBIGUOUS`, takže je univerzální profil ani odhad hry nebere.
  Slova z menu (Map, Help, Settings…) do jmen nepatří, jen do `menu`. Překlad v závorce za názvem
  záměrně ne – pruh by se roztáhl přes další text.
- Další profily (od 0.97.11) jsou v `core/GameProfilesMore.kt` po hrách (`Gta5`, `Rdr2` – doplněk k `OfflinePrep.NAMES`,
  `AlanWake2`, `ReRequiem`, `Bond007`, `Diablo4`; každá `COMMON`/`NAMES`/`CAST`/`TERMS`/`HUD`/`MENU`). Profil jen pro hry **bez
  oficiální češtiny** (Cyberpunk 2077, Ghost of Yōtei, Marvel's Wolverine ji mají). Jednopísmenná jména (M, Q) platí
  jen jako mluvčí (`Cast.mentions` je v textu nehledá). Běžná slova z menu (Use, Open, Fine…) do `menu` nepatří.
- Ulice ve všech hrách: `Glossary.STREET` (1–3 slova s velkým písmenem + Street/St/Avenue/Drive/Road…)
  nechá název v originále i bez seznamu; testy v `GlossaryTest.streetNamesStayInOriginal`.

**Ladění profilu ze hry (od 0.97.4):** záznam v síti má navíc `GET /snimek.jpg?k=…` (celý poslední snímek,
šedý, nanejvýš 1280 px – `StreamReader.still`, čtení titulků neovlivní) a hlášení oddíly „titulky ze hry“
(`SubtitleHistory`, originál → překlad) a „co aplikace četla v oblasti titulků“ (`core/ScreenReads.kt`: surový
text z OCR a co se z něj vzalo). Během hraní na PC: `.\ci\sbirej-hru.ps1 <adresa ze zařízení>` ukládá snímky
(jen změněné) a záznam do `zaznamy\hra-<čas>\`. Podle toho se ladí `hud`, `menu`, jména a pojmy – ne odhadem.
Jednodušší cesta je PlayLingo pro PC (`pc/`, hra v chiaki-ng) se zapnutým ⚙ → Ladění → Záznam hry do složky:
barevné snímky celé hry a `zaznam.txt` s přečteným textem a polohou jdou rovnou do `zaznamy\pc-<čas>\` (viz `pc/CTI-ME.md`).

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

- Při každé změně aplikace zvyš v `app/build.gradle.kts` obojí: `versionCode` (o 1, např. 97 → 98)
  i `versionName`, a stejná čísla v `BuildInfo` na konci `MainActivity.kt`.
  **Číslování od 0.97 (přání majitele, nechce se dostat k 1.0):** po 0.97 jde 0.97.1, 0.97.2, 0.97.3…
  (`versionName` = "0.97.N", `versionCode` dál roste o 1: 98, 99, 100…). Aktualizace se řídí jen
  `versionCode` (`UpdateCheck.parse`), název je jen k zobrazení – tečky navíc nevadí.
  Název commitu začíná číslem verze: `0.97.1: …`.
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
  Před otevřením přihlášení Sony smaže cookies a úložiště WebView (`openLogin`, od 0.97.28) – hráč vždy píše e-mail
  a heslo a vědomě volí účet (jinak Sony potichu vzala naposledy přihlášený, často herní účet → CE-110032-7).
- **Jméno účtu a žádný účet potichu (od 0.97.28).** Ověřený účet se ukládá s jménem (Online ID):
  `Prefs.setPsnAccount(id, name)` / `Prefs.psnName` ze všech cest – stránka z televize (`Account.name`), pomocník
  v telefonu, ruční opsání kódu (`PsnAuth.accountForCode` → `/userinfo`, `PhoneLogin.nameFromUserInfo`).
  `PhoneHelperActivity.useForTv` už nebere první účet z telefonu – účty z `Prefs.phoneAccounts` nabídne krok
  „Účet pro PlayLingo“ se jmény. Účet bez jména (uložený před 0.97.28) = „(jméno neznámé)“.
- Ruční opsání kódu (index 4, na obrazovce krok 4 spárování) zůstává jako poslední záloha a od 0.66 je úplné samo
  o sobě: má vlastní čtvereček, který vede rovnou na přihlášení Sony (jako před 0.57), a z kroku
  se čtverečkem na něj vede tlačítko „Stránka se neotevřela“. Je pro případ, že se stránka
  z televize v telefonu neotevře (telefon na jiné Wi-Fi nebo na mobilních datech). Tuhle cestu
  neodstraňuj.
- **Pořadí spárování PS5 od 0.97.28** (`ps5Form`, `pairNext`, Zpět): index → krok na obrazovce: 0 zapni PS5 (1),
  1 najdi PS5 (2), **7 Účet pro PlayLingo (3)** – „Použít účet JMÉNO“ / „Přihlásit jiný účet“ / účty z telefonu
  se jmény, čtvereček `HelpLinks.PS5_PREP`; 2–4 přihlášení tím účtem (4); 6 na PS5 přepnout na JMÉNO a Remote Play
  (5, `pg_1_text` s `%s`); 5 kód z PS5 (6, `pg_4_text`). V krocích 5 a 6 řádek „✔ Ověřený účet: JMÉNO“ s malým
  „Přihlásit jiný účet“ (`otherAccount`: zapomene účet i jméno, zpět na krok 3). Chyba párování: `ps5_fail` +
  `ps5_fail_user` (CE-110032-7, jméno), hlavní tlačítko „Zkusit znovu“ (`pairFailed`) a vedle „Přihlásit jiný účet“.
  Úspěch: `ps5_ok` se jménem. Účet se volí PŘED nastavením na PS5, protože PS5 spáruje jen, když číslo vyvolá
  stejný uživatel, jakým se aplikace přihlásila. Indexy 0–6 a `pairstep=2` drží zkouška.
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
- **Vzhled pruhu v televizi** (od 0.86, aplikace v televizi 1.3.0): `ukaz` nese pole `vzhled`
  (`core/TvLook.kt`, s testy `TvLookTest`) – průhlednost, písmo, barva, tučně, poloha (přes titulky /
  nad titulky / pevné místo), oblast čtení a kde byl anglický titulek, případně originál. Skládá ho
  `SubtitleOverlay.show` ze stejných hodnot jako pruh na Android TV; televize (`vzhledPruhu` v `app.js`)
  pruh položí stejnými pravidly jako `ScreenLayout`. Do té doby televize kreslila pruh pořád stejně
  (černý, dole) bez ohledu na Nastavení. Písmo: sp × 50/22 = px na obrazovce 1080.
- **Poloha „Těsně nad anglickým titulkem“** (od 0.88, `ScreenLayout.hug`, `Position.ABOVE_TEXT`, v televizi
  `poloha: "tesne"`, aplikace v televizi 1.3.1): pruh jde těsně nad nalezený anglický řádek. „Nad oblastí
  čtení“ (`ABOVE`) stojí na místě nad celou oblastí. V uloženém rozložení je to 7. číslo = 2 (starší verze
  ho čtou jako „přes titulky“).
- **Vzhled z ovladače televize** (od 0.89, aplikace v televizi 1.4.0, `core/TvSettings.kt` s testy
  `TvSettingsTest`): Android pošle `{"typ":"nastaveni",…}` (po připojení a po každé změně, v
  `reloadSettings`), televize při hře na OK ukáže nabídku (pozadí, velikost, poloha, originál; `panel…`
  v `app.js`, náhled hned po změně) a pošle `{"typ":"zmen","klic":…,"hodnota":…}`. `TvSender` ji předá
  jen po `TvSettings.parse` (jen tyhle 4 volby a hodnoty z `Prefs.OPACITIES`/`TEXT_SIZES`/polohy bez
  vlastní), `TranslatorService.applyTvChange` uloží do Prefs (profil té televize) a `reloadSettings`.
  Telefon zůstává jediné místo s celým nastavením. Seznamy hodnot jsou na třech místech (Prefs,
  TvSettings, app.js) – měň je najednou.
- **Stav v televizi a nová úvodní obrazovka** (od 0.97.14, aplikace v televizi 1.5.0): při hře je vlevo nahoře
  přehled v bodech jako Domů (`stavKresli` v `app.js`): vlastní řádky televize (HDMI, PlayLingo · připojeno / hledám)
  a pod nimi řádky z Androidu – zpráva `{"typ":"stav","radky":[{"barva":"ok|pozor|info","text":…}]}`
  (`TvSender.statusMessage` / `sendStatus`, obsah `TvStatus.rows` v `TvChoice.kt`: konzole a obraz, překlad
  neběží, jazyk + DeepL, hra). Posílá ji služba každé 2 s (jen změna) a `TvIdle` z otevřené aplikace. Zelený
  přehled po 8 s / prvním titulku zmizí, oranžový zůstává. Žádné věty („Hledám PlayLingo…“) – jen stavy.
  Úvodní obrazovka: řada HDMI 1–4 a pod ní Vzhled titulků (spustí naposledy použitý vstup a po připojení otevře
  nabídku vzhledu, `chciVzhled`), Ukázka, Nápověda (bod 5 = OK při hře), Jazyk, Ukončit; šipky v `sipka`.
- **Rychlá nabídka vzhledu na Android TV** (od 0.97.17, `QuickLook.kt`): stejné 4 volby jako OK v TV LG, otevře ji
  tlačítko zvolené v ⚙ → Obecné („Tlačítko vzhledu titulků“, `Prefs.quickKey`, `learnQuickKey`; OK, šipky, Zpět,
  Home, hlasitost nejdou – `QuickLook.FORBIDDEN`). Tlačítka chytá `TitulkyAccessibility.onKeyEvent`; filtr tlačítek
  (`FLAG_REQUEST_FILTER_KEY_EVENTS`) se zapíná jen když je tlačítko nastavené (`updateKeyFilter`) – jinak by systém
  posílal přes službu každé tlačítko. Změny přes `TranslatorService.quickChange` → `applyTvChange` (jako z TV LG),
  bez titulku ukázková věta (`quickSample`). Jen když běží překlad a aplikace není vidět. Funguje-li tlačítko při HDMI,
  záleží na výrobci TV – ověří jen majitel; po aktualizaci může být potřeba vypnout a zapnout službu v Usnadnění.
- **Stav přes hru i na Android TV** (od 0.97.23, `StatusPanel.kt`): stejné body jako v TV LG (`TvStatus.rows`) vlevo nahoře
  přes obraz, tlačítko rychlé nabídky v rámečku. Služba každé 2 s (`panelTick`, jen typ TV Android TV, ne v ukázce ani když
  je aplikace vidět); ukáže se při změně, zelený zmizí po 8 s / s prvním titulkem, oranžový zůstává.
  **Od 0.97.40 výchozí vypnuto** (přání majitele – stav stačí na Domů): volba ⚙ → Obecné „Stav přes hru“ (`Prefs.statusOverlay`,
  klíč `stav_pres_hru`, texty `so_row`/`so_desc` v `I18nQuick.kt`); bez ní se panel neukáže ani s rychlou nabídkou vzhledu.
- **Od 0.97.22 (aplikace v televizi 1.5.4):** okraj pruhu nad a pod textem 0–3 (`Prefs.barPad`, `BAR_PADS`, klíč `bar_pad`
  v `TvProfiles.KEYS`, Titulky → Pruh s překladem; do TV pole `okraj` v `TvLook`). Ve stavu v TV je OK jako tlačítko
  v rámečku (`klavesa`), ne šedá tečka. Po nahrání do TV LG se hned uloží nová verze (`TvSender.lastSeen`,
  `Prefs.tvSeen`) – dřív Domů hlásilo starou, dokud se TV znovu nepřipojila – a ukáže se dialog s potvrzením.
  Tlačítka v kartách (Domů, Nápověda) jsou `ui.smallButton` (žlutá; běžné `ui.button` v kartě splývá).
- **Od 0.97.21 (aplikace v televizi 1.5.3):** velikost písma je číslo 12–48 po dvou (`Prefs.TEXT_SIZES`,
  `TvSettings.TEXT_SIZES`, `VELIKOSTI` v `app.js` – měň najednou, hlídá `TvSettingsTest`), slovní popisky nejsou. Okraje
  pruhu jsou podle velikosti písma (Android `SubtitleOverlay.show`, TV `padding` v em), bez pevné výšky 150 px.
- **Od 0.97.20 (aplikace v televizi 1.5.2):** Originál pod překladem je po
  mluvčích (řádek = mluvčí, v `TvLook` s `
`) a jméno má stejnou barvu jako v překladu (`SubtitleOverlay.format`,
  `puvodniRadky` v `app.js`). Pruh v TV má zaoblené rohy. Barvy jmen přiděluje PlayLingo (`SpeakerColors`), z obrazu je nečte.
- **Domů hlásí starou aplikaci v televizi** (`TvChoice.outdated`: ohlášená verze vs. `receiver-manifest.json`
  v APK, text `w_tv_old`), s tlačítkem k nahrání.
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
  Tlačítko skeneru je v `LgInstallActivity` pod políčkem adresy (dokud není adresa, je hlavní); k čemu kamera
  je (`km_why`), řekne dialog před žádostí o povolení.
  **Dva tvary `LgInstallActivity`:** *stručný* (TV spárovaná v `LgVault` a známá adresa) = stav „LG OLED55B7 ·
  1.5.6 → 1.5.8“, jedno velké tlačítko („Aktualizovat v TV“ při starší verzi podle `TvChoice.outdated`, „Nahrát
  do TV“ poprvé, „Spustit PlayLingo v TV“ při aktuální), postup, řádek dnů vývojářského režimu a řádek „Jiná TV
  nebo potíže…“; *příprava* (nespárovaná TV, chyba nahrání, nebo první zjištění neskončí krokem READY, tj. TV /
  vývojářský režim nejde najít) = karta krok za krokem, adresa, kamera, Passphrase. Příprava se rozbalí
  natrvalo (`expanded`), ať stránka neskáče. Delší vysvětlení (síť, prodloužení režimu, nahrání z počítače)
  je v Nápovědě (`hc3_lg`), ne na stránce.
  **Pomocník krok za krokem** (od 0.91, `core/LgSetup.kt` s testy `LgSetupTest`, síť `LgFinder.kt`): karta
  v přípravě `LgInstallActivity` se každé 3 s (jen když je vidět) sama odškrtává – televize v síti (SSDP
  `urn:lge-com:service:webos-second-screen:1`, záloha projití /24 na portech 3000/9922; nalezenou adresu
  sama vyplní), vývojářský režim (otevřený port 9922), Key Server (9991; u spárované televize se
  přeskočí), Passphrase (6 znaků 0–9/A–F). Rozepsaný je jen krok na řadě; u vývojářského režimu odkaz
  na účet LG (telefon: tlačítko, televize: čtvereček). Dřívější texty `lg_prep…` nahradily `lg_s_…`.
  Od 0.86 umí `LgScanActivity` s `EXTRA_MODE = MODE_PS5` přečíst i osmimístné číslo pro spárování
  z obrazovky PS5 (`core/Ps5CodeScan.kt`, testy `Ps5CodeScanTest`); tlačítko „Načíst číslo…“ je
  v posledním kroku párování v MainActivity (jen telefon s kamerou). `MODE_PS5_IP` čte adresu PS5
  z Nastavení → Síť → Stav připojení (`Ps5CodeScan.ip`: adresa u „IPv4“, jinak jediná domácí adresa
  nekončící .1/.254); tlačítko je od 0.97.37 vždy hned pod políčkem adresy (malé „📷 …“), stejně pod číslem z PS5
  a v LgInstallActivity pod adresou i pod Passphrase (jen telefon s kamerou – `LgScanActivity.available`).
- Políčka pro adresu (PS5 i LG) nastavuje `IpField`: klávesnice pro adresy a čárka/mezera → tečka
  (česká číselná klávesnice má místo tečky čárku).
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

## DeepL – Kvalita překladu (od 0.97.28 v ostrém provozu, výchozí vypnuto do vložení klíče)

- **Kde:** Titulky → rozbalovací skupina **Kvalita překladu** hned pod jazykem překladu (`subtitlesPage`, `lookOpen ==
  LOOK_DEEPL`, tag `lkdl`) s celou `DeeplSection` (zapnout/vypnout, soukromí, klíč, ověření, zbývající znaky, měření,
  odebrat) a krok průvodce **Lepší překlad (DeepL)** (`W_TRANSLATE`, `build(content, wizard = true)`). Do 0.97.27 byl
  DeepL pokus v ⚙ Nastavení → Experimentální – tam už není (zůstala Zkušební verze a Ladění a hlášení se záznamem
  v síti). Řádek jazyka na Domů, řádek Režim překladu (⚙ → Čtení a překlad) a Nápověda kap. 4 vedou do Titulků
  s otevřenou skupinou (`openQuality()`). Jedna instance `deeplSection()` (drží rozepsaný klíč).
- **Cesta ke klíči ve třech krocích** (`keySteps`): televize = **tři čtverečky vedle sebe** – 1. registrace
  (`Deepl.SIGNUP_URL`, rovnou plán Developer), 2. stránka s klíči (`KEYS_URL`, ikona kopírování), 3. stránka z televize
  pro vložení (`DeeplPage`) + „⏳ Čekám na klíč z telefonu…“ a „Napsat klíč ručně“ (`manual`). Telefon / tablet (ne
  `Prefs.isTv`) = tlačítka Otevřít DeepL / Otevřít stránku s klíčem a políčko. Stránka pro telefon má stejné 3 kroky.
  První uložený klíč DeepL rovnou zapne (`store`). Průvodce: bez klíče hlavní tlačítko „Přeskočit – zůstat u offline
  překladu“ (`wnext`) a Zpět (bez Přeskočit průvodce), s klíčem „✔ DeepL je nastavený“ a Pokračovat.
- **DeepL API (ověřeno na deepl.com 20. 11. 2026):** registrace API = plán **Developer** („Test out the API for free“,
  1 000 000 znaků **celkem, bez měsíčního obnovení**, pak Growth); stránka s klíči = záložka „API Keys & Limits“, celý
  klíč se nezobrazuje, kopíruje se ikonou. Jestli registrace chce platební kartu, se z webu ověřit nedalo (texty proto
  jen „kdyby chtěl kartu, plán nic nestojí“). Klíč Developer nemusí končit `:fx` – texty to netvrdí; `Deepl.host` podle
  `:fx` dál volí api-free / api. Odkazy jsou přímo v APK (ne přes `pomoc/`) – když je DeepL přesune, oprav
  `SIGNUP_URL` / `KEYS_URL` i `pomoc/deepl.md`.
- Vypnuté = služba se chová přesně jako dřív (žádný dotaz do internetu), zkouška v emulátoru se tím nemění.
- **Klíč patří jen hráči.** V repozitáři, testech, APK ani CI žádný klíč není a nesmí být (ani náš
  „sdílený“). Ukládá se zašifrovaný přes Android Keystore (`DeeplKey`, stejný postup jako `LgVault`),
  nikam se nevypisuje; `Redact` maže tvar klíče DeepL (UUID s `:fx`) a `DiagReport.secrets` i uložený
  klíč. Majitel má pro ruční zkoušku klíč mimo repozitář v `%USERPROFILE%\.playlingo\deepl.txt` –
  nikam ho nekopíruj ani nevypisuj; jen pár ručních dotazů, žádné opakované zkoušení.
- Vložení klíče z telefonu: stejný server jako přihlášení (`LoginServer.forPage` + `LoginServer.Custom`,
  jednorázový klíč ve čtverečku), stránka `DeeplPage` v `core/Deepl.kt` (kroky 1–3, odkazy na DeepL na nové
  kartě, políčko pro vložení – schránku stránka číst nesmí). Běží jen, dokud je vidět její čtvereček: v Titulkách
  s otevřenou skupinou Kvalita překladu nebo v kroku průvodce (podmínka `deeplUi?.stopPage()` v `render()`).
  Na televizi bez klíče se čtvereček ukáže sám – dlouhý klíč se ovladačem psát nedá; ruční políčko je až po
  „Napsat klíč ručně“ (`DeeplSection.manual`).
- Se zapnutým DeepL ukazuje i karta „Režim překladu“ (Nastavení → Čtení a překlad) „Offline + DeepL“
  (`dl_mode_on`), zapnutý bez klíče `dl_mode_idle` – dřív tam stálo jen „Offline“ a mátlo to.
- **Od 0.97.2 se ukazuje vždy jen jedna varianta** (`deeplThenShow`): počká se na DeepL (nejvýš
  `Deepl.WAIT_MS` = 2,5 s) a ukáže se jeho překlad; offline jen když DeepL nepřijde včas / selže
  (`DEEPL|…|deepl / timeout-offline / error-…-offline / stale`). Výměna offline → DeepL v pruhu
  vypadala jako nová věta a majitel ji nechtěl. Následující popis výměny platí do 0.97.1:
- Překlad (`TranslatorService` → `improveWithDeepl`): offline překlad se ukáže hned, souběžně jde
  `POST /v2/translate` (původní anglický text bez `IDIOMS`, jména ze slovníku ve značce `<k>` s
  `ignore_tags`, `formality=prefer_less`). Odpověď do 1,5 s, jiná a k témuž zobrazenému řádku
  (`SubtitleTracker.revision` + pořadí zobrazení `showSeq`) pruh potichu vymění, i v televizi LG.
  Čeština dál prochází `Natural.czech` (DeepL v češtině tykání nastavit neumí a občas vyká).
  Vlastní paměť výsledků DeepL (jen v paměti procesu), offline paměť se nemíchá. Chyby (403, 456,
  429, síť) → offline a přestávka (`DeeplBackoff`). Rozhodování, čtení odpovědí a přestávky jsou
  v `core/Deepl.kt` s testy (`DeeplTest`). Měření v logu: `TP_TEST` řádky `DEEPL|ms=…|swap=yes/no/late/stale/cache|chars=…`
  (bez textu a klíče); souhrn v Nastavení a v hlášení o stavu.
- Limit znaků se nebere napevno: ukazuje se to, co vrátí `/v2/usage` (účet majitele má 1 000 000 – plán Developer).
  Texty neříkají „tento měsíc“ (`dl_usage`, `dl_p_quota`) – u plánu Developer se limit neobnovuje.
- Texty `dl_…` a `wz_tr_…` jsou v `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`); kroky `dl_s1`–`dl_s3`, `dl_s3_here`.

## Záznam ze zařízení (pro Claude na PC majitele)

Když je potřeba vidět, co se na zařízení majitele děje (Android i aplikace v televizi LG/Samsung):

1. Majitel na zařízení s Androidem zapne Nastavení → úplně dole Experimentální → **„Zpřístupnit záznam v domácí síti“**
   (od 0.97.15 ve skupině Ladění a hlášení, `logShareRow()` volané ze `settingsDiag()`; výchozí vypnuto, samo se vypne po 2 hodinách) a řekne adresu,
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

**Hlášení od jiných hráčů (od 0.90):** tlačítko **Do telefonu** na obrazovce hlášení zapne záznam v síti
(když neběží) a ukáže čtvereček na `GET /hlaseni?k=…` – stránka pro telefon (i iPhone) s tlačítkem ke
stažení (`/hlaseni.txt`, `Content-Disposition: attachment`) a textem k označení. Hráč soubor pošle chatem
nebo e-mailem. Záměrně bez e-mailové služby (Resend apod.): klíč by musel být v APK a šel by zneužít.
Pro rozbor odpojování zapisuje služba i `NET|` (síť ztracena/připojena), `SCREEN|` (televize se uspává),
`MEM|` (dochází paměť), `STOP|tlačítkem v notifikaci` a `PS5|quit … | trvalo=…s | síť=… | pokus=…`;
seznam ukončení ze strany PS5 je v hlášení („PlayStation ukončil spojení“) a důvod na Domů (`ls_ended`).

## Návrhy na později (`navrhy/`)

- `navrhy/preklad-menu.md` – překlad menu a UI hry (box s přeloženým menu vedle originálu, zvýraznění vybrané
  položky, tooltipy v Diablu IV). Schváleno majitelem jako samostatná session po profilu Diablo IV; nezačato.

## Stránky s pomocí (`pomoc/`)

Čtverečky a tlačítka v aplikaci (`core/HelpLinks.kt`) vedou od 0.97.30 na **GitHub Pages**
`https://adusa89.github.io/PlayLingo/<stránka>.html` (zdroj `pomoc/*.md`, zveřejňuje `.github/workflows/pomoc.yml`
jen z `main`, Jekyll s `pomoc/_config.yml`), ne přímo na stránky LG, Sony, Samsungu: na každé stránce je hlavní odkaz,
záložní odkazy a co hledat. Do 0.97.29 vedly na github.com/…/blob/… – telefon s aplikací GitHub je otevřel v ní a ta chtěla
přihlášení (majitel 20. 11. 2026). Pages musí v repozitáři zapnout správce (Settings → Pages → Source: GitHub Actions).
Když výrobce stránku přesune, oprav odkaz v `pomoc/*.md` – aplikace se měnit nemusí. Commit s `[skip ci]` přeskočí
i zveřejnění stránek: pak spusť „Stránky s pomocí“ ručně v záložce Actions (z `main`).
Kotvy jsou `<a id="…">` (např. `lg.md#ucet`, `ps5.md#ucet`). Stránky jsou česky s anglickým shrnutím, pro iPhone i Android.
Odkazy občas ověř: `grep -oh "https://[^) ]*" pomoc/*.md | sort -u` a `curl -L -o /dev/null -w "%{http_code}"`
(microsoft.com/link vrací robotům 403, v prohlížeči funguje).

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
- texty DeepL (`dl_…`, krok průvodce `wz_tr_…`; důvody `dl_p_<Deepl.Problem>`): `core/I18nDeepl.kt` (`DEEPL_S`, `DEEPL_X`).
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

## Kontrola před každým vydáním (přání majitele, 20. 11. 2026)

Než zvýšíš verzi a pošleš ji, projdi tohle a v odpovědi napiš, co bylo zkontrolováno a co ne:

1. **Obě zařízení**: stejné APK běží na Android TV i v telefonu (čtečka pro LG/Samsung). Co se změnilo na jednom,
   zkontroluj na druhém (`Prefs.isTv`, `Prefs.tvType`, `NetInfo.isHandheld`), a u LG/Samsungu i aplikaci v televizi.
2. **Výkon**: nic drahého v `render()`, tickeru MainActivity, `TvStatus`, ve čtení snímku (`TranslatorService.tick`,
   `StreamReader`) – žádné dešifrování klíčů, čtení souborů, překreslování celé stránky bez potřeby, nic navíc
   při každém snímku. Aplikace bývá otevřená celou hru (telefon).
3. **Vzhled**: krátké stavové řádky, rozbalovací skupiny, žádné vysvětlující věty mezi řádky, žádné tykání, „TV“.
4. **Texty** ve všech 10 jazycích, nepoužívané smazané (`AllLanguagesTest`), Nápověda a `NAVOD.md` podle nových cest.
5. **Testy**: `gradlew -p ci/mistni-kontrola test`, při změně `core` i `gradlew -p pc installDist`, při změně
   aplikace v televizi `node tizen/test/prijimac.test.js` a kopie do `tizen/app`.

## Zásady vzhledu (přání majitele, platí pro všechny obrazovky)

- **Stránky jsou věcné, hezké, bez balastu.** Domů ukazuje jen stav v krátkých bodech s tečkou (zelená = jede,
  oranžová = hráč musí něco udělat – a pak s tlačítkem, šedá = jen informace). Žádné vysvětlující věty,
  rady, varování „může vypadávat“ ani návody (např. ne „Wi-Fi 2,4 GHz – může vypadávat“, ne „telefon může
  při zhasnutém displeji…“ – Wi-Fi 2,4 GHz je prostě zelený řádek „Síť: Wi-Fi 2,4 GHz“).
- **Vysvětlení patří do Nápovědy** (kapitoly v `help()`), popis volby do **dialogu při jejím zapnutí**
  (`confirmOn` v MainActivity) – ne jako šedý text mezi řádky.
- **Související volby do rozbalovacích skupin** po vzoru Nápovědy (`settingRow` s ▼/▲, barevná šipka) –
  majitel Nápovědu v tomhle stylu výslovně chválil. Akce jako „Nastavit oblasti“ jsou řádky ve stejném
  stylu (s „›“), ne velká žlutá tlačítka; velké žluté je jen hlavní akce stránky (Spustit, Spárovat).
- **Stavové hlášky, ne věty a bez tykání** (přání majitele, 20. 11. 2026): na Domů, v řádcích a v menu jen
  „Popis · hodnota“ („LG OLED55B7 · připojeno“, nejistý odhad jen „Grand Theft Auto VI?“), ne „Hraješ asi …“ ani jiné oslovení hráče. Kratší
  i v úzkém menu telefonu. Platí pro nové texty ve všech jazycích; staré s tykáním přepisuj, když na ně narazíš.
- **„TV“ místo „televize“** v krátkých textech (řádky, tlačítka, stavy, kroky nahrávání): „Nahrát do TV“,
  „PlayLingo v TV LG 1.4.0“. Slovo televize jen v Nápovědě a delších popisech. „DeepL online“, ne „přes internet“.
- **Krátké stavové řádky:** „PS5-151 · připojeno“, „LG OLED55B7 · připojeno“ – jméno a jedno slovo stavu,
  žádné dlouhé věty. Popisky voleb bez závorek s vysvětlením („Diagnostický režim“, ne „… (co aplikace čte)“).
- **Rozlišuj zařízení:** co dává smysl jen na Android TV / boxu (Zpět do hry HDMI, povolení pruhu) a co jen
  v telefonu jako čtečce pro LG/Samsung (běh na pozadí) – `Prefs.isTv`, `Prefs.tvType`.

## Obrazovky a průvodce

- **Menu** (`Section` v MainActivity, v tomto pořadí): Domů, Konzole (nadpis stránky PlayStation /
  Xbox), Titulky (vzhled, jazyk překladu, oblasti), Nápověda a jako poslední **⚙ Nastavení** – to není
  v řadě menu, ale ozubené kolo vidět pořád (`gear`): v telefonu vpravo nahoře v liště, na televizi dole
  v levém sloupci (pořád poslední položka při procházení šipkou dolů – zkouška `07_menu_4`). Historie se
  otevírá z Nastavení (záložka Čtení a překlad).
- **Licence a platby jsou zatím skryté** (`SHOW_LICENSE = false` v MainActivity, odkryjí se při vydání):
  ukáže se jen, když zkušební doba skončila (`!canRun`), jinak by hráč nevěděl, proč aplikace nejde.
- **Průvodce prvním spuštěním** (`wizardSteps`, indexy `W_…`): Zařízení (Android TV/box, nebo
  telefon/tablet jako čtečka pro LG/Samsung, `Prefs.readerDevice`; výchozí podle
  `NetInfo.isHandheld`; samo nic nepřepíná – pomocník v telefonu se dál řídí `Prefs.forceTv`) →
  Konzole → Televize (u LG/Samsungu tlačítko nahrání) → Jazyk překladu → Kvalita překladu (od 0.94,
  `W_TRANSLATE`, od 0.97.28 „Lepší překlad (DeepL)“ bez volby DeepL / offline – rovnou věta proč, čtverečky a čekání na
  klíč, viz DeepL) → Zobrazení titulků (povolení; u LG/Samsungu jen vysvětlení) → Připojení konzole → Hotovo.
  **Připojení konzole (od 0.97.28):** nahoře jen „Krok 7 z 8“, spárování místo druhého pruhu malý podtitul
  „Spárování · 3 z 6“ (`pg_sub`, tag `psub`; mimo průvodce dál pruh `ui.steps`). Jedna řada tlačítek – ta ze spárování
  (`pnext`, `pback`; v kroku 1 spárování vede Zpět na předchozí krok průvodce) a pod ní malé „Spárovat později“
  (`wlater`, skočí na Hotovo); u Xboxu (`XboxForm`) navíc malé Zpět. Vnější Pokračovat/Zpět/Přeskočit průvodce
  se ukáže, až je konzole spárovaná. Krok Konzole nezmiňuje Remote Play.
  Příprava PS5 (druhý účet, Remote Play, skrytí hlášky, probouzení ze sítě, čtvereček na `pomoc/ps5.md`)
  je od 0.93 až v prvním kroku párování (Připojení konzole), ne u volby konzole – tam byla moc brzo.
- **Domů** (od 0.97): velký stav + Spustit/Zastavit a pod nadpisem přehled v bodech (`statusRows`, tečka
  zelená/oranžová/šedá; řádek, který potřebuje zásah, má pod sebou tlačítko): konzole + obraz (`LinkStatus`,
  při ukončení ze strany PS5 důvod, při převzetí jiným zařízením `ls_other_device`), televize (Android TV =
  povolení pruhu; LG/Samsung = `TvChoice.short`, nenahraná / zastaralá aplikace s tlačítkem nahrání – jinak
  žádné tlačítko), síť (`h_net_…`, vždy zeleně), rychlost čtení, jazyk + offline/DeepL (chybějící balíček
  s tlačítkem) a verze.
  Od 0.97.7: televize LG/Samsung = jen spojení („LG OLED55B7 · připojeno“ zeleně / „nepřipojeno“ oranžově,
  `tv_st_off`); spojení drží `TvIdle` (v `TvChoice.kt`) i před Spustit, dokud je aplikace vidět (služba ho před
  vlastním `TvSender` zavře). Bez připojené televize velký stav „Čeká na televizi“ (`st_wait_tv`), ne Připraveno.
  Verze: řádek PlayLingo (aktuální zeleně, novější oranžově „· nová X“ s tlačítkem Aktualizovat v řádku a postupem
  stahování pod ním – samostatná karta aktualizace není) a řádek PlayLingo v televizi (chybí / starší oranžově
  s tlačítkem nahrání, aktuální zeleně).
  Od 0.97.9 řádek hry jen s názvem: vybraná hra zeleně („Grand Theft Auto VI“, OK = výběr hry `pickGame`); když
  `GameGuess` z titulků pozná jinou hru, oranžově s otazníkem za vybranou hrou („Call of Duty · Grand Theft Auto VI?“, s univerzálním
  profilem jen „Grand Theft Auto VI?“) a tlačítkem Přepnout – jeden řádek, vybraná hra se nepřepíše.
  Od 0.97.10: „PlayLingo v TV LG 1.4.0“ (`h_ver_tv` dostává typ + verzi), překlad „Čeština · DeepL online + offline“. Univerzální profil bez odhadu = řádek není. Na novou verzi se aplikace ptá i po návratu do ní (nejvýš jednou za 5 min). Běh na pozadí (telefon, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) se
  zeptá jednou při prvním Spustit (`ui_jednou`) a dál je jen v ⚙ Nastavení → Obecné, na Domů ne.
  Řádek konzole/televize OK = změna. Samostatný souhrnný řádek ani návody v textu tam už nejsou. Náhled obrazu
  je jen na stránce Konzole (tlačítko Spustit tam od 0.97.13 není – jen na Domů, telefon i Android TV) (s popiskem `pv_stale`, když obraz nechodí). „Zpět do hry (HDMI)“ jen na zařízení
  v televizi (`Prefs.isTv`); telefon jako čtečka má jen Pozastavit a Zastavit. Jednorázové volby na Domů nepatří.
- **Titulky** (od 0.97.1, `subtitlesPage`): nahoře jazyk překladu (zkouška `13_nabidka_jazyk` na něj míří
  šipkou vpravo + OK), pod ním rozbalovací skupiny Písmo / Pruh s překladem / Oblasti titulků
  (`lookOpen`) a dole řádek Obnovit vzhled. Automatika oblastí ukáže popis v dialogu jen při zapnutí.
  Volba „Černý pruh přes anglické titulky“ zmizela – je to totéž co poloha „Přes titulky“.
- **Instalace** (do 0.97.1 se jmenovala Nápověda; klíč `m_help`, `help()`) – návodné věci patří sem, ne do
  Nastavení: nahrání aplikace do televize je v kapitole 3 (z ⚙ Nastavení → Obecné zmizelo v 0.97.2).
- **Nápověda / Instalace** (od 0.97, `help()`, texty `core/I18nHelp.kt`): nahoře „Nápověda pro“ (typ televize, výchozí
  zvolený), pod tím kapitoly 1–8 jako `settingRow` (OK rozbalí, `helpOpen`): instalace, příprava konzole (druhý
  účet, Remote Play, `rp_hide`, `ps5_rest` / Xbox), televize (povolení pruhu / pomocník LG / průvodce Samsung),
  jazyk a DeepL, spárování, hraní, když něco nejde (+ hlášení), volby v Nastavení. ✔/○ podle skutečného stavu
  jen pro zvolenou televizi. Stránky s návodem (`HelpLinks`): telefon tlačítko, televize čtvereček.
- **Nahrání do televize** se nabízí jen, když tam aplikace ještě nikdy nebyla nebo je starší (`TvChoice.upToDate`,
  `outdated`): Domů v přehledu, krok Televize v průvodci jinak ukáže „✔ PlayLingo je v televizi (verze X)“;
  vždy je k dispozici v Instalaci (kapitola 3). Pomocník LG u aktuální verze píše „V televizi je aktuální…“ a tlačítko
  „Spustit PlayLingo v televizi“; u spárované televize je políčko Passphrase schované pod tlačítkem
  „Zadat Passphrase znovu“.
- **Nastavení (⚙)** (od 0.97.15): každá záložka jen řádky `settingRow` a rozbalovací skupiny `group(id, název)`
  (`openGroups`), žádné nadpisy sekcí ani popisné věty. Čtení a překlad: Kvalita čtení, Režim překladu (OK → Titulky →
  Kvalita překladu), Jazykový model, skupiny Statistika (s Historií) a úplně dole Vlastní slovník. Experimentální:
  Zkušební verze a skupina Ladění a hlášení (diagnostika, hlášení, záznam v síti); DeepL je od 0.97.28 v Titulkách. Hra je v Titulkách
  (skupina `gameGroup`). Náhled obrazu na stránce Konzole je skupina – snímek se počítá jen, když je rozbalená.
  Starší popis níž platí jen zčásti:
- **Nastavení (⚙)** má záložky (`SettingsTab`) a ukazuje jen obsah vybrané: **Obecné** (konzole, typ
  televize + nahrání, jazyk aplikace, zařízení; povolení pruhu jen u Android TV),
  **Čtení a překlad** (kvalita čtení, překlad, hra, slovník, statistika, historie), **Experimentální**
  (zkušební verze, DeepL, záznam v síti a dole rozbalovací skupina Ladění a hlášení – diagnostický režim, hlášení).
  Od 0.97.6: záložky Demo režim a Ladění a hlášení nejsou (demo pro hráče nahradí videa; ukázkovou hru dál
  spouští zkouška v emulátoru parametrem `autostart`), záložky jsou s nadpisem v pevné hlavičce `pinned`
  nad posuvnou částí a přepnutí záložky posune obsah na začátek (`pageScroll`). Stránky nemají nadpis
  (název je v menu), jen Historie. U voleb jsou jen názvy;
  dlouhé popisy jsou v Nápovědě v části „Volby v Nastavení“ (`help_settings`). Nová volba = krátký název
  v Nastavení, popis do Nápovědy.

## Ukazatele postupu (od 0.96)

`Ui.progress(fraction)` – pruh (0..1; `null` = nevíme kolik, pruh běhá) a `Ui.steps(current, total, label)` –
„Krok 3 z 8“ (text `steps_of`) s pruhem na dílky. Používá se: stahování aktualizace (Domů i pomocník
v telefonu, procenta po 2 %; instalace = běhající pruh), stahování jazykového balíčku (ML Kit procenta neříká –
`OfflineTranslator.isDownloading`), připojování ke konzoli a čekání na obraz na Domů, hledání/párování PS5
(`pairBusy`), přihlášení Xboxu, nahrávání do LG (podle `LgPhase`), kroky průvodce, spárování PS5 (6 kroků; v průvodci
jen podtitul „Spárování · 3 z 6“, ať nejsou dva pruhy nad sebou) a pomocník LG (5 kroků). Nový postup o víc krocích = přidej `ui.steps`, nové čekání = `ui.progress`.

## Na co zkouška spoléhá (neměnit jen tak)

- Řádky v logu se značkou `TP_TEST` (`SHOW|`, `VIDEO|`, `LINK|`, `PAIR|`, `START|`…) –
  podle nich `ci/emulator-test.sh` počítá výsledek. Před ukázkovou hrou obě zkoušky čekají (nejvýš 3 min)
  na `MODEL|<jazyk>|ok` – jazykový balíček se stahuje od Googlu a občas nestihne (0.80: Android 11
  přeložil jen 2/15, v logu „Translation model files not found“). Aplikace po neúspěchu stahuje znovu
  (`prepareModel`, 20 s, nejvýš 6×; při chybě překladu nejvýš jednou za 30 s). Výsledek čekání je v `model.txt`.
- Věty ukázkové hry v `core/DemoGame.kt` – skript hledá 15 z nich podle textu. Měníš-li věty,
  uprav i skript.
- Parametry spuštění `nowizard`, `autostart`, `ps5test`, `phonelogin`, `pairstep`, `updtest`,
  `updself`, `logintest`, `helperlogin`, `fullapp`, `page` (jména `Section`: HOME, PS5, LOOK, HELP,
  SETTINGS; staré LICENSE = SETTINGS), `wizard` (číslo kroku průvodce, snímky `p2_<velikost>_pruvodce_<krok>`),
  `trtest`, `console` (ps / xbox, snímek `11b_stranka_xbox`) a `TestCardActivity`, `PhoneHelperActivity`.
- Zkouška přihlášení přes telefon (řádky `LOGIN|`, `HELPER|`, snímky `12n`–`12p`) se do výsledku
  nepočítá. V `result.txt` má být `ucet_z_telefonu` aspoň 1 (stránka z televize přijala účet)
  a `pomocnik` aspoň 1 (pomocník přijal odkaz ze stránky televize); na snímku `12n` má být
  „Ověřený účet: Zkouška“ (krok nastavení na PS5) a na `12o` „Televize čeká na účet“.
- Kroky průvodce spárováním se mají vejít na obrazovku i s tlačítky (snímky `12j`–`12l`).
  Podrobný postup proto patří na stránku pro telefon, v televizi jen krátce.
- Zkouška aktualizace (řádky `UPD|`, snímky `15a`–`15d`) se do výsledku nepočítá. `updtest`
  stáhne vydanou verzi (závisí na internetu; emulátor ji nenainstaluje). `updself` nainstaluje
  aplikaci samu přes sebe – na snímku `15c_dotaz` má být dotaz systému na instalaci a v
  `result.txt` má být `aktualizace_nahrazeno` aspoň 1.
- Pořadí položek menu a kroků průvodce – skript se jimi prokliká naslepo šipkami (viz Obrazovky
  a průvodce). Na každém kroku průvodce je po vykreslení vybrané tlačítko Pokračovat (`wnext`, u párování
  `pnext`), takže OK posune dál. Snímky průvodce: `01_pruvodce_zarizeni`, `01b_…konzole`,
  `01c_…televize`, `03_…jazyk`, `03b_…po_stazeni`, `03c_…preklad`, `02_…povoleni`, `04_…ps5_krok1`, `04b_…hledani`;
  stránky menu `07_menu_1`–`4` (Konzole, Titulky, Nápověda, Nastavení).
- Zbytky starého názvu „TestPreklad“ (značka logu, `rootProject.name`, názvy souborů
  v sestavování, `TestPreklad.apk` ve vydání, sůl odemykacího kódu v `License.kt`).
  Nepřejmenovávej je: visí na nich zkouška, odkaz ve starších verzích a platnost kódů.

## Poučení z historie (co už se zkoušelo)

- **Spojení s konzolí neměň bez výslovného zadání.** Emulátor PlayStation nemá, takže zkouška
  projde, i když to na skutečné konzoli nefunguje. Ověřit to umí jen uživatel na své televizi.
  Dva pokusy nehlásit se konzoli jako ovladač (0.34 „jen se dívat“, 0.38 „tichý režim“)
  se musely vrátit.
- **Probouzení PS5 je volba, výchozí vypnutá** (od 0.97.18, `Prefs.ps5Wake`, ⚙ → Obecné „Probouzet PlayStation“):
  probuzení přes síť zapne konzoli na spárovaném (druhém) účtu, ne na výběr účtu – to Remote Play neumí. Bez volby
  `Ps5Link.connectWhenAwake` jen každých 8 s zjistí (hledání v síti, `standby`), jestli konzole spí; Domů a stav v TV
  ukazují „PS5 · v klidovém režimu“ (`Ps5Link.asleep`, `h_con_sleep`) a po zapnutí ovladačem se připojí samo.
  Nenalezená konzole = připojí se jako dřív. Ověřit umí jen majitel na skutečné PS5.
  **Od 0.97.37 i po přerušeném spojení:** když hráč PS5 během překladu vypne (klidový režim), aplikace se znovu nepřipojuje
  (pokus o spojení by ji zapnul – hlásili testeři), jen čeká (`sleeping()` před každým pokusem, i s volbou Probouzet).
- **Přihlašovací kód účtu na PS5** (4 číslice, `Prefs.ps5LoginPin`): políčko se od 0.86 ukáže jen,
  když o něj PlayStation při spojení požádal (`Ps5Link.needsLoginPin`) nebo když už je nějaký uložený.
- **Hláška „Aplikace Remote Play je připojena“**: průvodce (u přípravy PS5) a Nápověda radí zapnout na
  PS5 „Skrýt stav připojení Remote Play“ (text `rp_hide` v `core/I18nUi.kt`).
- **Druhý účet PlayStation je záměr.** Když se aplikace připojí herním účtem, konzole hráči
  vypne ovladač a zhasne obraz. Průvodce proto vede na druhý účet.
- **Obraz 1080p jen pro televize s 3 GB paměti a víc** (`core/Perf.kt`). S 1080p na slabších
  televizích se od 0.29 odpojovaly ovladače; od 0.32 berou zase 720p.
- **Hlídání obrazu za spojení** (`Ps5.watchdog`, testy `Ps5WatchdogTest`): spojení se naváže znovu po 12 s
  jen tehdy, když data z konzole chodí, ale nic se nedekóduje; když nechodí nic (Wi-Fi zakolísala), čeká
  se 45 s – do 0.96 se rušilo už po 12 s a PS5 pak hráči hlásila „ovladač odpojen / účet připojen“ (0.95,
  telefon na 2,4 GHz se zhasnutým displejem). Výpadek dat se zapíše `PS5|stall`. Služba drží
  `PARTIAL_WAKE_LOCK` a Wi-Fi zámky (i `LOW_LATENCY`), jinak telefon se zhasnutým displejem uspal
  procesor. PERF má navíc paměť aplikace a Wi-Fi (MHz/dBm), `NET|Wi-Fi přešla na …` zapíše změnu pásma.
- **Odmítnutí spojení před obrazem (od 0.97.31)**: knihovna hlásí jen „Unknown Session Request Error“; skutečný důvod
  je v jejím záznamu `ps5-spojeni.txt`. `Ps5Link` při ukončení bez spojení přečte konec souboru, `Ps5.refusal` (testy
  `Ps5Test.refusalFromLibraryLog`) z něj pozná ACCOUNT (Invalid PSN ID / Regist failed), IN_USE, CRASH, VERSION, UNREACHABLE
  (connect failed) nebo UNKNOWN; řádky knihovny jdou do záznamu jako `PS5|refused: …` a `PS5|refusal …`. Domů pod řádkem
  konzole ukáže `ps5_ref_<důvod>` (u účtu s tlačítkem Spárovat znovu), UNKNOWN dál anglický text `ls_ended`. Řádek
  konzole před Spustit je „spárováno“ (`h_con_ready`, dřív „připraveno“ – mátlo, že je spojeno). Spojení se tím nemění.
- **Převzetí Remote Play jiným zařízením** (`Ps5.otherDevice`: „already in use“, nebo odpojení s takovým
  důvodem) → `Ps5Link.otherDevice`, Domů `ls_other_device`, v záznamu „Remote Play převzalo jiné zařízení“.
- **Opakované připojování ruší ovladače u konzole.** Proto se pokusy po čtyřech krátkých
  spojeních zastaví (0.32) a další pokus se odkládá aspoň o 15 s (0.37).
- **Zvětšený výřez pro drobné titulky nekresli do jedné znovupoužívané bitmapy.** Ve 0.53 se
  to na Androidu 9 třikrát po sobě zaseklo, 0.54 to vrátila.
- **Otisk snímku se počítá ze všech pixelů výřezu** (0.31). Ze vzorků se drobný titulek
  na klidné tmavé scéně přehlédl.
- **Pruh se neschovává podle toho, co je na obrazovce** (0.20) – při HDMI to televize nehlásí.
- **Překládají se jen repliky**, ne menu hry, pokyny k ovládání ani text v jiném jazyce
  (0.33, 0.43, 0.45). Čte se jen spodní třetina obrazu.
  Ani texty písní označené notou ♪ (`TextLogic.isLyrics`, od 0.93 – rádio v GTA VI).
- **Přihlášení účtem PlayStation jde jen přes telefon** (QR kód, od 0.19). Přihlášení přímo
  v televizi v přibaleném prohlížeči GeckoView ochrana Sony odmítala a prohlížeč dělal z APK
  176 MB; ve 0.55 bylo obojí odstraněno. Nevracej to. Od 0.57 se v telefonu nic neopisuje
  (viz Přihlášení přes telefon).
- **Velikost výřezu čtení nezrychlí.** Čas jednoho čtení (ML Kit) je ve zkoušce stejný při
  zvětšení 1× i 1,5× i při menší oblasti (měřeno na 0.54, řádky `READ|ocr=…` a `SCALE|`
  ve větvích `test-results-api…`). Menší výřez ani vypnuté zvětšování proto čtení nezrychlí.
- **Co aplikace dělá, když je otevřená** (0.97.14): barevný náhled hry jen pro stránku Konzole a nastavení
  oblastí (`UiState.previewWanted`), Domů se kvůli náhledu nepřekresluje; stav DeepL k zobrazení přes
  `DeeplTranslator.active` (bez dešifrování klíče v Keystore), verze aplikace pro TV z APK se čte jednou.
  Telefon jako čtečka mívá aplikaci otevřenou celou hru – nic drahého nedávej do `render()` ani do tickeru.
- Úpravy výkonu ověřuj na snímcích a časech ze zkoušky, ne odhadem. Emulátor čte jeden snímek
  3–6 s, skutečná televize zlomek toho – z emulátoru jde brát jen porovnání, ne skutečné časy.

## Licence

- chiaki-ng je pod AGPL-3.0, proto musí zdrojový kód aplikace zůstat veřejný. Hlavičky
  převzatých souborů s popisem našich úprav zachovej.
- Věta „Aplikace není produktem společnosti Sony Interactive Entertainment…“ v README zůstává.
