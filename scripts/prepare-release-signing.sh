#!/usr/bin/env bash
# Validate signing secrets, decode the keystore, and verify it with keytool.
set -euo pipefail

keystore_path="${SIGNING_STORE_FILE:-release.keystore}"

for var in SIGNING_KEYSTORE_BASE64 SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required signing secret: $var" >&2
    exit 1
  fi
done

printf '%s' "$SIGNING_KEYSTORE_BASE64" | base64 --decode > "$keystore_path"

if [[ ! -s "$keystore_path" ]]; then
  echo "Decoded keystore is empty or missing: $keystore_path" >&2
  exit 1
fi

keytool -list \
  -keystore "$keystore_path" \
  -storepass "$SIGNING_STORE_PASSWORD" \
  -alias "$SIGNING_KEY_ALIAS" >/dev/null

echo "Release keystore validated at $keystore_path"
