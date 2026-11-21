# Android TV / Google TV – první instalace PlayLinga

PlayLingo se do televize nebo boxu s Androidem nahraje jen **jednou**. Další verze už se instalují
samy z aplikace: na stránce Domů se ukáže **Aktualizovat**, stačí ho zvolit a instalaci potvrdit.
Spárování i nastavení zůstanou.

**Krátká adresa pro televizi:** `adusa89.github.io/PlayLingo/tv`
**Přímý odkaz na aplikaci:** https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk

<a id="instalace"></a>
<a id="downloader"></a>
## Cesta 1: aplikace Downloader (bez počítače a bez telefonu)

Bezplatná aplikace **Downloader** (od AFTVnews, oranžová ikona se šipkou) stáhne PlayLingo přímo
do televize. Nic se neposílá z telefonu ani nenahrává na flash disk.

### 1. Nainstalovat Downloader

1. Na ovladači stisknout Domů a otevřít **Google Play** (na Google TV: záložka **Aplikace** → hledat).
2. Vyhledat **Downloader** – správná je ta od **AFTVnews** (ne podobně pojmenované aplikace).
3. Zvolit **Instalovat** a pak **Otevřít**.
4. Při prvním spuštění se Downloader může zeptat na přístup k souborům / médiím → **Povolit**.
   Úvodní okno s novinkami se zavře tlačítkem **OK**.

### 2. Stáhnout PlayLingo

1. V Downloaderu je vlevo vybraná položka **Home** a vpravo řádek pro adresu. Po jeho zvolení se objeví
   klávesnice.
2. Napsat **`adusa89.github.io/PlayLingo/tv`** (bez `https://`, velká písmena P a L nevadí)
   a zvolit **Go**.
3. Stažení se rozběhne samo (ukazují se procenta). Trvá chvíli – soubor má desítky MB.
   - Když se místo stahování ukáže stránka s nápisem „Stahuji PlayLingo…“, zvolit na ní žlutý
     odkaz **PlayLingo.apk**.
   - Když nic nejde, napsat místo krátké adresy celý přímý odkaz výše.

### 3. Povolit Downloaderu instalaci aplikací (jen poprvé)

Android nainstaluje aplikaci jen z aplikace, které to bylo dovoleno. Po stažení se proto většinou ukáže
okno o zabezpečení:

- **Google TV** (Chromecast s Google TV, Google TV Streamer, novější televize Sony, TCL, Philips…):
  v okně zvolit **Nastavení**, v seznamu „Instalace neznámých aplikací“ přepnout **Downloader** na
  **Povoleno** a vrátit se tlačítkem Zpět.
- **Starší Android TV** (a když se okno neukáže): Nastavení → Aplikace → **Zabezpečení a omezení** →
  **Neznámé zdroje** → zapnout u **Downloader**.
- Když tam položka není, je schovaná ve vývojářských volbách: Nastavení → Systém → O systému →
  sedmkrát po sobě zvolit **Sestavení OS Android TV** („Nyní jste vývojář“). Pak znovu Aplikace →
  Zabezpečení a omezení → Neznámé zdroje.

Názvy se mezi výrobci trochu liší; stačí hledat „neznámé aplikace“ nebo „neznámé zdroje“.

### 4. Nainstalovat

1. Zpátky v Downloaderu se ukáže dotaz systému „Chcete tuto aplikaci nainstalovat?“ → **Instalovat**.
   (Kdyby se neukázal, stáhnout soubor v Downloaderu znovu – teď už se zeptá.)
2. Může se objevit okno **Play Protect** – viz [Když instalace nejde](#play-protect).
3. Po „Aplikace byla nainstalována“ zvolit **Otevřít** – PlayLingo se spustí a provede prvním
   nastavením.
4. Downloader pak nabídne smazání staženého souboru → **Smazat** (ušetří místo, už není potřeba).

### 5. Jak PlayLingo otevřít příště

- **Google TV:** Domů → **Aplikace** (nebo řada „Vaše aplikace“) → PlayLingo.
- **Starší Android TV:** Domů → řada **Aplikace** → PlayLingo. Ikona se tam dá přesunout
  dopředu (podržet OK → Přesunout).

Downloader už potom není potřeba – může zůstat, nebo se odinstaluje. **Aktualizace už jdou
v PlayLingu samy** (Domů → Aktualizovat). Při první aktualizaci se televize jednou zeptá na
povolení instalace z PlayLinga – povolí se stejně jako výše u Downloaderu.

<a id="telefon"></a>
## Cesta z telefonu s Androidem (bez Downloaderu)

1. Do telefonu nainstalovat PlayLingo: [PlayLingo.apk](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk).
2. **V TV jednou zapnout ladění přes síť:** Nastavení → Systém → Informace → sedmkrát **Sestavení OS** („Nyní jste
   vývojář“) → zpět → **Možnosti pro vývojáře** → zapnout **Ladění USB** (případně **Ladění přes síť** / **ADB**).
   Fire TV: Moje Fire TV → Možnosti pro vývojáře → **Ladění ADB**.
3. V PlayLingu v telefonu zvolit **Nahrát PlayLingo do Android TV**. Telefon TV v síti najde sám (nebo se adresa TV
   opíše z TV: Nastavení → Síť a internet).
4. TV se zeptá **„Povolit ladění?“** – zaškrtnout **Vždy povolit z tohoto počítače** a **OK**. PlayLingo se nahraje
   a spustí. Pak se ladění v TV může zase vypnout – další verze se instalují z aplikace samy.

Novější Google TV, které nabízejí jen **Bezdrátové ladění** s párovacím kódem, tahle cesta zatím neumí –
zbývá Downloader výš.

<a id="usb"></a>
## Cesta 2: USB flash disk

APK se stáhne z přímého odkazu výše v telefonu nebo počítači, nahraje na flash disk, ten se zapojí do
televize a APK se otevře správcem souborů (např. **File Commander** z Google Play). Povolení
„Neznámé zdroje“ pak dostane správce souborů, ne Downloaderu.

*Do budoucna:* možná půjde PlayLingo do televize nahrát i přímo z PlayLinga v telefonu s Androidem.
Zatím to není hotové – zbývá Downloader.

<a id="play-protect"></a>
## Když instalace nejde

- Okno **Play Protect** („Aplikace byla zablokována“ / „Neznámá aplikace“): zvolit
  **Další podrobnosti** → **Přesto nainstalovat**. PlayLingo není v Google Play, proto ho Play Protect
  nezná – je to v pořádku.
- Když tam ta možnost není: Google Play → ikona účtu → **Play Protect** → ozubené kolo → vypnout
  „Kontrolovat aplikace pomocí Play Protect“, nainstalovat a kontrolu zase zapnout.
- **„Aplikace nebyla nainstalována“:** málo místa – smazat v televizi něco nepotřebného (i staré soubory
  v Downloaderu: vlevo **Files**). Pomáhá i restart televize.
- **„Aplikace je v konfliktu s existujícím balíčkem“:** v televizi je stará verze s jiným podpisem
  (verze do 0.60). Odinstalovat ji (Nastavení → Aplikace → PlayLingo → Odinstalovat) a nainstalovat znovu.
- **Downloader ukazuje chybu stahování:** zkontrolovat, že je televize připojená k internetu, a zkusit
  přímý odkaz místo krátké adresy.

---
*English: on the TV, install **Downloader** (by AFTVnews) from Google Play. In Downloader type
`adusa89.github.io/PlayLingo/tv` and choose **Go**; the download starts by itself. When asked,
allow Downloader to install unknown apps (Google TV: **Settings** in the pop-up → Downloader →
Allowed; older Android TV: Settings → Apps → Security & restrictions → Unknown sources), then choose
**Install** and **Open**. If Play Protect warns you, choose More details → Install anyway. You only do
this once – later versions are offered inside PlayLingo (Home → Update). Alternatively copy the APK
to a USB stick and open it with a file manager.*
