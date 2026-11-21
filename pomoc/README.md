# Začít s PlayLingem

PlayLingo čte anglické titulky ze hry na PlayStationu 5 (nebo Xboxu), překládá je a ukazuje v pruhu přes obraz.
Odpověz na dvě otázky – ukážou se jen kroky, které se tě týkají.

<style>
.volby{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 18px}
.volby button{font:600 16px system-ui,sans-serif;padding:10px 14px;border-radius:10px;border:2px solid #d0d7de;background:#f6f8fa;cursor:pointer}
.volby button.on{border-color:#f2c14e;background:#fff5d6}
.cesta{border-left:4px solid #f2c14e;padding:2px 14px;margin:10px 0}
</style>

**1. Jakou máš TV?**
<div class="volby" id="tv">
<button data-v="atv">Android TV / Google TV<br><small>Sony, Philips, TCL, Chromecast, Shield…</small></button>
<button data-v="lg">LG (webOS)</button>
<button data-v="samsung">Samsung (Tizen)</button>
</div>

**2. Na čem chceš nastavovat?**
<div class="volby" id="na">
<button data-v="pc">💻 Počítač</button>
<button data-v="android">📱 Telefon s Androidem</button>
<button data-v="iphone">📱 iPhone</button>
<button data-v="tv">📺 Jen ovladač TV</button>
</div>

<p id="vyber" style="color:#57606a">Vyber obojí – nebo si přečti všechny cesty níž.</p>

<div class="cesta" data-tv="atv" data-na="pc" markdown="1">

### Android TV + 💻 počítač
1. **PlayLingo do TV:** v TV aplikace **Downloader** a adresa `adusa89.github.io/PlayLingo/tv` –
   [návod krok za krokem](android-tv.md#downloader). (Umíš-li `adb`: `adb connect <adresa TV>` a
   `adb install PlayLingo.apk` – [soubor](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk).)
2. **Spusť PlayLingo v TV** a v prvním kroku zvol **Nastavit telefonem nebo počítačem**.
3. **V počítači** (stejná síť jako TV) otevři adresu, kterou TV ukáže pod čtverečkem (💻 `http://192.168…`).
   Na stránce **PlayLingo · nastavení TV**: přihlášení 🔵 účtem pro PlayLingo (adresu stránky „redirect“
   zkopíruješ Ctrl+L, Ctrl+C), osmimístné číslo z PS5 a klíč DeepL. TV se spáruje sama.
</div>

<div class="cesta" data-tv="atv" data-na="android" markdown="1">

### Android TV + 📱 Android
1. **PlayLingo do telefonu:** [stáhnout PlayLingo.apk](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk)
   a nainstalovat.
2. **PlayLingo do TV z telefonu:** v PlayLingu v telefonu **Nahrát PlayLingo do Android TV** – v TV jednou zapnout
   ladění přes síť a potvrdit „Povolit ladění?“ ([návod](android-tv.md#telefon)). Nebo bez telefonu přes
   [Downloader](android-tv.md#downloader).
3. **Spusť PlayLingo v TV** → **Nastavit telefonem nebo počítačem** → naskenuj čtvereček telefonem a zvol
   **V aplikaci PlayLingo** (nic se nekopíruje, číslo z PS5 přečte kamerou, nastavíš i vzhled titulků),
   nebo **V prohlížeči**.
</div>

<div class="cesta" data-tv="atv" data-na="iphone" markdown="1">

### Android TV + 📱 iPhone
1. **PlayLingo do TV:** aplikace **Downloader** v TV a adresa `adusa89.github.io/PlayLingo/tv` –
   [návod](android-tv.md#downloader).
2. **Spusť PlayLingo v TV** → **Nastavit telefonem nebo počítačem** → naskenuj čtvereček iPhonem
   (stejná Wi‑Fi). V Safari se otevře **PlayLingo · nastavení TV**: přihlášení 🔵 účtem pro PlayLingo,
   číslo z PS5 (podrž prst v políčku → **Skenovat text**) a klíč DeepL.
3. Bez kopírování adresy „redirect“: [zkratka PlayLingo pro iPhone](zkratka.md).
</div>

<div class="cesta" data-tv="atv" data-na="tv" markdown="1">

### Android TV + 📺 jen ovladač
1. **PlayLingo do TV:** aplikace **Downloader** a adresa `adusa89.github.io/PlayLingo/tv` – [návod](android-tv.md#downloader).
2. **Spusť PlayLingo v TV** → **Nastavit přímo na TV** – průvodce tě provede ovladačem.
3. Jen na **přihlášení účtem PlayStation** budeš jednou potřebovat telefon nebo počítač (Sony přihlášení v TV
   nepustí) – TV ukáže čtvereček nebo adresu.
</div>

<div class="cesta" data-tv="lg" data-na="android" markdown="1">

### LG + 📱 Android
TV LG si obraz hry sama nepřečte – čte a překládá **PlayLingo v telefonu** a do TV posílá hotové věty.
1. **PlayLingo do telefonu:** [stáhnout PlayLingo.apk](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk).
2. V telefonu zvol **Použít telefon pro TV LG** – průvodce: vývojářský režim LG ([návod](lg.md)), **Nahrát do TV**,
   spárování PS5.
3. Při hraní: v TV spusť PlayLingo, v telefonu **Spustit**.
</div>

<div class="cesta" data-tv="samsung" data-na="android" markdown="1">

### Samsung + 📱 Android
TV Samsung si obraz hry sama nepřečte – čte a překládá **PlayLingo v telefonu** a do TV posílá hotové věty.
1. **PlayLingo do telefonu:** [stáhnout PlayLingo.apk](https://github.com/adusa89/PlayLingo/releases/download/latest/PlayLingo.apk).
2. V telefonu zvol **Použít telefon pro TV Samsung** – průvodce nahraje PlayLingo do TV přes **Apps2Samsung**
   ([návod](samsung.md)) a spáruje PS5.
3. Při hraní: v TV spusť PlayLingo, v telefonu **Spustit**.
</div>

<div class="cesta" data-tv="lg samsung" data-na="pc iphone tv" markdown="1">

### LG / Samsung bez telefonu s Androidem
TV LG a Samsung si obraz hry samy nepřečtou – číst a překládat musí **telefon s Androidem** nebo box s Android TV
(připojený k téže TV). iPhone ani počítač to zatím neumí. Stačí i starší telefon s Androidem 8 nebo novějším –
pak pokračuj cestou „📱 Android“ výš. (TV Samsung jde z počítače nahrát i přes [Apps2Samsung pro počítač](samsung.md#pocitac),
číst ale musí Android.)
</div>

<script>
(function () {
  var cesty = document.querySelectorAll('.cesta');
  var vyber = { tv: null, na: null };
  function ukaz() {
    var hotovo = vyber.tv && vyber.na;
    for (var i = 0; i < cesty.length; i++) {
      var c = cesty[i];
      var ok = !hotovo || ((' ' + c.getAttribute('data-tv') + ' ').indexOf(' ' + vyber.tv + ' ') >= 0 &&
        (' ' + c.getAttribute('data-na') + ' ').indexOf(' ' + vyber.na + ' ') >= 0);
      c.style.display = ok ? '' : 'none';
    }
    document.getElementById('vyber').style.display = hotovo ? 'none' : '';
  }
  ['tv', 'na'].forEach(function (id) {
    var tl = document.querySelectorAll('#' + id + ' button');
    for (var i = 0; i < tl.length; i++) tl[i].addEventListener('click', function () {
      for (var j = 0; j < tl.length; j++) tl[j].className = '';
      this.className = 'on';
      vyber[id] = this.getAttribute('data-v');
      ukaz();
    });
  });
  ukaz();
})();
</script>

## Všechny návody

- [PlayStation 5 – 🔵 účet pro PlayLingo a nastavení](ps5.md)
- [Android TV / Google TV – první instalace PlayLinga](android-tv.md)
- [Televize LG – vývojářský režim](lg.md)
- [Televize Samsung](samsung.md)
- [Telefon (Android i iPhone)](telefon.md) · [iPhone: zkratka PlayLingo](zkratka.md)
- [Xbox](xbox.md)
- [DeepL – lepší překlad](deepl.md)

Na tyhle stránky vedou i čtverečky (QR kódy) z aplikace. Když LG, Sony nebo Samsung své stránky přesunou,
opraví se odkaz tady a aplikace se měnit nemusí.

---
*English – Getting started: pick your TV and the device you set up with. **Android TV**: get PlayLingo onto the TV
(Downloader app with `adusa89.github.io/PlayLingo/tv`, or from an Android phone with PlayLingo → “Install PlayLingo
on Android TV”), start it and choose **Set up with a phone or computer** – a page with the 🔵 PlayLingo account sign-in,
the PS5 eight-digit number and the DeepL key opens on your phone or in a computer browser on the same network.
**LG / Samsung**: the TV cannot read the game picture itself – an **Android phone** with PlayLingo reads and translates
and sends the lines to the TV (iPhone and computer cannot do that yet).*
