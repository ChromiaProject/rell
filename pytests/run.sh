#!/bin/bash
#
# Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
#

set -eu

cd "$(dirname $0)"
pytest-3 -v -s "$@"
