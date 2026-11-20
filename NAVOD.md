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

Stejný postup najdeš i v aplikaci na stránce Instalace.

**Další verze už nahrávat nemusíš.** Když vyjde nová verze, aplikace ji nabídne na stránce Domů.
Zvol „Aktualizovat“ a instalaci potvrď na ovladači – spárování i nastavení zůstanou.
Při prvním použití televize požádá o povolení instalace z aplikace PlayLingo (Neznámé zdroje).
Platí od verze 0.61; starší verzi je potřeba jednou odinstalovat a novou nahrát ručně.

## 3. První nastavení (průvodce tě provede)

**Připrav PlayStation** (jen jednou; průvodce ti to ukáže v kroku Připojení konzole – bez toho
se párování nepovede):
1. Na PS5 zvol Přidat uživatele a založ **druhý účet** PlayStation s jiným e-mailem (zdarma). Proč
   druhý účet, je vysvětlené v bodě 4.
2. Přepni se na PS5 na toho druhého uživatele a zapni **Nastavení → Systém → Remote Play →
   Povolit Remote Play**.
   Hned tam zapni i **Skrýt stav připojení Remote Play** (anglicky Hide Remote Play Connection
   Status) – jinak ti ve hře pořád svítí hláška „Aplikace Remote Play je připojena“. Totéž nastav
   i pod svým herním uživatelem. Když tam volba není, aktualizuj systém PS5.
3. PS5 a zařízení s PlayLingem musí být ve stejné domácí síti.
4. Aby ho PlayLingo umělo probudit z klidového režimu: **Nastavení → Systém → Úspora energie →
   Funkce dostupné v klidovém režimu** → zapni „Zůstat připojen k internetu“ a „Povolit zapnutí
   systému PS5 ze sítě“.

Pak v aplikaci projdi průvodce:
1. **Zařízení:** na čem PlayLingo běží – Android TV nebo box s Androidem, nebo telefon či tablet
   (ten jen čte a hotové titulky posílá do televize LG nebo Samsung, viz bod 8).
2. **Konzole:** PlayStation 5, nebo Xbox (viz bod 7b).
3. **Televize:** Android TV (pruh ukazuje toto zařízení), LG, nebo Samsung. U LG a Samsungu jde
   aplikaci do televize nahrát rovnou z tohoto kroku, nebo později.
4. **Jazyk překladu:** vyber ho a počkej na stažení jazykového modelu (jednou, asi 30 MB).
   **Kvalita překladu:** buď **DeepL** (mnohem lepší, zdarma s vlastním klíčem – postup a čtvereček
   ukáže průvodce, viz bod 9 – klíč se jen zkopíruje a vloží v telefonu), nebo jen offline překlad (bez internetu, ale hodně doslovný).
5. **Zobrazení titulků** (jen Android TV): zapni aplikaci v nastavení televize: Usnadnění →
   PlayLingo → Zapnout.
6. **Připojení konzole:** spáruj PlayStation – viz další bod (Xbox: bod 7b).

Máš-li víc televizí LG nebo Samsung, pamatuje si PlayLingo vzhled titulků a oblasti pro každou zvlášť
a přepne je samo podle televize, ke které se připojí (v Titulkách je nahoře vidět, pro kterou).

Každou volbu jde později změnit v **Nastavení** – ozubené kolo (⚙), které je vidět pořád (v telefonu vpravo nahoře, na televizi dole v levém sloupci). Nastavení má záložky Obecné, Čtení a překlad a Experimentální (v ní i Ladění a hlášení); co která volba dělá, je v Instalaci v kapitole „Volby v Nastavení“.
Tam je i povolení, jazyk aplikace, a v části Experimentální zkušební verze a hlášení chyby. V menu jsou dál stránky Domů, Konzole (párování, nadpis podle konzole:
PlayStation nebo Xbox), Titulky a Instalace.

Stránka **Domů** ukazuje jen to podstatné: stav překladu, tlačítka Spustit / Zastavit a pod stavem přehled
v bodech – zelená tečka jede, oranžová potřebuje pozornost (a má rovnou tlačítko), šedá je jen informace:
konzole a jestli přichází obraz, televize (u Android TV povolení pruhu), síť tohoto zařízení, rychlost čtení,
jazyk a způsob překladu a verze aplikace (i v televizi). OK na řádku konzole nebo
televize je změní. Náhled obrazu z konzole je na stránce Konzole.

**Instalace** (dřív Nápověda) je postup od začátku v kapitolách (Instalace, Příprava konzole, Televize, Jazyk a kvalita
překladu, Spárování, Hraní, Když něco nejde, Volby v Nastavení). Kapitola se rozbalí po OK a ukazuje
jen to, co platí pro tvou televizi a konzoli; ✔ znamená hotovo. Nahoře jde přepnout „Instalace pro“
jinou televizi.

## 4. Spárování PlayStationu (jen jednou)

**Důležité: aplikace se připojuje DRUHÝM účtem PlayStation, ne tím, na kterém hraješ.**
Kdyby se připojila tvým herním účtem, konzole by ti při hraní vypínala ovladač a zhasínala obraz
(bere to tak, že ses přesunul hrát jinam).

Druhý účet a zapnuté Remote Play pod ním máš připravené z bodu 3. V aplikaci se pak přihlásíš
tím druhým účtem a číslo pro propojení zobrazíš na konzoli také pod ním. Hned po spárování se na
ovladači přepni na účet, na kterém hraješ, a hraj jako vždy, se svými hrami. Druhý účet se jen
„dívá“ – nic na něm nekupuješ ani nehraješ.

Aplikace tě provede šesti kroky, na každé obrazovce je jedna věc. Televize a PS5 musí být
ve stejné domácí síti. Připrav si telefon – bude potřeba jen teď, k hraní už ne.

1. **Zkontroluj na PlayStationu Remote Play** (pod druhým uživatelem): Nastavení → Systém →
   Remote Play → „Povolit Remote Play“ musí být zapnuté.
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

Texty písní (rádio v autě, hudba ve scéně), které hra označuje notou ♪, se nepřekládají.

## 7. Problémy
- **Stránka z čtverečku se v telefonu neotevře** – telefon nejspíš není na stejné Wi-Fi jako televize (nebo je na mobilních datech). Připoj ho k domácí Wi-Fi a naskenuj čtvereček znovu. Když to nejde, zvol v televizi „Stránka se neotevřela“: záložní čtvereček vede rovnou na přihlášení Sony a kód pak opíšeš ručně (krok 5).
- **Adresa nebo kód z telefonu neplatí** – platí jen pár minut; přihlas se v telefonu znovu a vlož novou adresu.
- **PlayLingo v telefonu nepustí přihlášení Sony** – zvol v něm „Nejde to? Přihlásit se v prohlížeči“ a prázdnou stránku „redirect“ pak nasdílej do PlayLinga (Sdílet → PlayLingo).
- **Nejde spárovat** – číslo z PS5 platí jen pár minut, vyžádej si nové. Zkontroluj adresu a účet.
- **„PlayStation neodpovídá“** – je konzole zapnutá (ne vypnutá ze zásuvky) a ve stejné síti?
- **Ve hře svítí „Aplikace Remote Play je připojena“** – na PS5 zapni Nastavení → Systém → Remote Play → Skrýt stav připojení Remote Play (pod herním i druhým uživatelem), viz bod 3.
- **Účet na PS5 má přihlašovací kód** – PlayStation ho při spojení odmítne a na stránce PlayStation se objeví políčko pro kód (4 číslice); zadej ho tam.
- **Pruh se neukáže** – zkontroluj Usnadnění (bod 3.5, nebo ⚙ Nastavení → Obecné → Povolení) a v Titulcích nastav vzhled – pruh se při tom ukáže na zkoušku.
- **Nic se nečte** – zapni ⚙ Nastavení → Experimentální → Ladění a hlášení → Diagnostický režim; uvidíš, co aplikace čte.
- **Na PS5 pořád naskakuje „ovladač odpojen / účet připojen“** – s druhým účtem se připojuje i jiné zařízení
  (třeba aplikace PS Remote Play) a PS5 pak spojení přehazuje. Domů to od 0.97 napíše. Druhý účet ať slouží jen PlayLingu.
- **Telefon jako čtečka: spojení vypadává, když telefon zhasne displej** – v ⚙ Nastavení → Obecné zapni
  „Povolit běh na pozadí“ (jinak ho šetření baterie uspí; poprvé se na to aplikace zeptá sama při Spustit).
  Od 0.97 drží PlayLingo telefon při překladu vzhůru sám.
- **Spojení vypadává na Wi-Fi 2,4 GHz** – ve stejném pásmu vysílají ovladače konzole. Připoj konzoli kabelem
  nebo dej zařízení s PlayLingem na 5 GHz. Když telefon sám přechází na 2,4 GHz, je to router (stejný název sítě
  pro obě pásma a slabší 5 GHz signál); pomůže samostatný název sítě pro 5 GHz.
- **Překlad běží, ale titulky nenaskakují** – podívej se na stránku Domů na řádek pod stavem překladu. Ukazuje, jak daleko to došlo: jestli se aplikace s PlayStationem spojila a jestli z něj opravdu přichází obraz. Když hlásí „Spojeno s PlayStationem, ale … nepřišel žádný obraz“, aplikace spojení po chvíli sama obnoví; když to trvá dlouho, překlad zastav, zkontroluj Wi-Fi a spusť ho znovu.
- **Aplikace minule spadla** – po dalším spuštění to napíše na stránce Domů. Kdyby se to opakovalo, pošli hlášení (další bod).
- **Hlášení chyby**: Instalace → Když něco nejde (nebo ⚙ Nastavení → Experimentální → Ladění a hlášení) → „Vytvořit hlášení chyby“ otevře přes celou obrazovku hlášení o stavu aplikace: verzi, televizi, stav spojení, poslední pád a poslední události. Stačí ho vyfotit (šipkami ▲▼ se posouvá, v telefonu prstem) a poslat. Hesla, klíče ani číslo účtu v něm nejsou. Delší verze se zároveň uloží do souboru, jehož cesta je na konci hlášení. Tlačítkem **Kopírovat** ho dáš do schránky, **Sdílet** ho pošle jako soubor (e-mailem, do chatu…). Na televizi nejjednodušší: tlačítko **Do telefonu** ukáže čtvereček – naskenuj ho telefonem (i iPhonem) na stejné Wi-Fi, stáhni hlášení a pošli ho autorovi chatem nebo e-mailem. Máš-li televizi LG nebo Samsung, je v hlášení i oddíl Televize – co se dělo v aplikaci v televizi.
- **Záznam pro vývojáře v domácí síti**: ⚙ Nastavení → záložka Experimentální → „Zpřístupnit záznam v domácí síti“ → ZAP. Pod řádkem se ukáže adresa (např. `http://192.168.1.23:8789/zaznam?k=ab3k7m`), přes kterou si vývojář na počítači ve stejné síti stáhne celé hlášení (bez hesel, klíčů a čísla účtu). Po 2 hodinách se to samo vypne; jde to vypnout i hned.

## 7b. Xbox (Series X|S / One)
PlayLingo umí číst titulky i z Xboxu. Hra jde do televize dál kabelem HDMI; PlayLingo si z Xboxu
přes vzdálené hraní (Remote Play) bere jen kopii obrazu v nižší kvalitě. Nic neovládá a zvuk
nepřehrává – hraješ normálně ovladačem.

1. **Na Xboxu (jen poprvé):** Nastavení → Zařízení a připojení → Vzdálené funkce → zapni
   „Povolit vzdálené funkce“. Režim napájení nastav na „Spánek“.
2. **V PlayLingu:** vyber **Xbox** v průvodci (krok Konzole), v ⚙ Nastavení → Konzole, nebo na
   stránce Domů OK na souhrnném řádku → Konzole. Položka menu **Konzole** pak ukazuje stránku Xbox.
3. Na stránce Xbox (menu Konzole) zvol **Přihlásit účet Microsoft**. Na obrazovce se ukáže kód. Na telefonu
   nebo počítači otevři **microsoft.com/link** (nebo načti čtvereček telefonem), zadej kód
   a přihlas se účtem, kterým hraješ na Xboxu. V telefonu stačí tlačítko „Otevřít stránku“.
4. Objeví se seznam tvých Xboxů – vyber ten, na kterém hraješ (jediný se vybere sám).
5. Spusť překlad jako obvykle a přepni televizi na HDMI s Xboxem.

Když Xbox spí nebo je vypnutý, PlayLingo ho nebudí – počká, až ho zapneš, a pak se připojí samo.
Přihlášení si PlayLingo pamatuje (zašifrované v zařízení); odhlásit se jde na stránce Xbox.
Stav spojení na stránce Domů funguje stejně jako u PlayStationu.

## 8. Televize LG a Samsung
Televize LG a Samsung si obraz hry samy nepřečtou. PlayLingo v nich jen ukazuje hru s pruhem
a číst a překládat musí telefon nebo box s Androidem ve stejné domácí síti. Typ televize se
volí v průvodci (krok Televize) a mění v ⚙ Nastavení → Typ televize nebo OK na souhrnném řádku
na stránce Domů: Android TV (pruh ukazuje toto zařízení, jako dřív), LG (webOS) nebo Samsung
(Tizen). V telefonu jsou tlačítka „Použít telefon pro televizi LG“ a „… Samsung“. Souhrnný řádek
na Domů ukazuje, jestli je aplikace z televize připojená; dokud se nikdy nepřipojila, je na Domů
upozornění s tlačítkem „Nahrát do televize“. Nahrát (i aktualizovat) aplikaci v televizi jde
kdykoli i z ⚙ Nastavení. Kdo měl dřív zapnuté „Posílat titulky do televize LG“, má typ LG.

Pruh v televizi vypadá podle Titulků v PlayLingu (průhlednost, písmo, barva, poloha). Když je
v televizi starší PlayLingo, než nese aplikace v telefonu, upozorní na to Domů s tlačítkem k nahrání.

**Vzhled titulků přímo v televizi** (aplikace v televizi od 1.4.0): při hře stiskni na ovladači
**OK**. Otevře se malá nabídka: pozadí pruhu, velikost písma, poloha pruhu a originál. Šipkami
▲▼ vybíráš, ◀▶ měníš, Zpět nabídku zavře. Změna se hned uloží v PlayLingu v telefonu, takže
telefon a televize mají vždycky stejné nastavení. Všechno ostatní se nastavuje jen v telefonu.

### Televize LG

**Nejjednodušší je pomocník v aplikaci** („Nahrát PlayLingo do televize LG“): ukáže postup krok
za krokem a kroky, které pozná sám, odškrtne. Televizi v síti najde sám (adresu psát nemusíš), pozná,
že je zapnutý vývojářský režim i Key Server, a na odkaz na účet LG ukáže čtvereček.

**Nahrání PlayLingo do televize LG z telefonu nebo boxu (bez počítače):**
1. Na webu webostv.developer.lge.com si založ účet LG (zdarma).
2. V televizi otevři obchod LG Content Store, nainstaluj aplikaci **Developer Mode**, přihlas se
   v ní tím účtem a zapni **Dev Mode Status**. Televize se restartuje.
3. Znovu otevři Developer Mode a zapni **Key Server**. Uvidíš adresu televize (IP) a **Passphrase**.
4. V PlayLingu zvol „Nahrát PlayLingo do televize LG“ (krok Televize v průvodci, ⚙ Nastavení,
   upozornění na Domů, pomocník v telefonu nebo Instalace), opiš adresu a Passphrase a zvol „Nahrát do televize“. PlayLingo se v televizi
   nainstaluje a spustí. Příště už Passphrase není potřeba – stejným tlačítkem televizi
   aktualizuješ. Starší verzi přes novější nikdy nenahraje.

   **V telefonu nemusíš nic opisovat:** zvol „Načíst z obrazovky televize kamerou“, povol kameru
   a namiř telefon na obrazovku Developer Mode tak, aby byla vidět adresa i Passphrase. Jakmile je
   PlayLingo přečte dvakrát stejně, ukáže je; zvol „Použít“, porovnej údaje s televizí a zvol
   „Nahrát do televize“. Pokud upozorní na podobné znaky (0/O, 1/I), zkontroluj hlavně je –
   špatná Passphrase nic nepokazí, televize ji jen odmítne. Kamera nic nefotí, neukládá ani
   neposílá. Na televizi a boxu (bez kamery) se tlačítko neukazuje.

Vývojářský režim po čase vyprší a PlayLingo pak z televize zmizí. Kolik dní zbývá, ukazuje
PlayLingo u tlačítka (pod 3 dny upozorní i na stránce Domů). Prodloužit ho jde jen v televizi: Developer Mode →
**Extend**. PlayLingo nic samo neprodlužuje.

Jiné cesty: z počítače nástrojem LG `ares` (postup ve `webos/CTI-ME.md`), nebo z obchodu LG,
až tam PlayLingo bude.

### Televize Samsung
Samsung pustí do televize vlastní aplikaci jen ve vývojářském režimu a jen podepsanou
certifikátem, který Samsung vystaví na tvůj účet Samsung (zdarma) právě pro tvou televizi.
To PlayLingo za tebe udělat neumí, proto se aplikace nahrává nástrojem Samsungu. Průvodce
„Nahrát PlayLingo do televize Samsung“ (krok Televize v průvodci, ⚙ Nastavení, upozornění na Domů,
pomocník v telefonu, Instalace)
tě provede:
1. **Vývojářský režim:** v televizi otevři Aplikace (Apps), na ovladači zadej 1 2 3 4 5, zapni
   **Developer mode** a do **Host PC IP** napiš adresu počítače (nebo telefonu), ze kterého budeš
   nahrávat. Televizi restartuj (podrž vypínač, dokud nezhasne a znovu nenaběhne).
2. **Ulož soubor** s aplikací (tlačítko „Uložit soubor“) – třeba na Disk Google, odkud ho vezmeš
   v počítači. Soubor není podepsaný.
3. **Podepiš a nahraj** ho: nejjistěji v počítači nástrojem **Tizen Studio** od Samsungu (Device
   Manager → připojit televizi, Certificate Manager → certifikát Samsung s přihlášením účtem
   Samsung, pak podepsat a nahrát), nebo komunitním instalátorem, který umí vlastní soubor .wgt
   (např. Apps2Samsung). Certifikát si schovej – aktualizace musí být podepsaná stejným.
4. **Spusť PlayLingo v televizi** a vyber vstup HDMI. Průvodce ukáže „Hotovo“, až se aplikace
   z televize opravdu připojí, a do televize pošle zkušební větu.

Podrobnosti a důvody jsou v `tizen/CTI-ME.md`.

## 9. Lepší překlad: DeepL (doporučeno)
Offline překlad je zdarma a bez internetu, ale hodně doslovný. **DeepL** překládá přirozeně, rozumí
slangu i nadávkám a pro hráče je zdarma – stačí vlastní bezplatný účet DeepL. Nastavíš ho v průvodci
(krok **Kvalita překladu**) nebo kdykoli v ⚙ Nastavení → Čtení a překlad → **Lepší překlad přes
DeepL – nastavit** (je to tamtéž jako ⚙ Nastavení → Experimentální).

Jak to funguje: offline překlad se ukáže hned jako vždy a DeepL ho během chvilky (do 1,5 s) potichu
nahradí lepším – i v televizi LG / Samsung. Když DeepL nejde (bez internetu, vyčerpaný měsíční limit,
špatný klíč), překládá se dál offline a důvod je vidět v Nastavení.

### Nastavení klíče – nic se nepíše ovladačem
Klíč je dlouhý (asi 40 znaků), proto ho do televize **nepíšeš** – zkopíruješ a vložíš ho jen v telefonu.

1. **Televize ukáže čtvereček.** Naskenuj ho telefonem (Android i iPhone), který je na **stejné
   Wi‑Fi** jako televize. Otevře se malá stránka „PlayLingo · DeepL“.
2. **Nemáš účet DeepL?** Na té stránce zvol **Otevřít DeepL** (otevře se na nové kartě):
   - nahoře **Developers → Free API Key**,
   - zaregistruj se e-mailem a heslem (platební kartu DeepL nechce),
   - potvrď e-mail odkazem, který ti DeepL pošle.
3. **Zkopíruj klíč.** Na stránce PlayLingo zvol **Otevřít stránku s klíčem** (nebo otevři
   https://www.deepl.com/your-account/keys) a u klíče klepni na tlačítko pro **kopírování**.
   Klíč končí na `:fx`.
4. **Vlož klíč.** Vrať se na kartu „PlayLingo · DeepL“, **podrž prst v políčku → Vložit** a zvol
   **Odeslat do televize**.
5. Televize klíč ověří, uloží a DeepL sama zapne. Hotovo – víc už nic nastavovat nemusíš.

Když telefon stránku neotevře (je na mobilních datech nebo na jiné Wi‑Fi), připoj ho ke stejné Wi‑Fi
a čtvereček naskenuj znovu. Ruční psaní klíče ovladačem je jen záloha: pod čtverečkem zvol **Zavřít**
a objeví se políčko. Přímo v telefonu s PlayLingem klíč jen vložíš do políčka.

### Dobré vědět
- **Limit:** bezplatný účet DeepL má měsíční limit znaků (aplikace ho po ověření ukáže, i kolik zbývá);
  stačí na desítky hodin hraní. Opakované věty se znovu neposílají.
- **Placený DeepL Pro** funguje taky – aplikace pozná druh klíče sama.
- **Soukromí:** se zapnutým DeepL se **rozpoznaný text titulků posílá přes internet do DeepL**. Klíč je
  jen tvůj, zůstává zašifrovaný v zařízení s PlayLingem a nikam jinam se neposílá (ani do hlášení).
- Klíč jde kdykoli odebrat tlačítkem **Odebrat klíč** a DeepL vypnout v Nastavení.

## Licence a zdrojový kód
Spojení s PlayStationem používá otevřenou knihovnu chiaki-ng
(https://github.com/streetpea/chiaki-ng, licence AGPL-3.0). Zdrojový kód aplikace včetně
úprav je v tomto repozitáři. Aplikace není produktem společnosti Sony.
Nahrání do televize LG používá knihovnu JSch (https://github.com/mwiede/jsch, licence BSD) a postup
podle projektu PlayCaption (licence AGPL-3.0).
Spojení s Xboxem používá knihovnu WebRTC (sestavení stream-webrtc-android, licence BSD/Apache 2.0)
a postup podle projektů PlayCaption (AGPL-3.0), xbox-xcloud-player (MIT) a XStreaming (AGPL-3.0).
Aplikace není produktem společnosti Microsoft.
