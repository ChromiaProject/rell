#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu

MAIN_CLASS=${1?"Specify main class!"}
shift

D=`dirname "${BASH_SOURCE[0]}"`

CP="$D/lib/*:$D/extra/*"

exec ${RELL_JAVA:-java} -cp "$CP" "$MAIN_CLASS" "$@"
