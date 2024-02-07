/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.testutils.LibModuleTester
import net.postchain.rell.base.utils.LazyPosString
import org.junit.Test

class CLibTypeTest: BaseCLibTest() {
    private val modTst = LibModuleTester(tst)

    @Test fun testConstant() {
        modTst.extraModule {
            imports(Lib_Rell.MODULE.lModule)
            type("data") {
                modTst.setRTypeFactory(this)
                constant("MAGIC", "integer") { value { Rt_IntValue.get(12345) } }
            }
        }
        chk("data.MAGIC", "int[12345]")
    }

    @Test fun testNoConstructor() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
            }
        }

        chkCompile("function f(x: data) {}", "OK")
        chk("data()", "ct_err:expr:type:no_constructor:data")
    }

    @Test fun testSpecialConstructor() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor(object: C_SpecialLibGlobalFunctionBody() {
                    override fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: List<S_Expr>): V_Expr {
                        args.forEach { it.compile(ctx) }
                        return C_ExprUtils.errorVExpr(ctx, name.pos)
                    }
                })
            }
        }

        chkCompile("function f(x: data) {}", "OK")
        chkCompile("function f() = data();", "OK")
        chkCompile("function f() = data(foo);", "ct_err:unknown_name:foo")
    }

    @Test fun testExtensionReference() {
        modTst.extraModule {
            imports(Lib_Rell.MODULE.lModule)
            struct("data") {}
            extension("data_ext", type = "data") {
                function("f", result = "text") {
                    body { _ -> Rt_TextValue.get("hello from f") }
                }
            }
        }

        val extName = "data_ext"
        chk("data()", "data[]")
        chk("data().f()", "text[hello from f]")
        chk("data().f(*)", "fn[$extName(data).f()]")
        chk(extName, "ct_err:unknown_name:$extName")
        chk("$extName()", "ct_err:unknown_name:$extName")
        chk("$extName.f()", "ct_err:unknown_name:$extName")
    }
}
