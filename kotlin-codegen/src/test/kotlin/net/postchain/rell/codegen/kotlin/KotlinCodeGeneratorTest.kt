package net.postchain.rell.codegen.kotlin

import assertk.assertions.contains
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class CodeGeneratorTest {

    val generator = CodeGenerator(KotlinDocumentFactory("com.example"))
    lateinit var sections: List<DocumentSection>

    @BeforeEach
    fun setup() {
        sections = generator.createSections(
            File(this::class.java.getResource("multi/a/module.rell")!!.toURI()).parentFile.parentFile,
            "a",
        )
    }

    @Test
    fun sections() {
        assertk.assert(sections).hasSize( 14 ) // 5 queries and 9 needed
    }

    @Test
    fun documents() {
        val documents = generator.constructDocuments(sections, true)
        assertk.assert(documents).hasSize(4)
        assertk.assert(documents[0].document.format()).contains("import com.example.b.BStruct")
        assertk.assert(documents[0].document.format()).contains("import com.example.c.CEntity")
        assertk.assert(documents[0].document.format()).contains("import com.example.e.EEntity")
    }

}
