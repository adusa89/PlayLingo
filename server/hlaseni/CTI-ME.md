# Schránka na hlášení od testerů (Cloudflare)

Tester v PlayLingu zvolí **Poslat vývojáři** (obrazovka hlášení, nebo ⚙ → Experimentální → Ladění a hlášení).
Aplikace pošle balík (hlášení o stavu, případně dávku snímků ze hry) sem a ukáže kód, např. **K7M2-QX9P**.
Tester ho pošle majiteli, Claude si balík stáhne (`ci/stahni-hlaseni.ps1` / `.sh`) do `zaznamy\hlaseni-<kód>\`.

- **Kód:** `src/index.js` (Cloudflare Worker), úložiště **R2** (`playlingo-hlaseni`), zkouška bez Cloudflare:
  `node --test test/server.test.mjs`.
- **Meze:** balík nejvýš 45 MB, z jedné adresy nejvýš 6 za hodinu, celkem 300 balíků / 3 GB za den. Balíky se po
  **30 dnech** samy smažou (denní úklid). Nahrávat může kdokoli s aplikací (klíč v APK by tajný nebyl), číst, vypsat
  a mazat jen s **čtecím klíčem** `CTENI_KLIC` – ten je jen v Cloudflare a u majitele, v repozitáři nikdy.
- **Kde to aplikace najde:** adresa je v `pomoc/hlaseni.json` (zveřejní se se stránkami s pomocí z `main`). Dokud je
  `url` prázdné, aplikace ukáže „Odesílání · zatím nenastavené“. Nová adresa = jen úprava toho souboru, ne nové APK.
- **Cena:** Workers i R2 mají bezplatnou úroveň (R2 10 GB, Workers 100 000 požadavků denně) – na hlášení bohatě
  stačí. **R2 ale při zapnutí chce platební kartu** (bez ní se R2 nezapne); do bezplatné úrovně se nic neúčtuje.

## Nasazení (jednou, na PC majitele, asi 10 minut)

Potřeba: Node.js (už je kvůli zkoušce aplikace v TV) a účet Cloudflare (zdarma, dash.cloudflare.com).

1. **R2 zapnout:** v Cloudflare vlevo **R2 Object Storage** → zapnout (karta, bezplatná úroveň).
2. V PowerShellu ve složce repozitáře:
   ```
   cd server\hlaseni
   npx wrangler login                            # otevře prohlížeč, povolit
   npx wrangler r2 bucket create playlingo-hlaseni
   npx wrangler deploy                           # vypíše adresu …workers.dev
   ```
3. **Čtecí klíč** (dlouhý náhodný text) vyrobit a uložit jen u sebe:
   ```
   $b = New-Object byte[] 30; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
   $k = [Convert]::ToBase64String($b) -replace '[+/=]', 'x'
   New-Item -ItemType Directory -Force $env:USERPROFILE\.playlingo | Out-Null
   Set-Content $env:USERPROFILE\.playlingo\hlaseni.txt $k -NoNewline
   $k | npx wrangler secret put CTENI_KLIC
   ```
   Klíč nikam nekopírovat (ani do chatu). Claude na PC ho čte ze souboru; v cloudovém sezení si o něj řekne
   formulářem (tajný údaj prostředí pro adresu schránky).
4. Adresu z kroku 2 napsat do `pomoc/hlaseni.json` (`"url":"https://playlingo-hlaseni.<účet>.workers.dev"`), nebo
   ji říct Claudovi. Po zveřejnění stránek ji aplikace začne používat.
5. Zkouška: `.\ci\stahni-hlaseni.ps1 -Seznam` (prázdný seznam = funguje).

## Pro Claude

- Stažení: `.\ci\stahni-hlaseni.ps1 K7M2-QX9P` (PC) nebo `ci/stahni-hlaseni.sh K7M2-QX9P` (cloud; host schránky musí
  být v síti prostředí povolený a čtecí klíč uložený jako síťový tajný údaj – `request_credential`, hlavička
  Authorization: Bearer). Obsah: `hlaseni.txt` (jako Kopírovat v hlášení), `info.json`, případně `snimky/` s `index.txt`.
- Po rozboru balík smazat: `curl -X DELETE -H "Authorization: Bearer …" <url>/v1/hlaseni/<kód>` (jinak zmizí po 30 dnech).
- Změna serveru: upravit `src/index.js`, `node --test test/server.test.mjs`, pak `npx wrangler deploy` (majitel).
