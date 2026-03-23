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
