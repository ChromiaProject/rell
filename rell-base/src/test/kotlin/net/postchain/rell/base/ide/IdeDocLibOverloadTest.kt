/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl
import net.postchain.rell.base.testutils.LibModuleTester
import org.junit.Test

class IdeDocLibOverloadTest: BaseIdeSymbolTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE)

    private fun extraModule(block: Ld_ModuleDsl.() -> Unit) = modTst.libModule(block)

    @Test fun testNamespaceFunctionOverload() {
        initNsFnOverload()

        chkSyms("function _f() = f();",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(): [text]",
        )
        chkSyms("function _f() = f(0);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(\n\tx: [integer]\n): [boolean]",
        )
        chkSyms("function _f() = f(0.0, x'');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(\n\ta: [decimal],\n\tb: [byte_array]\n): [integer]",
        )
    }

    @Test fun testNamespaceFunctionOverloadErrors() {
        initNsFnOverload()

        // TODO docs must have list of all function cases
        chkSyms("function _f() = f(false);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(): [text]",
            err = "expr_call_badargs:[f]:[boolean]",
        )
        chkSyms("function _f() = f;",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(): [text]",
            err = "expr_novalue:function:[f]",
        )
    }

    @Test fun testNamespaceFunctionOverloadPartCall() {
        initNsFnOverload()
        // TODO docs must have list of all function cases
        chkSyms("function _f() = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(): [text]",
            err = "expr:call:partial_ambiguous:[f]",
        )
        chkSyms("function _f(): () -> text = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(): [text]",
        )
        chkSyms("function _f(): (integer) -> boolean = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> f(\n\tx: [integer]\n): [boolean]",
        )
    }

    private fun initNsFnOverload() {
        extraModule {
            function("f", result = "text") {
                body { -> Rt_UnitValue }
            }
            function("f", result = "boolean") {
                param(name = "x", type = "integer")
                body { -> Rt_UnitValue }
            }
            function("f", result = "integer") {
                param(name = "a", type = "decimal")
                param(name = "b", type = "byte_array")
                body { -> Rt_UnitValue }
            }
        }
    }

    @Test fun testNamespaceGenericFunctionOverload() {
        initNsGenFnOverload()
        chkSyms("function _f() = f('');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\tx: [T]\n): [integer]",
        )
        chkSyms("function _f() = f(x'', 0.0);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\ta: [byte_array],\n\tb: [T]\n): [T]",
        )
    }

    //TODO support partial call
    /*@Test*/ fun testNamespaceGenericFunctionOverloadPartCall() {
        initNsGenFnOverload()
        chkSyms("function _f() = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\tx: [T]\n): [integer]",
            err = "...",
        )
        chkSyms("function _f(): (text) -> integer = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\tx: [T]\n): [integer]",
        )
        chkSyms("function _f(): (byte_array, decimal) -> decimal = f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\ta: [byte_array],\n\tb: [T]\n): [T]",
        )
    }

    private fun initNsGenFnOverload() {
        extraModule {
            function("f", result = "integer") {
                generic("T")
                param(name = "x", type = "T")
                body { -> Rt_UnitValue }
            }
            function("f", result = "T") {
                generic("T")
                param(name = "a", type = "byte_array")
                param(name = "b", type = "T")
                body { -> Rt_UnitValue }
            }
        }
    }

    @Test fun testNamespaceGenericFunctionOverloadError() {
        // Tricky test to cover the error global case (C_GlobalErrorLibFuncCaseMatch).
        // Relying on an unresolved unused type argument causing an error case, but this may change in the future.
        extraModule {
            function("f", result = "T") {
                generic("T")
                param(name = "x", type = "integer")
                body { -> Rt_UnitValue }
            }
            function("f", result = "T") {
                generic("T")
                param(name = "a", type = "byte_array")
                param(name = "b", type = "text")
                body { -> Rt_UnitValue }
            }
        }

        chkSyms("function _f() = f(123);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\tx: [integer]\n): [T]",
            err = "fn:sys:unresolved_type_params:[f]:T",
        )
        chkSyms("function _f() = f(x'', '');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:f|<function> <T> f(\n\ta: [byte_array],\n\tb: [text]\n): [T]",
            err = "fn:sys:unresolved_type_params:[f]:T",
        )
    }

    @Test fun testTypeDefConstructorOverload() {
        initTypeConOverload()
        chkSyms("function _f() = data('');",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text]\n)",
        )
        chkSyms("function _f() = data(0);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ty: [integer]\n)",
        )
    }

    @Test fun testTypeDefConstructorOverloadErrors() {
        initTypeConOverload()
        chkSyms("function _f() = data(false);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text]\n)",
            err = "expr_call_badargs:[data]:[boolean]",
        )
        chkSyms("function _f() = data;",
            "data=DEF_TYPE|-|-",
            "?doc=TYPE|mod:data|<type> data",
            err = "expr_novalue:type:[data]",
        )
    }

    @Test fun testTypeDefConstructorOverloadPartCall() {
        initTypeConOverload()
        // TODO docs must have list of all function cases
        chkSyms("function _f() = data(*);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text]\n)",
            err = "expr:call:partial_ambiguous:[data]",
        )
        chkSyms("function _f(): (text) -> data = data(*);",
            "data=DEF_TYPE|-|-",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text]\n)",
        )
        chkSyms("function _f() = data(0);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ty: [integer]\n)",
        )
    }

    @Test fun testTypeDefConstructorOverloadAlias() {
        initTypeConOverload()
        chkSyms("function _f() = tada('');",
            "tada=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text]\n)",
        )
        chkSyms("function _f() = tada(0);",
            "tada=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ty: [integer]\n)",
        )
    }

    private fun initTypeConOverload() {
        extraModule {
            alias("tada", "data")
            type("data") {
                modTst.setRTypeFactory(this)
                constructor {
                    param(name = "x", type = "text")
                    body { -> Rt_UnitValue }
                }
                constructor {
                    param(name = "y", type = "integer")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    @Test fun testTypeDefFunctionOverload() {
        initTypeFnOverload()
        chkSyms("function _f(d: data) = d.f();",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
        )
        chkSyms("function _f(d: data) = d.f(0);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\tx: [integer]\n): [boolean]",
        )
        chkSyms("function _f(d: data) = d.f(0.0, x'');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\ta: [decimal],\n\tb: [byte_array]\n): [integer]",
        )
    }

    @Test fun testTypeDefFunctionOverloadErrors() {
        initTypeFnOverload()

        // TODO docs must have list of all function cases
        chkSyms("function _f(d: data) = d.f(false);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
            err = "expr_call_badargs:[data.f]:[boolean]",
        )
        chkSyms("function _f(d: data) = d.f(foo = 'hello');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
            err = "expr_call_badargs:[data.f]:[foo:text]",
        )
        chkSyms("function _f(d: data) = d.f;",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
            err = "expr_novalue:function:[f]",
        )
    }

    @Test fun testTypeDefFunctionOverloadPartCall() {
        initTypeFnOverload()
        // TODO docs must have list of all function cases
        chkSyms("function _f(d: data) = d.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
            err = "expr:call:partial_ambiguous:[data.f]",
        )
        chkSyms("function _f(d: data): () -> text = d.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(): [text]",
        )
        chkSyms("function _f(d: data): (integer) -> boolean = d.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\tx: [integer]\n): [boolean]",
        )
        chkSyms("function _f(d: data): (decimal, byte_array) -> integer = d.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\ta: [decimal],\n\tb: [byte_array]\n): [integer]",
        )
    }

    private fun initTypeFnOverload() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                function("f", result = "text") {
                    body { -> Rt_UnitValue }
                }
                function("f", result = "boolean") {
                    param(name = "x", type = "integer")
                    body { -> Rt_UnitValue }
                }
                function("f", result = "integer") {
                    param(name = "a", type = "decimal")
                    param(name = "b", type = "byte_array")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    @Test fun testTypeDefFunctionOverloadStatic() {
        initTypeFnOverloadStatic()
        chkSyms("function _f() = data.f();",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
        )
        chkSyms("function _f() = data.f(0);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(\n\tx: [integer]\n): [boolean]",
        )
    }

    @Test fun testTypeDefFunctionOverloadStaticErrors() {
        initTypeFnOverloadStatic()

        // TODO docs must have list of all function cases
        chkSyms("function _f() = data.f(false);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
            err = "expr_call_badargs:[data.f]:[boolean]",
        )
        chkSyms("function _f() = data.f(foo = 'hello');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
            err = "expr_call_badargs:[data.f]:[foo:text]",
        )
        chkSyms("function _f() = data.f;",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
            err = "expr_novalue:function:[data.f]",
        )
    }

    @Test fun testTypeDefFunctionOverloadStaticPartCall() {
        initTypeFnOverloadStatic()
        // TODO docs must have list of all function cases
        chkSyms("function _f() = data.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
            err = "expr:call:partial_ambiguous:[data.f]",
        )
        chkSyms("function _f(): () -> text = data.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(): [text]",
        )
        chkSyms("function _f(): (integer) -> boolean = data.f(*);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<static> <function> f(\n\tx: [integer]\n): [boolean]",
        )
    }

    private fun initTypeFnOverloadStatic() {
        extraModule {
            type("data") {
                modTst.setRTypeFactory(this)
                staticFunction("f", result = "text") {
                    body { -> Rt_UnitValue }
                }
                staticFunction("f", result = "boolean") {
                    param(name = "x", type = "integer")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    @Test fun testTypeDefGenericConstructorOverloadSpecific() {
        initTypeGenConOverload()
        chkSyms("function _f() = data<decimal>('', 0.0);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
            "decimal=DEF_TYPE|-|-",
        )
        chkSyms("function _f() = data<decimal>(0.0, 123);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ta: [T],\n\tb: [integer]\n)",
            "decimal=DEF_TYPE|-|-",
        )
    }

    @Test fun testTypeDefGenericConstructorOverloadSpecificPartCall() {
        initTypeGenConOverload()
        chkSyms("function _f() = data<decimal>(*);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
            err = "expr:call:partial_ambiguous:[data<decimal>]",
        )
        chkSyms("function _f(): (text, decimal) -> data<decimal> = data<decimal>(*);",
            "data=DEF_TYPE|-|-",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
        )
        chkSyms("function _f(): (decimal, integer) -> data<decimal> = data<decimal>(*);",
            "data=DEF_TYPE|-|-",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ta: [T],\n\tb: [integer]\n)",
        )
    }

    @Test fun testTypeDefGenericConstructorOverloadRaw() {
        initTypeGenConOverload()
        chkSyms("function _f() = data('', 0.0);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
        )
        chkSyms("function _f() = data(0.0, 123);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ta: [T],\n\tb: [integer]\n)",
        )
    }

    //TODO support partial call
    /*@Test*/ fun testTypeDefGenericConstructorOverloadRawPartCall() {
        initTypeGenConOverload()
        chkSyms("function _f() = data(*);",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
            err = "...",
        )
        chkSyms("function _f(): (text, decimal) -> data = data(*);",
            "data=DEF_TYPE|-|-",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\tx: [text],\n\ty: [T]\n)",
        )
        chkSyms("function _f(): (decimal, integer) -> data = data(*);",
            "data=DEF_TYPE|-|-",
            "data=DEF_TYPE|-|-",
            "?doc=CONSTRUCTOR|mod:data|<constructor>(\n\ta: [T],\n\tb: [integer]\n)",
        )
    }

    private fun initTypeGenConOverload() {
        extraModule {
            type("data") {
                generic("T")
                modTst.setRTypeFactory(this, genericCount = 1)
                constructor {
                    param(name = "x", type = "text")
                    param(name = "y", type = "T")
                    body { -> Rt_UnitValue }
                }
                constructor {
                    param(name = "a", type = "T")
                    param(name = "b", type = "integer")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    @Test fun testTypeDefGenericFunctionOverload() {
        initTypeGenFnOverload()
        chkSyms("function _f(d: data<decimal>) = d.f(0.0);",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\tx: [T]\n): [boolean]",
        )
        chkSyms("function _f(d: data<decimal>) = d.f(0.0, '');",
            "f=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.f|<function> f(\n\ta: [T],\n\tb: [text]\n): [integer]",
        )
        chkSyms("function _f(d: data<decimal>) = d.g(0.0, 1L);",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.g|<function> <R> g(\n\tx: [T],\n\ty: [R]\n): [boolean]",
        )
        chkSyms("function _f(d: data<decimal>) = d.g(1L, 0.0, '');",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|mod:data.g|<function> <R> g(\n\ta: [R],\n\tb: [T],\n\tc: [text]\n): [integer]",
        )
    }

    //TODO support partial call
    /*@Test*/ fun testTypeDefGenericFunctionOverloadPartCall() {
        initTypeGenFnOverload()
        chkSyms("function _f(d: data<decimal>) = d.g(*);",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=mod:data.g|<function> <R> g(\n\tx: [T],\n\ty: [R]): [boolean]",
            err = "...",
        )
        chkSyms("function _f(d: data<decimal>): (decimal, big_integer) -> boolean = d.g(*);",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=mod:data.g|<function> <R> g(\n\tx: [T],\n\ty: [R]): [boolean]",
        )
        chkSyms("function _f(d: data<decimal>): (big_integer, decimal, text) -> integer = d.g(*);",
            "g=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=mod:data.g|<function> <R> g(\n\ta: [R],\n\tb: [T],\n\tc: [text]\n): [integer]",
        )
    }

    private fun initTypeGenFnOverload() {
        extraModule {
            type("data") {
                generic("T")
                modTst.setRTypeFactory(this, genericCount = 1)
                function("f", result = "boolean") {
                    param(name = "x", type = "T")
                    body { -> Rt_UnitValue }
                }
                function("f", result = "integer") {
                    param(name = "a", type = "T")
                    param(name = "b", type = "text")
                    body { -> Rt_UnitValue }
                }
                function("g", result = "boolean") {
                    generic("R")
                    param(name = "x", type = "T")
                    param(name = "y", type = "R")
                    body { -> Rt_UnitValue }
                }
                function("g", result = "integer") {
                    generic("R")
                    param(name = "a", type = "R")
                    param(name = "b", type = "T")
                    param(name = "c", type = "text")
                    body { -> Rt_UnitValue }
                }
            }
        }
    }
}
