# PlayLingo – čtečka pro počítač (zkušební)

Dělá na počítači s Windows totéž co překlad v aplikaci pro Android TV: čte anglické titulky
z obrazu hry, přeloží je bez internetu a hotové věty pošle do televize LG, kde je aplikace
ze složky `webos` ukáže v pruhu přes hru.

Je to pomůcka na zkoušení, ne hotový výrobek: běžný uživatel má titulky do LG posílat
z PlayLinga na Androidu (telefon nebo box). Počítač se hodí, když se ladí čtení a překlad –
vše je vidět v okně s výpisem a změna se dá vyzkoušet za pár vteřin.

## Z čeho se skládá

- `src/Main.kt` – čtečka. Výběr titulků, rozhodování, kdy větu ukázat a schovat, i posílání do
  televize jsou **tytéž soubory** jako v aplikaci (`app/src/main/java/cz/playlingo/core`), jen
  přeložené pro počítač. Oprava v `core` se tak projeví na Androidu i tady.
- `cteni.py` – pomocník pro to, co Android dělá svými knihovnami: snímek okna s hrou, čtení
  textu (RapidOCR) a překlad (model Argos, jádro CTranslate2).
- `priprava.ps1`, `spustit.ps1` – jednorázová příprava a spuštění.

## Postup

1. Jednou: `powershell -File pc\priprava.ps1` (stáhne asi 0,6 GB do `%LOCALAPPDATA%\PlayLingoTools`,
   do systému nic neinstaluje). Potřebuje Python 3.10+ a Javu 17 v `JAVA_HOME`.
2. Na televizi LG spustit PlayLingo a vybrat vstup HDMI. Televize si počítač najde sama.
3. Ukázka bez PlayStationu: `powershell -File pc\spustit.ps1` – věty ukázkové hry se kreslí
   na umělý obrázek, čtou, překládají a posílají do televize.
4. Skutečná hra: obraz z PlayStationu musí běžet v okně na počítači, např. v programu
   chiaki-ng (stejné spojení jako Remote Play, které používá i aplikace). Pak
   `powershell -File pc\spustit.ps1 -Okno chiaki` – čte se okno, jehož titulek obsahuje zadané slovo.
   Okno může být zakryté jiným, ale nesmí být minimalizované.

Konzole pustí jen jedno takové spojení najednou: počítač a PlayLingo na Androidu se u jedné
konzole nedají použít současně. Stejně tak smí na portu 8787 v počítači poslouchat jen jedno
z dvojice čtečka / `webos/posilac.js`.

Změny jen ve složce `pc` nemění verzi aplikace a commitují se s `[skip ci]`.
