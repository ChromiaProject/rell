#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu

MAIN_CLASS=${1?"Specify main class!"}
shift

D=`dirname "${BASH_SOURCE[0]}"`

JAR_FILE=`echo "$D"/lib/rell-tools-*.*.*.jar`
CP="$JAR_FILE:$D/extra/*"

exec ${RELL_JAVA:-java} -cp "$CP" "$MAIN_CLASS" "$@"
