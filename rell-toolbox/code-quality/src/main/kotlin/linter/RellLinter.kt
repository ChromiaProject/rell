/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.indexer.Resource

class RellLinter : AbstractRellLinter() {
    override fun enhanceWithLintIssues(config: LinterOptions, resource: Resource) {
        resource.linterIssues = lint(config, resource)
    }

    override fun lint(config: LinterOptions, resource: Resource): List<LinterIssue> {
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
