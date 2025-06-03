/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import java.io.OutputStream
import java.io.PrintStream
import java.util.logging.LogManager

object RellToolsLogUtils {
    fun initLogging() {
        System.setProperty("java.util.logging.config.class", RellJavaLoggingInit::class.java.name)

        val log4jKey = "log4j.configurationFile"
        if (System.getProperty(log4jKey) == null) {
            System.setProperty(log4jKey, "log4j2-rell-console.xml")
        }

        initLoggingStdOutErr()
        disableIllegalAccessWarning()
    }

    private fun initLoggingStdOutErr() {
        fun suppressMessage(s: String): Boolean {
            if (s == "WARNING: sun.reflect.Reflection.getCallerClass is not supported. This will impact performance.") {
                // Log4j: org.apache.logging.log4j.util.StackLocator (log4j-api-2.11.2)
                return true
            }

            return s.startsWith("SLF4J: ")
                    || s.endsWith(" org.apache.commons.beanutils.FluentPropertyBeanIntrospector introspect")
                    || s.startsWith("INFO: Error when creating PropertyDescriptor for public final void org.apache.commons.configuration2.AbstractConfiguration.")
        }

        fun filterStream(outs: OutputStream): PrintStream {
            return object : PrintStream(outs, true) {
                override fun println(s: String?) {
                    if (s != null && suppressMessage(s)) return
                    super.println(s)
                }
            }
        }

        System.setOut(filterStream(System.out))
        System.setErr(filterStream(System.err))
    }

    /**
     * Suppress famous Java 9+ warning about illegal reflective access
     * ("WARNING: An illegal reflective access operation has occurred").
     */
    private fun disableIllegalAccessWarning() {
        try {
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe

            val loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger")
            val loggerField = loggerClass.getDeclaredField("logger")
            unsafe.putObjectVolatile(loggerClass, unsafe.staticFieldOffset(loggerField), null)
        } catch (e: Throwable) {
            // ignore
        }
    }
}

class RellJavaLoggingInit {
    init {
        javaClass.getResourceAsStream("/rell_logging.properties")?.use { ins ->
            LogManager.getLogManager().readConfiguration(ins)
        }
    }
}
