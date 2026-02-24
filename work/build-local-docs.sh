#!/bin/bash
set -euo pipefail

# Script to generate Rell documentation for preview using Gradle-published artifacts

ROOT_DIR=$(cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

if [ ! -f "build.gradle.kts" ]; then
  echo "Please run from repository root (build.gradle.kts not found)." >&2
  exit 1
fi

echo "Generating Rell documentation preview..."

# Ensure Rell is available to chromia-cli
./gradlew publishToMavenLocal

# Create output directory
mkdir -p libdoc

# Generate documentation (skip redundant publish in local-chr)
./work/local-chr.sh --skip-publish generate docs-site --system -d libdoc

echo "Documentation preview generated in the libdoc directory"
