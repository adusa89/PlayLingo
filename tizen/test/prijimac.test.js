/*
 * Zkouška aplikace pro televizi bez televize: `node tizen/test/prijimac.test.js`
 *
 * Pustí společný app.js s platforma.js pro Samsung (tizen/app) i pro LG (webos/obchod/app)
 * v napodobeném prohlížeči (bez knihoven) a ověří: televize najde PlayLingo v síti, ohlásí se
 * platformou a verzí (zpráva „pripojeno“), ukáže obyčejný text i řádky `casti`, použije vzhled pruhu
 * z Androidu (pole `vzhled`) a odpoví „hotovo“,
 * a pošle svůj záznam událostí (zpráva „zaznam“, bez textu titulků).
 * Na Samsungu navíc, že se obraz ze vstupu HDMI pustí přes tizen.tvwindow pod stránku.
 */
'use strict';
var fs = require('fs');
var path = require('path');
var vm = require('vm');
var assert = require('assert');

var ROOT = path.join(__dirname, '..', '..');

function prvek(id) {
  var e = {
    id: id, style: {}, className: '', textContent: '', innerHTML: '', children: [], posluchaci: {},
    offsetHeight: 150,
    getAttribute: function (k) { return (e.atributy || {})[k] || null; },
    setAttribute: function (k, v) { e.atributy = e.atributy || {}; e.atributy[k] = v; },
    appendChild: function (c) { e.children.push(c); return c; },
    addEventListener: function (n, f) { (e.posluchaci[n] = e.posluchaci[n] || []).push(f); }
  };
  Object.defineProperty(e, 'textContent', {
    get: function () { return e._text !== undefined ? e._text : e.children.map(function (c) { return c.textContent; }).join(''); },
    set: function (v) { e._text = v; if (v === '') { e._text = undefined; e.children = []; } }
  });
  return e;
}

function spust(platforma) {
  var prvky = {};
  var tlacitka = [1, 2, 3, 4].map(function (n) { var t = prvek('hdmi' + n); t.setAttribute('data-akce', 'hdmi'); t.setAttribute('data-hdmi', String(n)); return t; })
    .concat(['ukazka', 'napoveda', 'jazyk', 'konec'].map(function (a) { var t = prvek(a); t.setAttribute('data-akce', a); return t; }));
  var klavesy = [];
  var odeslano = [];
  var spojeni = [];
  var tvwindow = [];
  var casovace = [];
  var document = {
    documentElement: prvek('html'),
    body: prvek('body'),
    hidden: false,
    getElementById: function (id) { return prvky[id] || (prvky[id] = prvek(id)); },
    querySelectorAll: function (s) { return s === '#domu .tl' ? tlacitka : []; },
    createElement: function (t) { return prvek(t); },
    createTextNode: function (t) { var e = prvek('#text'); e.textContent = t; return e; },
    addEventListener: function (n, f) { if (n === 'keydown') { klavesy.push(f); } }
  };
  function WebSocket(adresa) {
    var ws = this;
    ws.adresa = adresa;
    ws.send = function (z) { odeslano.push(JSON.parse(z)); };
    ws.close = function () {};
    spojeni.push(ws);
    setTimeout(function () {
      if (adresa === 'ws://192.168.1.20:8787') { ws.onmessage && ws.onmessage({ data: '{"typ":"vitej"}' }); }
      else if (ws.onerror) { ws.onerror(); }
    }, 1);
  }
  var okno = {
    WebSocket: WebSocket,
    location: { hash: '' },
    localStorage: { data: {}, getItem: function (k) { return this.data[k] || null; }, setItem: function (k, v) { this.data[k] = v; } },
    setTimeout: function (f, ms) { var t = setTimeout(f, Math.min(ms, 20)); casovace.push(t); return t; },
    clearTimeout: clearTimeout, setInterval: function () { return 0; }, clearInterval: function () {},
    requestAnimationFrame: function (f) { setTimeout(f, 0); },
    close: function () {}
  };
  if (platforma === 'tizen') {
    okno.tizen = {
      systeminfo: {
        getPropertyValue: function (n, ok) {
          var v = {
            BUILD: { model: 'UE55TU7172', buildVersion: 'T-NKLAKUC' },
            LOCALE: { language: 'cs_CZ' },
            ETHERNET_NETWORK: { ipAddress: '' },
            WIFI_NETWORK: { ipAddress: '192.168.1.10' },
            VIDEOSOURCE: { connected: [{ type: 'TV', number: 1 }, { type: 'HDMI', number: 1 }, { type: 'HDMI', number: 2 }] }
          }[n];
          setTimeout(function () { ok(v); }, 1);
        },
        getCapability: function () { return '5.5'; }
      },
      tvwindow: {
        setSource: function (zdroj, ok) { tvwindow.push('setSource HDMI' + zdroj.number); setTimeout(ok, 1); },
        show: function (ok, chyba, obdelnik, okno, vrstva) { tvwindow.push('show ' + vrstva); ok(); },
        hide: function (ok) { tvwindow.push('hide'); ok(); }
      },
      application: { getCurrentApplication: function () { return { exit: function () {}, getRequestedAppControl: function () { return null; } }; } }
    };
  } else {
    // LG: služby televize přes PalmServiceBridge
    okno.PalmServiceBridge = function () {
      var most = this;
      most.call = function (adresa) {
        var o = /getStatus/i.test(adresa) ? { returnValue: true, wifi: { ipAddress: '192.168.1.10' } }
          : /getSystemInfo/.test(adresa) ? { returnValue: true, modelName: 'OLED55B7', sdkVersion: '3.4', firmwareVersion: '05.00' }
          : { returnValue: true, settings: { localeInfo: { locales: { UI: 'en-GB' } } } };
        setTimeout(function () { most.onservicecallback(JSON.stringify(o)); }, 1);
      };
    };
  }
  okno.window = okno;
  okno.document = document;
  okno.navigator = { language: 'en-US' };
  var kontext = vm.createContext(okno);
  var slozka = platforma === 'tizen' ? path.join(ROOT, 'tizen', 'app') : path.join(ROOT, 'webos', 'obchod', 'app');
  ['platforma.js', 'app.js'].forEach(function (f) {
    vm.runInContext(fs.readFileSync(path.join(slozka, f), 'utf8'), kontext, { filename: f });
  });
  return {
    prvky: prvky, odeslano: odeslano, tvwindow: tvwindow, document: document, spojeni: spojeni,
    stiskni: function (kod) { klavesy.forEach(function (f) { f({ keyCode: kod, preventDefault: function () {} }); }); }
  };
}

function pockej(podminka, hotovo, popis) {
  var konec = Date.now() + 5000;
  (function znovu() {
    if (podminka()) { hotovo(); return; }
    if (Date.now() > konec) { throw new Error('nedočkal jsem se: ' + popis); }
    setTimeout(znovu, 5);
  })();
}

function zkus(platforma, verze, dalsi) {
  var tv = spust(platforma);
  // úvodní obrazovka, HDMI 1 je vybrané – OK spustí hru
  assert.strictEqual(tv.document.body.className, 's-domu');
  setTimeout(function () {
    tv.stiskni(13);
    assert.strictEqual(tv.document.documentElement.className, 's-hra', 'při hře je průhledná celá stránka');
    pockej(function () { return tv.odeslano.length > 0; }, function () {
      var p = tv.odeslano[0];
      assert.strictEqual(p.typ, 'pripojeno');
      assert.strictEqual(p.platforma, platforma);
      assert.strictEqual(p.verze, verze);
      assert.strictEqual(p.hdmi, 1);
      if (platforma === 'tizen') {
        assert.deepStrictEqual(tv.tvwindow.slice(-2), ['setSource HDMI1', 'show BEHIND']);
        assert.strictEqual(p.televize, 'UE55TU7172');
      } else {
        assert.strictEqual(tv.prvky.obraz.children[0].children[0].atributy.src, 'ext://hdmi:1');
        assert.strictEqual(p.televize, 'OLED55B7');
      }
      // zprávy z Androidu: obyčejný text, pak řádky casti (jméno v barvě, volby)
      var ws = tv.spojeni.filter(function (w) { return w.adresa === 'ws://192.168.1.20:8787'; }).pop();
      // s polem vzhled (od PlayLinga 0.86): průhlednost, písmo a pruh nad anglickými titulky
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 1, text: 'Počkej tady.', vzhled: {
        pozadi: 40, pismo: 44, barva: '#FFE066', poloha: 'nad', sirka: 0.84, cteni: [0.74, 0.98], text: [0.85, 0.9, 0.3, 0.7] } }) });
      assert.strictEqual(tv.prvky.titulek.textContent, 'Počkej tady.');
      assert.strictEqual(tv.prvky.pruh.style.display, 'flex');
      assert.strictEqual(tv.prvky.pruh.style.background, 'rgba(0,0,0,0.4)');
      assert.strictEqual(tv.prvky.titulek.style.fontSize, '44px');
      assert.strictEqual(tv.prvky.titulek.style.color, '#FFE066');
      // výška pruhu 150 px nad oblast čtení (0,74 × 1080 = 799) → nahoře 643 px, ne dole
      assert.strictEqual(tv.prvky.pruh.style.top, Math.round((0.74 - 0.006 - 150 / 1080) * 1080) + 'px');
      assert.strictEqual(tv.prvky.pruh.style.bottom, 'auto');
      // „tesne“: pruh těsně nad anglický titulek (0,85 × 1080), ne nad celou oblast čtení
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 9, text: 'Jo.', vzhled: { pozadi: 100, pismo: 50, poloha: 'tesne', cteni: [0.74, 0.98], text: [0.85, 0.9] } }) });
      assert.strictEqual(tv.prvky.pruh.style.top, Math.round((0.85 - 0.006 - 150 / 1080) * 1080) + 'px');
      ws.onmessage({ data: JSON.stringify({ typ: 'ukaz', id: 2, text: 'Mira: Ahoj\n• Ano', casti: [
        { jmeno: 'Mira', barva: '#F2C14E', text: 'Ahoj' }, { volba: true, text: 'Ano' }, { jmeno: 'X', barva: 'red;x', text: 'B' }] }) });
      var radky = tv.prvky.titulek.children;
      assert.strictEqual(radky.length, 3);
      assert.strictEqual(radky[0].children[0].style.color, '#F2C14E');
      assert.strictEqual(radky[1].className, 'volby');
      assert.strictEqual(radky[2].children[0].style.color, undefined, 'cizí barva se do stylu nedostane');
      // bez pole vzhled (starší PlayLingo) je pruh zase výchozí: černý a dole
      assert.strictEqual(tv.prvky.pruh.style.background, '');
      assert.strictEqual(tv.prvky.pruh.style.top, '');
      ws.onmessage({ data: '{"typ":"skryj","id":3}' });
      assert.strictEqual(tv.prvky.pruh.style.display, 'none');
      // záznam událostí: hned po ohlášení odešlo, co se nasbíralo (start, hledání, připojení)
      var zaznam1 = tv.odeslano.filter(function (z) { return z.typ === 'zaznam'; });
      assert.ok(zaznam1.length >= 1, 'po připojení odešel záznam');
      var radky1 = [].concat.apply([], zaznam1.map(function (z) { return z.radky; }));
      assert.ok(radky1.some(function (r) { return r.indexOf('start PlayLingo ' + platforma + ' ' + verze) >= 0; }), 'záznam má start s verzí');
      assert.ok(radky1.some(function (r) { return r.indexOf('připojeno k ws://192.168.1.20:8787') >= 0; }), 'záznam má připojení');
      pockej(function () { return tv.odeslano.filter(function (z) { return z.typ === 'hotovo'; }).length === 4; }, function () {
        // spojení spadne → televize se připojí znovu a pošle jen nové řádky, bez textu titulků
        var pred = tv.odeslano.length;
        ws.onclose();
        pockej(function () { return tv.odeslano.slice(pred).some(function (z) { return z.typ === 'zaznam'; }); }, function () {
          var radky2 = [].concat.apply([], tv.odeslano.slice(pred).filter(function (z) { return z.typ === 'zaznam'; }).map(function (z) { return z.radky; }));
          assert.ok(radky2.some(function (r) { return /ukaž #1: 12 znaků/.test(r); }), 'záznam má délku titulku');
          assert.ok(radky2.some(function (r) { return /spojení se ztratilo/.test(r); }), 'záznam má ztrátu spojení');
          assert.ok(!radky2.some(function (r) { return /start PlayLingo/.test(r); }), 'staré řádky se neposílají znovu');
          assert.ok(!radky1.concat(radky2).some(function (r) { return /Počkej|Ahoj/.test(r); }), 'text titulků do záznamu nejde');
          // Zpět vrátí úvodní obrazovku a na Samsungu schová obraz ze vstupu
          tv.stiskni(platforma === 'tizen' ? 10009 : 461);
          assert.strictEqual(tv.document.body.className, 's-domu');
          if (platforma === 'tizen') { assert.strictEqual(tv.tvwindow[tv.tvwindow.length - 1], 'hide'); }
          dalsi(tv);
        }, platforma + ': záznam po novém připojení');
      }, platforma + ': hotovo');    }, platforma + ': ohlášení');
  }, 30);
}

var vysledky = [];
function hotovo(platforma) {
  vysledky.push(platforma);
  console.log('OK: ' + platforma);
  if (vysledky.length === 2) { console.log('Aplikace pro televizi: LG i Samsung v pořádku.'); process.exit(0); }
}

['tizen', 'webos'].forEach(function (platforma) {
  var verzeSoubor = platforma === 'tizen'
    ? /version="([0-9.]+)"/.exec(fs.readFileSync(path.join(ROOT, 'tizen', 'app', 'config.xml'), 'utf8').split('<widget')[1])[1]
    : JSON.parse(fs.readFileSync(path.join(ROOT, 'webos', 'obchod', 'app', 'appinfo.json'), 'utf8')).version;
  zkus(platforma, verzeSoubor, function (tv) {
    hotovo(platforma);
  });
});
