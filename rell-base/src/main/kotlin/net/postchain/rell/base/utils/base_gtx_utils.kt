/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.R_App

object RellGtxConfigConstants {
    const val LANG_VERSION_KEY = "version"
    const val COMPILER_VERSION_KEY = "compilerVersion"
    const val SOURCES_KEY = "sources"
    const val FILES_KEY = "files"
}

class RellGtxModuleApp(val app: R_App, val compilerOptions: C_CompilerOptions)
