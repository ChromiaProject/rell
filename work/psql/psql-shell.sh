#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

PGPASSWORD=postchain psql -h 127.0.0.1 -p 5432 -U postchain -w "$@"
