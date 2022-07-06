package net.postchain.rell.codegen.kotlin

import assertk.assertions.hasSize
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.*
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.*
import net.postchain.rell.utils.RellCliUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

internal class CodeGeneratorTest {

    @Test
    fun sections() {
        val sections = CodeGenerator(KotlinDocumentFactory(), false).createSections(
            File(this::class.java.getResource("multi/a/module.rell")!!.toURI()).parentFile.parentFile,
            "a",
        )
        assertk.assert(sections).hasSize(2 + 1 + 2 + 1)
    }

}
