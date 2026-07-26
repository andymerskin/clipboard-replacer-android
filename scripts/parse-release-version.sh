#!/usr/bin/env bash
# Parse vMAJOR.MINOR.PATCH into versionName and versionCode.
# versionCode = major*10000 + minor*100 + patch (minor and patch must be < 100).
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <tag>" >&2
  exit 1
fi

tag="$1"
version="${tag#v}"

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Tag must be vMAJOR.MINOR.PATCH (e.g. v0.1.1), got: $tag" >&2
  exit 1
fi

IFS='.' read -r major minor patch <<< "$version"

for component in "$major" "$minor" "$patch"; do
  if [[ "$component" =~ ^0[0-9]+$ ]]; then
    echo "Version components must not have leading zeros: $tag" >&2
    exit 1
  fi
done

if (( minor >= 100 || patch >= 100 )); then
  echo "minor and patch must be < 100 for versionCode encoding: $tag" >&2
  exit 1
fi

version_code=$((major * 10000 + minor * 100 + patch))

echo "VERSION_NAME=$version"
echo "VERSION_CODE=$version_code"
