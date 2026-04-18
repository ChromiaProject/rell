/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.ImportFromNonModuleIssue
import net.postchain.rell.toolbox.parser.RellParser
import java.nio.file.Paths

class ImportFromNonModuleRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {
    private val fileName = Paths.get(resource.fileUri).fileName.toString()
    private var hasModuleHeader = false

    companion object {
        const val RULE_ID = "rule_import_from_non_module"
    }

    override val ruleId = RULE_ID

    override fun visitRuleX_RootParser(ctx: RellParser.RuleX_RootParserContext) {
        hasModuleHeader = ctx.ruleX_ModuleHeader() != null
    }

    override fun visitRuleX_ImportDef(ctx: RellParser.RuleX_ImportDefContext) {
        if (isDisabled(config.ruleImportFromNonModule) || hasIgnoreCommentOnTop(ctx.start) || hasSemanticErrors()) {
            return
        }

        if (fileName != "module.rell" && !hasModuleHeader) {
            report(ImportFromNonModuleIssue(ctx, ruleId, "Move import to 'module.rell'"))
        }
    }
}
