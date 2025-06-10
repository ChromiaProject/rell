package net.postchain.rell.codegen.python

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

internal class PythonDocGeneratorTest {

    companion object : SingleFileRellApp("docs") {
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun entity() {
        val entity = assertNotNull(testModule.entities["doc_entity"])
        val formatted = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).contains("""
            ${"\"\"\""}
            Some entity docs
            
            Author: some_author
            See: something else
            Since: 1.0.0
            ${"\"\"\""}
         """.trimIndent())
    }

    @Test
    @Ignore("we always generate to_dict() comments for entities")
    fun entityWithoutDoc() {
        println(java.io.File(".").absoluteFile.toURI().path)
        val entity = assertNotNull(testModule.entities["not_doc_entity"])
        val formatted = PythonEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).doesNotContain("\"\"\"")
    }

    @Test
    fun enums() {
        val enum = assertNotNull(testModule.enums["doc_enum"])
        val formatted = PythonEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).contains("""
            ${"\"\"\""}
            Some enum docs
            
            Author: some_author
            See: something else
            Since: 1.0.0
            ${"\"\"\""}
        """.trimIndent())
    }

    @Test
    fun enumsWithoutDoc() {
        val enum = assertNotNull(testModule.enums["not_doc_enum"])
        val formatted = PythonEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).doesNotContain("\"\"\"")
    }

    @Test
    fun operation() {
        val op = assertNotNull(testModule.operations["doc_operation"])
        val formatted = PythonOperation(op).format()
        assertThat(formatted).contains("""
        |${"\"\"\""}
        |Some operation docs
        |See: something else
        |Since: 1.0.0
        |
        |Args:
        |	int_param (int): Some integer
        |
        |Raises:
        |	SomethingScary: boo
        |${"\"\"\""}
        """.trimMargin())
    }

    @Test
    fun operationWithoutDoc() {
        val op = assertNotNull(testModule.operations["not_doc_operation"])
        val formatted = PythonOperation(op).format()
        assertThat(formatted).doesNotContain("\"\"\"")
    }

    @Test
    fun operationMissingDocTags() {
        val op = assertNotNull(testModule.operations["missing_tags_doc_operation"])
        val formatted = PythonOperation(op).format()
        assertThat(formatted).contains("""
            |${"\"\"\""}
            |Missing tags
            |${"\"\"\""}
        """.trimMargin())
    }

    @Test
    fun query() {
        val query = assertNotNull(testModule.queries["doc_query"])
        val formatted = PythonQuery(query).format()
        assertThat(formatted).contains("""
        |${"\"\"\""}
        |Some query docs
        |another line
        |See: something else
        |Since: 1.0.0
        |
        |Args:
        |	t (Optional[str]): Some text
        |
        |Returns:
        |	QueryObject: Some integer
        |
        |Raises:
        |	SomethingScary: boo
        |${"\"\"\""}
        """.trimMargin())
    }

    @Test
    fun queryWithoutDoc() {
        val query = assertNotNull(testModule.queries["not_doc_query"])
        val formatted = PythonQuery(query).format()
        assertThat(formatted).doesNotContain("\"\"\"")
    }

    @Test
    fun queryWithMissingDocTags() {
        val query = assertNotNull(testModule.queries["missing_tags_doc_query"])
        val formatted = PythonQuery(query).format()
        assertThat(formatted).contains("""
             |${"\"\"\""}
             |Some description without param or return
             |${"\"\"\""}
        """.trimMargin())
    }

    @Test
    fun struct() {
        val struct = assertNotNull(testModule.structs["doc_struct"])
        val formatted = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).contains("""
            ${"\"\"\""}
            Some struct docs
            
            Author: some_author
            See: something else
            Since: 1.0.0
            ${"\"\"\""}
        """.trimIndent())
    }

    @Test
    @Ignore("we always generate to_dict() comments for structs")
    fun structWithoutDoc() {
        val struct = assertNotNull(testModule.structs["not_doc_struct"])
        val formatted = PythonStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).doesNotContain("\"\"\"")
    }

    @Test
    fun containingOpeningCommentMarker() {
        val query = assertNotNull(testModule.queries["weird_comment"])
        val formatted = PythonQuery(query).format()
        assertThat(formatted).contains("""
            ${"\"\"\""}
            Weird first line
            /* @return weird number
            ${"\"\"\""}
        """.trimIndent())
    }
}