/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf
import kotlin.test.Test

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
                val a by param("integer", cast = Rt_Value)
                body(Rt_TextValue) { "_foo(integer):${a.str()}" }
            }
            function("_foo", "text") {
                val a by param("boolean", cast = Rt_Value)
                body(Rt_TextValue) { "_foo(boolean):${a.str()}" }
            }
        }
        chk("_foo(*)", "ct_err:expr:call:partial_ambiguous:[_foo]")
    }

    @Test fun testPartCallCasesNoHintOneBad() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("iterable<-any>", cast = Rt_Value)
                body(Rt_TextValue) { "_foo:${a.str()}" }
            }
        }
        chk("_foo(*)", "ct_err:expr:call:partial_bad_case:[_foo(iterable<-any>):text]")
    }

    @Test fun testPartCallCasesNoHintOneGood() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("integer", cast = Rt_Value)
                body(Rt_TextValue) { "_foo:${a.strCode()}" }
            }
        }
        chk("_foo(*)", "fn[_foo(*)]")
        chk("_foo(*)(123)", "text[_foo:int[123]]")
    }

    @Test fun testPartCallCasesHintMany() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("iterable<integer>", cast = Rt_Value)
                body(Rt_TextValue) { "_foo(iterable):${a.str()}" }
            }
            function("_foo", "text") {
                val a by param("collection<integer>", cast = Rt_Value)
                body(Rt_TextValue) { "_foo(collection):${a.str()}" }
            }
        }

        val err = "ct_err:expr:call:partial_ambiguous:[_foo]"
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f; }", err)
        chkEx("{ val f: (list<boolean>) -> text = _foo(*); return f; }", err)
    }

    @Test fun testPartCallCasesHintOneBad() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("iterable<-any>", cast = Rt_Value)
                body { Rt_TextValue.get("_foo:${a.str()}") }
            }
        }
        chkEx("{ val f = _foo(*); return f; }", "ct_err:expr:call:partial_bad_case:[_foo(iterable<-any>):text]")
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f; }", "fn[_foo(*)]")
        chkEx("{ val f: (list<integer>) -> text = _foo(*); return f([123]); }", "text[_foo:[123]]")
    }

    @Test fun testPartCallCasesHintOneGood() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("integer", cast = Rt_Value)
                body { Rt_TextValue.get("_foo:${a.strCode()}") }
            }
        }
        chkEx("{ val f: (integer) -> text = _foo(*); return f; }", "fn[_foo(*)]")
        chkEx("{ val f: (integer) -> text = _foo(*); return f(123); }", "text[_foo:int[123]]")
        chkEx(
            "{ val f: (boolean) -> text = _foo(*); return f; }",
            "ct_err:stmt_var_type:f:[(boolean)->text]:[(integer)->text]"
        )
    }

    @Test fun testPartCallExactMatchParams() {
        tst.extraMod = makeModule {
            function("_foo", "text") {
                val a by param("(integer?,text)", cast = Rt_Value)
                body { Rt_TextValue.get("_foo_0:${a.strCode()}") }
            }
            function("_foo", "text") {
                val a by param("(integer,text?)", cast = Rt_Value)
                body(Rt_TextValue) { "_foo_1:${a.strCode()}" }
            }
            function("_foo", "text") {
                val a by param("(integer?,text?)", cast = Rt_Value)
                body(Rt_TextValue) { "_foo_2:${a.strCode()}" }
            }
        }

        chkEx("{ val f = _foo(*); return _type_of(f); }", "ct_err:expr:call:partial_ambiguous:[_foo]")
        chkEx(
            "{ val f: ((integer,text)) -> text = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]"
        )

        chkEx("{ val f: ((integer?,text)) -> text = _foo(*); return _type_of(f); }", "text[((integer?,text))->text]")
        chkEx("{ val f: ((integer?,text)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_0:(int[1],text[A])]")

        chkEx("{ val f: ((integer,text?)) -> text = _foo(*); return _type_of(f); }", "text[((integer,text?))->text]")
        chkEx("{ val f: ((integer,text?)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_1:(int[1],text[A])]")

        chkEx("{ val f: ((integer?,text?)) -> text = _foo(*); return _type_of(f); }", "text[((integer?,text?))->text]")
        chkEx("{ val f: ((integer?,text?)) -> text = _foo(*); return f((1,'A')); }", "text[_foo_2:(int[1],text[A])]")
    }

    @Test fun testPartCallExactMatchResult() {
        tst.extraMod = makeModule {
            val tupleRtType = Rt_TupleType(
                fields = immListOf(
                    RR_TupleField(null, RR_Type.Primitive(net.postchain.rell.base.model.rr.RR_PrimitiveKind.INTEGER)),
                    RR_TupleField(null, RR_Type.Primitive(net.postchain.rell.base.model.rr.RR_PrimitiveKind.TEXT)),
                ),
                fieldClasses = immListOf(Rt_PrimitiveTypes.INTEGER, Rt_PrimitiveTypes.TEXT),
            )
            function("_foo", "(integer?,text)") {
                body { Rt_TupleValue.make(tupleRtType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_0")) }
            }
            function("_foo", "(integer,text?)") {
                body { Rt_TupleValue.make(tupleRtType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_1")) }
            }
            function("_foo", "(integer?,text?)") {
                body { Rt_TupleValue.make(tupleRtType, Rt_IntValue.get(1), Rt_TextValue.get("_foo_2")) }
            }
        }

        chkEx("{ val f = _foo(*); return _type_of(f); }", "ct_err:expr:call:partial_ambiguous:[_foo]")
        chkEx(
            "{ val f: () -> (integer,text) = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]"
        )

        chkEx("{ val f: () -> (integer?,text) = _foo(*); return _type_of(f); }", "text[()->(integer?,text)]")
        chkEx("{ val f: () -> (integer?,text) = _foo(*); return f(); }", "(int[1],text[_foo_0])")

        chkEx("{ val f: () -> (integer,text?) = _foo(*); return _type_of(f); }", "text[()->(integer,text?)]")
        chkEx("{ val f: () -> (integer,text?) = _foo(*); return f(); }", "(int[1],text[_foo_1])")

        chkEx("{ val f: () -> (integer?,text?) = _foo(*); return _type_of(f); }", "text[()->(integer?,text?)]")
        chkEx("{ val f: () -> (integer?,text?) = _foo(*); return f(); }", "(int[1],text[_foo_2])")

        chkEx(
            "{ val f: () -> (integer?,text?)? = _foo(*); return _type_of(f); }",
            "ct_err:expr:call:partial_ambiguous:[_foo]"
        )
    }
}
