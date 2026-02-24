#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

SCRIPT_DIR="$(dirname "$0")"
RELL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$RELL_DIR/rell-tools/build/install/rell-dist/postchain-node"

if [ ! -d "$DIST_DIR" ]; then
    echo "Running install task..."
    cd "$RELL_DIR"
    ./gradlew installRellDist || {
        echo "ERROR: Failed to build Rell distribution"
        exit 1
    }
fi

exec "$DIST_DIR/rell.sh" "$@"
