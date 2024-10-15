/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.testutils.LibModuleTester
import org.junit.Test

class IdeCompletionBlockTest: BaseIdeCompletionTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE)

    init {
        defaultLib = true
    }

    @Test fun testBasic() {
        val code = "val C = 123; ^0 function f() { ^1 val x = 123; ^2 } ^3"
        chkComps(code, 0, "C|CONSTANT|:C||integer|", "f|FUNCTION|:f|()|unit|")
        chkComps(code, 1, "C|CONSTANT|:C||integer|", "f|FUNCTION|:f|()|unit|")
        chkComps(code, 2, "C|CONSTANT|:C||integer|", "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 3, "C|CONSTANT|:C||integer|", "f|FUNCTION|:f|()|unit|")
    }

    @Test fun testBlockEmpty() {
        val code = "function f(x: integer) ^0 { ^1 } ^2"
        chkComps(code, 0, "f|FUNCTION|:f|(x: integer)|unit|")
        chkComps(code, 1, "f|FUNCTION|:f|(x: integer)|unit|", "x|PARAMETER|x||integer|-")
        chkComps(code, 2, "f|FUNCTION|:f|(x: integer)|unit|")
    }

    @Test fun testBlockMultipleVars() {
        val code = "function f() { ^0 val x = 1 ^1 ; ^2 val y = 'A' ^3 ; ^4 val z = true ^5; ^6 return; ^7  } ^8"
        chkComps(code, 0, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 1, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 2, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 3, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 4, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||text|-")
        chkComps(code, 5, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||text|-")
        chkComps(code, 6, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||text|-", "z|VAR|z||boolean|-")
        chkComps(code, 7, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||text|-", "z|VAR|z||boolean|-")
        chkComps(code, 8, "f|FUNCTION|:f|()|unit|")
    }

    @Test fun testBlockNested() {
        val code = """
            function f() { ^0
                val i = 1; ^1
                if (1 < 2) { ^2
                    val j = 'A'; ^3
                } ^4 else { ^5
                    val k = true; ^6
                    if (5 > 0) { ^7
                        val p = 0.0; ^8
                    } ^9
                    val q = 5L; ^10
                } ^11
                val r = x"1234"; ^12
            }
        """

        val f = "f|FUNCTION|:f|()|unit|"
        chkComps(code, 0, f)
        chkComps(code, 1, f, "i|VAR|i||integer|-")
        chkComps(code, 2, f, "i|VAR|i||integer|-")
        chkComps(code, 3, f, "i|VAR|i||integer|-", "j|VAR|j||text|-")
        chkComps(code, 4, f, "i|VAR|i||integer|-")
        chkComps(code, 5, f, "i|VAR|i||integer|-")
        chkComps(code, 6, f, "i|VAR|i||integer|-", "k|VAR|k||boolean|-")
        chkComps(code, 7, f, "i|VAR|i||integer|-", "k|VAR|k||boolean|-")
        chkComps(code, 8, f, "i|VAR|i||integer|-", "k|VAR|k||boolean|-", "p|VAR|p||decimal|-")
        chkComps(code, 9, f, "i|VAR|i||integer|-", "k|VAR|k||boolean|-")
        chkComps(code, 10, f, "i|VAR|i||integer|-", "k|VAR|k||boolean|-", "q|VAR|q||big_integer|-")
        chkComps(code, 11, f, "i|VAR|i||integer|-")
        chkComps(code, 12, f, "i|VAR|i||integer|-", "r|VAR|r||byte_array|-")
    }

    @Test fun testForVariableBlock() {
        val code = "function f() { ^0 for ^1 (x in [1,2,3] ^2) ^3 { ^4 print(x); ^5 } ^6 } ^7"
        chkComps(code, 0, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 1, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 2, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 3, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 4, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 5, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 6, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 7, "f|FUNCTION|:f|()|unit|")
    }

    @Test fun testForVariableStatement() {
        val code = "function f() { ^0 for ^1 (x in [1,2,3] ^2) ^3 print(x) ^4; ^5 }"
        chkComps(code, 0, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 1, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 2, "f|FUNCTION|:f|()|unit|")
        chkComps(code, 3, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 4, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-")
        chkComps(code, 5, "f|FUNCTION|:f|()|unit|")
    }

    @Test fun testForVariableMulti() {
        val code = "function f() { for (((x, y), z) in [((1,2),3)]) ^0 { ^1 print(x); ^2 } ^3 }"
        chkComps(code, 0, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||integer|-", "z|VAR|z||integer|-")
        chkComps(code, 1, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||integer|-", "z|VAR|z||integer|-")
        chkComps(code, 2, "f|FUNCTION|:f|()|unit|", "x|VAR|x||integer|-", "y|VAR|y||integer|-", "z|VAR|z||integer|-")
        chkComps(code, 3, "f|FUNCTION|:f|()|unit|")
    }

    @Test fun testParamFunctionBlock() {
        chkParamBlock("function", "FUNCTION", "unit", "")
    }

    @Test fun testParamOperationBlock() {
        chkParamBlock("operation", "OPERATION", "-", "")
    }

    @Test fun testParamQueryBlock() {
        chkParamBlock("query", "QUERY", "integer", "return 123;")
    }

    private fun chkParamBlock(kw: String, kind: String, retType: String, retCode: String) {
        val code = "^0 $kw f(^1 x: integer, ^2 y: text ^3) ^4 { ^5 val z = x + y; ^6 $retCode ^7 } ^8"
        val f = "f|$kind|:f|(x: integer, y: text)|$retType|"
        chkComps(code, 0, f)
        chkComps(code, 1, f)
        chkComps(code, 2, f)
        chkComps(code, 3, f)
        chkComps(code, 4, f)
        chkComps(code, 5, f, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 6, f, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-", "z|VAR|z||text|-")
        chkComps(code, 7, f, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-", "z|VAR|z||text|-")
        chkComps(code, 8, f)
    }

    @Test fun testParamFunctionExpr() {
        chkParamExpr("function", "FUNCTION")
    }

    @Test fun testParamQueryExpr() {
        chkParamExpr("query", "QUERY")
    }

    private fun chkParamExpr(kw: String, kind: String) {
        chkParamExpr0(kind, "^0 $kw f(^1 x: integer, ^2 y: text ^3) ^4 ^5 ^6 = ^7 123 ^8; ^9")
        chkParamExpr0(kind, "^0 $kw f(^1 x: integer, ^2 y: text ^3) ^4 : ^5 integer ^6 = ^7 123 ^8; ^9")
    }

    private fun chkParamExpr0(kind: String, code: String) {
        val f = "f|$kind|:f|(x: integer, y: text)|integer|"
        chkComps(code, 0, f)
        chkComps(code, 1, f)
        chkComps(code, 2, f)
        chkComps(code, 3, f)
        chkComps(code, 4, f)
        chkComps(code, 5, f)
        chkComps(code, 6, f)
        chkComps(code, 7, f, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 8, f, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 9, f)
    }

    @Test fun testCallArgumentsUserFunction() {
        def("function _f(x: integer, y: text = 'A') = 123;")

        val c = arrayOf("g|FUNCTION|:g|(i: boolean)|integer|", "i|PARAMETER|i||boolean|-")

        var code = "function g(i: boolean) = ^0 _f ^1 ( ^2 ) ^3;"
        val err = "expr:call:missing_args:[_f]:[0:x]"
        chkComps(code, 0, *c, err = err)
        chkComps(code, 1, *c, err = err)
        chkComps(code, 2, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-", err = err)
        chkComps(code, 3, *c, err = err)

        code = "function g(i: boolean) = ^0 _f ^1 ( ^2 123 ^3 ) ^4;"
        chkComps(code, 0, *c)
        chkComps(code, 1, *c)
        chkComps(code, 2, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 3, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 4, *c)

        code = "function g(i: boolean) = ^0 _f ^1 ( ^2 x = ^3 123 ^4 ) ^5;"
        chkComps(code, 0, *c)
        chkComps(code, 1, *c)
        chkComps(code, 2, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 3, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 4, *c, "x|PARAMETER|x||integer|-", "y|PARAMETER|y||text|-")
        chkComps(code, 5, *c)
    }

    @Test fun testCallArgumentsLibConstructor() {
        modTst.libModule {
            type("_data") {
                modTst.setRTypeFactory(this)
                constructor {
                    param("x", "integer")
                    param("y", "text")
                    body { -> Rt_UnitValue }
                }
            }
        }

        val code = "function f() { _data ^0 ( ^1 ) ^2; }"
        val err = "expr:call:missing_args:[_data]:[0:x,1:y]"
        val f = "f|FUNCTION|:f|()|unit|"

        chkComps(code, 0, f, err = err)
        chkComps(code, 1, f, "x|PARAMETER|x||integer|mod:_data", "y|PARAMETER|y||text|mod:_data", err = err)
        chkComps(code, 2, f, err = err)
    }

    @Test fun testCallArgumentsLibFunctionOfNamespace() {
        modTst.libModule {
            function("_g", "integer") {
                param("x", "integer")
                param("y", "boolean", arity = L_ParamArity.ZERO_ONE)
                param("z", "text", arity = L_ParamArity.ZERO_MANY)
                body { -> Rt_UnitValue }
            }
        }

        val code = "function f() { _g ^0 ( ^1 ) ^2; }"
        val err = "expr:call:missing_args:[_g]:[0:x]"
        val f = "f|FUNCTION|:f|()|unit|"

        chkComps(code, 0, f, err = err)
        chkComps(code, 1, f, "x|PARAMETER|x||integer|mod:_g", "y|PARAMETER|y||boolean|mod:_g", err = err)
        chkComps(code, 2, f, err = err)
    }

    @Test fun testCallArgumentsLibFunctionOfType() {
        modTst.libModule {
            type("_data") {
                modTst.setRTypeFactory(this)
                constructor {
                    body { -> Rt_UnitValue }
                }
                function("a", "unit") {
                    param("x", "integer")
                    param("y", "text")
                    body { -> Rt_UnitValue }
                }
                staticFunction("b", "unit") {
                    param("i", "boolean")
                    param("j", "decimal")
                    body { -> Rt_UnitValue }
                }
            }
        }

        val code = "function f() { _data().a ^0 ( ^1 ) ^2; }"
        val err = "expr:call:missing_args:[_data.a]:[0:x,1:y]"
        val f = "f|FUNCTION|:f|()|unit|"

        chkComps(code, 0, f, err = err)
        chkComps(code, 1, f, "x|PARAMETER|x||integer|mod:_data.a", "y|PARAMETER|y||text|mod:_data.a", err = err)
        chkComps(code, 2, f, err = err)
    }

    @Test fun testCallArgumentsLibFunctionOverload() {
        modTst.libModule {
            function("_g", "integer") {
                param("x", "integer")
                body { -> Rt_UnitValue }
            }
            function("_g", "text") {
                param("x", "text")
                param("y", "boolean")
                body { -> Rt_UnitValue }
            }
            function("_g", "integer") {
                param("y", "boolean")
                param("z", "decimal")
                body { -> Rt_UnitValue }
            }
        }

        val code = "function f() { _g ^0 ( ^1 ) ^2; }"
        val err = "expr_call_badargs:[_g]:[]"
        val f = "f|FUNCTION|:f|()|unit|"

        chkComps(code, 0, f, err = err)
        chkComps(code, 1, f,
            "x|PARAMETER|x||integer|mod:_g",
            "x|PARAMETER|x||text|mod:_g",
            "y|PARAMETER|y||boolean|mod:_g",
            "y|PARAMETER|y||boolean|mod:_g",
            "z|PARAMETER|z||decimal|mod:_g",
            err = err,
        )
        chkComps(code, 2, f, err = err)
    }

    @Test fun testCallArgumentsStructConstructor() {
        def("struct _data { x: integer; y: text = 'A'; }")

        val code = "function f(i: boolean) = ^0 _data ^1 ( ^2 y = 'B' ^3 ) ^4;"
        val g = arrayOf("f|FUNCTION|:f|(i: boolean)|_data|", "i|PARAMETER|i||boolean|-")
        val err = "attr_missing:[_data]:x"

        chkComps(code, 0, *g, err = err)
        chkComps(code, 1, *g, err = err)
        chkComps(code, 2, *g, "x|STRUCT_ATTR|:_data.x||integer|_data", "y|STRUCT_ATTR|:_data.y||text|_data", err = err)
        chkComps(code, 3, *g, "x|STRUCT_ATTR|:_data.x||integer|_data", "y|STRUCT_ATTR|:_data.y||text|_data", err = err)
        chkComps(code, 4, *g, err = err)
    }

    @Test fun testCreateEntityAttributes() {
        def("entity data { x: integer; y: text; }")

        val g = arrayOf("data|ENTITY|:data||-|", "f|FUNCTION|:f|(i: boolean)|data|", "i|PARAMETER|i||boolean|-")
        var code = "function f(i: boolean) = create ^0 data ^1 ( ^2 x = 1, ^3 y = 'a' ^4 ) ^5;"
        chkComps(code, 0, *g)
        chkComps(code, 1, *g)
        chkComps(code, 2, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data")
        chkComps(code, 3, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data")
        chkComps(code, 4, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data")
        chkComps(code, 5, *g)

        code = "function f(i: boolean): data = create ^0 data ^1 ( ^2 ) ^3;"
        val err = "attr_missing:[data]:x,y"
        chkComps(code, 0, *g, err = err)
        chkComps(code, 1, *g, err = err)
        chkComps(code, 2, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data", err = err)
        chkComps(code, 3, *g, err = err)
    }

    @Test fun testCreateUnknownName() {
        def("entity data { x: integer; y: text; }")

        val g = arrayOf("data|ENTITY|:data||-|", "f|FUNCTION|:f|(i: boolean)|data|", "i|PARAMETER|i||boolean|-")
        val code = "function f(i: boolean): data = create data ^0 ( ^1 foo ^2 ) ^3;"
        val err = "unknown_name:foo"

        chkComps(code, 0, *g, err = err)
        chkComps(code, 1, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data", err = err)
        chkComps(code, 2, *g, "x|ENTITY_ATTR|:data.x||integer|:data", "y|ENTITY_ATTR|:data.y||text|:data", err = err)
        chkComps(code, 3, *g, err = err)
    }

    @Test fun testAtEntity() {
        val code = "entity data { x: integer; } function _f() = data @ { ^0 };"
        chkComps(code, 0,
            "$|AT_VAR_DB|$||data|-",
            ".rowid|ENTITY_ATTR|:data.rowid||rowid|:data",
            ".x|ENTITY_ATTR|:data.x||integer|:data",
            "data|ENTITY|:data||-|",
            "data|AT_VAR_DB|data||data|-",
        )
    }
}
