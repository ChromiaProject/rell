#
# Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
#

docker run --net=host --mount type=bind,source="$(pwd)",target="/rell" --rm --tty rell-pytests
