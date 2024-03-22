# Rell Dokka Plugin

[![Kotlin Beta](https://kotl.in/badges/beta.svg)](https://kotlinlang.org/docs/components-stability.html)
[![Build Status](https://gitlab.com/chromaway/core-tools/rell-dokka-plugin/badges/dev/pipeline.svg)](https://gitlab.com/chromaway/core-tools/rell-dokka-plugin/)
[![Coverage Status](https://gitlab.com/chromaway/core-tools/rell-dokka-plugin/badges/dev/coverage.svg)](https://gitlab.com/core-tools/rell-dokka-plugin/)

This repository defines the rell dokka plugin for generating docs from rell sources

### Usage

This project has a CLI that can be used to generate a site.

```shell
$ ./gradlew run --args="--source /path/to/rell/src --target <out> --modules module1,module2"
```

### Running the server

To host the site using a simple web server, you can run the following command from the root of the target folder:
```shell
docker run -dit --name my-apache-app -p 8080:80 -v "$PWD":/usr/local/apache2/htdocs/ httpd:2.4
```

Alternatively, using node
```shell
npm init
npm install http-server
npx http-server
```

### Testing

This project includes a test dependency on `dokka-test-api` and `dokka-base-test-utils` that allows for easy testing. 
We highly encourage for you to extend tests classes with `BaseAbstractTest()` which allows you to write kotlin or java code
in your tests without a need to provide external files.
This way the tests are much cleaner and easier to reason about.

This repository contains most basic example of a [test using this mechanism](src/test/kotlin/template/MyAwesomePluginTest.kt).

### Debugging

Sometimes things don't work as we expected :) 

From our experience using debugger is the most efficient.
Apart from debugging tests you are able to debug whole projects while Dokka is running.
Enable the debugger in the project you wish to generate documentation for using `org.gradle.debug = true` and,
in intellij with your plugin, run the remote configuration.

For more information please visit [official intellij guide](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html#67dc8)

### Publishing

#### Publishing locally

In order to test your plugin locally, please use the `publishToMavenLocal` task.
