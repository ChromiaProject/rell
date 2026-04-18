/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_Module
import net.postchain.rell.base.model.R_ModuleName
import java.io.File
import kotlin.test.assertNotNull

open class SingleFileRellApp(private val fileName: String) {
    lateinit var app: R_App
    lateinit var testModule: R_Module

    fun compileApp() {
        val conf = RellApiCompile.Config.Builder()
                .moduleArgsMissingError(false)
                .mountConflictError(false)
                .docSymbolsEnabled(true)
                .build()
        val source = File(this::class.java.getResource("/$fileName.rell")!!.toURI()).parentFile
        app = RellApiCompile.compileApp(conf, source, listOf(fileName))
        testModule = assertNotNull(app.moduleMap[R_ModuleName.of(fileName)])
    }
}
