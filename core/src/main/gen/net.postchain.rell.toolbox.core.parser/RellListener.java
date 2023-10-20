// Generated from net.postchain.rell.toolbox.core.parser/Rell.g4 by ANTLR 4.13.1
package net.postchain.rell.toolbox.core.parser;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link RellParser}.
 */
public interface RellListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_RootParser}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_RootParser(RellParser.RuleX_RootParserContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_RootParser}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_RootParser(RellParser.RuleX_RootParserContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ModuleHeader}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ModuleHeader(RellParser.RuleX_ModuleHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ModuleHeader}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ModuleHeader(RellParser.RuleX_ModuleHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Modifier}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Modifier(RellParser.RuleX_ModifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Modifier}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Modifier(RellParser.RuleX_ModifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Modifier_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Modifier_0(RellParser.RuleX_Modifier_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Modifier_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Modifier_0(RellParser.RuleX_Modifier_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Modifier_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Modifier_1(RellParser.RuleX_Modifier_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Modifier_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Modifier_1(RellParser.RuleX_Modifier_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Annotation}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Annotation(RellParser.RuleX_AnnotationContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Annotation}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Annotation(RellParser.RuleX_AnnotationContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Name}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Name(RellParser.RuleX_NameContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Name}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Name(RellParser.RuleX_NameContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnnotationArgs}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnnotationArgs(RellParser.RuleX_AnnotationArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnnotationArgs}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnnotationArgs(RellParser.RuleX_AnnotationArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnnotationArg}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnnotationArg(RellParser.RuleX_AnnotationArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnnotationArg}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnnotationArg(RellParser.RuleX_AnnotationArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnnotationArgValue}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnnotationArgValue(RellParser.RuleX_AnnotationArgValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnnotationArgValue}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnnotationArgValue(RellParser.RuleX_AnnotationArgValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_LiteralExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_LiteralExpr(RellParser.RuleX_LiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_LiteralExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_LiteralExpr(RellParser.RuleX_LiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_LiteralExpr_5}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_LiteralExpr_5(RellParser.RuleX_LiteralExpr_5Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_LiteralExpr_5}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_LiteralExpr_5(RellParser.RuleX_LiteralExpr_5Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_LiteralExpr_6}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_LiteralExpr_6(RellParser.RuleX_LiteralExpr_6Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_LiteralExpr_6}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_LiteralExpr_6(RellParser.RuleX_LiteralExpr_6Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IntExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IntExpr(RellParser.RuleX_IntExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IntExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IntExpr(RellParser.RuleX_IntExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BigIntExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BigIntExpr(RellParser.RuleX_BigIntExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BigIntExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BigIntExpr(RellParser.RuleX_BigIntExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_DecimalExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_DecimalExpr(RellParser.RuleX_DecimalExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_DecimalExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_DecimalExpr(RellParser.RuleX_DecimalExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StringExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StringExpr(RellParser.RuleX_StringExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StringExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StringExpr(RellParser.RuleX_StringExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BytesExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BytesExpr(RellParser.RuleX_BytesExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BytesExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BytesExpr(RellParser.RuleX_BytesExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NullLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NullLiteralExpr(RellParser.RuleX_NullLiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NullLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NullLiteralExpr(RellParser.RuleX_NullLiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnnotationArgName}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnnotationArgName(RellParser.RuleX_AnnotationArgNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnnotationArgName}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnnotationArgName(RellParser.RuleX_AnnotationArgNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_QualifiedName}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_QualifiedName(RellParser.RuleX_QualifiedNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_QualifiedName}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_QualifiedName(RellParser.RuleX_QualifiedNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkMODULE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkMODULE(RellParser.RuleX_tkMODULEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkMODULE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkMODULE(RellParser.RuleX_tkMODULEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnnotatedDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnnotatedDef(RellParser.RuleX_AnnotatedDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnnotatedDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnnotatedDef(RellParser.RuleX_AnnotatedDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnyDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnyDef(RellParser.RuleX_AnyDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnyDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnyDef(RellParser.RuleX_AnyDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityDef(RellParser.RuleX_EntityDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityDef(RellParser.RuleX_EntityDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityKeyword}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityKeyword(RellParser.RuleX_EntityKeywordContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityKeyword}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityKeyword(RellParser.RuleX_EntityKeywordContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityKeyword_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityKeyword_0(RellParser.RuleX_EntityKeyword_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityKeyword_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityKeyword_0(RellParser.RuleX_EntityKeyword_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityKeyword_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityKeyword_1(RellParser.RuleX_EntityKeyword_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityKeyword_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityKeyword_1(RellParser.RuleX_EntityKeyword_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityAnnotations}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityAnnotations(RellParser.RuleX_EntityAnnotationsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityAnnotations}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityAnnotations(RellParser.RuleX_EntityAnnotationsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityBody}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityBody(RellParser.RuleX_EntityBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityBody}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityBody(RellParser.RuleX_EntityBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityBodyFull}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityBodyFull(RellParser.RuleX_EntityBodyFullContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityBodyFull}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityBodyFull(RellParser.RuleX_EntityBodyFullContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_RelAnyClause}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_RelAnyClause(RellParser.RuleX_RelAnyClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_RelAnyClause}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_RelAnyClause(RellParser.RuleX_RelAnyClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_RelAttributeClause}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_RelAttributeClause(RellParser.RuleX_RelAttributeClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_RelAttributeClause}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_RelAttributeClause(RellParser.RuleX_RelAttributeClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AttributeDefinition}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AttributeDefinition(RellParser.RuleX_AttributeDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AttributeDefinition}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AttributeDefinition(RellParser.RuleX_AttributeDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseAttributeDefinition}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseAttributeDefinition(RellParser.RuleX_BaseAttributeDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseAttributeDefinition}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseAttributeDefinition(RellParser.RuleX_BaseAttributeDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkMUTABLE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkMUTABLE(RellParser.RuleX_tkMUTABLEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkMUTABLE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkMUTABLE(RellParser.RuleX_tkMUTABLEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AttrHeader}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AttrHeader(RellParser.RuleX_AttrHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AttrHeader}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AttrHeader(RellParser.RuleX_AttrHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NameTypeAttrHeader}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NameTypeAttrHeader(RellParser.RuleX_NameTypeAttrHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NameTypeAttrHeader}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NameTypeAttrHeader(RellParser.RuleX_NameTypeAttrHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Type}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Type(RellParser.RuleX_TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Type}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Type(RellParser.RuleX_TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ComplexNullableType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ComplexNullableType(RellParser.RuleX_ComplexNullableTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ComplexNullableType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ComplexNullableType(RellParser.RuleX_ComplexNullableTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkLPAR}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkLPAR(RellParser.RuleX_tkLPARContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkLPAR}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkLPAR(RellParser.RuleX_tkLPARContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TypeRef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TypeRef(RellParser.RuleX_TypeRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TypeRef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TypeRef(RellParser.RuleX_TypeRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionType(RellParser.RuleX_FunctionTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionType(RellParser.RuleX_FunctionTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BasicType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BasicType(RellParser.RuleX_BasicTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BasicType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BasicType(RellParser.RuleX_BasicTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_PrimaryType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_PrimaryType(RellParser.RuleX_PrimaryTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_PrimaryType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_PrimaryType(RellParser.RuleX_PrimaryTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_GenericType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_GenericType(RellParser.RuleX_GenericTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_GenericType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_GenericType(RellParser.RuleX_GenericTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NameType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NameType(RellParser.RuleX_NameTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NameType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NameType(RellParser.RuleX_NameTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleType(RellParser.RuleX_TupleTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleType(RellParser.RuleX_TupleTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleTypeField}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleTypeField(RellParser.RuleX_TupleTypeFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleTypeField}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleTypeField(RellParser.RuleX_TupleTypeFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleTypeTail}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleTypeTail(RellParser.RuleX_TupleTypeTailContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleTypeTail}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleTypeTail(RellParser.RuleX_TupleTypeTailContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VirtualType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VirtualType(RellParser.RuleX_VirtualTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VirtualType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VirtualType(RellParser.RuleX_VirtualTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkVIRTUAL}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkVIRTUAL(RellParser.RuleX_tkVIRTUALContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkVIRTUAL}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkVIRTUAL(RellParser.RuleX_tkVIRTUALContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_MirrorStructType}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_MirrorStructType(RellParser.RuleX_MirrorStructTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_MirrorStructType}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_MirrorStructType(RellParser.RuleX_MirrorStructTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_MirrorStructType0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_MirrorStructType0(RellParser.RuleX_MirrorStructType0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_MirrorStructType0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_MirrorStructType0(RellParser.RuleX_MirrorStructType0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkSTRUCT}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkSTRUCT(RellParser.RuleX_tkSTRUCTContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkSTRUCT}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkSTRUCT(RellParser.RuleX_tkSTRUCTContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkQUESTION}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkQUESTION(RellParser.RuleX_tkQUESTIONContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkQUESTION}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkQUESTION(RellParser.RuleX_tkQUESTIONContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AnonAttrHeader}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AnonAttrHeader(RellParser.RuleX_AnonAttrHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AnonAttrHeader}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AnonAttrHeader(RellParser.RuleX_AnonAttrHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ExpressionRef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ExpressionRef(RellParser.RuleX_ExpressionRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ExpressionRef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ExpressionRef(RellParser.RuleX_ExpressionRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Expression}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Expression(RellParser.RuleX_ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Expression}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Expression(RellParser.RuleX_ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryExpr(RellParser.RuleX_UnaryExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryExpr(RellParser.RuleX_UnaryExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPrefixOperator(RellParser.RuleX_UnaryPrefixOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPrefixOperator(RellParser.RuleX_UnaryPrefixOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPrefixOperator_0(RellParser.RuleX_UnaryPrefixOperator_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPrefixOperator_0(RellParser.RuleX_UnaryPrefixOperator_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPrefixOperator_1(RellParser.RuleX_UnaryPrefixOperator_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPrefixOperator_1(RellParser.RuleX_UnaryPrefixOperator_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_2}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPrefixOperator_2(RellParser.RuleX_UnaryPrefixOperator_2Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_2}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPrefixOperator_2(RellParser.RuleX_UnaryPrefixOperator_2Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_3}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPrefixOperator_3(RellParser.RuleX_UnaryPrefixOperator_3Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPrefixOperator_3}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPrefixOperator_3(RellParser.RuleX_UnaryPrefixOperator_3Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IncrementOperator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IncrementOperator(RellParser.RuleX_IncrementOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IncrementOperator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IncrementOperator(RellParser.RuleX_IncrementOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IncrementOperator_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IncrementOperator_0(RellParser.RuleX_IncrementOperator_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IncrementOperator_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IncrementOperator_0(RellParser.RuleX_IncrementOperator_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IncrementOperator_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IncrementOperator_1(RellParser.RuleX_IncrementOperator_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IncrementOperator_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IncrementOperator_1(RellParser.RuleX_IncrementOperator_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_OperandExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_OperandExpr(RellParser.RuleX_OperandExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_OperandExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_OperandExpr(RellParser.RuleX_OperandExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExpr(RellParser.RuleX_BaseExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExpr(RellParser.RuleX_BaseExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprHead}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprHead(RellParser.RuleX_BaseExprHeadContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprHead}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprHead(RellParser.RuleX_BaseExprHeadContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprHead_9}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprHead_9(RellParser.RuleX_BaseExprHead_9Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprHead_9}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprHead_9(RellParser.RuleX_BaseExprHead_9Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprHead_10}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprHead_10(RellParser.RuleX_BaseExprHead_10Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprHead_10}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprHead_10(RellParser.RuleX_BaseExprHead_10Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_GenericTypeExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_GenericTypeExpr(RellParser.RuleX_GenericTypeExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_GenericTypeExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_GenericTypeExpr(RellParser.RuleX_GenericTypeExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailMember}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailMember(RellParser.RuleX_BaseExprTailMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailMember}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailMember(RellParser.RuleX_BaseExprTailMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailCall}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailCall(RellParser.RuleX_BaseExprTailCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailCall}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailCall(RellParser.RuleX_BaseExprTailCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallArgs}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallArgs(RellParser.RuleX_CallArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallArgs}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallArgs(RellParser.RuleX_CallArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallArg}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallArg(RellParser.RuleX_CallArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallArg}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallArg(RellParser.RuleX_CallArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallArgValue}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallArgValue(RellParser.RuleX_CallArgValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallArgValue}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallArgValue(RellParser.RuleX_CallArgValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallArgValue_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallArgValue_0(RellParser.RuleX_CallArgValue_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallArgValue_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallArgValue_0(RellParser.RuleX_CallArgValue_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallArgValue_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallArgValue_1(RellParser.RuleX_CallArgValue_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallArgValue_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallArgValue_1(RellParser.RuleX_CallArgValue_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NameExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NameExpr(RellParser.RuleX_NameExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NameExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NameExpr(RellParser.RuleX_NameExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_DollarExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_DollarExpr(RellParser.RuleX_DollarExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_DollarExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_DollarExpr(RellParser.RuleX_DollarExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AttrExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AttrExpr(RellParser.RuleX_AttrExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AttrExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AttrExpr(RellParser.RuleX_AttrExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkDOT}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkDOT(RellParser.RuleX_tkDOTContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkDOT}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkDOT(RellParser.RuleX_tkDOTContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ParenthesesExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ParenthesesExpr(RellParser.RuleX_ParenthesesExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ParenthesesExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ParenthesesExpr(RellParser.RuleX_ParenthesesExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleExprField}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleExprField(RellParser.RuleX_TupleExprFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleExprField}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleExprField(RellParser.RuleX_TupleExprFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameEqExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleExprFieldNameEqExpr(RellParser.RuleX_TupleExprFieldNameEqExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameEqExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleExprFieldNameEqExpr(RellParser.RuleX_TupleExprFieldNameEqExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkASSIGN}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkASSIGN(RellParser.RuleX_tkASSIGNContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkASSIGN}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkASSIGN(RellParser.RuleX_tkASSIGNContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameColonExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleExprFieldNameColonExpr(RellParser.RuleX_TupleExprFieldNameColonExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldNameColonExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleExprFieldNameColonExpr(RellParser.RuleX_TupleExprFieldNameColonExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkCOLON}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkCOLON(RellParser.RuleX_tkCOLONContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkCOLON}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkCOLON(RellParser.RuleX_tkCOLONContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleExprFieldExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleExprFieldExpr(RellParser.RuleX_TupleExprFieldExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleExprFieldExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleExprFieldExpr(RellParser.RuleX_TupleExprFieldExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleExprTail}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleExprTail(RellParser.RuleX_TupleExprTailContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleExprTail}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleExprTail(RellParser.RuleX_TupleExprTailContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CreateExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CreateExpr(RellParser.RuleX_CreateExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CreateExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CreateExpr(RellParser.RuleX_CreateExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkCREATE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkCREATE(RellParser.RuleX_tkCREATEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkCREATE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkCREATE(RellParser.RuleX_tkCREATEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CreateExprArg}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CreateExprArg(RellParser.RuleX_CreateExprArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CreateExprArg}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CreateExprArg(RellParser.RuleX_CreateExprArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ListLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ListLiteralExpr(RellParser.RuleX_ListLiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ListLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ListLiteralExpr(RellParser.RuleX_ListLiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkLBRACK}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkLBRACK(RellParser.RuleX_tkLBRACKContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkLBRACK}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkLBRACK(RellParser.RuleX_tkLBRACKContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EmptyMapLiteralExpr(RellParser.RuleX_EmptyMapLiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EmptyMapLiteralExpr(RellParser.RuleX_EmptyMapLiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NonEmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NonEmptyMapLiteralExpr(RellParser.RuleX_NonEmptyMapLiteralExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NonEmptyMapLiteralExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NonEmptyMapLiteralExpr(RellParser.RuleX_NonEmptyMapLiteralExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_MapLiteralExprEntry}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_MapLiteralExprEntry(RellParser.RuleX_MapLiteralExprEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_MapLiteralExprEntry}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_MapLiteralExprEntry(RellParser.RuleX_MapLiteralExprEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_MirrorStructExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_MirrorStructExpr(RellParser.RuleX_MirrorStructExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_MirrorStructExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_MirrorStructExpr(RellParser.RuleX_MirrorStructExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VirtualTypeExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VirtualTypeExpr(RellParser.RuleX_VirtualTypeExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VirtualTypeExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VirtualTypeExpr(RellParser.RuleX_VirtualTypeExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTail}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTail(RellParser.RuleX_BaseExprTailContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTail}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTail(RellParser.RuleX_BaseExprTailContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailSubscript}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailSubscript(RellParser.RuleX_BaseExprTailSubscriptContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailSubscript}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailSubscript(RellParser.RuleX_BaseExprTailSubscriptContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailNotNull}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailNotNull(RellParser.RuleX_BaseExprTailNotNullContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailNotNull}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailNotNull(RellParser.RuleX_BaseExprTailNotNullContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailSafeMember}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailSafeMember(RellParser.RuleX_BaseExprTailSafeMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailSafeMember}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailSafeMember(RellParser.RuleX_BaseExprTailSafeMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailUnaryPostfixOp}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailUnaryPostfixOp(RellParser.RuleX_BaseExprTailUnaryPostfixOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailUnaryPostfixOp}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailUnaryPostfixOp(RellParser.RuleX_BaseExprTailUnaryPostfixOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPostfixOperator(RellParser.RuleX_UnaryPostfixOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPostfixOperator(RellParser.RuleX_UnaryPostfixOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPostfixOperator_0(RellParser.RuleX_UnaryPostfixOperator_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPostfixOperator_0(RellParser.RuleX_UnaryPostfixOperator_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UnaryPostfixOperator_1(RellParser.RuleX_UnaryPostfixOperator_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UnaryPostfixOperator_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UnaryPostfixOperator_1(RellParser.RuleX_UnaryPostfixOperator_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailAt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailAt(RellParser.RuleX_BaseExprTailAtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailAt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailAt(RellParser.RuleX_BaseExprTailAtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprAt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprAt(RellParser.RuleX_AtExprAtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprAt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprAt(RellParser.RuleX_AtExprAtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprAt_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprAt_0(RellParser.RuleX_AtExprAt_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprAt_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprAt_0(RellParser.RuleX_AtExprAt_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprAt_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprAt_1(RellParser.RuleX_AtExprAt_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprAt_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprAt_1(RellParser.RuleX_AtExprAt_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprAt_2}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprAt_2(RellParser.RuleX_AtExprAt_2Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprAt_2}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprAt_2(RellParser.RuleX_AtExprAt_2Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprAt_3}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprAt_3(RellParser.RuleX_AtExprAt_3Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprAt_3}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprAt_3(RellParser.RuleX_AtExprAt_3Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkAT}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkAT(RellParser.RuleX_tkATContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkAT}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkAT(RellParser.RuleX_tkATContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkMUL}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkMUL(RellParser.RuleX_tkMULContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkMUL}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkMUL(RellParser.RuleX_tkMULContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkPLUS}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkPLUS(RellParser.RuleX_tkPLUSContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkPLUS}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkPLUS(RellParser.RuleX_tkPLUSContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhere}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhere(RellParser.RuleX_AtExprWhereContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhere}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhere(RellParser.RuleX_AtExprWhereContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhat}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhat(RellParser.RuleX_AtExprWhatContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhat}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhat(RellParser.RuleX_AtExprWhatContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhatSimple}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhatSimple(RellParser.RuleX_AtExprWhatSimpleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhatSimple}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhatSimple(RellParser.RuleX_AtExprWhatSimpleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplex}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhatComplex(RellParser.RuleX_AtExprWhatComplexContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplex}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhatComplex(RellParser.RuleX_AtExprWhatComplexContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplexItem}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhatComplexItem(RellParser.RuleX_AtExprWhatComplexItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhatComplexItem}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhatComplexItem(RellParser.RuleX_AtExprWhatComplexItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprWhatName}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprWhatName(RellParser.RuleX_AtExprWhatNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprWhatName}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprWhatName(RellParser.RuleX_AtExprWhatNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprModifiers}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprModifiers(RellParser.RuleX_AtExprModifiersContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprModifiers(RellParser.RuleX_AtExprModifiersContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprModifiers_0(RellParser.RuleX_AtExprModifiers_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprModifiers_0(RellParser.RuleX_AtExprModifiers_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprModifiers_1(RellParser.RuleX_AtExprModifiers_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprModifiers_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprModifiers_1(RellParser.RuleX_AtExprModifiers_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprOffset}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprOffset(RellParser.RuleX_AtExprOffsetContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprOffset}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprOffset(RellParser.RuleX_AtExprOffsetContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprLimit}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprLimit(RellParser.RuleX_AtExprLimitContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprLimit}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprLimit(RellParser.RuleX_AtExprLimitContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IfExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IfExpr(RellParser.RuleX_IfExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IfExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IfExpr(RellParser.RuleX_IfExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkIF}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkIF(RellParser.RuleX_tkIFContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkIF}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkIF(RellParser.RuleX_tkIFContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenExpr(RellParser.RuleX_WhenExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenExpr(RellParser.RuleX_WhenExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkWHEN}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkWHEN(RellParser.RuleX_tkWHENContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkWHEN}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkWHEN(RellParser.RuleX_tkWHENContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenExprCases}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenExprCases(RellParser.RuleX_WhenExprCasesContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenExprCases}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenExprCases(RellParser.RuleX_WhenExprCasesContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenExprCase}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenExprCase(RellParser.RuleX_WhenExprCaseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenExprCase}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenExprCase(RellParser.RuleX_WhenExprCaseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenCondition}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenCondition(RellParser.RuleX_WhenConditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenCondition}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenCondition(RellParser.RuleX_WhenConditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenConditionExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenConditionExpr(RellParser.RuleX_WhenConditionExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenConditionExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenConditionExpr(RellParser.RuleX_WhenConditionExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenConditionElse}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenConditionElse(RellParser.RuleX_WhenConditionElseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenConditionElse}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenConditionElse(RellParser.RuleX_WhenConditionElseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkSEMI}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkSEMI(RellParser.RuleX_tkSEMIContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkSEMI}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkSEMI(RellParser.RuleX_tkSEMIContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryExprOperand}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryExprOperand(RellParser.RuleX_BinaryExprOperandContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryExprOperand}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryExprOperand(RellParser.RuleX_BinaryExprOperandContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator(RellParser.RuleX_BinaryOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator(RellParser.RuleX_BinaryOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_0(RellParser.RuleX_BinaryOperator_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_0(RellParser.RuleX_BinaryOperator_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_1(RellParser.RuleX_BinaryOperator_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_1(RellParser.RuleX_BinaryOperator_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_2}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_2(RellParser.RuleX_BinaryOperator_2Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_2}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_2(RellParser.RuleX_BinaryOperator_2Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_3}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_3(RellParser.RuleX_BinaryOperator_3Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_3}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_3(RellParser.RuleX_BinaryOperator_3Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_4}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_4(RellParser.RuleX_BinaryOperator_4Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_4}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_4(RellParser.RuleX_BinaryOperator_4Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_5}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_5(RellParser.RuleX_BinaryOperator_5Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_5}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_5(RellParser.RuleX_BinaryOperator_5Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_6}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_6(RellParser.RuleX_BinaryOperator_6Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_6}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_6(RellParser.RuleX_BinaryOperator_6Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_7}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_7(RellParser.RuleX_BinaryOperator_7Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_7}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_7(RellParser.RuleX_BinaryOperator_7Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_8}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_8(RellParser.RuleX_BinaryOperator_8Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_8}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_8(RellParser.RuleX_BinaryOperator_8Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_9}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_9(RellParser.RuleX_BinaryOperator_9Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_9}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_9(RellParser.RuleX_BinaryOperator_9Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_10}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_10(RellParser.RuleX_BinaryOperator_10Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_10}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_10(RellParser.RuleX_BinaryOperator_10Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_11}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_11(RellParser.RuleX_BinaryOperator_11Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_11}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_11(RellParser.RuleX_BinaryOperator_11Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_12}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_12(RellParser.RuleX_BinaryOperator_12Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_12}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_12(RellParser.RuleX_BinaryOperator_12Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_13}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_13(RellParser.RuleX_BinaryOperator_13Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_13}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_13(RellParser.RuleX_BinaryOperator_13Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_14}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_14(RellParser.RuleX_BinaryOperator_14Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_14}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_14(RellParser.RuleX_BinaryOperator_14Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_15}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_15(RellParser.RuleX_BinaryOperator_15Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_15}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_15(RellParser.RuleX_BinaryOperator_15Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_16}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_16(RellParser.RuleX_BinaryOperator_16Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_16}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_16(RellParser.RuleX_BinaryOperator_16Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BinaryOperator_17}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BinaryOperator_17(RellParser.RuleX_BinaryOperator_17Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BinaryOperator_17}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BinaryOperator_17(RellParser.RuleX_BinaryOperator_17Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkIN}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkIN(RellParser.RuleX_tkINContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkIN}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkIN(RellParser.RuleX_tkINContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_RelKeyIndexClause}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_RelKeyIndexClause(RellParser.RuleX_RelKeyIndexClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_RelKeyIndexClause}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_RelKeyIndexClause(RellParser.RuleX_RelKeyIndexClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_KeyIndexKind}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_KeyIndexKind(RellParser.RuleX_KeyIndexKindContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_KeyIndexKind(RellParser.RuleX_KeyIndexKindContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_KeyIndexKind_0(RellParser.RuleX_KeyIndexKind_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_KeyIndexKind_0(RellParser.RuleX_KeyIndexKind_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_KeyIndexKind_1(RellParser.RuleX_KeyIndexKind_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_KeyIndexKind_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_KeyIndexKind_1(RellParser.RuleX_KeyIndexKind_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EntityBodyShort}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EntityBodyShort(RellParser.RuleX_EntityBodyShortContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EntityBodyShort}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EntityBodyShort(RellParser.RuleX_EntityBodyShortContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ObjectDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ObjectDef(RellParser.RuleX_ObjectDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ObjectDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ObjectDef(RellParser.RuleX_ObjectDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkOBJECT}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkOBJECT(RellParser.RuleX_tkOBJECTContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkOBJECT}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkOBJECT(RellParser.RuleX_tkOBJECTContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StructDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StructDef(RellParser.RuleX_StructDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StructDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StructDef(RellParser.RuleX_StructDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StructKeyword}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StructKeyword(RellParser.RuleX_StructKeywordContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StructKeyword}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StructKeyword(RellParser.RuleX_StructKeywordContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StructKeyword_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StructKeyword_0(RellParser.RuleX_StructKeyword_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StructKeyword_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StructKeyword_0(RellParser.RuleX_StructKeyword_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StructKeyword_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StructKeyword_1(RellParser.RuleX_StructKeyword_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StructKeyword_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StructKeyword_1(RellParser.RuleX_StructKeyword_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EnumDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EnumDef(RellParser.RuleX_EnumDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EnumDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EnumDef(RellParser.RuleX_EnumDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkENUM}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkENUM(RellParser.RuleX_tkENUMContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkENUM}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkENUM(RellParser.RuleX_tkENUMContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkCOMMA}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkCOMMA(RellParser.RuleX_tkCOMMAContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkCOMMA}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkCOMMA(RellParser.RuleX_tkCOMMAContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionDef(RellParser.RuleX_FunctionDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionDef(RellParser.RuleX_FunctionDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkFUNCTION}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkFUNCTION(RellParser.RuleX_tkFUNCTIONContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkFUNCTION}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkFUNCTION(RellParser.RuleX_tkFUNCTIONContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FormalParameter}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FormalParameter(RellParser.RuleX_FormalParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FormalParameter}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FormalParameter(RellParser.RuleX_FormalParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionBody}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionBody(RellParser.RuleX_FunctionBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionBody}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionBody(RellParser.RuleX_FunctionBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionBodyShort}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionBodyShort(RellParser.RuleX_FunctionBodyShortContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionBodyShort}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionBodyShort(RellParser.RuleX_FunctionBodyShortContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionBodyFull}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionBodyFull(RellParser.RuleX_FunctionBodyFullContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionBodyFull}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionBodyFull(RellParser.RuleX_FunctionBodyFullContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BlockStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BlockStmt(RellParser.RuleX_BlockStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BlockStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BlockStmt(RellParser.RuleX_BlockStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkLCURL}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkLCURL(RellParser.RuleX_tkLCURLContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkLCURL}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkLCURL(RellParser.RuleX_tkLCURLContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_StatementRef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_StatementRef(RellParser.RuleX_StatementRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_StatementRef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_StatementRef(RellParser.RuleX_StatementRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_Statement}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_Statement(RellParser.RuleX_StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_Statement}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_Statement(RellParser.RuleX_StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_EmptyStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_EmptyStmt(RellParser.RuleX_EmptyStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_EmptyStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_EmptyStmt(RellParser.RuleX_EmptyStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VarStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VarStmt(RellParser.RuleX_VarStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VarStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VarStmt(RellParser.RuleX_VarStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VarVal}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VarVal(RellParser.RuleX_VarValContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VarVal}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VarVal(RellParser.RuleX_VarValContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VarVal_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VarVal_0(RellParser.RuleX_VarVal_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VarVal_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VarVal_0(RellParser.RuleX_VarVal_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VarVal_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VarVal_1(RellParser.RuleX_VarVal_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VarVal_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VarVal_1(RellParser.RuleX_VarVal_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_VarDeclarator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_VarDeclarator(RellParser.RuleX_VarDeclaratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_VarDeclarator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_VarDeclarator(RellParser.RuleX_VarDeclaratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_SimpleVarDeclarator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_SimpleVarDeclarator(RellParser.RuleX_SimpleVarDeclaratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_SimpleVarDeclarator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_SimpleVarDeclarator(RellParser.RuleX_SimpleVarDeclaratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_TupleVarDeclarator}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_TupleVarDeclarator(RellParser.RuleX_TupleVarDeclaratorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_TupleVarDeclarator}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_TupleVarDeclarator(RellParser.RuleX_TupleVarDeclaratorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignStmt(RellParser.RuleX_AssignStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignStmt(RellParser.RuleX_AssignStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp(RellParser.RuleX_AssignOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp(RellParser.RuleX_AssignOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_0}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_0(RellParser.RuleX_AssignOp_0Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_0}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_0(RellParser.RuleX_AssignOp_0Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_1}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_1(RellParser.RuleX_AssignOp_1Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_1}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_1(RellParser.RuleX_AssignOp_1Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_2}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_2(RellParser.RuleX_AssignOp_2Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_2}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_2(RellParser.RuleX_AssignOp_2Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_3}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_3(RellParser.RuleX_AssignOp_3Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_3}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_3(RellParser.RuleX_AssignOp_3Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_4}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_4(RellParser.RuleX_AssignOp_4Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_4}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_4(RellParser.RuleX_AssignOp_4Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AssignOp_5}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AssignOp_5(RellParser.RuleX_AssignOp_5Context ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AssignOp_5}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AssignOp_5(RellParser.RuleX_AssignOp_5Context ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ReturnStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ReturnStmt(RellParser.RuleX_ReturnStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ReturnStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ReturnStmt(RellParser.RuleX_ReturnStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkRETURN}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkRETURN(RellParser.RuleX_tkRETURNContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkRETURN}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkRETURN(RellParser.RuleX_tkRETURNContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IfStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IfStmt(RellParser.RuleX_IfStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IfStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IfStmt(RellParser.RuleX_IfStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ElseStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ElseStmt(RellParser.RuleX_ElseStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ElseStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ElseStmt(RellParser.RuleX_ElseStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkELSE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkELSE(RellParser.RuleX_tkELSEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkELSE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkELSE(RellParser.RuleX_tkELSEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenStmt(RellParser.RuleX_WhenStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenStmt(RellParser.RuleX_WhenStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhenStmtCase}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhenStmtCase(RellParser.RuleX_WhenStmtCaseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhenStmtCase}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhenStmtCase(RellParser.RuleX_WhenStmtCaseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_WhileStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_WhileStmt(RellParser.RuleX_WhileStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_WhileStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_WhileStmt(RellParser.RuleX_WhileStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkWHILE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkWHILE(RellParser.RuleX_tkWHILEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkWHILE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkWHILE(RellParser.RuleX_tkWHILEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ForStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ForStmt(RellParser.RuleX_ForStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ForStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ForStmt(RellParser.RuleX_ForStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkFOR}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkFOR(RellParser.RuleX_tkFORContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkFOR}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkFOR(RellParser.RuleX_tkFORContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BreakStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BreakStmt(RellParser.RuleX_BreakStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BreakStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BreakStmt(RellParser.RuleX_BreakStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkBREAK}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkBREAK(RellParser.RuleX_tkBREAKContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkBREAK}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkBREAK(RellParser.RuleX_tkBREAKContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ContinueStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ContinueStmt(RellParser.RuleX_ContinueStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ContinueStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ContinueStmt(RellParser.RuleX_ContinueStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkCONTINUE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkCONTINUE(RellParser.RuleX_tkCONTINUEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkCONTINUE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkCONTINUE(RellParser.RuleX_tkCONTINUEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateStmt(RellParser.RuleX_UpdateStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateStmt(RellParser.RuleX_UpdateStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkUPDATE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkUPDATE(RellParser.RuleX_tkUPDATEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkUPDATE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkUPDATE(RellParser.RuleX_tkUPDATEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateTarget}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateTarget(RellParser.RuleX_UpdateTargetContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateTarget}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateTarget(RellParser.RuleX_UpdateTargetContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateTargetAt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateTargetAt(RellParser.RuleX_UpdateTargetAtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateTargetAt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateTargetAt(RellParser.RuleX_UpdateTargetAtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprFrom}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprFrom(RellParser.RuleX_AtExprFromContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprFrom}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprFrom(RellParser.RuleX_AtExprFromContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprFromSingle}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprFromSingle(RellParser.RuleX_AtExprFromSingleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprFromSingle}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprFromSingle(RellParser.RuleX_AtExprFromSingleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprFromMulti}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprFromMulti(RellParser.RuleX_AtExprFromMultiContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprFromMulti}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprFromMulti(RellParser.RuleX_AtExprFromMultiContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AtExprFromItem}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AtExprFromItem(RellParser.RuleX_AtExprFromItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AtExprFromItem}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AtExprFromItem(RellParser.RuleX_AtExprFromItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateTargetExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateTargetExpr(RellParser.RuleX_UpdateTargetExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateTargetExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateTargetExpr(RellParser.RuleX_UpdateTargetExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprNoCallNoAt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprNoCallNoAt(RellParser.RuleX_BaseExprNoCallNoAtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprNoCallNoAt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprNoCallNoAt(RellParser.RuleX_BaseExprNoCallNoAtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_BaseExprTailNoCallNoAt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_BaseExprTailNoCallNoAt(RellParser.RuleX_BaseExprTailNoCallNoAtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_BaseExprTailNoCallNoAt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_BaseExprTailNoCallNoAt(RellParser.RuleX_BaseExprTailNoCallNoAtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateWhatExpr}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateWhatExpr(RellParser.RuleX_UpdateWhatExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateWhatExpr}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateWhatExpr(RellParser.RuleX_UpdateWhatExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpdateWhatNameOp}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpdateWhatNameOp(RellParser.RuleX_UpdateWhatNameOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpdateWhatNameOp}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpdateWhatNameOp(RellParser.RuleX_UpdateWhatNameOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_DeleteStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_DeleteStmt(RellParser.RuleX_DeleteStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_DeleteStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_DeleteStmt(RellParser.RuleX_DeleteStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkDELETE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkDELETE(RellParser.RuleX_tkDELETEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkDELETE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkDELETE(RellParser.RuleX_tkDELETEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IncrementStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IncrementStmt(RellParser.RuleX_IncrementStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IncrementStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IncrementStmt(RellParser.RuleX_IncrementStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CallStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CallStmt(RellParser.RuleX_CallStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CallStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CallStmt(RellParser.RuleX_CallStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_CreateStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_CreateStmt(RellParser.RuleX_CreateStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_CreateStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_CreateStmt(RellParser.RuleX_CreateStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_GuardStmt}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_GuardStmt(RellParser.RuleX_GuardStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_GuardStmt}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_GuardStmt(RellParser.RuleX_GuardStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkGUARD}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkGUARD(RellParser.RuleX_tkGUARDContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkGUARD}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkGUARD(RellParser.RuleX_tkGUARDContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_FunctionBodyNone}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_FunctionBodyNone(RellParser.RuleX_FunctionBodyNoneContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_FunctionBodyNone}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_FunctionBodyNone(RellParser.RuleX_FunctionBodyNoneContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_NamespaceDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_NamespaceDef(RellParser.RuleX_NamespaceDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_NamespaceDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_NamespaceDef(RellParser.RuleX_NamespaceDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkNAMESPACE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkNAMESPACE(RellParser.RuleX_tkNAMESPACEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkNAMESPACE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkNAMESPACE(RellParser.RuleX_tkNAMESPACEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportDef(RellParser.RuleX_ImportDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportDef(RellParser.RuleX_ImportDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkIMPORT}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkIMPORT(RellParser.RuleX_tkIMPORTContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkIMPORT}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkIMPORT(RellParser.RuleX_tkIMPORTContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportModule}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportModule(RellParser.RuleX_ImportModuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportModule}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportModule(RellParser.RuleX_ImportModuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_AbsoluteImportModule}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_AbsoluteImportModule(RellParser.RuleX_AbsoluteImportModuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_AbsoluteImportModule}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_AbsoluteImportModule(RellParser.RuleX_AbsoluteImportModuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_RelativeImportModule}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_RelativeImportModule(RellParser.RuleX_RelativeImportModuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_RelativeImportModule}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_RelativeImportModule(RellParser.RuleX_RelativeImportModuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_UpImportModule}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_UpImportModule(RellParser.RuleX_UpImportModuleContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_UpImportModule}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_UpImportModule(RellParser.RuleX_UpImportModuleContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkCARET}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkCARET(RellParser.RuleX_tkCARETContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkCARET}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkCARET(RellParser.RuleX_tkCARETContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportTarget}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportTarget(RellParser.RuleX_ImportTargetContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportTarget}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportTarget(RellParser.RuleX_ImportTargetContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportTargetExact}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportTargetExact(RellParser.RuleX_ImportTargetExactContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportTargetExact}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportTargetExact(RellParser.RuleX_ImportTargetExactContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportTargetExactItem}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportTargetExactItem(RellParser.RuleX_ImportTargetExactItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportTargetExactItem}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportTargetExactItem(RellParser.RuleX_ImportTargetExactItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ImportTargetWildcard}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ImportTargetWildcard(RellParser.RuleX_ImportTargetWildcardContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ImportTargetWildcard}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ImportTargetWildcard(RellParser.RuleX_ImportTargetWildcardContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_OpDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_OpDef(RellParser.RuleX_OpDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_OpDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_OpDef(RellParser.RuleX_OpDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkOPERATION}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkOPERATION(RellParser.RuleX_tkOPERATIONContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkOPERATION}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkOPERATION(RellParser.RuleX_tkOPERATIONContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_QueryDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_QueryDef(RellParser.RuleX_QueryDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_QueryDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_QueryDef(RellParser.RuleX_QueryDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkQUERY}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkQUERY(RellParser.RuleX_tkQUERYContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkQUERY}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkQUERY(RellParser.RuleX_tkQUERYContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_QueryBody}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_QueryBody(RellParser.RuleX_QueryBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_QueryBody}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_QueryBody(RellParser.RuleX_QueryBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_IncludeDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_IncludeDef(RellParser.RuleX_IncludeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_IncludeDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_IncludeDef(RellParser.RuleX_IncludeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkINCLUDE}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkINCLUDE(RellParser.RuleX_tkINCLUDEContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkINCLUDE}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkINCLUDE(RellParser.RuleX_tkINCLUDEContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkSTRING}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkSTRING(RellParser.RuleX_tkSTRINGContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkSTRING}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkSTRING(RellParser.RuleX_tkSTRINGContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_ConstantDef}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_ConstantDef(RellParser.RuleX_ConstantDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_ConstantDef}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_ConstantDef(RellParser.RuleX_ConstantDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RellParser#ruleX_tkVAL}.
	 * @param ctx the parse tree
	 */
	void enterRuleX_tkVAL(RellParser.RuleX_tkVALContext ctx);
	/**
	 * Exit a parse tree produced by {@link RellParser#ruleX_tkVAL}.
	 * @param ctx the parse tree
	 */
	void exitRuleX_tkVAL(RellParser.RuleX_tkVALContext ctx);
}