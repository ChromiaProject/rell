package net.postchain.rell.codegen.typescript

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

internal class TypescriptCodeGeneratorTest {

    private val generator = CodeGenerator(TypescriptDocumentFactory())

    private fun generate(rellPath: String, vararg baseModule: String): Pair<List<DocumentSection>, Map<String, Document>> {
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
        val (sections, documents) = generate("/multi", "a", "f")
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
        val (sections, documents) = generate("/multi", "c.nested")
        assertThat(sections).hasSize(12 /* queries */ + 1 /* operations */ + 15 /* needed objects */)
        assertThat(documents).hasSize(6)
        println(documents)
        val c = documents["c/nested/c_nested.ts"]!!.format()
        assertThat(c).contains("import { AEnum }")
        assertThat(c).contains("import { RootEnum }")
    }

    @Test
    fun mapInput() {
        val (sections, documents) = generate("/map_input", "map_input")
        assertThat(sections).hasSize(10)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun setInput() {
        val (sections, documents) = generate("/set_input", "set_input")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun byteArray() {
        val (sections, documents) = generate("/byte_array", "byte_array")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun decimal() {
        val (sections, documents) = generate("/decimal", "decimal")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun big_integer() {
        val (sections, documents) = generate("/big_integer", "big_integer")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun rowId() {
        val (sections, documents) = generate("/rowid", "rowid")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun pubkey() {
        val (sections, documents) = generate("/pubkey", "pubkey")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun blockchainRid() {
        val (sections, documents) = generate("/blockchain_rid", "blockchain_rid")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun entity() {
        val (sections, documents) = generate("/entity", "entity")
        assertThat(sections).hasSize(4)
        assertThat(documents).hasSize(1)
    }

    @Test
    fun namespace() {
        val (sections, documents) = generate("/namespace", "ns_test")
        assertThat(sections).hasSize(3)
        assertThat(documents).hasSize(1)
    }
}
