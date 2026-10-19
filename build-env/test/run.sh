#!/bin/bash
# usage: run.sh <shotname> [install]
ADB="/c/Users/Robert/AppData/Local/Android/Sdk/platform-tools/adb.exe -s emulator-5554"
S="${SHOTS:-/e/new-w3bguru-websites/easy js8/build-env/test/out}"; mkdir -p "$S"
if [ "$2" = install ]; then
  $ADB install -r "E:/new-w3bguru-websites/easy js8/android/app/build/outputs/apk/debug/app-debug.apk" | tail -1
  for p in RECORD_AUDIO POST_NOTIFICATIONS BLUETOOTH_CONNECT BLUETOOTH_SCAN; do $ADB shell pm grant com.easyjs8.app android.permission.$p 2>/dev/null; done
  $ADB shell am force-stop com.easyjs8.app; $ADB logcat -c
  $ADB shell am start -n com.easyjs8.app/com.js8call.example.MainActivity >/dev/null
  timeout 20 $ADB shell 'sleep 5'
fi
[ -n "$ROT" ] && { $ADB shell cmd window user-rotation lock $ROT; timeout 10 $ADB shell "sleep 3"; }
$ADB exec-out screencap -p > "$S/$1.png"
$ADB logcat -d -b crash | tail -25
