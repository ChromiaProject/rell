/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.common.toHex
import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseCtx
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.V_SpecialMemberFunctionCall
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.testutils.VirtualTestUtils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.PostchainGtvUtils
import org.junit.Test

class CLibFunctionTypeExtTest: BaseCLibTest() {
    @Test fun testTypeExtensionMeta() {
        //tst.wrapFunctionCallErrors = false
        tst.strictToString = false
        tst.typeCheck = false
        tst.extraMod = makeModule {
            fun argsToStr(m: Map<String, R_Type>) = m.entries.joinToString(", ", "{", "}") { "${it.key}=${it.value.strCode()}" }
            extension("ext", type = "A") {
                generic("A", subOf = "any")
                staticFunction("static_self_type", result = "text") {
                    bodyMeta { body { -> Rt_TextValue.get(fnBodyMeta.rSelfType.strCode()) } }
                }
                staticFunction("static_result_type", result = "A") {
                    bodyMeta { body { -> Rt_TextValue.get(fnBodyMeta.rResultType.strCode()) } }
                }
                staticFunction("static_type_args", result = "text") {
                    bodyMeta { body { -> Rt_TextValue.get(argsToStr(fnBodyMeta.rTypeArgs)) } }
                }
                function("value_self_type", result = "text") {
                    bodyMeta { body { _ -> Rt_TextValue.get(fnBodyMeta.rSelfType.strCode()) } }
                }
                function("value_result_type", result = "A") {
                    bodyMeta { body { _ -> Rt_TextValue.get(fnBodyMeta.rResultType.strCode()) } }
                }
                function("value_type_args", result = "text") {
                    bodyMeta { body { _ -> Rt_TextValue.get(argsToStr(fnBodyMeta.rTypeArgs)) } }
                }
            }
        }

        chkTypeExtensionMeta("integer", "(0)")
        chkTypeExtensionMeta("list<integer>", "[0]")
        chkTypeExtensionMeta("map<integer,text>", "[0:'']")
        chkTypeExtensionMeta("list<(integer,text)>", "[(0,'')]")
    }

    private fun chkTypeExtensionMeta(type: String, expr: String) {
        //chk("$type.static_self_type()", type) //TODO fix
        chk("$type.static_result_type()", type)
        //chk("$type.static_type_args()", "{A=$type}") //TODO fix
        chk("_type_of($type.static_result_type())", type)

        chk("$expr.value_self_type()", type)
        chk("$expr.value_result_type()", type)
        //chk("$expr.value_type_args()", "{A=$type}") //TODO fix
        chk("_type_of($expr.value_result_type())", type)
    }

    @Test fun testTypeExtensionNamingSimple() {
        tst.extraMod = makeTypeExtensionMod()
        tst.typeCheck = false

        chkTypeExtensionNaming("boolean", "true")
        chkTypeExtensionNaming("integer", "(0)")
        chkTypeExtensionNaming("list<integer>", "[0]")

        chk("integer.test_decode(123)", "int[123]")
        chk("(0).test_encode(123)", "text[123]")
    }

    @Test fun testTypeExtensionNamingMirrorStruct() {
        tst.extraMod = makeTypeExtensionMod()
        def("entity data { x: integer; }")
        def("operation op(x: integer) {}")

        chkTypeExtensionNaming("struct<data>", "struct<data>(0)")
        chkTypeExtensionNaming("struct<mutable data>", "struct<mutable data>(0)")
        chkTypeExtensionNaming("struct<op>", "struct<op>(0)")
        chkTypeExtensionNaming("struct<mutable op>", "struct<mutable op>(0)")
    }

    @Test fun testTypeExtensionNamingVirtual() {
        tst.extraMod = makeTypeExtensionMod()
        def("struct rec { x: integer; y: text; }")

        chkTypeExtensionNamingVirtual("virtual<list<integer>>", "[123,456]", "[[0],[1]]")
        chkTypeExtensionNamingVirtual("virtual<set<integer>>", "[123,456]", "[[0],[1]]")
        chkTypeExtensionNamingVirtual("virtual<map<text,integer>>", "{'A':123,'B':456}", "[['A'],['B']]")
        chkTypeExtensionNamingVirtual("virtual<(integer,text)>", "[123,'A']", "[[0],[1]]")
        chkTypeExtensionNamingVirtual("virtual<rec>", "[123,'A']", "[[0],[1]]")
    }

    private fun chkTypeExtensionNamingVirtual(type: String, fullGtv: String, pathsGtv: String) {
        val gtv = VirtualTestUtils.argToGtv(fullGtv, pathsGtv)
        val hex = PostchainGtvUtils.gtvToBytes(gtv).toHex()
        chkTypeExtensionNaming(type, "$type.from_gtv(gtv.from_bytes(x'$hex'))")
    }

    private fun chkTypeExtensionNaming(type: String, expr: String) {
        chk("_type_of($type.test_decode(0))", "text[$type]")
        chk("_type_of($expr.test_encode(0))", "text[$type]")

        chkOp("$expr.test_prop = 123;", "ct_err:attr_not_mutable:ext($type).test_prop")

        chk("$type.test_decode()", "ct_err:expr:call:missing_args:[ext($type).test_decode]:[0:a]")
        chk("$type.test_decode('')", "ct_err:expr_call_badargs:[ext($type).test_decode]:[text]")
        chk("$type.test_decode(0)", "rt_err:x=int[0]")
        chk("$type.test_decode(-1)", "rt_err:fn:error:ext($type).test_decode:java.lang.IllegalStateException")
        chk("$type.test_decode(*)", "fn[ext($type).test_decode(*)]")
        chk("$type.test_decode(*)(0)", "rt_err:x=int[0]")
        chk("$type.test_decode(*)(-1)", "rt_err:fn:error:ext($type).test_decode:java.lang.IllegalStateException")

        chk("$expr.test_encode()", "ct_err:expr:call:missing_args:[ext($type).test_encode]:[0:a]")
        chk("$expr.test_encode('')", "ct_err:expr_call_badargs:[ext($type).test_encode]:[text]")
        chk("$expr.test_encode(*)", "fn[ext($type).test_encode(*)]")
        chk("$expr.test_encode(0)", "rt_err:x=int[0]")
        chk("$expr.test_encode(-1)", "rt_err:fn:error:ext($type).test_encode:java.lang.IllegalStateException")
        chk("$expr.test_encode(*)(0)", "rt_err:x=int[0]")
        chk("$expr.test_encode(*)(-1)", "rt_err:fn:error:ext($type).test_encode:java.lang.IllegalStateException")

        chk("$type.spec_decode()", "ct_err:test_error:ext($type).spec_decode")
        chk("$expr.spec_encode()", "ct_err:test_error:ext($type).spec_encode")
    }

    private fun makeTypeExtensionMod() = makeModule {
        extension("ext", type = "T") {
            generic("T", subOf = "any")
            property("test_prop", type = "integer", pure = true) {
                value { _ -> Rt_IntValue.ZERO }
            }
            staticFunction("test_decode", result = "T") {
                param("a", type = "integer")
                body { a ->
                    Rt_Utils.check(a.asInteger() != 0L) { "x=${a.strCode()}" toCodeMsg "x = ${a.str()}" }
                    check(a.asInteger() >= 0)
                    a
                }
            }
            function("test_encode", result = "T") {
                param("a", type = "integer")
                body { _, a ->
                    val v = a.asInteger()
                    Rt_Utils.check(v != 0L) { "x=${a.strCode()}" toCodeMsg "x = ${a.str()}" }
                    check(v >= 0)
                    Rt_TextValue.get(a.str())
                }
            }
            staticFunction("spec_decode", object: C_SpecialLibGlobalFunctionBody() {
                override fun compileCall(
                    ctx: C_ExprContext,
                    name: LazyPosString,
                    args: ImmList<S_Expr>
                ): V_Expr {
                    ctx.msgCtx.error(name.pos, "test_error:${name.str}", "Test error")
                    return C_ExprUtils.errorVExpr(ctx, name.pos)
                }
            })
            function("spec_encode", object: C_SpecialLibMemberFunctionBody() {
                override fun compileCall(
                    ctx: C_ExprContext,
                    callCtx: C_LibFuncCaseCtx,
                    selfType: R_Type,
                    args: ImmList<V_Expr>,
                ): V_SpecialMemberFunctionCall {
                    ctx.msgCtx.error(callCtx.linkPos, "test_error:${callCtx.qualifiedNameMsg()}", "Test error")
                    return BaseLTest.makeTypeFunCall(ctx)
                }
            })
        }
    }
}
