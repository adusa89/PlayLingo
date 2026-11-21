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

## Díly

1. `core/RemoteSetup.kt` – co se přenáší, tvar zpráv (JSON s typy hodnot), testy. ← hotovo jako první
2. TV: server nastavení, přijetí změn (`Prefs` + `TranslatorService.ACTION_RELOAD`), čtvereček v ⚙ a v průvodci.
3. Telefon: režim „Nastavuji TV“ (vlastní soubor nastavení, stažení z TV, průběžné posílání, pruh nahoře, odpojení).
4. Web pro iPhone: průvodce + hlavní volby na stránce z TV.
5. Nápověda, `NAVOD.md`, texty v 10 jazycích, zkouška v emulátoru.
