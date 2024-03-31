/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_CommonFunctionDsl
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.testutils.LibModuleTester
import org.junit.Test

class CLibVersionTest: BaseCLibTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE, moduleName = "test")

    @Test fun testNsBasic() {
        modTst.extraModule {
            constant("MAGIC", 123, since = "0.10.5")
            type("data", since = "0.10.5") {
                modTst.setRTypeFactory(this)
            }
            struct("rec", since = "0.10.5") {}
            property("prop", "integer", since = "0.10.5") { value { Rt_UnitValue } }
            property("spec_prop", BaseLTest.makeNsProp(), since = "0.10.5")
            function("f", result = "integer", since = "0.10.5") { body { -> Rt_UnitValue } }
            function("g", BaseLTest.makeNsFun(), since = "0.10.5")
        }

        chkVer("query q() = MAGIC;", "CONSTANT:[test:MAGIC]")

        chkVer("query q(r: rec) = 0;", "STRUCT:[test:rec]")
        chkVer("query q() = rec();", "STRUCT:[test:rec]")

        chkVer("query q() = prop;", "PROPERTY:[test:prop]")
        chkVer("query q() = spec_prop;", "PROPERTY:[test:spec_prop]")

        chkVer("query q() = f();", "FUNCTION:[test:f]")
        chkVer("query q() = g();", "FUNCTION:[test:g]")
    }

    @Test fun testNsAlias() {
        tst.extraMod = makeModule {
            constant("MAGIC", 123)
            function("f", result = "integer") { body { -> Rt_UnitValue } }
            alias("MAGIC_REF", "MAGIC", since = "0.10.5")
            alias("f_ref", "f", since = "0.10.5")
        }

        chkVer("query q() = MAGIC_REF;", "ALIAS:[test:MAGIC_REF]")
        chkCompile("query q() = MAGIC;", "OK")

        chkVer("query q() = f_ref();", "ALIAS:[test:f_ref]")
        chkCompile("query q() = f();", "OK")
    }

    @Test fun testNsAliasType() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor { body { -> Rt_UnitValue } }
                staticFunction("f", "integer") { body { -> Rt_UnitValue } }
            }
            alias("tada", "data", since = "0.10.5")
        }

        chkVer("function f(v: tada) {}", "ALIAS:[test:tada]")
        chkVer("function f() = tada();", "ALIAS:[test:tada]")
        chkVer("function f() = tada.f();", "ALIAS:[test:tada]")
    }

    @Test fun tsetNsType() {
        modTst.extraModule {
            type("data", since = "0.10.5") {
                modTst.setRTypeFactory(this)
                constructor { body { -> Rt_UnitValue } }
            }
        }
        chkVer("function q(v: data) = 0;", "TYPE:[test:data]")
        chkVer("function q() = data();", "TYPE:[test:data]")
    }

    @Test fun testNsNamespace() {
        tst.extraMod = makeModule {
            namespace("a") {
                namespace("b", since = "0.10.5") {
                    constant("V1", 123)
                }
            }
            namespace("x.y", since = "0.10.5") {
                constant("V2", 123)
            }
        }
        chkVer("query q() = a.b.V1;", "NAMESPACE:[test:a.b]")
        chkVer("query q() = x.y.V2;", "NAMESPACE:[test:x.y]")
    }

    @Test fun testNsNamespaceDisjoint() {
        tst.extraMod = makeModule {
            namespace("a") { constant("X", 123) }
            namespace("a", since = "0.10.5") {}
            namespace("a") {}

            namespace("b", since = "0.11.0") { constant("X", 123) }
            namespace("b", since = "0.10.5") {}
            namespace("b", since = "0.12.0") {}

            namespace("c", since = "0.10.6") { constant("X", 123) }
            namespace("c", since = "0.10.5") {}
            namespace("c", since = "0.10.4") {}
        }

        chkVer("query q() = a.X;", "NAMESPACE:[test:a]")
        chkVer("query q() = b.X;", "NAMESPACE:[test:b]")
        chkCompile("query q() = c.X;", "OK")
    }

    @Test fun testNsFunctionAlias() {
        tst.extraMod = makeModule {
            function("f1", "integer") {
                alias("f2", since = "0.10.5")
                body { -> Rt_UnitValue }
            }
            function("g1", "integer", since = "0.10.5") {
                alias("g2")
                body { -> Rt_UnitValue }
            }
        }

        chkCompile("query q() = f1();", "OK")
        chkVer("query q() = f2();", "ALIAS:[test:f2]")

        chkCompile("query q() = g2();", "OK")
        chkVer("query q() = g1();", "FUNCTION:[test:g1]")
    }

    @Test fun testNsFunctionOverload() {
        tst.extraMod = makeModule {
            function("f", "integer") {
                param("x", "integer")
                body { -> Rt_UnitValue }
            }
            function("f", "integer", since = "0.10.5") {
                param("x", "text")
                body { -> Rt_UnitValue }
            }
        }

        chkCompile("query q() = f(123);", "OK")
        chkVer("query q() = f('hello');", "FUNCTION:[test:f]")
    }

    @Test fun testTypeBasic() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constant("MAGIC", 123, since = "0.10.5")
                property("prop", "integer", since = "0.10.5") { value { Rt_UnitValue } }
                property("spec_prop", "integer", BaseLTest.makeTypeProp(), since = "0.10.5")
                function("f", "integer", since = "0.10.5") { body { -> Rt_UnitValue } }
                function("g", BaseLTest.makeTypeFun(), since = "0.10.5")
                staticFunction("sf", "integer", since = "0.10.5") { body { -> Rt_UnitValue } }
                staticFunction("sg", BaseLTest.makeNsFun(), since = "0.10.5")
            }
        }

        chkCompile("function f(v: data) {}", "OK")
        chkVer("function f() = data.MAGIC;", "CONSTANT:[test:data.MAGIC]")

        chkVer("function f(v: data) = v.prop;", "PROPERTY:[test:data.prop]")
        chkVer("function f(v: data) = v.spec_prop;", "PROPERTY:[test:data.spec_prop]")

        chkVer("function f(v: data) = v.f();", "FUNCTION:[test:data.f]")
        chkVer("function f(v: data) = v.g();", "FUNCTION:[test:data.g]")

        chkVer("function f() = data.sf();", "FUNCTION:[test:data.sf]")
        chkVer("function f() = data.sg();", "FUNCTION:[test:data.sg]")
    }

    @Test fun testTypeConstructor() {
        modTst.extraModule {
            type("data1") {
                modTst.setRTypeFactory(this)
                constructor(since = "0.10.5") { body { -> Rt_UnitValue } }
            }
            type("data2") {
                modTst.setRTypeFactory(this)
                constructor(BaseLTest.makeTypeCon(), since = "0.10.5")
            }
        }

        chkCompile("function f(v: data1) {}", "OK")
        chkVer("function f() = data1();", "CONSTRUCTOR:[test:data1]")

        chkCompile("function f(v: data2) {}", "OK")
        chkVer("function f() = data2();", "CONSTRUCTOR:[test:data2]")
    }

    @Test fun testTypeConstructorOverload() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor {
                    param("x", "integer")
                    body { -> Rt_UnitValue }
                }
                constructor(since = "0.10.5") {
                    param("x", "text")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkCompile("function f() = data(123);", "OK")
        chkVer("function f() = data('hello');", "CONSTRUCTOR:[test:data]")
    }

    @Test fun testTypeFunctionAlias() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("f1", "integer") {
                    alias("f2", since = "0.10.5")
                    body { -> Rt_UnitValue }
                }
                function("g1", "integer", since = "0.10.5") {
                    alias("g2")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkCompile("function q(v: data) = v.f1();", "OK")
        chkVer("function q(v: data) = v.f2();", "ALIAS:[test:data.f2]")

        chkCompile("function q(v: data) = v.g2();", "OK")
        chkVer("function q(v: data) = v.g1();", "FUNCTION:[test:data.g1]")
    }

    @Test fun testTypeFunctionOverload() {
        modTst.extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("f", "integer") {
                    param("x", "integer")
                    body { -> Rt_UnitValue }
                }
                function("f", "integer", since = "0.10.5") {
                    param("x", "text")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkCompile("function q(v: data) = v.f(123);", "OK")
        chkVer("function q(v: data) = v.f('hello');", "FUNCTION:[test:data.f]")
    }

    @Test fun testExtensionBasic() {
        tst.extraMod = makeModule {
            extension("test_ext", "integer") {
                property("prop", "integer", since = "0.10.5") { value { Rt_UnitValue } }
                function("f", "integer", since = "0.10.5") { body { -> Rt_UnitValue } }
                staticFunction("g", "integer", since = "0.10.5") { body { -> Rt_UnitValue } }
            }
        }

        chkVer("query q(v: integer) = v.prop;", "PROPERTY:[test:test_ext.prop]")
        chkVer("query q(v: integer) = v.f();", "FUNCTION:[test:test_ext.f]")
        chkVer("query q() = integer.g();", "FUNCTION:[test:test_ext.g]")
    }

    @Test fun testParameter() {
        initParameter {
            param("x", "text")
            param("y", "integer", arity = L_ParamArity.ZERO_ONE, since = "0.10.5")
            body { -> Rt_UnitValue }
        }

        chkParameter("", "f", "f")
        chkParameter("v: data", "v.g", "data.g")
        chkParameter("", "tada", "tada")
    }

    private fun initParameter(fnBlock: Ld_CommonFunctionDsl.() -> Ld_BodyResult) {
        tst.allowLibNamedArgsAnyVersion = true
        modTst.extraModule {
            function("f", "integer", block = fnBlock)
            type("data") {
                modTst.setRTypeFactory(this)
                function("g", "integer", block = fnBlock)
            }
            type("tada") {
                modTst.setRTypeFactory(this)
                constructor(block = fnBlock)
            }
        }
    }

    private fun chkParameter(param: String, fn: String, name: String) {
        chkCompile("function q($param) = $fn('');", "OK")
        chkVer("function q($param) = $fn('', 0);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(x = '', y = 0);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(y = 0, x = '');", "PARAMETER:[test:$name.y]")

        chkCompile("function q($param) = $fn(*);", "OK")
        chkVer("function q($param) = $fn(*, 0);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(x = *, y = *);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(y = *, x = *);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(x = '', y = *);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(y = *, x = '');", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(x = *, y = 0);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn(y = 0, x = *);", "PARAMETER:[test:$name.y]")
    }

    @Test fun testParameterZeroMany() {
        initParameter {
            param("x", "text")
            param("y", "integer", arity = L_ParamArity.ZERO_MANY, since = "0.10.5")
            body { -> Rt_UnitValue }
        }

        chkParameterZeroMany("", "f", "f")
        chkParameterZeroMany("v: data", "v.g", "data.g")
        chkParameterZeroMany("", "tada", "tada")
    }

    private fun chkParameterZeroMany(param: String, fn: String, name: String) {
        chkCompile("function q($param) = $fn('');", "OK")
        chkVer("function q($param) = $fn('', 0);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn('', 0, 1);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn('', 0, 1, 2);", "PARAMETER:[test:$name.y]")
        chkVer("function q($param) = $fn('', 0, 1, 2, 3);", "PARAMETER:[test:$name.y]")
    }

    @Test fun testStructAttribute() {
        tst.extraMod = makeModule {
            struct("rec") {
                attribute("x", "text")
                attribute("y", "integer", mutable = true, since = "0.10.5")
            }
        }

        chkCompile("function f(r: rec) = r.x;", "OK")
        chkVer("function f(r: rec) = r.y;", "ATTRIBUTE:[test:rec.y]")
        chkVer("function f(r: rec) { r.y = 0; }", "ATTRIBUTE:[test:rec.y]")
        chkVer("function f() = rec(x = '', y = 0);", "ATTRIBUTE:[test:rec.y]")
        chkVer("function f() = rec('', 0);", "ATTRIBUTE:[test:rec.y]")
        chkVer("function f() = rec(0, '');", "ATTRIBUTE:[test:rec.y]")

        // Must not be an error, but hard to support this case.
        chkCompile("function f() = rec(x = '');", "ct_err:attr_missing:[rec]:y")
    }

    private fun chkVer(code: String, expErr: String) {
        chkVer0(code, null, "OK")
        chkVer0(code, "0.10.6", "OK")
        chkVer0(code, "0.10.5", "OK")
        chkVer0(code, "0.10.4", "ct_err:version:lib:$expErr:0.10.5:0.10.4")
    }

    private fun chkVer0(expr: String, version: String?, exp: String) {
        tst.compatibilityVer = if (version == null) null else R_LangVersion.of(version)
        chkCompile(expr, exp)
    }
}
