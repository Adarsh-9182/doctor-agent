#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 "$PROJECT_ROOT/scripts/download_starter_model.py"
bash "$PROJECT_ROOT/scripts/build_android.sh" "-PbundleStarterModel=$PROJECT_ROOT/dist/models/qwen3_0_6b_mixed_int4.litertlm"
ANDROID_VERSION=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$PROJECT_ROOT/native/android/app/build.gradle.kts")
[ -n "$ANDROID_VERSION" ] || { printf 'Android version is missing.\n' >&2; exit 1; }
OUTPUT="$PROJECT_ROOT/dist/Doctor-Agent-android-$ANDROID_VERSION-with-model-debug.apk"
cp "$PROJECT_ROOT/native/android/app/build/outputs/apk/debug/app-debug.apk" "$OUTPUT"
printf 'Model-included APK: %s\n' "$OUTPUT"
