#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

set -euo pipefail

# Script to generate Rell documentation for preview using Gradle-published artifacts

ROOT_DIR=$(cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ ! -f "build.gradle.kts" ]; then
  echo "Please run from repository root (build.gradle.kts not found)." >&2
  exit 1
fi

echo "Generating Rell documentation preview..."

# Build chr against the local Rell build. buildLocalChr depends on :publishRellToMavenLocal, so the
# Rell snapshot lands in ~/.m2 within the same build graph (no nested Gradle).
./gradlew :performance:buildLocalChr

# Create output directory
mkdir -p libdoc

# Generate documentation with the freshly built chr distribution.
CHR="chromia-cli-local/chromia-cli/target/chromia-cli-dev-dist/bin/chr"
JAVA_ARGS="${JAVA_ARGS:-} --enable-native-access=ALL-UNNAMED" \
    "$CHR" generate docs-site --system -d libdoc

echo "Documentation preview generated in the libdoc directory"
