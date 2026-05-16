/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_VirtualListType
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_VirtualListValue
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
                val self by self(Rt_VirtualListValue)
                val index by param(Rt_IntValue)
                body {
                    self.get(index.value)
                }
            }

            function("to_full", result = "list<T>", since = "0.9.0") {
                bodyRaw(Lib_Type_Virtual.ToFull)
            }
        }
    }
}
