/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.type.Rt_UnitValue
import org.junit.Test
import kotlin.test.assertEquals

// TODO more alias tests:
// same namespace/nested namespace/outer namespace
// same namespace, but different occurrence
// overloaded functions
// all kinds of targets, including aliases
// recursion

class LNamespaceTest: BaseLTest() {
    @Test fun testNamespace() {
        val mod = makeModule("test") {
            namespace("foo") {
                type("data") {}
            }
        }
        chkDefs(mod, "namespace foo", "type foo.data")
    }

    @Test fun testType() {
        val mod = makeModule("test") {
            type("data") {}
        }
        chkDefs(mod, "type data")
    }

    @Test fun testFunction() {
        val mod = makeModule("test") {
            function("f", result = "anything") {
                body { -> Rt_UnitValue }
            }
        }
        chkDefs(mod, "function f(): anything")
    }

    @Test fun testNested() {
        val mod = makeModule("test") {
            namespace("foo") {
                namespace("bar") {
                    type("parent", abstract = true) {}
                }
            }
            type("child") {
                parent("foo.bar.parent")
            }
        }

        val parentDef = mod.getTypeDef("foo.bar.parent")
        val childDef = mod.getTypeDef("child")
        assertEquals("foo.bar.parent", parentDef.getMType().strCode())
        assertEquals("child", childDef.getMType().strCode())
        assertEquals("foo.bar.parent", childDef.getMType().getParentType()?.strCode())
    }

    @Test fun testNestedShort() {
        val mod = makeModule("test") {
            namespace("foo.bar") {
                type("data") {}
            }
            namespace("a.b.c") {
                type("tada") {}
            }
        }

        chkDefs(mod,
            "namespace foo",
            "namespace foo.bar",
            "type foo.bar.data",
            "namespace a",
            "namespace a.b",
            "namespace a.b.c",
            "type a.b.c.tada",
        )
    }

    @Test fun testMergingSimple() {
        val mod = makeModule("test") {
            namespace("foo") {
                type("integer") {}
            }
            namespace("bar") {
                type("boolean") {}
            }
            namespace("foo") {
                type("decimal") {}
            }
        }

        chkDefs(mod,
            "namespace foo",
            "type foo.integer",
            "type foo.decimal",
            "namespace bar",
            "type bar.boolean",
        )
    }

    @Test fun testMergingComplex() {
        val mod = makeModule("test") {
            namespace("a") {
                type("data1") {}
                namespace("b") {
                    type("data2") {}
                }
                type("data3") {}
            }
            namespace("a.c") {
                type("data4") {}
            }
            namespace("a.b") {
                type("data5") {}
            }
            namespace("a") {
                type("data6") {}
                namespace("c") {
                    type("data7") {}
                }
                type("data8") {}
            }
        }

        chkDefs(mod,
            "namespace a",
            "namespace a.b",
            "type a.b.data2",
            "type a.b.data5",
            "namespace a.c",
            "type a.c.data4",
            "type a.c.data7",
            "type a.data1",
            "type a.data3",
            "type a.data6",
            "type a.data8",
        )
    }

    @Test fun testTypeAlias() {
        val mod = makeModule("test") {
            alias("data1", "data")
            alias("data2", "data", C_MessageType.WARNING)
            alias("data3", "data", C_MessageType.ERROR)
            type("data")
        }

        chkDefs(mod,
            "alias data1 = data",
            "@deprecated(WARNING) alias data2 = data",
            "@deprecated(ERROR) alias data3 = data",
            "type data",
        )
    }

    @Test fun testFunctionAlias() {
        val mod = makeModule("test") {
            function("f", result = "anything") {
                alias("g")
                alias("h", C_MessageType.WARNING)
                alias("i", C_MessageType.ERROR)
                body { -> Rt_UnitValue }
            }
        }

        chkDefs(mod,
            "function f(): anything",
            "alias g = f",
            "@deprecated(WARNING) alias h = f",
            "@deprecated(ERROR) alias i = f",
        )
    }

    @Test fun testStruct() {
        val mod = makeModule("test") {
            struct("value") {}
            struct("data") {
                attribute("x", type = "data")
                attribute("y", type = "data", mutable = true)
            }
        }
        chkDefs(mod, "struct value", "struct data")
    }

    @Test fun testAliasType() {
        val mod = makeModule("test") {
            type("data")
            alias("data1", "data")
            alias("data2", "data", C_Deprecated.warning("other_data"))
            alias("data3", "data", C_Deprecated.error("other_data"))
        }

        chkDefs(mod,
            "type data",
            "alias data1 = data",
            "@deprecated(WARNING) alias data2 = data",
            "@deprecated(ERROR) alias data3 = data",
        )
    }

    @Test fun testAliasFunction() {
        val mod = makeModule("test") {
            alias("g", "f")
            alias("h", "f", C_Deprecated.warning("foo"))
            function("f", result = "anything") {
                body { -> Rt_UnitValue }
            }
            alias("i", "f", C_Deprecated.error("foo"))
        }

        chkDefs(mod,
            "alias g = f",
            "@deprecated(WARNING) alias h = f",
            "function f(): anything",
            "@deprecated(ERROR) alias i = f",
        )
    }

    @Test fun testAliasFunctionSubNamespace() {
        val mod = makeModule("test") {
            alias(target = "sub.f")
            alias(target = "sub.f", name = "g")
            alias(target = "h", name = "i")
            namespace("sub") {
                function("f", result = "anything") {
                    body { -> Rt_UnitValue }
                }
            }
            function("h", result = "nothing") {
                body { -> Rt_UnitValue }
            }
        }

        chkDefs(mod,
            "namespace sub",
            "function sub.f(): anything",
            "alias f = sub.f",
            "alias g = sub.f",
            "alias i = h",
            "function h(): nothing",
        )
    }

    @Test fun testAliasRelativeName() {
        val mod = makeModule("test") {
            namespace("a") {
                namespace("b") {
                    namespace("c") {
                        type("data")
                    }
                    alias("c_data", "c.data")
                    alias("ac_data", "a.c.data")
                    alias("abc_data", "a.b.c.data")
                }
                namespace("c") {
                    type("data")
                }
                alias("c_data", "c.data")
                alias("ac_data", "a.c.data")
                alias("abc_data", "a.b.c.data")
            }
        }

        chkDefs(mod,
            "namespace a",
            "namespace a.b",
            "namespace a.b.c",
            "type a.b.c.data",
            "alias a.b.c_data = a.b.c.data",
            "alias a.b.ac_data = a.c.data",
            "alias a.b.abc_data = a.b.c.data",
            "namespace a.c",
            "type a.c.data",
            "alias a.c_data = a.c.data",
            "alias a.ac_data = a.c.data",
            "alias a.abc_data = a.b.c.data",
        )
    }

    @Test fun testAliasNotFound() {
        chkModuleErr("LDE:alias_target_not_found:[test:ref]:[foo]") {
            alias(name = "ref", target = "foo")
        }
    }

    @Test fun testInclude() {
        val c = Ld_NamespaceDsl.make {
            type("type_c")
            struct("struct_c") {}
            function("fc", result = "anything") { body { -> Rt_UnitValue } }
        }
        val b = Ld_NamespaceDsl.make {
            namespace("c") { include(c) }
            type("type_b")
            struct("struct_b") {}
            function("fb", result = "anything") { body { -> Rt_UnitValue } }
        }
        val a = Ld_NamespaceDsl.make {
            namespace("b") { include(b) }
            type("type_a")
            struct("struct_a") {}
            function("fa", result = "anything") { body { -> Rt_UnitValue } }
        }
        val mod = makeModule("test") {
            namespace("a") { include(a) }
            namespace("b") { include(b) }
            namespace("c") { include(c) }
            type("type_root")
            struct("struct_root") {}
            function("froot", result = "anything") { body { -> Rt_UnitValue } }
        }
        chkDefs(
            mod,
            "namespace a",
            "namespace a.b",
            "namespace a.b.c",
            "type a.b.c.type_c",
            "struct a.b.c.struct_c",
            "function a.b.c.fc(): anything",
            "type a.b.type_b",
            "struct a.b.struct_b",
            "function a.b.fb(): anything",
            "type a.type_a",
            "struct a.struct_a",
            "function a.fa(): anything",
            "namespace b",
            "namespace b.c",
            "type b.c.type_c",
            "struct b.c.struct_c",
            "function b.c.fc(): anything",
            "type b.type_b",
            "struct b.struct_b",
            "function b.fb(): anything",
            "namespace c",
            "type c.type_c",
            "struct c.struct_c",
            "function c.fc(): anything",
            "type type_root",
            "struct struct_root",
            "function froot(): anything",
        )
    }
}
