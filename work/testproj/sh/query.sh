#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu
REQ="${1?"Specify request body!"}"
URL="http://localhost:7740/query/iid_1"
curl -s "$URL" -X POST -d "$REQ"
