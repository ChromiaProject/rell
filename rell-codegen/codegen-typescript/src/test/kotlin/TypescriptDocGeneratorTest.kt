/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import net.postchain.rell.codegen.SingleFileRellApp
import net.postchain.rell.codegen.deps.CamelCaseClassName
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class TypescriptDocGeneratorTest {

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
        val formatted = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).contains("""
            /**
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
        val formatted = TypescriptEntity(CamelCaseClassName.fromRellDefinition(entity), entity).format()
        assertThat(formatted).doesNotContain("/**")
    }

    @Test
    fun enums() {
        val enum = assertNotNull(testModule.enums["doc_enum"])
        val formatted = TypescriptEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).contains("""
            /**
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
        val formatted = TypescriptEnumeration(CamelCaseClassName.fromRellDefinition(enum), enum).format()
        assertThat(formatted).doesNotContain("/**")
    }

    @Test
    fun operation() {
        val op = assertNotNull(testModule.operations["doc_operation"])
        val formatted = TypescriptOperation(op).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Some operation docs
            |* @see something else
            |* @since 1.0.0
            |* @param {number} intParam Some integer
            |* @throws SomethingScary boo
            |*/
        """.trimMargin())
    }

    @Test
    fun operationWithoutDoc() {
        val op = assertNotNull(testModule.operations["not_doc_operation"])
        val formatted = TypescriptOperation(op).format()
        assertThat(formatted).doesNotContain("/**")
    }

    @Test
    fun operationMissingDocTags() {
        val op = assertNotNull(testModule.operations["missing_tags_doc_operation"])
        val formatted = TypescriptOperation(op).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Missing tags
            |*/
        """.trimMargin())
    }

    @Test
    fun query() {
        val query = assertNotNull(testModule.queries["doc_query"])
        val formatted = TypescriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Some query docs
            |* another line
            |* @see something else
            |* @since 1.0.0
            |* @param {string | null} t Some text
            |* @throws SomethingScary boo
            |* @return {QueryObject<number>} Some integer
            |*/
        """.trimMargin())
    }

    @Test
    fun queryWithoutDoc() {
        val query = assertNotNull(testModule.queries["not_doc_query"])
        val formatted = TypescriptQuery(query).format()
        assertThat(formatted).doesNotContain("/**")
    }

    @Test
    fun queryWithMissingDocTags() {
        val query = assertNotNull(testModule.queries["missing_tags_doc_query"])
        val formatted = TypescriptQuery(query).format()
        assertThat(formatted).contains("""
             |/**
             |*
             |* Some description without param or return
             |*/
        """.trimMargin())
    }

    @Test
    fun struct() {
        val struct = assertNotNull(testModule.structs["doc_struct"])
        val formatted = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).contains("""
            /**
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
        val formatted = TypescriptStruct(CamelCaseClassName.fromRellDefinition(struct), struct).format()
        assertThat(formatted).doesNotContain("/**")
    }

    @Test
    fun containingOpeningCommentMarker() {
        val query = assertNotNull(testModule.queries["weird_comment"])
        val formatted = TypescriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Weird first line
            |* /* @return weird number
            |*/
        """.trimMargin())
    }
}
