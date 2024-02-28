/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.model.Rt_TupleValue
import org.junit.Test

class CLibFunctionPartCallTest: BaseCLibTest() {
    @Test fun testPartCallOptionalParam1() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "text", arity = L_ParamArity.ZERO_ONE)
                bodyN { args -> Rt_TextValue.get(args.joinToString(",", "${this.fnSimpleName}:") { it.strCode() }) }
            }
        }

        val fn = "_foo"
        chk("_type_of($fn(*))", "text[(text)->text]")
        chk("$fn(*)", "fn[$fn(*)]")
        chk("$fn(*)('Bob')", "text[$fn:text[Bob]]")

        chkEx("{ val f: ()->text = $fn(*); return _type_of(f); }", "text[()->text]")
        chkEx("{ val f: ()->text = $fn(*); return f; }", "fn[$fn()]")
        chkEx("{ val f: ()->text = $fn(*); return f(); }", "text[$fn:]")

        chkEx("{ val f: (text)->text = $fn(*); return _type_of(f); }", "text[(text)->text]")
        chkEx("{ val f: (text)->text = $fn(*); return f; }", "fn[$fn(*)]")
        chkEx("{ val f: (text)->text = $fn(*); return f('Bob'); }", "text[$fn:text[Bob]]")
    }

    @Test fun testPartCallOptionalParam2() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "integer")
                param("b", "text", arity = L_ParamArity.ZERO_ONE)
                bodyN { args -> Rt_TextValue.get(args.joinToString(",", "${this.fnSimpleName}:") { it.strCode() }) }
            }
        }

        val fn = "_foo"
        chk("_type_of($fn(*))", "text[(integer,text)->text]")
        chk("$fn(*)", "fn[$fn(*,*)]")
        chk("$fn(*)(123,'Bob')", "text[$fn:int[123],text[Bob]]")

        chkEx("{ val f: (integer)->text = $fn(*); return _type_of(f); }", "text[(integer)->text]")
        chkEx("{ val f: (integer)->text = $fn(*); return f; }", "fn[$fn(*)]")
        chkEx("{ val f: (integer)->text = $fn(*); return f(123); }", "text[$fn:int[123]]")

        chkEx("{ val f: (integer,text)->text = $fn(*); return _type_of(f); }", "text[(integer,text)->text]")
        chkEx("{ val f: (integer,text)->text = $fn(*); return f; }", "fn[$fn(*,*)]")
        chkEx("{ val f: (integer,text)->text = $fn(*); return f(123,'Bob'); }", "text[$fn:int[123],text[Bob]]")

        val err = "ct_err:stmt_var_type"
        chkEx("{ val f: ()->text = $fn(*); return f; }", "$err:f:[()->text]:[(integer,text)->text]")
        chkEx("{ val f: (text)->text = $fn(*); return f; }", "$err:f:[(text)->text]:[(integer,text)->text]")
        chkEx("{ val f: (integer)->integer = $fn(*); return f; }", "$err:f:[(integer)->integer]:[(integer,text)->text]")
    }

    @Test fun testPartCallOptionalParamOverload() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "integer")
                param("b", "text", arity = L_ParamArity.ZERO_ONE)
                bodyN { args -> Rt_TextValue.get(args.joinToString(",", "${this.fnSimpleName}:a:") { it.strCode() }) }
            }
            function("_foo", "text") {
                param("a", "decimal")
                param("b", "text")
                bodyN { args -> Rt_TextValue.get(args.joinToString(",", "${this.fnSimpleName}:b:") { it.strCode() }) }
            }
        }

        val fn = "_foo"
        chk("$fn(*)", "ct_err:expr:call:partial_ambiguous:[$fn]")

        chkEx("{ val f: (integer)->text = $fn(*); return _type_of(f); }", "text[(integer)->text]")
        chkEx("{ val f: (integer)->text = $fn(*); return f; }", "fn[$fn(*)]")
        chkEx("{ val f: (integer)->text = $fn(*); return f(123); }", "text[$fn:a:int[123]]")

        chkEx("{ val f: (integer,text)->text = $fn(*); return _type_of(f); }", "text[(integer,text)->text]")
        chkEx("{ val f: (integer,text)->text = $fn(*); return f; }", "fn[$fn(*,*)]")
        chkEx("{ val f: (integer,text)->text = $fn(*); return f(123,'Bob'); }", "text[$fn:a:int[123],text[Bob]]")

        chkEx("{ val f: (decimal,text)->text = $fn(*); return _type_of(f); }", "text[(decimal,text)->text]")
        chkEx("{ val f: (decimal,text)->text = $fn(*); return f; }", "fn[$fn(*,*)]")
        chkEx("{ val f: (decimal,text)->text = $fn(*); return f(123,'Bob'); }", "text[$fn:b:dec[123],text[Bob]]")

        chkEx("{ val f: (decimal)->text = $fn(*); return f; }", "ct_err:expr:call:partial_ambiguous:[$fn]")
    }

    @Test fun testPartCallCasesNoHintMany() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "integer")
                body { a -> Rt_TextValue.get("_foo(integer):${a.str()}") }
            }
            function("_foo", "text") {
                param("a", "boolean")
                body { a -> Rt_TextValue.get("_foo(boolean):${a.str()}") }
            }
        }
        chk("_foo(*)", "ct_err:expr:call:partial_ambiguous:[_foo]")
    }

    @Test fun testPartCallCasesNoHintOneBad() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "iterable<-any>")
                body { a -> Rt_TextValue.get("_foo:${a.str()}") }
            }
        }
        chk("_foo(*)", "ct_err:expr:call:partial_bad_case:[_foo(iterable<-any>):text]")
    }

    @Test fun testPartCallCasesNoHintOneGood() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "integer")
                body { a -> Rt_TextValue.get("_foo:${a.strCode()}") }
            }
        }
        chk("_foo(*)", "fn[_foo(*)]")
        chk("_foo(*)(123)", "text[_foo:int[123]]")
    }

    @Test fun testPartCallCasesHintMany() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "iterable<integer>")
                body { a -> Rt_TextValue.get("_foo(iterable):${a.str()}") }
            }
            function("_foo", "text") {
                param("a", "collection<integer>")
                body { a -> Rt_TextValue.get("_foo(collection):${a.str()}") }
            }
        }

        val err = "ct_err:expr:call:partial_ambiguous:[_foo]"
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f; }", err)
        chkEx("{ val f: (list<boolean>) -> text = _foo(*); return f; }", err)
    }

    @Test fun testPartCallCasesHintOneBad() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "iterable<-any>")
                body { a -> Rt_TextValue.get("_foo:${a.str()}") }
            }
        }
        chkEx("{ val f = _foo(*); return f; }", "ct_err:expr:call:partial_bad_case:[_foo(iterable<-any>):text]")
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f; }", "fn[_foo(*)]")
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f([123]); }", "text[_foo:[123]]")
    }

    @Test fun testPartCallCasesHintOneGood() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "integer")
                body { a -> Rt_TextValue.get("_foo:${a.strCode()}") }
            }
        }
        chkEx("{ val f: (integer) -> text = _foo(*); return f; }", "fn[_foo(*)]")
        chkEx("{ val f: (integer) -> text = _foo(*); return f(123); }", "text[_foo:int[123]]")
        chkEx("{ val f: (boolean) -> text = _foo(*); return f; }",
            "ct_err:stmt_var_type:f:[(boolean)->text]:[(integer)->text]")
    }

    @Test fun testPartCallExactMatchParams() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                param("a", "(integer?,text)")
                body { a -> Rt_TextValue.get("_foo_0:${a.strCode()}") }
            }
            function("_foo", "text") {
                param("a", "(integer,text?)")
                body { a -> Rt_TextValue.get("_foo_1:${a.strCode()}") }
            }
            function("_foo", "text") {
                param("a", "(integer?,text?)")
                body { a -> Rt_TextValue.get("_foo_2:${a.strCode()}") }
            }
        }

        chkEx("{ val f = _foo(*); return _type_of(f); }", "ct_err:expr:call:partial_ambiguous:[_foo]")
        chkEx("{ val f: ((integer,text)) -> text = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]")

        chkEx("{ val f: ((integer?,text)) -> text = _foo(*); return _type_of(f); }", "text[((integer?,text))->text]")
        chkEx("{ val f: ((integer?,text)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_0:(int[1],text[A])]")

        chkEx("{ val f: ((integer,text?)) -> text = _foo(*); return _type_of(f); }", "text[((integer,text?))->text]")
        chkEx("{ val f: ((integer,text?)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_1:(int[1],text[A])]")

        chkEx("{ val f: ((integer?,text?)) -> text = _foo(*); return _type_of(f); }", "text[((integer?,text?))->text]")
        chkEx("{ val f: ((integer?,text?)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_2:(int[1],text[A])]")
    }

    @Test fun testPartCallExactMatchResult() {
        tst.extraMod = makeModule {
            val tupleType = R_TupleType.create(R_IntegerType, R_TextType)
            function("_foo", "(integer?,text)") {
                body { -> Rt_TupleValue.make(tupleType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_0")) }
            }
            function("_foo", "(integer,text?)") {
                body { -> Rt_TupleValue.make(tupleType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_1")) }
            }
            function("_foo", "(integer?,text?)") {
                body { -> Rt_TupleValue.make(tupleType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_2")) }
            }
        }

        chkEx("{ val f = _foo(*); return _type_of(f); }", "ct_err:expr:call:partial_ambiguous:[_foo]")
        chkEx("{ val f: () -> (integer,text) = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]")

        chkEx("{ val f: () -> (integer?,text) = _foo(*); return _type_of(f); }", "text[()->(integer?,text)]")
        chkEx("{ val f: () -> (integer?,text) = _foo(*); return f(); }", "(int[1],text[_foo_0])")

        chkEx("{ val f: () -> (integer,text?) = _foo(*); return _type_of(f); }", "text[()->(integer,text?)]")
        chkEx("{ val f: () -> (integer,text?) = _foo(*); return f(); }", "(int[1],text[_foo_1])")

        chkEx("{ val f: () -> (integer?,text?) = _foo(*); return _type_of(f); }", "text[()->(integer?,text?)]")
        chkEx("{ val f: () -> (integer?,text?) = _foo(*); return f(); }", "(int[1],text[_foo_2])")

        chkEx("{ val f: () -> (integer?,text?)? = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]")
    }
}
