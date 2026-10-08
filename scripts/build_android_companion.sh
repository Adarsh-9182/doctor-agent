#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 "$PROJECT_ROOT/scripts/download_starter_model.py"
bash "$PROJECT_ROOT/scripts/build_android.sh" "-PbundleStarterModel=$PROJECT_ROOT/dist/models/qwen3_0_6b_mixed_int4.litertlm"
cp "$PROJECT_ROOT/native/android/app/build/outputs/apk/debug/app-debug.apk" "$PROJECT_ROOT/dist/Doctor-Agent-android-0.8.0-with-model-debug.apk"
printf 'Model-included APK: %s\n' "$PROJECT_ROOT/dist/Doctor-Agent-android-0.8.0-with-model-debug.apk"
