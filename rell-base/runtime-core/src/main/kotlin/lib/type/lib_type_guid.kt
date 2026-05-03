/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type

object Lib_Type_Guid {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("guid", rrType = RR_Type.Primitive(RR_PrimitiveKind.GUID), since = "0.6.0")
    }
}


