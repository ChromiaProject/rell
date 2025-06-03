#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu
CONFIG_FILE=$1

function GetProperty() {
    K=$1
    cat $CONFIG_FILE | grep -F "$K" | cut -d= -f2
}

BASE_URL=$(GetProperty "database.url")
USERNAME=$(GetProperty "database.username")
PASSWORD=$(GetProperty "database.password")
SCHEMA=$(GetProperty "database.schema")

URL="$BASE_URL?user=$USERNAME&password=$PASSWORD&currentSchema=$SCHEMA"
echo $URL
