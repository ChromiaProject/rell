# Rell Docgen

[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)

Self-contained HTML documentation generator for Rell sources and for the in-process Rell standard library.

The `rell-dokka-plugin` module name and the `dokka` package segments are historical: the tool started life as a
JetBrains Dokka plugin, but no longer depends on Dokka. It walks the Rell compiler model directly and renders the
site itself.

## Documentation

- [Introduction](./docs/Introduction.md)
- [Architecture](./docs/Architecture.md)
- [Functionality](./docs/Functional.md)
- [Setup & Development](./docs/Setup.md)

### Usage

```shell
./gradlew :rell-dokka-plugin:run --args="--source /path/to/rell/src --target <out> --modules module1,module2"
```

### Debugging

Enable the debugger in the project you wish to generate documentation for using `org.gradle.debug = true` and,
in IntelliJ with this module open, run the remote configuration.

For more information see the [official IntelliJ guide](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html#67dc8).
