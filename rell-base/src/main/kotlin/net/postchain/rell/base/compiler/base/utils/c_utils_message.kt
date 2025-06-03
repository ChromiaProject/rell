/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import com.google.common.math.IntMath
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

enum class C_MessageType(val text: String, val ignorable: Boolean) {
    WARNING("Warning", true),
    ERROR("ERROR", false)
}

class C_Message(
        val type: C_MessageType,
        val pos: S_Pos,
        val code: String,
        val text: String
) {
    override fun toString(): String {
        return "$pos ${type.text}: $text"
    }
}

interface C_MessageManager {
    fun isError(): Boolean
    fun messages(): ImmList<C_Message>

    fun message(message: C_Message)
    fun message(type: C_MessageType, pos: S_Pos, lazyCodeMsg: Lazy<C_CodeMsg>)

    fun message(type: C_MessageType, pos: S_Pos, code: String, text: String) {
        message(C_Message(type, pos, code, text))
    }

    fun warning(pos: S_Pos, code: String, text: String) {
        message(C_MessageType.WARNING, pos, code, text)
    }

    fun error(pos: S_Pos, code: String, msg: String) {
        message(C_MessageType.ERROR, pos, code, msg)
    }

    fun error(error: C_Error) {
        error(error.pos, error.code, error.errMsg)
    }

    fun error(error: C_PosCodeMsg) {
        error(error.pos, error.code, error.msg)
    }

    fun error(pos: S_Pos, codeMsg: C_CodeMsg) {
        error(pos, codeMsg.code, codeMsg.msg)
    }

    fun error(pos: S_Pos, lazyCodeMsg: () -> C_CodeMsg) {
        message(C_MessageType.ERROR, pos, lazy(lazyCodeMsg))
    }

    fun <T> consumeError(code: () -> T): T? {
        return try {
            code()
        } catch (e: C_Error) {
            error(e)
            null
        }
    }

    fun errorWatcher(): C_ErrorWatcher
    fun firstErrorReporter(): C_FirstErrorReporter

    interface C_ErrorWatcher {
        fun hasNewErrors(): Boolean
    }

    interface C_FirstErrorReporter {
        fun error(pos: S_Pos, code: String, msg: String)
        fun error(pos: S_Pos, codeMsg: C_CodeMsg) = error(pos, codeMsg.code, codeMsg.msg)
    }
}

class C_DefaultMessageManager: C_MessageManager {
    private val messages = mutableListOf<C_InternalMessage>()
    private var errorCount = 0

    override fun isError() = errorCount > 0

    override fun message(message: C_Message) {
        messages.add(C_InternalMessage_Direct(message))
        trackMessage(message.type)
    }

    override fun message(type: C_MessageType, pos: S_Pos, lazyCodeMsg: Lazy<C_CodeMsg>) {
        messages.add(C_InternalMessage_Lazy(type, pos, lazyCodeMsg))
        trackMessage(type)
    }

    private fun trackMessage(type: C_MessageType) {
        if (type == C_MessageType.ERROR) {
            errorCount = IntMath.checkedAdd(errorCount, 1)
        }
    }

    override fun messages(): ImmList<C_Message> {
        return messages.mapToImmList { it.getMessage() }
    }

    override fun errorWatcher(): C_MessageManager.C_ErrorWatcher = C_ErrorWatcherImpl()
    override fun firstErrorReporter(): C_MessageManager.C_FirstErrorReporter = C_FirstErrorReporterImpl()

    private inner class C_ErrorWatcherImpl: C_MessageManager.C_ErrorWatcher {
        private var lastErrorCount = errorCount

        override fun hasNewErrors(): Boolean {
            val count = errorCount
            val res = count > lastErrorCount
            lastErrorCount = count
            return res
        }
    }

    private inner class C_FirstErrorReporterImpl: C_MessageManager.C_FirstErrorReporter {
        private var reported = false

        override fun error(pos: S_Pos, code: String, msg: String) {
            if (!reported) {
                reported = true
                this@C_DefaultMessageManager.error(pos, code, msg)
            }
        }
    }

    private abstract class C_InternalMessage {
        abstract fun getMessage(): C_Message
    }

    private class C_InternalMessage_Direct(val cMessage: C_Message): C_InternalMessage() {
        override fun getMessage() = cMessage
    }

    private class C_InternalMessage_Lazy(
        val type: C_MessageType,
        val pos: S_Pos,
        val lazyCodeMsg: Lazy<C_CodeMsg>,
    ): C_InternalMessage() {
        override fun getMessage(): C_Message {
            val codeMsg = lazyCodeMsg.value
            return C_Message(type, pos, codeMsg.code, codeMsg.msg)
        }
    }
}
