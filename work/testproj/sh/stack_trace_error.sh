#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

set -eu
$(dirname $0)/query.sh '{"type":"error_q","e":"'$1'"}' | jq -r '.error'
