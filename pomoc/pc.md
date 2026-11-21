# PlayLingo pro počítač s Windows (zkušební)

Počítač může dělat čtečku pro **TV LG nebo Samsung** místo telefonu s Androidem – hodí se hlavně
s iPhonem. Hra jde z PS5 kabelem do TV a hraje se 🟢 herním účtem jako vždy. Počítač se k PS5 připojí
přes Remote Play 🔵 účtem pro PlayLingo, čte obraz, překládá a hotové věty posílá do PlayLinga v TV.

Potřeba: **Windows 10 nebo 11 (64 bit)** ve stejné domácí síti jako PS5 a TV, asi 1,5 GB místa.

<a id="stazeni"></a>
## 1. Stáhnout a spustit

1. **[Stáhnout PlayLingo-PC.zip](https://github.com/adusa89/PlayLingo/releases/download/pc/PlayLingo-PC.zip)**
   (asi 250 MB).
2. ZIP rozbalit (pravé tlačítko → **Extrahovat vše**) třeba do Dokumentů a ve složce spustit **PlayLingo.exe**.
   Nic se neinstaluje; smazáním složky PlayLingo zmizí.
3. Windows může ukázat „Systém Windows ochránil váš počítač“ (program není podepsaný) –
   **Další informace → Přesto spustit**. Na dotaz brány firewall **povolit** (TV a telefon se k počítači připojují
   v domácí síti).

<a id="priprava"></a>
## 2. Příprava (jen poprvé)

Na stránce **Domů**:

- **Překlad · chybí → Připravit** – stáhne překladový model pro zvolený jazyk (asi minuta).
- **chiaki-ng · chybí → Stáhnout** – program pro Remote Play, přes který se počítač k PS5 připojí (asi 120 MB).

<a id="ctecka"></a>
## 3. Čtečka pro TV

1. **⚙ Nastavení → Použití → Čtečka pro TV.**
2. **PS5 · nespárováno → Spárovat** – 🔵 účet pro PlayLingo (druhý uživatel na PS5, viz [návod k PS5](ps5.md#ucet)):
   přihlásit se telefonem (i iPhonem) přes čtvereček, pak na PS5 pod 🔵 uživatelem **Nastavení → Systém →
   Remote Play → Propojit zařízení** a osmimístné číslo opsat do PlayLinga. Potom na PS5 zpět na 🟢 herní účet.
3. PlayLingo do TV: [LG](lg.md) / [Samsung](samsung.md) – nahrání do TV zatím jde jen z Androidu
   (LG i z počítače nástroji LG, Samsung přes [Apps2Samsung pro počítač](samsung.md#pocitac)).
4. **Spustit.** Pak PS5 zapnout ovladačem – počítač se připojí sám, PS5 sám nebudí. Okno chiaki-ng může zůstat
   za jinými okny, jen ho **neminimalizovat**. Zvuk z počítače je vypnutý.

Hraní přímo na počítači (bez TV): **⚙ → Použití → Hraji na PC** a tlačítko **Hrát**.

---
*English: PlayLingo for Windows 10/11 (64-bit), experimental. It can be the reader for an LG or Samsung TV instead of
an Android phone – handy with an iPhone. Download [PlayLingo-PC.zip](https://github.com/adusa89/PlayLingo/releases/download/pc/PlayLingo-PC.zip),
extract it and run **PlayLingo.exe** (nothing is installed; if SmartScreen warns: More info → Run anyway). On Home
press **Prepare** (translation model) and **Download** (chiaki-ng). Then ⚙ → Usage → **TV reader**, pair the PS5 with
the 🔵 PlayLingo account and press Start; turn the PS5 on with the controller. Keep the chiaki-ng window open (it may be
behind other windows, but not minimised).*
