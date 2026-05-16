/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.Rt_UnitValue

object Lib_Type_Unit {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("unit", rrType = RR_Type.Primitive(RR_PrimitiveKind.UNIT), since = "0.6.0") {
            """
                A type with no member values, much like `void` in other languages.

                Typically used as a return type for functions where no return value is required. Indeed, when a function
                is declared without a specified return type, the return type is implicitly `unit`. In other words, the
                function definition:

                ```rell
                function f(...) { ... }
                ```

                is equivalent to:

                ```rell
                function f(...): unit { ... }
                ```
            """.comment()
            constructor(pure = true, since = "0.6.0") {
                comment("Does nothing, and returns nothing.")
                constant(Rt_UnitValue)
            }
        }
    }
}
