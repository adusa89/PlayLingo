"""Zkouška balíčku ke stažení (PlayLingo-PC.zip) na GitHubu: přibalený Python s knihovnami přečte
ukázkový titulek (RapidOCR) a přeloží ho (model Argos) – přesně tak, jak to dělá čtečka (cteni.py).

  python zkouska_balicku.py <python z balíčku> <cteni.py> <složka modelu>

Konec s kódem 0 = čtení i překlad fungují. Pouští ho jen `.github/workflows/pc.yml`.
"""
import subprocess
import sys
import time

python, cteni, model = sys.argv[1:4]
VETA = "Where are you going tonight?"

p = subprocess.Popen([python, cteni, "--model", model, "--jazyk", "cs"], stdin=subprocess.PIPE,
                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")


def radek():
    r = p.stdout.readline()
    if not r:
        print("CHYBA: pomocník skončil\n" + p.stderr.read())
        sys.exit(1)
    return r.rstrip("\n")


def posli(s):
    p.stdin.write(s + "\n")
    p.stdin.flush()


t0 = time.time()
prvni = radek()
print("start:", prvni, "za %.1f s" % (time.time() - t0))
if prvni != "PRIPRAVENO":
    sys.exit(1)

posli("CTI\t1\t" + VETA)
hlava = radek().split("\t")
print("čtení:", hlava)
if hlava[0] != "OK":
    sys.exit(1)
precteno = [radek().split("\t")[-1] for _ in range(int(hlava[1]))]
print("přečteno:", precteno)
if "GOING" not in " ".join(precteno).upper():
    print("CHYBA: věta se nepřečetla")
    sys.exit(1)

posli("PRELOZ\t" + VETA)
preklad = radek().split("\t")
print("překlad:", preklad)
if preklad[0] != "OK" or len(preklad) < 2 or not preklad[1].strip():
    sys.exit(1)

p.stdin.close()
print("OK")
