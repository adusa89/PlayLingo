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
3. Telefon – návrh:
   - Manifest: `PhoneHelperActivity` přijme i `playlingo://tv` → `RemoteClient.connect` (mimo hlavní vlákno
     `op=stav`), uloží cíl (vlastní SharedPreferences `dalkove`: host, port, klíč, jméno TV, aktivní).
   - `Prefs.p()` v režimu TV používá soubor `dalkove_tv` (ne `test_preklad`), aby telefon jako čtečka pro LG
     o svoje nastavení nepřišel; příznak režimu držet v `@Volatile` proměnné (žádné čtení souboru při každém
     `Prefs.…`). Stejně `DeeplKey` (`deepl` → `deepl_dalkove`) a klíč DeepL se místo uložení pošle `op=deepl`.
   - Po spojení: soubor `dalkove_tv` vymazat a naplnit snímkem z TV (TV je hlavní), `tv_type` = Android TV,
     `reader_device` = false. Posluchač `OnSharedPreferenceChangeListener` (silná reference v objektu) při
     zápisu snímku vypnutý; jinak změna přenášené volby → po 400 ms `RemoteSetup.diff` proti naposledy
     poslanému → `op=zmen`. Při chybě sítě pruh „TV nedostupná“ a nový pokus při další změně / po 10 s.
   - Spárování PS5 v telefonu zapíše `ps5_host` do `dalkove_tv` → odejde do TV jako každá jiná volba.
   - `MainActivity` v režimu TV: nahoře pruh „Nastavuji: <TV> · Odpojit“; Spustit / náhled / povolení pruhu
     (Usnadnění, překrytí) / běh na pozadí / krok průvodce Zařízení se v telefonu neukazují (patří TV);
     krok Zobrazení titulků jen věta „zapni v TV ovladačem“. Odpojit → zpět do `test_preklad`, pomocník.
4. Web pro iPhone: na GET stránka s průvodcem (jazyk, hra, DeepL, přihlášení PlayStation přes `PhoneLogin`,
   kód z PS5 – zde páruje TV, `Ps5Pairing` v TV) a vzhledem titulků; POST stejné `op=` jako aplikace.
5. Nápověda, `NAVOD.md`, CLAUDE.md, zkouška v emulátoru (snímek čtverečku v ⚙), pak zvýšit verzi a do `main`.
