/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lib.type.Rt_UnitValue
import org.junit.Test

class LFunctionTest: BaseLTest() {
    @Test fun testFunctionBasic() {
        val mod = makeModule("test") {
            type("text") {}
            type("boolean") {}
            type("json") {
                function("to_text") {
                    result("text")
                    param(name = "pretty", type = "boolean", arity = L_ParamArity.ZERO_ONE)
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkTypeMems(mod, "json", "function to_text(@arity(ZERO_ONE) pretty: boolean): text")
    }

    @Test fun testFunctionHeader() {
        val mod = makeModule("test") {
            type("integer")
            type("text")
            type("data") {
                function("param_conflict") {
                    param("a", "text")
                    chkErr("LDE:common_fun:param_name_conflict:a") { param("a", type = "integer") }
                    param("b", "integer")
                    result(type = "integer")
                    body { -> Rt_UnitValue }
                }
                function("result_after_params") {
                    param("a", "text")
                    result(type = "integer")
                    body { -> Rt_UnitValue }
                }
                function("result_before_params") {
                    result(type = "integer")
                    param("a", "text")
                    body { -> Rt_UnitValue }
                }
                function("result_between_params") {
                    param("a", "text")
                    result(type = "integer")
                    chkErr("LDE:common_fun:params_already_defined:b") { param("b", type = "integer") }
                    body { -> Rt_UnitValue }
                }
                function("result_already_defined", result = "integer") {
                    chkErr("LDE:function:result_already_defined:integer") { result(type = "integer") }
                    chkErr("LDE:function:result_already_defined:text") { result(type = "text") }
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkTypeMems(mod, "data",
            "function param_conflict(a: text, b: integer): integer",
            "function result_after_params(a: text): integer",
            "function result_before_params(a: text): integer",
            "function result_between_params(a: text): integer",
            "function result_already_defined(): integer",
        )
    }

    @Test fun testFunctionUsesSelfType() {
        val mod = makeModule("test") {
            type("integer") {}
            type("text") {
                function("upper_case") {
                    result(type = "text")
                    body { -> Rt_UnitValue }
                }
                function("index_of") {
                    result(type = "integer")
                    param("a", type = "text")
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkTypeMems(mod, "text", "function upper_case(): text", "function index_of(a: text): integer")
    }

    @Test fun testTypeParamOfFunction() {
        val mod = makeModule("test") {
            type("data") {
                function("f") {
                    generic("T")
                    result(type = "T")
                    param("a", type = "T")
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkTypeMems(mod, "data", "function <T> f(a: T): T")
    }

    @Test fun testTypeParamUsesTypeParamFunction() {
        val mod = makeModule("test") {
            type("data") {
                function("f") {
                    generic("T")
                    generic("U", subOf = "T")
                    result(type = "U")
                    param("a", type = "T")
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkTypeMems(mod, "data", "function <T,U:-T> f(a: T): U")
    }

    @Test fun testTypeParamConflictOuter() {
        val mod = makeModule("test") {
            type("data") {
                generic("T")
                constructor {
                    chkErr("LDE:fun:type_param_conflict_outer:T") { generic("T") }
                    generic("U")
                    chkErr("LDE:fun:type_param_conflict:U") { generic("U") }
                    generic("V")
                    body { -> Rt_UnitValue }
                }
                function("f", result = "anything") {
                    chkErr("LDE:fun:type_param_conflict_outer:T") { generic("T") }
                    generic("U")
                    chkErr("LDE:fun:type_param_conflict:U") { generic("U") }
                    generic("V")
                    body { -> Rt_UnitValue }
                }
                staticFunction("g", result = "anything") {
                    chkErr("LDE:fun:type_param_conflict_outer:T") { generic("T") }
                    generic("U")
                    chkErr("LDE:fun:type_param_conflict:U") { generic("U") }
                    generic("V")
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkTypeMems(mod, "data",
            "constructor <U,V> ()",
            "function <U,V> f(): anything",
            "static function <U,V> g(): anything",
        )
    }

    @Test fun testParamNullable() {
        chkModuleErr("LDE:function:param_not_nullable:integer") {
            type("integer")
            function("f", result = "anything") {
                param("a", type = "integer", nullable = true)
                body { -> Rt_UnitValue }
            }
        }
    }

    @Test fun testAliasConflict() {
        val mod = makeModule("test") {
            function("f", result = "anything") {
                chkErr("LDE:alias_conflict:f") { alias("f") }
                alias("g")
                chkErr("LDE:alias_conflict:g") { alias("g") }
                alias("h")
                body { -> Rt_UnitValue }
            }
        }
        chkDefs(mod,
            "function f(): anything",
            "alias g = f",
            "alias h = f",
        )
    }

    @Test fun testPure() {
        val bodyFalse = C_SysFunctionBody(pure = false, { _, _ -> Rt_UnitValue }, null)
        val bodyTrue = C_SysFunctionBody(pure = true, { _, _ -> Rt_UnitValue }, null)

        val mod = makeModule("test") {
            function("f", result = "anything") { body { -> Rt_UnitValue } }
            function("g", result = "anything", pure = true) { body { -> Rt_UnitValue } }
            function("h", result = "anything", pure = false) { body { -> Rt_UnitValue } }
            function("p", result = "anything") { bodyRaw(bodyTrue) }
            function("q", result = "anything") { bodyRaw(bodyFalse) }
            function("r", result = "anything", pure = true) { bodyRaw(bodyTrue) }
            function("s", result = "anything", pure = false) { bodyRaw(bodyFalse) }
            chkErr("LDE:body:pure_diff:true:false") {
                function("t", result = "anything", pure = true) { bodyRaw(bodyFalse) }
            }
            chkErr("LDE:body:pure_diff:false:true") {
                function("u", result = "anything", pure = false) { bodyRaw(bodyTrue) }
            }
        }

        chkDefs(mod,
            "function f(): anything",
            "pure function g(): anything",
            "function h(): anything",
            "pure function p(): anything",
            "function q(): anything",
            "pure function r(): anything",
            "function s(): anything",
        )
    }
}
