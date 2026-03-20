## Language Info

Introduction: https://docs.chromia.com/rell/rell-intro

Library RellDoc (beta): https://docs.chromia.com/pages/rell/index.html

Release notes (detailed changelog): https://gitlab.com/chromaway/rell/-/tree/dev/doc/release-notes

## Build and Run

Requirements:
- Java 21
- [Docker](https://docs.docker.com/get-started/get-docker/) (for PostgreSQL and Testcontainers-based integration tests)

| Command                     | Description                                                                    |
|-----------------------------|--------------------------------------------------------------------------------|
| `./gradlew assemble`        | Compile and package all JARs                                                   |
| `./gradlew build`           | Same as above, plus run all tests (requires PostgreSQL and Docker)             |
| `./gradlew clean`           | Clean the build outputs                                                        |
| `./gradlew installRellDist` | Install Rell runtime to `rell-tools/build/install/rell-dist/`                  |

### Run the Rell shell (REPL):

```shell
./work/rell.sh
```

Example session:

```text
Rell 0.16.0-SNAPSHOT
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
./work/rell.sh -d . hello main
```

Output:

```text
Hello, world!
```

## Copyright & License Information

Copyright (c) 2017–2025 ChromaWay AB. All rights reserved.

This software can be used either under the terms of a commercial license
obtained from ChromaWay AB or, alternatively, under the terms
of the GNU General Public License with additional linking exceptions.
See the LICENSE file for details.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
