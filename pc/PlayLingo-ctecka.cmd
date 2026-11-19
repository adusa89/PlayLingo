@echo off
rem Spusti ctecku PlayLingo pro pocitac dvojklikem.
rem   Bez parametru: ukazka bez PlayStationu.
rem   S parametrem:  PlayLingo-ctecka.cmd chiaki  = cte okno, jehoz titulek obsahuje "chiaki".
rem Ukonceni: zavrit okno nebo Ctrl+C.
chcp 65001 >nul
title PlayLingo - ctecka
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0spustit.ps1"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0spustit.ps1" -Okno "%~1"
)
if errorlevel 1 pause
