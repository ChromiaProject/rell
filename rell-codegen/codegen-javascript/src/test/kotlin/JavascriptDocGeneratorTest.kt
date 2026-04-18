/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.javascript

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class JavascriptDocGeneratorTest {

    companion object : SingleFileRellApp("docs") {
        @JvmStatic
        @BeforeAll
        fun compileTestApp() {
            compileApp()
        }
    }

    @Test
    fun operation() {
        val op = assertNotNull(testModule.operations["doc_operation"])
        val formatted = JavascriptOperation(op).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Some operation docs
            |* @see something else
            |* @since 1.0.0
            |* @param {number} intParam Some integer
            |* @throws SomethingScary boo
            |* @return {Operation}
            |*/
        """.trimMargin())
    }

    @Test
    fun operationWithoutDoc() {
        val op = assertNotNull(testModule.operations["not_doc_operation"])
        val formatted = JavascriptOperation(op).format()
        assertThat(formatted).contains("""
            |/**
            |* @param {number} intParam
            |* @return {Operation}
            |*/
        """.trimMargin())
    }

    @Test
    fun operationMissingDocTags() {
        val op = assertNotNull(testModule.operations["missing_tags_doc_operation"])
        val formatted = JavascriptOperation(op).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Missing tags
            |* @param {number} intParam
            |* @return {Operation}
            |*/
        """.trimMargin())
    }

    @Test
    fun query() {
        val query = assertNotNull(testModule.queries["doc_query"])
        val formatted = JavascriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Some query docs
            |* another line
            |* @see something else
            |* @since 1.0.0
            |* @param {string | null} t Some text
            |* @throws SomethingScary boo
            |* @return {QueryObject} Some integer
            |*/
        """.trimMargin())
    }

    @Test
    fun queryWithoutDoc() {
        val query = assertNotNull(testModule.queries["not_doc_query"])
        val formatted = JavascriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |* @param {string | null} t
            |* @return {QueryObject}
            |*/
        """.trimMargin())
    }

    @Test
    fun queryWithMissingDocTags() {
        val query = assertNotNull(testModule.queries["missing_tags_doc_query"])
        val formatted = JavascriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Some description without param or return
            |* @param {string | null} t
            |* @return {QueryObject}
            |*/
        """.trimMargin())
    }

    @Test
    fun containingOpeningCommentMarker() {
        val query = assertNotNull(testModule.queries["weird_comment"])
        val formatted = JavascriptQuery(query).format()
        assertThat(formatted).contains("""
            |/**
            |*
            |* Weird first line
            |* /* @return weird number
            |* @return {QueryObject}
            |*/
        """.trimMargin())
    }
}
