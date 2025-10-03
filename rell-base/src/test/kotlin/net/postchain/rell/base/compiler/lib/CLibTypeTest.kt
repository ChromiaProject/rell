/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.testutils.LibModuleTester
import kotlin.test.Test

class CLibTypeTest: BaseCLibTest() {
    private val modTst = LibModuleTester(tst)

    @Test fun testConstant() {
        modTst.libModule {
            imports(Lib_Rell.MODULE.lModule)
            type("data") {
                modTst.setRTypeFactory(this)
                constant("MAGIC", "integer") { value { Rt_IntValue.get(12345) } }
            }
        }
        chk("data.MAGIC", "int[12345]")
    }

    @Test fun testNoConstructor() {
        modTst.libModule {
            type("data") {
                modTst.setRTypeFactory(this)
            }
        }

        chkCompile("function f(x: data) {}", "OK")
        chk("data()", "ct_err:expr:type:no_constructor:data")
    }

    @Test fun testSpecialConstructor() {
        modTst.libModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor(BaseLTest.makeTypeCon())
            }
        }

        chkCompile("function f(x: data) {}", "OK")
        chkCompile("function f() = data();", "OK")
        chkCompile("function f() = data(foo);", "ct_err:unknown_name:foo")
    }

    @Test fun testExtensionReference() {
        modTst.libModule {
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

    @Test fun testDeprecated() {
        modTst.libModule {
            imports(Lib_Rell.MODULE.lModule)
            type("data") {
                modTst.setRTypeFactory(this)
                constant("X", 123)
                staticFunction("f", "integer") { body { -> Rt_UnitValue } }
            }
            alias("tada", "data", C_MessageType.ERROR) //TODO remove alias when direct type deprecation is supported
        }

        chkCompile("function f(v: tada) {}", "ct_err:deprecated:ALIAS:[mod:tada]:data")
        chkCompile("function f() = tada.X;", "ct_err:deprecated:ALIAS:[mod:tada]:data")
        chkCompile("function f() = tada.f();", "ct_err:deprecated:ALIAS:[mod:tada]:data")
    }

    @Test fun testGenericProperty() {
        tst.typeCheck = false
        modTst.libModule {
            imports(Lib_Rell.MODULE.lModule)
            type("data") {
                generic("T")
                modTst.setRTypeFactory(this, genericCount = 1)
                constructor {
                    param("value", type = "T")
                    body { value -> value }
                }
                property("prop", type = "T", pure = true) {
                    value { self, _ -> self }
                }
            }
        }

        chk("data<integer>(123).prop", "int[123]")
        chk("data<text>('hello').prop", "text[hello]")
        chk("data<boolean>(true).prop", "boolean[true]")
    }
}
