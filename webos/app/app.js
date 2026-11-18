/*
 * PlayLingo – zkušební aplikace pro televize LG (webOS).
 *
 * Zjišťuje na skutečné televizi, co je pro překlad titulků potřeba:
 *   - jestli aplikace umí ukázat obraz ze vstupu HDMI a nakreslit přes něj pruh,
 *   - jestli si aplikace může ten obraz sama přečíst (pak by nebylo potřeba
 *     připojovat se k PlayStationu po síti),
 *   - jakou verzi systému a prohlížeče televize má.
 *
 * Schválně psáno postaru (ES5: var, function, žádné šipky ani šablony),
 * protože televize z let 2016–2017 mají prohlížeč Chromium 38.
 */
(function () {
  'use strict';

  var VETY = [
    'Tady by byl překlad titulků ze hry.',
    'Musíme se dostat na druhou stranu řeky.',
    'Počkej tady. Hned jsem zpátky.',
    'Tohle se mi vůbec nelíbí.'
  ];

  var udaje = { model: '?', system: '?', jadro: '?' };
  var vybrane = 0;
  var hraje = false;
  var casovace = [];
  var tlacitka = document.getElementById('tlacitka').children;

  function el(id) { return document.getElementById(id); }

  function napis(id, text, stav) {
    var e = el(id);
    e.textContent = text;
    e.className = stav || '';
  }

  function pozdeji(fn, ms) {
    var t = setTimeout(fn, ms);
    casovace.push(t);
    return t;
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
    var m = /Chrom(?:e|ium)\/(\d+)/.exec(navigator.userAgent);
    udaje.jadro = m ? 'Chromium ' + m[1] : 'neznámé';
    napis('u-jadro', udaje.jadro);

    sluzba('luna://com.webos.service.tv.systemproperty/getSystemInfo',
      { keys: ['modelName', 'firmwareVersion', 'sdkVersion'] },
      function (o) {
        if (o && o.modelName) {
          udaje.model = o.modelName;
          udaje.system = 'webOS ' + (o.sdkVersion || '?') + ' (' + (o.firmwareVersion || '?') + ')';
        } else {
          udaje.model = 'nezjištěno';
          udaje.system = 'nezjištěno';
        }
        napis('u-model', udaje.model);
        napis('u-system', udaje.system);
        if (hraje) { vypisUdaje(); }
      });
  }

  function vypisUdaje() {
    napis('v-model', udaje.model);
    napis('v-system', udaje.system);
    napis('v-jadro', udaje.jadro);
  }

  // ---- Obraz ze vstupu HDMI přímo v aplikaci ---------------------------

  function ukazVstup(cislo) {
    var stav = { hraje: false, chyba: false };
    var video = document.createElement('video');
    video.setAttribute('autoplay', 'autoplay');
    var zdroj = document.createElement('source');
    zdroj.setAttribute('src', 'ext://hdmi:' + cislo);
    zdroj.setAttribute('type', 'service/webos-external');
    video.addEventListener('playing', function () { stav.hraje = true; });
    video.addEventListener('loadedmetadata', function () { stav.hraje = true; });
    video.addEventListener('error', function () { stav.chyba = true; });
    zdroj.addEventListener('error', function () { stav.chyba = true; });
    video.appendChild(zdroj);
    el('obraz').appendChild(video);
    return { video: video, stav: stav };
  }

  // ---- Titulky po síti ------------------------------------------------
  //
  // Televize si obraz sama přečíst neumí (zjistila zkouška níže), proto čtení
  // a překlad dělá jiné zařízení v domácí síti (PlayLingo v telefonu nebo v krabičce
  // s Android TV) a sem posílá hotové věty. Televize si ho najde sama: zná svou adresu,
  // tak postupně zkusí všechny adresy téže sítě na portu PORT. Kdo odpoví zprávou
  // {"typ":"vitej"}, je PlayLingo. Adresa se zapamatuje a příště se zkusí první.
  // Pro zkoušky jde adresu určit i napevno parametry spuštění
  // {"titulky": "ws://adresa:port", "hdmi": 2}.
  // Zprávy: {"typ":"ukaz","id":1,"text":"…"} a {"typ":"skryj","id":2}. Na každou
  // televize po vykreslení odpoví {"typ":"hotovo","id":…}, aby šlo změřit zpoždění.

  var PORT = 8787;
  var spojeni = null;
  var prijem = null;      // { hdmi: číslo vstupu, pevna: adresa z parametrů nebo null }
  var hledani = 0;        // pořadové číslo hledání; starší rozběhnuté pokusy se podle něj zahodí
  var ukazka = null;      // střídání zkušebních vět, dokud nepřijdou skutečné titulky

  function hlaseni(text) {
    var e = el('hlaseni');
    e.textContent = text || '';
    e.style.display = text ? 'block' : 'none';
  }

  function poVykresleni(fn) {
    if (window.requestAnimationFrame) { window.requestAnimationFrame(fn); } else { setTimeout(fn, 0); }
  }

  function ulozenaAdresa() {
    try { return window.localStorage.getItem('titulky') || ''; } catch (e) { return ''; }
  }

  function ulozAdresu(adresa) {
    try { window.localStorage.setItem('titulky', adresa); } catch (e) { /* bez paměti se jen hledá déle */ }
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

  function cekam() {
    return prijem && prijem.pevna ? 'Čekám na titulky z ' + prijem.pevna : 'Hledám v síti PlayLingo, které pošle titulky…';
  }

  // Nalezené spojení: od teď je na obrazovce jen hra a pruh.
  function prevezmi(ws, adresa) {
    spojeni = ws;
    if (!prijem.pevna) { ulozAdresu(adresa); }
    if (ukazka !== null) { clearInterval(ukazka); ukazka = null; }
    document.body.className = 'hraje prijem';
    el('pruh').style.display = 'none';
    hlaseni('');
    ws.onerror = null;
    ws.onmessage = function (udalost) {
      var z;
      try { z = JSON.parse(udalost.data); } catch (e) { return; }
      if (z.typ === 'ukaz') {
        el('titulek').textContent = String(z.text || '');
        el('pruh').style.display = '';
      } else if (z.typ === 'skryj') {
        el('pruh').style.display = 'none';
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
      el('pruh').style.display = 'none';
      hlaseni(cekam());
      pozdeji(hledej, 2000);
    };
    try {
      ws.send(JSON.stringify({ typ: 'pripojeno', televize: udaje.model, system: udaje.system, hdmi: prijem.hdmi }));
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
      sluzba('luna://com.palm.connectionmanager/getstatus', {}, function (o) {
        if (!plati()) { return; }
        var ip = o && ((o.wired && o.wired.ipAddress) || (o.wifi && o.wifi.ipAddress));
        var m = /^(\d+\.\d+\.\d+\.)(\d+)$/.exec(ip || '');
        if (!m) { znovu(); return; }
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
    var minula = ulozenaAdresa();
    if (minula) { jedna(minula, 2000, celaSit); } else { celaSit(); }
  }

  // Spuštění s adresou napevno (zkoušky): rovnou jen hra a pruh.
  function spustPrijem(adresa, cislo) {
    if (hraje) { ukonciZkousku(); }
    hraje = true;
    prijem = { hdmi: cislo, pevna: adresa };
    document.body.className = 'hraje prijem';
    el('pruh').style.display = 'none';
    hlaseni(cekam());
    ukazVstup(cislo);
    hledej();
  }

  // Parametry spuštění: při startu je má PalmSystem, při spuštění už běžící
  // aplikace je nese událost webOSRelaunch.
  function podleParametru(p) {
    if (typeof p === 'string') {
      try { p = JSON.parse(p || '{}'); } catch (e) { p = {}; }
    }
    if (p && typeof p.titulky === 'string' && /^wss?:\/\//.test(p.titulky)) {
      spustPrijem(p.titulky, parseInt(p.hdmi, 10) || 1);
    }
  }

  // ---- Zkouška --------------------------------------------------------

  function spustZkousku(cislo) {
    hraje = true;
    document.body.className = 'hraje';
    napis('v-vstup', '– HDMI ' + cislo);
    vypisUdaje();
    napis('v-hdmi', 'čekám…');
    napis('v-cteni', 'čekám…');
    napis('v-snimek', 'čekám…');
    napis('v-wasm', typeof WebAssembly === 'object' ? 'ano' : 'ne',
      typeof WebAssembly === 'object' ? 'ano' : 'ne');

    var vstup = ukazVstup(cislo);
    var stav = vstup.stav;
    var video = vstup.video;

    // Titulky v pruhu se střídají jako při hře – dokud se neozve zařízení se skutečnými.
    var poradi = 0;
    el('titulek').textContent = VETY[0];
    ukazka = setInterval(function () {
      poradi = (poradi + 1) % VETY.length;
      el('titulek').textContent = VETY[poradi];
    }, 3500);
    prijem = { hdmi: cislo, pevna: null };
    hledej();

    pozdeji(function () {
      if (stav.chyba) {
        napis('v-hdmi', 'televize vstup odmítla', 'ne');
      } else if (stav.hraje) {
        napis('v-hdmi', 'televize hlásí, že hraje', 'ano');
      } else {
        napis('v-hdmi', 'bez hlášení – rozhodne, co vidíte');
      }
      zkusCteni(video);
      zkusSnimek();
    }, 5000);
  }

  // Umí si aplikace přečíst, co je na obraze? (zmenšený snímek do plátna)
  function zkusCteni(video) {
    try {
      var platno = el('platno');
      var kresba = platno.getContext('2d');
      kresba.clearRect(0, 0, 160, 90);
      kresba.drawImage(video, 0, 0, 160, 90);
      var body = kresba.getImageData(0, 0, 160, 90).data;
      var svetlych = 0;
      var i;
      for (i = 0; i < body.length; i += 4) {
        if (body[i] + body[i + 1] + body[i + 2] > 60) { svetlych++; }
      }
      if (svetlych > 150) {
        napis('v-cteni', 'ano, obraz jde přečíst', 'ano');
      } else {
        napis('v-cteni', 'ne, aplikace vidí jen černou', 'ne');
      }
    } catch (e) {
      napis('v-cteni', 'ne, televize to nedovolí', 'ne');
    }
  }

  // Druhá cesta k obrazu: snímek obrazovky od systému (na různých
  // televizích se ta služba jmenuje různě, zkusí se obě).
  function zkusSnimek() {
    var parametry = {
      path: '/tmp/playlingo-zkouska.jpg',
      method: 'DISPLAY',
      format: 'JPG',
      width: 960,
      height: 540
    };
    sluzba('luna://com.webos.service.tv.capture/executeOneShot', parametry, function (a) {
      if (a && a.returnValue) {
        napis('v-snimek', 'ano (služba A)', 'ano');
        return;
      }
      sluzba('luna://com.webos.service.capture/executeOneShot', parametry, function (b) {
        if (b && b.returnValue) {
          napis('v-snimek', 'ano (služba B)', 'ano');
        } else {
          var duvod = (b && b.errorText) || (a && a.errorText) || 'zakázáno';
          napis('v-snimek', 'ne – ' + String(duvod).substring(0, 34), 'ne');
        }
      });
    });
  }

  function ukonciZkousku() {
    var i;
    for (i = 0; i < casovace.length; i++) {
      clearTimeout(casovace[i]);
      clearInterval(casovace[i]);
    }
    casovace = [];
    if (ukazka !== null) { clearInterval(ukazka); ukazka = null; }
    prijem = null;
    hledani++;
    zavri(spojeni);
    spojeni = null;
    hlaseni('');
    el('pruh').style.display = '';
    el('obraz').innerHTML = '';
    hraje = false;
    document.body.className = '';
  }

  // ---- Ovládání dálkovým ovladačem -----------------------------------

  function oznac(index) {
    var i;
    vybrane = (index + tlacitka.length) % tlacitka.length;
    for (i = 0; i < tlacitka.length; i++) {
      tlacitka[i].className = i === vybrane ? 'tl vybrane' : 'tl';
    }
  }

  function potvrd(index) {
    oznac(index);
    spustZkousku(tlacitka[vybrane].getAttribute('data-hdmi'));
  }

  document.addEventListener('keydown', function (udalost) {
    var klavesa = udalost.keyCode;
    var zpet = klavesa === 461 || klavesa === 27 || klavesa === 8;
    if (hraje) {
      if (zpet) { ukonciZkousku(); udalost.preventDefault(); }
      return;
    }
    if (klavesa === 37) { oznac(vybrane - 1); }
    else if (klavesa === 39) { oznac(vybrane + 1); }
    else if (klavesa === 13) { potvrd(vybrane); }
    else if (zpet) {
      if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); }
      else { window.close(); }
    }
  });

  // Ukazovátko ovladače Magic Remote.
  (function () {
    var i;
    function pripoj(index) {
      tlacitka[index].addEventListener('mouseover', function () { if (!hraje) { oznac(index); } });
      tlacitka[index].addEventListener('click', function () { if (!hraje) { potvrd(index); } });
    }
    for (i = 0; i < tlacitka.length; i++) { pripoj(i); }
  })();

  oznac(0);
  zjistiTelevizi();

  document.addEventListener('webOSRelaunch', function (udalost) { podleParametru(udalost.detail); });
  podleParametru(window.PalmSystem && window.PalmSystem.launchParams);
})();
