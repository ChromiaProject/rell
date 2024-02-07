/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lib.type.Rt_UnitValue
import org.junit.Test

class LTypeNameConflictTest: BaseLTest() {
    @Test fun testFunction() {
        val block = makeBlock { function("f", "anything") { body { -> Rt_UnitValue } } }
        val defs = arrayOf("function f(): anything")
        chkNameConflictOK(defs, block, "function f(a: anything): anything") {
            function("f", "anything") { param("a", "anything"); body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "f") {
            function("f", makeTypeFun())
        }
        chkNameConflictOK(defs, block, "static function f(): anything") {
            staticFunction("f", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "constant f: integer = int[123]") {
            constant("f", 123)
        }
        chkNameConflictErr(defs, block, "f") {
            property("f", "anything") { value { Rt_UnitValue } }
        }
    }

    @Test fun testStaticFunction() {
        val block = makeBlock { staticFunction("f", "anything") { body { -> Rt_UnitValue } } }
        val defs = arrayOf("static function f(): anything")
        chkNameConflictOK(defs, block, "static function f(a: anything): anything") {
            staticFunction("f", "anything") { param("a", "anything"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "function f(): anything") {
            function("f", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "special function f(...)") {
            function("f", makeTypeFun())
        }
        chkNameConflictErr(defs, block, "f") {
            constant("f", 123)
        }
        chkNameConflictOK(defs, block, "property f: anything") {
            property("f", "anything") { value { Rt_UnitValue } }
        }
    }

    @Test fun testSpecialFunction() {
        val block = makeBlock { function("f", makeTypeFun()) }
        val defs = arrayOf("special function f(...)")
        chkNameConflictErr(defs, block, "f") {
            function("f", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "f") {
            function("f", makeTypeFun())
        }
        chkNameConflictOK(defs, block, "static function f(): anything") {
            staticFunction("f", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "constant f: integer = int[123]") {
            constant("f", 123)
        }
        chkNameConflictErr(defs, block, "f") {
            property("f", "anything") { value { Rt_UnitValue } }
        }
    }

    @Test fun testConstant() {
        val block = makeBlock { constant("c", 123) }
        val defs = arrayOf("constant c: integer = int[123]")
        chkNameConflictOK(defs, block, "function c(): anything") {
            function("c", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "special function c(...)") {
            function("c", makeTypeFun())
        }
        chkNameConflictErr(defs, block, "c") {
            staticFunction("c", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "c") {
            constant("c", 123)
        }
        chkNameConflictOK(defs, block, "property c: anything") {
            property("c", "anything") { value { Rt_UnitValue } }
        }
    }

    @Test fun testProperty() {
        val block = makeBlock { property("p", "anything") { value { Rt_UnitValue } } }
        val defs = arrayOf("property p: anything")
        chkNameConflictErr(defs, block, "p") {
            function("p", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "p") {
            function("p", makeTypeFun())
        }
        chkNameConflictOK(defs, block, "static function p(): anything") {
            staticFunction("p", "anything") { body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "constant p: integer = int[123]") {
            constant("p", 123)
        }
        chkNameConflictErr(defs, block, "p") {
            property("p", "anything") { value { Rt_UnitValue } }
        }
    }

    @Test fun testAliasFunction() {
        val (defs, block) = initAlias()
        chkNameConflictOK(defs, block, "function x(): anything", "alias c = x") {
            function("x", "anything") { alias("c"); body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "p") {
            function("x", "anything") { alias("p"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "function x(a: anything): anything", "alias f = x") {
            function("x", "anything") { alias("f"); param("a", "anything"); body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "g") {
            function("x", "anything") { alias("g"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "function x(): anything", "alias h = x") {
            function("x", "anything") { alias("h"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "function x(): anything", "alias i = x") {
            function("x", "anything") { alias("i"); body { -> Rt_UnitValue } }
        }
    }

    @Test fun testAliasStaticFunction() {
        val (defs, block) = initAlias()
        chkNameConflictErr(defs, block, "c") {
            staticFunction("x", "anything") { alias("c"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "static function x(): anything", "alias p = x") {
            staticFunction("x", "anything") { alias("p"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "static function x(): anything", "alias f = x") {
            staticFunction("x", "anything") { alias("f"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "static function x(): anything", "alias g = x") {
            staticFunction("x", "anything") { alias("g"); body { -> Rt_UnitValue } }
        }
        chkNameConflictOK(defs, block, "static function x(a: anything): anything", "alias h = x") {
            staticFunction("x", "anything") { alias("h"); param("a", "anything"); body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "i") {
            staticFunction("x", "anything") { alias("i"); param("a", "anything"); body { -> Rt_UnitValue } }
        }
    }

    private fun initAlias(): Pair<Array<String>, Ld_TypeDefDsl.() -> Unit> {
        val block = makeBlock {
            constant("c", 123)
            property("p", "anything") { value { Rt_UnitValue } }
            function("f", "anything") { body { -> Rt_UnitValue } }
            function("g", makeTypeFun())
            staticFunction("h", "anything") { param("a", "anything"); body { -> Rt_UnitValue } }
            staticFunction("i", makeNsFun())
        }

        val defs = arrayOf(
            "constant c: integer = int[123]",
            "property p: anything",
            "function f(): anything",
            "special function g(...)",
            "static function h(a: anything): anything",
            "static special function i(...)",
        )

        return defs to block
    }

    private fun chkNameConflictOK(
        defs: Array<String>,
        block1: Ld_TypeDefDsl.() -> Unit,
        vararg moreDefs: String,
        block2: Ld_TypeDefDsl.() -> Unit,
    ) {
        val mod = makeModule("test") {
            type("integer")
            type("data") {
                block1(this)
                block2(this)
            }
        }
        chkTypeMems(mod, "data", *defs, *moreDefs)
    }

    private fun chkNameConflictErr(
        defs: Array<String>,
        block1: Ld_TypeDefDsl.() -> Unit,
        name: String,
        block2: Ld_TypeDefDsl.() -> Unit,
    ) {
        val mod = makeModule("test") {
            type("integer")
            type("data") {
                block1(this)
                chkErr("LDE:name_conflict:$name") { block2(this) }
            }
        }
        chkTypeMems(mod, "data", *defs)
    }

    private fun makeBlock(block: Ld_TypeDefDsl.() -> Unit): Ld_TypeDefDsl.() -> Unit = block
}
