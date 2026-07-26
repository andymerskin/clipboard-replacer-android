#!/usr/bin/env bash
# Verify tag-driven version props produce the expected APK names.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

eval "$( "$root/scripts/parse-release-version.sh" v0.1.1 )"

debug_apk="app/build/outputs/apk/debug/ClipboardReplacer-${VERSION_NAME}-debug.apk"
release_apk="app/build/outputs/apk/release/ClipboardReplacer-${VERSION_NAME}-release.apk"

./gradlew :app:assembleDebug --quiet \
  -PVERSION_NAME="$VERSION_NAME" \
  -PVERSION_CODE="$VERSION_CODE"

if [[ ! -f "$debug_apk" ]]; then
  echo "Expected debug APK not found: $debug_apk" >&2
  ls -la app/build/outputs/apk/debug/ >&2 || true
  exit 1
fi

echo "Verified debug APK: $debug_apk (versionName=$VERSION_NAME, versionCode=$VERSION_CODE)"

keystore_properties="$root/keystore.properties"
if [[ ! -f "$keystore_properties" ]]; then
  echo "Skipping release APK verification (keystore.properties not found)"
  exit 0
fi

while IFS='=' read -r key value; do
  [[ -z "$key" || "$key" =~ ^[[:space:]]*# ]] && continue
  key="${key//[[:space:]]/}"
  value="${value//$'\r'/}"
  case "$key" in
    storeFile) export SIGNING_STORE_FILE="$root/$value" ;;
    storePassword) export SIGNING_STORE_PASSWORD="$value" ;;
    keyAlias) export SIGNING_KEY_ALIAS="$value" ;;
    keyPassword) export SIGNING_KEY_PASSWORD="$value" ;;
  esac
done < "$keystore_properties"

for var in SIGNING_STORE_FILE SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "Incomplete keystore.properties; skipping release APK verification" >&2
    exit 0
  fi
done

export REQUIRE_RELEASE_SIGNING=true

./gradlew :app:assembleRelease --quiet \
  -PVERSION_NAME="$VERSION_NAME" \
  -PVERSION_CODE="$VERSION_CODE"

if [[ ! -f "$release_apk" ]]; then
  echo "Expected release APK not found: $release_apk" >&2
  ls -la app/build/outputs/apk/release/ >&2 || true
  exit 1
fi

"$root/scripts/verify-release-apk.sh" "$release_apk"
echo "Verified release APK: $release_apk (versionName=$VERSION_NAME, versionCode=$VERSION_CODE)"
