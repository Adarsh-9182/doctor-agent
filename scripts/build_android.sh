#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_PROJECT="$PROJECT_ROOT/native/android"

bash "$PROJECT_ROOT/scripts/prepare_android.sh"
"$ANDROID_PROJECT/gradlew" --project-dir "$ANDROID_PROJECT" assembleDebug
printf 'Built %s\n' "$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
