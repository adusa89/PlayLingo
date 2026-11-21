# Stáhne balík od testera (hlášení + snímky) ze schránky PlayLinga podle kódu, který aplikace ukázala.
#
#   .\ci\stahni-hlaseni.ps1 K7M2-QX9P      # stáhne a rozbalí do zaznamy\hlaseni-K7M2QX9P\
#   .\ci\stahni-hlaseni.ps1 -Seznam        # balíky ve schránce (nejnovější první)
#
# Adresa schránky je v pomoc\hlaseni.json (url). Čtecí klíč leží mimo repozitář v %USERPROFILE%\.playlingo\hlaseni.txt
# (nebo proměnná PLAYLINGO_CTENI) – nikam ho nekopírovat ani nevypisovat.
param([Parameter(Position = 0)][string]$Kod = '', [switch]$Seznam)
$ErrorActionPreference = 'Stop'
$koren = Split-Path $PSScriptRoot -Parent
$url = ((Get-Content "$koren\pomoc\hlaseni.json" -Raw | ConvertFrom-Json).url).TrimEnd('/')
if (-not $url) { throw 'Schránka ještě není nastavená (pomoc\hlaseni.json: url je prázdné).' }
$klic = $env:PLAYLINGO_CTENI
if (-not $klic) { $klic = (Get-Content "$env:USERPROFILE\.playlingo\hlaseni.txt" -Raw -ErrorAction SilentlyContinue) }
if (-not $klic) { throw 'Chybí čtecí klíč (%USERPROFILE%\.playlingo\hlaseni.txt).' }
$hlava = @{ Authorization = 'Bearer ' + $klic.Trim() }
if ($Seznam) { Invoke-RestMethod "$url/v1/seznam" -Headers $hlava | Format-Table kod, cas, druh, verze, velikost; exit 0 }
if (-not $Kod) { throw 'Chybí kód, např. K7M2-QX9P.' }
$k = $Kod.ToUpper().Replace('-', '').Replace(' ', '')
$cil = "$koren\zaznamy\hlaseni-$k"
New-Item -ItemType Directory -Force $cil | Out-Null
Invoke-WebRequest "$url/v1/hlaseni/$k" -Headers $hlava -OutFile "$cil\balik.zip"
Expand-Archive "$cil\balik.zip" -DestinationPath $cil -Force
Write-Host "Uloženo do $cil"
Get-ChildItem $cil | Select-Object -First 20 | Format-Table Name, Length
