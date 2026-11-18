# PlayLingo pro televize LG (webOS) – zkouška

Zatím to není překladač, jen **zkušební aplikace**. Na skutečné televizi zjistí, na čem se dá
verze pro LG postavit:

1. jestli aplikace umí ukázat hru ze vstupu HDMI a nakreslit přes ni pruh s titulkem,
2. jestli si aplikace umí obraz sama přečíst (pak by se nemusela připojovat k PlayStationu po síti),
3. jakou verzi systému a prohlížeče televize má.

Je psaná tak, aby běžela i na televizích z let 2016–2017 (webOS 3.x, např. OLED55B7).

## Jak ji dostat do televize

1. V televizi nainstalujte z LG Content Store aplikaci **Developer Mode**. Přihlaste se v ní
   účtem z webu webostv.developer.lge.com (je zdarma), zapněte **Dev Mode Status** a **Key Server**.
   Televize se restartuje.
2. Na počítači ve stejné síti nainstalujte program **webOS Dev Manager**
   (github.com/webosbrew/dev-manager-desktop), přidejte televizi (adresu a heslo ukazuje aplikace
   Developer Mode) a přes **Install** vyberte soubor `PlayLingo-zkouska.ipk`.
3. Na televizi spusťte **PlayLingo zkouška**, vyberte vstup, do kterého je zapojený PlayStation,
   a výslednou obrazovku vyfoťte.

Vývojářský režim se po čase sám vypne a aplikaci odstraní; v aplikaci Developer Mode jde prodloužit.

## Co zkouška zjistila (OLED55B7, webOS 3.9)

Hru ze vstupu HDMI s pruhem přes obraz aplikace ukázat umí. Obraz si ale přečíst neumí
(vidí jen černou) a snímek obrazovky jí systém nedovolí. Číst a překládat proto musí jiné
zařízení v domácí síti a televizi posílat hotové věty.

## Titulky po síti

Po výběru vstupu si aplikace v domácí síti sama najde zařízení, které titulky pošle (zkouší
všechny adresy své sítě na portu 8787), a přepne se: na obrazovce zůstane jen hra a pruh.
Nalezenou adresu si pamatuje. Tlačítko Zpět vrátí úvodní obrazovku.

Titulky posílá:

- **PlayLingo na Androidu** (od 0.62) se zapnutou volbou Nastavení → „Posílat titulky do
  televize LG“. V telefonu je nejdřív potřeba v pomocníkovi klepnout na „Tohle je televize nebo
  box“, aby se otevřela celá aplikace. Čte a překládá jako vždy, jen věty jdou i do televize.
- **Zkušební posílač v počítači:** `node webos/posilac.js` – dokola posílá zkušební věty a měří,
  za jak dlouho je televize vykreslí. Nic nečte ani nepřekládá.

Adresu jde pro zkoušky určit i napevno parametry spuštění, např. nástrojem LG:
`ares-launch -d lgtv cz.playlingo.zkouska -p "{'titulky':'ws://ADRESA:8787','hdmi':2}"`

## Sestavení

`python3 webos/sestavit.py` vytvoří `webos/PlayLingo-zkouska.ipk` (nic se neinstaluje).
Zdroj aplikace je ve složce `webos/app`.
