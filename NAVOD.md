# PlayLingo – stručný návod

Aplikace pro Android TV, boxy, Chromecast a projektory. Sama se po domácí síti připojí
k PlayStationu 5, přečte anglické titulky hry, přeloží je (offline, zdarma) a ukáže je
v černém pruhu přes obraz. Hra jde do televize dál kabelem HDMI.

**Nejrychlejší start:** https://adusa89.github.io/PlayLingo/ – po výběru TV a toho, na čem se nastavuje (počítač,
Android, iPhone, jen ovladač), stránka ukáže jen potřebné kroky.

## 1. APK
Ke stažení `PlayLingo.apk`:
https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk

## 2. Instalace
Nejjednodušší je aplikace **Downloader** (od AFTVnews, zdarma v Google Play v televizi) – nic se
neposílá z telefonu:
1. V televizi nainstalovat z Google Play **Downloader** a otevřít ho (přístup k souborům → Povolit).
2. Do řádku pro adresu napsat kód **6340991** a zvolit **Go** – PlayLingo se stáhne. (Záloha: adresa
   `adusa89.github.io/PlayLingo/tv`.)
3. Povolit Downloaderu instalaci: na Google TV v okně, které se ukáže, **Nastavení** → Downloader →
   Povoleno; na starší Android TV Nastavení → Aplikace → Zabezpečení a omezení → Neznámé zdroje.
4. Zvolit **Instalovat** a pak **Otevřít**. Stažený soubor pak Downloader nabídne smazat.

Podrobný postup i pro různé značky televizí: [pomoc/android-tv.md](pomoc/android-tv.md#instalace).
Jinak jde APK nahrát i z USB flash disku správcem souborů. (Do budoucna možná půjde nahrát
PlayLingo do televize přímo z PlayLinga v telefonu – zatím ne.)

Když aplikace nejde nainstalovat nebo aktualizovat:
1. Objeví-li se okno Play Protect, zvolit „Další podrobnosti“ → „Přesto nainstalovat“.
2. Když tam ta možnost není: Google Play → ikona účtu → Play Protect → ozubené kolo → vypnout
   „Kontrolovat aplikace pomocí Play Protect“, nainstalovat a pak kontrolu zase zapnout.
3. Hlásí-li televize „instalace z neznámých zdrojů“: Nastavení → Aplikace → Zabezpečení a omezení →
   Neznámé zdroje → povolit aplikaci, ze které se instaluje (Downloader nebo správce souborů).
4. Skončí-li instalace chybou: uvolnit místo v televizi, případně starou verzi odinstalovat a nainstalovat znovu.

Stejný postup je i v aplikaci na stránce Instalace.

**Bez Downloaderu, z telefonu s Androidem (zkušební):** nainstalovat PlayLingo do telefonu, v televizi
zapnout Možnosti pro vývojáře (Nastavení → Systém → Informace → 7× Sestavení OS) a v nich Ladění USB /
Ladění přes síť (Fire TV: Ladění ADB). V telefonu v PlayLingu zvolit „Nahrát PlayLingo do Android TV“ –
televizi najde sám, nahraje do ní PlayLingo a spustí ho. Televize se jednou zeptá „Povolit ladění?“ –
zaškrtnout Vždy povolit a potvrdit. Potom jde ladění v televizi zase vypnout. Bezdrátové ladění
s párovacím kódem (Android 11+) zatím nejde.

**Další verze už se ručně nenahrávají.** Když vyjde nová verze, aplikace ji nabídne na stránce Domů.
Stačí zvolit „Aktualizovat“ a instalaci potvrdit na ovladači – spárování i nastavení zůstanou.
Při prvním použití televize požádá o povolení instalace z aplikace PlayLingo (Neznámé zdroje).
Platí od verze 0.61; starší verzi je potřeba jednou odinstalovat a novou nahrát ručně.

## Víc TV nebo víc PlayStationů
- **Dvě Android TV:** PlayLingo se nahraje do každé zvlášť a každá má vlastní nastavení i vlastní čtvereček
  „Nastavit z telefonu“. Aplikace PlayLingo v telefonu (režim „Nastavuji TV“) se připojí k té TV, jejíž čtvereček
  se naskenuje.
- **Dvě TV LG / Samsung s jedním telefonem:** telefon si pamatuje vzhled titulků a oblasti pro každou TV zvlášť
  a použije ty pro TV, ve které je PlayLingo právě spuštěné.
- **Dva PlayStationy:** na stránce Konzole **Spárovat další PS5** (dosavadní zůstane spárovaná) a řádkem **Aktivní PS5**
  se mezi nimi přepíná (při běžícím překladu se PlayLingo spojí znovu). Když je v síti víc PS5, **Najít** nic nevybírá
  samo – ukáže je k výběru (už spárované s poznámkou „spárováno“). **Zrušit spárování** zruší jen tu aktivní.

## 3. První nastavení (provede průvodce)

Průvodce má 9 kroků, na každé obrazovce jedna věc. Na PlayStationu se zatím nic nenastavuje – co je potřeba,
ukáže až krok Připojení konzole (bod 4).

1. **Jazyky:** všechny tři jazyky na jednom místě – jazyk aplikace (texty se hned přepnou), **Překládat z**
   (v jakém jazyce jsou titulky ve hře; výchozí angličtina, jen jazyky v latince – čtení obrazu jiné písmo neumí)
   a **Jazyk překladu** (do čeho se překládá). Vše jde později změnit (jazyk aplikace v ⚙ Nastavení → Obecné,
   ostatní v Titulkách).
   Pod tím (Android TV s PlayStationem) **Nastavit telefonem nebo počítačem (doporučeno)**, nebo **Nastavit přímo
   na TV**. Telefonem či počítačem ukáže TV jeden čtvereček a pod ním adresu pro počítač – v telefonu (i iPhonu)
   nebo v prohlížeči počítače ve stejné síti se otevře stránka **PlayLingo · nastavení TV**
   se vším, co se jinak píše ovladačem:
   - **1 · 🔵 Účet pro PlayLingo** – Android: tlačítko **Otevřít v aplikaci PlayLingo** (nic se nekopíruje);
     iPhone: přihlášení v prohlížeči s obrázkem, kde adresu „redirect“ zkopírovat, nebo zkratka **PlayLingo**
     v Sdílet ([návod](https://adusa89.github.io/PlayLingo/zkratka.html)).
   - **2 · PS5 pod 🔵 účtem** – na PS5 se přepnout na 🔵 účet, zapnout Remote Play a **Propojit zařízení**; osmimístné
     číslo se opíše do telefonu (ne do TV) a TV se spáruje sama.
   - **3 · DeepL** (nepovinné) – klíč pro lepší překlad.
   TV mezitím ukazuje seznam, který se sám odškrtává (✔ účet · ✔ PS5 spárovaná · ✔ DeepL). Na konci se na PS5
   přepnout zpět na 🟢 herní účet a v TV zvolit **Pokračovat na TV** – průvodce pokračuje jazykem překladu a hotové
   kroky se už jen potvrdí. Totéž kdykoli později – i u už nastavené TV – v ⚙ Nastavení → Obecné nebo na stránce
   Konzole → **Nastavit z telefonu**: stránka ukáže, co v TV už je, a u hotového nabídne **Změnit** / **Spárovat
   znovu** (jiný účet, nové spárování, jiný klíč DeepL).
   - **Android:** nahoře volba **Jak pokračovat?** – **V aplikaci PlayLingo** (bez kopírování, číslo z PS5 kamerou,
     celé nastavení TV včetně vzhledu titulků; bez aplikace ji tlačítko stáhne), nebo **Pokračovat v prohlížeči**
     (nic se neinstaluje). iPhone a počítač vidí rovnou prohlížeč.
   - **Číslo z PS5 kamerou:** Android – **📷 Načíst číslo kamerou** (čtečka v PlayLingu v telefonu, číslo jde rovnou
     do TV); iPhone – podržet prst v políčku → **Skenovat text** a namířit na číslo.
   - **Kde se co dělá** (rozbalovací řádek na stránce i v TV): 📱 telefon jen přihlášení 🔵 účtem · 🎮 PS5 přepnout
     na 🔵 účet, Remote Play, Propojit zařízení → číslo, pak zpět na 🟢 herní účet · 📺 TV nic, jen čtvereček.
   - **Webová obdoba aplikace (iPhone, počítač):** na stejné stránce je nahoře **Stav TV** (stejné body jako na Domů)
     a dole **4 · Titulky a hra** – jazyk překladu, Překládat z, hra, velikost písma, pozadí, poloha pruhu, originál,
     pokyny / úkol / překlad menu (u her, které je znají) a DeepL zapnout / vypnout. Změna se v TV projeví hned.
     Méně častá nastavení zůstávají v TV nebo v aplikaci PlayLingo v telefonu.
2. **Zařízení:** na čem PlayLingo běží – Android TV nebo box s Androidem, nebo telefon či tablet
   (ten jen čte a hotové titulky posílá do televize LG nebo Samsung, viz bod 8).
3. **Konzole:** PlayStation 5, nebo Xbox (viz bod 7b).
4. **Televize:** Android TV (pruh ukazuje toto zařízení), LG, nebo Samsung. U LG a Samsungu tu je tlačítko
   pro nahrání aplikace do televize – jde přeskočit a aplikaci nahrát později (Domů, Instalace).
   V telefonu průvodce sám hledá televizi v domácí síti: najde-li jen LG, nebo jen Samsung, rovnou ji
   předvybere a ukáže „✔ V síti · …“ (volbu jde změnit).
5. **Offline překlad:** počkat na stažení jazykového balíčku pro zvolenou dvojici jazyků (jednou, asi 30 MB).
6. **Lepší překlad (DeepL):** jedna věta proč a jeden čtvereček pro telefon – stránka v telefonu provede
   registrací na DeepL, zkopírováním klíče i jeho vložením – viz bod 9. Bez klíče zvolit **Přeskočit – zůstat
   u offline překladu**; DeepL jde nastavit kdykoli později v Titulkách → Kvalita překladu. Po přijetí klíče
   se ukáže „✔ DeepL je nastavený“ a Pokračovat.
7. **Zobrazení titulků** (jen Android TV): zapnout aplikaci v nastavení televize: Usnadnění →
   PlayLingo → Zapnout. Po návratu do PlayLinga s povolením průvodce sám pokračuje. U LG a Samsungu jen
   krátké vysvětlení.
8. **Připojení konzole:** spárování PlayStationu v šesti malých krocích – viz bod 4 (Xbox: bod 7b). Má
   jen tlačítka spárování a pod nimi malé **Spárovat později** (průvodce skočí na Hotovo). Co jde, udělá
   sám: zapnutou PS5 najde v síti hned na prvním kroku a přeskočí hledání; po napsání (nebo přečtení
   kamerou) osmimístného čísla z PS5 rovnou spáruje a po spárování přejde na Hotovo.
9. **Hotovo:** jak se hraje; se spárovanou konzolí tlačítko Spustit.

U více televizí LG nebo Samsung si PlayLingo pamatuje vzhled titulků a oblasti pro každou zvlášť
a přepne je samo podle televize, ke které se připojí (v Titulkách je nahoře vidět, pro kterou).

Každou volbu jde později změnit v **Nastavení** – ozubené kolo (⚙), které je vidět pořád (v telefonu vpravo nahoře, na televizi dole v levém sloupci). Nastavení má záložky Obecné, Čtení a překlad a Experimentální (v ní i Ladění a hlášení); co která volba dělá, je v Instalaci v kapitole „Volby v Nastavení“.
Tam je i povolení, jazyk aplikace, a v části Experimentální zkušební verze a hlášení chyby. Hra (profil hry) a DeepL
(Kvalita překladu) jsou v Titulkách. V menu jsou dál stránky Domů, Konzole (párování, nadpis podle konzole:
PlayStation nebo Xbox), Titulky a Instalace.

Stránka **Domů** ukazuje jen to podstatné: stav překladu, tlačítka Spustit / Zastavit a pod stavem přehled
v bodech – zelená tečka jede, oranžová potřebuje pozornost (a má rovnou tlačítko), šedá je jen informace:
konzole a jestli přichází obraz, televize (u Android TV povolení pruhu), síť tohoto zařízení, rychlost čtení,
jazyk a způsob překladu a verze aplikace (i v televizi). OK na řádku konzole nebo
televize je změní. Náhled obrazu z konzole je na stránce Konzole.

**Instalace** (dřív Nápověda) je postup od začátku v kapitolách (Instalace, Příprava konzole, Televize, Jazyk a kvalita
překladu, Spárování, Hraní, Když něco nejde, Volby v Nastavení). Kapitola se rozbalí po OK a ukazuje
jen to, co platí pro zvolenou televizi a konzoli; ✔ znamená hotovo. Nahoře jde přepnout „Instalace pro“
jinou televizi.

## 4. Spárování PlayStationu (jen jednou)

**Důležité: aplikace se připojuje DRUHÝM účtem PlayStation, ne tím, na kterém se hraje.**
Kdyby se připojila herním účtem, konzole by při hraní vypínala ovladač a zhasínala obraz
(bere to tak, že se hra přesunula jinam). Po spárování se na ovladači přepne zpět na herní účet
a hraje se jako vždy, se stejnými hrami. Druhý účet se jen „dívá“.

Aby se účty nepletly, značí je aplikace (i Nápověda, stránka v telefonu a tento návod) vždy stejně:

- 🔵 **Účet pro PlayLingo** – druhý účet, jen pro PlayLingo: přihlašuje se jím v telefonu, na PS5 se na něj
  přepne, pod ním se zapne Remote Play a vyvolá osmimístné číslo. U jména účtu je vždy 🔵 (např. „🔵 JMÉNO“).
- 🟢 **Herní účet** – ten, na kterém se hraje. Na něj se na PS5 po spárování přepne zpátky.

**PS5 spáruje jen tehdy, když osmimístné číslo vyvolá na PS5 stejný uživatel, jakým se PlayLingo
přihlásilo.** Proto aplikace účet nikdy nebere potichu a jeho jméno (Online ID) ukazuje až do konce.

Aplikace provede šesti kroky, na každé obrazovce je jedna věc. V průvodci je nahoře „Krok 8 z 9“
a pod ním malý podtitul „Spárování · 3 z 6“, na stránce Konzole pruh s dílky.

1. **Zapnout PlayStation.** Stejná domácí síť jako zařízení s PlayLingem (nejlépe obojí kabelem).
   Na PS5 se zatím nic nenastavuje.
2. **Najít PlayStation.** Aplikace ho v síti najde sama. Když ne, zadat adresu ručně
   (PS5 → Nastavení → Síť → Stav připojení → Adresa IPv4).
3. **🔵 Účet pro PlayLingo.** Nahoře je legenda obou značek (🔵 / 🟢). Druhý účet PlayStation, na kterém se nehraje. Ještě není? Založí se zdarma –
   návod je pod tlačítky (čtvereček, v telefonu tlačítko; stránka [pomoc/ps5.md](pomoc/ps5.md)).
   - Když už je účet uložený (i po „Zrušit spárování“), ukáže se **Použít účet 🔵 JMÉNO** a **Přihlásit jiný účet**.
   - Když jsou účty uložené v PlayLingu v telefonu, nabídnou se se jmény.
4. **Přihlásit se 🔵 účtem pro PlayLingo** (telefon), ne 🟢 herním. V telefonu otevřít fotoaparát, namířit ho na čtvereček v televizi
   a klepnout na odkaz – otevře se stránka PlayLingo z této televize (telefon musí být na stejné Wi-Fi).
   **Android:** nahoře je žluté **Otevřít v aplikaci PlayLingo** – když PlayLingo v telefonu není, tlačítko
   ho stáhne (po instalaci se vrátit na stránku a klepnout znovu); přihlášení proběhne v aplikaci a nic se nekopíruje.
   **iPhone** (nebo „Přihlášení v prohlížeči“ níž na stránce): klepnout na „Přihlásit se u PlayStation“, zadat e-mail a heslo **🔵 druhého** účtu (na „passkey“ zvolit
   „Remind Me Later“). Zůstane skoro prázdná stránka „redirect“ – zkopírovat její adresu a vložit ji na
   stránku PlayLingo. Televize pak pokračuje sama.
   **Bez telefonu:** pod čtverečkem je adresa stránky (např. `http://192.168.1.20:8765/p/k7mq2xra`) –
   stačí ji otevřít v prohlížeči počítače ve stejné síti.
   **Záloha – ruční opsání kódu:** když se stránka v telefonu neotevře, zvolit „Stránka se neotevřela“.
   Krok má vlastní čtvereček rovnou na přihlášení Sony; na stránce „redirect“ najít v adrese „code=“
   a opsat do televize to, co je za ním, až po znak & (i s tečkou a velkými písmeny). Kód platí pár minut.
   Výsledek: **„✔ Ověřený účet: 🔵 JMÉNO“** (s malým tlačítkem Přihlásit jiný účet).
5. **Na PS5 přepnout na uživatele 🔵 JMÉNO** (tlačítko PS → ikona profilu → Přepnout uživatele) a v
   **Nastavení → Systém → Remote Play** zapnout **Povolit Remote Play** a **Skrýt stav připojení Remote Play**
   (anglicky Hide Remote Play Connection Status – jinak ve hře svítí „Aplikace Remote Play je připojena“;
   nastavit ho i pod 🟢 herním uživatelem). Když tam volba není, aktualizovat systém PS5.
6. **Kód z PS5.** Pod uživatelem 🔵 JMÉNO zvolit Nastavení → Systém → Remote Play → **Propojit zařízení**.
   Osmimístné číslo opsat do PlayLinga (v telefonu ho přečte kamerou) a zvolit Spárovat. Po úspěchu:
   „Spárováno (účet 🔵 JMÉNO). Na ovladači se přepnout zpět na 🟢 herní účet.“ Totéž připomene poslední
   krok průvodce (Hotovo): „Na PS5 se přepnout zpět na 🟢 herní účet“.

**Chyba CE‑110032‑7 na PS5 / „Spárování se nepovedlo“ v aplikaci:** číslo nejspíš vyvolal jiný
uživatel než 🔵 JMÉNO. Aplikace to napíše a nabídne **Zkusit znovu** a **Přihlásit jiný účet**: buď se na PS5
přepnout na 🔵 JMÉNO a vyvolat nové číslo, nebo se přihlásit tím účtem, pod kterým se číslo vyvolává (musí to být
🔵 druhý účet, ne 🟢 herní).

**Probouzení z klidového režimu** je volba v ⚙ Nastavení → Obecné („Probouzet PlayStation“, výchozí
vypnutá). Na PS5 k tomu: Nastavení → Systém → Úspora energie → Funkce dostupné v klidovém režimu →
„Zůstat připojen k internetu“ a „Povolit zapnutí systému PS5 ze sítě“.

**PlayLingo i v telefonu (jen Android, nepovinné).** Stejnou aplikaci jde nainstalovat i do
telefonu – tam nepřekládá, jen pomáhá s přihlášením. Na stránce z čtverečku zvolit „Otevřít
v aplikaci PlayLingo“, přihlásit se druhým účtem přímo v aplikaci a ta pošle účet do televize sama,
bez kopírování. Účet si zapamatuje (jen číslo a jméno, heslo ne), takže při příštím párování nebo
na další televizi stačí naskenovat čtvereček a klepnout na „Odeslat do televize“. Přihlášení u Sony
v pomocníkovi začíná vždy načisto (e-mail a heslo se píší pokaždé), ať se omylem nepoužije herní účet.
Když telefon sám čte pro televizi LG / Samsung, uložené účty nabídne krok 3 „Účet pro PlayLingo“ se jmény.

## 5. Hraní
1. Na stránce Domů zvolit „Spustit překlad“ a počkat na „Překlad běží“ (objeví se i malý náhled
   toho, co aplikace z PlayStationu vidí).
2. Přepnout televizi na HDMI s PlayStationem a hrát. Ve hře mít zapnuté anglické titulky.
3. Titulky si aplikace na obrazovce najde sama.
4. Každá postava má v pruhu jméno ve své barvě. Když hra nabídne volby odpovědi pod sebou
   (očíslované nebo zarovnané vlevo), ukáže je pruh jako seznam s odrážkami, každou přeloženou
   zvlášť. Menu hry a pokyny k ovládání se dál nepřekládají.

**Co se u hry překládá navíc (Titulky → Hra):** titulky vždy. U her, jejichž profil to zná (zatím GTA), má každý další
text vlastní přepínač: **Pokyny pod titulky** (krátký šedý řádek „Take your shot“ pod replikou), **Úkol pod titulky**
(řádek úkolu jako samostatný pruh, s volbami písma, polohy a pozadí) a u GTA VI **Překlad menu hry** (výchozí vypnuto).
Žádná jiná volba je nepřebíjí. Kdo měl do verze 0.97.57 „Rozsah čtení: jen titulky“, má je po aktualizaci vypnuté.

**GTA VI: pozadí titulků musí být zapnuté.** Bez něj se text ztrácí v obraze a PlayLingo nic nepřečte. Ve hře:
Settings → UI → Subtitles & Captions → Background – cokoli kromě Off. Připomíná to i řádek „Ve hře: pozadí titulků“
v Titulky → Hra a Nápověda → Hraní.

**Menu hry (zatím GTA VI, základ):** po otevření menu zleva (Mapa, Hra, Obchod, Nápověda, Fotostudio, Nastavení) nebo
Nastavení hry, ukáže pruh přeložené položky – vybraná je se šipkou ▸ – a u Nastavení i přeložený nadpis a popis vybrané volby.
Jednotlivé volby Nastavení se zatím nepřekládají. Funguje s profilem hry GTA VI a se zapnutým Titulky → Hra → Překlad menu hry.

**Seznam her:** jména, postavy a pojmy jednotlivých her se dají aktualizovat bez nové verze aplikace. V Titulky → Hra
je řádek „Seznam her · verze N“ – OK zjistí, jestli není novější, a stáhne ho. Samo (nejvýš jednou za 6 hodin) jen se
zapnutou volbou Nastavení → Experimentální → „Seznam her automaticky“. Zvolená hra se tím nikdy nemění.

Texty písní (rádio v autě, hudba ve scéně), které hra označuje notou ♪, se nepřekládají.

## 7. Problémy
- **Stránka z čtverečku se v telefonu neotevře** – telefon nejspíš není na stejné Wi-Fi jako televize (nebo je na mobilních datech). Připojit ho k domácí Wi-Fi a naskenovat čtvereček znovu. Když to nejde, zvolit v televizi „Stránka se neotevřela“: záložní čtvereček vede rovnou na přihlášení Sony a kód se pak opíše ručně (krok 5).
- **Adresa nebo kód z telefonu neplatí** – platí jen pár minut; přihlásit se v telefonu znovu a vložit novou adresu.
- **PlayLingo v telefonu nepustí přihlášení Sony** – zvolit v něm „Nejde to? Přihlásit se v prohlížeči“ a prázdnou stránku „redirect“ pak nasdílet do PlayLinga (Sdílet → PlayLingo).
- **Nejde spárovat** – číslo z PS5 platí jen pár minut, je potřeba vyžádat nové. Zkontrolovat adresu a účet.
- **Spárováno, ale po Spustit se nic nepřipojí** – řádek „PS5 · spárováno“ znamená jen, že spárování proběhlo; spojení vzniká až po Spustit. Když ho PS5 odmítne, Domů napíše proč – nejčastěji chybí **Povolit Remote Play** pod účtem pro PlayLingo (PS5 pak hlásí jen obecnou chybu, Domů radí právě tohle) (nepřijatý účet – na PS5 se přepnout na ten účet, ať se přihlásí do PSN, nebo spárovat znovu; Remote Play spadl – PS5 úplně vypnout a zapnout; PS5 nepřijímá spojení – zkontrolovat Povolit Remote Play a klidový režim). Řádky „zdroj: … video …“ dole na obrazovce ukazuje Diagnostický režim (⚙ Nastavení → Experimentální → Ladění a hlášení), vypnout jde tamtéž.
- **„PlayStation neodpovídá“** – je konzole zapnutá (ne vypnutá ze zásuvky) a ve stejné síti?
- **Ve hře svítí „Aplikace Remote Play je připojena“** – na PS5 zapnout Nastavení → Systém → Remote Play → Skrýt stav připojení Remote Play (pod herním i druhým uživatelem), viz bod 3.
- **Účet na PS5 má přihlašovací kód** – PlayStation ho při spojení odmítne a na stránce PlayStation se objeví políčko pro kód (4 číslice); tam se zadá.
- **Pruh se neukáže** – zkontrolovat Usnadnění (bod 3.5, nebo ⚙ Nastavení → Obecné → Povolení) a v Titulcích nastavit vzhled – pruh se při tom ukáže na zkoušku.
- **Vzhled titulků během hry (Android TV)**: v ⚙ Nastavení → Obecné zvolit „Tlačítko vzhledu titulků“ a stisknout tlačítko ovladače, které se ve hře nepoužívá (barevné, Info…). Při hře pak otevře nabídku vzhledu přímo přes obraz; šipky mění hodnoty, Zpět zavře. (V TV LG/Samsung je totéž pod OK při hře nebo v nabídce Vzhled titulků.)
- **Anglický titulek vyčuhuje nad pruh nebo po stranách** (hlavně dvouřádkový) – v Titulky → Pruh s překladem nechat polohu „Přes anglické titulky“ a zapnout „Zakrýt celou oblast titulků“: pruh pak kryje celé místo, kde se titulky naposledy ukazovaly, ne jen právě přečtený řádek (Android TV i TV LG/Samsung s aplikací v TV od 1.5.14).
- **Nic se nečte** – zapnout ⚙ Nastavení → Experimentální → Ladění a hlášení → Diagnostický režim; ukáže, co aplikace čte.
- **Na PS5 pořád naskakuje „ovladač odpojen / účet připojen“** – s druhým účtem se připojuje i jiné zařízení
  (třeba aplikace PS Remote Play) a PS5 pak spojení přehazuje. Domů to od 0.97 napíše. Druhý účet ať slouží jen PlayLingu.
- **Telefon jako čtečka: spojení vypadává, když telefon zhasne displej** – v ⚙ Nastavení → Obecné zapnout
  „Povolit běh na pozadí“ (jinak ho šetření baterie uspí; poprvé se na to aplikace zeptá sama při Spustit).
  Od 0.97 drží PlayLingo telefon při překladu vzhůru sám.
- **Spojení vypadává na Wi-Fi 2,4 GHz** – ve stejném pásmu vysílají ovladače konzole. Pomůže konzole na kabelu
  nebo zařízení s PlayLingem na 5 GHz. Když telefon sám přechází na 2,4 GHz, je to router (stejný název sítě
  pro obě pásma a slabší 5 GHz signál); pomůže samostatný název sítě pro 5 GHz.
- **Překlad běží, ale titulky nenaskakují** – stačí se podívat na stránku Domů na řádek pod stavem překladu. Ukazuje, jak daleko to došlo: jestli se aplikace s PlayStationem spojila a jestli z něj opravdu přichází obraz. Když hlásí „Spojeno s PlayStationem, ale … nepřišel žádný obraz“, aplikace spojení po chvíli sama obnoví; když to trvá dlouho, překlad zastavit, zkontrolovat Wi-Fi a spustit ho znovu.
- **Aplikace minule spadla** – po dalším spuštění to napíše na stránce Domů. Kdyby se to opakovalo, pomůže hlášení (další bod).
- **Hlášení chyby**: Instalace → Když něco nejde (nebo ⚙ Nastavení → Experimentální → Ladění a hlášení) → „Vytvořit hlášení chyby“ otevře přes celou obrazovku hlášení o stavu aplikace: verzi, televizi, stav spojení, poslední pád a poslední události. Stačí ho vyfotit (šipkami ▲▼ se posouvá, v telefonu prstem) a poslat. Hesla, klíče ani číslo účtu v něm nejsou. Delší verze se zároveň uloží do souboru, jehož cesta je na konci hlášení. Tlačítko **Kopírovat** ho dá do schránky, **Sdílet** ho pošle jako soubor (e-mailem, do chatu…). Na televizi nejjednodušší: tlačítko **Do telefonu** ukáže čtvereček – naskenovat ho telefonem (i iPhonem) na stejné Wi-Fi, stáhnout hlášení a poslat ho autorovi chatem nebo e-mailem. S televizí LG nebo Samsung je v hlášení i oddíl Televize – co se dělo v aplikaci v televizi.
- **Záznam pro vývojáře v domácí síti**: ⚙ Nastavení → záložka Experimentální → Ladění a hlášení → „Zpřístupnit záznam v domácí síti“ → ZAP. Pod řádkem se ukáže adresa (např. `http://192.168.1.23:8789/zaznam?k=ab3k7m`), přes kterou si vývojář na počítači ve stejné síti stáhne celé hlášení (bez hesel, klíčů a čísla účtu). Po 2 hodinách se to samo vypne; jde to vypnout i hned.
- **Záznam snímků (pro vývojáře)**: při běžícím překladu ⚙ Nastavení → Experimentální → Ladění a hlášení → „Záznam snímků“ → OK = Start. Aplikace ukládá snímky obrazovky hry (jen když se obraz změní, nejvýš asi jeden za sekundu) a k nim, co v tu chvíli četla. Ve hře udělat, co je potřeba ukázat (menu, úkol, dvouřádkové titulky…), a znovu OK = Stop. Každé Start…Stop je samostatná dávka (v řádku „Nahrává · dávka 3 · 42 snímků“). Se zapnutým záznamem v domácí síti jde Start/Stop i tlačítkem „● Snímky“ / „■ Snímky“ v notifikaci. Samo se zastaví po 10 minutách nebo se zastavením překladu; všechny dávky dohromady zaberou nejvýš 300 MB (nejstarší se mažou). Vývojář si je stáhne přes záznam v domácí síti.

## 7b. Xbox (Series X|S / One)
PlayLingo umí číst titulky i z Xboxu. Hra jde do televize dál kabelem HDMI; PlayLingo si z Xboxu
přes vzdálené hraní (Remote Play) bere jen kopii obrazu v nižší kvalitě. Nic neovládá a zvuk
nepřehrává – hraje se normálně ovladačem.

1. **Na Xboxu (jen poprvé):** Nastavení → Zařízení a připojení → Vzdálené funkce → zapnout
   „Povolit vzdálené funkce“. Režim napájení nastavit na „Spánek“.
2. **V PlayLingu:** vybrat **Xbox** v průvodci (krok Konzole), v ⚙ Nastavení → Konzole, nebo na
   stránce Domů OK na souhrnném řádku → Konzole. Položka menu **Konzole** pak ukazuje stránku Xbox.
3. Na stránce Xbox (menu Konzole) zvolit **Přihlásit účet Microsoft**. Na obrazovce se ukáže kód. Na telefonu
   nebo počítači otevřít **microsoft.com/link** (nebo načíst čtvereček telefonem), zadat kód
   a přihlásit se účtem, kterým se hraje na Xboxu. V telefonu stačí tlačítko „Otevřít stránku“.
4. Objeví se seznam Xboxů na účtu – vybrat ten, na kterém se hraje (jediný se vybere sám).
5. Spustit překlad jako obvykle a přepnout televizi na HDMI s Xboxem.

Když Xbox spí nebo je vypnutý, PlayLingo ho nebudí – počká, až se zapne, a pak se připojí samo.
Přihlášení si PlayLingo pamatuje (zašifrované v zařízení); odhlásit se jde na stránce Xbox.
Stav spojení na stránce Domů funguje stejně jako u PlayStationu.

## 8. Televize LG a Samsung
Televize LG a Samsung si obraz hry samy nepřečtou. PlayLingo v nich jen ukazuje hru s pruhem
a číst a překládat musí telefon nebo box s Androidem ve stejné domácí síti. Typ televize se
volí v průvodci (krok Televize) a mění v ⚙ Nastavení → Typ televize nebo OK na souhrnném řádku
na stránce Domů: Android TV (pruh ukazuje toto zařízení, jako dřív), LG (webOS) nebo Samsung
(Tizen). V telefonu jsou tlačítka „Použít telefon pro televizi LG“ a „… Samsung“. Souhrnný řádek
na Domů ukazuje, jestli je aplikace z televize připojená; dokud se nikdy nepřipojila, je na Domů
upozornění s tlačítkem „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). Nahrát (i aktualizovat) aplikaci v televizi jde
kdykoli i z ⚙ Nastavení. Kdo měl dřív zapnuté „Posílat titulky do televize LG“, má typ LG.

Pruh v televizi vypadá podle Titulků v PlayLingu (průhlednost, písmo, barva, poloha). Když je
v televizi starší PlayLingo, než nese aplikace v telefonu, upozorní na to Domů s tlačítkem k nahrání.

**Vzhled titulků přímo v televizi** (aplikace v televizi od 1.4.0): při hře stisknout na ovladači
**OK**. Otevře se malá nabídka: pozadí pruhu, velikost písma, poloha pruhu a originál. Šipky
▲▼ vybírají, ◀▶ mění, Zpět nabídku zavře. Změna se hned uloží v PlayLingu v telefonu, takže
telefon a televize mají vždycky stejné nastavení. Všechno ostatní se nastavuje jen v telefonu.

### Televize LG

**Nejjednodušší je pomocník v aplikaci** („Nahrát PlayLingo do televize LG“): ukáže postup krok
za krokem a kroky, které pozná sám, odškrtne. Televizi v síti najde sám (adresa se psát nemusí), pozná,
že je zapnutý vývojářský režim i Key Server, a na odkaz na účet LG ukáže čtvereček.

**Nahrání PlayLingo do televize LG z telefonu nebo boxu (bez počítače):**
1. Na webu webostv.developer.lge.com založit účet LG (zdarma).
2. V televizi otevřít obchod LG Content Store, nainstalovat aplikaci **Developer Mode**, přihlásit se
   v ní tím účtem a zapnout **Dev Mode Status**. Televize se restartuje.
3. Znovu otevřít Developer Mode a zapnout **Key Server**. Ukáže se adresa televize (IP) a **Passphrase**.
4. V PlayLingu zvolit „Nahrát PlayLingo do televize LG“ (krok Televize v průvodci, ⚙ Nastavení,
   upozornění na Domů, pomocník v telefonu nebo Instalace), opsat adresu a Passphrase a zvolit „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). PlayLingo se v televizi
   nainstaluje a spustí. Příště už Passphrase není potřeba – stejné tlačítko televizi
   aktualizuje. Starší verzi přes novější nikdy nenahraje.

   **V telefonu se nic neopisuje:** zvolit „Načíst z obrazovky televize kamerou“, povolit kameru
   a namířit telefon na obrazovku Developer Mode tak, aby byla vidět adresa i Passphrase. Jakmile je
   PlayLingo přečte dvakrát stejně, ukáže je; zvolit „Použít“, porovnat údaje s televizí a zvolit
   „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). Pokud upozorní na podobné znaky (0/O, 1/I), zkontrolovat hlavně je –
   špatná Passphrase nic nepokazí, televize ji jen odmítne. Kamera nic nefotí, neukládá ani
   neposílá. Na televizi a boxu (bez kamery) se tlačítko neukazuje.

Vývojářský režim po čase vyprší a PlayLingo pak z televize zmizí. Kolik dní zbývá, ukazuje
PlayLingo u tlačítka (pod 3 dny upozorní i na stránce Domů). Prodloužit ho jde jen v televizi: Developer Mode →
**Extend**. PlayLingo nic samo neprodlužuje.

Jiné cesty: z počítače nástrojem LG `ares` (postup ve `webos/CTI-ME.md`), nebo z obchodu LG,
až tam PlayLingo bude.

### Televize Samsung
Samsung pustí do televize vlastní aplikaci jen ve vývojářském režimu a jen podepsanou
certifikátem, který Samsung vystaví na vlastní účet Samsung (zdarma) právě pro danou televizi.
Podpis i nahrání zařídí zdarma aplikace **Apps2Samsung** v telefonu s Androidem – bez
počítače, asi 10 minut. Průvodce „Nahrát PlayLingo do TV Samsung“ (krok Televize v průvodci,
⚙ Nastavení, upozornění na Domů, pomocník v telefonu, Instalace) provede krok za krokem; kroky
se odškrtávají samy, kde to jde (jinak tlačítkem Hotovo):
1. **Vývojářský režim v TV:** Aplikace (Apps) → na ovladači 1 2 3 4 5 → **Developer mode** On →
   do **Host PC IP** adresu **telefonu** s Apps2Samsungem (průvodce v telefonu ji ukáže velkým
   písmem) → TV restartovat (podržet vypínač). Když průvodce najde TV Samsung v síti s vývojářským
   režimem, odškrtne krok sám.
2. **Apps2Samsung do telefonu** (tlačítko Stáhnout Apps2Samsung, na televizi čtvereček na stránku
   s odkazy). Je-li už v telefonu, průvodce to pozná.
3. **Přidat PlayLingo do Apps2Samsungu:** tlačítko otevře Apps2Samsung s předvyplněným zdrojem
   PlayLingo (na televizi čtvereček), stačí potvrdit. Ručně: ⋯ → Add a source →
   `https://github.com/adusa89/PlayLingo/releases/tag/latest`.
4. **Nainstalovat:** v Apps2Samsungu vybrat TV a u PlayLinga Install (napoprvé přihlášení účtem
   Samsung). Pak v TV spustit PlayLingo – průvodce ukáže „hotovo“, až se aplikace z televize
   opravdu připojí, a do televize pošle zkušební větu. Aktualizace: v Apps2Samsungu Update.

Bez telefonu s Androidem: rozbalovací řádek „Bez telefonu s Androidem: počítač“ uloží soubor
s aplikací (.wgt, nepodepsaný) a vede na Tizen Studio v počítači. Odkazy jsou na stránce
https://adusa89.github.io/PlayLingo/samsung.html.

Podrobnosti a důvody jsou v `tizen/CTI-ME.md`.

## 9. Lepší překlad: DeepL (doporučeno)
Offline překlad je zdarma a bez internetu, ale hodně doslovný. **DeepL** překládá přirozeně, rozumí
slangu i nadávkám a pro hráče je zdarma – stačí vlastní bezplatný účet DeepL API. Nastaví se v průvodci
(krok **Lepší překlad (DeepL)**) nebo kdykoli v **Titulky → Kvalita překladu** (rozbalovací skupina hned
pod jazykem překladu; vede tam i řádek s jazykem na Domů). Tamtéž je zapnutí a vypnutí, ověření klíče,
kolik znaků zbývá a odebrání klíče.

Jak to funguje: PlayLingo počká na DeepL (nejvýš 2,5 s) a ukáže jeho překlad – i v televizi LG / Samsung.
Když DeepL nejde (bez internetu, vyčerpaný limit, špatný klíč), ukáže se offline překlad a důvod je vidět
v Titulkách → Kvalita překladu.

### Nastavení klíče – nic se nepíše ovladačem
Klíč je dlouhý (asi 40 znaků), proto se do televize **nepíše** – zkopíruje a vloží se jen v telefonu.
Televize ukáže **jeden čtvereček**; naskenuje se telefonem (Android i iPhone, stejná Wi‑Fi). Otevře se
stránka „PlayLingo · DeepL“, která provede třemi kroky:

1. **Účet DeepL API zdarma** (jen poprvé) – tlačítko otevře ceník DeepL na záložce **API**
   (https://www.deepl.com/pro#api). U plánu **Developer** klepnout na **Vytvořte si účet zdarma**.
   - **Bez účtu DeepL:** zadat e-mail a heslo, zvolit **Continue** a **potvrdit e-mail** odkazem, který
     DeepL pošle.
   - **S účtem DeepL, i jen na překládání (Translator):** nejdřív se na deepl.com **přihlásit**, pak otevřít
     ceník → API a klepnout na totéž tlačítko u Developeru – plán se **přidá k tomuto účtu**, nový e-mail
     není potřeba (ověřeno 21. 11. 2026).
   - **Pozor:** odkazy „Získejte API klíč zdarma“ / „API zdarma“ v menu DeepL přihlášeného posílají zpátky
     do Translatoru. Platí jen tlačítko u plánu Developer v ceníku.
   - Plán je zdarma: **1 000 000 znaků celkem**, každý měsíc se neobnovuje. Platební kartu DeepL nechce.
2. **Stránka s klíčem** – https://www.deepl.com/your-account/keys (záložka **API Keys & Limits**). Celý klíč
   se tam nezobrazuje: zkopíruje se **ikonou kopírování** vedle klíče. Když tam klíč není, zvolit **Create key**.
3. **Vložit klíč** – vrátit se na stránku „PlayLingo · DeepL“, **podržet prst v políčku → Vložit** a zvolit
   **Odeslat do TV**. Televize mezitím
   ukazuje „Čekám na klíč z telefonu…“.
4. Televize klíč ověří, uloží a DeepL zapne: „✔ DeepL je nastavený“.

Když telefon stránku neotevře (mobilní data, jiná Wi‑Fi), připojit ho ke stejné Wi‑Fi a naskenovat znovu.
Ruční psaní ovladačem je jen záloha: **Napsat klíč ručně**. **Telefon jako čtečka** čtverečky nemá – má
tlačítka **Otevřít DeepL**, **Otevřít stránku s klíčem** a políčko, kam se klíč vloží.

### Dobré vědět
- **Mám DeepL, ale klíč nevidím / všechno mě přesměruje do Translatoru:** účet ještě nemá plán API.
  Přihlásit se, otevřít https://www.deepl.com/pro#api a u plánu **Developer** klepnout na **Vytvořte si účet
  zdarma** – plán se přidá k účtu. Pak stránka s klíči → **Create key**.
- **Registrace visí na „Security check“:** vypnout VPN nebo blokování reklam (na iPhonu i Soukromý přenos
  iCloud), případně zkusit jiný prohlížeč nebo mobilní data.
- **Limit:** plán Developer má 1 000 000 znaků celkem (aplikace po ověření ukáže, kolik zbývá).
  Opakované věty se znovu neposílají. Placený klíč (API Pro / Growth) funguje taky – aplikace pozná druh klíče sama.
- **Soukromí:** se zapnutým DeepL se **rozpoznaný text titulků posílá přes internet do DeepL**. Klíč patří
  jen hráči, zůstává zašifrovaný v zařízení s PlayLingem a nikam jinam se neposílá (ani do hlášení).
- Klíč jde kdykoli odebrat tlačítkem **Odebrat klíč** a DeepL vypnout v Titulkách → Kvalita překladu.

## Licence a zdrojový kód
Spojení s PlayStationem používá otevřenou knihovnu chiaki-ng
(https://github.com/streetpea/chiaki-ng, licence AGPL-3.0). Zdrojový kód aplikace včetně
úprav je v tomto repozitáři. Aplikace není produktem společnosti Sony.
Nahrání do televize LG používá knihovnu JSch (https://github.com/mwiede/jsch, licence BSD) a postup
podle projektu PlayCaption (licence AGPL-3.0).
Spojení s Xboxem používá knihovnu WebRTC (sestavení stream-webrtc-android, licence BSD/Apache 2.0)
a postup podle projektů PlayCaption (AGPL-3.0), xbox-xcloud-player (MIT) a XStreaming (AGPL-3.0).
Aplikace není produktem společnosti Microsoft.
