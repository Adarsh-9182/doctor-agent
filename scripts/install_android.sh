#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB_PATH="$SDK_PATH/platform-tools/adb"
APK_PATH="${1:-$PROJECT_ROOT/dist/Doctor-Agent-android-0.8.0-with-model-debug.apk}"
if [ ! -x "$ADB_PATH" ]; then
  printf 'Install Android SDK platform-tools or set ANDROID_HOME.\n' >&2
  exit 1
fi
if [ ! -f "$APK_PATH" ]; then
  printf 'APK not found: %s\n' "$APK_PATH" >&2
  exit 1
fi
if ! "$ADB_PATH" -d get-state >/dev/null 2>&1; then
  "$ADB_PATH" devices -l
  printf 'Connect one Android phone by USB, enable USB debugging, and authorize this Mac on the phone.\n' >&2
  exit 1
fi
"$ADB_PATH" -d install -r "$APK_PATH"
"$ADB_PATH" -d shell am start -n org.doctoragent.mobile/.MainActivity
