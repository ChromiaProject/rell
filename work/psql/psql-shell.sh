#!/bin/bash
PGPASSWORD=postchain psql -h 127.0.0.1 -p 5432 -U postchain -w "$@"
