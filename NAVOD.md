# PlayLingo – stručný návod

Aplikace pro Android TV, boxy, Chromecast a projektory. Sama se po domácí síti připojí
k PlayStationu 5, přečte anglické titulky hry, přeloží je (offline, zdarma) a ukáže je
v černém pruhu přes obraz. Hra jde do televize dál kabelem HDMI.

## 1. APK
Stáhni `PlayLingo.apk`:
https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk

## 2. Instalace
Nahraj APK do televize (USB flash + správce souborů, nebo aplikace Downloader) a nainstaluj.
Když aplikace nejde nainstalovat nebo aktualizovat:
1. Objeví-li se okno Play Protect, zvol „Další podrobnosti“ → „Přesto nainstalovat“.
2. Když tam ta možnost není: Google Play → ikona účtu → Play Protect → ozubené kolo → vypni
   „Kontrolovat aplikace pomocí Play Protect“, nainstaluj a pak kontrolu zase zapni.
3. Hlásí-li televize „instalace z neznámých zdrojů“: Nastavení → Aplikace → Zabezpečení a omezení →
   Neznámé zdroje → povol aplikaci, ze které instaluješ (Downloader nebo správce souborů).
4. Skončí-li instalace chybou: uvolni místo v televizi, případně starou verzi odinstaluj a nainstaluj znovu.

Stejný postup najdeš i v aplikaci na stránce Nápověda.

**Další verze už nahrávat nemusíš.** Když vyjde nová verze, aplikace ji nabídne na stránce Domů.
Zvol „Aktualizovat“ a instalaci potvrď na ovladači – spárování i nastavení zůstanou.
Při prvním použití televize požádá o povolení instalace z aplikace PlayLingo (Neznámé zdroje).
Platí od verze 0.61; starší verzi je potřeba jednou odinstalovat a novou nahrát ručně.

## 3. První nastavení (průvodce tě provede)
1. Vyber jazyk překladu.
2. Zapni aplikaci v nastavení televize: Usnadnění → PlayLingo → Zapnout.
3. Počkej na stažení jazykového modelu (jednou, asi 30 MB).
4. Spáruj PlayStation – viz další bod.

## 4. Spárování PlayStationu (jen jednou)

**Důležité: aplikace se připojuje DRUHÝM účtem PlayStation, ne tím, na kterém hraješ.**
Kdyby se připojila tvým herním účtem, konzole by ti při hraní vypínala ovladač a zhasínala obraz
(bere to tak, že ses přesunul hrát jinam).

1. Na PlayStationu zvol Přidat uživatele a založ nový účet PlayStation s jiným e-mailem (je zdarma).
2. Pod tím druhým uživatelem zapni Nastavení → Systém → Remote Play → Povolit Remote Play.
3. V aplikaci se v průvodci přihlas tím druhým účtem (QR kód v telefonu).
4. Číslo pro propojení zobraz na konzoli také pod druhým uživatelem.
5. Hned po spárování se na ovladači přihlas na účet, na kterém hraješ, a hraj jako vždy, se svými hrami.
   Druhý účet se jen „dívá“ – nic na něm nekupuješ ani nehraješ.

Aplikace tě provede šesti kroky, na každé obrazovce je jedna věc. Televize a PS5 musí být
ve stejné domácí síti. Připrav si telefon – bude potřeba jen teď, k hraní už ne.

1. **Zapni na PlayStationu Remote Play.** Na PS5: Nastavení → Systém → Remote Play →
   zapni „Povolit Remote Play“.
2. **Najdi PlayStation.** Aplikace ho v síti najde sama. Když ne, zadej adresu ručně
   (PS5 → Nastavení → Síť → Stav připojení → Adresa IPv4).
3. **Naskenuj čtvereček telefonem.** Otevři v telefonu fotoaparát, namiř ho na čtvereček
   v televizi a klepni na odkaz. Otevře se stránka PlayLingo z tvé televize – telefon proto
   musí být na stejné Wi-Fi jako televize.
   **Bez telefonu:** pod čtverečkem je adresa stránky (např. `http://192.168.1.20:8765/p/k7mq2xra`).
   Opiš ji do prohlížeče v počítači ve stejné síti a další krok udělej tam – adresu stránky
   „redirect“ zkopíruješ z řádku s adresou v prohlížeči (Ctrl+C) a vložíš na kartu PlayLingo.
4. **V telefonu se přihlas.** Na stránce klepni na „Přihlásit se u PlayStation“ a zadej e-mail
   a heslo druhého účtu. Když se stránka zeptá na „passkey“, zvol „Remind Me Later“. Nakonec
   zůstane skoro prázdná stránka s jediným slovem „redirect“. Zkopíruj její adresu (klepni
   nahoře na adresu a zvol Kopírovat), vrať se na stránku PlayLingo a adresu tam vlož.
   Televize pak pokračuje sama, nic se neopisuje.
5. **Záloha: opiš kód ručně.** Tenhle krok televize sama přeskočí. Potřebuješ ho, jen když
   se stránka z čtverečku v telefonu neotevře (v kroku 3 pak zvol „Stránka se neotevřela“).
   Krok má vlastní čtvereček, který vede rovnou na přihlášení Sony: naskenuj ho, přihlas se,
   na prázdné stránce „redirect“ klepni nahoře na adresu, najdi v ní „code=“ a opiš do
   televize to, co je hned za ním, až po znak & (i s tečkou a velkými písmeny). Zvol
   „Ověřit kód“. Kód platí jen pár minut.
6. **Opiš číslo z PlayStationu.** Na PS5: Nastavení → Systém → Remote Play → Propojit
   zařízení. Osmimístné číslo opiš do televize a zvol Spárovat.

**PlayLingo i v telefonu (jen Android, nepovinné).** Stejnou aplikaci můžeš nainstalovat i do
telefonu – tam nepřekládá, jen pomáhá s přihlášením. Na stránce z čtverečku zvol „Otevřít
v aplikaci PlayLingo“, přihlas se druhým účtem přímo v aplikaci a ta pošle účet do televize sama,
bez kopírování. Účet si zapamatuje (jen jeho číslo, heslo ne), takže při příštím párování nebo
na další televizi stačí naskenovat čtvereček a klepnout na „Odeslat do televize“.

## 5. Hraní
1. Na stránce Domů zvol „Spustit překlad“ a počkej na „Překlad běží“ (objeví se i malý náhled
   toho, co aplikace z PlayStationu vidí).
2. Přepni televizi na HDMI s PlayStationem a hraj. Ve hře měj zapnuté anglické titulky.
3. Titulky si aplikace na obrazovce najde sama.
4. Každá postava má v pruhu jméno ve své barvě. Když hra nabídne volby odpovědi pod sebou
   (očíslované nebo zarovnané vlevo), ukáže je pruh jako seznam s odrážkami, každou přeloženou
   zvlášť. Menu hry a pokyny k ovládání se dál nepřekládají.

## 6. Zkouška bez PlayStationu
Na stránce Domů zvol „Zkouška bez PlayStationu“. Ukáže se ukázková hra s anglickými titulky
a přes ně pruh s překladem. Tím si ověříš, že pruh a překlad v televizi fungují.

## 7. Problémy
- **Stránka z čtverečku se v telefonu neotevře** – telefon nejspíš není na stejné Wi-Fi jako televize (nebo je na mobilních datech). Připoj ho k domácí Wi-Fi a naskenuj čtvereček znovu. Když to nejde, zvol v televizi „Stránka se neotevřela“: záložní čtvereček vede rovnou na přihlášení Sony a kód pak opíšeš ručně (krok 5).
- **Adresa nebo kód z telefonu neplatí** – platí jen pár minut; přihlas se v telefonu znovu a vlož novou adresu.
- **PlayLingo v telefonu nepustí přihlášení Sony** – zvol v něm „Nejde to? Přihlásit se v prohlížeči“ a prázdnou stránku „redirect“ pak nasdílej do PlayLinga (Sdílet → PlayLingo).
- **Nejde spárovat** – číslo z PS5 platí jen pár minut, vyžádej si nové. Zkontroluj adresu a účet.
- **„PlayStation neodpovídá“** – je konzole zapnutá (ne vypnutá ze zásuvky) a ve stejné síti?
- **Účet na PS5 má přihlašovací kód** – zadej ho na stránce PlayStation (4 číslice).
- **Pruh se neukáže** – zkontroluj Usnadnění (bod 3.2) a vyzkoušej bod 6.
- **Nic se nečte** – zapni Nastavení → Diagnostický režim; uvidíš, co aplikace čte.
- **Překlad běží, ale titulky nenaskakují** – podívej se na stránku Domů na řádek pod stavem překladu. Ukazuje, jak daleko to došlo: jestli se aplikace s PlayStationem spojila a jestli z něj opravdu přichází obraz. Když hlásí „Spojeno s PlayStationem, ale … nepřišel žádný obraz“, aplikace spojení po chvíli sama obnoví; když to trvá dlouho, překlad zastav, zkontroluj Wi-Fi a spusť ho znovu.
- **Aplikace minule spadla** – po dalším spuštění to napíše na stránce Domů. Kdyby se to opakovalo, pošli hlášení (další bod).
- **Hlášení chyby**: Nápověda → „Vytvořit hlášení chyby“ otevře přes celou obrazovku hlášení o stavu aplikace: verzi, televizi, stav spojení, poslední pád a poslední události. Stačí ho vyfotit (šipkami ▲▼ se posouvá, v telefonu prstem) a poslat. Hesla, klíče ani číslo účtu v něm nejsou. Delší verze se zároveň uloží do souboru, jehož cesta je na konci hlášení.

## 7b. Xbox (Series X|S / One)
PlayLingo umí číst titulky i z Xboxu. Hra jde do televize dál kabelem HDMI; PlayLingo si z Xboxu
přes vzdálené hraní (Remote Play) bere jen kopii obrazu v nižší kvalitě. Nic neovládá a zvuk
nepřehrává – hraješ normálně ovladačem.

1. **Na Xboxu (jen poprvé):** Nastavení → Zařízení a připojení → Vzdálené funkce → zapni
   „Povolit vzdálené funkce“. Režim napájení nastav na „Spánek“.
2. **V PlayLingu:** na stránce Domů klikni na řádek **Konzole** a vyber **Xbox** (totéž je
   v Nastavení). Stránka konzole v menu se pak jmenuje Xbox.
3. Na stránce Xbox zvol **Přihlásit účet Microsoft**. Na obrazovce se ukáže kód. Na telefonu
   nebo počítači otevři **microsoft.com/link** (nebo načti čtvereček telefonem), zadej kód
   a přihlas se účtem, kterým hraješ na Xboxu. V telefonu stačí tlačítko „Otevřít stránku“.
4. Objeví se seznam tvých Xboxů – vyber ten, na kterém hraješ (jediný se vybere sám).
5. Spusť překlad jako obvykle a přepni televizi na HDMI s Xboxem.

Když Xbox spí nebo je vypnutý, PlayLingo ho nebudí – počká, až ho zapneš, a pak se připojí samo.
Přihlášení si PlayLingo pamatuje (zašifrované v zařízení); odhlásit se jde na stránce Xbox.
Stav spojení na stránce Domů funguje stejně jako u PlayStationu.

## 8. Televize LG
Televize LG si obraz hry sama nepřečte. PlayLingo v ní jen ukazuje hru s pruhem a číst
a překládat musí telefon nebo box s Androidem ve stejné domácí síti (v telefonu tlačítko
„Použít telefon pro televizi LG“, v boxu Nastavení → „Posílat titulky do televize LG“).

**Nahrání PlayLingo do televize LG z telefonu nebo boxu (bez počítače):**
1. Na webu webostv.developer.lge.com si založ účet LG (zdarma).
2. V televizi otevři obchod LG Content Store, nainstaluj aplikaci **Developer Mode**, přihlas se
   v ní tím účtem a zapni **Dev Mode Status**. Televize se restartuje.
3. Znovu otevři Developer Mode a zapni **Key Server**. Uvidíš adresu televize (IP) a **Passphrase**.
4. V PlayLingu zvol „Nahrát PlayLingo do televize LG“ (Nastavení, pomocník v telefonu nebo
   Nápověda), opiš adresu a Passphrase a zvol „Nahrát do televize“. PlayLingo se v televizi
   nainstaluje a spustí. Příště už Passphrase není potřeba – stejným tlačítkem televizi
   aktualizuješ. Starší verzi přes novější nikdy nenahraje.

Vývojářský režim po čase vyprší a PlayLingo pak z televize zmizí. Kolik dní zbývá, ukazuje
PlayLingo u tlačítka (pod 3 dny upozorní). Prodloužit ho jde jen v televizi: Developer Mode →
**Extend**. PlayLingo nic samo neprodlužuje.

Jiné cesty: z počítače nástrojem LG `ares` (postup ve `webos/CTI-ME.md`), nebo z obchodu LG,
až tam PlayLingo bude.

## Licence a zdrojový kód
Spojení s PlayStationem používá otevřenou knihovnu chiaki-ng
(https://github.com/streetpea/chiaki-ng, licence AGPL-3.0). Zdrojový kód aplikace včetně
úprav je v tomto repozitáři. Aplikace není produktem společnosti Sony.
Nahrání do televize LG používá knihovnu JSch (https://github.com/mwiede/jsch, licence BSD) a postup
podle projektu PlayCaption (licence AGPL-3.0).
Spojení s Xboxem používá knihovnu WebRTC (sestavení stream-webrtc-android, licence BSD/Apache 2.0)
a postup podle projektů PlayCaption (AGPL-3.0), xbox-xcloud-player (MIT) a XStreaming (AGPL-3.0).
Aplikace není produktem společnosti Microsoft.
