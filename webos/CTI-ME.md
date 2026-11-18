# PlayLingo a televize LG (webOS)

Televize LG si obraz hry sama přečíst neumí (ověřeno na OLED55B7, viz `zkouska/`). Proto na LG
běží PlayLingo jen jako obrazovka: ukáže hru ze vstupu HDMI a přes ni pruh s překladem. Číst
a překládat musí jiné zařízení v téže domácí síti a televizi posílat hotové věty.

```
PlayStation ──HDMI──────────────────────────▶ televize LG  (aplikace z obchod/)
     │                                             ▲
     └─ obraz po síti ─▶ PlayLingo na Androidu ────┘ hotové věty po domácí síti (port 8787)
                         (telefon nebo box; čte a překládá)
```

## Co je kde

| Složka / soubor | Co to je | Kdy se použije |
|---|---|---|
| `obchod/` | **Aplikace pro televizi** (id `cz.playlingo.tv`), česky a anglicky. Podává se do obchodu LG. | Tohle je PlayLingo pro LG. |
| `zkouska/` | Diagnostická aplikace (id `cz.playlingo.zkouska`): zjistí, co daná televize aplikaci dovolí. | Jen při zkoumání nového modelu televize. |
| `posilac.js` | Zkušební posílač vět z počítače (`node webos/posilac.js`). Nic nečte ani nepřekládá. | Zkouška aplikace v televizi bez Androidu. |
| `../pc/` | Zkušební čtečka pro počítač: čte a překládá jako Android. | Ladění čtení a překladu. |
| `../app/…/core/TvSender.kt` | Strana Androidu: posílání vět do televize a popis zpráv. | Volba „Posílat titulky do televize LG“. |

Jak spolu televize a zařízení mluví, je popsané na jediném místě: v `TvSender.kt`. V televizi to
obsluhuje jediný soubor, `obchod/app/app.js` (funkce `hledej`, `zkus`, `prevezmi`).

## Jak to zprovoznit doma

1. **Telefon:** v PlayLingu (od 0.63) klepnout na „Použít telefon pro televizi LG“. V boxu
   s Android TV: Nastavení → „Posílat titulky do televize LG“.
2. **Televize:** spustit PlayLingo a vybrat vstup HDMI s PlayStationem. Televize si zařízení
   v síti najde sama a zapamatuje si ho.
3. V telefonu spustit překlad (nebo zkoušku bez PlayStationu).

## Nahrání do televize při vývoji

Televize musí mít zapnutý vývojářský režim (aplikace Developer Mode z LG Content Store, účet
z webostv.developer.lge.com, zapnout Dev Mode Status a Key Server). Na počítači nástroj LG:
`npm install -g @webos-tools/cli`.

```
ares-setup-device -a lgtv -i "host=ADRESA_TELEVIZE" -i "port=9922" -i "username=prisoner"
ares-novacom --device lgtv --getkey --passphrase KOD_Z_TELEVIZE
ares-package --no-minify webos/obchod/app -o webos/obchod
ares-install -d lgtv webos/obchod/cz.playlingo.tv_1.0.0_all.ipk
ares-launch  -d lgtv cz.playlingo.tv
ares-inspect -d lgtv -a cz.playlingo.tv      # živý přístup do běžící aplikace
```

Vývojářský režim se po čase sám vypne a aplikace z televize zmizí; v aplikaci Developer Mode
jde prodloužit. Trvalá cesta pro uživatele je obchod LG – viz `obchod/CTI-ME.md`.

Televize z roku 2017 má prohlížeč Chromium 38: kód pro televizi musí zůstat ve starém zápisu
JavaScriptu (žádné `let`/`const`, šipkové funkce, `Promise`, `fetch` ani šablonové řetězce).
