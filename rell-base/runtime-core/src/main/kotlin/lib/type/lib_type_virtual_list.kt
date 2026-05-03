/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_VirtualListType
import net.postchain.rell.base.runtime.asInteger
import net.postchain.rell.base.runtime.asVirtualList
import net.postchain.rell.base.utils.doc.DocCode

object Lib_Type_VirtualList {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("virtual_list", hidden = true, since = "0.9.0") {
            generic("T")
            generic("T2")
            parent("virtual_collection<T2>")

            rTypeMeta(R_VirtualListType.META)

            docCode { t, _ ->
                DocCode.builder()
                    .keyword("virtual").raw("<")
                    .link("list").raw("<").append(t).raw(">")
                    .raw(">")
                    .build()
            }

            function("get", result = "T2", pure = true, since = "0.9.0") {
                param("index", type = "integer")
                body { a, b ->
                    val list = a.asVirtualList()
                    val index = b.asInteger()
                    val res = list.get(index)
                    res
                }
            }

            function("to_full", result = "list<T>", since = "0.9.0") {
                bodyRaw(Lib_Type_Virtual.ToFull)
            }
        }
    }
}
