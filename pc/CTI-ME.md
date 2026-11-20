# PlayLingo pro počítač (zkušební)

Dělá na počítači s Windows totéž co překlad v aplikaci pro Android TV: čte anglické titulky
z obrazu hry a přeloží je bez internetu. Překlad ukáže v pruhu přímo na obrazovce počítače přes
okno s hrou (výchozí), nebo ho pošle do televize LG, případně obojí.

Je to základ budoucí aplikace pro Windows a handheldy (Lenovo Legion Go apod.): hráč hraje přes
chiaki-ng na svém **hlavním účtu** a PlayLingo se k PlayStationu vůbec nepřipojuje, jen čte obraz
z okna. Druhý účet ani přihlášení přes telefon tu nejsou potřeba. Zatím běží z repozitáře
(potřebuje Javu 17 a Python), ne jako samostatný instalační balíček.

## Spuštění

**Dvojklik na `pc\PlayLingo.cmd`** otevře okno aplikace (při prvním spuštění a po změně kódu se
předtím asi minutu sestavuje). Potřebuje Javu 17 (Temurin) a pro Připravit i Python 3.10+.

- **Domů** – velký stav (Připraveno / Hledání PS5… / Probouzení PS5… (jen se zapnutou volbou „Probouzet PS5“ v Nastavení, výchozí vypnuto) / Spouštění hry… / Běží /
  Čeká na okno hry / Chybí příprava), Hrát (u chiaki-ng) nebo Spustit / Zastavit a řádky
  „Popis · hodnota“ s tečkou jako v aplikaci pro TV: chiaki-ng (chybí = Stáhnout), PS5 (nespárováno
  = Spárovat, zapnutá / klidový režim / nenalezena = Hledat), u jiného programu okno hry
  (nenalezeno = tlačítko Vybrat okno), překlad (chybí = tlačítko Připravit – stáhne knihovny
  a model asi 0,6 GB do `%LOCALAPPDATA%\PlayLingoTools`, do systému nic neinstaluje), výstup,
  rychlost čtení a poslední titulek.
- **Titulky** – nahoře **Hra** (profil hry z `core/GameProfiles.kt`, výchozí Univerzální) a **Rozsah čtení**
  (Titulky a texty hry / Jen titulky); pod tím vzhled pruhu se stejnými hodnotami jako na Androidu: velikost
  písma (Podle titulků nebo 12–48), barva, tučné, neprůhlednost pozadí 0–100 % (pod 40 % má písmo tmavý
  obrys), okraj pruhu 0–3, poloha (přes titulky / nad titulky / nahoře / dole), originál pod překladem
  a Zkušební pruh na 4 s. Vzhled platí hned, i za běhu; změna hry nebo rozsahu čtení spustí čtení znovu.
  Zvolená hra je i na Domů („Hra · Grand Theft Auto VI“; univerzální = řádek není).
- **⚙ Nastavení** – okno hry (část titulku okna, Vybrat = nabídka oken, kde může běžet hra:
  chiaki / PS Remote Play / Xbox nahoře, skrytá systémová okna ne), ukázková hra
  bez PlayStationu, jazyk překladu, výstup (Pruh na PC / TV LG / Samsung / PC i TV), příprava,
  DeepL (zapnutí, klíč, odkaz na klíče v účtu DeepL) a Záznam (výpis čtení). Změna okna, jazyka,
  výstupu nebo DeepL za běhu čtení sama spustí znovu.

**Profil hry a překlad jako na Androidu (od 21. 11. 2026, `src/Ctecka.kt`):**
- jména, místa, auta a ulice zůstávají anglicky (`Glossary.prepare` – offline i DeepL), herní pojmy mají
  pevný překlad, ručně zadané věty (`BuiltinPhrases`), slang se upraví a čeština prochází `Natural.czech`;
- rod postav (`Glossary.genders` → `CzechGender`: „Lucia šla“), hlášky a menu hry se nečtou
  (`GameProfile.screen`), v Diablu IV se nečte chat a pozná se mluvčí „Jachal says:“;
- **paměť překladů** (`TranslationCache`) – stejná věta se nepřekládá znovu; soubor
  `%LOCALAPPDATA%\PlayLingoTools\pamet-<jazyk>-<hra>.txt`;
- **barvy jmen** z profilu hry (GTA VI Jason žlutě, Lucia modře) a naučené z obrazu: u nového titulku se
  jménem `cteni.py` (příkaz `BARVA`) vrátí barvu nejsvětlejších bodů jména, po 2 shodách platí (`NameColor`);
- **pokyny GTA** („Take your shot“) menším šedým řádkem pod replikou, s „Jen titulky“ se vynechají;
- **překlad menu GTA VI** (`core/GameMenu.kt`, jen s „Titulky a texty hry“): když v pruhu není titulek,
  jednou za 1,5 s (v menu za 0,4 s) se přečte horních 60 % obrazu (`cteni.py`, příkaz `MENU`) a překlad
  menu se ukáže vedle menu, řádky pod sebou; titulky se mezitím nečtou. Na skutečné hře neověřeno.

**Výstup do TV LG / Samsung jako z telefonu:** každý titulek nese vzhled (`TvLook`: písmo, pozadí, barva,
okraj, poloha, kde byl anglický titulek, originál, u menu místo vedle menu), TV dostává volby pro nabídku
**OK při hře** (`TvSettings`) – změna z ovladače TV se uloží jako z okna PC – a každé 2 s stav vlevo nahoře
(konzole, překlad, hra).

**DeepL** (`src/DeeplPc.kt`) jede stejně jako na Androidu od 0.97.2: počká se na překlad DeepL
(nejvýš ~2,5 s) a ukáže se jen jedna varianta, offline jen když DeepL nepomůže; čeština prochází
`Natural.czech`. Požadavek, odpověď, chyby a přestávky jsou z `core/Deepl.kt`. Klíč je zašifrovaný
přes Windows (DPAPI) v `%LOCALAPPDATA%\PlayLingoTools\deepl.bin` a nikam se nevypisuje. Z příkazové
řádky: `--deepl-klic SOUBOR` klíč uloží, `--deepl` zapne překlad přes DeepL.

Nastavení se ukládá do `%LOCALAPPDATA%\PlayLingoTools\aplikace.properties`.

**Hraní přes chiaki-ng v PlayLingu** (Nastavení → Hra běží v → chiaki-ng v PlayLingu, výchozí;
`src/Chiaki.kt`): hráč nemusí obsluhovat druhý program.

1. Domů → „chiaki-ng · chybí“ → **Stáhnout**: přenosná verze z posledního vydání
   `streetpea/chiaki-ng` (x64 nebo ARM, ~120 MB) do `%LOCALAPPDATA%\PlayLingoTools\chiaki-ng`,
   nic se neinstaluje. Nainstalovaný chiaki-ng (Program Files) se najde taky.
2. „PS5 · nespárováno“ → **Spárovat** (nebo ⚙ → Spárovat PS5): průvodce PlayLinga ve třech krocích,
   do chiaki-ng se nic nezadává (`src/Parovani.kt`):
   1. **PS5 v síti** – najde zapnutou PS5 (klidový režim nestačí), u více konzolí „Jiná PS5“.
   2. **Účet PlayStation** (hlavní účet, na kterém se hraje) – bez opisování čísla účtu:
      - telefonem: čtvereček vede na stránku z PC (`core/LoginServer.kt` jako na Android TV, vlastní
        text stránky pro PC); po přihlášení u Sony se na ni vloží adresa „redirect“ a PC si účet vezme;
      - nebo **Přihlásit v prohlížeči** tohoto PC: po přihlášení stačí adresu stránky „redirect“
        zkopírovat, PlayLingo ji samo převezme ze schránky.
      Uloží se jen číslo a jméno účtu (`aplikace.properties`), příště se krok rovnou odškrtne („Jiný účet“).
      Windows se při prvním otevření stránky může zeptat na povolení sítě pro Javu – bez něj funguje
      jen přihlášení v prohlížeči PC.
   3. **Kód z PS5** – 8 číslic (PS5: Nastavení → Systém → Remote Play → Spárovat zařízení) → Spárovat.
   Spárování dělá PlayLingo samo (`src/Registrace.kt`, protokol z `chiaki-ng/lib/src/regist.c`
   a `rpcrypt.c` pro PS5) a výsledek zapíše tam, kam si ho ukládá chiaki-ng
   (`HKCU\Software\Chiaki\Chiaki\registered_hosts`, `Chiaki.uloz` – stejný tvar jako QSettings
   v chiaki-ng). Odtud ho chiaki-ng vezme při hře a PlayLingo jméno konzole a číslo pro probuzení.
   Otevřený chiaki-ng by si seznam konzolí při zavření přepsal, proto ho průvodce nabídne zavřít.
   Chyby jsou krátké stavy: „Kód · neplatný“, „PS5 · obrazovka s kódem zavřená“, „Účet · neplatný“…
3. **Hrát**: PlayLingo najde PS5 v síti (zpráva z `core/Ps5.kt`), z klidového režimu ji probudí
   (zpráva WAKEUP jako v `chiaki-ng/lib/src/discovery.c`), spustí
   `chiaki stream <konzole> <adresa> --fullscreen --exit-app-on-stream-exit` a čte okno toho procesu
   (`pid:<číslo>` v `OknoHry` i `cteni.py`). Konec hry v chiaki-ng = konec čtení; Zastavit zavře obojí.
   Výpis chiaki-ng je v `%LOCALAPPDATA%\PlayLingoTools\chiaki-vystup.txt`.

Ladění: `playlingo-pc.bat --hledej-ps5` vypíše chiaki-ng, spárované konzole a PS5 v síti.

**Hra v jiném programu** (Hra běží v → Jiný program, např. oficiální PS Remote Play): čte se okno
podle části titulku. Okno může být přes celou obrazovku (ne výhradní režim celé obrazovky) nebo
zakryté jiným oknem, ale nesmí být minimalizované.

## Z čeho se skládá

- `src/Aplikace.kt` – okno aplikace (barvy jako `Theme` v aplikaci pro Android, Swing). Ovládá se
  myší, dotykem i klávesnicí (Tab / šipky, Enter); vybraný prvek má zlatý rámeček.
- `src/Ctecka.kt` – čtení a překlad. Výběr titulků, rozhodování, kdy větu ukázat a schovat,
  i posílání do televize jsou **tytéž soubory** jako v aplikaci (`app/src/main/java/cz/playlingo/core`),
  jen přeložené pro počítač. Oprava v `core` se tak projeví na Androidu i tady.
- `src/Pruh.kt` – pruh s překladem na obrazovce: okno vždy navrchu, průhledné, myš a dotyky jím
  propadají do hry a nebere si zaměření. Leží přes anglický titulek (jako na Android TV), velikost
  písma podle něj a jede s oknem hry, když se posune nebo změní velikost. Polohu okna zjišťuje od
  Windows přes knihovnu JNA a přepočítá ji podle zvětšení obrazovky ve Windows (handheld 150–200 %).
- `src/Nastaveni.kt` – uložené nastavení a nabídky hodnot (pozadí a barvy jako na Android TV).
- `src/Main.kt` – totéž z příkazové řádky (`spustit.ps1`), výpis do konzole.
- `cteni.py` – pomocník pro to, co Android dělá svými knihovnami: snímek okna s hrou, čtení
  textu (RapidOCR) a překlad (model Argos, jádro CTranslate2). Jeho chyby jdou do
  `%LOCALAPPDATA%\PlayLingoTools\cteni-chyby.txt`.
- `PlayLingo.cmd` + `aplikace.ps1` – spuštění okna; `priprava.ps1` – příprava (z okna s `-BezSestaveni`).

## Záznam hry pro ladění

⚙ Nastavení → Ladění → **Záznam hry do složky · Ano** (z příkazové řádky `--zaznam`). Každé spuštění
čtení pak dostane vlastní složku `zaznamy\pc-<čas>\` v repozitáři (v `.gitignore`), řádek „Složka
záznamů“ ji otevře:

- `zaznam.txt` – vše ze Záznamu v okně a k tomu řádky `CTENI`: co čtečka přečetla ve spodní třetině,
  kde to leželo v celém obraze (`[vlevo,nahoře-vpravo,dole]`) a co z toho vzala jako titulek
  (stejný přečtený text za sebou jen jednou). Čas na milisekundy.
- `snimek-HHMMSS-mmm.jpg` – celý obraz hry barevně v plném rozlišení, nejvýš jednou za 2 s a jen
  když se obraz změnil (`cteni.py`, ukládá v pozadí, čtení titulků nečeká). Text z celé obrazovky se
  za hry nečte – ze snímků se přečte až při rozboru (tentýž RapidOCR z `%LOCALAPPDATA%\PlayLingoTools\venv`).

Podle toho se ladí profily her (např. repliky nad postavami v Diablu IV) – Claude čte složku přímo,
bez předávání ze zařízení.

## Příkazová řádka (ladění)

1. Jednou: `powershell -File pc\priprava.ps1 [-Jazyk cs]`.
2. Ukázka: `powershell -File pc\spustit.ps1`; hra: `powershell -File pc\spustit.ps1 -Okno chiaki`.
   `-Vystup pc | tv | oboje` – kam s překladem (výchozí pruh na počítači). Přímo `playlingo-pc.bat`
   přijme navíc `--hra gta6|gta|rdr2|diablo4|…` a `--jen-titulky`, `--port` jiný než 8787 pro zkoušku vedle
   běžící čtečky.
3. Jen s výstupem do TV: na televizi LG spustit PlayLingo a vybrat vstup HDMI. Televize si počítač najde sama.

Pro snímky při ladění okno přijme `-Dplaylingo.stranka=Titulky` a `-Dplaylingo.autostart=1`;
`-Dplaylingo.parovani=1` otevře průvodce spárováním a s `-Dplaylingo.snimek=<složka>` uloží po 4 s
všechny jeho kroky jako `parovani-1…4.png` (jen okno průvodce, ne celá obrazovka) a zavře ho.
Bez `parovani` uloží `-Dplaylingo.snimek=<složka>` stránky okna (`okno-Domů.png`, `okno-Titulky.png`,
`okno-Nastavení.png`) a zkušební pruh (`pruh.png`, jen jeho obdélník) a aplikaci zavře.

Konzole pustí jen jedno takové spojení najednou: chiaki-ng na počítači a PlayLingo na Androidu
se u jedné konzole nedají použít současně. Stejně tak smí na portu 8787 v počítači poslouchat jen
jedno z dvojice čtečka / `webos/posilac.js`.

Změny jen ve složce `pc` nemění verzi aplikace a commitují se s `[skip ci]`.
