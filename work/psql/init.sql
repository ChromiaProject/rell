/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

CREATE USER "postchain";
ALTER USER "postchain" WITH PASSWORD 'postchain';

CREATE DATABASE "postchain" WITH TEMPLATE = template0 LC_COLLATE = 'C.UTF-8' LC_CTYPE = 'C.UTF-8' ENCODING 'UTF-8';
GRANT ALL PRIVILEGES ON DATABASE "postchain" TO "postchain";

CREATE DATABASE "wrong_collation" WITH TEMPLATE = template0 LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8' ENCODING 'UTF-8';
GRANT ALL PRIVILEGES ON DATABASE "wrong_collation" TO "postchain";
