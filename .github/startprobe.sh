#!/usr/bin/env bash
# Einmalige Startprobe der Release-APK: R8 entfernt, was es fuer unbenutzt
# haelt - erwischt es dabei etwas, das nur per Reflexion erreicht wird, faellt
# die App erst auf dem Handy um. Hier faellt sie vorher um.
set -u
APK=app/build/outputs/apk/release/app-release.apk
adb shell getprop ro.product.cpu.abilist
adb install -r "$APK" || exit 1
adb logcat -c

adb shell am start -W -n de.tagstock/.ui.MainActivity
sleep 10
if [ -z "$(adb shell pidof de.tagstock | tr -d '\r')" ]; then
  adb logcat -d | grep -A 40 "FATAL EXCEPTION"
  echo "Die App laeuft nach dem Start nicht mehr."
  exit 1
fi
echo "Gestartet."

# Zufaellige Bedienung: Reiter, Listen, Formulare, Dialoge.
adb shell monkey -p de.tagstock -s 4711 --throttle 150 --pct-syskeys 0 \
  --ignore-timeouts --ignore-security-exceptions -v 600 > monkey.txt 2>&1
tail -5 monkey.txt
adb logcat -d > logcat.txt
if grep -q "// CRASH: de.tagstock" monkey.txt || grep -q "FATAL EXCEPTION" logcat.txt; then
  grep -A 40 "FATAL EXCEPTION" logcat.txt | head -80
  grep -A 30 "// CRASH" monkey.txt | head -40
  echo "Die App ist bei der Bedienung abgestuerzt."
  exit 1
fi
echo "Startprobe bestanden."
