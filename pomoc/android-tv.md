# Android TV / Google TV – první instalace PlayLinga

PlayLingo se do televize nebo boxu s Androidem nahraje jen **jednou**. Další verze už se instalují
samy z aplikace: na stránce Domů se ukáže **Aktualizovat**, zvolíš ho a instalaci potvrdíš.
Spárování i nastavení zůstanou.

**Krátká adresa pro televizi:** `adusa89.github.io/PlayLingo/tv`
**Přímý odkaz na aplikaci:** https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk

<a id="instalace"></a>
<a id="downloader"></a>
## Cesta 1: aplikace Downloader (bez počítače a bez telefonu)

Bezplatná aplikace **Downloader** (od AFTVnews, oranžová ikona se šipkou) stáhne PlayLingo přímo
do televize. Nic se neposílá z telefonu ani nenahrává na flash disk.

### 1. Nainstaluj Downloader

1. Na ovladači stiskni Domů a otevři **Google Play** (na Google TV: záložka **Aplikace** → hledat).
2. Vyhledej **Downloader** – správná je ta od **AFTVnews** (ne podobně pojmenované aplikace).
3. Zvol **Instalovat** a pak **Otevřít**.
4. Při prvním spuštění se Downloader může zeptat na přístup k souborům / médiím → **Povolit**.
   Úvodní okno s novinkami zavři tlačítkem **OK**.

### 2. Stáhni PlayLingo

1. V Downloaderu je vlevo vybraná položka **Home** a vpravo řádek pro adresu. Zvol ho – objeví se
   klávesnice.
2. Napiš **`adusa89.github.io/PlayLingo/tv`** (bez `https://`, velká písmena P a L nevadí)
   a zvol **Go**.
3. Stažení se rozběhne samo (vidíš procenta). Trvá chvíli – soubor má desítky MB.
   - Když se místo stahování ukáže stránka s nápisem „Stahuji PlayLingo…“, zvol na ní žlutý
     odkaz **PlayLingo.apk**.
   - Když nic nejde, napiš místo krátké adresy celý přímý odkaz výše.

### 3. Povol Downloaderu instalaci aplikací (jen poprvé)

Android nainstaluje aplikaci jen z aplikace, které to dovolíš. Po stažení se proto většinou ukáže
okno o zabezpečení:

- **Google TV** (Chromecast s Google TV, Google TV Streamer, novější televize Sony, TCL, Philips…):
  v okně zvol **Nastavení**, v seznamu „Instalace neznámých aplikací“ přepni **Downloader** na
  **Povoleno** a vrať se tlačítkem Zpět.
- **Starší Android TV** (a když se okno neukáže): Nastavení → Aplikace → **Zabezpečení a omezení** →
  **Neznámé zdroje** → zapni u **Downloader**.
- Když tam položka není, je schovaná ve vývojářských volbách: Nastavení → Systém → O systému →
  sedmkrát po sobě zvol **Sestavení OS Android TV** („Nyní jste vývojář“). Pak znovu Aplikace →
  Zabezpečení a omezení → Neznámé zdroje.

Názvy se mezi výrobci trochu liší; hledej „neznámé aplikace“ nebo „neznámé zdroje“.

### 4. Nainstaluj

1. Zpátky v Downloaderu se ukáže dotaz systému „Chcete tuto aplikaci nainstalovat?“ → **Instalovat**.
   (Kdyby se neukázal, stáhni soubor v Downloaderu znovu – teď už se zeptá.)
2. Může se objevit okno **Play Protect** – viz [Když instalace nejde](#play-protect).
3. Po „Aplikace byla nainstalována“ zvol **Otevřít** – PlayLingo se spustí a provede tě prvním
   nastavením.
4. Downloader pak nabídne smazání staženého souboru → **Smazat** (ušetří místo, už není potřeba).

### 5. Jak PlayLingo otevřít příště

- **Google TV:** Domů → **Aplikace** (nebo řada „Vaše aplikace“) → PlayLingo.
- **Starší Android TV:** Domů → řada **Aplikace** → PlayLingo. Ikonu si tam můžeš přesunout
  dopředu (podrž OK → Přesunout).

Downloader už potom nepotřebuješ – můžeš ho nechat, nebo odinstalovat. **Aktualizace už jdou
v PlayLingu samy** (Domů → Aktualizovat). Při první aktualizaci se televize jednou zeptá na
povolení instalace z PlayLinga – povol ho stejně jako výše u Downloaderu.

<a id="telefon"></a>
## Cesta z telefonu s Androidem (bez Downloaderu)

1. Do telefonu nainstaluj PlayLingo: [PlayLingo.apk](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk).
2. **V TV jednou zapni ladění přes síť:** Nastavení → Systém → Informace → sedmkrát **Sestavení OS** („Nyní jste
   vývojář“) → zpět → **Možnosti pro vývojáře** → zapni **Ladění USB** (případně **Ladění přes síť** / **ADB**).
   Fire TV: Moje Fire TV → Možnosti pro vývojáře → **Ladění ADB**.
3. V PlayLingu v telefonu zvol **Nahrát PlayLingo do Android TV**. Telefon TV v síti najde sám (nebo adresu TV
   opiš z TV: Nastavení → Síť a internet).
4. TV se zeptá **„Povolit ladění?“** – zaškrtni **Vždy povolit z tohoto počítače** a **OK**. PlayLingo se nahraje
   a spustí. Pak můžeš ladění v TV zase vypnout – další verze se instalují z aplikace samy.

Novější Google TV, které nabízejí jen **Bezdrátové ladění** s párovacím kódem, tahle cesta zatím neumí –
použij Downloader výš.

<a id="usb"></a>
## Cesta 2: USB flash disk

Stáhni APK z přímého odkazu výše v telefonu nebo počítači, nahraj ho na flash disk, zapoj do
televize a otevři ho správcem souborů (např. **File Commander** z Google Play). Povolení
„Neznámé zdroje“ pak dáváš správci souborů, ne Downloaderu.

*Do budoucna:* možná půjde PlayLingo do televize nahrát i přímo z PlayLinga v telefonu s Androidem.
Zatím to není hotové – použij Downloader.

<a id="play-protect"></a>
## Když instalace nejde

- Okno **Play Protect** („Aplikace byla zablokována“ / „Neznámá aplikace“): zvol
  **Další podrobnosti** → **Přesto nainstalovat**. PlayLingo není v Google Play, proto ho Play Protect
  nezná – je to v pořádku.
- Když tam ta možnost není: Google Play → ikona účtu → **Play Protect** → ozubené kolo → vypni
  „Kontrolovat aplikace pomocí Play Protect“, nainstaluj a kontrolu zase zapni.
- **„Aplikace nebyla nainstalována“:** málo místa – smaž v televizi něco nepotřebného (i staré soubory
  v Downloaderu: vlevo **Files**). Pomáhá i restart televize.
- **„Aplikace je v konfliktu s existujícím balíčkem“:** v televizi je stará verze s jiným podpisem
  (verze do 0.60). Odinstaluj ji (Nastavení → Aplikace → PlayLingo → Odinstalovat) a nainstaluj znovu.
- **Downloader ukazuje chybu stahování:** zkontroluj, že je televize připojená k internetu, a zkus
  přímý odkaz místo krátké adresy.

---
*English: on the TV, install **Downloader** (by AFTVnews) from Google Play. In Downloader type
`adusa89.github.io/PlayLingo/tv` and choose **Go**; the download starts by itself. When asked,
allow Downloader to install unknown apps (Google TV: **Settings** in the pop-up → Downloader →
Allowed; older Android TV: Settings → Apps → Security & restrictions → Unknown sources), then choose
**Install** and **Open**. If Play Protect warns you, choose More details → Install anyway. You only do
this once – later versions are offered inside PlayLingo (Home → Update). Alternatively copy the APK
to a USB stick and open it with a file manager.*
