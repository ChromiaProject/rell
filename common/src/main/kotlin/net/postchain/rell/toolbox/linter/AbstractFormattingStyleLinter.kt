package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.indexer.Resource

abstract class AbstractFormattingStyleLinter {
    abstract fun enhanceWithFormatterIssues(
        linterOptions: LinterOptions,
        formatterOptions: FormatterOptions?,
        resource: Resource,
        fileContent: String
    )

    abstract fun lint(
        linterOptions: LinterOptions,
        formatterOptions: FormatterOptions?,
        fileContent: String
    ): List<FormatterIssue>
}