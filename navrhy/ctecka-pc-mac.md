# Čtečka pro TV na počítači (Windows, později Mac)

Přání majitele 21. 11. 2026: „Čtečku pro PC nebo Mac to určitě chceme! I nabídnout u LG pro iPhone.“

## Proč

TV LG a Samsung si obraz hry samy nepřečtou – čte zařízení s Androidem (telefon nebo box) a do TV posílá hotové věty.
Kdo má jen iPhone, dnes čtečku nemá. Počítač, který je doma stejně zapnutý, může dělat totéž co telefon.

## Jak to bude fungovat

- Hra jde z PS5 kabelem do TV, hraje se 🟢 herním účtem jako dřív.
- Počítač se k PS5 připojí přes Remote Play **🔵 účtem pro PlayLingo** (stejně jako Android čtečka) v programu chiaki-ng,
  PlayLingo čte jeho okno, překládá a věty posílá do aplikace PlayLingo v TV (výstup do TV LG/Samsung už `pc/` umí).
- Okno chiaki-ng může být za jinými okny, ne minimalizované; zvuk z PC ztlumený; PS5 se nebudí (čeká se, až ji hráč zapne).

## Díly

1. **Režim „Čtečka pro TV“ v PlayLingu pro Windows** (`pc/`, ⚙ → Použití, výchozí dosavadní „Hraji na PC“) – hotovo, čeká na zkoušku majitele na Windows. Čtečka má vlastní profil chiaki-ng `playlingo` (spárování 🔵 účtem zvlášť, zvuk 0).
   Ověří jen majitel: Windows + PS5 + TV LG.
2. **Balíček ke stažení pro Windows** – dnes `pc/` běží z repozitáře (Java 17 + Python). Pro hráče: ZIP / instalátor
   ve vydání (`PlayLingo-PC.zip`: Java z jlink, vložený Python s RapidOCR a modelem překladu, chiaki-ng se stáhne sám),
   sestavený na GitHubu (`windows-latest`). **Hotovo:** `.github/workflows/pc.yml`, vydání `pc` z `main`, návod `pomoc/pc.md`.
3. **Mac** – stejný Kotlin a Python, jiné jsou: snímek okna (ScreenCaptureKit / Quartz přes pyobjc, povolení
   „Nahrávání obrazovky“), spárování chiaki-ng (plist místo registru Windows), uložení klíče DeepL (Klíčenka místo DPAPI),
   balíček .app. Bez Macu se nedá vyzkoušet – potřeba někoho s Macem (majitel / tester).
4. **Nabídka na webu „Začít s PlayLingem“** (`pomoc/README.md`): u LG/Samsung + iPhone místo „bez Androidu to nejde“
   i „Čtečka v počítači (zkušební)“ s odkazem na stažení. **Hotovo** (zveřejní se z `main`).

## Otevřené otázky

- Jestli PS5 pustí Remote Play 🔵 účtu z PC, zatímco 🟢 hraje (z Androidu ano – stejný protokol, předpoklad ano).
- Jestli chiaki-ng kreslí do okna i za jinými okny celou dobu (PrintWindow) a nesníží kvalitu.
- Čtení na PC je pomalejší než ML Kit? (RapidOCR na CPU – změřit `READ|ocr=` v záznamu.)
