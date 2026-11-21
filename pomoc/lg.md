# Televize LG – vývojářský režim (jen poprvé)

PlayLingo se do televize LG nahraje z telefonu nebo boxu, ale televize k tomu potřebuje zapnutý
**vývojářský režim** (zdarma). V aplikaci PlayLingo („Nahrát PlayLingo do televize LG“) jsou
stejné kroky a ty, které pozná sama, odškrtne.

<a id="ucet"></a>
## 1. Účet LG pro vývojáře (zdarma)

👉 **[Otevřít stránku LG pro vývojáře](https://webostv.developer.lge.com/)** → vpravo nahoře **Sign In**
→ **Create Account** (založení účtu LG). Stačí e-mail a heslo; účet je zdarma.

Když odkaz nefunguje:
- [Návod LG k aplikaci Developer Mode](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app)
- [Komunitní návod webOS Brew](https://www.webosbrew.org/devmode/)
- nebo hledat: **„LG webOS TV developer account sign up“**

E-mail a heslo je dobré si poznamenat – v televizi se píšou ovladačem.

<a id="aplikace"></a>
## 2. Aplikace Developer Mode v televizi

1. V televizi otevřít obchod **LG Content Store** (nebo **Apps**).
2. Vyhledat **Developer Mode** a nainstalovat ji.
3. Spustit ji, přihlásit se účtem z kroku 1.
4. Zapnout **Dev Mode Status**. Televize se restartuje.

<a id="key-server"></a>
## 3. Key Server

Znovu otevřít aplikaci Developer Mode a zapnout **Key Server**. Pod ním se ukáže adresa televize (IP)
a **Passphrase** (6 znaků). V telefonu je PlayLingo přečte kamerou, nic se neopisuje.

## 4. Nahrát PlayLingo

V PlayLingu zvolit „Nahrát do TV“ (u starší verze „Aktualizovat v TV“). Hotovo – příště už Passphrase potřeba není.

## Dobré vědět
- Vývojářský režim po čase vyprší (PlayLingo ukáže, kolik zbývá). Prodlužuje se v televizi:
  Developer Mode → **Extend**.
- Jedním účtem LG jde mít vývojářský režim jen na jedné televizi najednou.

---
*English: create a free LG developer account at [webostv.developer.lge.com](https://webostv.developer.lge.com/)
(Sign In → Create Account), install the **Developer Mode** app from the LG Content Store, sign in,
turn on **Dev Mode Status** (the TV restarts), then turn on **Key Server**. PlayLingo on your phone
reads the address and Passphrase with the camera and installs itself. Fallbacks:
[LG guide](https://webostv.developer.lge.com/develop/getting-started/developer-mode-app),
[webOS Brew guide](https://www.webosbrew.org/devmode/).*
