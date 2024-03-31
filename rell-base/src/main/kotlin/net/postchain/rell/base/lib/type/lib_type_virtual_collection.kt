/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl

object Lib_Type_VirtualCollection {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("virtual_collection", abstract = true, hidden = true, since = "0.9.0") {
            generic("T")
            parent("iterable<T>")

            function("to_text", "text", since = "0.9.0") {
                alias("str", since = "0.9.0")
                bodyRaw(Lib_Type_Any.ToText_NoDb)
            }

            function("empty", "boolean", pure = true, since = "0.9.0") {
                body { a ->
                    val col = a.asVirtualCollection()
                    Rt_BooleanValue.get(col.size() == 0)
                }
            }

            function("size", "integer", pure = true, since = "0.9.0") {
                body { a ->
                    val col = a.asVirtualCollection()
                    Rt_IntValue.get(col.size().toLong())
                }
            }
        }
    }
}
