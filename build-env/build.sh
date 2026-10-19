#!/usr/bin/env bash
# Runs inside the build-env container with the repo mounted at /src.
# Builds FFTW3 and Hamlib once (skipped when their output already exists),
# then builds the debug APK.
set -euo pipefail
cd /src

if [ ! -d android/libs/hamlib/arm64-v8a ]; then
  ./android/hamlib/build-hamlib-android.sh
fi

if [ ! -d android/libs/fftw3 ]; then
  (cd android && ./build-fftw3.sh)
fi

cd android
./gradlew --no-daemon assembleDebug
ls -l app/build/outputs/apk/debug/
