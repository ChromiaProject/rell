/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_LibSimpleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf

private const val FAILURE_SNAME = "failure"
private val FAILURE_QNAME = Lib_RellTest.NAMESPACE_NAME.add(FAILURE_SNAME)

object Lib_Test_Assert {
    val FAILURE_TYPE: R_Type = R_TestFailureType

    val NAMESPACE = Ld_NamespaceDsl.make {
        include(Lib_Test_Type_Failure.NAMESPACE)

        alias(target = "rell.test.assert_equals")
        alias(target = "rell.test.assert_not_equals")
        alias(target = "rell.test.assert_true")
        alias(target = "rell.test.assert_false")
        alias(target = "rell.test.assert_null")
        alias(target = "rell.test.assert_not_null")
        alias(target = "rell.test.assert_fails")

        alias(target = "rell.test.assert_lt")
        alias(target = "rell.test.assert_gt")
        alias(target = "rell.test.assert_le")
        alias(target = "rell.test.assert_ge")
        alias(target = "rell.test.assert_gt_lt")
        alias(target = "rell.test.assert_gt_le")
        alias(target = "rell.test.assert_ge_lt")
        alias(target = "rell.test.assert_ge_le")

        namespace("rell.test") {
            function("assert_equals", pure = true) {
                generic("T")
                result("unit")
                param("actual", type = "T")
                param("expected", type = "T")
                body { actualValue, expectedValue ->
                    calcAssertEquals("assert_equals", expectedValue, actualValue, R_BinaryOp_Eq)
                }
            }

            function("assert_not_equals", pure = true) {
                generic("T")
                result("unit")
                param("actual", type = "T")
                param("illegal", type = "T")
                body { actualValue, expectedValue ->
                    val equalsValue = R_BinaryOp_Eq.evaluate(actualValue, expectedValue)
                    if (equalsValue.asBoolean()) {
                        val code = "assert_not_equals:${actualValue.strCode()}"
                        throw Rt_AssertError.exception(code, "expected not <${actualValue.str()}>")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_true", "unit", pure = true) {
                param("actual", "boolean")
                body { arg ->
                    calcAssertBoolean(true, arg)
                }
            }

            function("assert_false", "unit", pure = true) {
                param("actual", "boolean")
                body { arg ->
                    calcAssertBoolean(false, arg)
                }
            }

            function("assert_null", "unit", pure = true) {
                param("actual", type = "anything", nullable = true)
                body { arg ->
                    if (arg != Rt_NullValue) {
                        throw Rt_AssertError.exception("assert_null:${arg.strCode()}", "expected null but was <${arg.str()}>")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_not_null", "unit", pure = true) {
                generic("T", subOf = "any")
                param("actual", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL)
                body { arg ->
                    if (arg == Rt_NullValue) {
                        throw Rt_AssertError.exception("assert_not_null", "expected not null")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_fails", "rell.test.failure") {
                generic("T")
                param("fn", type = "() -> T")
                bodyContext { ctx, arg ->
                    val fn = arg.asFunction()
                    calcAssertFails(ctx, fn, null)
                }
            }

            function("assert_fails", "rell.test.failure") {
                generic("T")
                param("expected_message", type = "text")
                param("fn", type = "() -> T")
                bodyContext { ctx, arg1, arg2 ->
                    val expected = arg1.asString()
                    val fn = arg2.asFunction()
                    calcAssertFails(ctx, fn, expected)
                }
            }

            defAssertCompare(this, "assert_lt", R_CmpOp_Lt)
            defAssertCompare(this, "assert_gt", R_CmpOp_Gt)
            defAssertCompare(this, "assert_le", R_CmpOp_Le)
            defAssertCompare(this, "assert_ge", R_CmpOp_Ge)

            defAssertRange(this, "assert_gt_lt", R_CmpOp_Gt, R_CmpOp_Lt)
            defAssertRange(this, "assert_gt_le", R_CmpOp_Gt, R_CmpOp_Le)
            defAssertRange(this, "assert_ge_lt", R_CmpOp_Ge, R_CmpOp_Lt)
            defAssertRange(this, "assert_ge_le", R_CmpOp_Ge, R_CmpOp_Le)
        }
    }

    private fun defAssertCompare(mk: Ld_NamespaceDsl, name: String, op: R_CmpOp) = with(mk) {
        function(name, "unit", pure = true) {
            generic("T", subOf = "comparable")
            param("actual", type = "T")
            param("expected", type = "T")
            bodyMeta {
                val comparator = getAssertComparator(this)
                body { left, right ->
                    calcAssertCompare(comparator, op, left, right)
                    Rt_UnitValue
                }
            }
        }
    }

    private fun defAssertRange(m: Ld_NamespaceDsl, name: String, op1: R_CmpOp, op2: R_CmpOp) = with(m) {
        function(name, "unit", pure = true) {
            generic("T", subOf = "comparable")
            param("actual", type = "T")
            param("expected1", type = "T")
            param("expected2", type = "T")
            bodyMeta {
                val comparator = getAssertComparator(this)
                body { actual, expected1, expected2 ->
                    calcAssertCompare(comparator, op1, actual, expected1)
                    calcAssertCompare(comparator, op2, actual, expected2)
                    Rt_UnitValue
                }
            }
        }
    }

    private fun getAssertComparator(m: Ld_FunctionMetaBodyDsl): Comparator<Rt_Value> {
        val rType = m.fnBodyMeta.typeArg("T")
        val comparator = rType.comparator()
        return if (comparator != null) comparator else {
            // Must not happen, because there are type constraints (comparable), but checking for extra safety.
            m.validationError("assert:no_comparator:${rType.strCode()}", "Type '${rType.str()}' is not comparable")
            return Comparator { _, _ -> 0 }
        }
    }

    fun failureValue(message: String): Rt_Value = Rt_TestFailureValue(message)

    fun checkErrorMessage(fn: String, expected: String?, actual: String) {
        if (expected != null && !actual.contains(expected)) {
            val code = "$fn:mismatch:[$expected]:[$actual]"
            val msg = "expected to contain <$expected> but was <$actual>"
            throw Rt_AssertError.exception(code, msg)
        }
    }

    fun calcAssertEquals(fn: String, expected: Rt_Value, actual: Rt_Value, op: R_BinaryOp = R_BinaryOp_Eq): Rt_Value {
        val equalsValue = op.evaluate(actual, expected)
        if (!equalsValue.asBoolean()) {
            val code = "$fn:${actual.strCode()}:${expected.strCode()}"
            throw Rt_AssertError.exception(code, "expected <${expected.str()}> but was <${actual.str()}>")
        }
        return Rt_UnitValue
    }

    private fun calcAssertBoolean(expected: Boolean, arg: Rt_Value): Rt_Value {
        val v = arg.asBoolean()
        if (v != expected) {
            throw Rt_AssertError.exception("assert_boolean:$expected", "expected $expected")
        }
        return Rt_UnitValue
    }

    private fun calcAssertFails(ctx: Rt_CallContext, fn: Rt_FunctionValue, expected: String?): Rt_Value {
        var err: Rt_Error? = null
        try {
            fn.call(ctx, immListOf())
        } catch (e: Rt_Exception) {
            if (e.err is Rt_AssertError) {
                throw e
            }
            err = e.err
        }

        if (err == null) {
            throw Rt_AssertError.exception("assert_fails:no_fail:${fn.strCode()}", "code did not fail")
        }

        val message = err.message()
        checkErrorMessage("assert_fails", expected, message)

        return Rt_TestFailureValue(message)
    }

    private fun calcAssertCompare(
        comparator: Comparator<Rt_Value>,
        op: R_CmpOp,
        actualValue: Rt_Value,
        expectedValue: Rt_Value,
    ) {
        val diff = comparator.compare(actualValue, expectedValue)
        if (!op.check(diff)) {
            val code = "assert_compare:${op.code}:${actualValue.strCode()}:${expectedValue.strCode()}"
            throw Rt_AssertError.exception(code, "comparison failed: ${actualValue.str()} ${op.code} ${expectedValue.str()}")
        }
    }
}

private object Lib_Test_Type_Failure {
    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type("failure", rType = R_TestFailureType) {
                property("message", type = "text", pure = true) {
                    value { a ->
                        val v = a as Rt_TestFailureValue
                        v.messageValue
                    }
                }
            }
        }
    }
}

class Rt_AssertError private constructor(val code: String, val msg: String): Rt_Error() {
    override fun code() = "asrt_err:$code"
    override fun message() = msg

    companion object {
        fun exception(code: String, msg: String) = Rt_Exception(Rt_AssertError(code, msg))
    }
}

private object R_TestFailureType: R_LibSimpleType(FAILURE_QNAME.str(), Lib_RellTest.typeDefName(FAILURE_QNAME)) {
    override fun isReference() = true
    override fun isDirectPure() = false
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.FAILURE_TYPE
}

private class Rt_TestFailureValue(val message: String): Rt_Value() {
    val messageValue = Rt_TextValue.get(message)

    override val valueType = VALUE_TYPE
    override fun type(): R_Type = R_TestFailureType
    override fun str() = message
    override fun strCode(showTupleFieldNames: Boolean) = "${R_TestFailureType.name}[$message]"

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("TEST_FAILURE")
    }
}
