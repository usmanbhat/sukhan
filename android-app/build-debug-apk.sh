#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
cd "$ROOT"
./gradlew --no-daemon :app:clean :app:assembleDebug
APK="$ROOT/.fresh-build/app/outputs/apk/debug/app-debug.apk"
OUT="$ROOT/dist/Sukhan-offline-demo-debug.apk"
mkdir -p "$ROOT/dist"
test -f "$APK"
cp "$APK" "$OUT"
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify "$OUT"
sha256sum "$OUT"
printf 'Ready: %s\n' "$OUT"
