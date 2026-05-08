/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.R_AttrValidator
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.plus

class Rt_ExceptionInfo(val stack: ImmList<R_StackPos>, val extraMessage: String? = null) {
    fun fullMessage(err: Rt_Error): String {
        var res = err.message()
        if (extraMessage != null) {
            res = "$extraMessage: $res"
        }
        return res
    }

    companion object {
        val NONE = Rt_ExceptionInfo(stack = immListOf())
    }
}

/**
 * Runtime exception carrying a Rell [Rt_Error] plus a mutable [Rt_ExceptionInfo] capturing the
 * call stack and an optional extra-message wrapper.
 *
 * The `info` field is mutated in place by interpreter catch-and-rethrow sites to attach
 * source-position metadata (see [attachStackPos], [attachExtraMessage]). This avoids
 * allocating a fresh `Rt_Exception` (and a fresh JVM `fillInStackTrace`) on every
 * exception-decoration step — those decorations show up as a hot leaf on workloads with
 * deeply nested catch-rethrow chains (e.g. FT4 `rule_serde`). Because callers don't retain
 * references to the caught exception after rethrowing, in-place mutation is observationally
 * identical to allocating a wrapper.
 *
 * `getMessage()` is overridden to read [info] lazily so the rendered message reflects the
 * current state after mutation, rather than the snapshot captured at construction time.
 */
class Rt_Exception(
    val err: Rt_Error,
    info: Rt_ExceptionInfo = Rt_ExceptionInfo.NONE,
    cause: Throwable? = null,
): RuntimeException(null, cause) {
    var info: Rt_ExceptionInfo = info
        private set

    override val message: String
        get() = info.fullMessage(err)

    fun fullMessage() = info.fullMessage(err)

    /**
     * Append [stackPos] to the current stack and rethrow the same exception instance.
     * If [nested] is `false` and the existing stack is already non-empty, the stack is
     * preserved unchanged (matches the legacy rule in `Rt_CallFrame.error`).
     */
    fun attachStackPos(stackPos: R_StackPos, nested: Boolean) {
        val cur = info
        val newStack = if (nested || cur.stack.isEmpty()) (cur.stack + stackPos) else cur.stack
        if (newStack !== cur.stack) {
            info = Rt_ExceptionInfo(stack = newStack, extraMessage = cur.extraMessage)
        }
    }

    /** Replace the [Rt_ExceptionInfo.extraMessage] wrapper, preserving the existing stack. */
    fun attachExtraMessage(extraMessage: String) {
        val cur = info
        info = Rt_ExceptionInfo(stack = cur.stack, extraMessage = extraMessage)
    }

    companion object {
        @JvmStatic
        fun common(code: String, msg: String) = Rt_Exception(Rt_CommonError(code, msg))
    }
}

interface Rt_Error {
    fun code(): String
    fun message(): String
}

class Rt_CommonError(val code: String, private val msg: String): Rt_Error {
    override fun code() = "rt_err:$code"
    override fun message() = msg
}

class Rt_RequireError(val userMsg: String?): Rt_Error {
    override fun code() = "req_err:" + if (userMsg != null) "[$userMsg]" else "null"
    override fun message() = userMsg ?: "Requirement error"

    companion object {
        fun exception(userMsg: String?) = Rt_Exception(Rt_RequireError(userMsg))
    }
}

class Rt_ValueTypeError(val expected: String, val actual: String): Rt_Error {
    override fun code() = "rtv_err:$expected:$actual"
    override fun message() = "Value type mismatch: $actual instead of $expected"

    companion object {
        fun exception(expected: String, actual: String) = Rt_Exception(Rt_ValueTypeError(expected, actual))
    }
}

class Rt_GtvError(val code: String, val msg: String): Rt_Error {
    override fun code() = "gtv_err:$code"
    override fun message() = msg

    companion object {
        fun exception(code: String, msg: String) = Rt_Exception(Rt_GtvError(code, msg))
    }
}

fun R_AttrValidator.Error.raise(): Nothing = throw Rt_Exception.common(code, msg)
