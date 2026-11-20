# Zadání: překlad menu a UI hry (pro samostatnou session)

Stav: **navrženo 20. 11. 2026, nezačato.** Začít až po profilu Diablo IV. Tahle session má na starosti jen tohle;
vylepšování aplikace pro TV běží jinde – nesahat na Domů, Nastavení ani spojení s konzolí bez potřeby.

## Cíl

Hry bez češtiny mají anglická menu a UI (u Diabla IV hlavně popisy předmětů – tooltipy). PlayLingo dnes menu
**záměrně přeskakuje** (`GameScreen.isMenu`, `TextLogic.assemble`, čte se jen pás s titulky). Nově: když je na
obrazovce menu, přeložit ho a ukázat překlad tak, aby hráč dál ovládal originál.

## Rozhodnutí majitele

- **Varianta B (doporučená a schválená jako první):** vedle menu box s přeloženými položkami – řádky ve stejné
  výšce jako položky originálu, **vybraná položka zvýrazněná i v boxu** (hra ji v obraze odliší rámečkem /
  světlejším pozadím / barvou – poznat z obrazu). Přeložené položky se vybírat nedají, proto se jen ukazují.
- Varianta A (malý překlad hned vedle každé položky) – ne, v hustém UI se překrývá.
- Varianta C (poloprůhledné přepsání přímo přes položku, aby prosvítal výběr) – možná později jako volba;
  rizika: při posunu menu „ujíždí“, čeština je delší než angličtina.
- U Diabla IV má největší cenu překlad **tooltipu vybraného předmětu** (dlouhé texty s vlastnostmi).
- Menu se při čtení titulků **dál vynechává** – překlad menu je samostatný režim, ne titulek v pruhu.
- Zásady vzhledu z CLAUDE.md platí (stavové hlášky, „TV“, žádné tykání, nová volba = krátký název v Nastavení,
  popis do Nápovědy).

## Co už je k dispozici

- OCR vrací řádky s polohou (`OcrLine` left/top/right/bottom, `OcrBlock`) – `TranslatorService.toBlocks`.
- Detekce menu podle profilu hry: `GameProfile.menu` / `GameScreen.isMenu` (2+ přesné řádky z menu),
  profily v `core/GameProfiles.kt` a `core/GameProfilesMore.kt`.
- Snímek celé obrazovky: `StreamReader.still()` (šedý), barevný náhled `colorSnapshot` jen když je aplikace vidět.
- Překlad s pamětí: `TranslatorService.translate` + `TranslationCache`, slovník `Glossary` (jména a ulice zůstávají).
- Kreslení přes obraz: Android TV `SubtitleOverlay` (Usnadnění), LG/Samsung `webos/obchod/app/app.js` přes
  `core/TvSender.kt` (nové věci ve zprávách jen jako nepovinná pole, JS jen ES5 – Chromium 38).
- Ladění ze hry: záznam v síti (`/snimek.jpg`, oddíl „co aplikace četla v oblasti titulků“) a `ci/sbirej-hru.ps1`.

## Postup (návrh)

1. **Podklady:** majitel zahraje Diablo IV se zapnutým záznamem v síti, na PC běží `ci/sbirej-hru.ps1`.
   Z snímků zjistit rozložení menu, písmo, jak hra zvýrazňuje vybranou položku, kde je tooltip.
2. **Režim menu:** když `isMenu` (nebo hustý text) → OCR celé obrazovky (pomalejší, ale menu stojí;
   číst jen při změně otisku obrazu `FrameSignature`), položky přeložit (paměť, ať se nepřekládá dokola).
3. **Box s překladem** vedle menu na Android TV i v TV LG/Samsung (nové pole ve zprávě `ukaz`, televize
   ho položí vedle oblasti menu; když se nevejde, druhá strana obrazovky).
4. **Vybraná položka:** porovnat jas/barvu pozadí řádků (vybraný se liší) → zvýraznit v boxu.
5. **Tooltip předmětu** (Diablo IV): souvislý blok textu u vybraného předmětu → překlad do boxu.
6. Volba v Nastavení (krátký název, výchozí vypnuto), popis do Nápovědy, texty ve všech 10 jazycích.

## Aktuální úkol hry (přání majitele 20. 11. 2026)

U Diabla IV je vpravo pod mapou aktuální úkol – název a co dělat (snímek ze hry: „Hatred's Wake“ ·
„Speak with Lorath aboard the ship“). Statický text, dnes se nečte (čte se jen pás s titulky a stojící text
se schová jako nápis), ale hráč bez něj neví, kam jít. Návrh:

- V profilu hry pevná oblast úkolu (Diablo IV: vpravo pod mapou, zhruba x 0,79–1,0, y 0,26–0,38 – upřesnit
  ze snímků `zaznamy\hra-20261120-154048`), mimo oblast titulků.
- Číst jen při změně otisku té oblasti (úkol se mění zřídka → čtení nic nestojí), přeložit s pamětí,
  jména a místa (Lorath, Backwater) nechat podle profilu.
- Ukázat trvale a nenápadně, odděleně od pruhu s replikami: malé písmo pod originálem úkolu, nebo v rohu
  se stavem v TV LG (`stavKresli` v `app.js`) / na Android TV. Repliky a úkol se nemíchají.
- Volba v Nastavení (krátký název, např. „Překlad úkolu“), výchozí podle profilu hry.
- Pořadí: první kus téhle session (malý a hned užitečný), před boxem s menu a tooltipy.

## Zásada pro všechny „texty hry“ (majitel 20. 11. 2026, platí i pro GTA VI)

- Univerzální chování (jen pás s titulky) zůstává pro všechny hry; profil hry ho jen vylepšuje.
- I s vybranou hrou volba **„Rozsah čtení“: Jen titulky / Titulky a texty hry**. „Jen titulky“ = z profilu jen jména,
  rod, pojmy a přeskakování; žádné další oblasti obrazovky. „Titulky a texty hry“ = navíc oblasti z profilu
  (Diablo IV: úkol, menu, tooltipy; GTA VI: prompty pro hráče pod titulky, např. „Take your shot“).
- Oblasti a jejich vzhled patří do `GameProfile` (nové pole), ne natvrdo do kódu čtečky. Jedna volba v Nastavení
  pro všechny hry (krátký název), popis do Nápovědy.
- Texty hry se nemíchají s replikami v pruhu. GTA prompty odděluje hlavní session (0.97.24+), menu/úkol tahle.

## Rizika

- Čas čtení celé obrazovky na slabší TV (měřit `READ|ocr=…`); menu je statické, takže stačí číst při změně.
- Husté UI (ikony, čísla) – OCR šum; brát jen řádky s písmeny, ignorovat čísla/statistiky beze slov.
- Spojení s konzolí neměnit (viz Poučení z historie v CLAUDE.md).
- Ověřit umí jen majitel na skutečné hře – emulátor hru nemá.
