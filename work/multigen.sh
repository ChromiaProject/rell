#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

SCRIPT_DIR="$(dirname "$0")"
RELL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$RELL_DIR/rell-tools/build/install/rell-dist/postchain-node"

if [ ! -d "$DIST_DIR" ]; then
    echo "ERROR: $DIST_DIR not found."
    echo "Run: ./gradlew :rell-tools:installRellDist"
    exit 1
fi

exec "$DIST_DIR/multigen.sh" "$@"
