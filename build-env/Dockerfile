# Linux build environment for Easy JS8 (JS8Android fork).
# Mirrors .github/workflows/android-build.yml so Windows can build the app.
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      openjdk-17-jdk-headless autoconf automake libtool pkg-config make \
      curl wget unzip git ca-certificates file patch \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_VERSION=26.1.10909125
ENV ANDROID_NDK_ROOT=/opt/android-sdk/ndk/26.1.10909125
ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/26.1.10909125

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -sL -o /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q /tmp/tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/tools.zip

ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
       "ndk;${ANDROID_NDK_VERSION}" "cmake;3.22.1"

WORKDIR /src
