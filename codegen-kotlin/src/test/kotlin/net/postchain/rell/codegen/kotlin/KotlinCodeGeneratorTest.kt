package net.postchain.rell.codegen.kotlin

import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.document.DocumentFile
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors

internal class CodeGeneratorTest {

    val generator = CodeGenerator(KotlinDocumentFactory("com.example"))
    lateinit var sections: List<DocumentSection>
    lateinit var documents: List<DocumentFile>

    @BeforeEach
    fun setup() {
        sections = generator.createSections(
            File(this::class.java.getResource("multi/a/module.rell")!!.toURI()).parentFile.parentFile,
            "a", "f",
        )
        documents = generator.constructDocuments(sections, true)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        val classes = Files.createTempDirectory("rell-codegen-classes")
        K2JVMCompiler().run {
            val args = K2JVMCompilerArguments().apply {
                freeArgs = documents.stream().map(DocumentFile::path).map { "$target/$it" }.collect(Collectors.toList())
                destination = classes.toAbsolutePath().toString()
                classpath = System.getProperty("java.class.path")
                    .split(System.getProperty("path.separator"))
                    .filter {
                        File(it).exists() && File(it).canRead()
                    }.joinToString(":")
                noStdlib = true
                noReflect = true
                skipRuntimeVersionCheck = true
                jvmTarget = "11"
            }
            val exitCode: ExitCode = execImpl(
                PrintingMessageCollector(
                    System.out,
                    MessageRenderer.WITHOUT_PATHS, false),
                Services.EMPTY,
                args)
            assertk.assert(exitCode.code).isEqualTo(0)
        }
    }

    @Test
    fun sections() {
        assertk.assert(sections).hasSize( 23 ) // 9 queries, 1 operation and 14 needed
    }

    @Test
    fun documents() {
        assertk.assert(documents).hasSize(6)
        assertk.assert(documents[1].document.format()).contains("import com.example.RootStruct")
        assertk.assert(documents[1].document.format()).contains("import com.example.b.BStruct")
        assertk.assert(documents[1].document.format()).contains("import com.example.c.CEntity")
        assertk.assert(documents[1].document.format()).contains("import com.example.e.EEntity")
    }

}
