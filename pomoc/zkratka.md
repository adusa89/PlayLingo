# iPhone: zkratka „PlayLingo“ – bez kopírování adresy

<p id="stav" style="font-weight:600"></p>

Při přihlášení účtem 🔵 **pro PlayLingo** skončí Sony na skoro prázdné stránce „redirect“. Její adresu
bys jinak musel zkopírovat a vložit. Se zkratkou stačí na té stránce klepnout na **Sdílet → PlayLingo**
a adresa dojde do TV sama.

<a id="pridat"></a>
## 1. Přidej zkratku (jen jednou)

<p id="icloud"></p>

Když tlačítko výš chybí, vytvoř si zkratku sám (asi minuta):

1. Otevři aplikaci **Zkratky** → **+** (nová zkratka) a pojmenuj ji **PlayLingo**.
2. Klepni na **ⓘ** (podrobnosti) → zapni **Zobrazit v listu sdílení** → přijímat jen **URL adresy**.
3. Přidej akci **Zakódovat URL** (URL Encode) – vstup: **Vstup zkratky**.
4. Přidej akci **Otevřít URL** a jako adresu napiš
   `https://adusa89.github.io/PlayLingo/zkratka.html?u=` a za ni vlož proměnnou **Zakódovaný text**.
5. Hotovo – ulož.

<a id="tv"></a>
## 2. Propoj zkratku se svou TV (jen jednou)

Na stránce **PlayLingo · nastavení TV** (čtvereček v TV, Android TV) klepni na **iPhone bez kopírování:
zkratka PlayLingo**. Otevře se tahle stránka a nahoře se ukáže „✔ TV je uložená“.

## 3. Při přihlášení

1. Na stránce PlayLingo klepni na **Přihlásit se u PlayStation** a přihlas se 🔵 účtem pro PlayLingo
   (ne 🟢 herním).
2. Na skoro prázdné stránce „redirect“ klepni na **Sdílet** (čtvereček se šipkou) → **PlayLingo**.
3. Otevře se stránka z TV a účet se ověří sám.

<script>
(function () {
  var KEY = 'playlingo_tv';
  var stav = document.getElementById('stav');
  function q(name) {
    var m = new RegExp('[?&]' + name + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  function domaci(url) {
    // jen stránka z TV v domácí síti (http://192.168.x.x:port/p/klic) – nikam jinam adresu nepošleme
    return /^http:\/\/(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)[0-9.]+:\d+\/p\/[A-Za-z0-9]+$/.test(url || '');
  }
  var store = null;
  try { store = window.localStorage; } catch (e) {}
  var tv = q('tv');
  if (tv && domaci(tv)) {
    try { store.setItem(KEY, tv); } catch (e) {}
    stav.textContent = '✔ TV je uložená – zkratka teď pošle adresu do ní. / TV saved.';
  }
  var u = q('u');
  if (u) {
    var saved = null;
    try { saved = store.getItem(KEY); } catch (e) {}
    if (saved && domaci(saved) && u.indexOf('code=') >= 0) {
      stav.textContent = '⏳ Posílám do TV… / Sending to the TV…';
      location.replace(saved + '?u=' + encodeURIComponent(u));
    } else if (!saved) {
      stav.textContent = '○ Zkratka ještě nezná tvou TV – udělej krok 2. / Do step 2 first.';
    } else {
      stav.textContent = '○ To není adresa stránky „redirect“. / Not a “redirect” address.';
    }
  }
  // odkaz na hotovou zkratku z iCloudu – doplní ho majitel, až ji vytvoří (viz CLAUDE.md, Přihlášení přes telefon)
  var ICLOUD = '';
  if (ICLOUD) document.getElementById('icloud').innerHTML = '<a href="' + ICLOUD + '"><b>➕ Přidat zkratku PlayLingo</b></a>';
})();
</script>

---
*English: On iPhone the Sony sign-in ends on an almost empty “redirect” page. With the **PlayLingo** shortcut you
just tap **Share → PlayLingo** there and the address goes to the TV by itself. Add the shortcut once (Shortcuts →
**+** → Show in Share Sheet, URLs → action **URL Encode** → action **Open URL**
`https://adusa89.github.io/PlayLingo/zkratka.html?u=` + encoded text), then link it to your TV once from the
“PlayLingo · TV settings” page (button “iPhone without copying”).*
