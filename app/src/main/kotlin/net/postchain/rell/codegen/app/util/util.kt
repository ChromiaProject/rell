package net.postchain.rell.codegen.app.util

import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import java.io.File

fun compile(source: File, moduleName: String) = RellCliUtils.compileApp(
    C_SourceDir.diskDir(source),
    C_CompilerModuleSelection(
        listOf(R_ModuleName.of(moduleName))
    ),
    true,
    C_CompilerOptions.DEFAULT
)