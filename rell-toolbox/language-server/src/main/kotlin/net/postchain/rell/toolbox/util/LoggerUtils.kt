/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.util

import net.postchain.rell.toolbox.jacoco.annotations.ExcludeFromJacocoGeneratedReport
import net.postchain.rell.toolbox.lsp.server.LauncherType
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.RollingFileAppender

@ExcludeFromJacocoGeneratedReport
fun initializeLogger(logLevel: Level, launcherType: LauncherType) {
    val loggerContext = LoggerContext.getContext(false)
    val appenders = loggerContext.configuration.appenders

    val rootLogger = LogManager.getLogger("") as Logger
    rootLogger.level = logLevel

    // Configure logger for STDIO launcher. For socket launcher it uses properties from log4j2.properties
    if (launcherType == LauncherType.STDIO) {
        rootLogger.removeAppender(appenders["ConsoleLogger"])
        rootLogger.addAppender(appenders["RollingFile"])
    }
}

fun getCurrentLogFileName(): String {
    val loggerContext = LoggerContext.getContext(false)
    val config = loggerContext.configuration
    val appender = config.appenders["RollingFile"] // Replace with the name of your appender
    if (appender is RollingFileAppender) {
        return appender.fileName
    }
    return ""
}
