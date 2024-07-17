package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.linter.issues.LinterIssue

class RellLinter {
    fun enhanceWithLintIssues(config: LinterOptions, resource: Resource) {
        resource.linterIssues = lint(config, resource)
    }

    fun lint(config: LinterOptions, resource: Resource): List<LinterIssue> {
        if (!config.enabled) {
            return listOf()
        }
        val parseTree = resource.parseTree
        val linterContext = LinterContext()
        val linterVisitor = LinterVisitor(config, resource, linterContext)
        linterVisitor.visit(parseTree)

        return linterContext.getIssues()
    }
}

class LinterContext {
    private val issues = mutableListOf<LinterIssue>()
    fun addIssue(issue: LinterIssue) = issues.add(issue)
    fun getIssues(): List<LinterIssue> = issues
}
