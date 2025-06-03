/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.shell

import com.google.common.base.Throwables
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.repl.ReplOutputChannel
import net.postchain.rell.base.repl.ReplOutputChannelFactory
import net.postchain.rell.base.repl.ReplValueFormat
import net.postchain.rell.base.repl.ReplValueFormatter
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

object CliReplOutputChannelFactory: ReplOutputChannelFactory {
    override fun createOutputChannel(): ReplOutputChannel {
        return CliReplOutputChannel
    }
}

private object CliReplOutputChannel: ReplOutputChannel {
    private var valueFormat = ReplValueFormat.ONE_ITEM_PER_LINE

    override fun printInfo(msg: String) {
        println(msg)
    }

    override fun printCompilerError(code: String, msg: String) {
        println(msg)
    }

    override fun printCompilerMessage(message: C_Message) {
        println(message)
    }

    override fun printRuntimeError(e: Rt_Exception) {
        val msg = "Run-time error: ${e.message}"
        val fullMsg = Rt_Utils.appendStackTrace(msg, e.info.stack)
        println(fullMsg.trim())
    }

    override fun printPlatformRuntimeError(e: Throwable) {
        val s = Throwables.getStackTraceAsString(e).trim()
        println("Run-time error: $s")
    }

    override fun setValueFormat(format: ReplValueFormat) {
        valueFormat = format
    }

    override fun printValue(value: Rt_Value) {
        val s = ReplValueFormatter.format(value, valueFormat)
        if (s != null) {
            println(s)
        }
    }

    override fun printControl(code: String, msg: String) {
        println(msg)
    }
}
