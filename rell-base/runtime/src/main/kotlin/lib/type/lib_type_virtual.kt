/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_VirtualType
import net.postchain.rell.base.runtime.simple
import net.postchain.rell.base.utils.doc.DocCode

object Lib_Type_Virtual {
    val ToFull = C_SysFunctionBody.simple { a ->
        val virtual = a.asVirtual()
        val full = virtual.toFull()
        full
    }

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("virtual", hidden = true, since = "0.9.0") {
            generic("T")

            rTypeMeta(R_VirtualType.META)

            docCode { t ->
                DocCode.builder()
                    .keyword("virtual").raw("<").append(t).raw(">")
                    .build()
            }

            function("to_full", result = "T", since = "0.9.0") {
                bodyRaw(ToFull)
            }
        }
    }
}
