/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.fail

class LDocCommentDefTest: BaseLTest() {
    @Test fun testNamespaceConstant() {
        chkNamespace("magic") {
            constant("magic", 123L, since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("magic") {
            constant("magic", BigInteger.valueOf(123), since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("magic") {
            constant("magic", BigDecimal.valueOf(123), since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("magic") {
            val v = Rt_IntValue.get(123)
            constant("magic", "integer", v, since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("magic") {
            constant("magic", "integer", since = it.since, comment = it.comment) {
                it.fn(this)
                value { Rt_IntValue.get(123) }
            }
        }
    }

    @Test fun testNamespaceAlias() {
        chkNamespace("ref") {
            type("cls")
            alias("ref", "cls", since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("ref") {
            type("cls")
            val dep = C_MessageType.WARNING
            alias("ref", "cls", deprecated = dep, since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testNamespaceProperty() {
        chkNamespace("prop") {
            property("prop", "integer", since = it.since, comment = it.comment) {
                it.fn(this)
                value { _ -> Rt_UnitValue }
            }
        }
        chkNamespace("prop") {
            property("prop", makeNsProp(), since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testNamespaceNamespace() {
        chkNamespace("ns") {
            namespace("ns", since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("a.b.c") {
            namespace("a.b.c", since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("a.b.c") {
            namespace("a") {
                namespace("b") {
                    namespace("c", since = it.since, comment = it.comment) { it.fn(this) }
                }
            }
        }
    }

    @Test fun testNamespaceNamespaceComplex() {
        val mod = makeModule("test") {
            namespace("a.b.c", since = "0.10.5", comment = "hello") {}
            namespace("d.e.f") {
                since("0.10.6")
                comment("bye")
            }
        }

        chkComment(mod, "a", null)
        chkComment(mod, "a.b", null)
        chkComment(mod, "a.b.c", "hello|since:0.10.5")
        chkComment(mod, "d", null)
        chkComment(mod, "d.e", null)
        chkComment(mod, "d.e.f", "bye|since:0.10.6")
    }

    @Test fun testNamespaceType() {
        chkNamespace("cls") {
            type("cls", since = it.since, comment = it.comment) { it.fn(this) }
        }
        //TODO support docs for type variables
//        chkNamespace("cls.T") {
//            type("cls") {
//                generic("T", since = it.since, comment = it.comment) { it.fn(this) }
//            }
//        }
    }

    @Test fun testNamespaceExtension() {
        chkNamespace("ext") {
            type("cls")
            extension("ext", "cls", since = it.since, comment = it.comment) { it.fn(this) }
        }
        //TODO support docs for type variables
//        chkNamespace("ext.T") {
//            type("cls")
//            extension("ext", "cls") {
//                generic("T", since = it.since, comment = it.comment) { it.fn(this) }
//            }
//        }
    }

    @Test fun testNamespaceStruct() {
        chkNamespace("rec") {
            struct("rec", since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkNamespace("rec.value") {
            struct("rec") {
                attribute("value", "integer", since = it.since, comment = it.comment) { it.fn(this) }
            }
        }
    }

    @Test fun testNamespaceFunction() {
        chkNamespace("f") {
            function("f", "any", since = it.since, comment = it.comment) {
                it.fn(this)
                body { -> Rt_UnitValue }
            }
        }
        chkNamespace("f") {
            function("f", makeNsFun(), since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testTypeConstant() {
        chkType("magic") {
            constant("magic", 123L, since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkType("magic") {
            constant("magic", BigInteger.valueOf(123), since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkType("magic") {
            constant("magic", BigDecimal.valueOf(123), since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkType("magic") {
            val v = Rt_IntValue.get(123)
            constant("magic", "integer", v, since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkType("magic") {
            constant("magic", "integer", since = it.since, comment = it.comment) {
                it.fn(this)
                value { Rt_IntValue.get(123) }
            }
        }
    }

    @Test fun testTypeConstructor() {
        chkType("!init") {
            constructor(since = it.since, comment = it.comment) {
                it.fn(this)
                body { -> Rt_UnitValue }
            }
        }
        chkType("!init") {
            constructor(makeNsFun(), since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testTypeProperty() {
        chkType("prop") {
            property("prop", "integer", since = it.since, comment = it.comment) {
                it.fn(this)
                value { _ -> Rt_UnitValue }
            }
        }
        chkType("prop") {
            property("prop", "integer", makeTypeProp(), since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testTypeFunction() {
        chkType("f") {
            function("f", "any", since = it.since, comment = it.comment) {
                it.fn(this)
                body { -> Rt_UnitValue }
            }
        }
        chkType("f") {
            function("f", makeTypeFun(), since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkType("f") {
            staticFunction("f", "any", since = it.since, comment = it.comment) {
                it.fn(this)
                body { -> Rt_UnitValue }
            }
        }
        chkType("f") {
            staticFunction("f", makeNsFun(), since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    @Test fun testFunction() {
        //TODO support docs for type variables
//        chkFunction("f.T") {
//            generic("T", "integer", since = it.since, comment = it.comment) { it.fn(this) }
//        }
        chkFunction("f.x") {
            param("x", "integer", since = it.since, comment = it.comment) { it.fn(this) }
        }
        chkFunction("ref") {
            alias("ref", since = it.since, comment = it.comment) { it.fn(this) }
        }
    }

    private fun chkType(name: String, block: Ld_TypeDefDsl.(TestParams) -> Unit) {
        chkNamespace("data.$name") { params ->
            type("data") {
                block(this, params)
            }
        }
    }

    private fun chkFunction(name: String, block: Ld_FunctionDsl.(TestParams) -> Unit) {
        chkNamespace(name) { params ->
            function("f", "any") {
                block(this, params)
                body { -> Rt_UnitValue }
            }
        }
        chkType(name) { params ->
            function("f", "any") {
                block(this, params)
                body { -> Rt_UnitValue }
            }
        }
        chkType(name) { params ->
            staticFunction("f", "any") {
                block(this, params)
                body { -> Rt_UnitValue }
            }
        }
    }

    private fun chkNamespace(name: String, block: Ld_NamespaceBodyDsl.(TestParams) -> Unit) {
        // No since and comment arguments.
        chkNamespace0(name, block, null, null, null) {}
        chkNamespace0(name, block, null, null, "|since:0.10.5") {
            since("0.10.5")
        }
        chkNamespace0(name, block, null, null, "comment 123") {
            comment("comment 123")
        }
        chkNamespace0(name, block, null, null, "comment 123|since:0.10.5") {
            since("0.10.5")
            comment("comment 123")
        }

        // Since argument.
        chkNamespace0(name, block, "0.10.5", null, "|since:0.10.5") {}
        chkNamespace0(name, block, "0.10.5", null, "|since:0.10.5") {
            chkErr("LDE:since:already_set:0.10.5") { since("0.10.10") }
        }
        chkNamespace0(name, block, "0.10.5", null, "comment 123|since:0.10.5") {
            comment("comment 123")
        }

        // Comment argument.
        chkNamespace0(name, block, null, "comment 123", "comment 123") {}
        chkNamespace0(name, block, null, "comment 123", "comment 123") {
            chkErr("LDE:comment:already_set") { comment("comment 456") }
        }
        chkNamespace0(name, block, null, "comment 123", "comment 123|since:0.10.5") {
            since("0.10.5")
        }

        // Invalid version.
        chkErr("LDE:version:invalid:foo") {
            chkNamespace0(name, block, "foo", null, null) { fail() }
        }
        chkErr("LDE:version:unknown:0.1.2") {
            chkNamespace0(name, block, "0.1.2", null, null) { fail() }
        }
        chkNamespace0(name, block, null, null, null) {
            chkErr("LDE:version:invalid:foo") { since("foo") }
            chkErr("LDE:version:unknown:0.1.2") { since("0.1.2") }
        }
    }

    private fun chkNamespace0(
        name: String,
        block: Ld_NamespaceBodyDsl.(TestParams) -> Unit,
        since: String?,
        comment: String?,
        exp: String?,
        innerBlock: Ld_MemberDsl.() -> Unit,
    ) {
        val params = TestParams(since, comment, innerBlock)
        chkComment(name, exp) {
            block(this, params)
        }
    }

    private class TestParams(val since: String?, val comment: String?, val fn: Ld_MemberDsl.() -> Unit)
}
