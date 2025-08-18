package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.parser.RellParser.*
import org.antlr.v4.runtime.ParserRuleContext

internal fun RuleX_CallArgsContext.getCallArgWithTrailingComma():
    Pair<List<RuleX_CallArgContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_28()?.ruleX_CommaSeparated_27()
    return Pair(commaSeparated?.ruleX_CallArg(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_AtExprWhereContext.getExpressionRefWithTrailingComma():
    Pair<List<RuleX_ExpressionRefContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_20()?.ruleX_CommaSeparated_19()
    return Pair(commaSeparated?.ruleX_ExpressionRef(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_FormalParametersContext.getFormalParameterWithTrailingComma():
    Pair<List<RuleX_FormalParameterContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_36()?.ruleX_CommaSeparated_35()
    return Pair(commaSeparated?.ruleX_FormalParameter(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_EnumDefContext.getXNamesWithTrailingComma():
    Pair<List<RuleX_EnumValueContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_12()?.ruleX_CommaSeparated_11()
    return Pair(commaSeparated?.ruleX_EnumValue(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_KeyIndexClauseContext.getAttributeDefsWithTrailingComma():
    Pair<List<RuleX_BaseAttributeDefinitionContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_8()
    return Pair(commaSeparated?.ruleX_BaseAttributeDefinition(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_AnnotationArgsContext.getAnnotationArgWithTrailingComma():
    Pair<List<RuleX_AnnotationArgContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_7()?.ruleX_CommaSeparated_6()
    return Pair(commaSeparated?.ruleX_AnnotationArg(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_CreateExprContext.getCreateExprArgWithTrailingComma():
    Pair<List<RuleX_CreateExprArgContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CreateExprArgs()?.ruleX_CommaSeparated_26()?.ruleX_CommaSeparated_25()
    return Pair(commaSeparated?.ruleX_CreateExprArg(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_UpdateStmtContext.getUpdateWhatExprWithTrailingComma():
    Pair<List<RuleX_UpdateWhatExprContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_UpdateWhat()?.ruleX_CommaSeparated_34()?.ruleX_CommaSeparated_33()
    return Pair(commaSeparated?.ruleX_UpdateWhatExpr(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_AtExprFromContext.getAtExprFromItemWithTrailingComma():
    Pair<List<RuleX_AtExprFromItemContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_16()?.ruleX_CommaSeparated_15()
    return Pair(commaSeparated?.ruleX_AtExprFromItem(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_AtExprWhatComplexContext.getAtExprWhatComplexItemWithTrailingComma():
    Pair<List<RuleX_AtExprWhatComplexItemContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_18()?.ruleX_CommaSeparated_17()
    return Pair(commaSeparated?.ruleX_AtExprWhatComplexItem(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_AtExprWhatComplexItemContext.getBaseExpr(): RuleX_BaseExprContext? =
    ruleX_ExpressionRef()?.ruleX_Expression()?.ruleX_UnaryExpr()?.ruleX_OperandExpr()?.ruleX_BaseExpr()

internal fun RuleX_TupleVarDeclaratorContext.getTupleVarContext(): RuleX_CommaSeparated_30Context =
    ruleX_CommaSeparated_30()

internal fun RuleX_TupleVarDeclaratorContext.getVarDeclaratorWithTrailingComma():
    Pair<List<RuleX_VarDeclaratorContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_30()?.ruleX_CommaSeparated_29()
    return Pair(commaSeparated?.ruleX_VarDeclarator(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_NonEmptyMapLiteralExprContext.getMapExprContext(): RuleX_CommaSeparated_24Context =
    ruleX_CommaSeparated_24()

internal fun RuleX_NonEmptyMapLiteralExprContext.getMapExprEntryWithTrailingComma():
    Pair<List<RuleX_MapLiteralExprEntryContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_24()?.ruleX_CommaSeparated_23()
    return Pair(commaSeparated?.ruleX_MapLiteralExprEntry(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_GenericTypeContext.getGenericTypeContext(): RuleX_CommaSeparated_3Context =
    ruleX_CommaSeparated_3()

internal fun RuleX_GenericTypeContext.getTypeRefWithTrailingComma():
    Pair<List<RuleX_TypeRefContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_3()?.ruleX_CommaSeparated_2()
    return Pair(commaSeparated?.ruleX_TypeRef(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_ListLiteralExprContext.getListLiteralExprContext(): RuleX_CommaSeparated_22Context =
    ruleX_CommaSeparated_22()

internal fun RuleX_ListLiteralExprContext.getExpressionRefWithTrailingComma():
    Pair<List<RuleX_ExpressionRefContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_22()?.ruleX_CommaSeparated_21()
    return Pair(commaSeparated?.ruleX_ExpressionRef(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_EntityAnnotationsContext.getEntityAnnotationContext():
    RuleX_CommaSeparated_10Context = ruleX_CommaSeparated_10()

internal fun RuleX_EntityAnnotationsContext.getXNamesWithTrailingComma():
    Pair<List<RuleX_NameContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_10()?.ruleX_CommaSeparated_9()
    return Pair(commaSeparated?.ruleX_Name(), commaSeparated?.ruleX_tkCOMMA())
}

internal fun RuleX_TupleExprContext.getXNamesWithTrailingComma():
    Pair<List<RuleX_TupleExprFieldContext>?, RuleX_tkCOMMAContext?> {
    val commaSeparated = ruleX_CommaSeparated_14()?.ruleX_CommaSeparated_13()
    return Pair(commaSeparated?.ruleX_TupleExprField(), commaSeparated?.ruleX_tkCOMMA())
}