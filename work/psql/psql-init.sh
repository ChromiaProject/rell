#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu

MYDIR=$(dirname "$(readlink -f "$0")")
F=$MYDIR/init.sql
psql -h 127.0.0.1 -p 5432 -U postgres -w -f "$F"
