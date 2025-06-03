/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_CommonFunctionDsl
import net.postchain.rell.base.testutils.LibModuleTester
import org.junit.Test

class CLibFunctionNamedArgsTest: BaseCLibTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE)

    @Test fun testNamedArgsSimple() {
        initNamedArgs {
            param("a", type = "integer")
            param("b", type = "boolean")
        }

        chkNamedArgs("(123, true)", "OK", "text[#0:123,true]")
        chkNamedArgs("(a = 123, b = true)", "OK", "text[#0:123,true]")
        chkNamedArgs("(123, b = true)", "OK", "text[#0:123,true]")
        chkNamedArgs("(a = 123, true)", "ct_err:expr:call:positional_after_named")
        chkNamedArgs("(b = true, a = 123)", "OK", "text[#0:123,true]")
        chkNamedArgs("(123)", "ct_err:expr:call:missing_args:[FN]:[1:b]")
        chkNamedArgs("(a = 123)", "ct_err:expr:call:missing_args:[FN]:[1:b]")
        chkNamedArgs("(b = true)", "ct_err:expr:call:missing_args:[FN]:[0:a]")
        chkNamedArgs("(b = 123, a = true)", "ct_err:expr_call_badargs:[FN]:[b:integer,a:boolean]")
    }

    @Test fun testNamedArgsMoreErrors() {
        initNamedArgs {
            param("a", type = "integer")
            param("b", type = "boolean")
        }

        chkNamedArgs("(a = 'A')", "ct_err:expr:call:missing_args:[FN]:[1:b]") //TODO must be also wrong type error
        chkNamedArgs("(b = 'A')", "ct_err:expr:call:missing_args:[FN]:[0:a]") //TODO must be also wrong type error

        chkNamedArgs("(a = 123, c = true)",
            "ct_err:[expr:call:missing_args:[FN]:[1:b]][expr:call:unknown_named_arg:[FN]:c]")
        chkNamedArgs("(c = 123, b = true)",
            "ct_err:[expr:call:missing_args:[FN]:[0:a]][expr:call:unknown_named_arg:[FN]:c]")
        chkNamedArgs("(a = '', c = 123)",
            "ct_err:[expr:call:missing_args:[FN]:[1:b]][expr:call:unknown_named_arg:[FN]:c]") //TODO +wrong type error
        chkNamedArgs("(c = 123, b = '')",
            "ct_err:[expr:call:missing_args:[FN]:[0:a]][expr:call:unknown_named_arg:[FN]:c]") //TODO +wrong type error
    }

    @Test fun testNamedArgsVarargZeroMany() {
        initNamedArgs {
            param("a", type = "integer")
            param("b", type = "text", arity = L_ParamArity.ZERO_MANY)
        }

        chkNamedArgs("(123)", "OK", "text[#0:123]")
        chkNamedArgs("(a = 123)", "OK", "text[#0:123]")
        chkNamedArgs("(123, 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(123, 'A', 'B')", "OK", "text[#0:123,A,B]")
        chkNamedArgs("(a = 123, b = 'A')", "ct_err:expr:call:named_arg_vararg:[FN]:b")
        chkNamedArgs("(b = 'A', a = 123)", "ct_err:expr:call:named_arg_vararg:[FN]:b")
    }

    @Test fun testNamedArgsVarargOneMany() {
        initNamedArgs {
            param("a", type = "integer")
            param("b", type = "text", arity = L_ParamArity.ONE_MANY)
        }

        chkNamedArgs("(123)", "ct_err:expr:call:missing_args:[FN]:[1:b]")
        chkNamedArgs("(a = 123)", "ct_err:expr:call:missing_args:[FN]:[1:b]")
        chkNamedArgs("(123, 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(123, 'A', 'B')", "OK", "text[#0:123,A,B]")
        chkNamedArgs("(a = 123, b = 'A')", "ct_err:expr:call:named_arg_vararg:[FN]:b")
        chkNamedArgs("(b = 'A', a = 123)", "ct_err:expr:call:named_arg_vararg:[FN]:b")
        chkNamedArgs("(123, 'A', b = 'A')", "ct_err:expr:call:named_conflict:[FN]:b")
        chkNamedArgs("(123, b = 'A', b = 'A')", "ct_err:[expr:call:named_arg_vararg:[FN]:b][expr:call:named_arg_dup:b]")
    }

    @Test fun testNamedArgsZeroOne() {
        initNamedArgs {
            param("a", type = "integer", arity = L_ParamArity.ZERO_ONE)
            param("b", type = "text", arity = L_ParamArity.ZERO_ONE)
        }

        chkNamedArgs("()", "OK", "text[#0:]")
        chkNamedArgs("(123)", "OK", "text[#0:123]")
        chkNamedArgs("(123, 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(a = 123)", "OK", "text[#0:123]")
        chkNamedArgs("(a = 123, b = 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(123, b = 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(b = 'A', a = 123)", "OK", "text[#0:123,A]")
        chkNamedArgs("(b = 'A')", "ct_err:expr:call:missing_args:[FN]:[0:a]")
    }

    @Test fun testNamedArgsGeneric() {
        initNamedArgs {
            generic("T")
            param("a", type = "list<T>")
            param("b", type = "T")
        }

        chkNamedArgs("([123], 456)", "OK", "text[#0:[123],456]")
        chkNamedArgs("([123], b = 456)", "OK", "text[#0:[123],456]")
        chkNamedArgs("(a = [123], b = 456)", "OK", "text[#0:[123],456]")
        chkNamedArgs("(b = 456, a = [123])", "OK", "text[#0:[123],456]")
        chkNamedArgs("([123], 'A')", "ct_err:expr_call_badargs:[FN]:[list<integer>,text]")
        chkNamedArgs("(a = [123], b = 'A')", "ct_err:expr_call_badargs:[FN]:[a:list<integer>,b:text]")
        chkNamedArgs("(b = 'A', a = [123])", "ct_err:expr_call_badargs:[FN]:[b:text,a:list<integer>]")
    }

    @Test fun testNamedArgsOverload() {
        val block1: Ld_CommonFunctionDsl.() -> Unit = {
            param("a", "integer")
            param("b", "text")
        }
        val block2: Ld_CommonFunctionDsl.() -> Unit = {
            param("b", "text")
            param("c", "boolean")
        }
        initNamedArgsOverload(listOf(block1, block2))

        chkNamedArgs("(123, 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("('A', true)", "OK", "text[#1:A,true]")

        chkNamedArgs("(a = 123, b = 'A')", "OK", "text[#0:123,A]")
        chkNamedArgs("(b = 'A', a = 123)", "OK", "text[#0:123,A]")
        chkNamedArgs("(b = 'A', c = true)", "OK", "text[#1:A,true]")
        chkNamedArgs("(c = true, b = 'A')", "OK", "text[#1:A,true]")

        chkNamedArgs("(a = 123, b = 'A', c = true)", "ct_err:expr_call_badargs:[FN]:[a:integer,b:text,c:boolean]")
        chkNamedArgs("(c = true, b = 'A', a = 123)", "ct_err:expr_call_badargs:[FN]:[c:boolean,b:text,a:integer]")
        chkNamedArgs("(a = 123, c = true)", "ct_err:expr_call_badargs:[FN]:[a:integer,c:boolean]")
        chkNamedArgs("(c = true, a = 123)", "ct_err:expr_call_badargs:[FN]:[c:boolean,a:integer]")

        chkNamedArgs("(a = 123)", "ct_err:expr_call_badargs:[FN]:[a:integer]")
        chkNamedArgs("(b = 'A')", "ct_err:expr_call_badargs:[FN]:[b:text]")
        chkNamedArgs("(c = true)", "ct_err:expr_call_badargs:[FN]:[c:boolean]")

        chkNamedArgs("(a = 123, b = 'A', x = 0.0)", "ct_err:expr_call_badargs:[FN]:[a:integer,b:text,x:decimal]")
        chkNamedArgs("(b = 'A', c = true, x = 0.0)", "ct_err:expr_call_badargs:[FN]:[b:text,c:boolean,x:decimal]")
        chkNamedArgs("(a = 123, x = 0.0)", "ct_err:expr_call_badargs:[FN]:[a:integer,x:decimal]")
        chkNamedArgs("(b = 'A', x = 0.0)", "ct_err:expr_call_badargs:[FN]:[b:text,x:decimal]")
        chkNamedArgs("(c = true, x = 0.0)", "ct_err:expr_call_badargs:[FN]:[c:boolean,x:decimal]")
    }

    // Special case of reordering arguments for nullable-flag parameters.
    @Test fun testNamedArgsNullable() {
        initNamedArgs {
            param("a", type = "text")
            param("b", type = "integer?", nullable = true)
        }

        chkNamedArgs("(a = 'A', b = 123)", "ct_err:expr_call_badargs:[FN]:[a:text,b:integer]")
        chkNamedArgs("(b = 123, a = 'A')", "ct_err:expr_call_badargs:[FN]:[b:integer,a:text]")
        chkNamedArgs("(a = 'A', b = _nullable(123))", "OK", "text[#0:A,123]")
        chkNamedArgs("(b = _nullable(123), a = 'A')", "OK", "text[#0:A,123]")

        val body = "val x: integer? = 123; return {FN}{CODE};"
        chkNamedArgs("(a = 'A', b = x)", "OK", "text[#0:A,123]", body = body, warn = "expr:smartnull:var:never:[x]")
        chkNamedArgs("(b = x, a = 'A')", "OK", "text[#0:A,123]", body = body, warn = "expr:smartnull:var:never:[x]")
    }

    @Test fun testNamedArgsImplication() {
        initNamedArgs {
            param("a", type = "integer?", implies = L_ParamImplication.NOT_NULL)
            param("b", type = "text")
        }

        val body = "val x: integer? = _nullable(123); {FN}{CODE}; return _type_of(x);"
        chkNamedArgs("(a = 123, b = 'A')", "OK", "text[integer?]", body = body)
        chkNamedArgs("(a = x, b = 'A')", "OK", "text[integer]", body = body)
        chkNamedArgs("(b = 'A', a = x)", "OK", "text[integer]", body = body)
    }

    @Test fun testPartCall() {
        initNamedArgs {
            param("a", type = "text")
            param("b", type = "integer")
        }

        //TODO make normal tests (w/o chkCompile) when custom test types are supported
        chkPartCall("f", ::chkFull, null)
        chkPartCall("g", ::chkCompile, "OK")
        chkPartCall("data().h", ::chkCompile, "OK")
    }

    private fun chkPartCall(fn: String, tester: (String, String) -> Unit, exp: String?) {
        tester("query q() = $fn(a = 'a', b = 123);", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(a = 'a', b = 123, *);", exp ?: "fn[f(text[a],int[123])]")
        tester("query q() = $fn(a = 'a', b = 123, *)();", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(a = *, b = *);", exp ?: "fn[f(*,*)]")
        tester("query q() = $fn(a = *, b = *)('a', 123);", exp ?: "text[#0:a,123]")

        tester("query q() = $fn(b = 123, a = 'a');", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(b = 123, a = 'a', *);", exp ?: "fn[f(text[a],int[123])]")
        tester("query q() = $fn(b = 123, a = 'a', *)();", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(b = *, a = *);", exp ?: "fn[f(*,*)]")
        tester("query q() = $fn(b = *, a = *)(123, 'a');", exp ?: "text[#0:a,123]")

        tester("query q() = $fn(a = 'a', *);", exp ?: "fn[f(text[a],*)]")
        tester("query q() = $fn(a = 'a', *)(123);", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(a = *);", exp ?: "fn[f(*,*)]")
        tester("query q() = $fn(a = *)('a', 123);", exp ?: "text[#0:a,123]")

        tester("query q() = $fn(b = 123, *);", exp ?: "fn[f(*,int[123])]")
        tester("query q() = $fn(b = 123, *)('a');", exp ?: "text[#0:a,123]")
        tester("query q() = $fn(b = *);", exp ?: "fn[f(*,*)]")
        tester("query q() = $fn(b = *)(123, 'a');", exp ?: "text[#0:a,123]")
    }

    private fun initNamedArgs(block: Ld_CommonFunctionDsl.() -> Unit) {
        initNamedArgsOverload(listOf(block))
    }

    private fun initNamedArgsOverload(blocks: List<Ld_CommonFunctionDsl.() -> Unit>) {
        modTst.libModule {
            blocks.forEachIndexed { i, block ->
                function("f", result = "text") {
                    block(this)
                    bodyN { args -> Rt_TextValue.get("#$i:${args.joinToString(","){it.str()}}") }
                }
            }
            type("g") {
                modTst.setRTypeFactory(this)
                blocks.forEach { block ->
                    constructor {
                        block(this)
                        body { _ -> Rt_UnitValue }
                    }
                }
            }
            type("data") {
                modTst.setRTypeFactory(this)
                constructor {
                    body { -> Rt_UnitValue }
                }
                blocks.forEachIndexed { i, block ->
                    function("h", result = "text") {
                        block(this)
                        bodyN { args -> Rt_TextValue.get("#$i:${args.drop(1).joinToString(","){it.str()}}") }
                    }
                }
            }
        }
    }

    private fun chkNamedArgs(
        code: String,
        expCompile: String,
        expEval: String = expCompile,
        body: String = "return {FN}{CODE};",
        warn: String? = null,
    ) {
        chkNamedArgs0("f", body, code, true, expCompile, expEval, warn)
        chkNamedArgs0("g", body, code, false, expCompile, expEval, warn)
        chkNamedArgs0("data().h", body, code, false, expCompile, expEval, warn)
    }

    private fun chkNamedArgs0(
        fn: String,
        body: String,
        code: String,
        eval: Boolean,
        expCompile: String,
        expEval: String,
        warn: String?,
    ) {
        val fnName = fn.replace("()", "")
        val realBody = body.replace("{FN}", fn).replace("{CODE}", code)
        val fullCode = "query q() { $realBody }"
        chkCompile(fullCode, expCompile.replace("FN", fnName))
        if (eval) {
            chkFull(fullCode, expEval.replace("FN", fnName))
        }
        if (warn != null) {
            chkWarn(warn)
        }
    }
}
