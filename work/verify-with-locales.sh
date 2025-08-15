#!/usr/bin/env bash
# Runs `mvn verify` under several locales to catch locale-specific issues.
# Usage: start from the project root or anywhere; the script cd's to project root.

set -euo pipefail

# Determine project root (this script lives in work/)
SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
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
  echo "==== Running mvn verify for locale ${LANG}_${COUNTRY} ===="
  mvn -Ptest-locale -Duser.language="$LANG" -Duser.country="$COUNTRY" verify
  echo
done

echo "All locales finished successfully."
