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

internal class KotlinEntityTest {

    @Test
    fun format() {
        val a = RellCliUtils.compileApp(
            C_SourceDir.diskDir(File(javaClass.getResource("entity.rell")!!.toURI()).parentFile),
            C_CompilerModuleSelection(
                listOf(R_ModuleName.of("entity"))
            ),
            true,
            C_CompilerOptions.DEFAULT
        )

        val entity = a.moduleMap[R_ModuleName.of("entity")]!!.entities.values.first()
        val k = KotlinEntity(entity)
        val formatted = k.format()
        assert(formatted).all {
            contains("TestEntity")
            contains("@Name(\"name\") val name: String")
            contains("num: Int")
            contains("bType: Boolean")
        }
    }
}