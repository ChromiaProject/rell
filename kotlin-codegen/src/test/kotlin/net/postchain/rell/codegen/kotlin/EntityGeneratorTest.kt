package net.postchain.rell.codegen.kotlin

import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

internal class EntityGeneratorTest {

    @Test
    fun test() {
        val a = RellCliUtils.compileApp(
            C_SourceDir.diskDir(File(javaClass.getResource("entity.rell")!!.toURI()).parentFile),
            C_CompilerModuleSelection(
                listOf(R_ModuleName.of("entity"))
            ),
            true,
            C_CompilerOptions.DEFAULT
        )
        val tempDirectory = createTempDirectory("test").toFile()
        println(tempDirectory.absoluteFile)
        EntityGenerator().generate(a, tempDirectory)
        tempDirectory.listFiles().asList().forEach {
            println(it.name)
            println(it.readText())
        }
    }
}