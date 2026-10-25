#!/bin/sh
# Builds Wave for macOS: Wave.app, then a .dmg to drag it into Applications.
# The launcher is marked as built for the current macOS SDK so macOS gives Wave's window the
# current look (Liquid Glass buttons), and the app is signed ad hoc so it runs.
set -e
cd "$(dirname "$0")/.."
VERSION=$(grep 'val waveVersion' desktop/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
./gradlew -q :desktop:createReleaseDistributable
APP=desktop/build/compose/binaries/main-release/app/Wave.app
SDK=$(xcrun --show-sdk-version)
LAUNCHER="$APP/Contents/MacOS/Wave"
vtool -set-build-version macos 11.0 "$SDK" -replace -output "$LAUNCHER.new" "$LAUNCHER"
mv "$LAUNCHER.new" "$LAUNCHER"
chmod +x "$LAUNCHER"
codesign --force --deep -s - "$APP"
OUT=dist/Wave-$VERSION-macos-arm64.dmg
STAGE=$(mktemp -d)
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
rm -f "$OUT"
hdiutil create -volname "Wave" -srcfolder "$STAGE" -ov -format ULFO "$OUT" >/dev/null
rm -rf "$STAGE"
echo "$OUT"
