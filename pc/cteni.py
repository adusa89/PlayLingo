#!/usr/bin/env python3
"""Pomocník čtečky pro počítač: získá obraz hry, přečte z něj text a přeloží věty.

Pouští ho čtečka (pc/src/Main.kt) a mluví s ním po řádcích přes standardní vstup a výstup:

  CTI <tab> vynutit(0/1) [<tab> text ukázky]
      -> STEJNE                       obraz se od minula nezměnil (jen když vynutit=0)
      -> CHYBA <tab> popis            obraz není (např. okno s hrou není otevřené)
      -> OK <tab> N <tab> šířka <tab> výška <tab> x0 <tab> y0 <tab> ms
         a za tím N řádků:  vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> text
         (souřadnice jsou v čteném výřezu; x0, y0 je jeho levý horní roh v celém obraze)
  PRELOZ <tab> anglický text
      -> OK <tab> překlad
  BARVA <tab> vlevo <tab> nahoře <tab> vpravo <tab> dole      (v celém posledním snímku)
      -> OK <tab> r <tab> g <tab> b    průměrná barva nejsvětlejších bodů (písmo jména mluvčího)
      -> NIC                          málo bodů / žádný snímek
  OKNO <tab> nazev
      -> OK                           dál se čte jiné okno (stejný zápis jako --okno, např. pid:1234)
  MENU
      -> OK <tab> N <tab> šířka <tab> výška <tab> ms  a za tím N řádků:
         vlevo <tab> nahoře <tab> vpravo <tab> dole <tab> jas <tab> text
         horních 60 % posledního snímku, zmenšené na nejvýš 960 bodů na šířku (šířka × výška = celý
         zmenšený snímek); jas = průměrný jas pod řádkem 0–255 (vybraná položka menu má bílý pruh)

Čte se jen spodní třetina obrazu, stejně jako v aplikaci pro Android TV.
Výběr titulků a rozhodování, co ukázat, tu není – to dělá čtečka stejnou logikou jako Android.

Záznam pro ladění (--zaznam SLOZKA): celý obraz hry barevně a v plném rozlišení se uloží jako
snimek-HHMMSS-mmm.jpg, nejvýš jednou za --zaznam-s sekund a jen když se obraz od minule změnil.
Text se z něj nečte (čtení by zdržovalo titulky) – přečte se až potom při rozboru.

Zdroj obrazu:
  --okno NAZEV    okno, jehož titulek obsahuje NAZEV (např. chiaki, kde běží obraz z PlayStationu)
  (bez --okno)    ukázka: text ze čtečky se nakreslí na umělý „herní“ obrázek
"""
import argparse
import codecs
import ctypes
import io
import random
import sys
import time

import numpy as np

sys.stdin = io.TextIOWrapper(sys.stdin.buffer, encoding="utf-8", newline="\n")
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", newline="\n")

OBLAST_OD = 2.0 / 3.0      # čte se spodní třetina obrazu


def ven(*casti):
    sys.stdout.write("\t".join(str(c) for c in casti) + "\n")
    sys.stdout.flush()


# ---- obraz z okna (Windows) -------------------------------------------------

class Okno:
    """Snímek obsahu okna podle části jeho titulku. Okno nesmí být minimalizované."""

    def __init__(self, nazev):
        self.nazev = nazev.lower()
        self.u = ctypes.windll.user32
        self.g = ctypes.windll.gdi32
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)      # souřadnice ve skutečných bodech
        except Exception:
            pass
        self.hwnd = None

    def _najdi(self):
        nalezeno = []
        typ = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
        # „pid:1234“ = největší viditelné okno procesu (chiaki-ng spuštěný PlayLingem)
        if self.nazev.startswith("pid:") and self.nazev[4:].isdigit():
            from ctypes import wintypes
            hledany = int(self.nazev[4:])
            okna = []

            def podle_procesu(hwnd, _):
                if self.u.IsWindowVisible(hwnd):
                    pid = wintypes.DWORD()
                    self.u.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
                    if pid.value == hledany:
                        r = wintypes.RECT()
                        self.u.GetWindowRect(hwnd, ctypes.byref(r))
                        okna.append(((r.right - r.left) * (r.bottom - r.top), hwnd))
                return True

            self.u.EnumWindows(typ(podle_procesu), 0)
            return max(okna)[1] if okna else None

        def kazde(hwnd, _):
            if self.u.IsWindowVisible(hwnd):
                n = self.u.GetWindowTextLengthW(hwnd)
                if n:
                    buf = ctypes.create_unicode_buffer(n + 1)
                    self.u.GetWindowTextW(hwnd, buf, n + 1)
                    if self.nazev in buf.value.lower():
                        nalezeno.append(hwnd)
            return True

        self.u.EnumWindows(typ(kazde), 0)
        return nalezeno[0] if nalezeno else None

    def snimek(self):
        from ctypes import wintypes
        if not self.hwnd or not self.u.IsWindow(self.hwnd):
            self.hwnd = self._najdi()
        if not self.hwnd:
            return None, "okno „%s“ není otevřené" % self.nazev
        if self.u.IsIconic(self.hwnd):
            return None, "okno je minimalizované"
        r = wintypes.RECT()
        self.u.GetClientRect(self.hwnd, ctypes.byref(r))
        w, h = r.right, r.bottom
        if w < 320 or h < 180:
            return None, "okno je moc malé"
        # PrintWindow s vykreslením celého obsahu funguje i pro okna kreslená grafickou kartou
        # a i když je okno zakryté jiným.
        dc = self.u.GetDC(self.hwnd)
        mem = self.g.CreateCompatibleDC(dc)
        bmp = self.g.CreateCompatibleBitmap(dc, w, h)
        self.g.SelectObject(mem, bmp)
        self.u.PrintWindow(self.hwnd, mem, 1 | 2)

        class BITMAPINFOHEADER(ctypes.Structure):
            _fields_ = [("biSize", wintypes.DWORD), ("biWidth", wintypes.LONG), ("biHeight", wintypes.LONG),
                        ("biPlanes", wintypes.WORD), ("biBitCount", wintypes.WORD), ("biCompression", wintypes.DWORD),
                        ("biSizeImage", wintypes.DWORD), ("biXPelsPerMeter", wintypes.LONG),
                        ("biYPelsPerMeter", wintypes.LONG), ("biClrUsed", wintypes.DWORD), ("biClrImportant", wintypes.DWORD)]

        bi = BITMAPINFOHEADER()
        bi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
        bi.biWidth, bi.biHeight, bi.biPlanes, bi.biBitCount = w, -h, 1, 32
        buf = ctypes.create_string_buffer(w * h * 4)
        self.g.GetDIBits(mem, bmp, 0, h, buf, ctypes.byref(bi), 0)
        self.g.DeleteObject(bmp)
        self.g.DeleteDC(mem)
        self.u.ReleaseDC(self.hwnd, dc)
        obraz = np.frombuffer(buf, dtype=np.uint8).reshape(h, w, 4)[:, :, :3]      # BGR
        return obraz, None


# ---- ukázka: umělý „herní“ obrázek s titulkem -------------------------------

class Ukazka:
    """Nakreslí zadaný titulek na barevné neklidné pozadí (zkouška bez PlayStationu)."""
    W, H = 1280, 720

    def __init__(self):
        from PIL import Image, ImageDraw, ImageFilter, ImageFont
        self.Image, self.ImageDraw, self.ImageFont = Image, ImageDraw, ImageFont
        random.seed(7)
        im = Image.new("RGB", (self.W, self.H), (40, 30, 30))
        d = ImageDraw.Draw(im)
        for _ in range(200):
            x, y, r = random.randint(0, self.W), random.randint(0, self.H), random.randint(20, 170)
            d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(random.randint(20, 230) for _ in range(3)))
        self.pozadi = im.filter(ImageFilter.GaussianBlur(5))
        self.pismo = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 26)
        self.pismo_hud = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 20)

    def snimek(self, text):
        im = self.pozadi.copy()
        d = self.ImageDraw.Draw(im)
        d.text((40, 28), "OBJECTIVE: Reach the tower", font=self.pismo_hud, fill=(255, 220, 90))
        radky = [r for r in text.split("\n") if r]
        y = self.H - 70 - len(radky) * 34
        for r in radky:
            w = d.textlength(r, font=self.pismo)
            d.text(((self.W - w) / 2, y), r, font=self.pismo, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
            y += 34
        return np.asarray(im)[:, :, ::-1], None


# ---- záznam pro ladění -----------------------------------------------------

class Zaznamnik:
    """Ukládá celé snímky hry do složky záznamu (v pozadí, čtení titulků nečeká)."""

    def __init__(self, slozka, interval):
        import os
        import threading
        os.makedirs(slozka, exist_ok=True)
        self.slozka = slozka
        self.interval = max(0.5, interval)
        self.posledni = 0.0
        self.otisk = None
        self.zamek = threading.Lock()
        self.threading = threading

    def mozna(self, obraz):
        ted = time.time()
        if ted - self.posledni < self.interval:
            return
        h, w = obraz.shape[:2]
        vh, vw = (h // 16) * 16, (w // 16) * 16
        otisk = obraz[:vh, :vw].mean(axis=2).reshape(vh // 16, 16, vw // 16, 16).mean(axis=(1, 3))
        if self.otisk is not None and self.otisk.shape == otisk.shape and np.abs(otisk - self.otisk).max() < 6:
            return
        if not self.zamek.acquire(blocking=False):
            return      # předchozí snímek se ještě ukládá
        self.posledni = ted
        self.otisk = otisk
        jmeno = time.strftime("snimek-%H%M%S-", time.localtime(ted)) + "%03d.jpg" % int((ted % 1) * 1000)
        kopie = np.ascontiguousarray(obraz[:, :, ::-1])      # BGR → RGB

        def uloz():
            try:
                from PIL import Image
                Image.fromarray(kopie).save(self.slozka + "/" + jmeno, quality=85)
            except Exception as e:
                sys.stderr.write("záznam: %r\n" % e)
            finally:
                self.zamek.release()

        self.threading.Thread(target=uloz, daemon=True).start()


# ---- překlad ------------------------------------------------------------------

class Prekladac:
    """Překlad bez internetu: model Argos (jádro CTranslate2), dělení slov Moses + BPE."""

    def __init__(self, slozka, cil):
        import ctranslate2
        from sacremoses import MosesDetokenizer, MosesTokenizer
        from subword_nmt.apply_bpe import BPE
        self.model = ctranslate2.Translator(slozka + "/model", device="cpu", inter_threads=1, intra_threads=4)
        self.bpe = BPE(codecs.open(slozka + "/bpe.model", encoding="utf-8"))
        self.tok = MosesTokenizer(lang="en")
        self.detok = MosesDetokenizer(lang=cil)

    def preloz(self, text):
        kusy = self.bpe.process_line(self.tok.tokenize(text, return_str=True, escape=True)).split()
        if not kusy:
            return ""
        r = self.model.translate_batch([kusy], beam_size=4, max_decoding_length=160)
        slova = " ".join(r[0].hypotheses[0]).replace("@@ ", "").replace("@@", "").split()
        return self.detok.detokenize(slova)


def velka(text):
    """Řádek psaný velkými písmeny čtečka občas vrátí s pár malými („We HAVe TO GET Out“).
    Když velká písmena jasně převažují, bere se celý jako velký; o zbytek se postará čtečka."""
    pismena = [c for c in text if c.isalpha()]
    if len(pismena) >= 4 and sum(c.isupper() for c in pismena) >= 0.6 * len(pismena):
        return text.upper()
    return text


def barva_jmena(obraz, obdelnik):
    """Barva písma jména mluvčího (jako NameColor.fromSamples na Androidu): nejsvětlejší čtvrtina bodů
    v obdélníku (aspoň jas 110) je písmo, z nich průměrná barva. Bílé/šedé jméno pozná až čtečka."""
    if obraz is None:
        return ("NIC",)
    h, w = obraz.shape[:2]
    l, t, r, b = obdelnik
    l, r = max(0, min(l, w - 1)), max(0, min(r, w))
    t, b = max(0, min(t, h - 1)), max(0, min(b, h))
    if r - l < 2 or b - t < 2:
        return ("NIC",)
    kus = obraz[t:b, l:r].reshape(-1, 3).astype(np.int32)      # BGR
    jas = (kus[:, 2] * 299 + kus[:, 1] * 587 + kus[:, 0] * 114) // 1000
    if len(jas) < 8:
        return ("NIC",)
    mez = max(int(np.sort(jas)[(len(jas) * 3) // 4]), 110)
    pismo = kus[jas >= mez]
    if len(pismo) < 4:
        return ("NIC",)
    bb, gg, rr = pismo.mean(axis=0)
    return ("OK", int(rr), int(gg), int(bb))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--model", required=True, help="složka rozbaleného překladového modelu Argos")
    p.add_argument("--jazyk", default="cs")
    p.add_argument("--okno", default="", help="část titulku okna s obrazem hry; bez toho běží ukázka")
    p.add_argument("--zaznam", default="", help="složka pro snímky hry (ladění); bez toho se nic neukládá")
    p.add_argument("--zaznam-s", type=float, default=2.0, help="nejkratší odstup snímků záznamu v sekundách")
    a = p.parse_args()

    from rapidocr_onnxruntime import RapidOCR
    # delší strana nejvýš 1280 bodů: čtení pásu s titulky pak trvá asi pětinu sekundy
    ocr = RapidOCR(det_limit_side_len=1280, det_limit_type="max")
    prekladac = Prekladac(a.model, a.jazyk)
    zdroj = Okno(a.okno) if a.okno else Ukazka()
    zaznamnik = Zaznamnik(a.zaznam, a.zaznam_s) if a.zaznam else None
    ocr(np.zeros((120, 640, 3), dtype=np.uint8), use_cls=False)      # první čtení trvá déle – ať to není až ve hře
    prekladac.preloz("Hello.")
    ven("PRIPRAVENO")

    minule = None
    posledni = None      # celý poslední snímek (BGR) – pro barvu jména a menu
    for radek in sys.stdin:
        cast = radek.rstrip("\n").split("\t")
        try:
            if cast[0] == "OKNO":
                # čtečka pro TV: po každém připojení k PS5 je chiaki-ng nový proces s novým oknem
                if isinstance(zdroj, Okno) and len(cast) > 1 and cast[1]:
                    zdroj = Okno(cast[1])
                    minule = None
                    posledni = None
                    ven("OK")
                else:
                    ven("CHYBA", "okno nejde změnit")
            elif cast[0] == "BARVA":
                ven(*barva_jmena(posledni, [int(float(c)) for c in cast[1:5]]))
            elif cast[0] == "MENU":
                if posledni is None:
                    ven("OK", 0, 0, 0, 0)
                    continue
                t0 = time.time()
                h, w = posledni.shape[:2]
                krok = max(1, (w + 959) // 960)
                mensi = posledni[:int(h * 0.6):krok, ::krok]
                sedy = mensi.mean(axis=2)
                vysledek, _ = ocr(np.ascontiguousarray(mensi), use_cls=False)
                radky = []
                for ctyruhelnik, text, jistota in (vysledek or []):
                    if jistota < 0.5:
                        continue
                    xs = [b[0] for b in ctyruhelnik]
                    ys = [b[1] for b in ctyruhelnik]
                    l, t, r, b = int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))
                    jas = int(sedy[max(0, t):max(t + 1, b), max(0, l):max(l + 1, r)].mean())
                    radky.append((l, t, r, b, jas, text.replace("\t", " ").replace("\n", " ")))
                ven("OK", len(radky), w // krok, h // krok, int((time.time() - t0) * 1000))
                for r in radky:
                    ven(*r)
            elif cast[0] == "PRELOZ":
                ven("OK", prekladac.preloz(cast[1] if len(cast) > 1 else "").replace("\t", " ").replace("\n", " "))
            elif cast[0] == "CTI":
                vynutit = len(cast) > 1 and cast[1] == "1"
                if isinstance(zdroj, Ukazka):
                    obraz, chyba = zdroj.snimek((cast[2] if len(cast) > 2 else "").replace("\\n", "\n"))
                else:
                    obraz, chyba = zdroj.snimek()
                if obraz is None:
                    minule = None
                    ven("CHYBA", chyba)
                    continue
                posledni = obraz
                if zaznamnik:
                    zaznamnik.mozna(obraz)
                h, w = obraz.shape[:2]
                y0 = int(h * OBLAST_OD)
                vyrez = obraz[y0:h]
                # hrubý otisk výřezu: když se od minula nezměnil, číst znovu netřeba
                # (průměr jasu po čtverečcích 8×8; stačí jediný změněný čtvereček – i jednoslovný
                # titulek „Wait“ zabere jen zlomek výřezu a v průměru celé plochy by zanikl)
                vh, vw = (vyrez.shape[0] // 8) * 8, (vyrez.shape[1] // 8) * 8
                otisk = vyrez[:vh, :vw].mean(axis=2).reshape(vh // 8, 8, vw // 8, 8).mean(axis=(1, 3))
                stejne = minule is not None and minule.shape == otisk.shape and np.abs(otisk - minule).max() < 6
                minule = otisk
                if stejne and not vynutit:
                    ven("STEJNE")
                    continue
                t0 = time.time()
                vysledek, _ = ocr(np.ascontiguousarray(vyrez), use_cls=False)
                ms = int((time.time() - t0) * 1000)
                radky = []
                for ctyruhelnik, text, jistota in (vysledek or []):
                    if jistota < 0.5:
                        continue
                    xs = [b[0] for b in ctyruhelnik]
                    ys = [b[1] for b in ctyruhelnik]
                    radky.append((int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys)), velka(text.replace("\t", " ").replace("\n", " "))))
                ven("OK", len(radky), w, h, 0, y0, ms)
                for r in radky:
                    ven(*r)
            else:
                ven("CHYBA", "neznámý příkaz")
        except Exception as e:      # jedna chyba nesmí čtečku shodit
            ven("CHYBA", repr(e).replace("\t", " ").replace("\n", " ")[:200])


if __name__ == "__main__":
    main()
