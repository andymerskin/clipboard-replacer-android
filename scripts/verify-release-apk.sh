#!/usr/bin/env bash
# Verify a release APK is signed and installable.
set -euo pipefail

apk="${1:?Usage: $0 <path-to-apk>}"

if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 1
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root/build-tools" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME with build-tools is required" >&2
  exit 1
fi

apksigner="$(find "$sdk_root/build-tools" -name apksigner -type f 2>/dev/null | sort -V | tail -1)"
if [[ -z "$apksigner" ]]; then
  echo "apksigner not found under $sdk_root/build-tools" >&2
  exit 1
fi

"$apksigner" verify --print-certs "$apk"
echo "Verified signed APK: $apk"
