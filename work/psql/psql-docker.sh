#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

MYDIR=$(dirname "$(readlink -f "$0")")

for C in docker psql; do
    if ! command -v $C &> /dev/null; then
        echo "Error: $C command is not available. Please install it and try again." >&2
        exit 1
    fi
done

PASSWORD=$(head -c32 /dev/urandom | base64)

log() { echo "$(date -u +'%F %T.%3N') $1"; }

init() {
    SLEEP=5
    log "Sleeping for ${SLEEP}s before init"
    sleep $SLEEP
    log "Init start"
    PGPASSWORD=$PASSWORD "$MYDIR/psql-init.sh"
    log "Init done"
}

log "Main loop start"

while true; do
    init &
    INIT_PID=$!

    docker run --tmpfs=/pgtmpfs:size=2000m -p 127.0.0.1:5432:5432 -e PGDATA=/pgtmpfs -e POSTGRES_PASSWORD="$PASSWORD" postgres:10-alpine
    wait $INIT_PID

    SLEEP=2
    log ""
    log "Docker stopped; sleeping for ${SLEEP}s and restarting"
    log ""
    sleep $SLEEP
done
