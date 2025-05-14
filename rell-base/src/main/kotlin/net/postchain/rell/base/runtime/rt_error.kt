/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf

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

class Rt_Exception(
    val err: Rt_Error,
    val info: Rt_ExceptionInfo = Rt_ExceptionInfo.NONE,
    cause: Throwable? = null,
): RuntimeException(info.fullMessage(err), cause) {
    fun fullMessage() = info.fullMessage(err)

    companion object {
        fun common(code: String, msg: String) = Rt_Exception(Rt_CommonError(code, msg))
    }
}

abstract class Rt_Error {
    abstract fun code(): String
    abstract fun message(): String
}

class Rt_CommonError(val code: String, private val msg: String): Rt_Error() {
    override fun code() = "rt_err:$code"
    override fun message() = msg
}

class Rt_RequireError(val userMsg: String?): Rt_Error() {
    override fun code() = "req_err:" + if (userMsg != null) "[$userMsg]" else "null"
    override fun message() = userMsg ?: "Requirement error"

    companion object {
        fun exception(userMsg: String?) = Rt_Exception(Rt_RequireError(userMsg))
    }
}

class Rt_ValueTypeError(val expected: Rt_ValueType, val actual: Rt_ValueType): Rt_Error() {
    override fun code() = "rtv_err:$expected:$actual"
    override fun message() = "Value type mismatch: $actual instead of $expected"

    companion object {
        fun exception(expected: Rt_ValueType, actual: Rt_ValueType) = Rt_Exception(Rt_ValueTypeError(expected, actual))
    }
}

class Rt_GtvError(val code: String, val msg: String): Rt_Error() {
    override fun code() = "gtv_err:$code"
    override fun message() = msg

    companion object {
        fun exception(code: String, msg: String) = Rt_Exception(Rt_GtvError(code, msg))
    }
}
