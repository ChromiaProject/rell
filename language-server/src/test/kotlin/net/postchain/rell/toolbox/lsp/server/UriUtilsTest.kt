package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class UriUtilsTest {
    @Test
    fun `Should parse Windows path correctly`() {
        val fileUri = "file:///c%3A/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        val parsedUri = parseFileUri(fileUri)
        val expected = "/c:/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        assertThat(parsedUri!!.path).isEqualTo(expected)
    }

    @Test
    fun `Should parse Unix path correctly`() {
        val fileUri = "file:///Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        val parsedUri = parseFileUri(fileUri)
        val expected = "/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        assertThat(parsedUri!!.path).isEqualTo(expected)
    }

    @Test
    fun `Should parse path with space correctly`() {
        val fileUri = "file:///Users/dummy%20user/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        val parsedUri = parseFileUri(fileUri)
        val expected = "/Users/dummy user/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        assertThat(parsedUri!!.path).isEqualTo(expected)
    }

    @Test
    fun `Returns null when parsing non file URI`() {
        val gitUri = "git:/path/to/ft3-lib/rell/src/lib/ft4/accounts/strategies/transfer/operations.rell?" +
            "{\"path\":\"/path/to/lib/ft3-lib/rell/src/lib/ft4/accounts/strategies/transfer/operations.rell" +
            "\",\"ref\":\"~\"}"
        val parsedUri = parseFileUri(gitUri)
        assertThat(parsedUri).isNull()
    }
}
