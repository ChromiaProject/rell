package net.postchain.rell.codegen

import net.postchain.rell.model.R_App
import net.postchain.rell.model.R_Module
import net.postchain.rell.model.R_ModuleName
import java.io.File
import kotlin.test.assertNotNull

open class SingleFileRellApp(private val fileName: String) {
    lateinit var app: R_App
    lateinit var testModule: R_Module

    fun compileApp() {
        app = compile(File(this::class.java.getResource("/$fileName.rell")!!.toURI()).parentFile, fileName)
        testModule = assertNotNull(app.moduleMap[R_ModuleName.of(fileName)])
    }
}
