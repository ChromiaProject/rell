/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.BaseLTest
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl
import net.postchain.rell.base.model.R_TypeMeta
import net.postchain.rell.base.testutils.LibModuleTester
import kotlin.test.Test

class IdeDocLibTest: BaseIdeSymbolTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE)

    private fun extraModule(block: Ld_ModuleDsl.() -> Unit) = modTst.libModule(block)

    @Test fun testNamespaceNamespace() {
        extraModule {
            namespace("ns") {
                type("data", rType = R_IntegerType)
            }
        }
        chkSyms("struct _s { _x: ns.data; }", "ns=DEF_NAMESPACE|-|-", "?head=NAMESPACE|mod:ns")
    }

    @Test fun testNamespaceTypeSimple() {
        extraModule {
            namespace("ns") {
                type("data", rType = R_IntegerType)
            }
        }
        chkSyms("struct _s { _x: ns.data; }", "data=DEF_TYPE|-|-", "?head=TYPE|mod:ns.data")
    }

    @Test fun testNamespaceTypeGeneric() {
        extraModule {
            namespace("ns") {
                type("data") {
                    generic("T")
                    rTypeMeta(R_TypeMeta.make { _ -> R_IntegerType })
                }
            }
        }
        chkSyms("struct _s { _x: ns.data<integer>; }", "data=DEF_TYPE|-|-", "?head=TYPE|mod:ns.data")
    }

    @Test fun testNamespaceStruct() {
        extraModule {
            struct("data") {
                attribute("value", "integer")
            }
        }
        chkSyms("struct _s { _x: data; }", "data=DEF_STRUCT|-|-", "?head=STRUCT|mod:data")
    }

    @Test fun testNamespaceConstant() {
        extraModule {
            constant("MAGIC", 123L)
        }
        chkSyms("query q() = MAGIC;", "MAGIC=DEF_CONSTANT|-|-", "?head=CONSTANT|mod:MAGIC")
    }

    @Test fun testNamespaceProperty() {
        extraModule {
            property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
            property("pure_prop", pure = true, type = "integer") { value { _ -> Rt_UnitValue } }
            property("spec_prop", BaseLTest.makeNsProp(Rt_IntValue.ZERO))
        }
        chkSyms("query q() = prop;", "prop=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|mod:prop")
        chkSyms("query q() = pure_prop;", "pure_prop=MEM_SYS_PROPERTY_PURE|-|-", "?head=PROPERTY|mod:pure_prop")
        chkSyms("query q() = spec_prop;", "spec_prop=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|mod:spec_prop")
    }

    @Test fun testNamespaceFunction() {
        extraModule {
            function("foo", result = "text") {
                param("a", type = "integer")
                body { -> Rt_UnitValue }
            }
        }
        chkSyms("query q() = foo(123);", "foo=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:foo")
    }

    @Test fun testNamespaceFunctionSpecial() {
        extraModule {
            function("foo", BaseLTest.makeNsFun())
        }
        chkSyms("query q() = foo();", "foo=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:foo")
    }

    @Test fun testNamespaceFunctionDeprecated() {
        extraModule {
            function("f", result = "text") {
                deprecated("new_f")
                body { -> Rt_UnitValue }
            }
            function("g", result = "text") {
                deprecated("new_g", error = false)
                body { -> Rt_UnitValue }
            }
        }

        chkSyms("query q() = f();", "f=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:f",
            err = "deprecated:FUNCTION:[mod:f]:new_f")
        chkSyms("query q() = g();", "g=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:g",
            warn = "deprecated:FUNCTION:[mod:g]:new_g")
    }

    @Test fun testNamespaceFunctionAlias() {
        extraModule {
            function("f", result = "text") {
                alias("g")
                alias("h", deprecated = C_MessageType.WARNING)
                alias("k", deprecated = C_MessageType.ERROR)
                body { -> Rt_UnitValue }
            }
        }

        chkSyms("query q() = g();",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:g|<alias> g = [f]\n\n<function> f(): [text]",
        )
        chkSyms("query q() = h();",
            "h=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:h|@deprecated\n<alias> h = [f]\n\n<function> f(): [text]",
            warn = "deprecated:ALIAS:[mod:h]:f",
        )
        chkSyms("query q() = k();",
            "k=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:k|@deprecated(ERROR)\n<alias> k = [f]\n\n<function> f(): [text]",
            err = "deprecated:ALIAS:[mod:k]:f",
        )
    }

    @Test fun testNamespaceAlias() {
        extraModule {
            constant("VALUE", 123)
            struct("data") {
                attribute("x", "integer")
            }
            function("foo", result = "text") { body { -> Rt_UnitValue } }
            alias("value_ref", "VALUE")
            alias("data_ref", "data")
            alias("foo_ref", "foo")
        }

        chkSyms("query q() = value_ref;",
            "value_ref=DEF_CONSTANT|-|-",
            "?doc=ALIAS|mod:value_ref|<alias> value_ref = [VALUE]\n\n<val> VALUE: [integer] = 123",
        )
        chkSyms("struct _s { _x: data_ref; }",
            "data_ref=DEF_STRUCT|-|-",
            "?doc=ALIAS|mod:data_ref|<alias> data_ref = [data]\n\n<struct> data",
        )
        chkSyms("query q() = foo_ref();",
            "foo_ref=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:foo_ref|<alias> foo_ref = [foo]\n\n<function> foo(): [text]",
        )
    }

    @Test fun testNamespaceAliasDeprecated() {
        extraModule {
            function("f", result = "text") {
                deprecated("new_f")
                body { -> Rt_UnitValue }
            }
            alias("link_f", "f")
        }

        chkSyms("query q() = link_f();",
            "link_f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:link_f|<alias> link_f = [f]\n\n@deprecated(ERROR)\n<function> f(): [text]",
        )
    }

    @Test fun testNamespaceAliasType() {
        extraModule {
            alias("data_1", "data")
            alias("data_2", "data", deprecated = C_MessageType.WARNING)
            alias("data_3", "data", deprecated = C_MessageType.ERROR)
            type("data", rType = R_IntegerType)
        }

        val msg = "deprecated:ALIAS"

        chkSyms("struct _s { _x: data_1; }",
            "data_1=DEF_TYPE|-|-", "?doc=ALIAS|mod:data_1|<alias> data_1 = [data]\n\n<type> data")
        chkSyms("struct _s { _x: data_2; }",
            "data_2=DEF_TYPE|-|-",
            "?doc=ALIAS|mod:data_2|@deprecated\n<alias> data_2 = [data]\n\n<type> data",
            warn = "$msg:[mod:data_2]:data",
        )
        chkSyms("struct _s { _x: data_3; }",
            "data_3=DEF_TYPE|-|-",
            "?doc=ALIAS|mod:data_3|@deprecated(ERROR)\n<alias> data_3 = [data]\n\n<type> data",
            err = "$msg:[mod:data_3]:data",
        )
    }

    @Test fun testTypeDefConstant() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constant("VALUE", 123)
            }
        }
        chkSyms("query q() = data.VALUE;", "VALUE=DEF_CONSTANT|-|-", "?head=CONSTANT|mod:data.VALUE")
    }

    @Test fun testTypeDefProperty() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
                property("pure_prop", type = "integer", pure = true) { value { _ -> Rt_UnitValue } }
                property("spec_prop", type = "integer", BaseLTest.makeTypeProp())
            }
        }
        chkSyms("function f(d: data) = d.prop;", "prop=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|mod:data.prop")
        chkSyms("function f(d: data) = d.pure_prop;", "pure_prop=MEM_SYS_PROPERTY_PURE|-|-",
            "?head=PROPERTY|mod:data.pure_prop")
        chkSyms("function f(d: data) = d.spec_prop;", "spec_prop=MEM_SYS_PROPERTY|-|-",
            "?head=PROPERTY|mod:data.spec_prop")
    }

    @Test fun testTypeDefConstructor() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor {
                    param(type = "integer", name = "x")
                    body { -> Rt_UnitValue }
                }
                constructor {
                    deprecated("something_else", error = false)
                    param(type = "byte_array", name = "a")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkSyms("function f() = data(123);", "data=DEF_TYPE|-|-", "?head=CONSTRUCTOR|mod:data")
        chkSyms("function f() = data(x'1234');", "data=DEF_TYPE|-|-", "?head=CONSTRUCTOR|mod:data",
            warn = "deprecated:CONSTRUCTOR:[mod:data]:something_else",
        )
    }

    @Test fun testTypeDefConstructorSpecial() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                constructor(BaseLTest.makeNsFun())
            }
        }
        chkSyms("function f() = data();", "data=DEF_TYPE|-|-", "?head=CONSTRUCTOR|mod:data")
    }

    @Test fun testTypeDefFunction() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("foo", result = "integer") {
                    body { -> Rt_UnitValue }
                }
                function("spec", BaseLTest.makeTypeFun())
                staticFunction("stat", result = "integer") {
                    body { -> Rt_UnitValue }
                }
                staticFunction("stat_spec", BaseLTest.makeNsFun())
            }
        }

        chkSyms("function f(d: data) = d.foo();", "foo=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.foo")
        chkSyms("function f(d: data) = d.spec();", "spec=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.spec")
        chkSyms("function f() = data.stat();", "stat=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.stat")
        chkSyms("function f() = data.stat_spec();", "stat_spec=DEF_FUNCTION_SYSTEM|-|-",
            "?head=FUNCTION|mod:data.stat_spec")
    }

    @Test fun testTypeDefFunctionDeprecated() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("f", result = "text") {
                    deprecated("new_f")
                    body { -> Rt_UnitValue }
                }
                function("g", result = "text") {
                    deprecated("new_g", error = false)
                    body { -> Rt_UnitValue }
                }
                staticFunction("h", result = "text") {
                    deprecated("new_h", error = false)
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkSyms("function _f(d: data) = d.f();", "f=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.f",
            err = "deprecated:FUNCTION:[mod:data.f]:new_f",
        )
        chkSyms("function _f(d: data) = d.g();", "g=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.g",
            warn = "deprecated:FUNCTION:[mod:data.g]:new_g",
        )
        chkSyms("function _f() = data.h();", "h=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|mod:data.h",
            warn = "deprecated:FUNCTION:[mod:data.h]:new_h",
        )
    }

    @Test fun testTypeDefFunctionAlias() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("f", result = "text") {
                    alias("g")
                    alias("h", deprecated = C_MessageType.WARNING)
                    alias("k", deprecated = C_MessageType.ERROR)
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkSyms("function _f(d: data) = d.f();",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
        )
        chkSyms("function _f(d: data) = d.g();",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:data.g|<alias> g = [f]\n\n<function> f(): [text]",
        )
        chkSyms("function _f(d: data) = d.h();",
            "h=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:data.h|@deprecated\n<alias> h = [f]\n\n<function> f(): [text]",
            warn = "deprecated:ALIAS:[mod:data.h]:f",
        )
        chkSyms("function _f(d: data) = d.k();",
            "k=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=ALIAS|mod:data.k|@deprecated(ERROR)\n<alias> k = [f]\n\n<function> f(): [text]",
            err = "deprecated:ALIAS:[mod:data.k]:f",
        )
    }

    @Test fun testExtensionProperty() {
        extraModule {
            extension("test_ext", type = "T") {
                generic("T", subOf = "any")
                property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
                property("pure_prop", type = "integer", pure = true) { value { _ -> Rt_UnitValue } }
                property("spec_prop", type = "integer", BaseLTest.makeTypeProp())
            }
        }

        chkSymsExpr("''.prop",
            "prop=MEM_SYS_PROPERTY|-|-", "?doc=PROPERTY|mod:test_ext.prop|prop: [integer]")
        chkSymsExpr("''.pure_prop",
            "pure_prop=MEM_SYS_PROPERTY_PURE|-|-", "?doc=PROPERTY|mod:test_ext.pure_prop|<pure> pure_prop: [integer]")
        chkSymsExpr("''.spec_prop",
            "spec_prop=MEM_SYS_PROPERTY|-|-", "?doc=PROPERTY|mod:test_ext.spec_prop|spec_prop: [integer]")
    }

    @Test fun testExtensionFunction() {
        extraModule {
            extension("test_ext", type = "T") {
                generic("T", subOf = "any")
                function("foo", result = "T") {
                    body { -> Rt_UnitValue }
                }
                function("spec", BaseLTest.makeTypeFun())
                staticFunction("stat", result = "T") {
                    body { -> Rt_UnitValue }
                }
                staticFunction("stat_spec", BaseLTest.makeNsFun())
            }
        }

        chkSymsExpr("''.foo()",
            "foo=DEF_FUNCTION_SYSTEM|-|-", "?doc=FUNCTION|mod:test_ext.foo|<function> foo(): [T]")
        chkSymsExpr("''.spec()",
            "spec=DEF_FUNCTION_SYSTEM|-|-", "?doc=FUNCTION|mod:test_ext.spec|<function> spec(...)")
        chkSymsExpr("text.stat()",
            "stat=DEF_FUNCTION_SYSTEM|-|-", "?doc=FUNCTION|mod:test_ext.stat|<static> <function> stat(): [T]")
        chkSymsExpr("text.stat_spec()",
            "stat_spec=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:test_ext.stat_spec|<static> <function> stat_spec(...)",
        )
    }

    @Test fun testStructAttribute() {
        extraModule {
            struct("data") {
                attribute("foo", type = "integer")
                attribute("bar", type = "text", mutable = true)
            }
        }

        chkSyms("function _f(d: data) = d.foo;", "foo=MEM_STRUCT_ATTR|-|-", "?head=STRUCT_ATTR|mod:data.foo")
        chkSyms("function _f(d: data) = d.bar;", "bar=MEM_STRUCT_ATTR_VAR|-|-", "?head=STRUCT_ATTR|mod:data.bar")
        chkSyms("function _f() = data(foo = 123, bar = 'hello');",
            "foo=MEM_STRUCT_ATTR|-|-", "?head=STRUCT_ATTR|mod:data.foo",
            "bar=MEM_STRUCT_ATTR_VAR|-|-", "?head=STRUCT_ATTR|mod:data.bar",
        )
    }

    @Test fun testFunctionParameter() {
        extraModule {
            function("f", result = "unit") {
                param(type = "integer", name = "a")
                param(type = "integer", name = "b", lazy = true)
                body { -> Rt_UnitValue }
            }
        }

        chkSyms("function _f() { f(a = 123, b = 456); }",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(\n\ta: [integer],\n\t<lazy> b: [integer]\n): [unit]",
            "a=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|a|a: [integer]",
            "b=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|b|<lazy> b: [integer]",
        )
    }
}
