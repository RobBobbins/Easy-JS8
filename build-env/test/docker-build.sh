#!/bin/bash
S="${SHOTS:-/e/new-w3bguru-websites/easy js8/build-env/test/out}"; mkdir -p "$S"
MSYS_NO_PATHCONV=1 docker run --rm -v "E:/new-w3bguru-websites/easy js8:/src" -v easyjs8-gradle:/root/.gradle -v easyjs8-android:/root/.android -v easyjs8-ndk:/opt/android-sdk/ndk easyjs8-build bash /src/build-env/build.sh > "$S/build.log" 2>&1
echo EXIT $?; grep -E "^e: |^w: .*js8ui|BUILD|error:" "$S/build.log" | head -30
