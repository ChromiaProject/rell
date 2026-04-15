# Rell Dokka Plugin

[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)

Dokka plugin for generating documentation from Rell sources.

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
in IntelliJ with your plugin, run the remote configuration.

For more information see the [official IntelliJ guide](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html#67dc8).
