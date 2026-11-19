/*
 * PlayLingo pro televize LG (webOS) – verze pro obchod LG.
 *
 * Televize ukáže hru ze vstupu HDMI a přes ni pruh s překladem titulků. Sama si obraz
 * přečíst neumí, proto čte a překládá PlayLingo na Androidu (telefon nebo box) v téže
 * domácí síti a sem posílá hotové věty. Televize si ho najde sama: zná svou adresu,
 * tak postupně zkusí všechny adresy téže sítě na portu PORT. Kdo odpoví zprávou
 * {"typ":"vitej"}, je PlayLingo. Zprávy jsou popsané v app/…/core/TvSender.kt.
 *
 * Tohle je jediné místo, kde televize zprávy přijímá. Zkoušet jde i bez Androidu:
 * `node webos/posilac.js` posílá z počítače zkušební věty.
 *
 * Schválně psáno postaru (ES5: var, function, žádné šipky ani šablony),
 * protože televize z let 2016–2017 mají prohlížeč Chromium 38.
 */
(function () {
  'use strict';

  var TEXTY = {
    cs: {
      nadpis: 'Překlad titulků ze hry',
      popis: 'Televize ukáže hru z PlayStationu a přes ni pruh s překladem. Číst a překládat ' +
        'musí aplikace PlayLingo v telefonu nebo boxu s Androidem ve stejné domácí síti.',
      otazka: 'Do kterého vstupu je zapojený PlayStation?',
      tl_ukazka: 'Ukázka',
      tl_napoveda: 'Jak to funguje',
      tl_jazyk: 'Jazyk: čeština',
      tl_zpet: 'Zpět',
      tl_konec: 'Ukončit',
      ovladani: 'Šipkami vyberte, potvrďte tlačítkem OK. Tlačítko Zpět vrátí tuto obrazovku, na ní aplikaci ukončí.',
      hledam: 'Hledám PlayLingo v domácí síti… V aplikaci PlayLingo na Androidu zapněte ' +
        'Nastavení → Posílat titulky do televize LG.',
      pripojeno: 'PlayLingo je připojeno. Překlad se ukáže, jakmile ve hře někdo promluví.',
      bez_vstupu: 'Vstup HDMI %1 se nepodařilo otevřít. Tlačítkem Zpět vyberte jiný.',
      bez_site: 'Televize není připojená k domácí síti.',
      uk_stitek: 'Ukázka',
      uk_popis: 'Hra ukazuje titulky anglicky. PlayLingo je přečte, přeloží a překlad ukáže v černém pruhu dole.',
      np_nadpis: 'Jak to funguje',
      np_1: 'Do telefonu nebo boxu s Androidem nainstalujte PlayLingo: github.com/adusa89/PlayLingo',
      np_2: 'Podle průvodce v ní spárujte PlayStation 5. Zařízení musí být ve stejné domácí síti jako televize.',
      np_3: 'V PlayLingu na Androidu zapněte Nastavení → Posílat titulky do televize LG a spusťte překlad.',
      np_4: 'Tady v televizi vyberte vstup HDMI s PlayStationem. Televize si PlayLingo v síti najde sama.',
      np_sony: 'Aplikace není produktem společnosti Sony Interactive Entertainment. „PlayStation“ a „PS5“ jsou jejich ochranné známky.'
    },
    en: {
      nadpis: 'Translated game subtitles',
      popis: 'The TV shows your PlayStation game with a translation bar over it. Reading and ' +
        'translating is done by the PlayLingo app on an Android phone or box in the same home network.',
      otazka: 'Which input is your PlayStation connected to?',
      tl_ukazka: 'Demo',
      tl_napoveda: 'How it works',
      tl_jazyk: 'Language: English',
      tl_zpet: 'Back',
      tl_konec: 'Exit',
      ovladani: 'Select with the arrow keys and press OK. The Back button returns to this screen and exits the app from here.',
      hledam: 'Looking for PlayLingo in your home network… In PlayLingo on Android, turn on ' +
        'Settings → Send subtitles to LG TV.',
      pripojeno: 'PlayLingo is connected. The translation appears as soon as someone speaks in the game.',
      bez_vstupu: 'HDMI %1 could not be opened. Press Back and choose another input.',
      bez_site: 'The TV is not connected to your home network.',
      uk_stitek: 'Demo',
      uk_popis: 'The game shows subtitles in English. PlayLingo reads them, translates them and shows ' +
        'the translation in the black bar below (here: Czech).',
      np_nadpis: 'How it works',
      np_1: 'Install PlayLingo on an Android phone or box: github.com/adusa89/PlayLingo',
      np_2: 'Follow its guide to pair your PlayStation 5. The device must be in the same home network as the TV.',
      np_3: 'In PlayLingo on Android, turn on Settings → Send subtitles to LG TV and start translating.',
      np_4: 'Here on the TV, choose the HDMI input of your PlayStation. The TV finds PlayLingo in the network by itself.',
      np_sony: 'This app is not a product of Sony Interactive Entertainment. “PlayStation” and “PS5” are their trademarks.'
    }
  };

  // Ukázka: titulek ve hře a jeho překlad.
  var UKAZKA = [
    ['We need to get across the river.', 'Musíme se dostat na druhou stranu řeky.'],
    ['Wait here. I\'ll be right back.', 'Počkej tady. Hned jsem zpátky.'],
    ['I don\'t like this at all.', 'Tohle se mi vůbec nelíbí.'],
    ['Saddle up, we ride at dawn.', 'Nasedni na koně, vyrážíme za svítání.']
  ];

  var PORT = 8787;
  var jazyk = 'en';
  var T = TEXTY.en;
  var strana = 'domu';
  var vybrane = 0;
  var casovace = [];
  var tlacitka = document.querySelectorAll('#domu .tl');
  var udaje = { model: '?', system: '?' };

  var spojeni = null;
  var prijem = null;      // { hdmi: číslo vstupu, pevna: adresa z parametrů nebo null }
  var hledani = 0;        // pořadové číslo hledání; starší rozběhnuté pokusy se podle něj zahodí
  var zpetCasovac = null;

  function el(id) { return document.getElementById(id); }

  function pozdeji(fn, ms) {
    var t = setTimeout(fn, ms);
    casovace.push(t);
    return t;
  }

  function pamet(klic, hodnota) {
    try {
      if (hodnota === undefined) { return window.localStorage.getItem(klic) || ''; }
      window.localStorage.setItem(klic, hodnota);
    } catch (e) { /* bez paměti se jen hledá déle */ }
    return '';
  }

  // ---- Texty ------------------------------------------------------------

  function nastavJazyk(kod) {
    var i;
    var prvky = document.querySelectorAll('[data-t]');
    jazyk = /^(cs|sk)/i.test(kod || '') ? 'cs' : 'en';
    T = TEXTY[jazyk];
    document.documentElement.lang = jazyk;
    for (i = 0; i < prvky.length; i++) {
      prvky[i].textContent = T[prvky[i].getAttribute('data-t')] || '';
    }
  }

  // ---- Dotazy na systém televize -------------------------------------

  // Zavolá službu televize. Odpověď (nebo chybu) vrátí vždy přesně jednou.
  function sluzba(adresa, parametry, hotovo) {
    var Most = window.WebOSServiceBridge || window.PalmServiceBridge;
    var vyrizeno = false;
    function konec(odpoved) {
      if (vyrizeno) { return; }
      vyrizeno = true;
      hotovo(odpoved);
    }
    if (!Most) {
      konec({ returnValue: false, errorText: 'mimo televizi' });
      return;
    }
    try {
      var most = new Most();
      most.onservicecallback = function (zprava) {
        var o;
        try { o = JSON.parse(zprava); } catch (e) { o = { returnValue: false, errorText: String(zprava) }; }
        konec(o);
      };
      most.call(adresa, JSON.stringify(parametry));
    } catch (e) {
      konec({ returnValue: false, errorText: String(e && e.message || e) });
    }
    setTimeout(function () { konec({ returnValue: false, errorText: 'bez odpovědi' }); }, 5000);
  }

  function zjistiTelevizi() {
    sluzba('luna://com.webos.service.tv.systemproperty/getSystemInfo',
      { keys: ['modelName', 'firmwareVersion', 'sdkVersion'] },
      function (o) {
        if (o && o.modelName) {
          udaje.model = o.modelName;
          udaje.system = 'webOS ' + (o.sdkVersion || '?') + ' (' + (o.firmwareVersion || '?') + ')';
        }
      });
  }

  // ---- Obrazovky --------------------------------------------------------

  function hlaseni(text) {
    var e = el('hlaseni');
    e.textContent = text || '';
    e.style.display = text ? 'block' : 'none';
  }

  function pruh(text) {
    if (text === null) { el('pruh').style.display = 'none'; return; }
    el('titulek').textContent = text;
    el('pruh').style.display = 'flex';
  }

  function ukazZpet(ano) {
    if (zpetCasovac !== null) { clearTimeout(zpetCasovac); zpetCasovac = null; }
    el('zpet').style.display = ano ? 'block' : 'none';
  }

  function oznac(index) {
    var i;
    vybrane = Math.max(0, Math.min(tlacitka.length - 1, index));
    for (i = 0; i < tlacitka.length; i++) {
      tlacitka[i].className = tlacitka[i].className.replace(/ ?vybrane/, '') + (i === vybrane ? ' vybrane' : '');
    }
  }

  // Uklidí vše, co po sobě nechala hra, ukázka nebo nápověda, a ukáže danou obrazovku.
  function prejdi(kam) {
    var i;
    for (i = 0; i < casovace.length; i++) {
      clearTimeout(casovace[i]);
      clearInterval(casovace[i]);
    }
    casovace = [];
    prijem = null;
    hledani++;
    zavri(spojeni);
    spojeni = null;
    hlaseni('');
    pruh(null);
    el('obraz').innerHTML = '';
    strana = kam;
    document.body.className = 's-' + kam;
    ukazZpet(kam === 'ukazka' || kam === 'napoveda');
  }

  function spustUkazku() {
    var poradi = 0;
    function ukaz() {
      el('original').textContent = UKAZKA[poradi][0];
      pruh(UKAZKA[poradi][1]);
      poradi = (poradi + 1) % UKAZKA.length;
    }
    prejdi('ukazka');
    ukaz();
    casovace.push(setInterval(ukaz, 3500));
  }

  // ---- Obraz ze vstupu HDMI přímo v aplikaci ---------------------------

  function ukazVstup(cislo) {
    var video = document.createElement('video');
    var zdroj = document.createElement('source');
    function chyba() {
      if (strana === 'hra' && !spojeni) { hlaseni(T.bez_vstupu.replace('%1', cislo)); }
    }
    video.setAttribute('autoplay', 'autoplay');
    zdroj.setAttribute('src', 'ext://hdmi:' + cislo);
    zdroj.setAttribute('type', 'service/webos-external');
    video.addEventListener('error', chyba);
    zdroj.addEventListener('error', chyba);
    video.appendChild(zdroj);
    el('obraz').appendChild(video);
  }

  // bezVstupu: jen pro snímek obrazovky v počítači, kde žádný vstup HDMI není.
  function spustHru(cislo, pevna, bezVstupu) {
    prejdi('hra');
    prijem = { hdmi: cislo, pevna: pevna || null };
    pamet('hdmi', String(cislo));
    oznac(cislo - 1);
    hlaseni(T.hledam);
    if (!bezVstupu) { ukazVstup(cislo); }
    hledej();
  }

  // ---- Titulky po síti ------------------------------------------------

  function poVykresleni(fn) {
    if (window.requestAnimationFrame) { window.requestAnimationFrame(fn); } else { setTimeout(fn, 0); }
  }

  function zavri(ws) {
    if (!ws) { return; }
    try { ws.onopen = ws.onmessage = ws.onerror = ws.onclose = null; ws.close(); } catch (e) { /* už zavřené */ }
  }

  // Zkusí jednu adresu. Když se do `ms` ozve PlayLingo, vrátí otevřené spojení, jinak null.
  function zkus(adresa, ms, hotovo) {
    var ws = null;
    var vyrizeno = false;
    function konec(ok) {
      if (vyrizeno) { return; }
      vyrizeno = true;
      if (!ok) { zavri(ws); }
      hotovo(ok ? ws : null);
    }
    try { ws = new WebSocket(adresa); } catch (e) { konec(false); return; }
    ws.onmessage = function (udalost) {
      var z = null;
      try { z = JSON.parse(udalost.data); } catch (e) { /* není to PlayLingo */ }
      konec(!!z && z.typ === 'vitej');
    };
    ws.onerror = function () { konec(false); };
    ws.onclose = function () { konec(false); };
    setTimeout(function () { konec(false); }, ms);
  }

  // Nalezené spojení: od teď je na obrazovce jen hra a pruh.
  function prevezmi(ws, adresa) {
    spojeni = ws;
    if (!prijem.pevna) { pamet('titulky', adresa); }
    pruh(null);
    hlaseni(T.pripojeno);
    pozdeji(function () { if (spojeni === ws) { hlaseni(''); } }, 6000);
    ws.onerror = null;
    ws.onmessage = function (udalost) {
      var z;
      try { z = JSON.parse(udalost.data); } catch (e) { return; }
      if (z.typ === 'ukaz') {
        hlaseni('');
        pruh(String(z.text || ''));
      } else if (z.typ === 'skryj') {
        pruh(null);
      } else {
        return;
      }
      poVykresleni(function () {
        try { ws.send(JSON.stringify({ typ: 'hotovo', id: z.id })); } catch (e) { /* spojení mezitím spadlo */ }
      });
    };
    ws.onclose = function () {
      if (spojeni !== ws || !prijem) { return; }
      spojeni = null;
      pruh(null);
      hlaseni(T.hledam);
      pozdeji(hledej, 2000);
    };
    try {
      ws.send(JSON.stringify({ typ: 'pripojeno', televize: udaje.model, system: udaje.system, hdmi: prijem.hdmi }));
    } catch (e) { /* onclose se postará */ }
  }

  function stavSite(hotovo) {
    sluzba('luna://com.palm.connectionmanager/getStatus', {}, function (a) {
      if (a && a.returnValue) { hotovo(a); return; }
      sluzba('luna://com.palm.connectionmanager/getstatus', {}, hotovo);
    });
  }

  function hledej() {
    if (!prijem || spojeni) { return; }
    var moje = ++hledani;
    function plati() { return !!prijem && !spojeni && moje === hledani; }
    function znovu() { if (plati()) { pozdeji(hledej, 5000); } }
    function jedna(adresa, ms, jinak) {
      zkus(adresa, ms, function (ws) {
        if (ws && plati()) { prevezmi(ws, adresa); return; }
        zavri(ws);
        if (plati()) { jinak(); }
      });
    }
    function celaSit() {
      // LG službu píše getStatus, televize z roku 2017 odpovídá na getstatus – zkusí se obojí.
      stavSite(function (o) {
        if (!plati()) { return; }
        var ip = o && ((o.wired && o.wired.ipAddress) || (o.wifi && o.wifi.ipAddress));
        var m = /^(\d+\.\d+\.\d+\.)(\d+)$/.exec(ip || '');
        if (!m) {
          if (o && o.returnValue) { hlaseni(T.bez_site); }
          znovu();
          return;
        }
        var fronta = [];
        var bezi = 0;
        var nalezeno = false;
        var n;
        for (n = 1; n < 255; n++) {
          if (String(n) !== m[2]) { fronta.push('ws://' + m[1] + n + ':' + PORT); }
        }
        function spust(adresa) {
          bezi++;
          zkus(adresa, 1500, function (ws) {
            bezi--;
            if (ws && !nalezeno && plati()) { nalezeno = true; prevezmi(ws, adresa); return; }
            zavri(ws);
            dalsi();
          });
        }
        function dalsi() {
          if (nalezeno || !plati()) { return; }
          while (bezi < 24 && fronta.length) { spust(fronta.shift()); }
          if (bezi === 0 && !fronta.length) { znovu(); }
        }
        dalsi();
      });
    }
    if (prijem.pevna) { jedna(prijem.pevna, 3000, znovu); return; }
    var minula = pamet('titulky');
    if (minula) { jedna(minula, 2000, celaSit); } else { celaSit(); }
  }

  // Parametry spuštění (jen pro zkoušky): {"titulky": "ws://adresa:port", "hdmi": 2}.
  // Při startu je má PalmSystem, při spuštění už běžící aplikace je nese událost webOSRelaunch.
  function podleParametru(p) {
    if (typeof p === 'string') {
      try { p = JSON.parse(p || '{}'); } catch (e) { p = {}; }
    }
    if (p && typeof p.titulky === 'string' && /^wss?:\/\//.test(p.titulky)) {
      spustHru(parseInt(p.hdmi, 10) || 1, p.titulky);
    }
  }

  // ---- Ovládání ---------------------------------------------------------

  function potvrd(index) {
    oznac(index);
    var akce = tlacitka[vybrane].getAttribute('data-akce');
    if (akce === 'hdmi') { spustHru(parseInt(tlacitka[vybrane].getAttribute('data-hdmi'), 10)); }
    else if (akce === 'jazyk') {
      // Jazyk aplikace v televizi (česky / anglicky). Jazyk překladu se volí v PlayLingu na Androidu.
      nastavJazyk(jazyk === 'cs' ? 'en' : 'cs');
      pamet('jazyk', jazyk);
    }
    else if (akce === 'ukazka') { spustUkazku(); }
    else if (akce === 'konec') { ukonci(); }
    else { prejdi('napoveda'); }
  }

  // Úplné ukončení. Samotné platformBack aplikaci jen schová do pozadí, kde by dál hledala
  // čtečku v síti; window.close() ji zavře.
  function ukonci() {
    try { window.close(); } catch (e) { /* jinak aspoň do pozadí */ }
    if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); }
  }

  // Domů jsou dvě řady: čtyři vstupy (0–3) a pod nimi Ukázka (4), Jak to funguje (5), Jazyk (6)
  // a Ukončit (7).
  function sipka(klavesa) {
    var v = vybrane;
    if (klavesa === 37) { if (v !== 0 && v !== 4) { oznac(v - 1); } }
    else if (klavesa === 39) { if (v !== 3 && v !== 7) { oznac(v + 1); } }
    else if (klavesa === 40) { if (v < 4) { oznac(v + 4); } }
    else if (klavesa === 38) { if (v >= 4) { oznac(v - 4); } }
  }

  document.addEventListener('keydown', function (udalost) {
    var klavesa = udalost.keyCode;
    var zpet = klavesa === 461 || klavesa === 27 || klavesa === 8;
    if (strana !== 'domu') {
      // V ukázce a nápovědě je označené tlačítko Zpět, takže ho potvrdí i OK.
      if (zpet || (klavesa === 13 && strana !== 'hra')) { prejdi('domu'); udalost.preventDefault(); }
      return;
    }
    if (klavesa >= 37 && klavesa <= 40) { sipka(klavesa); udalost.preventDefault(); }
    else if (klavesa === 13) { potvrd(vybrane); }
    else if (zpet) { ukonci(); udalost.preventDefault(); }
  });

  // Ukazovátko ovladače Magic Remote.
  (function () {
    var i;
    function pripoj(index) {
      tlacitka[index].addEventListener('mouseover', function () { if (strana === 'domu') { oznac(index); } });
      tlacitka[index].addEventListener('click', function () { if (strana === 'domu') { potvrd(index); } });
    }
    for (i = 0; i < tlacitka.length; i++) { pripoj(i); }
    el('zpet').addEventListener('click', function () { prejdi('domu'); });
    // Při hře tlačítko Zpět nepřekáží; ukáže se jen na chvíli po pohybu ukazovátka.
    document.addEventListener('mousemove', function () {
      if (strana !== 'hra') { return; }
      ukazZpet(true);
      zpetCasovac = setTimeout(function () { if (strana === 'hra') { ukazZpet(false); } }, 4000);
    });
  })();

  // ---- Start --------------------------------------------------------------

  // Značka v adrese je jen pro snímky obrazovky a zkoušení v počítači:
  // index.html#cs, #en, #ukazka, #napoveda, #hra (jde spojit: #en-ukazka).
  var znacka = String(window.location.hash || '').replace('#', '').split('-');
  var vynuceny = znacka.indexOf('cs') >= 0 ? 'cs' : (znacka.indexOf('en') >= 0 ? 'en' : '');

  // Pořadí: značka v adrese (snímky), jazyk zvolený tlačítkem, jinak jazyk televize.
  var zvoleny = vynuceny || pamet('jazyk');
  nastavJazyk(zvoleny || navigator.language);
  prejdi('domu');
  oznac((parseInt(pamet('hdmi'), 10) || 1) - 1);
  zjistiTelevizi();
  if (!zvoleny) {
    // Jazyk nabídky televize je spolehlivější než jazyk prohlížeče.
    sluzba('luna://com.webos.settingsservice/getSystemSettings', { keys: ['localeInfo'] }, function (o) {
      var kod = o && o.settings && o.settings.localeInfo && o.settings.localeInfo.locales && o.settings.localeInfo.locales.UI;
      if (kod && !pamet('jazyk')) { nastavJazyk(kod); }
    });
  }

  if (znacka.indexOf('ukazka') >= 0) { spustUkazku(); }
  else if (znacka.indexOf('napoveda') >= 0) { prejdi('napoveda'); }
  else if (znacka.indexOf('hra') >= 0) { spustHru(1, null, true); }

  document.addEventListener('webOSRelaunch', function (udalost) { podleParametru(udalost.detail); });
  podleParametru(window.PalmSystem && window.PalmSystem.launchParams);
})();
