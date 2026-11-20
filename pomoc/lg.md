# Televize LG – vývojářský režim (jen poprvé)

PlayLingo se do televize LG nahraje z telefonu nebo boxu, ale televize k tomu potřebuje zapnutý
**vývojářský režim** (zdarma). V aplikaci PlayLingo („Nahrát PlayLingo do televize LG“) uvidíš
stejné kroky a ty, které pozná sama, odškrtne.

<a id="ucet"></a>
## 1. Účet LG pro vývojáře (zdarma)

👉 **[Otevřít stránku LG pro vývojáře](https://webostv.developer.lge.com/)** → vpravo nahoře **Sign In**
→ **Create Account** (založení účtu LG). Stačí e-mail a heslo; účet je zdarma.

Když odkaz nefunguje:
- [Návod LG k aplikaci Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Komunitní návod webOS Brew](https://www.webosbrew.org/devmode/)
- nebo hledej: **„LG webOS TV developer account sign up“**

E-mail a heslo si poznamenej – v televizi je budeš psát ovladačem.

<a id="aplikace"></a>
## 2. Aplikace Developer Mode v televizi

1. V televizi otevři obchod **LG Content Store** (nebo **Apps**).
2. Vyhledej **Developer Mode** a nainstaluj ji.
3. Spusť ji, přihlas se účtem z kroku 1.
4. Zapni **Dev Mode Status**. Televize se restartuje.

<a id="key-server"></a>
## 3. Key Server

Znovu otevři aplikaci Developer Mode a zapni **Key Server**. Pod ním uvidíš adresu televize (IP)
a **Passphrase** (6 znaků). V telefonu je PlayLingo přečte kamerou, nic se neopisuje.

## 4. Nahrát PlayLingo

V PlayLingu zvol „Nahrát do televize“. Hotovo – příště už Passphrase potřeba není.

## Dobré vědět
- Vývojářský režim po čase vyprší (PlayLingo ukáže, kolik zbývá). Prodloužíš ho v televizi:
  Developer Mode → **Extend**.
- Jedním účtem LG jde mít vývojářský režim jen na jedné televizi najednou.

---
*English: create a free LG developer account at [webostv.developer.lge.com](https://webostv.developer.lge.com/)
(Sign In → Create Account), install the **Developer Mode** app from the LG Content Store, sign in,
turn on **Dev Mode Status** (the TV restarts), then turn on **Key Server**. PlayLingo on your phone
reads the address and Passphrase with the camera and installs itself. Fallbacks:
[LG guide](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app),
[webOS Brew guide](https://www.webosbrew.org/devmode/).*
