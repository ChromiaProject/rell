package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.CachedRellCliEnv
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class KotlinCodeGeneratorTest {

    private val rellCliEnv = CachedRellCliEnv(RellCliEnv.DEFAULT, true, true)
    private val config = object : KotlinCodeGeneratorConfig { override fun packageName() = "com.example" }
    private val generator = CodeGenerator(KotlinDocumentFactory(config), config, rellCliEnv)

    private fun generateAndCompile(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, StringSerializable>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        val classes = Files.createTempDirectory("rell-codegen-classes")
        K2JVMCompiler().run {
            val args = K2JVMCompilerArguments().apply {
                freeArgs = documents.keys.map { "$target/$it" }
                destination = classes.toAbsolutePath().toString()
                classpath = System.getProperty("java.class.path")
                    .split(File.pathSeparator)
                    .filter {
                        File(it).exists() && File(it).canRead()
                    }.joinToString(":")
                noStdlib = true
                noReflect = true
                jvmTarget = "21"
            }
            val exitCode: ExitCode = exec(
                PrintingMessageCollector(
                    System.out,
                    MessageRenderer.WITHOUT_PATHS, false
                ),
                Services.EMPTY,
                args
            )
            assertThat(exitCode.code).isEqualTo(0)
        }
        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateAndCompile("/multi", "a", "f")
        assertThat(sections).hasSize(13 /* queries */ + 1 /* operations */ + 16 /* needed objects */)
        assertThat(documents).hasSize(7)
        val a = documents["a/a.kt"]!!.format()
        assertThat(a).contains("import com.example.RootStruct")
        assertThat(a).contains("import com.example.b.BStruct")
        assertThat(a).contains("import com.example.c.CEntity")
        assertThat(a).contains("import com.example.e.EEntity")
        assertThat(a).contains("import com.example.c.nested.NestedEnum")
    }

    @Test
    fun multiNestedModule() {
        val (sections, documents) = generateAndCompile("/multi", "c.nested")
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 15 /* needed objects */)
        assertThat(documents).hasSize(6)
        val c = documents["c/nested/c_nested.kt"]!!.format()
        assertThat(c).contains("import com.example.a.AEnum")
        assertThat(c).contains("import com.example.RootEnum")
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generateAndCompile("/map_input", "map_input")
        assertThat(sections).hasSize(11)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generateAndCompile("/set_input", "set_input")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun enumInput() {
        val (sections, documents) = generateAndCompile("/enum_input", "enum_input")
        assertThat(sections).hasSize(2)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generateAndCompile("/byte_array", "byte_array")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generateAndCompile("/decimal", "decimal")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun big_integer() {
        val (sections, documents) = generateAndCompile("/big_integer", "big_integer")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generateAndCompile("/rowid", "rowid")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun pubkey() {
        val (sections, documents) = generateAndCompile("/pubkey", "pubkey")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun blockchainRid() {
        val (sections, documents) = generateAndCompile("/blockchain_rid", "blockchain_rid")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun entity() {
        val (sections, documents) = generateAndCompile("/entity", "entity")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generateAndCompile("/namespace", "ns_test")
        assertThat(sections).hasSize(3)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun queriesWithMixedTupleReturnTypeAreSkipped() {
        val rellApp = SingleFileRellApp("mixed_tuple_queries")
        rellApp.compileApp()

        val sections = generator.createSections(rellApp.app)

        assertThat(sections).hasSize(2)
        assertThat(rellCliEnv.errorCache).containsAtLeast(
                "Skipping [mixed_tuple_queries:return_type_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_nullable_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_list_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_map_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)"
        )
    }

    @Test
    fun builtinStructs() {
        val (sections, documents) = generateAndCompile("/builtin", "structs")
        val a = documents["/root.kt"]!!.format()
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(2)
        assertThat(a).contains("class GtxOperation(")
        assertThat(a).contains("class GtxTransactionBody(")
        assertThat(a).contains("class GtxTransaction(")
    }
}
