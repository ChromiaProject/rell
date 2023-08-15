package net.postchain.rell.codegen.typescript

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isZero
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.postgresql.core.Oid.PATH
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@Testcontainers
internal class TypescriptCodeGeneratorTest {

    private val generator = CodeGenerator(TypescriptDocumentFactory())

    companion object {
        @Container
        private val compilerContainer = GenericContainer("node:20")
                .withCommand("tail", "-f", "/dev/null")

        @BeforeAll
        @JvmStatic
        fun setup() {
            val res = compilerContainer.execInContainer("sh", "-c", "npm install -g typescript postchain-client")
            assertThat(res.exitCode).isZero()
        }
    }


    private fun generate(rellPath: String, vararg baseModule: String, path: Path): Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections, true)
        val target = path
        DocumentSaver(target.toFile()).saveDocuments(documents)

        with(File(path.toFile(), "tsconfig.json")) {
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

        val containerPath = "/usr/share/${path.name}"
        compilerContainer.copyFileToContainer(MountableFile.forHostPath(path), containerPath)
        val res = compilerContainer.execInContainer("sh", "-c", "tsc -p $containerPath/tsconfig.json")
        assertThat(res.exitCode, displayActual = { "typescript compilation failed: ${res.stderr}" }).isZero()
        println(compilerContainer.execInContainer("sh", "-c", "ls $containerPath/dist"))
        return sections to documents
    }

    @Test
    fun multiModule(@TempDir dir: Path) {
        val (sections, documents) = generate("/multi", "a", "f", path = dir)
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
        val (sections, documents) = generate("/multi", "c.nested", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 15 /* needed objects */)
        assertThat(documents).hasSize(6)
        println(documents)
        val c = documents["c/nested/c_nested.ts"]!!.format()
        assertThat(c).contains("import { AEnum }")
        assertThat(c).contains("import { RootEnum }")
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generate("/map_input", "map_input", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(10)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generate("/set_input", "set_input", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generate("/byte_array", "byte_array", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generate("/decimal", "decimal", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun big_integer() {
        val (sections, documents) = generate("/big_integer", "big_integer", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generate("/rowid", "rowid", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun pubkey() {
        val (sections, documents) = generate("/pubkey", "pubkey", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun blockchainRid() {
        val (sections, documents) = generate("/blockchain_rid", "blockchain_rid", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun entity() {
        val (sections, documents) = generate("/entity", "entity", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generate("/namespace", "ns_test", path = Files.createTempDirectory("rell-codegen"))
        assertThat(sections).hasSize(3)
        assertThat(documents).hasSize(1)
    }
}
