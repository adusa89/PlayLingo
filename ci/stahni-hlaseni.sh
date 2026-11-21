#!/usr/bin/env bash
# Stáhne balík od testera (hlášení + snímky) ze schránky PlayLinga podle kódu, který aplikace ukázala.
#
#   ci/stahni-hlaseni.sh K7M2-QX9P        # stáhne a rozbalí do zaznamy/hlaseni-K7M2QX9P/
#   ci/stahni-hlaseni.sh --seznam         # vypíše balíky ve schránce (nejnovější první)
#
# Adresa schránky je v pomoc/hlaseni.json (pole url). Čtecí klíč: proměnná PLAYLINGO_CTENI, nebo soubor
# ~/.playlingo/hlaseni.txt. Klíč se nikam nevypisuje. V cloudovém sezení Claude ho nemá – spojení s klíčem
# tam doplní proxy (síťový tajný údaj prostředí pro hostitele schránky), proto bez klíče skript zkusí i tak.
set -euo pipefail
cd "$(dirname "$0")/.."
URL=$(python3 -c 'import json;print(json.load(open("pomoc/hlaseni.json"))["url"].rstrip("/"))')
[ -n "$URL" ] || { echo "Schránka ještě není nastavená (pomoc/hlaseni.json: url je prázdné)." >&2; exit 1; }
KLIC=${PLAYLINGO_CTENI:-}
[ -z "$KLIC" ] && [ -f "$HOME/.playlingo/hlaseni.txt" ] && KLIC=$(tr -d ' \r\n' < "$HOME/.playlingo/hlaseni.txt")
HLAVA=()
[ -n "$KLIC" ] && HLAVA=(-H "Authorization: Bearer $KLIC")
if [ "${1:-}" = "--seznam" ]; then
  curl -fsS "${HLAVA[@]}" "$URL/v1/seznam" | python3 -m json.tool
  exit 0
fi
KOD=$(echo "${1:?Chybí kód, např. K7M2-QX9P}" | tr 'a-z' 'A-Z' | tr -d ' -')
CIL="zaznamy/hlaseni-$KOD"
mkdir -p "$CIL"
curl -fsS "${HLAVA[@]}" -o "$CIL/balik.zip" "$URL/v1/hlaseni/$KOD"
python3 -c 'import sys,zipfile;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$CIL/balik.zip" "$CIL"
echo "Uloženo do $CIL:"; ls "$CIL" | head -20
