/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import java.io.File
import kotlin.test.assertNotNull

open class SingleFileRellApp(private val fileName: String) {
    lateinit var app: R_App
    lateinit var testModule: R_Module

    fun compileApp() {
        val source = File(this::class.java.getResource("/$fileName.rell")!!.toURI()).parentFile
        val cSourceDir = C_SourceDir.diskDir(source)
        val modules = listOf(fileName).mapToImmList { ModuleName.of(it) }
        val options = C_CompilerOptions.DEFAULT.toBuilder()
            .ide(true)
            .ideDocSymbolsEnabled(true)
            .build()
        val modSel = C_CompilerModuleSelection(modules, emptyList<ModuleName>().toImmList())
        val cRes = C_Compiler.compile(cSourceDir, modSel, options)
        app = cRes.app!!
        testModule = assertNotNull(app.moduleMap[ModuleName.of(fileName)])
    }
}
