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
  - `MainActivity.kt` – všechny obrazovky a průvodce
  - `Updater.kt` – aktualizace z aplikace (stažení nové verze a předání systému k instalaci)
  - `DemoFeeder.kt`, `TestCardActivity.kt` – ukázková „hra“ pro zkoušku bez PlayStationu
- `app/src/main/cpp`, `app/src/main/java/com/metallic/chiaki/lib` – naše upravená vrstva nad
  knihovnou chiaki-ng (bez zvuku a nativního dekodéru).
- `third_party/chiaki-ng` – submodul. Needituj ho; před sestavením se do něj kopíruje
  `app/src/main/cpp` (úloha `patchChiaki` v `app/build.gradle.kts`).
- `.github/workflows/build.yml`, `ci/emulator-test.sh` – sestavení, zkouška, vydání.
- `ci/mistni-kontrola` – překlad a unit testy bez Android SDK (viz níže).
- `webos` – PlayLingo pro televize LG (viz Televize LG): `webos/obchod` je aplikace pro televizi,
  `webos/zkouska` diagnostika. Do APK se nic z toho nedostane.
- `pc` – zkušební čtečka pro počítač s Windows (viz `pc/CTI-ME.md`): čte titulky z okna s obrazem
  hry, přeloží je a pošle do televize LG. Překládá se proti téže složce `core` jako aplikace,
  takže změna v `core` se týká i jí – po úpravě `core` ověř i `gradlew -p pc installDist`.
  Do APK se nedostane a sestavení na GitHubu o ní neví.

## Sestavení a zkouška

Každý push (na jakoukoli větev) spustí GitHub Actions:

1. unit testy (`./gradlew testDebugUnitTest`),
2. sestavení APK pro televize (ARM) a zkušebního APK pro emulátor (x86),
3. zkoušku v emulátoru Android TV na Androidu 9, 11, 13 a 14 (`ci/emulator-test.sh`),
4. vydání, jen když zkouška prošla:
   - z `main` → `latest` (nabídne se všem) a vydání pod číslem verze,
   - z větví `zaloha-…` → jen vydání pod číslem verze,
   - z ostatních větví → zkušební kanál `zkusebni` (nabídne se jen tomu, kdo má v aplikaci
     zapnuté Nastavení → Zkušební verze). Tak se nové věci zkoušejí na televizi uživatele,
     aniž by šly všem.

Když v sezení není Android SDK (`ANDROID_HOME` je prázdné), nejde APK sestavit místně. Kód ale
jde přeložit a unit testy pustit: `cd ci/mistni-kontrola && gradle test` (asi minuta). Dělej to
před každým pushem – odhalí překlepy a chyby v typech dřív než zkouška na GitHubu. Nezná zdroje
(`res/`), manifest ani nativní část; nový zdroj `R.…` nebo novou funkci knihoven Google použitou
v kódu je potřeba doplnit do `ci/mistni-kontrola/stub`.

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

## Podpis

- Všechny verze musí být podepsané stejným klíčem. S jiným podpisem televize novou verzi přes
  starou nenainstaluje a uživatel přijde o spárování i nastavení. Verze do 0.55 měly každá
  jiný (dočasný) podpis – proto se musely pokaždé odinstalovat.
- Klíč v repozitáři není a nesmí být (repozitář je veřejný a co se jednou dostane do historie,
  tam zůstane). Je jen v tajných údajích repozitáře na GitHubu: `PLAYLINGO_KEY_BASE64` (soubor
  p12 v base64) a `PLAYLINGO_KEY_PASSWORD`. Sestavení ho při běhu rozbalí do dočasného souboru
  a po sobě smaže. Zálohu klíče a hesla má jen uživatel. Klíč ani heslo nikam nezapisuj,
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
- **PlayLingo v telefonu.** Stejné APK nainstalované v telefonu (pozná se podle mobilní sítě,
  `NetInfo.isPhone`) ukáže místo aplikace jen pomocníka. Otevírá ho odkaz `playlingo://login?…`
  ze stránky televize. Přihlášení Sony běží v něm, kód si vezme sám, číslo účtu pošle televizi
  (`LoginClient`) a zapamatuje si ho (`Prefs.phoneAccounts`; jen číslo a jméno, žádné heslo).
  Účet posílá jen na adresy domácí sítě. Záloha: přihlášení v prohlížeči a Sdílet → PlayLingo.
- Ruční opsání kódu (krok 5 průvodce) zůstává jako poslední záloha. Pořadí a počet kroků
  průvodce se nezměnily.
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
  stranách.
- Android: volba Nastavení → „Posílat titulky do televize LG“ (`Prefs.tvSend`, výchozí vypnuto).
  `SubtitleOverlay` předává `TvSender` každou změnu pruhu. S vypnutou volbou se nic neotevírá ani
  neposílá a aplikace se chová jako dřív. Se zapnutou se čte i tehdy, když je vidět aplikace,
  a nevyžaduje se povolení pruhu.
- Telefon: pomocník (`PhoneHelperActivity`) má od 0.63 tlačítko „Použít telefon pro televizi LG“.
  Zapne `Prefs.forceTv` a `Prefs.tvSend`, předá už ověřený účet do párování a otevře celou
  aplikaci. Jestli se celá aplikace dá na telefonu pohodlně ovládat, umí ověřit jen uživatel.
- Televize uživatele má prohlížeč Chromium 38. Kód pro televizi proto musí zůstat ve starém zápisu
  JavaScriptu: žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce.
- Zkoušení bez Androidu: `node webos/posilac.js` posílá z počítače zkušební věty, `pc/` čte
  a překládá na počítači. Nástroj LG (`ares-package`, `ares-install`, `ares-launch`, `ares-inspect`
  z `@webos-tools/cli`) nahraje aplikaci do televize ve vývojářském režimu; `ares-inspect` dá živý
  přístup do běžící aplikace.
- Změny jen ve složkách `webos` a `pc` nemění verzi aplikace pro Android TV a commitují se
  s `[skip ci]`. Balíčky `.ipk` se necommitují.

## Texty aplikace a jazyky

Aplikace má dvě na sobě nezávislá nastavení jazyka (`core/Lang.kt`):

- **jazyk překladu** (`Prefs.lang`, stránka Překlad a průvodce) – do čeho se titulky překládají;
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
  `hp_…`): `core/I18nLogin.kt` (`LOGIN_S`, `LOGIN_X`).

Texty, které se přestaly používat, smaž ze všech jazyků. Když se mění postup pro uživatele,
uprav i `NAVOD.md` a stránku Nápověda v aplikaci.

Aplikace pro televizi LG (`webos/obchod`) má vlastní texty česky a anglicky přímo v `app.js`
a tlačítko Jazyk na úvodní obrazovce; do obchodu LG se podává s anglickými podklady.

## Na co zkouška spoléhá (neměnit jen tak)

- Řádky v logu se značkou `TP_TEST` (`SHOW|`, `VIDEO|`, `LINK|`, `PAIR|`, `START|`…) –
  podle nich `ci/emulator-test.sh` počítá výsledek.
- Věty ukázkové hry v `core/DemoGame.kt` – skript hledá 15 z nich podle textu. Měníš-li věty,
  uprav i skript.
- Parametry spuštění `nowizard`, `autostart`, `ps5test`, `phonelogin`, `pairstep`, `updtest`,
  `updself`, `logintest`, `helperlogin` a `TestCardActivity`, `PhoneHelperActivity`.
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
- Pořadí položek menu a kroků průvodce – skript se jimi prokliká naslepo šipkami.
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
