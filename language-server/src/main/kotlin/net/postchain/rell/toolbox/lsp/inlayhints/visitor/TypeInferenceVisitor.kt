package net.postchain.rell.toolbox.lsp.inlayhints.visitor

import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.createTypeInlayHint
import net.postchain.rell.toolbox.lsp.inlayhints.RellInlayHintsProvider.Companion.isInRange
import net.postchain.rell.toolbox.lsp.inlayhints.RellTypeProcessor
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position

class TypeInferenceVisitor(
    private val resource: Resource,
    private val range: org.eclipse.lsp4j.Range,
    private val hints: MutableList<InlayHint>
) : RellBaseVisitor<Unit>() {

    override fun visitRuleX_VarStmt(ctx: RellParser.RuleX_VarStmtContext) {
        val declarator = ctx.ruleX_VarDeclarator() ?: return
        val typeRef = declarator.ruleX_SimpleVarDeclarator()
            ?.ruleX_AttrHeader()
            ?.ruleX_NameTypeAttrHeader()
        val nameNode = declarator.ruleX_SimpleVarDeclarator()
            ?.ruleX_AttrHeader()
            ?.ruleX_AnonAttrHeader()
            ?.ruleX_QualifiedNameNode()
        processDeclaration(
            typeRef = typeRef,
            name = nameNode,
            getType = { getSemanticTypeForVariable(ctx) },
            getPosition = { getPositionAfterVariableName(declarator) }
        )
    }

    override fun visitRuleX_ConstantDef(ctx: RellParser.RuleX_ConstantDefContext?) {
        ctx ?: return
        if (ctx.ruleX_TypeRef() != null) return

        processDeclaration(
            typeRef = ctx.ruleX_TypeRef(),
            name = ctx.ruleX_Name()?.ruleX_NameNode(),
            getType = { getTypeForConstant(ctx) },
            getPosition = { getPositionAfterConstantName(ctx) }
        )
    }

    private fun processDeclaration(
        typeRef: ParserRuleContext?,
        name: ParserRuleContext?,
        getType: () -> String?,
        getPosition: () -> Position?
    ) {
        val defPosition = name?.let {
            Position(it.start.line - 1, it.stop.charPositionInLine)
        }

        if (typeRef != null || defPosition == null) return

        if (!isInRange(defPosition, range)) return

        val inferredType = getType() ?: return
        val hintPosition = getPosition() ?: return
        hints.add(createTypeInlayHint(hintPosition, inferredType))
    }

    private fun getTypeForConstant(constantCtx: RellParser.RuleX_ConstantDefContext): String? =
        constantCtx.ruleX_Name().ruleX_NameNode()?.let { node ->
            Interval(node.start.startIndex, node.stop.stopIndex + 1)
        }?.let { interval ->
            resource.getSymbolInfoForInterval(interval)?.let { symbolInfo ->
                extractTypeFromSymbolInfo(symbolInfo)
            }
        }

    private fun getPositionAfterConstantName(constantCtx: RellParser.RuleX_ConstantDefContext): Position? =
        constantCtx.ruleX_Name()?.ruleX_NameNode()?.let { nameNode ->
            val tokenLen = nameNode.text?.length ?: return null
            Position(nameNode.stop.line - 1, nameNode.stop.charPositionInLine + tokenLen)
        }

    // TODO: do we need to show hints, for simple literal expressions eg: `val x = 1;` ??
    private fun getSemanticTypeForVariable(varStmt: RellParser.RuleX_VarStmtContext): String? {
        return getIntervalForPosition(varStmt)?.let { interval ->
            resource.getSymbolInfoForInterval(interval)?.let { symbolInfo ->
                extractTypeFromSymbolInfo(symbolInfo)
            }
        }
    }

    private fun getIntervalForPosition(varStmt: RellParser.RuleX_VarStmtContext): Interval? =
        varStmt.ruleX_VarDeclarator()
            ?.ruleX_SimpleVarDeclarator()
            ?.ruleX_AttrHeader()
            ?.ruleX_AnonAttrHeader()
            ?.ruleX_QualifiedNameNode()
            ?.let { node ->
                Interval(node.start.startIndex, node.stop.stopIndex + 1)
            }

    private fun extractTypeFromSymbolInfo(symbolInfo: IdeSymbolInfo): String? =
        symbolInfo.kind.takeIf {
            it in variableStatementKind
        }?.let { _ ->
            symbolInfo.doc?.let { extractTypeFromDocSymbol(it) }
        }

    private fun extractTypeFromDocSymbol(docSymbol: DocSymbol): String? {
        val typeStr = docSymbol.declaration
            .completion
            ?.result
            ?: return null

        return RellTypeProcessor.processType(typeStr.trim())
    }

    private fun getPositionAfterVariableName(varDeclarator: RellParser.RuleX_VarDeclaratorContext): Position? =
        varDeclarator.ruleX_SimpleVarDeclarator()
            ?.ruleX_AttrHeader()
            ?.ruleX_AnonAttrHeader()
            ?.let { header ->
                val tokenLen = header.ruleX_QualifiedNameNode()?.text?.length ?: return null
                Position(header.stop.line - 1, header.stop.charPositionInLine + tokenLen)
            }

    companion object {
        val variableStatementKind = listOf(
            IdeSymbolKind.LOC_VAL,
            IdeSymbolKind.LOC_VAR,
            IdeSymbolKind.DEF_CONSTANT
        )
    }
}
