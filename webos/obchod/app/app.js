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
      popis: 'Čte a překládá PlayLingo na Androidu ve stejné síti',
      otazka: 'Vstup s PlayStationem',
      tl_vzhled: 'Vzhled titulků',
      tl_ukazka: 'Ukázka',
      tl_napoveda: 'Nápověda',
      tl_jazyk: 'Jazyk: čeština',
      tl_zpet: 'Zpět',
      tl_konec: 'Ukončit',
      ovladani: '◀ ▶ ▲ ▼ výběr · OK potvrdit · Zpět ukončit',
      st_hledam: 'hledám v síti…',
      st_pripojeno: 'připojeno',
      st_vstup: 'nejde otevřít',
      st_sit: 'TV bez sítě',
      st_ok: 'vzhled titulků',
      uk_stitek: 'Ukázka',
      uk_popis: 'Hra ukazuje titulky anglicky. PlayLingo je přečte, přeloží a překlad ukáže v černém pruhu dole.',
      np_nadpis: 'Jak to funguje',
      np_1: 'Do telefonu nebo boxu s Androidem nainstalujte PlayLingo: github.com/adusa89/PlayLingo',
      np_2: 'Podle průvodce v ní spárujte PlayStation 5. Zařízení musí být ve stejné domácí síti jako televize.',
      np_3: 'V PlayLingu na Androidu zvolte Nastavení → Typ TV podle této televize a spusťte překlad.',
      np_4: 'Tady v televizi vyberte vstup HDMI s PlayStationem. Televize si PlayLingo v síti najde sama.',
      np_5: 'Během hry: OK otevře vzhled titulků (pozadí, písmo, poloha, originál). Totéž je v nabídce pod Vzhled titulků.',
      np_sony: 'Aplikace není produktem společnosti Sony Interactive Entertainment. „PlayStation“ a „PS5“ jsou jejich ochranné známky.',
      pn_nadpis: 'Vzhled titulků',
      pn_pruhlednost: 'Pozadí pruhu',
      pn_velikost: 'Velikost písma',
      pn_poloha: 'Poloha pruhu',
      pn_original: 'Ukazovat i originál',
      pn_podle_hry: 'Podle hry',
      pn_polohy: ['Přes titulky', 'Těsně nad titulkem', 'Nad oblastí čtení', 'Nahoře'],
      pn_vlastni: 'Vlastní (z telefonu)',
      pn_ano: 'Zapnuto',
      pn_ne: 'Vypnuto',
      pn_ovladani: '▲▼ volba · ◀▶ změna · Zpět zavře. Ostatní nastavení je v PlayLingu v telefonu.',
      pn_ukazka: 'Takhle bude vypadat překlad titulků.',
      pn_ukazka_orig: 'This is how the subtitle translation will look.'
    },
    en: {
      nadpis: 'Translated game subtitles',
      popis: 'Reading and translating: PlayLingo on Android in the same network',
      otazka: 'PlayStation input',
      tl_vzhled: 'Subtitle look',
      tl_ukazka: 'Demo',
      tl_napoveda: 'Help',
      tl_jazyk: 'Language: English',
      tl_zpet: 'Back',
      tl_konec: 'Exit',
      ovladani: '◀ ▶ ▲ ▼ select · OK confirm · Back exit',
      st_hledam: 'searching the network…',
      st_pripojeno: 'connected',
      st_vstup: 'cannot open',
      st_sit: 'TV offline',
      st_ok: 'subtitle look',
      uk_stitek: 'Demo',
      uk_popis: 'The game shows subtitles in English. PlayLingo reads them, translates them and shows ' +
        'the translation in the black bar below (here: Czech).',
      np_nadpis: 'How it works',
      np_1: 'Install PlayLingo on an Android phone or box: github.com/adusa89/PlayLingo',
      np_2: 'Follow its guide to pair your PlayStation 5. The device must be in the same home network as the TV.',
      np_3: 'In PlayLingo on Android, choose Settings → TV type matching this TV and start translating.',
      np_4: 'Here on the TV, choose the HDMI input of your PlayStation. The TV finds PlayLingo in the network by itself.',
      np_5: 'While playing: OK opens the subtitle look (background, text, position, original). The same is in the menu under Subtitle look.',
      np_sony: 'This app is not a product of Sony Interactive Entertainment. “PlayStation” and “PS5” are their trademarks.',
      pn_nadpis: 'Subtitle look',
      pn_pruhlednost: 'Bar background',
      pn_velikost: 'Text size',
      pn_poloha: 'Bar position',
      pn_original: 'Show the original too',
      pn_podle_hry: 'Like the game',
      pn_polohy: ['Over the subtitles', 'Right above the subtitle', 'Above the reading area', 'At the top'],
      pn_vlastni: 'Custom (from the phone)',
      pn_ano: 'On',
      pn_ne: 'Off',
      pn_ovladani: '▲▼ choose · ◀▶ change · Back closes. Everything else is in PlayLingo on the phone.',
      pn_ukazka: 'This is how the translation will look.',
      pn_ukazka_orig: 'This is how the subtitle translation will look.'
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

  // ---- Záznam událostí (pro vývojáře) ----------------------------------
  // Posledních ZAZNAM_MAX řádků toho, co se v televizi dělo (start, hledání, spojení, chyby).
  // Text titulků se nezapisuje, jen délka. Po připojení se pošle do PlayLinga na Androidu
  // zprávou {"typ":"zaznam","radky":[…]} a pak po dávkách (viz TvSender.kt, TvLog.kt);
  // starší PlayLingo na Androidu ji jen zahodí.
  var ZAZNAM_MAX = 300;
  var ZAZNAM_DAVKA = 25;
  var zaznam = [];
  var zaznamCelkem = 0;     // kolik řádků kdy vzniklo
  var zaznamOdeslano = 0;   // kolik z nich už odešlo

  function dvojmistne(n) { return (n < 10 ? '0' : '') + n; }

  var zaznamPosledni = [];  // poslední různé texty – hledání se opakuje dokola, zapíše se jen počet
  var zaznamOpakovani = 0;

  function pridejRadek(text) {
    var d = new Date();
    zaznam.push(dvojmistne(d.getHours()) + ':' + dvojmistne(d.getMinutes()) + ':' + dvojmistne(d.getSeconds()) + ' ' + text);
    if (zaznam.length > ZAZNAM_MAX) { zaznam.shift(); }
    zaznamCelkem++;
  }

  function zapis(text) {
    text = String(text).replace(/[\r\n]+/g, ' ').substring(0, 200);
    if (zaznamPosledni.indexOf(text) >= 0) { zaznamOpakovani++; return; }
    if (zaznamOpakovani) { pridejRadek('(předchozí řádky se ' + zaznamOpakovani + '× opakovaly)'); zaznamOpakovani = 0; }
    zaznamPosledni.push(text);
    if (zaznamPosledni.length > 3) { zaznamPosledni.shift(); }
    pridejRadek(text);
  }

  // Pošle řádky, které ještě neodešly (nejvýš celý buffer), po dávkách ZAZNAM_DAVKA.
  function posliZaznam(ws) {
    var nove, radky, i;
    if (!ws) { return; }
    nove = Math.min(zaznam.length, zaznamCelkem - zaznamOdeslano);
    if (nove <= 0) { return; }
    radky = zaznam.slice(zaznam.length - nove);
    try {
      for (i = 0; i < radky.length; i += ZAZNAM_DAVKA) {
        ws.send(JSON.stringify({ typ: 'zaznam', radky: radky.slice(i, i + ZAZNAM_DAVKA) }));
      }
      zaznamOdeslano = zaznamCelkem;
    } catch (e) { /* spojení spadlo, pošle se příště */ }
  }

  window.onerror = function (zprava, soubor, radek) {
    zapis('CHYBA: ' + zprava + ' (' + String(soubor || '').split('/').pop() + ':' + radek + ')');
    return false;
  };

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
      zapis('televize: ' + model + ', ' + system);
    });
  }

  // ---- Obrazovky --------------------------------------------------------

  // ---- Stav při hře (od 1.5.0): body s tečkou jako Domů v PlayLingu na Androidu --------------
  // Vlastní řádky televize (vstup HDMI, spojení s PlayLingem) a pod nimi řádky z Androidu
  // (zpráva {"typ":"stav","radky":[{"barva":"ok|pozor|info","text":"…"}]}, od PlayLinga 0.97.14).
  // Když je všechno zelené, po chvíli zmizí; oranžový řádek zůstává, dokud se to nespraví.
  var stav = { vstupChyba: false, bezSite: false, android: [] };
  var stavPodpis = '';
  var stavCasovac = null;

  function stavRadky() {
    var r = [];
    if (!prijem) { return r; }
    r.push({ barva: stav.vstupChyba ? 'pozor' : 'ok', text: 'HDMI ' + prijem.hdmi + (stav.vstupChyba ? ' · ' + T.st_vstup : '') });
    if (spojeni) { r.push({ barva: 'ok', text: 'PlayLingo · ' + T.st_pripojeno }); }
    else { r.push({ barva: 'pozor', text: stav.bezSite ? T.st_sit : 'PlayLingo · ' + T.st_hledam }); }
    if (spojeni) { r = r.concat(stav.android); }
    // nápověda k OK: tlačítko v rámečku místo šedé tečky (od 1.5.4)
    if (spojeni && nastaveni) { r.push({ barva: 'klavesa', text: T.st_ok }); }
    return r;
  }

  // ukazat: true = ukázat znovu; jinak se jen překreslí, když je vidět (nebo když se stav změnil)
  function stavKresli(ukazat) {
    var e = el('hlaseni'), r = stavRadky(), i, radek, tecka, text, pozor = false, podpis = '';
    for (i = 0; i < r.length; i++) { podpis += r[i].barva + r[i].text + '|'; if (r[i].barva === 'pozor') { pozor = true; } }
    if (podpis !== stavPodpis) { stavPodpis = podpis; ukazat = true; }
    if (!r.length) { e.style.display = 'none'; return; }
    if (!ukazat && e.style.display !== 'block') { return; }
    e.textContent = '';
    for (i = 0; i < r.length; i++) {
      radek = document.createElement('div');
      radek.className = 'st-radek';
      tecka = document.createElement('span');
      if (r[i].barva === 'klavesa') { tecka.className = 'klavesa'; tecka.textContent = 'OK'; }
      else { tecka.className = 'tecka ' + r[i].barva; }
      text = document.createElement('span');
      text.textContent = r[i].text;
      radek.appendChild(tecka);
      radek.appendChild(text);
      e.appendChild(radek);
    }
    e.style.display = 'block';
    if (stavCasovac) { clearTimeout(stavCasovac); stavCasovac = null; }
    // při otevřené nabídce vzhledu (OK) zůstává stav vidět, dokud se nabídka nezavře
    if (!pozor && panelRadek < 0) { stavCasovac = setTimeout(function () { el('hlaseni').style.display = 'none'; }, 8000); }
  }

  function stavSkryj() {
    if (stavCasovac) { clearTimeout(stavCasovac); stavCasovac = null; }
    el('hlaseni').style.display = 'none';
  }

  function prectiStav(z) {
    var out = [], i, b, rr = z.radky;
    if (!rr || !rr.length) { return out; }
    for (i = 0; i < rr.length && i < 6; i++) {
      if (!rr[i] || typeof rr[i].text !== 'string') { continue; }
      b = rr[i].barva === 'ok' || rr[i].barva === 'pozor' ? rr[i].barva : 'info';
      out.push({ barva: b, text: rr[i].text.substring(0, 80) });
    }
    return out;
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

  function cislo(x, od, az, vychozi) {
    x = Number(x);
    return isFinite(x) ? Math.max(od, Math.min(az, x)) : vychozi;
  }

  // Kam položit pruh nad oblast čtení / na pevné místo, aby nezakryl anglické titulky, které se
  // čtou (stejná pravidla jako ScreenLayout.barTop na Androidu). Vše jako zlomky výšky obrazu.
  function mimoCteni(chci, h, cteni) {
    var mezera = 0.006, nad, pod;
    if (chci + h > cteni[0] - mezera && chci < cteni[1] + mezera) {
      nad = cteni[0] - mezera - h;
      pod = cteni[1] + mezera;
      if (nad >= 0 && (chci + h / 2 <= (cteni[0] + cteni[1]) / 2 || pod + h > 1)) { chci = nad; }
      else if (pod + h <= 1) { chci = pod; }
      else { chci = Math.max(0, nad); }
    }
    return Math.max(0, Math.min(Math.max(0, 1 - h), chci));
  }

  // Vzhled a poloha pruhu podle Nastavení v PlayLingu na Androidu (pole vzhled, viz core/TvLook.kt):
  // průhlednost, písmo, barva a poloha přes titulky / těsně nad titulkem / nad oblastí čtení / na pevném místě.
  // Bez pole vzhled (starší PlayLingo) zůstane pruh jako dřív – černý, dole přes celou šířku.
  // Barva jména z částí titulku (casti) pro řádek originálu – stejné jméno, stejná barva.
  function barvaPodleJmena(casti, jmeno) {
    var i, k = String(jmeno).toLowerCase();
    if (!casti || !casti.length) { return null; }
    for (i = 0; i < casti.length; i++) {
      if (casti[i] && casti[i].jmeno && String(casti[i].jmeno).toLowerCase() === k) { return barvaJmena(casti[i].barva); }
    }
    return null;
  }

  // Originál pod překladem po řádcích; jméno mluvčího v barvě jako v překladu (od 1.5.2).
  function puvodniRadky(puvodni, text, casti) {
    var radky = String(text).split('\n'), i, m, radek, kus, b;
    for (i = 0; i < radky.length; i++) {
      radek = document.createElement('div');
      m = /^([^:]{1,40}):\s*(.*)$/.exec(radky[i]);
      b = m ? barvaPodleJmena(casti, m[1]) : null;
      if (m && b) {
        kus = document.createElement('span');
        kus.className = 'jmeno barevne';
        kus.style.color = b;
        kus.textContent = m[1] + ': ';
        radek.appendChild(kus);
        radek.appendChild(document.createTextNode(m[2]));
      } else {
        radek.textContent = radky[i];
      }
      puvodni.appendChild(radek);
    }
  }

  function vzhledPruhu(v, casti) {
    var p = el('pruh'), t = el('titulek'), s = p.style;
    var W = 1920, H = 1080;
    var cteni, text, pres, maxW, w, h, top, stred, puvodni, x;
    s.left = ''; s.top = ''; s.bottom = ''; s.width = ''; s.maxWidth = ''; s.minHeight = ''; s.background = ''; s.borderRadius = '';
    t.style.fontSize = ''; t.style.color = ''; t.style.fontWeight = ''; t.style.padding = ''; t.style.textShadow = ''; t.style.textAlign = ''; t.style.lineHeight = '';
    if (!v || typeof v !== 'object' || p.style.display === 'none') { return; }
    if (typeof v.original === 'string' && v.original) {
      puvodni = document.createElement('div');
      puvodni.className = 'puvodni';
      puvodniRadky(puvodni, v.original, casti);
      t.appendChild(puvodni);
    }
    s.background = 'rgba(0,0,0,' + (cislo(v.pozadi, 0, 100, 100) / 100) + ')';
    // průhledný pruh (pod 60 %, od 1.5.7 až 0 %): černý obrys písma, ať je text čitelný i na světlém obrazu
    if (cislo(v.pozadi, 0, 100, 100) < 60) { t.style.textShadow = '0 0 3px #000000, 0 0 6px #000000, 0 2px 4px #000000'; }
    // zaoblené rohy jako na Android TV (od 1.5.2)
    s.borderRadius = '18px';
    t.style.fontSize = cislo(v.pismo, 16, 120, 50) + 'px';
    if (/^#[0-9A-Fa-f]{6}$/.test(String(v.barva || ''))) { t.style.color = v.barva; }
    if (v.tucne === true) { t.style.fontWeight = 'bold'; }
    // okraje podle velikosti písma – menší písmo = nižší pruh (od 1.5.3)
    // okraj nad a pod textem podle volby 0–3 v telefonu (od 1.5.4; bez ní 1)
    x = [0.04, 0.16, 0.32, 0.5][Math.round(cislo(v.okraj, 0, 3, 1))];
    t.style.padding = x + 'em 0.8em ' + (x + 0.04) + 'em';
    cteni = [cislo(v.cteni && v.cteni[0], 0, 1, 0.74), cislo(v.cteni && v.cteni[1], 0, 1, 0.98)];
    text = null;
    if (v.text && v.text.length >= 2) {
      text = [cislo(v.text[0], 0, 1, 0), cislo(v.text[1], 0, 1, 0), cislo(v.text[2], 0, 1, -1), cislo(v.text[3], 0, 1, -1)];
      if (!(text[1] > text[0])) { text = null; }
    }
    pres = v.poloha === 'pres';
    // šířka podle textu (ne přes celou obrazovku), nejvýš podle nastavení
    maxW = Math.round(W * cislo(v.sirka, 0.2, 1, 0.84));
    s.bottom = 'auto'; s.top = '0px'; s.left = '0px'; s.width = 'auto'; s.maxWidth = maxW + 'px';
    if (pres) {
      // přes titulky: pruh aspoň tak vysoký, aby zakryl anglický text, nejvýš čtvrtina obrazovky
      x = text ? text[1] - text[0] : cteni[1] - cteni[0];
      s.minHeight = Math.min(Math.round(Math.max(0.02, Math.min(1, x + 0.024)) * H), H / 4) + 'px';
    } else {
      s.minHeight = '0px';
    }
    // zaokrouhlit nahoru: o zlomek pixelu užší pruh by zalomil poslední slovo na další řádek
    w = p.getBoundingClientRect ? Math.ceil(p.getBoundingClientRect().width) : p.offsetWidth;
    w = w || maxW;
    if (pres && text && text[3] > text[2]) { w = Math.max(w, Math.round((text[3] - text[2]) * W) + 40); }
    w = Math.max(160, Math.min(W, w));
    s.width = w + 'px';
    s.maxWidth = '';
    h = (p.offsetHeight || 150) / H;
    if (pres) {
      top = text ? (text[0] + text[1]) / 2 - h / 2 : (cteni[0] + cteni[1]) / 2 - h / 2;
      top = Math.max(0, Math.min(Math.max(0, 1 - h), top));
    } else if (v.poloha === 'tesne' && text) {
      // těsně nad nalezený anglický titulek (posouvá se s ním)
      top = Math.max(0, text[0] - 0.006 - h);
    } else if (v.poloha === 'stred') {
      top = mimoCteni(cislo(v.stred, 0, 1, 0.08) - h / 2, h, cteni);
    } else if (text && cteni[1] - cteni[0] > 0.6) {
      // automatika ještě hledá (čte se celá obrazovka) → těsně nad nalezený text
      top = Math.max(0, text[0] - 0.006 - h);
    } else {
      top = mimoCteni(cteni[0] - 0.006 - h, h, cteni);
    }
    // pevné místo (od 1.5.9): překlad menu hry hned vedle menu, řádky pod sebou zarovnané vlevo
    if (v.poloha === 'misto' && v.misto && v.misto.length >= 2) {
      t.style.textAlign = 'left';
      // řádek proti řádku (od 1.5.10): výška řádku = rozestup položek menu ve hře, písmo se jí přizpůsobí
      x = v.misto.length >= 3 ? cislo(v.misto[2], 0, 0.2, 0) * H : 0;
      if (x > 0) {
        t.style.lineHeight = Math.round(x) + 'px';
        t.style.fontSize = Math.min(cislo(v.pismo, 16, 120, 50), Math.round(x * 0.62)) + 'px';
        t.style.padding = '0 0.6em';
        w = Math.ceil(p.getBoundingClientRect ? p.getBoundingClientRect().width : p.offsetWidth) || w;
        h = (p.offsetHeight || 150) / H;
      }
      s.left = Math.round(Math.max(0, Math.min(W - w, cislo(v.misto[0], 0, 1, 0.3) * W))) + 'px';
      s.top = Math.round(Math.max(0, Math.min(H - h * H, cislo(v.misto[1], 0, 1, 0.2) * H - x * 0.25))) + 'px';
      return;
    }
    stred = pres && text && text[3] > text[2] ? (text[2] + text[3]) / 2 : 0.5;
    s.left = Math.round(Math.max(0, Math.min(W - w, stred * W - w / 2))) + 'px';
    s.top = Math.round(Math.max(0, Math.min(H - h * H, top * H))) + 'px';
  }

  // Pruh po řádcích (pole casti, viz TvSender.kt): jména v barvách mluvčích a volby odpovědí
  // jako seznam s odrážkami. Bez casti (starší PlayLingo na Androidu) jen obyčejný text.
  // vzhled = nastavení z Androidu (viz vzhledPruhu), bez něj výchozí pruh.
  function pruhCasti(casti, text, vzhled) {
    var t = el('titulek');
    var i, c, radek, kus, seznam = null;
    if (!casti || Object.prototype.toString.call(casti) !== '[object Array]' || !casti.length) {
      pruh(text);
      vzhledPruhu(vzhled, null);
      return;
    }
    t.textContent = '';
    for (i = 0; i < casti.length; i++) {
      c = casti[i] || {};
      radek = document.createElement('div');
      if (c.pokyn) {
        // pokyn pro hráče (GTA) – menší a šedý řádek, ne replika (od 1.5.5)
        seznam = null;
        radek.className = 'pokyn';
        radek.textContent = String(c.text || '');
        t.appendChild(radek);
        continue;
      }
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
    vzhledPruhu(vzhled, casti);
  }

  // ---- Úkol ve hře (od 1.5.11) ------------------------------------------
  // Řádek s úkolem (GTA VI dole pod titulky) má vlastní prvek #ukol, nezávislý na pruhu:
  // skrytí pruhu (skryj, vypršení) ho neschová a naopak. Zpráva z Androidu
  // {"typ":"ukol","text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":…,"pozadi":…,"barva":…,"casti":[…]}
  // – misto = obdélník anglického úkolu jako zlomky obrazu (jako vzhled.text), pismo v px na 1080 řádků,
  // pozadi 0–100 (100 = černé), casti = barevné kusy textu (barva null = výchozí). Prázdný text = schovat.
  var UKOL_MAX = 300;
  var ukolPodpis = '';   // co je naposledy v záznamu, ať se stejný úkol nezapisuje dokola

  function ukolSkryj() {
    var u = el('ukol');
    u.style.display = 'none';
    u.textContent = '';
    ukolPodpis = '';
  }

  function ukolUkaz(z) {
    var u = el('ukol'), s = u.style;
    var W = 1920, H = 1080;
    var text = String(z.text || '').substring(0, UKOL_MAX);
    var casti = z.casti, i, c, kus, zbyva, barva, m, cx, cy, maxW, w, h, pozadi, podpis;
    if (!/\S/.test(text)) {
      if (ukolPodpis) { zapis('úkol skryt'); }
      ukolSkryj();
      return;
    }
    u.textContent = '';
    if (casti && Object.prototype.toString.call(casti) === '[object Array]' && casti.length) {
      // barevné kusy jako span, celkem nejvýš UKOL_MAX znaků
      zbyva = UKOL_MAX;
      for (i = 0; i < casti.length && i < 40 && zbyva > 0; i++) {
        c = casti[i] || {};
        kus = document.createElement('span');
        kus.textContent = String(c.text || '').substring(0, zbyva);
        zbyva -= kus.textContent.length;
        if (barvaJmena(c.barva)) { kus.style.color = barvaJmena(c.barva); }
        u.appendChild(kus);
      }
    } else {
      casti = null;
      u.textContent = text;
    }
    pozadi = cislo(z.pozadi, 0, 100, 90);
    barva = barvaJmena(z.barva);
    s.color = barva || '#FFFFFF';
    s.background = 'rgba(0,0,0,' + (pozadi / 100) + ')';
    s.fontSize = cislo(z.pismo, 16, 120, 40) + 'px';
    // nejdřív změřit: šířka podle textu, nejvýš 90 % obrazovky
    maxW = Math.round(W * 0.9);
    s.left = '0px'; s.top = '0px'; s.width = 'auto'; s.maxWidth = maxW + 'px';
    s.display = 'block';
    w = u.getBoundingClientRect ? Math.ceil(u.getBoundingClientRect().width) : u.offsetWidth;
    w = Math.max(1, Math.min(maxW, w || maxW));
    h = u.offsetHeight || Math.round(cislo(z.pismo, 16, 120, 40) * 1.6);
    // střed obdélníku anglického úkolu; bez platného misto dole uprostřed
    m = z.misto;
    cx = 0.5; cy = 0.9;
    if (m && Object.prototype.toString.call(m) === '[object Array]' && m.length >= 4) {
      m = [cislo(m[0], 0, 1, -1), cislo(m[1], 0, 1, -1), cislo(m[2], 0, 1, -1), cislo(m[3], 0, 1, -1)];
      if (m[0] >= 0 && m[1] >= 0 && m[2] > m[0] && m[3] > m[1]) {
        cx = (m[0] + m[2]) / 2;
        cy = (m[1] + m[3]) / 2;
      }
    }
    s.left = Math.round(Math.max(0, Math.min(W - w, cx * W - w / 2))) + 'px';
    s.top = Math.round(Math.max(0, Math.min(H - h, cy * H - h / 2))) + 'px';
    // jen délka a počet kusů, ne text úkolu (zásada soukromí záznamu)
    podpis = (z.id !== undefined ? '#' + String(z.id).substring(0, 20) + ': ' : '') + text.length + ' znaků' +
      (casti ? ', ' + casti.length + ' částí' : '');
    if (podpis !== ukolPodpis) { zapis('úkol ' + podpis); ukolPodpis = podpis; }
  }

  // ---- Vzhled titulků z ovladače (od 1.4.0) -----------------------------
  // Všechno nastavení patří PlayLingu na Androidu. Televize dostane zprávou `nastaveni` pár voleb,
  // ukáže je v malé nabídce (OK při hře) a o změnu požádá zprávou `zmen`; Android ji ověří, uloží
  // a pošle zpět nové hodnoty (viz core/TvSettings.kt). Hodnoty jsou stejné jako v telefonu.
  var PRUHLEDNOSTI = [0, 20, 40, 60, 80, 90, 100];
  var VELIKOSTI = [0, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44, 46, 48];
  var POLOHY = ['pres', 'tesne', 'nad', 'nahore'];
  var nastaveni = null;
  var panelRadek = -1;
  var chciVzhled = false;   // Vzhled titulků z úvodní obrazovky: otevřít nabídku hned po připojení
  var posledni = null;
  var ukazkaVidet = false;

  function prectiNastaveni(z) {
    var n = {
      pruhlednost: cislo(z.pruhlednost, 0, 100, 100),
      velikost: z.podleHry === true ? 0 : cislo(z.velikost, 0, 120, 22),
      poloha: POLOHY.indexOf(z.poloha) >= 0 ? z.poloha : 'vlastni',
      original: z.original === true
    };
    return n;
  }

  // Poslední vzhled z Androidu upravený podle voleb v nabídce (náhled hned po změně).
  function nahled(ukazka) {
    var v = {}, k, zdroj = (posledni && posledni.vzhled) || { pozadi: 100, pismo: 50, poloha: 'nad', cteni: [0.74, 0.98], sirka: 0.84 };
    for (k in zdroj) { if (Object.prototype.hasOwnProperty.call(zdroj, k)) { v[k] = zdroj[k]; } }
    if (!nastaveni) { return v; }
    v.pozadi = nastaveni.pruhlednost;
    if (nastaveni.velikost > 0) { v.pismo = Math.round(nastaveni.velikost * 50 / 22); }
    if (nastaveni.poloha === 'nahore') { v.poloha = 'stred'; v.stred = 0.08; }
    else if (nastaveni.poloha !== 'vlastni') { v.poloha = nastaveni.poloha; }
    if (!nastaveni.original) { delete v.original; }
    else if (ukazka && !v.original) { v.original = T.pn_ukazka_orig; }
    return v;
  }

  function panelHodnota(i) {
    var n = nastaveni;
    if (i === 0) { return n.pruhlednost + ' %'; }
    // velikost jako číslo (stejné jako v telefonu), 0 = podle velikosti titulků ve hře
    if (i === 1) { return n.velikost > 0 ? String(n.velikost) : T.pn_podle_hry; }
    if (i === 2) { return POLOHY.indexOf(n.poloha) >= 0 ? T.pn_polohy[POLOHY.indexOf(n.poloha)] : T.pn_vlastni; }
    return n.original ? T.pn_ano : T.pn_ne;
  }

  function panelKresli() {
    var p = el('panel'), i, radek, popis, hodnota, nazvy;
    if (panelRadek < 0 || !nastaveni) { p.style.display = 'none'; return; }
    nazvy = [T.pn_pruhlednost, T.pn_velikost, T.pn_poloha, T.pn_original];
    p.textContent = '';
    radek = document.createElement('div');
    radek.className = 'pn-nadpis';
    radek.textContent = T.pn_nadpis;
    p.appendChild(radek);
    for (i = 0; i < nazvy.length; i++) {
      radek = document.createElement('div');
      radek.className = 'pn-radek' + (i === panelRadek ? ' vybrane' : '');
      popis = document.createElement('span');
      popis.className = 'pn-popis';
      popis.textContent = nazvy[i];
      hodnota = document.createElement('span');
      hodnota.className = 'pn-hodnota';
      hodnota.textContent = '◀ ' + panelHodnota(i) + ' ▶';
      radek.appendChild(popis);
      radek.appendChild(hodnota);
      p.appendChild(radek);
    }
    radek = document.createElement('div');
    radek.className = 'pn-ovladani';
    radek.textContent = T.pn_ovladani;
    p.appendChild(radek);
    p.style.display = 'block';
    // náhled: skutečný titulek, nebo ukázková věta, když zrovna žádný není
    if (!posledni || ukazkaVidet || el('pruh').style.display === 'none' ||
        (nastaveni.original && !(posledni.vzhled && posledni.vzhled.original))) {
      ukazkaVidet = true;
      pruhCasti(null, T.pn_ukazka, nahled(true));
    } else if (posledni) {
      pruhCasti(posledni.casti, posledni.text, nahled(false));
    }
  }

  function panelOtevri() {
    if (!nastaveni || !spojeni) { return; }
    panelRadek = 0;
    zapis('nabídka vzhledu otevřena');
    panelKresli();
    stavKresli(true);
  }

  function panelZavri() {
    if (panelRadek < 0) { return; }
    panelRadek = -1;
    el('panel').style.display = 'none';
    if (ukazkaVidet) { ukazkaVidet = false; pruh(null); }
    // stav zmizí s nabídkou; oranžový (něco chybí) zůstane
    if (stavPodpis.indexOf('pozor') < 0) { stavSkryj(); }
  }

  function dalsi(seznam, hodnota, krok) {
    var i = seznam.indexOf(hodnota);
    if (i < 0) { return seznam[0]; }
    return seznam[(i + krok + seznam.length) % seznam.length];
  }

  function panelZmen(krok) {
    var n = nastaveni, klic, hodnota;
    if (!n || !spojeni) { return; }
    if (panelRadek === 0) { n.pruhlednost = dalsi(PRUHLEDNOSTI, n.pruhlednost, krok); klic = 'pruhlednost'; hodnota = n.pruhlednost; }
    else if (panelRadek === 1) { n.velikost = dalsi(VELIKOSTI, n.velikost, krok); klic = 'velikost'; hodnota = n.velikost; }
    else if (panelRadek === 2) { n.poloha = dalsi(POLOHY, n.poloha, krok); klic = 'poloha'; hodnota = n.poloha; }
    else { n.original = !n.original; klic = 'original'; hodnota = n.original; }
    try { spojeni.send(JSON.stringify({ typ: 'zmen', klic: klic, hodnota: hodnota })); } catch (e) { zapis('změnu se nepodařilo poslat'); }
    zapis('změna vzhledu: ' + klic);
    panelKresli();
  }

  // Klávesy při hře: OK otevře nabídku; v ní šipky a Zpět. Vrací true, když klávesu vyřídila.
  function panelKlavesa(klavesa, zpet) {
    if (panelRadek < 0) {
      if (klavesa === 13 && nastaveni && spojeni) { panelOtevri(); return true; }
      return false;
    }
    if (zpet) { panelZavri(); }
    else if (klavesa === 38) { panelRadek = (panelRadek + 3) % 4; panelKresli(); }
    else if (klavesa === 40) { panelRadek = (panelRadek + 1) % 4; panelKresli(); }
    else if (klavesa === 37) { panelZmen(-1); }
    else if (klavesa === 39 || klavesa === 13) { panelZmen(1); }
    return true;
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
    stav = { vstupChyba: false, bezSite: false, android: [] };
    stavPodpis = '';
    stavSkryj();
    chciVzhled = false;
    panelZavri();
    pruh(null);
    ukolSkryj();
    P.skryjVstup();
    el('obraz').innerHTML = '';
    strana = kam;
    zapis('obrazovka: ' + kam);
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
      zapis('vstup HDMI ' + cislo + ' se nepodařilo otevřít');
      if (strana === 'hra') { stav.vstupChyba = true; stavKresli(true); }
    });
  }

  // bezVstupu: jen pro snímek obrazovky v počítači, kde žádný vstup HDMI není.
  function spustHru(cislo, pevna, bezVstupu) {
    prejdi('hra');
    prijem = { hdmi: cislo, pevna: pevna || null };
    pamet('hdmi', String(cislo));
    oznac(cislo - 1);
    zapis('hra: HDMI ' + cislo + (pevna ? ', pevná adresa ' + pevna : ''));
    stavKresli(true);
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
    zapis('připojeno k ' + adresa);
    if (!prijem.pevna) { pamet('titulky', adresa); }
    pruh(null);
    ukolSkryj();
    stav.bezSite = false;
    stavKresli(true);
    ws.onerror = null;
    ws.onmessage = function (udalost) {
      var z;
      try { z = JSON.parse(udalost.data); } catch (e) { return; }
      if (z.typ === 'nastaveni') {
        // volby vzhledu z Androidu (po připojení a po každé změně)
        nastaveni = prectiNastaveni(z);
        if (panelRadek >= 0) { panelKresli(); }
        // z nabídky Vzhled titulků na úvodní obrazovce: nabídka se otevře hned po připojení
        if (chciVzhled && panelRadek < 0) { chciVzhled = false; panelOtevri(); }
        stavKresli(false);
        return;
      }
      if (z.typ === 'stav') {
        // stav z Androidu (konzole, překlad, hra) – body s tečkou jako na Domů
        stav.android = prectiStav(z);
        stavKresli(false);
        return;
      }
      if (z.typ === 'ukol') {
        // úkol ve hře (od 1.5.11) – vlastní prvek, pruhu se netýká; bez odpovědi hotovo
        try { ukolUkaz(z); } catch (e) { zapis('úkol se nepodařilo ukázat'); ukolSkryj(); }
        return;
      }
      if (z.typ === 'ukaz') {
        // první titulek: zelený přehled už není potřeba (oranžový zůstane)
        if (stavPodpis.indexOf('pozor') < 0) { stavSkryj(); }
        posledni = { casti: z.casti, text: String(z.text || ''), vzhled: z.vzhled };
        ukazkaVidet = false;
        pruhCasti(z.casti, String(z.text || ''), panelRadek >= 0 ? nahled(false) : z.vzhled);
        // jen délka a počet řádků, ne text titulku
        zapis('ukaž #' + z.id + ': ' + String(z.text || '').length + ' znaků' +
          (z.casti && z.casti.length ? ', ' + z.casti.length + ' částí' : '') + (z.vzhled ? ', vzhled ' + String(z.vzhled.poloha || '?') + ' ' + String(z.vzhled.pozadi) + ' %' : ''));
      } else if (z.typ === 'skryj') {
        posledni = null;
        if (panelRadek >= 0) { ukazkaVidet = true; pruhCasti(null, T.pn_ukazka, nahled(true)); } else { pruh(null); }
        zapis('skryj #' + z.id);
      } else {
        return;
      }
      poVykresleni(function () {
        try { ws.send(JSON.stringify({ typ: 'hotovo', id: z.id })); } catch (e) { /* spojení mezitím spadlo */ }
      });
    };
    ws.onclose = function () {
      if (spojeni !== ws || !prijem) { return; }
      zapis('spojení se ztratilo, hledám znovu');
      spojeni = null;
      nastaveni = null;
      chciVzhled = false;   // nabídka by jinak vyskočila nečekaně až po dalším připojení
      stav.android = [];
      panelZavri();
      pruh(null);
      ukolSkryj();
      stavKresli(true);
      pozdeji(hledej, 2000);
    };
    // platforma a verze: podle nich průvodce na Androidu pozná, že je aplikace v televizi nainstalovaná
    try {
      ws.send(JSON.stringify({ typ: 'pripojeno', televize: udaje.model, system: udaje.system, hdmi: prijem.hdmi,
        aplikace: 'PlayLingo', platforma: P.platforma, verze: P.verze }));
    } catch (e) { zapis('ohlášení se nepodařilo poslat'); }
    // záznam: co se nasbíralo do teď (i z hledání), dál po dávkách (setInterval při startu)
    posliZaznam(ws);
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
          zapis('adresa televize neznámá (' + (ip || '-') + (odpovedela ? '' : ', systém neodpověděl') + ')');
          if (odpovedela) { stav.bezSite = true; stavKresli(false); }
          znovu();
          return;
        }
        zapis('hledám v síti ' + m[1] + 'x (televize .' + m[2] + ', port ' + PORT + ')');
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
          if (bezi === 0 && !fronta.length) { zapis('v síti nic nenalezeno, zkusím za 5 s'); znovu(); }
        }
        dalsi();
      });
    }
    if (prijem.pevna) { zapis('zkouším ' + prijem.pevna); jedna(prijem.pevna, 3000, znovu); return; }
    var minula = pamet('titulky');
    if (minula) {
      zapis('zkouším minulou adresu ' + minula);
      jedna(minula, 2000, function () { zapis('minulá adresa neodpověděla'); celaSit(); });
    } else { celaSit(); }
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
    else if (akce === 'vzhled') {
      // vzhled se mění při hře (náhled přes obraz): spustí naposledy použitý vstup a po připojení otevře nabídku
      spustHru(parseInt(pamet('hdmi'), 10) || 1);
      chciVzhled = true;
    }
    else if (akce === 'konec') { ukonci(); }
    else { prejdi('napoveda'); }
  }

  // Úplné ukončení (i hledání v síti). Jak, ví platforma.js.
  function ukonci() {
    prejdi('domu');
    P.ukonci();
  }

  // Domů jsou dvě řady: čtyři vstupy (0–3) a pod nimi pět menších tlačítek – Vzhled titulků (4),
  // Ukázka (5), Nápověda (6), Jazyk (7), Ukončit (8). Šipka nahoru/dolů jde na nejbližší tlačítko druhé řady.
  function sipka(klavesa) {
    var v = vybrane;
    if (klavesa === 37) { if (v !== 0 && v !== 4) { oznac(v - 1); } }
    else if (klavesa === 39) { if (v !== 3 && v !== 8) { oznac(v + 1); } }
    else if (klavesa === 40) { if (v < 4) { oznac(4 + Math.round(v * 4 / 3)); } }
    else if (klavesa === 38) { if (v >= 4) { oznac(Math.round((v - 4) * 3 / 4)); } }
  }

  document.addEventListener('keydown', function (udalost) {
    var klavesa = udalost.keyCode;
    var zpet = P.jeZpet(klavesa);
    if (strana === 'hra' && panelKlavesa(klavesa, zpet)) { udalost.preventDefault(); return; }
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
    // Při hře se tlačítko Zpět neukazuje (majitel: rušilo, svítilo po každém pohybu ukazovátka) – Zpět je
    // na ovladači. Se zapnutým ukazovátkem Magic Remote posílá televize OK jako kliknutí, ne jako klávesu 13:
    // kliknutí při hře proto dělá totéž co OK (otevře nabídku vzhledu, v ní mění vybranou volbu). Od 1.5.6.
    document.addEventListener('click', function () {
      if (strana !== 'hra') { return; }
      panelKlavesa(13, false);
    });
  })();

  // ---- Start --------------------------------------------------------------

  // Značka v adrese je jen pro snímky obrazovky a zkoušení v počítači:
  // index.html#cs, #en, #ukazka, #napoveda, #hra (jde spojit: #en-ukazka).
  var znacka = String(window.location.hash || '').replace('#', '').split('-');
  var vynuceny = znacka.indexOf('cs') >= 0 ? 'cs' : (znacka.indexOf('en') >= 0 ? 'en' : '');

  // Pořadí: značka v adrese (snímky), jazyk zvolený tlačítkem, jinak jazyk televize.
  var zvoleny = vynuceny || pamet('jazyk');
  zapis('start PlayLingo ' + P.platforma + ' ' + P.verze + ' · ' + String(navigator.userAgent || '?').substring(0, 120));
  // nové řádky záznamu jdou do Androidu po dávkách (mimo `casovace`, ty maže každé přepnutí obrazovky)
  setInterval(function () { posliZaznam(spojeni); }, 10000);
  nastavJazyk(zvoleny || navigator.language);
  zapis('jazyk: ' + jazyk + (zvoleny ? ' (zvolený)' : ''));
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
