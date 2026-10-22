#!/bin/bash
# inject.sh "<text>" <freq> <type> [snr]
ADB="/c/Users/Robert/AppData/Local/Android/Sdk/platform-tools/adb.exe -s emulator-5600"
$ADB shell am broadcast -n com.easyjs8.app/com.js8call.example.debug.DebugDecodeReceiver --es text "\"$1\"" --ef freq $2 --ei type $3 --ei snr ${4:--10} >/dev/null
