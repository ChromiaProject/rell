/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class KotlinDocGeneratorTest {

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
        val formatted = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).contains("""
            /**
            * Entity docs:doc_entity
            *
            * Rell entity is typically encoded as a GtvInteger. If used as struct<docs:doc_entity>, then GtvObjectMapper.toGtvArray() is used for encoding.
            *
            * Some entity docs
            * @author some_author
            * @see something else
            * @since 1.0.0
            */
         """.trimIndent())
    }

    @Test
    fun entityWithoutDoc() {
        println(java.io.File(".").absoluteFile.toURI().path)
        val entity = assertNotNull(testModule.entities["not_doc_entity"])
        val formatted = KotlinEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).contains("""
            /**
            * Entity docs:not_doc_entity
            *
            * Rell entity is typically encoded as a GtvInteger. If used as struct<docs:not_doc_entity>, then GtvObjectMapper.toGtvArray() is used for encoding.
            *
            */
         """.trimIndent())
    }

    @Test
    fun enums() {
        val enum = assertNotNull(testModule.enums["doc_enum"])
        val formatted = KotlinEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).contains("""
            /**
            * Enum docs:doc_enum
            *
            * Some enum docs
            * @author some_author
            * @see something else
            * @since 1.0.0
            */
        """.trimIndent())
    }

    @Test
    fun enumsWithoutDoc() {
        val enum = assertNotNull(testModule.enums["not_doc_enum"])
        val formatted = KotlinEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).contains("""
            /**
            * Enum docs:not_doc_enum
            *
            */
        """.trimIndent())
    }

    @Test
    fun operation() {
        val op = assertNotNull(testModule.operations["doc_operation"])
        val formatted = KotlinOperation(op).format()
        assertThat(formatted).contains("""
            /**
             * Operation docs:doc_operation
             *
             * Some operation docs
             * @see something else
             * @since 1.0.0
             * @param intParam Some integer
             * @throws SomethingScary boo
             */
        """.trimIndent())
    }

    @Test
    fun operationWithoutDoc() {
        val op = assertNotNull(testModule.operations["not_doc_operation"])
        val formatted = KotlinOperation(op).format()
        assertThat(formatted).contains("""
            /**
             * Operation docs:not_doc_operation
             *
             */
        """.trimIndent())
    }

    @Test
    fun operationMissingDocTags() {
        val op = assertNotNull(testModule.operations["missing_tags_doc_operation"])
        val formatted = KotlinOperation(op).format()
        assertThat(formatted).contains("""
            /**
             * Operation docs:missing_tags_doc_operation
             *
             * Missing tags
             */
        """.trimIndent())
    }

    @Test
    fun query() {
        val query = assertNotNull(testModule.queries["doc_query"])
        val formatted = KotlinQuery(query).format()
        assertThat(formatted).contains("""
            /**
             * Query docs:doc_query
             *
             * Some query docs
             * another line
             * @see something else
             * @since 1.0.0
             * @param t Some text
             * @throws SomethingScary boo
             * @return Some integer
             */
        """.trimIndent())
    }

    @Test
    fun queryWithoutDoc() {
        val query = assertNotNull(testModule.queries["not_doc_query"])
        val formatted = KotlinQuery(query).format()
        assertThat(formatted).contains("""
            /**
             * Query docs:not_doc_query
             *
             */
        """.trimIndent())
    }

    @Test
    fun queryWithMissingDocTags() {
        val query = assertNotNull(testModule.queries["missing_tags_doc_query"])
        val formatted = KotlinQuery(query).format()
        assertThat(formatted).contains("""
            /**
             * Query docs:missing_tags_doc_query
             *
             * Some description without param or return
             */
        """.trimIndent())
    }

    @Test
    fun struct() {
        val struct = assertNotNull(testModule.structs["doc_struct"])
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).contains("""
            /**
            * Struct docs:doc_struct
            *
            * Some struct docs
            * @author some_author
            * @see something else
            * @since 1.0.0
            */
        """.trimIndent())
    }

    @Test
    fun structWithoutDoc() {
        val struct = assertNotNull(testModule.structs["not_doc_struct"])
        val formatted = KotlinStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).contains("""
            /**
            * Struct docs:not_doc_struct
            *
            */
        """.trimIndent())
    }

    @Test
    fun containingOpeningCommentMarker() {
        val query = assertNotNull(testModule.queries["weird_comment"])
        val formatted = KotlinQuery(query).format()
        assertThat(formatted).contains("""
            /**
             * Query docs:weird_comment
             *
             * Weird first line
             *  @return weird number
             */
        """.trimIndent())
    }
}
