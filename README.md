# Rell Dokka Plugin

[![Kotlin Alpha](https://kotl.in/badges/alpha.svg)](https://kotlinlang.org/docs/components-stability.html)

This repository defines the rell dokka plugin for generating docs from rell sources

### Usage

This project has a CLI that can be used to generate a site.

```shell
$ ./gradlew run --args="--source /path/to/rell/src --target <out> --modules module1,module2"
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

In order to test your plugin locally, please use the `publishToMavenLocal` task, as explained in the [Applying the plugin](#applying-the-plugin) section.

#### Publishing to Maven Central

Publishing extension has been preconfigured for deployment to Maven Central repository via OSSRH.
A jar file with documentation (`javadoc.jar`) is created with Dokka.
In order to sigh the publication, you have to provide one of the following sets of environmental variables:

1) * SIGN_KEY_ID - The public key ID (The last 8 symbols of the keyId)
   * SIGN_KEY - The secret (private) key
   * SIGN_KEY_PASSPHRASE - The passphrase used to protect your private key
   
2) * SIGN_KEY - The secret (private) key
   * SIGN_KEY_PASSPHRASE - The passphrase used to protect your private key
  
For more information about signing the publication, please refer to the [Signing Plugin readme](https://docs.gradle.org/current/userguide/signing_plugin.html).

OSSRH credentials also have to be provided via
 
* SONATYPE_USER 
* SONATYPE_PASSWORD
    
environmental variables. 

Please follow the [OSSRH guide](https://central.sonatype.org/pages/ossrh-guide.html) for the detailed steps on how to get the credentials and claim the group name.

Finally, the publication can be started with `./gradlew publish` command

### Final words
After creating a plugin please consider sharing it with the community on [official Dokka plugins list](https://kotlin.github.io/dokka/1.8.10/community/plugins-list/)
