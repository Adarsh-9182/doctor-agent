#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
APP="$PROJECT_ROOT/dist/Doctor Agent.app"
CONTENTS="$APP/Contents"
BUILD="$PROJECT_ROOT/dist/mac-build"

mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources" "$BUILD"
swiftc -target arm64-apple-macosx14.0 -parse-as-library \
  -framework SwiftUI -framework AppKit -framework AVFoundation \
  "$PROJECT_ROOT/native/mac/DoctorAgentMac.swift" \
  -o "$BUILD/DoctorAgent-arm64"
swiftc -target x86_64-apple-macosx14.0 -parse-as-library \
  -framework SwiftUI -framework AppKit -framework AVFoundation \
  "$PROJECT_ROOT/native/mac/DoctorAgentMac.swift" \
  -o "$BUILD/DoctorAgent-x86_64"
lipo -create "$BUILD/DoctorAgent-arm64" "$BUILD/DoctorAgent-x86_64" \
  -output "$CONTENTS/MacOS/DoctorAgent"
cp "$PROJECT_ROOT/knowledge.json" "$CONTENTS/Resources/knowledge.json"
cat > "$CONTENTS/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key><string>DoctorAgent</string>
  <key>CFBundleIdentifier</key><string>org.doctoragent.desktop</string>
  <key>CFBundleName</key><string>Doctor Agent</string>
  <key>CFBundleDisplayName</key><string>Doctor Agent</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleVersion</key><string>0.2.0</string>
  <key>CFBundleShortVersionString</key><string>0.2.0</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>NSHighResolutionCapable</key><true/>
  <key>NSSupportsAutomaticGraphicsSwitching</key><true/>
</dict>
</plist>
PLIST
codesign --force --deep --sign - "$APP"
printf 'Built %s\n' "$APP"
