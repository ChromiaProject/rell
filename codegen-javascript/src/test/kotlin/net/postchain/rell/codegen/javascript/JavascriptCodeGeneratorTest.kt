package net.postchain.rell.codegen.javascript

import assertk.Assert
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.support.expected
import assertk.assertions.support.show
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.nio.file.Files
import org.testcontainers.containers.Container.ExecResult
import org.testcontainers.utility.MountableFile
import kotlin.io.path.name

@Testcontainers
internal class JavascriptCodeGeneratorTest {

    private val generator = CodeGenerator(JavascriptDocumentFactory())

    companion object {
        @Container
        private val compilerContainer = GenericContainer("node:20")
                .withCommand("tail", "-f", "/dev/null")

        @BeforeAll
        @JvmStatic
        fun setup() {
            val res = compilerContainer.execInContainer("sh", "-c", "npm install -g eslint")
            assertThat(res).executeSuccessFully()
        }

        private fun Assert<ExecResult>.executeSuccessFully() = given { actual ->
            if (actual.exitCode == 0) return
            expected("to execute successfully but but exit code was ${show(actual.exitCode)}: ${actual.stderr.ifBlank { actual.stdout }}")
        }
    }

    private fun generateAndLint(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections, true)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)
        with(File(target.toFile(), ".eslintrc.json")) {
            writeText("""
                {
                    "env": {
                        "browser": true,
                        "es2021": true,
                        "node": true
                    },
                    "extends": "eslint:recommended",
                    "parserOptions": {
                        "ecmaVersion": "latest",
                        "sourceType": "module"
                    },
                    "rules": {
                    }
                }
            """.trimIndent())
        }

        val containerPath = "/usr/share/${target.name}"
        compilerContainer.copyFileToContainer(MountableFile.forHostPath(target), containerPath)
        val res = compilerContainer.execInContainer("sh", "-c", "eslint $containerPath")
        assertThat(res).executeSuccessFully()
        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateAndLint("/multi", "a", "f")
        assertThat(sections).hasSize(13 /* queries */ + 1 /* operations */ + 1 /* assertions */)
        assertThat(documents).hasSize(4)
        val a = documents["a/a.js"]!!.format()
        assertThat(a).contains("import { assertObject } from \"../root\";")
    }

    @Test
    fun multiNestedModule() {
        val (sections, documents) = generateAndLint("/multi", "c.nested")
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 1 /*assertion*/)
        assertThat(documents).hasSize(3)
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generateAndLint("/map_input", "map_input")
        assertThat(sections).hasSize(2 /* queries */ + 7 /* operations */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generateAndLint("/set_input", "set_input")
        assertThat(sections).hasSize(2 /* queries */ + 2 /* operations */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generateAndLint("/byte_array", "byte_array")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generateAndLint("/decimal", "decimal")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun big_integer() {
        val (sections, documents) = generateAndLint("/big_integer", "big_integer")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generateAndLint("/rowid", "rowid")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun pubkey() {
        val (sections, documents) = generateAndLint("/pubkey", "pubkey")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 2 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun blockchainRid() {
        val (sections, documents) = generateAndLint("/blockchain_rid", "blockchain_rid")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 2 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun entity() {
        val (sections, documents) = generateAndLint("/entity", "entity")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generateAndLint("/namespace", "ns_test")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun queriesWithMixedTupleReturnTypeAreSkipped() {
        val rellApp = SingleFileRellApp("mixed_tuple_queries")
        rellApp.compileApp()

        val skippedQueries = mutableListOf<String>()
        val sections = generator.createSections(rellApp.app, true, true) { skippedQuery, _ ->
            skippedQueries.add(skippedQuery)
        }

        assertThat(sections).hasSize(2)
        assertThat(skippedQueries).containsExactly("mixed_tuple_queries:return_type_unnamed_and_named_tuple")
    }
}
