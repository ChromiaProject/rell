## Language Info

Introduction: https://docs.chromia.com/rell/rell-intro

Library RellDoc (beta): https://docs.chromia.com/pages/rell/index.html

Release notes (detailed changelog): https://gitlab.com/chromaway/rell/-/tree/dev/doc/release-notes

## Build and Run

Requirements:
- Java 21
- Maven

Build (w/o unit tests - they require database setup):

```shell
mvn clean install -DskipTests -Pdistro
```

### Run Rell shell (REPL):

```shell
work/rell.sh
```

Example:

```text
Rell 0.15.0-SNAPSHOT
Type '\q' to quit or '\?' for help.
>>> 2+2
4
>>> range(10) @* {} (@sum $)
45
>>>
```

### Run a program:

Create a file `hello.rell`:

```rell
module;

function main() {
    print('Hello, world!');
}
```

Run it (specify the parent directory of `hello.rell` via `-d`):

```shell
work/rell.sh -d . hello main
```

Output:

```text
Hello, world!
```

## Running Unit Tests

First, set up a PostgreSQL database and user:

```
CREATE USER "postchain";
ALTER USER "postchain" WITH PASSWORD 'postchain';
CREATE DATABASE "postchain" WITH TEMPLATE = template0 LC_COLLATE = 'C.UTF-8' LC_CTYPE = 'C.UTF-8' ENCODING 'UTF-8';
GRANT ALL PRIVILEGES ON DATABASE "postchain" TO "postchain";
```

Database connection configuration for tests is taken from the file `src/test/resources/rell-db-config.properties`.

**WARNING**: Unit tests drop all existing tables in the specified database, so make sure you specify a right database.

To run unit tests in IntelliJ, launch the `All_tests` configuration (`work/All_tests.run.xml`).

## Generating Rell grammar test snippets

### Using IntelliJ:
1. Create run configuration from `work/Test_snippets.run.xml` file
2. Run it

Archive will be created in `user.home` directory, with name `testsources-<RELL_VERSION>.zip`

**Do not run snippet generation using Maven, currently it's not supported**

## Copyright & License information

Copyright (c) 2017-2025 ChromaWay AB. All rights reserved.

This software can used either under terms of commercial license
obtained from ChromaWay AB, or, alternatively, under the terms
of the GNU General Public License with additional linking exceptions.
See file LICENSE for details.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
