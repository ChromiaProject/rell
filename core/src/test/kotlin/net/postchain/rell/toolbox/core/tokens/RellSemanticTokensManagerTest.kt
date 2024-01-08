package net.postchain.rell.toolbox.core.tokens

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsExactly
import assertk.assertions.extracting
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.core.indexer.RellResourceFactory
import net.postchain.rell.toolbox.core.parser.AntlrRellParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectory

class RellSemanticTokensManagerTest {

    @Test
    fun `Correct semantic tokens returned`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
                
                function foo() {
                    return "foo";
                }
            """.trimIndent()
            )
        }
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

        val tokens = RellSemanticTokensManager().getSemanticTokens(resource)

        val mainFunctionSemanticToken = listOf(1, 9, 4, RellSymbolKind.FUNCTION.numId)
        val fooFunctionSemanticToken = listOf(5, 9, 3, RellSymbolKind.FUNCTION.numId)
        assertThat(tokens).extracting { listOf(it.line, it.col, it.len, it.tokenType) }.containsAll(
            mainFunctionSemanticToken,
            fooFunctionSemanticToken
        )
    }

    @Test
    fun `Correct relative semantic tokens returned`(@TempDir tempDir: File) {
        val srcDir = File(tempDir, "src").toPath().createDirectory().toFile()
        val rellFile = File(srcDir, "rell_file.rell").apply {
            writeText(
                """
                module;
                function main() {
                    return "main";
                }
                
                function foo() {
                    return "foo";
                }
            """.trimIndent()
            )
        }
        val resourceFactory = RellResourceFactory(tempDir.toURI(), AntlrRellParser())
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val resource = resourceFactory.buildRellResource(rellFile.toURI(), fileMap)

        val relativeTokens = RellSemanticTokensManager().getRelativeSemanticTokens(resource)

        val expectedRelativeTokens = arrayOf(1, 9, 4, 20, 0, 4, 9, 3, 20, 0)
        assertThat(relativeTokens).containsExactly(*expectedRelativeTokens)
    }
}