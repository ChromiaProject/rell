package net.postchain.rell.codegen.typescript

import assertk.Assert
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.support.expected
import assertk.assertions.support.show
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.CachedRellCliEnv
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.Container.ExecResult
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Files
import kotlin.io.path.name

@Testcontainers
internal class TypescriptCodeGeneratorTest {

    private val rellCliEnv = CachedRellCliEnv(RellCliEnv.DEFAULT, true, true)
    private val generator = CodeGenerator(TypescriptDocumentFactory(), object: TypescriptCodeGeneratorConfig {}, rellCliEnv)

    companion object {
        @Container
        private val compilerContainer = GenericContainer("node:20")
                .withCommand("tail", "-f", "/dev/null")

        @BeforeAll
        @JvmStatic
        fun setup() {
            val res = compilerContainer.execInContainer("sh", "-c", "npm install -g typescript postchain-client@1.7.0")
            assertThat(res).executeSuccessFully()
        }

        private fun Assert<ExecResult>.executeSuccessFully() = given { actual ->
            if (actual.exitCode == 0) return
            expected("to execute successfully but but exit code was ${show(actual.exitCode)}: ${actual.stdout}")
        }
    }


    private fun generateAndCompile(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        with(File(target.toFile(), "tsconfig.json")) {
            writeText("""
                {
                  "compilerOptions": {
                    "noImplicitAny": false,
                    "noEmitOnError": true,
                    "removeComments": false,
                    "moduleResolution": "node",
                    "sourceMap": true,
                    "target": "es2015",
                    "outDir": "dist",
                    "paths": {
                        "*": ["/usr/local/lib/node_modules/*"]
                    }
                  },
                }
            """.trimIndent())
        }

        val containerPath = "/usr/share/${target.name}"
        compilerContainer.copyFileToContainer(MountableFile.forHostPath(target), containerPath)
        val res = compilerContainer.execInContainer("sh", "-c", "tsc -p $containerPath/tsconfig.json")
        assertThat(res).executeSuccessFully()
        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateAndCompile("/multi", "a", "f")
        assertThat(sections).hasSize(13 /* queries */ + 1 /* operations */ + 16 /* needed objects */)
        assertThat(documents).hasSize(7)
        val a = documents["a/a.ts"]!!.format()
        assertThat(a).contains("import { RootStruct } from \"../root\"")
        assertThat(a).contains("import { BStruct } from \"../b/b\"")
        assertThat(a).contains("import { CEntity } from \"../c/c\"")
        assertThat(a).contains("import { EEntity } from \"../e/e\"")
        assertThat(a).contains("import { CNestedEntity } from \"../c/c\"")
        assertThat(a).contains("import { NestedEnum } from \"../c/nested/c_nested\"")
    }

    @Test
    fun multiNestedModule() {
        val (sections, documents) = generateAndCompile("/multi", "c.nested")
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 15 /* needed objects */)
        assertThat(documents).hasSize(6)
        println(documents)
        val c = documents["c/nested/c_nested.ts"]!!.format()
        assertThat(c).contains("import { AEnum }")
        assertThat(c).contains("import { RootEnum }")
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generateAndCompile("/map_input", "map_input")
        assertThat(sections).hasSize(10)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generateAndCompile("/set_input", "set_input")
        assertThat(sections).hasSize(4)
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
        assertThat(rellCliEnv.errorCache).containsAll(
                "Skipping [mixed_tuple_queries:return_type_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_nullable_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_list_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)",
                "Skipping [mixed_tuple_queries:return_type_map_unnamed_and_named_tuple] Query return type contains unsupported mixed tuple type: (integer,foo:integer)"
        )
    }

    @Test
    fun builtinStructs() {
        val (_, documents) = generateAndCompile("/builtin", "structs")
        val a = documents["/root.ts"]!!.format()
        assertThat(a).contains("export type GtxOperation =")
        assertThat(a).contains("export type GtxTransactionBody =")
        assertThat(a).contains("export type GtxTransaction =")
    }
}
