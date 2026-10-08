#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_PROJECT="$PROJECT_ROOT/native/android"

if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
elif [ -z "${JAVA_HOME:-}" ] && [ -d /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
  export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -z "${ANDROID_HOME:-}" ]; then
  export ANDROID_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
fi
if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools" ]; then
  printf 'Android SDK platform 35 and build-tools are required. Set ANDROID_HOME to your installed SDK.\n' >&2
  exit 1
fi
# A clean archive avoids retaining deleted model bytes in an incremental APK.
"$ANDROID_PROJECT/gradlew" --project-dir "$ANDROID_PROJECT" clean assembleDebug "$@"
printf 'Built %s\n' "$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
