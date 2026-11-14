#!/bin/bash
# Automatická zkouška v emulátoru Android TV: instalace, průvodce, všechny obrazovky,
# spuštění překladu nad ukázkovou „hrou“ a kontrola, že se titulky přeložily.
set -x
OUT=test-results
mkdir -p $OUT
PKG=cz.testpreklad
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
key() { adb shell input keyevent "$1"; sleep 1; }

adb install -r TestPreklad-emulator.apk || { echo "INSTALL FAILED" > $OUT/result.txt; exit 1; }
adb shell getprop ro.build.version.release > $OUT/device.txt
adb shell getprop ro.product.model >> $OUT/device.txt
adb shell wm size >> $OUT/device.txt
adb shell cat /proc/meminfo | head -1 >> $OUT/device.txt
# Android 13+: povolení notifikací, aby dotaz nepřekryl průvodce
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

# 1) první spuštění: průvodce krok za krokem
adb shell am start -n $PKG/.MainActivity
sleep 10; shot 01_pruvodce_vitej
key 23; sleep 2; shot 02_pruvodce_povoleni
key 23; sleep 2; shot 03_pruvodce_jazyk
sleep 45; shot 03b_pruvodce_jazyk_po_stazeni
key 23; sleep 2; shot 04_pruvodce_ps5_krok1
key 23; sleep 8; shot 04b_pruvodce_ps5_krok2_hledani
# zpět v průvodci spárováním a pak dokončení průvodce
adb shell am start -n $PKG/.MainActivity --ez nowizard true
sleep 3; shot 06_domu

# 2) všechny stránky menu
for i in 1 2 3 4 5 6 7; do key 20; key 23; sleep 2; shot "07_menu_$i"; done

# 3) povolení a spuštění překladu (povolení snímání udělí test místo člověka)
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
# Usnadnění zapnuté jako u skutečného uživatele (pruh přes Usnadnění, aplikace ví, co je na obrazovce)
adb shell am force-stop $PKG
# (vynucené zastavení aplikace Usnadnění vypíná, proto se zapíná až po něm)
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell am start -n $PKG/.MainActivity --ez autostart true
sleep 12; shot 08_po_spusteni

# 4) ukázková hra s titulky – jde do aplikace jako video (stejná cesta jako z PlayStationu);
#    aplikace ji má dekódovat, přečíst, přeložit a zakrýt pruhem
adb shell am start -n $PKG/.TestCardActivity
for i in 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48; do sleep 3; shot "09_hra_$i"; done

# 5) spojení s PlayStationem: konzole tu není, ale ověří se, že se nativní část načte,
#    hledání v síti a párování skončí slušně a spojení se zkouší znovu – bez pádu
key 4; sleep 2
adb shell am start -n $PKG/.MainActivity --es ps5test 10.0.2.2
sleep 45; shot 10_ps5_nedostupna
key 20; key 23; sleep 3; shot 11_stranka_playstation

# 6) přihlašovací stránka Sony ve vestavěném prohlížeči: načte se na této verzi Androidu?
adb shell am start -n $PKG/.MainActivity --ez psnlogin true
sleep 35; shot 12_prihlaseni_sony
sleep 25; shot 12b_prihlaseni_sony_pozdeji
# zkouška odeslání: vymyšlený e-mail. Když Sony odpoví věcně (účet nenalezen / krok s heslem),
# přihlašovací dotaz prochází; „connection timed out“ by znamenalo, že ho prohlížeč blokuje.
for i in $(seq 1 22); do adb shell input keyevent 22; done
key 23; sleep 3; shot 12c_pole_email
adb shell input text "zkouska.testpreklad@example.com"; sleep 2
key 4; sleep 2
for i in 1 2 3 4 5; do adb shell input keyevent 20; done
shot 12d_pred_odeslanim
key 23; sleep 25; shot 12e_po_odeslani
sleep 20; shot 12f_po_odeslani_pozdeji
# krok s heslem: vymyšlené heslo. Věcná odpověď („nesprávné údaje“) = dotaz prošel ochranou Sony;
# „Can't connect to the server“ = ochrana ho odmítla.
key 19; key 19; key 23; sleep 3
adb shell input text "Zkouska-Heslo-12345"; sleep 2
key 4; sleep 2
for i in 1 2 3 4 5; do adb shell input keyevent 20; done
shot 12g_heslo_pred_odeslanim
key 23; sleep 25; shot 12h_heslo_po_odeslani
sleep 20; shot 12i_heslo_po_odeslani_pozdeji
key 4; sleep 2
key 4; sleep 2; key 4; sleep 2

# 6b) přihlášení přes telefon: QR kód a políčko na kód
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 2
sleep 4; shot 12j_ucet_1_qr
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 3
sleep 3; shot 12k_ucet_2_prihlaseni
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 4
sleep 3; shot 12l_ucet_3_kod
adb shell am start -n $PKG/.MainActivity --ez phonelogin true --ei pairstep 5
sleep 3; shot 12m_cislo_z_ps5

# 7) nabídka se seznamem (jazyk): stránka Titulky → první řádek → OK
adb shell am start -n $PKG/.MainActivity --ez nowizard true
sleep 2; key 20; key 20; key 20; key 23; sleep 1; key 22; sleep 1; key 23; sleep 2; shot 13_nabidka_jazyk
key 4; sleep 1

# 8) jiná rozlišení obrazovky televize: 720p a 4K. Pruh se má i tam postavit přesně na titulek.
for mode in "1280x720 213 720p" "3840x2160 640 4k"; do
  set -- $mode
  adb shell am force-stop $PKG
  adb shell wm size $1; adb shell wm density $2; sleep 3
  adb shell settings put secure enabled_accessibility_services $PKG/$PKG.TitulkyAccessibility
  adb shell settings put secure accessibility_enabled 1
  sleep 4
  adb shell am start -n $PKG/.MainActivity --ez autostart true
  sleep 12
  adb shell am start -n $PKG/.TestCardActivity
  sleep 20; shot "14_rozliseni_$3_a"
  sleep 8; shot "14_rozliseni_$3_b"
  sleep 8; shot "14_rozliseni_$3_c"
  key 4; sleep 1
done
adb shell am force-stop $PKG
adb shell wm size reset; adb shell wm density reset; sleep 2

adb logcat -d -v time > $OUT/logcat_full.txt
grep -E "TP_TEST|TestPreklad|AndroidRuntime|FATAL|libchiaki|DEBUG   :" $OUT/logcat_full.txt > $OUT/log.txt
SHOWS=$(grep -c "TP_TEST.*SHOW|" $OUT/log.txt)
adb shell dumpsys meminfo $PKG | grep -E "TOTAL|Native Heap|Dalvik Heap" | head -4 > $OUT/memory.txt
# počítají se jen pády naší aplikace (na Androidu 9 padá vestavěná aplikace emulátoru, to není naše chyba)
CRASH=$(grep -cE "Process: cz.testpreklad|Fatal signal.*cz.testpreklad" $OUT/logcat_full.txt)
LIB=$(grep -c "TP_TEST.*START|.*lib=ok" $OUT/log.txt)
DECODED=$(grep "TP_TEST.*VIDEO|" $OUT/log.txt | grep -oE "decoded=[0-9]+" | cut -d= -f2 | sort -n | tail -1)
LINK=$(grep -c "TP_TEST.*LINK|\(WAITING\|FAILED\)" $OUT/log.txt)
PAIR=$(grep -c "TP_TEST.*PAIR|\(failed\|timeout\|error\)" $OUT/log.txt)
# kolik z deseti různých ukázkových titulků (velká písmena, jména, krátká slova…) se přeložilo
KINDS=0
for k in "We need money" "reckon" "ride at dawn" "keep going like this" "right way" "no idea" "GET OUT OF HERE" "|Wait|" "never supposed to meet" "left door" "voice down" "been here for years" "lantern" "bridge is out"; do
  grep -qF -- "$k" <(grep "TP_TEST.*SHOW|" $OUT/log.txt) && KINDS=$((KINDS+1)) || echo "NEPRELOZENO: $k" >> $OUT/missing.txt
done
echo "ruznych_titulku=$KINDS/14" > $OUT/kinds.txt
echo "prelozenych_titulku=$SHOWS pady=$CRASH knihovna_ps5=$LIB dekodovano_snimku=${DECODED:-0} pokusy_o_spojeni=$LINK parovani_bez_konzole=$PAIR" > $OUT/result.txt
if [ "$SHOWS" -ge 3 ] && [ "$KINDS" -ge 7 ] && [ "$CRASH" -eq 0 ] && [ "$LIB" -ge 1 ] && [ "${DECODED:-0}" -ge 20 ] && [ "$LINK" -ge 1 ] && [ "$PAIR" -ge 1 ]; then
  echo "VYSLEDEK=OK" >> $OUT/result.txt
else
  echo "VYSLEDEK=CHYBA" >> $OUT/result.txt
fi
cat $OUT/kinds.txt $OUT/missing.txt >> $OUT/result.txt 2>/dev/null
grep "TP_TEST" $OUT/log.txt | grep -v "READ|" >> $OUT/result.txt
adb shell dumpsys package com.google.android.webview 2>/dev/null | grep -m1 versionName >> $OUT/device.txt || true
grep "TP_TEST" $OUT/log.txt > $OUT/reads.txt
tail -c 400000 $OUT/logcat_full.txt > $OUT/logcat_tail.txt; rm $OUT/logcat_full.txt
cat $OUT/result.txt
exit 0
