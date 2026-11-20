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

- **Domů** – velký stav (Připraveno / Hledání PS5… / Probouzení PS5… / Spouštění hry… / Běží /
  Čeká na okno hry / Chybí příprava), Hrát (u chiaki-ng) nebo Spustit / Zastavit a řádky
  „Popis · hodnota“ s tečkou jako v aplikaci pro TV: chiaki-ng (chybí = Stáhnout), PS5 (nespárováno
  = Spárovat, zapnutá / klidový režim / nenalezena = Hledat), u jiného programu okno hry
  (nenalezeno = tlačítko Vybrat okno), překlad (chybí = tlačítko Připravit – stáhne knihovny
  a model asi 0,6 GB do `%LOCALAPPDATA%\PlayLingoTools`, do systému nic neinstaluje), výstup,
  rychlost čtení a poslední titulek.
- **Titulky** – vzhled pruhu: velikost písma, barva, tučné, pozadí, poloha (přes titulky / nad
  titulky / dole) a Zkušební pruh na 4 s. Změny platí hned, i za běhu.
- **⚙ Nastavení** – okno hry (část titulku okna, Vybrat = nabídka oken, kde může běžet hra:
  chiaki / PS Remote Play / Xbox nahoře, skrytá systémová okna ne), ukázková hra
  bez PlayStationu, jazyk překladu, výstup (Pruh na PC / TV LG / Samsung / PC i TV), příprava,
  DeepL (zapnutí, klíč, odkaz na klíče v účtu DeepL) a Záznam (výpis čtení). Změna okna, jazyka,
  výstupu nebo DeepL za běhu čtení sama spustí znovu.

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
2. „PS5 · nespárováno“ → **Spárovat**: otevře se chiaki-ng, v něm se jednou spáruje s PS5
   (hlavní účet). chiaki-ng si to uloží do registru (`HKCU\Software\Chiaki\Chiaki\registered_hosts`),
   PlayLingo odtud bere jméno konzole a číslo pro probuzení.
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

## Příkazová řádka (ladění)

1. Jednou: `powershell -File pc\priprava.ps1 [-Jazyk cs]`.
2. Ukázka: `powershell -File pc\spustit.ps1`; hra: `powershell -File pc\spustit.ps1 -Okno chiaki`.
   `-Vystup pc | tv | oboje` – kam s překladem (výchozí pruh na počítači).
3. Jen s výstupem do TV: na televizi LG spustit PlayLingo a vybrat vstup HDMI. Televize si počítač najde sama.

Pro snímky při ladění okno přijme `-Dplaylingo.stranka=Titulky` a `-Dplaylingo.autostart=1`.

Konzole pustí jen jedno takové spojení najednou: chiaki-ng na počítači a PlayLingo na Androidu
se u jedné konzole nedají použít současně. Stejně tak smí na portu 8787 v počítači poslouchat jen
jedno z dvojice čtečka / `webos/posilac.js`.

Změny jen ve složce `pc` nemění verzi aplikace a commitují se s `[skip ci]`.
