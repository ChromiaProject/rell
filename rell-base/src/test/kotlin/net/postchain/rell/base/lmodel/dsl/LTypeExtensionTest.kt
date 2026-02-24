/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lib.type.Rt_UnitValue
import kotlin.test.Test
import kotlin.test.assertEquals

class LTypeExtensionTest: BaseLTest() {
    @Test fun testExtension() {
        val mod = makeModule("test") {
            extension("ext", type = "T") {
                generic("T", subOf = "any")
                function("get", result = "T") {
                    body { -> Rt_UnitValue }
                }
                function("set", result = "anything") {
                    param("a", type = "T")
                    body { -> Rt_UnitValue }
                }
                staticFunction("create", result = "T") {
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkDefs(mod, "extension ext<T:-any>: T")
        chkTypeMems(mod, "ext", "function get(): T", "function set(a: T): anything", "static function create(): T")
    }

    @Test fun testExtensionList() {
        val mod = makeModule("test") {
            type("data")
            extension("data_ext", type = "data") {
            }
            namespace("ns") {
                type("sub_data")
                extension("sub_data_ext", type = "ns.sub_data") {
                }
            }
        }

        val exp = "extension data_ext: data, extension ns.sub_data_ext: ns.sub_data"
        assertEquals(exp, mod.namespace.allTypeExtensions().joinToString { it.strCode() })
    }

    @Test fun testExtensionTypeParams() {
        val mod = makeModule("test") {
            extension("ext0", type = "any") {
                // No type params - OK.
            }
            extension("ext1", type = "A") {
                generic("A")
            }
            extension("ext2", type = "(A,B)") {
                generic("A")
                generic("B")
            }
            extension("ext3", type = "(A,B,C)") {
                generic("A")
                generic("B")
                generic("C")
            }
        }

        chkDefs(mod,
            "extension ext0: any",
            "extension ext1<A>: A",
            "extension ext2<A,B>: (A,B)",
            "extension ext3<A,B,C>: (A,B,C)",
        )
    }

    @Test fun testExtensionOfStruct() {
        val mod = makeModule("test") {
            struct("data") {}
            extension("data_ext", type = "data") {
            }
        }
        chkDefs(mod, "struct data", "extension data_ext: data")
    }

    @Test fun testExtensionOfStructImported() {
        val mod = makeModule("test") {
            struct("data") {}
        }
        val mod2 = makeModule("client") {
            imports(mod)
            extension("data_ext", type = "data") {
            }
        }
        chkDefs(mod2, "extension data_ext: data")
    }

    @Test fun testUseExtensionAsType() {
        chkModuleErr("LDE:type_not_found:ext") {
            extension("ext", type = "any") {
            }
            function("f", result = "ext") {
                body { -> Rt_UnitValue }
            }
        }
    }
}
