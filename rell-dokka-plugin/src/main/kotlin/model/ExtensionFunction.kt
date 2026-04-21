/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.model

import net.postchain.rell.base.model.R_FunctionBase

data class ExtensionFunction(val targetAppLevelName: String, val fnBase: R_FunctionBase) {
    val defName = fnBase.defName
}
