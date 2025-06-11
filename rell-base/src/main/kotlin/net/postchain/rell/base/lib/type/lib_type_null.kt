/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl

object Lib_Type_Null {
    val NAMESPACE = Ld_NamespaceDsl.make {
        // Not a real extension, used directly when calling a method on a "null" literal.
        type("null_ext", abstract = true, hidden = true, since = "0.10.6") {
            function("to_gtv", result = "gtv", pure = true, since = "0.10.6") {
                comment("""
                    Convert this `null` to a `gtv null`.

                    @see gtv <a href="../gtv/index.html"><code>gtv</code></a>
                """)
                Lib_Type_Gtv.makeToGtvBody(this, pretty = false)
            }
        }
    }
}
