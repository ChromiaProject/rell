#!/usr/bin/env bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

# Runs `./gradlew check` under several locales to catch locale-specific issues.
# Usage: start from the project root or anywhere; the script cd's to project root.

set -euo pipefail

# Determine project root (this script lives in work/)
SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.."; pwd)
cd "$ROOT_DIR"

# Locales to test: language:COUNTRY pairs
LOCALES=(
  "tr:TR"  # Turkish (Turkey)
  "hi:IN"  # Hindi (India)
  "ar:SA"  # Arabian (Saudi Arabia)
  "ja:JP"  # Japanese (Japan)
)

for lc in "${LOCALES[@]}"; do
  IFS=":" read -r LANG COUNTRY <<< "$lc"
  echo "==== Running ./gradlew check for locale ${LANG}_${COUNTRY} ===="
  ./gradlew check -Puser.language="$LANG" -Puser.country="$COUNTRY"
  echo
done

echo "All locales finished successfully."
