package net.postchain.rell.toolbox.util

import net.postchain.rell.toolbox.lsp.LauncherType
import org.apache.logging.log4j.core.config.Configurator

fun initializeLogger(debug: Boolean, launcherType: LauncherType) {

    //TODO: Consider if we should build config completely programatically instead of reading configs from files
    //https://www.baeldung.com/log4j2-programmatic-config
    val classLoader = ClassLoader.getSystemClassLoader()
    val configFileName = when {
        launcherType == LauncherType.SOCKET -> classLoader.getResource("log4j2-socket.properties")
        launcherType == LauncherType.STDIO && debug -> classLoader.getResource("log4j2-debug-stdio.properties")
        launcherType == LauncherType.STDIO && !debug -> classLoader.getResource("log4j2-null.properties")
        else -> {
            ""
        }
    }
    if (configFileName != null) {
        Configurator.initialize(
            null,
            configFileName.toString()
        )
    } else {
        println("No logger is initialized")
    }
}
