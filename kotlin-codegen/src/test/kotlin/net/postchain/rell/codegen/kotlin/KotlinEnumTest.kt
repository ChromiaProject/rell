package net.postchain.rell.codegen.kotlin

import assertk.all
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import org.junit.jupiter.api.Test

import java.io.File

import assertk.assert
import assertk.assertions.contains
import net.postchain.rell.model.R_Module
import org.junit.jupiter.api.BeforeAll
import kotlin.test.assertNotNull

internal class KotlinEnumTest {

    companion object {
        lateinit var testModule: R_Module
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            testModule = RellCliUtils.compileApp(
                C_SourceDir.diskDir(File(this::class.java.getResource("enumerations.rell")!!.toURI()).parentFile),
                C_CompilerModuleSelection(
                    listOf(R_ModuleName.of("enumerations"))
                ),
                true,
                C_CompilerOptions.DEFAULT
            ).let {
                assertNotNull(it.moduleMap[R_ModuleName.of("enumerations")])
            }
        }
    }

    @Test
    fun simpleEnumerations() {
        val struct = assertNotNull(testModule.enums["test"], "enum does not exist")
        val formatted = KotlinEnumeration(struct).format()
        assert(formatted).all {
            contains("A,")
            contains("BValue")
        }
    }


}