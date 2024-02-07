/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.utils.doc.DocCode

object Lib_Type_VirtualList {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("virtual_list", hidden = true) {
            generic("T")
            generic("T2")
            parent("virtual_collection<T2>")

            rType { t, _ ->
                R_ListType(t).virtualType
            }

            docCode { t, _ ->
                DocCode.builder()
                    .keyword("virtual").raw("<")
                    .link("list").raw("<").append(t).raw(">")
                    .raw(">")
                    .build()
            }

            function("get", result = "T2", pure = true) {
                param("index", type = "integer")
                body { a, b ->
                    val list = a.asVirtualList()
                    val index = b.asInteger()
                    val res = list.get(index)
                    res
                }
            }

            function("to_full", result = "list<T>") {
                bodyRaw(Lib_Type_Virtual.ToFull)
            }
        }
    }
}
