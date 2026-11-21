@echo off
rem Ctecka PlayLingo z prikazove radky (ladeni, vypis do konzole).
rem Bezne se pouziva okno aplikace PlayLingo.cmd - tam je i "Ctecka pro TV"
rem (Nastaveni -> Pouziti), ktera se sama pripoji k PS5 a posila preklad do TV LG / Samsung.
rem   Bez parametru: ukazka bez PlayStationu.
rem   S parametrem:  PlayLingo-ctecka.cmd chiaki     = cte okno, jehoz titulek obsahuje "chiaki".
rem   Druhy parametr: pc / tv / oboje = kam s prekladem (vychozi pruh na PC),
rem                  napr. PlayLingo-ctecka.cmd chiaki tv
rem Ukonceni: zavrit okno nebo Ctrl+C.
chcp 65001 >nul
title PlayLingo - ctecka
set "VYSTUP=%~2"
if "%VYSTUP%"=="" set "VYSTUP=pc"
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0spustit.ps1" -Vystup %VYSTUP%
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0spustit.ps1" -Okno "%~1" -Vystup %VYSTUP%
)
if errorlevel 1 pause
