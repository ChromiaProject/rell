package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.indexer.Resource

abstract class AbstractRellLinter {
    abstract fun enhanceWithLintIssues(config: LinterOptions, resource: Resource)
    abstract fun lint(config: LinterOptions, resource: Resource): List<LinterIssue>
}
