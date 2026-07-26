#!/usr/bin/env bash
# Verify tag-driven version props produce the expected APK name.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

eval "$( "$root/scripts/parse-release-version.sh" v0.1.1 )"
expected_apk="app/build/outputs/apk/debug/ClipboardReplacer-${VERSION_NAME}-debug.apk"

./gradlew :app:assembleDebug --quiet \
  -PVERSION_NAME="$VERSION_NAME" \
  -PVERSION_CODE="$VERSION_CODE"

if [[ ! -f "$expected_apk" ]]; then
  echo "Expected APK not found: $expected_apk" >&2
  ls -la app/build/outputs/apk/debug/ >&2 || true
  exit 1
fi

echo "Verified APK: $expected_apk (versionName=$VERSION_NAME, versionCode=$VERSION_CODE)"
