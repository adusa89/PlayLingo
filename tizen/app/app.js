/*
 * PlayLingo pro televize LG (webOS) a Samsung (Tizen).
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
 * Soubor je stejný ve webos/obchod/app i v tizen/app (hlídá to sestavení APK, úloha
 * samsungBalicek). Co se na LG a Samsungu dělá jinak, je v platforma.js (window.PlayLingoTv).
 *
 * Schválně psáno postaru (ES5: var, function, žádné šipky ani šablony),
 * protože televize z let 2016–2017 mají prohlížeč Chromium 38 (LG) a Samsung z let
 * 2015–2016 ještě starší WebKit.
 */
(function () {
  'use strict';

  var P = window.PlayLingoTv;

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
        'Nastavení → Posílat titulky do televize.',
      pripojeno: 'PlayLingo je připojeno. Překlad se ukáže, jakmile ve hře někdo promluví.',
      bez_vstupu: 'Vstup HDMI %1 se nepodařilo otevřít. Tlačítkem Zpět vyberte jiný.',
      bez_site: 'Televize není připojená k domácí síti.',
      uk_stitek: 'Ukázka',
      uk_popis: 'Hra ukazuje titulky anglicky. PlayLingo je přečte, přeloží a překlad ukáže v černém pruhu dole.',
      np_nadpis: 'Jak to funguje',
      np_1: 'Do telefonu nebo boxu s Androidem nainstalujte PlayLingo: github.com/adusa89/PlayLingo',
      np_2: 'Podle průvodce v ní spárujte PlayStation 5. Zařízení musí být ve stejné domácí síti jako televize.',
      np_3: 'V PlayLingu na Androidu zapněte Nastavení → Posílat titulky do televize a spusťte překlad.',
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
        'Settings → Send subtitles to TV.',
      pripojeno: 'PlayLingo is connected. The translation appears as soon as someone speaks in the game.',
      bez_vstupu: 'HDMI %1 could not be opened. Press Back and choose another input.',
      bez_site: 'The TV is not connected to your home network.',
      uk_stitek: 'Demo',
      uk_popis: 'The game shows subtitles in English. PlayLingo reads them, translates them and shows ' +
        'the translation in the black bar below (here: Czech).',
      np_nadpis: 'How it works',
      np_1: 'Install PlayLingo on an Android phone or box: github.com/adusa89/PlayLingo',
      np_2: 'Follow its guide to pair your PlayStation 5. The device must be in the same home network as the TV.',
      np_3: 'In PlayLingo on Android, turn on Settings → Send subtitles to TV and start translating.',
      np_4: 'Here on the TV, choose the HDMI input of your PlayStation. The TV finds PlayLingo in the network by itself.',
      np_sony: 'This app is not a product of Sony Interactive Entertainment. “PlayStation” and “PS5” are their trademarks.'
    }
  };

  // Ukázka: titulek ve hře a jeho překlad.
  var UKAZKA = [
    ['We need to get across the river.', 'Musíme se dostat na druhou stranu řeky.'],
    ['Wait here. I\'ll be right back.', 'Počkej tady. Hned jsem zpátky.'],
    ['I don\'t like this at all.', 'Tohle se mi vůbec nelíbí.'],
    ['Saddle up, we ride at dawn.', 'Nasedni na koně, vyrážíme za svítání.'],
    // dva mluvčí – každý má jméno ve své barvě
    ['Mira: I\'ll take the left door.\nTobin: Then I\'m going right.', '', [
      { jmeno: 'Mira', barva: '#F2C14E', text: 'Vezmu levé dveře.' },
      { jmeno: 'Tobin', barva: '#7FC8F8', text: 'Tak já jdu doprava.' }
    ]],
    // otázka a volby odpovědi – v pruhu jako seznam
    ['Kessler: So, what will it be?\n1. I\'ll take the job.\n2. Tell me more about the pay.\n3. Not interested.', '', [
      { jmeno: 'Kessler', barva: '#8EDB8A', text: 'Tak co bude?' },
      { volba: true, text: 'Vezmu tu práci.' },
      { volba: true, text: 'Řekni mi víc o výplatě.' },
      { volba: true, text: 'Nemám zájem.' }
    ]]
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

  // ---- Dotazy na systém televize (platforma.js) ---------------------

  function zjistiTelevizi() {
    P.zjistiTelevizi(function (model, system) {
      udaje.model = model;
      udaje.system = system;
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

  // Barva jména z Androidu – jen zápis #RRGGBB, nic jiného se do stylu nedostane.
  function barvaJmena(b) {
    b = String(b || '');
    return /^#[0-9A-Fa-f]{6}$/.test(b) ? b : '';
  }

  // Pruh po řádcích (pole casti, viz TvSender.kt): jména v barvách mluvčích a volby odpovědí
  // jako seznam s odrážkami. Bez casti (starší PlayLingo na Androidu) jen obyčejný text.
  function pruhCasti(casti, text) {
    var t = el('titulek');
    var i, c, radek, kus, seznam = null;
    if (!casti || Object.prototype.toString.call(casti) !== '[object Array]' || !casti.length) {
      pruh(text);
      return;
    }
    t.textContent = '';
    for (i = 0; i < casti.length; i++) {
      c = casti[i] || {};
      radek = document.createElement('div');
      if (c.volba) {
        // volby pod sebou zarovnané vlevo, celý seznam uprostřed pruhu
        if (!seznam) {
          seznam = document.createElement('div');
          seznam.className = 'volby';
          t.appendChild(seznam);
        }
        kus = document.createElement('span');
        kus.className = 'odrazka';
        kus.textContent = '• ';
        radek.appendChild(kus);
        radek.appendChild(document.createTextNode(String(c.text || '')));
        seznam.appendChild(radek);
      } else {
        seznam = null;
        if (c.jmeno) {
          kus = document.createElement('span');
          kus.className = 'jmeno';
          kus.textContent = String(c.jmeno) + ': ';
          // bez barvy (uživatel má jména bez barvy) zůstane jméno bílé jako text
          if (barvaJmena(c.barva)) { kus.style.color = barvaJmena(c.barva); kus.className = 'jmeno barevne'; }
          radek.appendChild(kus);
        }
        radek.appendChild(document.createTextNode(String(c.text || '')));
        t.appendChild(radek);
      }
    }
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
    P.skryjVstup();
    el('obraz').innerHTML = '';
    strana = kam;
    // i na <html>: Samsung kreslí obraz ze vstupu pod celou stránku, takže musí být průhledná celá
    document.documentElement.className = 's-' + kam;
    document.body.className = 's-' + kam;
    ukazZpet(kam === 'ukazka' || kam === 'napoveda');
  }

  function spustUkazku() {
    var poradi = 0;
    function ukaz() {
      var u = UKAZKA[poradi];
      el('original').textContent = u[0];
      pruhCasti(u[2], u[1]);
      // anglický titulek nad pruhem, i když je pruh vyšší (víc řádků)
      el('original').style.bottom = Math.max(190, el('pruh').offsetHeight + 40) + 'px';
      poradi = (poradi + 1) % UKAZKA.length;
    }
    prejdi('ukazka');
    ukaz();
    casovace.push(setInterval(ukaz, 3500));
  }

  // ---- Obraz ze vstupu HDMI přímo v aplikaci ---------------------------

  function ukazVstup(cislo) {
    P.ukazVstup(cislo, el('obraz'), function () {
      if (strana === 'hra' && !spojeni) { hlaseni(T.bez_vstupu.replace('%1', cislo)); }
    });
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
        pruhCasti(z.casti, String(z.text || ''));
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
    // platforma a verze: podle nich průvodce na Androidu pozná, že je aplikace v televizi nainstalovaná
    try {
      ws.send(JSON.stringify({ typ: 'pripojeno', televize: udaje.model, system: udaje.system, hdmi: prijem.hdmi,
        aplikace: 'PlayLingo', platforma: P.platforma, verze: P.verze }));
    } catch (e) { /* onclose se postará */ }
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
      P.adresaSite(function (ip, odpovedela) {
        if (!plati()) { return; }
        var m = /^(\d+\.\d+\.\d+\.)(\d+)$/.exec(ip || '');
        if (!m) {
          if (odpovedela) { hlaseni(T.bez_site); }
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
  // Odkud se berou, ví platforma.js.
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

  // Úplné ukončení (i hledání v síti). Jak, ví platforma.js.
  function ukonci() {
    prejdi('domu');
    P.ukonci();
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
    var zpet = P.jeZpet(klavesa);
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
    P.jazykTelevize(function (kod) {
      if (kod && !pamet('jazyk')) { nastavJazyk(kod); }
    });
  }

  if (znacka.indexOf('ukazka') >= 0) { spustUkazku(); }
  else if (znacka.indexOf('napoveda') >= 0) { prejdi('napoveda'); }
  else if (znacka.indexOf('hra') >= 0) { spustHru(1, null, true); }

  P.parametry(podleParametru);
})();
