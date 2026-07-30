/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.compiler

import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeFilePath

class RellCompilerFilePath(
    internal val cPath: C_SourcePath,
    internal val idePath: IdeFilePath,
)
