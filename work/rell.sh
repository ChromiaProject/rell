#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

SCRIPT_DIR="$(dirname "$0")"
RELL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ARGS=()
if [ $# -gt 0 ]; then
    ARGS+=(--args="$*")
fi

exec "$RELL_DIR/gradlew" -q --console=plain :rell-tools:runRepl "${ARGS[@]}"
