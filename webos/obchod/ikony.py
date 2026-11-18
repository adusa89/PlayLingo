#!/usr/bin/env python3
"""Nakreslí ikony verze pro obchod LG: do balíčku (80 a 130 bodů) a pro obchod (400 bodů).

Použití:  python3 webos/obchod/ikony.py   (potřebuje knihovnu Pillow)
Stejný motiv jako ikona zkušební aplikace: žlutá dlaždice, tmavá obrazovka, dole pruh titulku.
"""
import os
from PIL import Image, ImageDraw

TADY = os.path.dirname(os.path.abspath(__file__))
ZLUTA, TMAVA = (242, 193, 78, 255), (14, 16, 20, 255)


def ikona(strana):
    k = 4 * strana / 400.0          # kreslí se čtyřikrát větší a zmenší, ať jsou hrany hladké
    obr = Image.new("RGBA", (4 * strana, 4 * strana), (0, 0, 0, 0))
    d = ImageDraw.Draw(obr)

    def obdelnik(x0, y0, x1, y1, r, barva):
        d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=barva)

    # LG vrací ikony se zaoblenými rohy nebo průhledným pozadím: dlaždice musí být plný čtverec.
    obdelnik(0, 0, 400, 400, 0, ZLUTA)
    obdelnik(64, 92, 336, 308, 26, TMAVA)
    obdelnik(104, 236, 296, 268, 10, ZLUTA)
    return obr.resize((strana, strana), Image.LANCZOS).convert("RGB")


ikona(80).save(os.path.join(TADY, "app", "icon.png"))
ikona(130).save(os.path.join(TADY, "app", "largeIcon.png"))
ikona(400).save(os.path.join(TADY, "ikona-400.png"))
print("Hotovo: app/icon.png, app/largeIcon.png, ikona-400.png")
