package net.postchain.rell.toolbox.linter

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.net.URI
import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.indexer.RellResourceFactory
import net.postchain.rell.toolbox.parser.AntlrRellParser
import net.postchain.rell.toolbox.formatter.FormatterOptions
import org.junit.jupiter.api.Test

class AutoFixerTest {
    private val autoFixer = AutoFixer()
    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val resourceFactory =
        RellResourceFactory(javaClass.getResource("/linter/auto-fixer/")!!.toURI(), AntlrRellParser(), ChromiaModelProvider(null))

    @Test
    fun `should fix all formatting issues`() {
        checkAutofix("formatting.rell", LinterOptions(enabled = true, ruleFormatter = true))
    }

    @Test
    fun `should fix all quote issues`() {
        checkAutofix("quotes.rell", LinterOptions(enabled = true, ruleQuoteFormat = Quote.DOUBLE))
    }

    @Test
    fun `should fix all auto-fixable issues`() {
        checkAutofix("all.rell", LinterOptions(enabled = true, ruleFormatter = true, ruleQuoteFormat = Quote.DOUBLE))
    }

    @Test
    fun `should remove extra trailing new line`() {
        checkAutofix(
            "end_of_line.rell",
            LinterOptions(enabled = true, ruleFormatter = true, ruleQuoteFormat = Quote.DOUBLE)
        )
    }

    @Test
    fun `should delete newline and indent`() {
        checkAutofix("delete.rell", LinterOptions(enabled = true, ruleFormatter = true, ruleQuoteFormat = Quote.DOUBLE))
    }

    private fun checkAutofix(fileName: String, linterOptions: LinterOptions) {
        val fixedContent = autofix(fileName, linterOptions)
        val expectedContent = getExpectedContent(fileName)
        assertThat(fixedContent).isEqualTo(expectedContent)
    }

    private fun getExpectedContent(fileName: String): String {
        return getFileContent(fileName.replace(".rell", "_fixed.rell"))
    }

    private fun autofix(fileName: String, linterOptions: LinterOptions): String {
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        val fileUri = getFileUri(fileName)
        val fileContent = getFileContent(fileName)
        val resource = resourceFactory.buildRellResource(fileUri, fileMap)

        resource.formatterIssues = formattingStyleLinter.lint(linterOptions, FormatterOptions(), fileContent)
        resource.linterIssues = rellLinter.lint(linterOptions, resource)

        return autoFixer.fix(resource, fileContent)
    }

    private fun getFileUri(fileName: String): URI {
        return javaClass.getResource("/linter/auto-fixer/$fileName")!!.toURI()
    }

    private fun getFileContent(fileName: String): String {
        return javaClass.getResource("/linter/auto-fixer/$fileName")!!.readText()
    }
}