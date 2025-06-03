/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import org.junit.Test

class LNamespaceNameConflictTest: BaseLTest() {
    @Test fun testNamespace() {
        val block = makeBlock { namespace("ns") {} }
        val defs = arrayOf("namespace ns")

        chkNameConflictOK(defs, block, "type ns.foo", "type ns.bar") {
            namespace("ns") { type("foo") }
            namespace("ns") { type("bar") }
        }
        chkNameConflictErr(defs, block, "ns") { type("ns") }
        chkNameConflictErr(defs, block, "ns") { struct("ns") {} }
        chkNameConflictErr(defs, block, "ns") { constant("ns", "anything", Rt_UnitValue) }
        chkNameConflictErr(defs, block, "ns") { property("ns", "anything") { value { _ -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, "ns") { property("ns", makeNsProp()) }
        chkNameConflictErr(defs, block, "ns") { function("ns", "anything") { body { -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, "ns") { function("ns", makeNsFun()) }
    }

    @Test fun testFunction() {
        val block = makeBlock { function("f", "anything") { body { -> Rt_UnitValue } } }
        val defs = arrayOf("function f(): anything")

        chkNameConflictErr(defs, block, "f") { namespace("f") {} }
        chkNameConflictErr(defs, block, "f") { type("f") }
        chkNameConflictErr(defs, block, "f") { struct("f") {} }
        chkNameConflictErr(defs, block, "f") { constant("f", "anything", Rt_UnitValue) }
        chkNameConflictErr(defs, block, "f") { property("f", "anything") { value { _ -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, "f") { property("f", makeNsProp()) }
        chkNameConflictOK(defs, block, "function f(a: anything): anything") {
            function("f", "anything") { param("a", "anything"); body { -> Rt_UnitValue } }
        }
        chkNameConflictErr(defs, block, "f") { function("f", makeNsFun()) }
    }

    @Test fun testOther() {
        chkNameConflictCommon("data", "type data") { type("data") }
        chkNameConflictCommon("data", "struct data") { struct("data") {} }
        chkNameConflictCommon("c", "constant c: integer = unit") { constant("c", "integer", Rt_UnitValue) }
        chkNameConflictCommon("p", "property p: integer") { property("p", "integer") { value { _ -> Rt_UnitValue } } }
        chkNameConflictCommon("p", "property p") { property("p", makeNsProp()) }
        chkNameConflictCommon("f", "special function f()") { function("f", makeNsFun()) }
    }

    private fun chkNameConflictCommon(name: String, def: String, block: Ld_NamespaceBodyDsl.() -> Unit) {
        val defs = arrayOf(def)
        chkNameConflictErr(defs, block, name) { namespace(name) {} }
        chkNameConflictErr(defs, block, name) { type(name) }
        chkNameConflictErr(defs, block, name) { struct(name) {} }
        chkNameConflictErr(defs, block, name) { constant(name, "anything", Rt_UnitValue) }
        chkNameConflictErr(defs, block, name) { property(name, "anything") { value { _ -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, name) { property(name, makeNsProp()) }
        chkNameConflictErr(defs, block, name) { function(name, "anything") { body { -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, name) { function(name, makeNsFun()) }
    }

    @Test fun testAlias() {
        val block = makeBlock {
            namespace("ns") { function("f", "anything") { body { -> Rt_UnitValue } } }
            alias(target = "ns.f", name = "l")
        }
        val defs0 = arrayOf("namespace ns", "function ns.f(): anything")
        val defs = defs0 + arrayOf("alias l = ns.f")

        chkNameConflictErr(defs, block, "l") { type("l") }
        chkNameConflictErr(defs, block, "l") { struct("l") {} }
        chkNameConflictErr(defs, block, "l") { constant("l", "anything", Rt_UnitValue) }
        chkNameConflictErr(defs, block, "l") { property("l", "anything") { value { _ -> Rt_UnitValue } } }
        chkNameConflictErr(defs, block, "l") { property("l", makeNsProp()) }
        chkNameConflictErr(defs, block, "l") { function("l", makeNsFun()) }

        chkNameConflictErr(defs, block, "l") {
            function("l", "anything") { param("a", "anything"); body { -> Rt_UnitValue } }
        }
    }

    @Test fun testAliasType() {
        chkAliasType("ns")
        chkAliasType("t")
        chkAliasType("s")
        chkAliasType("c")
        chkAliasType("p1")
        chkAliasType("p2")
        chkAliasType("f1")
        chkAliasType("f2")
    }

    private fun chkAliasType(alias: String) {
        val (defs, block) = initAlias()
        chkNameConflictErr(defs, block, alias) {
            alias(alias, "t")
        }
    }

    @Test fun testAliasFunction() {
        chkAliasFunction("ns")
        chkAliasFunction("t")
        chkAliasFunction("s")
        chkAliasFunction("c")
        chkAliasFunction("p1")
        chkAliasFunction("p2")
        chkAliasFunction("f2")

        val (defs, block) = initAlias()
        chkNameConflictOK(defs, block, "function x(a: anything): anything", "alias f1 = x") {
            function("x", "anything") {
                alias("f1")
                param("a", "anything")
                body { -> Rt_UnitValue }
            }
        }
    }

    private fun chkAliasFunction(alias: String) {
        val (defs, block) = initAlias()
        chkNameConflictErr(defs, block, alias) { function("x", "anything") { alias(alias); body { -> Rt_UnitValue } } }
    }

    private fun initAlias(): Pair<Array<String>, Ld_NamespaceBodyDsl.() -> Unit> {
        val block = makeBlock {
            namespace("ns") {}
            type("t")
            struct("s") {}
            constant("c", "integer", Rt_IntValue.ZERO)
            property("p1", "integer") { value { _ -> Rt_UnitValue } }
            property("p2", makeNsProp())
            function("f1", "integer") { body { -> Rt_UnitValue } }
            function("f2", makeNsFun())
        }

        val defs = arrayOf(
            "namespace ns",
            "type t",
            "struct s",
            "constant c: integer = int[0]",
            "property p1: integer",
            "property p2",
            "function f1(): integer",
            "special function f2()",
        )

        return defs to block
    }

    private fun chkNameConflictOK(
        defs: Array<String>,
        block1: Ld_NamespaceBodyDsl.() -> Unit,
        vararg moreDefs: String,
        block2: Ld_NamespaceBodyDsl.() -> Unit,
    ) {
        val mod = makeModule("test") {
            imports(Lib_Rell.MODULE.lModule)
            block1(this)
            block2(this)
        }
        chkDefs(mod, *defs, *moreDefs)
    }

    private fun chkNameConflictErr(
        defs: Array<String>,
        block1: Ld_NamespaceBodyDsl.() -> Unit,
        name: String,
        block2: Ld_NamespaceBodyDsl.() -> Unit,
    ) {
        val mod = makeModule("test") {
            imports(Lib_Rell.MODULE.lModule)
            block1(this)
            chkErr("LDE:name_conflict:$name") { block2(this) }
        }
        chkDefs(mod, *defs)
    }

    private fun makeBlock(block: Ld_NamespaceBodyDsl.() -> Unit): Ld_NamespaceBodyDsl.() -> Unit = block
}
