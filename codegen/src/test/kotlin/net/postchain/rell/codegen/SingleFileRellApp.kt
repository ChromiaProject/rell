package net.postchain.rell.codegen

import net.postchain.rell.model.R_App
import net.postchain.rell.model.R_Module
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.cli.RellCliApi
import net.postchain.rell.utils.cli.RellCliCompileConfig
import java.io.File
import kotlin.test.assertNotNull

open class SingleFileRellApp(private val fileName: String) {
    lateinit var app: R_App
    lateinit var testModule: R_Module

    fun compileApp() {
        val conf = RellCliCompileConfig.Builder()
                .moduleArgsMissingError(false)
                .mountConflictError(false)
                .build()
        val source = File(this::class.java.getResource("/$fileName.rell")!!.toURI()).parentFile
        app = RellCliApi.compileApp(conf, source, listOf(fileName))
        testModule = assertNotNull(app.moduleMap[R_ModuleName.of(fileName)])
    }
}
