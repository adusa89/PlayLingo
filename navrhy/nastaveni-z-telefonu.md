# Nastavení Android TV z telefonu (návrh, schváleno majitelem 21. 11. 2026)

Cíl: PlayLingo v telefonu umí u Android TV totéž co u LG – projít celý průvodce a všechna nastavení
v telefonu – a televize se podle toho nastaví. Pro iPhone (a Android bez aplikace) stránka z televize.

## Rozhodnutí majitele

1. **Spárování PS5 proběhne v telefonu** a hotové spárování (`ps5_host`: adresa, klíč registrace,
   klíč relace, MAC – nic vázané na zařízení) se přenese do TV. Na skutečné PS5 ověří jen majitel;
   když přenesené spárování nepůjde, televize se dál umí spárovat sama jako dřív.
2. **Průběžně i později:** telefon je s TV spojený a každá změna se v TV projeví hned – v průvodci
   i kdykoli později (vzhled titulků, hra, DeepL…).
3. **Web pro iPhone:** průvodce + hlavní volby (jazyk, hra, DeepL klíč, přihlášení PlayStation,
   kód z PS5 – tady páruje TV, vzhled titulků). Méně častá nastavení dál v TV.

## Jak to bude fungovat

- **Televize (Android TV)** drží v domácí síti stránku/server nastavení (stejný základ jako
  `LoginServer`, stálý klíč spárování s telefonem, ochrana proti zkoušení klíčů). V TV: ⚙ → Obecné →
  „Nastavit z telefonu“ a v prvním kroku průvodce čtvereček.
- **Čtvereček** vede na stránku z TV. Android s PlayLingem: odkaz `playlingo://tv?…` otevře aplikaci
  v režimu „Nastavuji TV …“. iPhone / Android bez aplikace: webový průvodce přímo na té stránce.
- **Aplikace v telefonu v režimu TV** používá vlastní sadu nastavení (ne svoje – telefon jako čtečka
  pro LG o nic nepřijde). Po spojení si stáhne nastavení z TV (TV je hlavní), ukáže celou aplikaci
  s pruhem „Nastavuji: Android TV …“ a každou změnu přenášených voleb pošle do TV. Spárování PS5,
  účet PlayStation a klíč DeepL jdou zvlášť (klíč DeepL je v TV zašifrovaný jako dnes).
- **Co se přenáší:** jen nastavení hry a titulků (`core/RemoteSetup.TRANSFER`), ne věci vázané na
  zařízení (typ zařízení, statistiky, licence, tlačítko vzhledu z ovladače TV, zkušební kanál…).
- **Co telefon za TV neudělá:** povolení pruhu v Usnadnění (musí se zapnout ovladačem v TV), stažení
  jazykového balíčku (stáhne si ho TV sama po změně jazyka).

## Díly a stav (větev `nastaveni-z-telefonu`)

1. ✔ `core/RemoteSetup.kt` – co se přenáší (`transfers`), tvar zpráv (JSON s typy hodnot), odkaz
   `playlingo://tv?h=&p=&k=&n=` a stránka z TV. Testy `RemoteSetupTest`.
2. ✔ TV: `RemoteHost.kt` – `LoginServer.forPage` se stálým klíčem (`Prefs.remoteKey`, port 8766), běží
   s otevřenou aplikací (`MainActivity.onResume`) i ve službě (`TranslatorService.onCreate`), jen
   `RemoteHost.eligible` (ne telefon). POST `op=stav` → `RemoteSetup.snapshot`, `op=zmen&data=` →
   `Prefs.applyRemote` + `ACTION_RELOAD` (jen běžící služba) + překreslení TV, `op=deepl&key=` → ověření
   a `DeeplKey.save`. GET → stránka s tlačítkem do aplikace. ⚙ → Obecné → „Nastavit z telefonu“ (čtvereček).
   Zbývá: čtvereček i v prvním kroku průvodce v TV.
3. ✔ Telefon (režim „Nastavuji TV“):
   - Manifest: `PhoneHelperActivity` přijme i `playlingo://tv?…` → `RemoteSetup.parseLink` → `RemoteClient.connect`
     (mimo hlavní vlákno `op=stav`). Úspěch = celá `MainActivity` (extra `dalkove`); jinak hláška `rs_e_net` (jiná
     Wi-Fi), `rs_e_version` (starší PlayLingo v TV), `rs_e_bad` (neplatný odkaz). Pomocník v režimu TV ukazuje nahoře
     kartu „Nastavuji: <TV> · stav“ s Otevřít nastavení TV / Odpojit; „Použít telefon pro TV LG/Samsung“ režim ukončí.
   - `RemoteClient.kt`: cíl v SharedPreferences `dalkove` (host, port, klíč, jméno TV, aktivní, co TV naposledy
     potvrdila – po novém spuštění aplikace pokračuje a dopošle, co nestihla). Příznak `@Volatile active`, ze souboru
     se čte jednou (`RemoteClient.on`). `Prefs.p()` pak bere `dalkove_tv`; vlastní soubor telefonu (`Prefs.own`) dál
     drží účty z telefonu, `force_tv`, zkušební verze, viděnou verzi a licenci. `NetInfo.isPhone` je v režimu TV false.
   - Po spojení: `dalkove_tv` vymazán a naplněn snímkem TV + `tv_type` Android TV, `reader_device` false
     (`RemoteSync.localValues`). Posluchač (silná reference) → po 400 ms `RemoteSync.pending` (rozdíl proti potvrzenému)
     → `op=zmen`; TV nedostupná → stav `UNREACHABLE`, nový pokus s další změnou / po 10 s. Logika bez Androidu
     v `core/RemoteSync.kt`, testy `RemoteSyncTest`.
   - DeepL: `DeeplKey` v režimu TV v `deepl_dalkove`; uložený klíč odejde `op=deepl&key=` (TV ho ověří, „REJECTED“ →
     „TV odmítla klíč DeepL“), zapnutí/vypnutí `op=deepl&on=1/0` (nové v `RemoteHost`), snímek nese `deepl_on`.
     Klíč, který má jen TV, se do telefonu neposílá – Titulky ukazují „Klíč DeepL je uložený v TV“.
   - Spárování PS5 v telefonu zapíše `ps5_host` do `dalkove_tv` → odejde do TV jako každá jiná volba.
   - `MainActivity` v režimu TV: pruh „Nastavuji: <TV> · připojeno / TV nedostupná“ + Odpojit nahoře na každé stránce
     i v průvodci; Spustit/Zastavit (a tím náhled) se neukazuje, `startFlow`/`startDemo` nic nespustí; řádek TV na Domů
     = jméno TV a stav spojení; jazykový balíček se v telefonu nestahuje ani nekontroluje; ⚙ → Obecné bez typu TV,
     zařízení, běhu na pozadí, Usnadnění, tlačítka vzhledu a překrytí (zůstává Stav přes hru – přenáší se). Průvodce
     přeskakuje krok Zařízení, v kroku Televize jen Android TV, v kroku Zobrazení titulků věta `rs_perm_tv`.
   - Neověřeno (jen skutečná zařízení): celé spojení telefon ↔ Android TV, spárování PS5 z telefonu a jeho použití v TV.
     Účet Xbox se nepřenáší (je mimo `Prefs`) – Xbox se v režimu TV přihlašuje v TV.
4. Web pro iPhone: na GET stránka s průvodcem (jazyk, hra, DeepL, přihlášení PlayStation přes `PhoneLogin`,
   kód z PS5 – zde páruje TV, `Ps5Pairing` v TV) a vzhledem titulků; POST stejné `op=` jako aplikace.
5. Nápověda, `NAVOD.md`, CLAUDE.md, zkouška v emulátoru (snímek čtverečku v ⚙), pak zvýšit verzi a do `main`.
