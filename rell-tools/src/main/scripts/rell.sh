#!/bin/bash
#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

set -eu
D=`dirname "${BASH_SOURCE[0]}"`
exec "$D/javarun.sh" net.postchain.rell.tools.RellCLIKt "$@"
