package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class CodeGeneratorTest {

    private val generator = CodeGenerator(KotlinDocumentFactory("com.example"))

    fun generateAndCompile(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
            File(this::class.java.getResource(rellPath)!!.toURI()).parentFile.parentFile,
            *baseModule,
        )
        val documents = generator.constructDocuments(sections, true)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        val classes = Files.createTempDirectory("rell-codegen-classes")
        K2JVMCompiler().run {
            val args = K2JVMCompilerArguments().apply {
                freeArgs = documents.keys.map { "$target/$it" }
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
            assertThat(exitCode.code).isEqualTo(0)
        }
        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateAndCompile("multi/a/module.rell", "a", "f")
        assertThat(sections).hasSize( 9 /* queries */ + 1 /* operations */ + 13 /* needed objects */ )
        assertThat(documents).hasSize(6)
        val a = documents["a/a.kt"]!!.format()
        assertThat(a).contains("import com.example.RootStruct")
        assertThat(a).contains("import com.example.b.BStruct")
        assertThat(a).contains("import com.example.c.CEntity")
        assertThat(a).contains("import com.example.e.EEntity")
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generateAndCompile("map_input/module.rell", "map_input")
        assertThat(sections).hasSize(1)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generateAndCompile("byte_array/module.rell", "byte_array")
        assertThat(sections).hasSize(3)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generateAndCompile("decimal/module.rell", "decimal")
        assertThat(sections).hasSize(3)
        assertThat(documents).hasSize(1)
    }
}
