# Test Překlad – stručný návod

Aplikace pro Android TV, boxy, Chromecast a projektory. Sama se po domácí síti připojí
k PlayStationu 5, přečte anglické titulky hry, přeloží je (offline, zdarma) a ukáže je
v černém pruhu přes obraz. Hra jde do televize dál kabelem HDMI.

## 1. APK
Stáhni `TestPreklad.apk`:
https://github.com/adusa89/Test-Preklad/releases/download/latest/TestPreklad.apk

## 2. Instalace
Nahraj APK do televize (USB flash + správce souborů, nebo aplikace Downloader) a nainstaluj.
Když instalaci zablokuje Play Protect, vypni v Google Play → Play Protect → nastavení
„Kontrolovat aplikace pomocí Play Protect“ a nainstaluj znovu.

## 3. První nastavení (průvodce tě provede)
1. Vyber jazyk překladu.
2. Zapni aplikaci v nastavení televize: Usnadnění → Test Překlad → Zapnout.
3. Počkej na stažení jazykového modelu (jednou, asi 30 MB).
4. Spáruj PlayStation – viz další bod.

## 4. Spárování PlayStationu (jen jednou)
Televize a PS5 musí být ve stejné domácí síti.
1. Na PS5: Nastavení → Systém → Remote Play → zapni „Povolit Remote Play“.
2. Tamtéž zvol „Propojit zařízení“ – ukáže se osmimístné číslo (platí pár minut).
3. V aplikaci na stránce PlayStation zvol „Najít PlayStation v síti“ (nebo zadej adresu ručně:
   PS5 → Nastavení → Síť → Stav připojení → Adresa IPv4).
4. **Přihlas se k účtu PlayStation přes telefon** (jednou, asi dvě minuty):
   - televize ukáže QR kód – namiř na něj foťák telefonu a otevři odkaz,
   - v telefonu se přihlas e-mailem a heslem ke svému účtu PlayStation,
   - zůstane skoro prázdná stránka s nápisem „redirect“; klepni nahoře na její adresu,
     na konci je „code=“ a za ním krátký kód,
   - ten kód opiš do televize a zvol „Ověřit kód“.
   Přihlášení jde zkusit i přímo v televizi („Zkusit přihlášení přímo v televizi“), ale ochrana
   Sony ho tam často odmítne hláškou „Can't connect to the server“. Pak použij telefon.
5. Na PS5 zvol „Propojit zařízení“, osmimístné číslo opiš do televize a zvol Spárovat.

Telefon je potřeba jen k tomuto jednomu přihlášení. K hraní už ne.

## 5. Hraní
1. Na stránce Domů zvol „Spustit překlad“ a počkej na „Překlad běží“ (objeví se i malý náhled
   toho, co aplikace z PlayStationu vidí).
2. Přepni televizi na HDMI s PlayStationem a hraj. Ve hře měj zapnuté anglické titulky.
3. Titulky si aplikace na obrazovce najde sama.

## 6. Zkouška bez PlayStationu
Na stránce Domů zvol „Zkouška bez PlayStationu“. Ukáže se ukázková hra s anglickými titulky
a přes ně pruh s překladem. Tím si ověříš, že pruh a překlad v televizi fungují.

## 7. Problémy
- **Přihlášení v televizi hlásí „Can't connect to the server“ nebo „Something went wrong“** – odmítla tě ochrana Sony; přihlas se přes telefon (bod 4.4).
- **Kód z telefonu neplatí** – platí jen krátce; přihlas se v telefonu znovu a opiš nový.
- **Nejde spárovat** – číslo z PS5 platí jen pár minut, vyžádej si nové. Zkontroluj adresu a účet.
- **„PlayStation neodpovídá“** – je konzole zapnutá (ne vypnutá ze zásuvky) a ve stejné síti?
- **Účet na PS5 má přihlašovací kód** – zadej ho na stránce PlayStation (4 číslice).
- **Pruh se neukáže** – zkontroluj Usnadnění (bod 3.2) a vyzkoušej bod 6.
- **Nic se nečte** – zapni Nastavení → Diagnostický režim; uvidíš, co aplikace čte.
- Nápověda → „Vytvořit hlášení chyby“ uloží soubor s podrobnostmi pro vývojáře.

## Licence a zdrojový kód
Spojení s PlayStationem používá otevřenou knihovnu chiaki-ng
(https://github.com/streetpea/chiaki-ng, licence AGPL-3.0). Zdrojový kód aplikace včetně
úprav je v tomto repozitáři. Aplikace není produktem společnosti Sony.
