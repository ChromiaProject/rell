#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

set -eu

SCRIPT_DIR="$(dirname "$0")"
RELL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DIST_DIR="$RELL_DIR/rell-tools/build/install/rell-dist/postchain-node"

# Build the local distribution (quietly).
"$RELL_DIR/gradlew" -q --console=plain :rell-tools:installRellDist

exec "$DIST_DIR/rell.sh" "$@"
