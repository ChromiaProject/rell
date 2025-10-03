/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.model.R_LibUniqueType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_LibValueType
import net.postchain.rell.base.runtime.Rt_Value
import kotlin.test.Test

class CLibFunctionLazyParamTest: BaseCLibTest() {
    @Test fun testLazyParamVarStates() {
        tst.extraMod = makeModule {
            function("f", result = "unit") {
                param("a", type = "integer", lazy = true)
                body { _ -> Rt_UnitValue }
            }
            extension("ext", type = "any") {
                function("g", result = "unit") {
                    param("a", type = "integer", lazy = true)
                    body { _, _ -> Rt_UnitValue }
                }
            }
        }

        chkEx("{ val t = _nullable_int(123); return _type_of(t); }", "text[integer?]")
        chkEx("{ val t = _nullable_int(123); return abs(t); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val t = _nullable_int(123); f(t!!); return _type_of(t); }", "text[integer?]")
        chkEx("{ val t = _nullable_int(123); f(t!!); return abs(t); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
        chkEx("{ val t = _nullable_int(123); ''.g(t!!); return _type_of(t); }", "text[integer?]")
        chkEx("{ val t = _nullable_int(123); ''.g(t!!); return abs(t); }", "ct_err:expr_call_badargs:[abs]:[integer?]")
    }

    @Test fun testLazyParamsMany() {
        tst.extraMod = makeModule {
            function("sum", "text") {
                param("a", "integer", arity = L_ParamArity.ONE_MANY, lazy = true)
                bodyN { args ->
                    val sum = args.sumOf { it.asLazyValue().asInteger() }
                    Rt_TextValue.get("${args.size}:$sum")
                }
            }
        }
        chk("sum(1)", "text[1:1]")
        chk("sum(1, 2)", "text[2:3]")
        chk("sum(1, 2, 3)", "text[3:6]")
        chk("sum(1, 2, 3, 4)", "text[4:10]")
        chk("sum(1, 2, 3, 4, 5)", "text[5:15]")
    }

    @Test fun testIfIntBasic() {
        tst.extraMod = IfIntDefs.MODULE
        chkIfIntBasic("if_int(%s, %s, %s)")
        chkIfIntBasic("(%s).if_int(%s, %s)")
    }

    private fun chkIfIntBasic(expr: String) {
        chk(expr.format(true, 123, 456), "int[123]")
        chk(expr.format(false, 123, 456), "int[456]")
    }

    @Test fun testIfIntLazy() {
        tst.extraMod = IfIntDefs.MODULE
        def("function f(x: boolean) { print('f:' + x); return x; }")
        def("function g(x: integer) { print('g:' + x); return x; }")
        def("function h(x: integer) { print('h:' + x); return x; }")

        chkIfIntLazy("if_int(%s, %s, %s)")
        chkIfIntLazy("(%s).if_int(%s, %s)")
    }

    private fun chkIfIntLazy(expr: String) {
        chk(expr.format("f(true)", "g(123)", "h(456)"), "int[123]")
        chkOut("f:true", "g:123")

        chk(expr.format("f(false)", "g(123)", "h(456)"), "int[456]")
        chkOut("f:false", "h:456")
    }

    @Test fun testIfIntPartial() {
        tst.extraMod = IfIntDefs.MODULE

        // Make sure that functions with lazy parameters can't be partially called.
        var err = "ct_err:expr:call:partial_not_supported:[if_int]"
        chk("if_int(*)", err)
        chk("if_int(true, *)", err)
        chk("if_int(false, *)", err)
        chk("if_int(*, 123, 456)", err)
        chk("if_int(*, 123)", err)
        chk("if_int(*, *, 456)", err)

        err = "ct_err:expr:call:partial_not_supported:[boolean_ext(boolean).if_int]"
        chk("(true).if_int(*)", err)
        chk("(false).if_int(*)", err)
        chk("(true).if_int(123, *)", err)
        chk("(true).if_int(*, 456)", err)
    }

    @Test fun testIfIntRuntimeError() {
        tst.extraMod = IfIntDefs.MODULE
        def("function f(x: integer) { if (x < 0) _test.throw('x_is_negative:' + x, 'x = ' + x); return x; }")
        chkIfIntRuntimeError("if_int(%s, %s, %s)")
        chkIfIntRuntimeError("(%s).if_int(%s, %s)")
    }

    private fun chkIfIntRuntimeError(expr: String) {
        tst.extraMod = IfIntDefs.MODULE
        chk(expr.format(true, "f(-123)", 456), "rt_err:throw:x_is_negative:-123")
        chkStack(":f(main.rell:1)", ":q(main.rell:2)", ":q(main.rell:2)")
        chk(expr.format(false, "f(-123)", 456), "int[456]")

        chk(expr.format(true, 123, "f(-456)"), "int[123]")
        chk(expr.format(false, 123, "f(-456)"), "rt_err:throw:x_is_negative:-456")
        chkStack(":f(main.rell:1)", ":q(main.rell:2)", ":q(main.rell:2)")
    }

    @Test fun testIfIntComplexWhat() {
        tstCtx.useSql = true
        tst.extraMod = IfIntDefs.MODULE

        def("entity data { v: boolean; a: integer; b: integer; }")
        def("function f(x: boolean) { print('f:' + x); return x; }")
        def("function g(x: integer) { print('g:' + x); return x; }")
        def("function h(x: integer) { print('h:' + x); return x; }")
        insert("c0.data", "v,a,b", "100,FALSE,123,456")
        insert("c0.data", "v,a,b", "101,TRUE,321,654")
        insert("c0.data", "v,a,b", "102,FALSE,789,987")
        insert("c0.data", "v,a,b", "103,TRUE,654,321")

        chkIfIntComplexWhat("if_int(%s, %s, %s)")
        chkIfIntComplexWhat("(%s).if_int(%s, %s)")
    }

    private fun chkIfIntComplexWhat(expr: String) {
        chk("data @{ .rowid.to_integer() == 100 } ( .v, .a, .b )", "(v=boolean[false],a=int[123],b=int[456])")
        chk("data @{ .rowid.to_integer() == 100 } ( ${expr.format(".v", ".a", ".b")} )", "int[456]")
        chk("data @{ .rowid.to_integer() == 101 } ( ${expr.format(".v", ".a", ".b")} )", "int[321]")
        chkOut()

        chk("data @{ .rowid.to_integer() == 100 } ( ${expr.format("f(.v)", "g(.a)", "h(.b)")} )", "int[456]")
        chkOut("f:false", "h:456")
        chk("data @{ .rowid.to_integer() == 101 } ( ${expr.format("f(.v)", "g(.a)", "h(.b)")} )", "int[321]")
        chkOut("f:true", "g:321")

        chk("data @*{} ( ${expr.format("f(.v)", "g(.a)", "h(.b)")} )", "list<integer>[int[456],int[321],int[987],int[654]]")
        chkOut("f:false", "h:456", "f:true", "g:321", "f:false", "h:987", "f:true", "g:654")
        chk("data @*{} ( ${expr.format("f(.v)", "g(555)", "h(.b)")} )", "list<integer>[int[456],int[555],int[987],int[555]]")
        chkOut("f:false", "h:456", "f:true", "g:555", "f:false", "h:987", "f:true")
        chk("data @*{} ( ${expr.format("f(.v)", "g(.a)", "h(555)")} )", "list<integer>[int[555],int[321],int[555],int[654]]")
        chkOut("f:false", "h:555", "f:true", "g:321", "f:false", "f:true", "g:654")
    }

    private object IfIntDefs {
        private const val TYPE_NAME = "test_type"

        val MODULE: C_LibModule = C_LibModule.make("test", Lib_Rell.MODULE, requireSince = false) {
            extension("boolean_ext", type = "boolean") {
                function("if_int", result = "integer") {
                    param("a", type = "integer", lazy = true)
                    param("b", type = "integer", lazy = true)
                    body { arg1, arg2, arg3 ->
                        val resValue = if (arg1.asBoolean()) arg2 else arg3
                        resValue.asLazyValue()
                    }
                }
            }

            type(TYPE_NAME, rType = R_TestType) {
                constructor {
                    body { ->
                        Rt_TestTypeValue()
                    }
                }

                function("if_int", result = "integer") {
                    param("a", type = "boolean")
                    param("b", type = "integer", lazy = true)
                    param("c", type = "integer", lazy = true)
                    body { _, arg1, arg2, arg3 ->
                        val resValue = if (arg1.asBoolean()) arg2 else arg3
                        resValue.asLazyValue()
                    }
                }
            }

            function("if_int", "integer") {
                param("a", "boolean")
                param("b", "integer", lazy = true)
                param("c", "integer", lazy = true)
                body { a, b, c ->
                    val resValue = if (a.asBoolean()) b else c
                    resValue.asLazyValue()
                }
            }
        }

        private object R_TestType: R_LibUniqueType(TYPE_NAME, C_DefinitionName("rell", TYPE_NAME)) {
            override fun createGtvConversion() = GtvRtConversion_None
            override fun getLibTypeDef() = MODULE.getTypeDef("test_type")
        }

        private class Rt_TestTypeValue: Rt_Value() {
            override val valueType = Rt_LibValueType.of(TYPE_NAME)
            override fun type(): R_Type = R_TestType
            override fun str(format: StrFormat): String = TYPE_NAME
            override fun strCode(showTupleFieldNames: Boolean) = TYPE_NAME
        }
    }
}
