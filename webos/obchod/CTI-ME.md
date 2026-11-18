# PlayLingo pro televize LG – verze pro obchod

Aplikace, která se podává do obchodu LG (LG Content Store). Televize ukáže hru ze vstupu HDMI
a přes ni pruh s překladem. Číst a překládat musí PlayLingo na Androidu (od 0.63 tlačítko „Použít telefon pro televizi LG“, v boxu volba
Nastavení → „Posílat titulky do televize LG“) ve stejné domácí síti.

Tohle je jediná aplikace PlayLingo pro televizi; přehled všeho kolem LG je v `../CTI-ME.md`.
Zprávy mezi Androidem a televizí popisuje `app/src/main/java/cz/playlingo/core/TvSender.kt`
a v televizi je obsluhuje jen `app/app.js` (`hledej`, `zkus`, `prevezmi`). Diagnostická zkouška
ve `../zkouska` po síti nic nepřijímá.

## Co je ve složce

- `app/` – aplikace (id `cz.playlingo.tv`, verze v `app/appinfo.json`). **Id se po podání už nedá
  změnit.** Texty jsou česky (i pro slovenštinu) a anglicky, podle jazyka televize.
- `ikona-400.png` – ikona, která se nahrává do obchodu zvlášť. `ikony.py` nakreslí ji i ikony v balíčku.
- `snimky/` – snímky obrazovky 1920×1080 pro obchod, česky a anglicky.
- `dokumenty/` – dva dokumenty pro LG vyplněné do jejich šablon (verze šablon z listopadu 2026):
  - `ux_scenario_PlayLingo.pptx` – popis obrazovek pro testery. Na titulní straně chybí jméno
    a e-mail vývojáře a datum podání je potřeba upravit podle skutečnosti.
  - `self_evaluation_checklist_5.0.xlsx` – kontrolní seznam. Body, které se aplikace netýkají, mají
    „NA“. Body bez výsledku (24 na prvním listu) se musí vyzkoušet na televizi a teprve pak dostat
    „Pass“; LG vrací podání, kde výsledek neodpovídá skutečnosti. Na druhém listu zbývá bod 5
    (jestli v obchodě LG už není aplikace stejného jména).

## Sestavení a zkouška

- Balíček: `ares-package --no-minify webos/obchod/app -o webos/obchod` (nástroj LG z `@webos-tools/cli`).
  Vznikne `cz.playlingo.tv_<verze>_all.ipk`; necommituje se.
- Do televize ve vývojářském režimu: `ares-install -d lgtv webos/obchod/cz.playlingo.tv_<verze>_all.ipk`.
- V počítači: otevřít `app/index.html` v prohlížeči. Značka v adrese vynutí jazyk a obrazovku:
  `#cs`, `#en`, `#en-ukazka`, `#cs-napoveda`. Stejně vznikly snímky ve `snimky/`.
- `requiredACG` v `appinfo.json` vyjmenovává služby televize, které aplikace volá (model televize,
  adresa v síti, jazyk). Od webOS 27 bez něj obchod aplikaci nepřijme. Přibude-li volání další
  služby, doplň i sem.

## Podání u LG (seller.lgappstv.com)

Potřeba: účet prodejce, balíček `.ipk`, `ikona-400.png`, snímky, texty níže a dva dokumenty podle
šablon LG – popis ovládání pro testery (UX scenario) a kontrolní seznam (self-checklist; šablona je
na webostv.developer.lge.com/distribute/app-self-checklist).

### Texty do obchodu

**Název:** PlayLingo

**Krátký popis (EN):** Translated subtitles for PlayStation games, shown in a bar over the game.

**Popis (EN):**
PlayLingo shows translated subtitles while you play on your PlayStation 5. The TV displays the game
from its HDMI input and draws a black bar with the translation over it.

Reading and translating the subtitles is done by the free PlayLingo app on an Android phone or
Android TV box in the same home network; the TV finds it automatically and only displays the text.
Nothing is sent to the internet. The Android app is available at github.com/adusa89/PlayLingo.

Not a product of Sony Interactive Entertainment. “PlayStation” and “PS5” are their trademarks.

**Popis (CS):**
PlayLingo ukazuje přeložené titulky při hraní na PlayStationu 5. Televize ukáže hru ze vstupu HDMI
a přes ni černý pruh s překladem.

Titulky čte a překládá bezplatná aplikace PlayLingo v telefonu nebo boxu s Androidem ve stejné
domácí síti; televize si ji najde sama a jen ukazuje text. Nic se neposílá do internetu. Aplikace
pro Android je na github.com/adusa89/PlayLingo.

Aplikace není produktem společnosti Sony Interactive Entertainment. „PlayStation“ a „PS5“ jsou
jejich ochranné známky.

### Pokyny pro testery (podklad pro UX scenario)

1. **Home.** Seven buttons: HDMI 1–4 (first row), Demo, How it works and Language (second row).
   Language switches the app between English and Czech; the choice is remembered. Arrow keys move
   the yellow highlight, OK confirms, the Magic Remote pointer highlights and clicks. Back on this
   screen leaves the app.
2. **Demo** (no other device needed). A sample scene with an English subtitle and its translation
   in the black bar; sentences change every 3.5 s. Back or OK returns Home.
3. **How it works.** Four setup steps. Back or OK returns Home.
4. **HDMI 1–4.** The TV shows the chosen HDMI input full screen and a note “Looking for PlayLingo
   in your home network…”. Without the Android app the note stays; the input picture is still
   shown. Moving the pointer shows a Back button for 4 s. Back returns Home.
5. **With the Android app** (PlayLingo 0.62+, Settings → Send subtitles to LG TV, same network):
   the note changes to “PlayLingo is connected”, then each translated subtitle appears in the bar
   and disappears when the game subtitle ends. Full translation needs a PlayStation 5 paired with
   the Android app; the Demo screen shows the same result without it.

The app has no login, no payments, no ads and plays no audio or video of its own.
