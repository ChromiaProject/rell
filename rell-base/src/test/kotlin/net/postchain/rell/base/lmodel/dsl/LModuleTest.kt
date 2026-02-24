/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.model.R_ModuleName
import kotlin.test.Test
import kotlin.test.assertEquals

class LModuleTest: BaseLTest() {
    @Test fun testBasic() {
        val mod = makeModule("foo.bar") {}
        assertEquals(R_ModuleName.of("foo.bar"), mod.moduleName)
    }

    @Test fun testImport() {
        val mod1 = makeModule("test.mod.foo") {
            type("parent", abstract = true) {}
        }
        val mod2 = makeModule("test.mod.bar") {
            imports(mod1)
            type("child") {
                parent("test.mod.foo::parent")
            }
        }
        val childDef = mod2.getTypeDef("child")
        assertEquals("parent", childDef.getMType().getParentType()?.strCode())
    }

    @Test fun testImportAliasNull() {
        val mod1 = makeModule("test.mod.foo") {
            type("parent", abstract = true) {}
        }
        val mod2 = makeModule("test.mod.bar") {
            imports(mod1)
            type("child") {
                parent("parent")
            }
        }
        val childDef = mod2.getTypeDef("child")
        assertEquals("parent", childDef.getMType().getParentType()?.strCode())
    }

    @Test fun testImportAliasConflict() {
        val mod1 = makeModule("test.mod.foo") {}
        val mod2 = makeModule("test.mod.bar") {}
        val mod3 = makeModule("test.mod.foo") {}

        chkModuleErr("LDE:import_module_conflict:test.mod.foo") {
            imports(mod1)
            imports(mod3)
        }
        chkModuleErr("LDE:import_module_conflict:test.mod.foo") {
            imports(mod3)
            imports(mod1)
        }
        makeModule("test") {
            imports(mod1)
            imports(mod2)
        }
        makeModule("test") {
            imports(mod2)
            imports(mod3)
        }
        makeModule("test") {
            imports(mod1)
            imports(mod1)
        }
        makeModule("test") {
            imports(mod2)
            imports(mod2)
        }
    }

    @Test fun testImportTypeAmbiguity() {
        val mod1 = makeModule("test.mod.foo") {
            type("parent", abstract = true) {}
        }
        chkModuleErr("LDE:type_ambiguous:parent:test,test.mod.foo") {
            imports(mod1)
            type("parent", abstract = true) {}
            type("child") {
                parent("parent")
            }
        }
    }
}
