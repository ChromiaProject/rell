/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty_RtValue
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.L_ParamImplication
import org.junit.Test
import java.math.BigInteger

class LDocTest: BaseLTest() {
    @Test fun testModule() {
        val mod = makeDocModule("foo.bar") {
            type("data")
            namespace("ns") {
                type("other")
            }
        }
        chkDoc(mod, "", "MODULE|foo.bar", "<module>")
    }

    @Test fun testNamespaceNamespace() {
        val mod = makeDocModule {
            namespace("ns") {
                type("data")
            }
        }
        chkDoc(mod, "ns", "NAMESPACE|mod:ns", "<namespace> ns")
    }

    @Test fun testNamespaceTypeSimple() {
        val mod = makeDocModule {
            type("secret", hidden = true)
            type("parent", abstract = true)
            type("data") {
                parent("parent")
            }
        }
        chkDoc(mod, "parent", "TYPE|mod:parent", "<abstract> <type> parent")
        chkDoc(mod, "data", "TYPE|mod:data", "<type> data: [parent]")
        chkDoc(mod, "secret", "TYPE|mod:secret", "<internal> <type> secret")
    }

    @Test fun testNamespaceTypeGeneric() {
        val mod = makeDocModule {
            type("hashable")
            type("_iterable", abstract = true) {
                generic("T")
            }
            type("_map") {
                generic("K", subOf = "hashable")
                generic("V")
                parent("_iterable<(K,V)>")
            }
        }

        chkDoc(mod, "_iterable", "TYPE|mod:_iterable", "<abstract> <type> _iterable<T>")
        chkDoc(mod, "_map", "TYPE|mod:_map", "<type> _map<K: -[hashable], V>: [_iterable]<([K], [V])>")
    }

    @Test fun testNamespaceTypeAlias() {
        val mod = makeDocModule {
            alias("data_1", "data")
            alias("data_2", "data", deprecated = C_MessageType.WARNING)
            alias("data_3", "data", deprecated = C_MessageType.ERROR)
            type("data")
        }

        chkDoc(mod, "data", "TYPE|mod:data", "<type> data")
        chkDoc(mod, "data_1", "ALIAS|mod:data_1", "<alias> data_1 = [data]\n\n<type> data")
        chkDoc(mod, "data_2", "ALIAS|mod:data_2", "@deprecated\n<alias> data_2 = [data]\n\n<type> data")
        chkDoc(mod, "data_3", "ALIAS|mod:data_3", "@deprecated(ERROR)\n<alias> data_3 = [data]\n\n<type> data")
    }

    @Test fun testNamespaceExtensionSimple() {
        val mod = makeDocModule {
            extension("test_ext", type = "any") {
                property("v", type = "integer") { value { _ -> Rt_UnitValue } }
            }
        }
        chkDoc(mod, "test_ext", "TYPE_EXTENSION|mod:test_ext", "<extension> test_ext: [any]")
    }

    @Test fun testNamespaceExtensionGeneric() {
        val mod = makeDocModule {
            type("_comparable")
            type("_iterable", abstract = true) {
                generic("T")
            }
            extension("test_ext", type = "_iterable<T>") {
                generic("T", subOf = "_comparable")
                function("sorted", result = "_iterable<T>") {
                    body { _ -> Rt_UnitValue }
                }
            }
        }
        chkDoc(mod, "test_ext",
            "TYPE_EXTENSION|mod:test_ext",
            "<extension> test_ext<T: -[_comparable]>: [_iterable]<[T]>",
        )
    }

    @Test fun testNamespaceStruct() {
        val mod = makeDocModule {
            struct("data") {
                attribute("value", "integer")
            }
        }
        chkDoc(mod, "data", "STRUCT|mod:data", "<struct> data")
    }

    @Test fun testNamespaceConstant() {
        val mod = makeDocModule {
            constant("INT", 123L)
            constant("BIGINT", BigInteger.valueOf(123))
            constant("FIXED_VAL", "integer", Rt_IntValue.get(456))
            constant("LATE_VAL", "integer") { value { Rt_IntValue.get(789) } }
        }
        chkDoc(mod, "INT", "CONSTANT|mod:INT", "<val> INT: [integer] = 123")
        chkDoc(mod, "BIGINT", "CONSTANT|mod:BIGINT", "<val> BIGINT: [big_integer] = 123L")
        chkDoc(mod, "FIXED_VAL", "CONSTANT|mod:FIXED_VAL", "<val> FIXED_VAL: [integer] = 456")
        chkDoc(mod, "LATE_VAL", "CONSTANT|mod:LATE_VAL", "<val> LATE_VAL: [integer] = 789")
    }

    @Test fun testNamespaceProperty() {
        val mod = makeDocModule {
            property("prop", type = "integer") { value { Rt_UnitValue } }
            property("pure_prop", type = "integer", pure = true) { value { Rt_UnitValue } }
        }
        chkDoc(mod, "prop", "PROPERTY|mod:prop", "prop: [integer]")
        chkDoc(mod, "pure_prop", "PROPERTY|mod:pure_prop", "<pure> pure_prop: [integer]")
    }

    @Test fun testNamespacePropertySpecial() {
        val mod = makeDocModule {
            property("prop", C_NamespaceProperty_RtValue(Rt_UnitValue))
        }
        chkDoc(mod, "prop", "PROPERTY|mod:prop", "prop")
    }

    @Test fun testNamespaceFunction() {
        val mod = makeDocModule {
            function("foo", result = "text") {
                param("a", type = "integer")
                param("b", type = "decimal")
                body { -> Rt_UnitValue }
            }
            function("pure_1", result = "text", pure = true) { body { -> Rt_UnitValue } }
            function("pure_2", result = "text") {
                bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
            }
        }
        chkDoc(mod, "foo", "FUNCTION|mod:foo", "<function> foo(\n\ta: [integer],\n\tb: [decimal]\n): [text]")
        chkDoc(mod, "pure_1", "FUNCTION|mod:pure_1", "<pure> <function> pure_1(): [text]")
        chkDoc(mod, "pure_2", "FUNCTION|mod:pure_2", "<pure> <function> pure_2(): [text]")
    }

    @Test fun testNamespaceFunctionGeneric() {
        val mod = makeDocModule {
            function("foo", result = "T?") {
                generic("T", subOf = "any")
                param("a", type = "(T,integer)")
                body { -> Rt_UnitValue }
            }
        }
        chkDoc(mod, "foo", "FUNCTION|mod:foo", "<function> <T: -[any]> foo(\n\ta: ([T], [integer])\n): [T]?")
    }

    @Test fun testNamespaceFunctionSpecial() {
        val mod = makeDocModule {
            function("foo", makeNsFun())
        }
        chkDoc(mod, "foo", "FUNCTION|mod:foo", "<function> foo(...)")
    }

    @Test fun testNamespaceFunctionDeprecated() {
        val mod = makeDocModule {
            function("f", result = "text") {
                deprecated("new_f")
                param("a", type = "integer")
                body { -> Rt_UnitValue }
            }
            function("g", result = "text") {
                deprecated("new_g", error = false)
                param("a", type = "integer")
                body { -> Rt_UnitValue }
            }
        }

        chkDoc(mod, "f", "FUNCTION|mod:f", "@deprecated(ERROR)\n<function> f(\n\ta: [integer]\n): [text]")
        chkDoc(mod, "g", "FUNCTION|mod:g", "@deprecated\n<function> g(\n\ta: [integer]\n): [text]")
    }

    @Test fun testNamespaceFunctionAlias() {
        val mod = makeDocModule {
            function("f", result = "text") {
                alias("g")
                alias("h", deprecated = C_MessageType.WARNING)
                alias("k", deprecated = C_MessageType.ERROR)
                body { -> Rt_UnitValue }
            }
        }

        chkDoc(mod, "f", "FUNCTION|mod:f", "<function> f(): [text]")
        chkDoc(mod, "g", "ALIAS|mod:g", "<alias> g = [f]\n\n<function> f(): [text]")
        chkDoc(mod, "h", "ALIAS|mod:h", "@deprecated\n<alias> h = [f]\n\n<function> f(): [text]")
        chkDoc(mod, "k", "ALIAS|mod:k", "@deprecated(ERROR)\n<alias> k = [f]\n\n<function> f(): [text]")
    }

    @Test fun testNamespaceAlias() {
        val mod = makeDocModule {
            constant("VALUE", 123)
            struct("data") {
                attribute("x", "integer")
            }
            function("foo", result = "unit") { body { -> Rt_UnitValue } }
            namespace("sub") {
                constant("SUB_VALUE", 456)
            }
            alias("value_ref", "VALUE")
            alias("data_ref", "data")
            alias("foo_ref", "foo")
            alias("sub_value_ref", "sub.SUB_VALUE")
        }

        chkDoc(mod, "value_ref", "ALIAS|mod:value_ref", "<alias> value_ref = [VALUE]\n\n<val> VALUE: [integer] = 123")
        chkDoc(mod, "data_ref", "ALIAS|mod:data_ref", "<alias> data_ref = [data]\n\n<struct> data")
        chkDoc(mod, "foo_ref", "ALIAS|mod:foo_ref", "<alias> foo_ref = [foo]\n\n<function> foo(): [unit]")
        chkDoc(mod, "sub_value_ref", "ALIAS|mod:sub_value_ref",
            "<alias> sub_value_ref = [sub.SUB_VALUE]\n\n<val> SUB_VALUE: [integer] = 456")
    }

    @Test fun testNamespaceAliasDeprecated() {
        val mod = makeDocModule {
            function("f", result = "text") {
                deprecated("new_f")
                body { -> Rt_UnitValue }
            }
            alias("link_f", "f")
        }

        chkDoc(mod, "f", "FUNCTION|mod:f", "@deprecated(ERROR)\n<function> f(): [text]")
        chkDoc(mod, "link_f", "ALIAS|mod:link_f", "<alias> link_f = [f]\n\n@deprecated(ERROR)\n<function> f(): [text]")
    }

    @Test fun testTypeDefConstant() {
        val mod = makeDocModule {
            type("data") {
                constant("VALUE", 123)
            }
        }
        chkDoc(mod, "data.VALUE", "CONSTANT|mod:data.VALUE", "<val> VALUE: [integer] = 123")
    }

    @Test fun testTypeDefProperty() {
        val mod = makeDocModule {
            type("data") {
                property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
                property("pure_prop", type = "integer", pure = true) { value { _ -> Rt_UnitValue } }
                property("spec_prop", type = "integer", makeTypeProp())
                property("pure_spec_prop", type = "integer", makeTypeProp(pure = true))
            }
        }
        chkDoc(mod, "data.prop", "PROPERTY|mod:data.prop", "prop: [integer]")
        chkDoc(mod, "data.pure_prop", "PROPERTY|mod:data.pure_prop", "<pure> pure_prop: [integer]")
        chkDoc(mod, "data.spec_prop", "PROPERTY|mod:data.spec_prop", "spec_prop: [integer]")
        chkDoc(mod, "data.pure_spec_prop", "PROPERTY|mod:data.pure_spec_prop", "<pure> pure_spec_prop: [integer]")
    }

    @Test fun testTypeDefConstructor() {
        val mod = makeDocModule {
            type("data") {
                constructor {
                    param(type = "integer", name = "x")
                    body { -> Rt_UnitValue }
                }
                constructor(pure = true) {
                    param(type = "text", name = "y")
                    body { -> Rt_UnitValue }
                }
                constructor {
                    param(type = "integer", name = "z")
                    bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
                }
                constructor {
                    deprecated("...", error = false)
                    param(type = "byte_array", name = "a")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkDoc(mod, "data.!init#0", "CONSTRUCTOR|mod:data", "<constructor>(\n\tx: [integer]\n)")
        chkDoc(mod, "data.!init#1", "CONSTRUCTOR|mod:data", "<pure> <constructor>(\n\ty: [text]\n)")
        chkDoc(mod, "data.!init#2", "CONSTRUCTOR|mod:data", "<pure> <constructor>(\n\tz: [integer]\n)")
        chkDoc(mod, "data.!init#3", "CONSTRUCTOR|mod:data", "@deprecated\n<constructor>(\n\ta: [byte_array]\n)")
    }

    @Test fun testTypeDefConstructorSpecial() {
        val mod = makeDocModule {
            type("data") {
                constructor(makeNsFun())
            }
        }

        chkDoc(mod, "data.!init", "CONSTRUCTOR|mod:data", "<constructor>(...)")
    }

    @Test fun testTypeDefConstructorGeneric() {
        val mod = makeDocModule {
            type("data") {
                constructor {
                    generic("T", subOf = "integer")
                    param(type = "list<T>", name = "a")
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkDoc(mod, "data.!init", "CONSTRUCTOR|mod:data", "<constructor><T: -[integer]>(\n\ta: [list]<[T]>\n)")
    }

    @Test fun testTypeDefFunction() {
        val mod = makeDocModule {
            type("data") {
                function("foo", result = "integer") {
                    param(type = "text", name = "x")
                    body { -> Rt_UnitValue }
                }
                function("spec", makeTypeFun())
                staticFunction("stat", result = "integer") {
                    param(type = "text", name = "x")
                    body { -> Rt_UnitValue }
                }
                staticFunction("stat_spec", makeNsFun())

                function("pure_1", result = "text", pure = true) { body { -> Rt_UnitValue } }
                function("pure_2", result = "text") {
                    bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
                }
                staticFunction("stat_pure_1", result = "text", pure = true) { body { -> Rt_UnitValue } }
                staticFunction("stat_pure_2", result = "text") {
                    bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
                }
            }
        }

        chkDoc(mod, "data.foo", "FUNCTION|mod:data.foo", "<function> foo(\n\tx: [text]\n): [integer]")
        chkDoc(mod, "data.spec", "FUNCTION|mod:data.spec", "<function> spec(...)")
        chkDoc(mod, "data.stat", "FUNCTION|mod:data.stat", "<static> <function> stat(\n\tx: [text]\n): [integer]")
        chkDoc(mod, "data.stat_spec", "FUNCTION|mod:data.stat_spec", "<static> <function> stat_spec(...)")
        chkDoc(mod, "data.pure_1", "FUNCTION|mod:data.pure_1", "<pure> <function> pure_1(): [text]")
        chkDoc(mod, "data.pure_2", "FUNCTION|mod:data.pure_2", "<pure> <function> pure_2(): [text]")
        chkDoc(mod, "data.stat_pure_1", "FUNCTION|mod:data.stat_pure_1", "<pure> <static> <function> stat_pure_1(): [text]")
        chkDoc(mod, "data.stat_pure_2", "FUNCTION|mod:data.stat_pure_2", "<pure> <static> <function> stat_pure_2(): [text]")
    }

    @Test fun testTypeDefFunctionDeprecated() {
        val mod = makeDocModule {
            type("data") {
                function("f", result = "text") {
                    deprecated("new_f")
                    param("a", type = "integer")
                    body { -> Rt_UnitValue }
                }
                function("g", result = "text") {
                    deprecated("new_g", error = false)
                    param("a", type = "integer")
                    body { -> Rt_UnitValue }
                }
                staticFunction("h", result = "text") {
                    deprecated("new_h", error = false)
                    param("a", type = "integer")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkDoc(mod, "data.f", "FUNCTION|mod:data.f", "@deprecated(ERROR)\n<function> f(\n\ta: [integer]\n): [text]")
        chkDoc(mod, "data.g", "FUNCTION|mod:data.g", "@deprecated\n<function> g(\n\ta: [integer]\n): [text]")
        chkDoc(mod, "data.h", "FUNCTION|mod:data.h", "@deprecated\n<static> <function> h(\n\ta: [integer]\n): [text]")
    }

    @Test fun testTypeDefFunctionAlias() {
        val mod = makeDocModule {
            type("data") {
                function("f", result = "text") {
                    alias("g")
                    alias("h", deprecated = C_MessageType.WARNING)
                    alias("k", deprecated = C_MessageType.ERROR)
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkDoc(mod, "data.f", "FUNCTION|mod:data.f", "<function> f(): [text]")
        chkDoc(mod, "data.g", "ALIAS|mod:data.g", "<alias> g = [f]\n\n<function> f(): [text]")
        chkDoc(mod, "data.h", "ALIAS|mod:data.h", "@deprecated\n<alias> h = [f]\n\n<function> f(): [text]")
        chkDoc(mod, "data.k", "ALIAS|mod:data.k", "@deprecated(ERROR)\n<alias> k = [f]\n\n<function> f(): [text]")
    }

    @Test fun testStructAttribute() {
        val mod = makeDocModule {
            struct("data") {
                attribute("foo", type = "integer")
                attribute("bar", type = "text", mutable = true)
            }
        }
        chkDoc(mod, "data.foo", "STRUCT_ATTR|mod:data.foo", "foo: [integer]")
        chkDoc(mod, "data.bar", "STRUCT_ATTR|mod:data.bar", "<mutable> bar: [text]")
    }

    @Test fun testFunctionParameter() {
        val mod = makeDocModule {
            function("f", result = "unit") {
                param(type = "integer", name = "a")
                param(type = "integer", name = "b", lazy = true)
                param(type = "integer", name = "c", arity = L_ParamArity.ZERO_MANY)
                body { -> Rt_UnitValue }
            }
        }
        chkDoc(mod, "f.a", "PARAMETER|a", "a: [integer]")
        chkDoc(mod, "f.b", "PARAMETER|b", "<lazy> b: [integer]")
        chkDoc(mod, "f.c", "PARAMETER|c", "<zero_many> c: [integer]")
    }

    @Test fun testFunctionMultipleParameters() {
        val mod = makeDocModule {
            function("f", result = "text") {
                body { -> Rt_UnitValue }
            }
            function("g", result = "text") {
                param(type = "integer", name = "x")
                body { -> Rt_UnitValue }
            }
            function("h", result = "text") {
                param(type = "integer", name = "x")
                param(type = "integer", name = "y")
                body { -> Rt_UnitValue }
            }
            function("p", result = "text") {
                param(type = "integer", name = "x")
                param(type = "integer", name = "y")
                param(type = "integer", name = "z")
                body { -> Rt_UnitValue }
            }
        }

        chkDoc(mod, "f", "FUNCTION|mod:f", "<function> f(): [text]")
        chkDoc(mod, "g", "FUNCTION|mod:g", "<function> g(\n\tx: [integer]\n): [text]")
        chkDoc(mod, "h", "FUNCTION|mod:h", "<function> h(\n\tx: [integer],\n\ty: [integer]\n): [text]")
        chkDoc(mod, "p", "FUNCTION|mod:p", "<function> p(\n\tx: [integer],\n\ty: [integer],\n\tz: [integer]\n): [text]")
    }

    @Test fun testFunctionParamModifiers() {
        chkFunParamModifiers("a: [integer]") { param(type = "integer", name = "a") }
        chkFunParamModifiers("<exact> a: [integer]") { param(type = "integer", name = "a", exact = true) }
        chkFunParamModifiers("<nullable> a: [integer]?") { param("a", type = "integer?", nullable = true) }
        chkFunParamModifiers("<lazy> a: [integer]") { param("a", type = "integer", lazy = true) }
        chkFunParamModifiers("<zero_one> a: [integer]") { param("a", type = "integer", arity = L_ParamArity.ZERO_ONE) }
        chkFunParamModifiers("<zero_many> a: [integer]") { param("a", type = "integer", arity = L_ParamArity.ZERO_MANY) }
        chkFunParamModifiers("<one_many> a: [integer]") { param("a", type = "integer", arity = L_ParamArity.ONE_MANY) }
        chkFunParamModifiers("@implies(NOT_NULL) a: [integer]?") {
            param("a", type = "integer?", implies = L_ParamImplication.NOT_NULL)
        }
        chkFunParamModifiers("<exact> <lazy> <zero_many> a: [integer]") {
            param("a", type = "integer", exact = true, lazy = true, arity = L_ParamArity.ZERO_MANY)
        }
    }

    private fun chkFunParamModifiers(expected: String, block: Ld_FunctionDsl.() -> Unit) {
        val mod = makeDocModule {
            function("foo", result = "unit") {
                block(this)
                body { -> Rt_UnitValue }
            }
        }
        chkDoc(mod, "foo", "FUNCTION|mod:foo", "<function> foo(\n\t$expected\n): [unit]")
    }

    @Test fun testExtensionProperty() {
        val mod = makeDocModule {
            extension("ext", type = "T") {
                generic("T", subOf = "any")
                property("prop", type = "integer") { value { _ -> Rt_UnitValue } }
                property("pure_prop", type = "integer", pure = true) { value { _ -> Rt_UnitValue } }
                property("spec_prop", type = "integer", makeTypeProp())
                property("pure_spec_prop", type = "integer", makeTypeProp(pure = true))
            }
        }
        chkDoc(mod, "ext.prop", "PROPERTY|mod:ext.prop", "prop: [integer]")
        chkDoc(mod, "ext.pure_prop", "PROPERTY|mod:ext.pure_prop", "<pure> pure_prop: [integer]")
        chkDoc(mod, "ext.spec_prop", "PROPERTY|mod:ext.spec_prop", "spec_prop: [integer]")
        chkDoc(mod, "ext.pure_spec_prop", "PROPERTY|mod:ext.pure_spec_prop", "<pure> pure_spec_prop: [integer]")
    }

    @Test fun testExtensionFunction() {
        val mod = makeDocModule {
            extension("ext", type = "T") {
                generic("T", subOf = "any")
                function("foo", result = "integer") {
                    param(type = "text", name = "x")
                    body { -> Rt_UnitValue }
                }
                function("spec", makeTypeFun())
                staticFunction("stat", result = "integer") {
                    param(type = "text", name = "x")
                    body { -> Rt_UnitValue }
                }
                staticFunction("stat_spec", makeNsFun())

                function("pure_1", result = "text", pure = true) { body { -> Rt_UnitValue } }
                function("pure_2", result = "text") {
                    bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
                }
                staticFunction("stat_pure_1", result = "text", pure = true) { body { -> Rt_UnitValue } }
                staticFunction("stat_pure_2", result = "text") {
                    bodyRaw(C_SysFunctionBody(true, { _, _ -> Rt_UnitValue }, null))
                }
            }
        }

        chkDoc(mod, "ext.foo", "FUNCTION|mod:ext.foo", "<function> foo(\n\tx: [text]\n): [integer]")
        chkDoc(mod, "ext.spec", "FUNCTION|mod:ext.spec", "<function> spec(...)")
        chkDoc(mod, "ext.stat", "FUNCTION|mod:ext.stat", "<static> <function> stat(\n\tx: [text]\n): [integer]")
        chkDoc(mod, "ext.stat_spec", "FUNCTION|mod:ext.stat_spec", "<static> <function> stat_spec(...)")
        chkDoc(mod, "ext.pure_1", "FUNCTION|mod:ext.pure_1", "<pure> <function> pure_1(): [text]")
        chkDoc(mod, "ext.pure_2", "FUNCTION|mod:ext.pure_2", "<pure> <function> pure_2(): [text]")
        chkDoc(mod, "ext.stat_pure_1", "FUNCTION|mod:ext.stat_pure_1", "<pure> <static> <function> stat_pure_1(): [text]")
        chkDoc(mod, "ext.stat_pure_2", "FUNCTION|mod:ext.stat_pure_2", "<pure> <static> <function> stat_pure_2(): [text]")
    }

    @Test fun testTypeExprSimple() {
        chkTypeExpr("anything", "[anything]")
        chkTypeExpr("nothing", "[nothing]")
        chkTypeExpr("any", "[any]")
        chkTypeExpr("null", "<null>")

        chkTypeExpr("integer", "[integer]")
        chkTypeExpr("my_struct", "[my_struct]")
        chkTypeExpr("my_type", "[my_type]")

        chkTypeExpr("T", "[T]")
        chkTypeExpr("integer?", "[integer]?")
    }

    @Test fun testTypeExprComplex() {
        chkTypeExpr("(integer,text)->boolean", "([integer], [text]) -> [boolean]")
        chkTypeExpr("(integer,text)->boolean?", "([integer], [text]) -> [boolean]?")
        chkTypeExpr("((integer,text)->boolean)?", "(([integer], [text]) -> [boolean])?")

        chkTypeExpr("(integer,text)", "([integer], [text])")
        chkTypeExpr("(a:integer,text)", "(a: [integer], [text])")
        chkTypeExpr("(integer,b:text)", "([integer], b: [text])")
        chkTypeExpr("(a:integer,b:text)", "(a: [integer], b: [text])")
    }

    @Test fun testTypeExprGeneric() {
        chkTypeExpr("my_list<integer>", "[my_list]<[integer]>")
        chkTypeExpr("my_map<integer,text>", "[my_map]<[integer], [text]>")
        chkTypeExpr("my_map<integer,list<(text,boolean)>>", "[my_map]<[integer], [list]<([text], [boolean])>>")

        chkTypeExpr("my_list<-integer>", "[my_list]<-[integer]>")
        chkTypeExpr("my_list<+integer>", "[my_list]<+[integer]>")
        chkTypeExpr("my_list<*>", "[my_list]<*>")
    }

    private fun chkTypeExpr(type: String, expected: String) {
        val mod = makeDocModule {
            struct("my_struct") {}
            type("my_type")
            type("my_list") {
                generic("T")
            }
            type("my_map") {
                generic("K")
                generic("V")
            }
            function("test_fn", result = type) {
                generic("T")
                body { -> Rt_UnitValue }
            }
        }
        chkDoc(mod, "test_fn", "FUNCTION|mod:test_fn", "<function> <T> test_fn(): $expected")
    }

    private fun makeDocModule(name: String = "mod", block: Ld_ModuleDsl.() -> Unit): L_Module {
        return makeModule(name) {
            imports(Lib_Rell.MODULE.lModule)
            block(this)
        }
    }
}
