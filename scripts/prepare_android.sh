#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_PROJECT="$PROJECT_ROOT/native/android"

mkdir -p "$ANDROID_PROJECT/app/src/main/assets"
cp "$PROJECT_ROOT/knowledge.json" "$ANDROID_PROJECT/app/src/main/assets/knowledge.json"
printf 'Prepared Android project at %s\n' "$ANDROID_PROJECT"
printf 'Open that folder in Android Studio to build/install the debug APK.\n'
