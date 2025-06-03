/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.lib

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.testutils.LibModuleTester
import org.junit.Test

class CLibTest: BaseCLibTest() {
    private val modTst = LibModuleTester(tst, Lib_Rell.MODULE)

    @Test fun testConstantType() {
        modTst.libModule {
            constant("X", "integer?") {
                value { Rt_IntValue.get(123) }
            }
            type("data") {
                modTst.setRTypeFactory(this)
                constant("Y", "integer?") {
                    value { Rt_IntValue.get(456) }
                }
            }
        }

        chk("_type_of(X)", "text[integer?]")
        chk("_type_of(data.Y)", "text[integer?]")
    }
}
