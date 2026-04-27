/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_LibUniqueType
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf

private const val FAILURE_SNAME = "failure"
private val FAILURE_QNAME = Lib_RellTest.NAMESPACE_NAME.add(FAILURE_SNAME)

internal object Lib_Test_Assert {
    val NAMESPACE = Ld_NamespaceDsl.make {
        include(Lib_Test_Type_Failure.NAMESPACE)

        alias(target = "rell.test.assert_equals", since = "0.10.4")
        alias(target = "rell.test.assert_not_equals", since = "0.10.4")
        alias(target = "rell.test.assert_true", since = "0.10.4")
        alias(target = "rell.test.assert_false", since = "0.10.4")
        alias(target = "rell.test.assert_null", since = "0.10.4")
        alias(target = "rell.test.assert_not_null", since = "0.10.4")
        alias(target = "rell.test.assert_fails", since = "0.10.4")

        alias(target = "rell.test.assert_lt", since = "0.10.4")
        alias(target = "rell.test.assert_gt", since = "0.10.4")
        alias(target = "rell.test.assert_le", since = "0.10.4")
        alias(target = "rell.test.assert_ge", since = "0.10.4")
        alias(target = "rell.test.assert_gt_lt", since = "0.10.4")
        alias(target = "rell.test.assert_gt_le", since = "0.10.4")
        alias(target = "rell.test.assert_ge_lt", since = "0.10.4")
        alias(target = "rell.test.assert_ge_le", since = "0.10.4")

        // Needed to specify "since".
        namespace("rell", since = "0.10.4") {
        }

        namespace("rell.test", since = "0.10.4") {
            function("assert_equals", pure = true, since = "0.10.4") {
                comment("Assert two values are equal.")
                generic("T")
                result("unit")
                param("actual", type = "T", comment = "the actual value")
                param("expected", type = "T", comment = "the expected value")
                body { actualValue, expectedValue ->
                    calcAssertEquals("assert_equals", expectedValue, actualValue)
                }
            }

            function("assert_not_equals", pure = true, since = "0.10.4") {
                comment("Assert two values are unequal.")
                generic("T")
                result("unit")
                param("actual", type = "T", comment = "the actual value")
                param("illegal", type = "T", comment = "the unexpected value")
                body { actualValue, expectedValue ->
                    val equalsValue = evaluateBinaryOp("R_BinaryOp_Eq", actualValue, expectedValue)
                    if (equalsValue.asBoolean()) {
                        val code = "assert_not_equals:${actualValue.strCode()}"
                        throw Rt_AssertError.exception(code, "expected not <${actualValue.str(Rt_StrFormat.V2)}>")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_true", "unit", pure = true, since = "0.10.4") {
                comment("""
                    Assert a value is `true`.

                    Prefer other assertion functions where possible, as they provide better error messages.
                """)
                param("actual", "boolean", comment = "the value")
                body { arg ->
                    calcAssertBoolean(true, arg)
                }
            }

            function("assert_false", "unit", pure = true, since = "0.10.4") {
                comment("""
                    Assert a value is `false`.

                    Prefer other assertion functions where possible, as they provide better error messages.
                """)
                param("actual", "boolean", comment = "the value")
                body { arg ->
                    calcAssertBoolean(false, arg)
                }
            }

            function("assert_null", "unit", pure = true, since = "0.10.4") {
                comment("Assert a value is `null`.")
                param("actual", type = "anything", nullable = true, comment = "the value")
                body { arg ->
                    if (arg != Rt_NullValue) {
                        throw Rt_AssertError.exception("assert_null:${arg.strCode()}", "expected null but was <${arg.str()}>")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_not_null", "unit", pure = true, since = "0.10.4") {
                comment("Assert a value is not `null`.")
                generic("T", subOf = "any")
                param("actual", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL) {
                    comment("the value")
                }
                body { arg ->
                    if (arg == Rt_NullValue) {
                        throw Rt_AssertError.exception("assert_not_null", "expected not null")
                    }
                    Rt_UnitValue
                }
            }

            function("assert_fails", "rell.test.failure", since = "0.11.0") {
                comment("""
                    Asserts a function fails; i.e. throws an exception.

                    #### Example
                    ##### Application code
                    ```rell
                    // This will throw a exception.
                    function bad(): integer {
                        return [0][1];
                    }
                    ```
                    ##### Test code
                    ```rell
                    // This test will pass.
                    function test_bad() {
                        rell.test.assert_fails(bad(*));
                    }
                    ```
                """)
                generic("T")
                param("fn", type = "() -> T", comment = "the function value to invoke")
                bodyContext { _, arg ->
                    val fn = arg.asFunction()
                    calcAssertFails(fn, null)
                }
            }

            function("assert_fails", "rell.test.failure", since = "0.11.0") {
                comment("""
                    Asserts a function fails; i.e. throws an exception; with a given exception message.

                    Verifies that the given exception message is a substring of the exception thrown by the given
                    function (if thrown).

                    #### Example
                    ##### Application code
                    ```rell
                    // This will throw a exception.
                    function bad(): integer {
                        return [0][1];
                    }
                    ```
                    ##### Test code
                    ```rell
                    // This test will pass.
                    function test_bad() {
                        rell.test.assert_fails("out of bounds", bad(*));
                    }
                    ```
                """)
                generic("T")
                param("expected_message", type = "text") {
                    comment("the expected substring of the error message")
                }
                param("fn", type = "() -> T", comment = "the function value to invoke")
                bodyContext { _, arg1, arg2 ->
                    val expected = arg1.asString()
                    val fn = arg2.asFunction()
                    calcAssertFails(fn, expected)
                }
            }

            defAssertCompare(this, "assert_lt", R_CmpOp_Lt, "exclusive", "upper", "greatest")
            defAssertCompare(this, "assert_gt", R_CmpOp_Gt, "exclusive", "lower", "least")
            defAssertCompare(this, "assert_le", R_CmpOp_Le, "inclusive", "upper", "greatest")
            defAssertCompare(this, "assert_ge", R_CmpOp_Ge, "inclusive", "lower", "least")

            defAssertRange(this, "assert_gt_lt", R_CmpOp_Gt, R_CmpOp_Lt, "exclusive", "exclusive")
            defAssertRange(this, "assert_gt_le", R_CmpOp_Gt, R_CmpOp_Le, "exclusive", "inclusive")
            defAssertRange(this, "assert_ge_lt", R_CmpOp_Ge, R_CmpOp_Lt, "inclusive", "exclusive")
            defAssertRange(this, "assert_ge_le", R_CmpOp_Ge, R_CmpOp_Le, "inclusive", "inclusive")
        }
    }

    private fun defAssertCompare(
        mk: Ld_NamespaceDsl,
        name: String,
        op: R_CmpOp,
        clusivity: String,
        bound: String,
        superlative: String
    ) = with(mk) {
        function(name, "unit", pure = true, since = "0.10.4") {
            comment("Assert that a value is ${op.str} a given $bound bound.")
            generic("T", subOf = "comparable")
            param("actual", type = "T", comment = "the actual value")
            param("${superlative}_expected", type = "T", comment = "the $bound bound ($clusivity)")
            bodyMeta {
                val comparator = getAssertComparator(this)
                body { left, right ->
                    calcAssertCompare(comparator, op, left, right)
                    Rt_UnitValue
                }
            }
        }
    }

    private fun defAssertRange(
        m: Ld_NamespaceDsl,
        name: String,
        greaterOp: R_CmpOp,
        lessOp: R_CmpOp,
        greaterClusivity: String,
        lessClusivity: String
    ) = with(m) {
        function(name, "unit", pure = true, since = "0.10.4") {
            comment("""
                Assert that a value falls within given bounds.

                Specifically, assert that the value is ${greaterOp.str} a lower bound, and ${lessOp.str} an upper bound.
            """)
            generic("T", subOf = "comparable")
            param("actual", type = "T", comment = "the actual value")
            param("least_expected", type = "T", comment = "the lower bound ($greaterClusivity)")
            param("greatest_expected", type = "T", comment = "the upper bound ($lessClusivity)")
            bodyMeta {
                val comparator = getAssertComparator(this)
                body { actual, leastExpected, greatestExpected ->
                    calcAssertCompare(comparator, greaterOp, actual, leastExpected)
                    calcAssertCompare(comparator, lessOp, actual, greatestExpected)
                    Rt_UnitValue
                }
            }
        }
    }

    private fun getAssertComparator(m: Ld_FunctionMetaBodyDsl): Comparator<Rt_Value> {
        val rtType = m.typeArgRt("T")
        val comparator = rtType.comparator
        return if (comparator != null) {
            comparator
        } else {
            // Must not happen, because there are type constraints (comparable), but checking for extra safety.
            m.validationError("assert:no_comparator:${rtType.name}", "Type '${rtType.name}' is not comparable")
            Comparator { _, _ -> 0 }
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

    fun calcAssertEquals(
        fn: String,
        expected: Rt_Value,
        actual: Rt_Value,
    ): Rt_Value {
        val equalsValue = evaluateBinaryOp("R_BinaryOp_Eq", actual, expected)
        if (!equalsValue.asBoolean()) {
            val code = "$fn:${actual.strCode()}:${expected.strCode()}"
            val expectedStr = Rt_AssertEqualsError.valueToStr(expected, 500)
            val actualStr = Rt_AssertEqualsError.valueToStr(actual, 500)
            throw Rt_AssertEqualsError.exception(code, "expected <$expectedStr> but was <$actualStr>", expected, actual)
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

    private fun calcAssertFails(fn: Rt_FunctionValue, expected: String?): Rt_Value {
        var err: Rt_Error? = null
        try {
            fn.call(immListOf())
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
            val expectedStr = expectedValue.str(Rt_StrFormat.V2)
            val actualStr = actualValue.str(Rt_StrFormat.V2)
            throw Rt_AssertError.exception(code, "comparison failed: $actualStr ${op.code} $expectedStr")
        }
    }
}

private object Lib_Test_Type_Failure {
    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type("failure", rType = R_TestFailureType, since = "0.11.0") {
                comment("A test failure, with a message that gives the reason for the failure.")
                property("message", type = "text", pure = true, since = "0.11.0") {
                    comment("The reason for this failure (typically the message of a thrown exception).")
                    value { a ->
                        val v = a as Rt_TestFailureValue
                        v.messageValue
                    }
                }
            }
        }
    }
}

internal open class Rt_AssertError(
    val code: String,
    val msg: String,
): Rt_Error() {
    final override fun code() = "asrt_err:$code"
    final override fun message() = msg

    companion object {
        fun exception(code: String, msg: String) = Rt_Exception(Rt_AssertError(code, msg))
    }
}

internal class Rt_AssertEqualsError private constructor(
    code: String,
    msg: String,
    val expected: Rt_Value,
    val actual: Rt_Value,
): Rt_AssertError(code, msg) {
    companion object {
        fun exception(code: String, msg: String, expected: Rt_Value, actual: Rt_Value): RuntimeException {
            return Rt_Exception(Rt_AssertEqualsError(code, msg, expected, actual))
        }

        fun valueToStr(v: Rt_Value, truncate: Int): String {
            val s = v.str(Rt_StrFormat.V2)
            return if (s.length <= truncate) s else (s.substring(0, truncate) + "...")
        }
    }
}

internal object R_TestFailureType: R_LibUniqueType(FAILURE_QNAME.str(), Lib_RellTest.typeDefName(FAILURE_QNAME)) {
    override fun isReference() = true
    override fun isDirectPure() = false
}


