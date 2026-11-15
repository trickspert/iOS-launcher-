#!/usr/bin/env bash
# Installs the APK on an emulator, uses it as the home screen and fails on any crash.
# Small screenshots are printed base64-encoded so they can be read straight from the job log.
set -u
PKG=dev.liquidglass.launcher
mkdir -p shots

N=0
shot() {
  N=$((N+1))
  adb exec-out screencap -p > "shots/$(printf %02d $N)-$1.png"
  echo "screenshot $N: $1"
}

alive() { adb shell pidof $PKG >/dev/null 2>&1; }

adb install -r GlassLauncher.apk
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 20
shot start

adb shell cmd package set-home-activity $PKG/.MainActivity || true
adb shell input keyevent KEYCODE_HOME
sleep 5
shot home

# Pull down for search, then dismiss.
adb shell input swipe 540 900 540 1600 350
sleep 3
shot search
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2

# Next page, then the App Library.
adb shell input swipe 950 1300 120 1300 260
sleep 3
shot page2
# Open the first folder on page 2 (live-refracting glass panel), then close it.
adb shell input tap 657 270
sleep 3
shot folder
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input swipe 950 1300 120 1300 260
sleep 3
shot library

adb shell input keyevent KEYCODE_HOME
sleep 3
# Long-press an icon (menu) then release.
adb shell input swipe 400 1150 400 1150 1200
sleep 2
shot longpress
adb shell input keyevent KEYCODE_BACK
sleep 1

# Long-press empty space → edit mode, open Customize sheet.
adb shell input swipe 760 1680 760 1680 1200
sleep 2
shot edit
# Customize sheet
adb shell input tap 290 60
sleep 3
shot settings
# Scroll the sheet: first from a plain area, then starting on a control.
adb shell input swipe 1045 1500 1045 900 600
sleep 2
shot settings-scrolled
# Swipe up over the sliders: must scroll, not move a slider.
adb shell input swipe 540 1700 540 700 700
sleep 2
adb shell input swipe 1045 1800 1045 600 700
sleep 2
shot settings-bottom
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 2
# Drag an icon across the grid (long-press, move, drop).
adb shell input draganddrop 400 1400 800 1700 1800 || true
sleep 2
shot dragged
adb shell input keyevent KEYCODE_HOME
sleep 2

# Launch another app and come back (exercises the return animation).
adb shell am start -a android.settings.SETTINGS
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 4
shot returned

adb logcat -d > shots/logcat.txt
echo "===== frame stats ====="
adb shell dumpsys gfxinfo $PKG > shots/gfxinfo.txt || true
grep -E "Total frames|Janky frames|percentile" shots/gfxinfo.txt || true

status=0
if grep -q "FATAL EXCEPTION" shots/logcat.txt; then
  echo "===== CRASH ====="
  grep -A 40 "FATAL EXCEPTION" shots/logcat.txt | head -120
  status=1
fi
if ! alive; then
  echo "===== launcher process is not running ====="
  grep -E "AndroidRuntime|$PKG" shots/logcat.txt | tail -60
  status=1
fi
echo "===== launcher warnings ====="
PID_LINES=$(grep -E " (W|E) " shots/logcat.txt | grep -iE "liquidglass|AndroidRuntime|Choreographer|RuntimeShader" | tail -40)
echo "$PID_LINES"
echo "===== glass log ====="
grep -iE "RuntimeShader|AGSL|SkSL|glass" shots/logcat.txt | tail -20 || true
exit $status
