package net.postchain.rell.toolbox.lsp.server
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.net.URI

class UriUtilsTest {
    @Test
    fun `Should parse Windows path correctly`() {
        val fileUri = "file:///c%3A/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        val parsedUri = parseFileUri(fileUri)
        val expected = URI("file:/c:/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell")
        assertThat(parsedUri).isEqualTo(expected)
    }

    @Test
    fun `Should parse Unix path correctly`() {
        val fileUri = "file:///Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell"
        val parsedUri = parseFileUri(fileUri)
        val expected = URI("file:/Users/dummyUser/Documents/code/project-folder/rell/src/asd/attributes/boolean.rell")
        assertThat(parsedUri).isEqualTo(expected)
    }
}
