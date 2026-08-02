/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.Test

class RellIssueTest {

    @Test
    fun fromCMessage() {
        val resource = TestUtils().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val rellIssue = resource.semanticErrors.map { RellIssue.fromCMessage(it, resource.tokenStream) }
        assertThat(rellIssue).isNotEmpty()
    }

    @Test
    fun fromSyntaxError() {
        val resource = TestUtils().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val rellIssue = resource.syntaxErrors.map { RellIssue.fromSyntaxError(it, resource.tokenStream) }
        assertThat(rellIssue).isNotEmpty()
    }

    @Test
    fun semanticErrorSpansWholeToken() {
        val resource = TestUtils().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val issue = RellIssue.fromCMessage(resource.semanticErrors.first(), resource.tokenStream)
        assertThat(issue.endLine).isEqualTo(issue.line)
        assertThat(issue.endColumn - issue.column).isEqualTo("anEntity".length)
    }

    @Test
    fun syntaxErrorSpansWholeToken() {
        val resource = TestUtils().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val issue = RellIssue.fromSyntaxError(resource.syntaxErrors.first(), resource.tokenStream)
        assertThat(issue.endLine).isEqualTo(issue.line)
        assertThat(issue.endColumn - issue.column).isEqualTo("create".length)
    }

    @Test
    fun cMessageIfFileNameIsNotCompliant() {
        val resource = TestUtils().createTestResource("naming-issue.rell", "rellDappWithErrors")
        assertThat(resource.semanticErrors).isNotEmpty()
        assertThat(
            resource.semanticErrors.first().text
        ).isEqualTo("Relative workspace path contains '-', cannot compile.")
    }
}
