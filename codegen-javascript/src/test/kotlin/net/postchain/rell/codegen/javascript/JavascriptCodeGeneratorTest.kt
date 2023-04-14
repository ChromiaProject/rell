package net.postchain.rell.codegen.javascript

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentSaver
import net.postchain.rell.codegen.section.DocumentSection
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class JavascriptCodeGeneratorTest {
    private val generator = CodeGenerator(JavascriptDocumentFactory())

    private fun generateAndCompile(rellPath: String, vararg baseModule: String):
            Pair<List<DocumentSection>, Map<String, Document>> {
        val sections = generator.createSections(
                File(this::class.java.getResource(rellPath)!!.toURI()),
                baseModule.asList())
        val documents = generator.constructDocuments(sections, true)
        val target = Files.createTempDirectory("rell-codegen")
        DocumentSaver(target.toFile()).saveDocuments(documents)

        return sections to documents
    }

    @Test
    fun multiModule() {
        val (sections, documents) = generateAndCompile("/multi", "a", "f")
        assertThat(sections).hasSize(13 /* queries */ + 1 /* operations */ + 1 /* assertions */)
        assertThat(documents).hasSize(4)
        val a = documents["a/a.js"]!!.format()
        assertThat(a).contains("import { assertObject } from \"../root\";")
    }

    @Test
    fun multiNestedModule() {
        val (sections, documents) = generateAndCompile("/multi", "c.nested")
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 1 /*assertion*/)
        assertThat(documents).hasSize(3)
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generateAndCompile("/map_input", "map_input")
        assertThat(sections).hasSize(2 /* queries */ + 7 /* operations */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generateAndCompile("/set_input", "set_input")
        assertThat(sections).hasSize(2 /* queries */ + 2 /* operations */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generateAndCompile("/byte_array", "byte_array")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 4 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generateAndCompile("/decimal", "decimal")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 4 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generateAndCompile("/rowid", "rowid")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 4 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun pubkey() {
        val (sections, documents) = generateAndCompile("/pubkey", "pubkey")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun blockchainRid() {
        val (sections, documents) = generateAndCompile("/blockchain_rid", "blockchain_rid")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 3 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun entity() {
        val (sections, documents) = generateAndCompile("/entity", "entity")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 4 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generateAndCompile("/namespace", "ns_test")
        assertThat(sections).hasSize(2 /* queries */ + 1 /* operation */ + 1 /*assertions*/)
        assertThat(documents).hasSize(1 + 1 /* root */)
    }
}
