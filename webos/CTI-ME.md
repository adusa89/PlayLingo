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

## Sestavení

`python3 webos/sestavit.py` vytvoří `webos/PlayLingo-zkouska.ipk` (nic se neinstaluje).
Zdroj aplikace je ve složce `webos/app`.
