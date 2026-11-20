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

- **Domů** – velký stav (Připraveno / Načítání… / Běží / Čeká na okno hry / Chybí příprava),
  Spustit / Zastavit a řádky „Popis · hodnota“ s tečkou jako v aplikaci pro TV: okno hry
  (nenalezeno = tlačítko Vybrat okno), překlad (chybí = tlačítko Připravit – stáhne knihovny
  a model asi 0,6 GB do `%LOCALAPPDATA%\PlayLingoTools`, do systému nic neinstaluje), výstup,
  rychlost čtení a poslední titulek.
- **Titulky** – vzhled pruhu: velikost písma, barva, tučné, pozadí, poloha (přes titulky / nad
  titulky / dole) a Zkušební pruh na 4 s. Změny platí hned, i za běhu.
- **⚙ Nastavení** – okno hry (část titulku okna, Vybrat = nabídka otevřených oken), ukázková hra
  bez PlayStationu, jazyk překladu, výstup (Pruh na PC / TV LG / Samsung / PC i TV), příprava
  a Záznam (výpis čtení). Změna okna, jazyka nebo výstupu za běhu čtení sama spustí znovu.

Nastavení se ukládá do `%LOCALAPPDATA%\PlayLingoTools\aplikace.properties`.

Skutečná hra: v chiaki-ng se připojit k PS5 (hlavní účet), v PlayLingu Spustit. Okno chiaki-ng
může být přes celou obrazovku (ne výhradní režim celé obrazovky) nebo zakryté jiným oknem, ale
nesmí být minimalizované.

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
