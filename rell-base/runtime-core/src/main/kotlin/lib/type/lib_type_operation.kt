/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_OperationType

object Lib_Type_Operation {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("operation", abstract = true, hidden = true, since = "0.10.4") {
            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRTypeOrNull(mType)
                rType is R_OperationType
            }
        }
    }
}
