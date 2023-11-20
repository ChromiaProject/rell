// Generated from net.postchain.rell.toolbox.core.parser/Rell.g4 by ANTLR 4.13.1
package net.postchain.rell.toolbox.core.parser;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link RellParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface RellVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_RootParser}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_RootParser(RellParser.RuleX_RootParserContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ModuleHeader}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ModuleHeader(RellParser.RuleX_ModuleHeaderContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Modifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Modifier(RellParser.RuleX_ModifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Modifier_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Modifier_0(RellParser.RuleX_Modifier_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Modifier_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Modifier_1(RellParser.RuleX_Modifier_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Annotation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Annotation(RellParser.RuleX_AnnotationContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Name}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Name(RellParser.RuleX_NameContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnnotationArgs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnnotationArgs(RellParser.RuleX_AnnotationArgsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnnotationArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnnotationArg(RellParser.RuleX_AnnotationArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnnotationArgValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnnotationArgValue(RellParser.RuleX_AnnotationArgValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_LiteralExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_LiteralExpr(RellParser.RuleX_LiteralExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_LiteralExpr_5}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_LiteralExpr_5(RellParser.RuleX_LiteralExpr_5Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_LiteralExpr_6}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_LiteralExpr_6(RellParser.RuleX_LiteralExpr_6Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IntExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IntExpr(RellParser.RuleX_IntExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BigIntExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BigIntExpr(RellParser.RuleX_BigIntExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_DecimalExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_DecimalExpr(RellParser.RuleX_DecimalExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StringExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StringExpr(RellParser.RuleX_StringExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BytesExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BytesExpr(RellParser.RuleX_BytesExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NullLiteralExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NullLiteralExpr(RellParser.RuleX_NullLiteralExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnnotationArgName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnnotationArgName(RellParser.RuleX_AnnotationArgNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_QualifiedName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_QualifiedName(RellParser.RuleX_QualifiedNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkMODULE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkMODULE(RellParser.RuleX_tkMODULEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnnotatedDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnnotatedDef(RellParser.RuleX_AnnotatedDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnyDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnyDef(RellParser.RuleX_AnyDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityDef(RellParser.RuleX_EntityDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityKeyword}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityKeyword(RellParser.RuleX_EntityKeywordContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityKeyword_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityKeyword_0(RellParser.RuleX_EntityKeyword_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityKeyword_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityKeyword_1(RellParser.RuleX_EntityKeyword_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityAnnotations}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityAnnotations(RellParser.RuleX_EntityAnnotationsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityBody(RellParser.RuleX_EntityBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityBodyFull}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityBodyFull(RellParser.RuleX_EntityBodyFullContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_RelAnyClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_RelAnyClause(RellParser.RuleX_RelAnyClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_RelAttributeClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_RelAttributeClause(RellParser.RuleX_RelAttributeClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AttributeDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AttributeDefinition(RellParser.RuleX_AttributeDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseAttributeDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseAttributeDefinition(RellParser.RuleX_BaseAttributeDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkMUTABLE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkMUTABLE(RellParser.RuleX_tkMUTABLEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AttrHeader}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AttrHeader(RellParser.RuleX_AttrHeaderContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NameTypeAttrHeader}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NameTypeAttrHeader(RellParser.RuleX_NameTypeAttrHeaderContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Type(RellParser.RuleX_TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ComplexNullableType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ComplexNullableType(RellParser.RuleX_ComplexNullableTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkLPAR}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkLPAR(RellParser.RuleX_tkLPARContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TypeRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TypeRef(RellParser.RuleX_TypeRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionType(RellParser.RuleX_FunctionTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BasicType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BasicType(RellParser.RuleX_BasicTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_PrimaryType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_PrimaryType(RellParser.RuleX_PrimaryTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_GenericType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_GenericType(RellParser.RuleX_GenericTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NameType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NameType(RellParser.RuleX_NameTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleType(RellParser.RuleX_TupleTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleTypeField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleTypeField(RellParser.RuleX_TupleTypeFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleTypeTail}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleTypeTail(RellParser.RuleX_TupleTypeTailContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VirtualType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VirtualType(RellParser.RuleX_VirtualTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkVIRTUAL}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkVIRTUAL(RellParser.RuleX_tkVIRTUALContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_MirrorStructType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_MirrorStructType(RellParser.RuleX_MirrorStructTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_MirrorStructType0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_MirrorStructType0(RellParser.RuleX_MirrorStructType0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkSTRUCT}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkSTRUCT(RellParser.RuleX_tkSTRUCTContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkQUESTION}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkQUESTION(RellParser.RuleX_tkQUESTIONContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AnonAttrHeader}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AnonAttrHeader(RellParser.RuleX_AnonAttrHeaderContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ExpressionRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ExpressionRef(RellParser.RuleX_ExpressionRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Expression(RellParser.RuleX_ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryExpr(RellParser.RuleX_UnaryExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPrefixOperator(RellParser.RuleX_UnaryPrefixOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPrefixOperator_0(RellParser.RuleX_UnaryPrefixOperator_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPrefixOperator_1(RellParser.RuleX_UnaryPrefixOperator_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_2}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPrefixOperator_2(RellParser.RuleX_UnaryPrefixOperator_2Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_3}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPrefixOperator_3(RellParser.RuleX_UnaryPrefixOperator_3Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IncrementOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IncrementOperator(RellParser.RuleX_IncrementOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IncrementOperator_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IncrementOperator_0(RellParser.RuleX_IncrementOperator_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IncrementOperator_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IncrementOperator_1(RellParser.RuleX_IncrementOperator_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_OperandExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_OperandExpr(RellParser.RuleX_OperandExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExpr(RellParser.RuleX_BaseExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprHead}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprHead(RellParser.RuleX_BaseExprHeadContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprHead_9}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprHead_9(RellParser.RuleX_BaseExprHead_9Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprHead_10}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprHead_10(RellParser.RuleX_BaseExprHead_10Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_GenericTypeExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_GenericTypeExpr(RellParser.RuleX_GenericTypeExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailMember(RellParser.RuleX_BaseExprTailMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailCall(RellParser.RuleX_BaseExprTailCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallArgs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallArgs(RellParser.RuleX_CallArgsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallArg(RellParser.RuleX_CallArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallArgValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallArgValue(RellParser.RuleX_CallArgValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallArgValue_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallArgValue_0(RellParser.RuleX_CallArgValue_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallArgValue_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallArgValue_1(RellParser.RuleX_CallArgValue_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NameExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NameExpr(RellParser.RuleX_NameExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_DollarExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_DollarExpr(RellParser.RuleX_DollarExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AttrExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AttrExpr(RellParser.RuleX_AttrExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkDOT}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkDOT(RellParser.RuleX_tkDOTContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ParenthesesExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ParenthesesExpr(RellParser.RuleX_ParenthesesExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleExprField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleExprField(RellParser.RuleX_TupleExprFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameEqExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleExprFieldNameEqExpr(RellParser.RuleX_TupleExprFieldNameEqExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkASSIGN}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkASSIGN(RellParser.RuleX_tkASSIGNContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameColonExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleExprFieldNameColonExpr(RellParser.RuleX_TupleExprFieldNameColonExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkCOLON}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkCOLON(RellParser.RuleX_tkCOLONContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleExprFieldExpr(RellParser.RuleX_TupleExprFieldExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleExprTail}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleExprTail(RellParser.RuleX_TupleExprTailContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CreateExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CreateExpr(RellParser.RuleX_CreateExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkCREATE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkCREATE(RellParser.RuleX_tkCREATEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CreateExprArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CreateExprArg(RellParser.RuleX_CreateExprArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ListLiteralExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ListLiteralExpr(RellParser.RuleX_ListLiteralExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkLBRACK}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkLBRACK(RellParser.RuleX_tkLBRACKContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EmptyMapLiteralExpr(RellParser.RuleX_EmptyMapLiteralExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NonEmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NonEmptyMapLiteralExpr(RellParser.RuleX_NonEmptyMapLiteralExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_MapLiteralExprEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_MapLiteralExprEntry(RellParser.RuleX_MapLiteralExprEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_MirrorStructExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_MirrorStructExpr(RellParser.RuleX_MirrorStructExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VirtualTypeExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VirtualTypeExpr(RellParser.RuleX_VirtualTypeExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTail}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTail(RellParser.RuleX_BaseExprTailContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailSubscript}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailSubscript(RellParser.RuleX_BaseExprTailSubscriptContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailNotNull}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailNotNull(RellParser.RuleX_BaseExprTailNotNullContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailSafeMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailSafeMember(RellParser.RuleX_BaseExprTailSafeMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailUnaryPostfixOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailUnaryPostfixOp(RellParser.RuleX_BaseExprTailUnaryPostfixOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPostfixOperator(RellParser.RuleX_UnaryPostfixOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPostfixOperator_0(RellParser.RuleX_UnaryPostfixOperator_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UnaryPostfixOperator_1(RellParser.RuleX_UnaryPostfixOperator_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailAt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailAt(RellParser.RuleX_BaseExprTailAtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprAt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprAt(RellParser.RuleX_AtExprAtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprAt_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprAt_0(RellParser.RuleX_AtExprAt_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprAt_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprAt_1(RellParser.RuleX_AtExprAt_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprAt_2}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprAt_2(RellParser.RuleX_AtExprAt_2Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprAt_3}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprAt_3(RellParser.RuleX_AtExprAt_3Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkAT}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkAT(RellParser.RuleX_tkATContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkMUL}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkMUL(RellParser.RuleX_tkMULContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkPLUS}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkPLUS(RellParser.RuleX_tkPLUSContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhere}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhere(RellParser.RuleX_AtExprWhereContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhat}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhat(RellParser.RuleX_AtExprWhatContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhatSimple}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhatSimple(RellParser.RuleX_AtExprWhatSimpleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplex}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhatComplex(RellParser.RuleX_AtExprWhatComplexContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplexItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhatComplexItem(RellParser.RuleX_AtExprWhatComplexItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprWhatName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprWhatName(RellParser.RuleX_AtExprWhatNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprModifiers(RellParser.RuleX_AtExprModifiersContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprModifiers_0(RellParser.RuleX_AtExprModifiers_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprModifiers_1(RellParser.RuleX_AtExprModifiers_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprOffset}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprOffset(RellParser.RuleX_AtExprOffsetContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprLimit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprLimit(RellParser.RuleX_AtExprLimitContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IfExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IfExpr(RellParser.RuleX_IfExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkIF}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkIF(RellParser.RuleX_tkIFContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenExpr(RellParser.RuleX_WhenExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkWHEN}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkWHEN(RellParser.RuleX_tkWHENContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenExprCases}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenExprCases(RellParser.RuleX_WhenExprCasesContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenExprCase}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenExprCase(RellParser.RuleX_WhenExprCaseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenCondition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenCondition(RellParser.RuleX_WhenConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenConditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenConditionExpr(RellParser.RuleX_WhenConditionExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenConditionElse}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenConditionElse(RellParser.RuleX_WhenConditionElseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkSEMI}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkSEMI(RellParser.RuleX_tkSEMIContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryExprOperand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryExprOperand(RellParser.RuleX_BinaryExprOperandContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator(RellParser.RuleX_BinaryOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_0(RellParser.RuleX_BinaryOperator_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_1(RellParser.RuleX_BinaryOperator_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_2}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_2(RellParser.RuleX_BinaryOperator_2Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_3}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_3(RellParser.RuleX_BinaryOperator_3Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_4}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_4(RellParser.RuleX_BinaryOperator_4Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_5}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_5(RellParser.RuleX_BinaryOperator_5Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_6}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_6(RellParser.RuleX_BinaryOperator_6Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_7}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_7(RellParser.RuleX_BinaryOperator_7Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_8}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_8(RellParser.RuleX_BinaryOperator_8Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_9}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_9(RellParser.RuleX_BinaryOperator_9Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_10}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_10(RellParser.RuleX_BinaryOperator_10Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_11}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_11(RellParser.RuleX_BinaryOperator_11Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_12}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_12(RellParser.RuleX_BinaryOperator_12Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_13}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_13(RellParser.RuleX_BinaryOperator_13Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_14}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_14(RellParser.RuleX_BinaryOperator_14Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_15}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_15(RellParser.RuleX_BinaryOperator_15Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_16}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_16(RellParser.RuleX_BinaryOperator_16Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_17}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BinaryOperator_17(RellParser.RuleX_BinaryOperator_17Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkIN}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkIN(RellParser.RuleX_tkINContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_RelKeyIndexClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_RelKeyIndexClause(RellParser.RuleX_RelKeyIndexClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_KeyIndexKind(RellParser.RuleX_KeyIndexKindContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_KeyIndexKind_0(RellParser.RuleX_KeyIndexKind_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_KeyIndexKind_1(RellParser.RuleX_KeyIndexKind_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EntityBodyShort}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EntityBodyShort(RellParser.RuleX_EntityBodyShortContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ObjectDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ObjectDef(RellParser.RuleX_ObjectDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkOBJECT}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkOBJECT(RellParser.RuleX_tkOBJECTContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StructDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StructDef(RellParser.RuleX_StructDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StructKeyword}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StructKeyword(RellParser.RuleX_StructKeywordContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StructKeyword_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StructKeyword_0(RellParser.RuleX_StructKeyword_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StructKeyword_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StructKeyword_1(RellParser.RuleX_StructKeyword_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EnumDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EnumDef(RellParser.RuleX_EnumDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkENUM}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkENUM(RellParser.RuleX_tkENUMContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkCOMMA}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkCOMMA(RellParser.RuleX_tkCOMMAContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionDef(RellParser.RuleX_FunctionDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkFUNCTION}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkFUNCTION(RellParser.RuleX_tkFUNCTIONContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FormalParameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FormalParameter(RellParser.RuleX_FormalParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionBody(RellParser.RuleX_FunctionBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionBodyShort}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionBodyShort(RellParser.RuleX_FunctionBodyShortContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionBodyFull}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionBodyFull(RellParser.RuleX_FunctionBodyFullContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BlockStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BlockStmt(RellParser.RuleX_BlockStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkLCURL}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkLCURL(RellParser.RuleX_tkLCURLContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_StatementRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_StatementRef(RellParser.RuleX_StatementRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_Statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_Statement(RellParser.RuleX_StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_EmptyStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_EmptyStmt(RellParser.RuleX_EmptyStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VarStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VarStmt(RellParser.RuleX_VarStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VarVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VarVal(RellParser.RuleX_VarValContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VarVal_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VarVal_0(RellParser.RuleX_VarVal_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VarVal_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VarVal_1(RellParser.RuleX_VarVal_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_VarDeclarator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_VarDeclarator(RellParser.RuleX_VarDeclaratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_SimpleVarDeclarator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_SimpleVarDeclarator(RellParser.RuleX_SimpleVarDeclaratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_TupleVarDeclarator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_TupleVarDeclarator(RellParser.RuleX_TupleVarDeclaratorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignStmt(RellParser.RuleX_AssignStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp(RellParser.RuleX_AssignOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_0}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_0(RellParser.RuleX_AssignOp_0Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_1}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_1(RellParser.RuleX_AssignOp_1Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_2}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_2(RellParser.RuleX_AssignOp_2Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_3}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_3(RellParser.RuleX_AssignOp_3Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_4}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_4(RellParser.RuleX_AssignOp_4Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AssignOp_5}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AssignOp_5(RellParser.RuleX_AssignOp_5Context ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ReturnStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ReturnStmt(RellParser.RuleX_ReturnStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkRETURN}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkRETURN(RellParser.RuleX_tkRETURNContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IfStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IfStmt(RellParser.RuleX_IfStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ElseStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ElseStmt(RellParser.RuleX_ElseStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkELSE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkELSE(RellParser.RuleX_tkELSEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenStmt(RellParser.RuleX_WhenStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhenStmtCase}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhenStmtCase(RellParser.RuleX_WhenStmtCaseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_WhileStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_WhileStmt(RellParser.RuleX_WhileStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkWHILE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkWHILE(RellParser.RuleX_tkWHILEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ForStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ForStmt(RellParser.RuleX_ForStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkFOR}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkFOR(RellParser.RuleX_tkFORContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BreakStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BreakStmt(RellParser.RuleX_BreakStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkBREAK}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkBREAK(RellParser.RuleX_tkBREAKContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ContinueStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ContinueStmt(RellParser.RuleX_ContinueStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkCONTINUE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkCONTINUE(RellParser.RuleX_tkCONTINUEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateStmt(RellParser.RuleX_UpdateStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkUPDATE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkUPDATE(RellParser.RuleX_tkUPDATEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateTarget}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateTarget(RellParser.RuleX_UpdateTargetContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateTargetAt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateTargetAt(RellParser.RuleX_UpdateTargetAtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprFrom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprFrom(RellParser.RuleX_AtExprFromContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprFromSingle}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprFromSingle(RellParser.RuleX_AtExprFromSingleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprFromMulti}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprFromMulti(RellParser.RuleX_AtExprFromMultiContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AtExprFromItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AtExprFromItem(RellParser.RuleX_AtExprFromItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateTargetExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateTargetExpr(RellParser.RuleX_UpdateTargetExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprNoCallNoAt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprNoCallNoAt(RellParser.RuleX_BaseExprNoCallNoAtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_BaseExprTailNoCallNoAt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_BaseExprTailNoCallNoAt(RellParser.RuleX_BaseExprTailNoCallNoAtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateWhatExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateWhatExpr(RellParser.RuleX_UpdateWhatExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpdateWhatNameOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpdateWhatNameOp(RellParser.RuleX_UpdateWhatNameOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_DeleteStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_DeleteStmt(RellParser.RuleX_DeleteStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkDELETE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkDELETE(RellParser.RuleX_tkDELETEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IncrementStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IncrementStmt(RellParser.RuleX_IncrementStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CallStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CallStmt(RellParser.RuleX_CallStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_CreateStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_CreateStmt(RellParser.RuleX_CreateStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_GuardStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_GuardStmt(RellParser.RuleX_GuardStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkGUARD}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkGUARD(RellParser.RuleX_tkGUARDContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_FunctionBodyNone}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_FunctionBodyNone(RellParser.RuleX_FunctionBodyNoneContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_NamespaceDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_NamespaceDef(RellParser.RuleX_NamespaceDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkNAMESPACE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkNAMESPACE(RellParser.RuleX_tkNAMESPACEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportDef(RellParser.RuleX_ImportDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkIMPORT}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkIMPORT(RellParser.RuleX_tkIMPORTContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportModule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportModule(RellParser.RuleX_ImportModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_AbsoluteImportModule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_AbsoluteImportModule(RellParser.RuleX_AbsoluteImportModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_RelativeImportModule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_RelativeImportModule(RellParser.RuleX_RelativeImportModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_UpImportModule}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_UpImportModule(RellParser.RuleX_UpImportModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkCARET}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkCARET(RellParser.RuleX_tkCARETContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportTarget}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportTarget(RellParser.RuleX_ImportTargetContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportTargetExact}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportTargetExact(RellParser.RuleX_ImportTargetExactContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportTargetExactItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportTargetExactItem(RellParser.RuleX_ImportTargetExactItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ImportTargetWildcard}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ImportTargetWildcard(RellParser.RuleX_ImportTargetWildcardContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_OpDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_OpDef(RellParser.RuleX_OpDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkOPERATION}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkOPERATION(RellParser.RuleX_tkOPERATIONContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_QueryDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_QueryDef(RellParser.RuleX_QueryDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkQUERY}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkQUERY(RellParser.RuleX_tkQUERYContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_QueryBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_QueryBody(RellParser.RuleX_QueryBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_IncludeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_IncludeDef(RellParser.RuleX_IncludeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkINCLUDE}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkINCLUDE(RellParser.RuleX_tkINCLUDEContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkSTRING}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkSTRING(RellParser.RuleX_tkSTRINGContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_ConstantDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_ConstantDef(RellParser.RuleX_ConstantDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RellParser#ruleX_tkVAL}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleX_tkVAL(RellParser.RuleX_tkVALContext ctx);
}