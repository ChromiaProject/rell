package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import net.postchain.rell.toolbox.core.TestUtil
import org.junit.jupiter.api.Test

class RellIssueTest {


    @Test
    fun fromCMessage() {
        val resource = TestUtil().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val rellIssue = resource.semanticErrors.map(RellIssue::fromCMessage)
        assertThat(rellIssue).isNotEmpty()
    }

    @Test
    fun fromSyntaxError() {
        val resource = TestUtil().createTestResource("single_syntax_and_semantic_error.rell", "rellDappWithErrors")
        val rellIssue = resource.syntaxErrors.map(RellIssue::fromSyntaxError)
        assertThat(rellIssue).isNotEmpty()
    }

    @Test
    fun cMessageIfFileNameIsNotCompliant() {
        val resource = TestUtil().createTestResource("naming-issue.rell", "rellDappWithErrors")
        assertThat(resource.semanticErrors).isNotEmpty()
        assertThat(resource.semanticErrors.first().text).isEqualTo("Relative workspace path contains '-', cannot compile.")
    }


}