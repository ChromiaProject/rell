package net.postchain.rell.toolbox.transformer;

import net.postchain.rell.toolbox.transformer.AntlrToRellContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.ObjectUtils;

import static net.postchain.rell.toolbox.parser.RellParser.*;

public final class AntlrToRell {
    private static final RellcTransformer TRANS_XKEYWORD_MODIFIER0_0 = RellcUtils.transformer("X_KeywordModifier0_0");
    private static final RellcTransformer TRANS_XKEYWORD_MODIFIER0_1 = RellcUtils.transformer("X_KeywordModifier0_1");
    private static final RellcTransformer TRANS_XKEYWORD_MODIFIER = RellcUtils.transformer("X_KeywordModifier");
    private static final RellcTransformer TRANS_XNAME_NODE = RellcUtils.transformer("X_NameNode");
    private static final RellcTransformer TRANS_XNAME = RellcUtils.transformer("X_Name");
    private static final RellcTransformer TRANS_XINT_EXPR = RellcUtils.transformer("X_IntExpr");
    private static final RellcTransformer TRANS_XBIG_INT_EXPR = RellcUtils.transformer("X_BigIntExpr");
    private static final RellcTransformer TRANS_XDECIMAL_EXPR = RellcUtils.transformer("X_DecimalExpr");
    private static final RellcTransformer TRANS_XSTRING_EXPR = RellcUtils.transformer("X_StringExpr");
    private static final RellcTransformer TRANS_XBYTES_EXPR = RellcUtils.transformer("X_BytesExpr");
    private static final RellcTransformer TRANS_XLITERAL_EXPR_5 = RellcUtils.transformer("X_LiteralExpr_5");
    private static final RellcTransformer TRANS_XLITERAL_EXPR_6 = RellcUtils.transformer("X_LiteralExpr_6");
    private static final RellcTransformer TRANS_XNULL_LITERAL_EXPR = RellcUtils.transformer("X_NullLiteralExpr");
    private static final RellcTransformer TRANS_XANNOTATION_ARG_VALUE = RellcUtils.transformer("X_AnnotationArgValue");
    private static final RellcTransformer TRANS_XQUALIFIED_NAME_NODE = RellcUtils.transformer("X_QualifiedNameNode");
    private static final RellcTransformer TRANS_XQUALIFIED_NAME = RellcUtils.transformer("X_QualifiedName");
    private static final RellcTransformer TRANS_XANNOTATION_ARG_NAME = RellcUtils.transformer("X_AnnotationArgName");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_6 = RellcUtils.transformer("X_CommaSeparated_6");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_7 = RellcUtils.transformer("X_CommaSeparated_7");
    private static final RellcTransformer TRANS_XANNOTATION_ARGS = RellcUtils.transformer("X_AnnotationArgs");
    private static final RellcTransformer TRANS_XANNOTATION = RellcUtils.transformer("X_Annotation");
    private static final RellcTransformer TRANS_XMODIFIERS = RellcUtils.transformer("X_Modifiers");
    private static final RellcTransformer TRANS_XMODULE_HEADER = RellcUtils.transformer("X_ModuleHeader");
    private static final RellcTransformer TRANS_XENTITY_KEYWORD_0 = RellcUtils.transformer("X_EntityKeyword_0");
    private static final RellcTransformer TRANS_XENTITY_KEYWORD_1 = RellcUtils.transformer("X_EntityKeyword_1");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_9 = RellcUtils.transformer("X_CommaSeparated_9");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_10 = RellcUtils.transformer("X_CommaSeparated_10");
    private static final RellcTransformer TRANS_XENTITY_ANNOTATIONS = RellcUtils.transformer("X_EntityAnnotations");
    private static final RellcTransformer TRANS_XCOMPLEX_NULLABLE_TYPE = RellcUtils.transformer("X_ComplexNullableType");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_4 = RellcUtils.transformer("X_CommaSeparated_4");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_5 = RellcUtils.transformer("X_CommaSeparated_5");
    private static final RellcTransformer TRANS_XFUNCTION_TYPE = RellcUtils.transformer("X_FunctionType");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_2 = RellcUtils.transformer("X_CommaSeparated_2");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_3 = RellcUtils.transformer("X_CommaSeparated_3");
    private static final RellcTransformer TRANS_XGENERIC_TYPE = RellcUtils.transformer("X_GenericType");
    private static final RellcTransformer TRANS_XNAME_TYPE = RellcUtils.transformer("X_NameType");
    private static final RellcTransformer TRANS_XTUPLE_TYPE_FIELD = RellcUtils.transformer("X_TupleTypeField");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_0 = RellcUtils.transformer("X_CommaSeparated_0");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_1 = RellcUtils.transformer("X_CommaSeparated_1");
    private static final RellcTransformer TRANS_XTUPLE_TYPE = RellcUtils.transformer("X_TupleType");
    private static final RellcTransformer TRANS_XVIRTUAL_TYPE = RellcUtils.transformer("X_VirtualType");
    private static final RellcTransformer TRANS_XMIRROR_STRUCT_TYPE = RellcUtils.transformer("X_MirrorStructType");
    private static final RellcTransformer TRANS_XBASIC_TYPE = RellcUtils.transformer("X_BasicType");
    private static final RellcTransformer TRANS_XNAME_TYPE_ATTR_HEADER = RellcUtils.transformer("X_NameTypeAttrHeader");
    private static final RellcTransformer TRANS_XANON_ATTR_HEADER = RellcUtils.transformer("X_AnonAttrHeader");
    private static final RellcTransformer TRANS_XUNARY_PREFIX_OPERATOR_0 = RellcUtils.transformer("X_UnaryPrefixOperator_0");
    private static final RellcTransformer TRANS_XUNARY_PREFIX_OPERATOR_1 = RellcUtils.transformer("X_UnaryPrefixOperator_1");
    private static final RellcTransformer TRANS_XUNARY_PREFIX_OPERATOR_2 = RellcUtils.transformer("X_UnaryPrefixOperator_2");
    private static final RellcTransformer TRANS_XINCREMENT_OPERATOR_0 = RellcUtils.transformer("X_IncrementOperator_0");
    private static final RellcTransformer TRANS_XINCREMENT_OPERATOR_1 = RellcUtils.transformer("X_IncrementOperator_1");
    private static final RellcTransformer TRANS_XUNARY_PREFIX_OPERATOR_3 = RellcUtils.transformer("X_UnaryPrefixOperator_3");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_MEMBER = RellcUtils.transformer("X_BaseExprTailMember");
    private static final RellcTransformer TRANS_XCALL_ARG_VALUE_0 = RellcUtils.transformer("X_CallArgValue_0");
    private static final RellcTransformer TRANS_XCALL_ARG_VALUE_1 = RellcUtils.transformer("X_CallArgValue_1");
    private static final RellcTransformer TRANS_XCALL_ARG = RellcUtils.transformer("X_CallArg");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_27 = RellcUtils.transformer("X_CommaSeparated_27");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_28 = RellcUtils.transformer("X_CommaSeparated_28");
    private static final RellcTransformer TRANS_XCALL_ARGS = RellcUtils.transformer("X_CallArgs");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_CALL = RellcUtils.transformer("X_BaseExprTailCall");
    private static final RellcTransformer TRANS_XGENERIC_TYPE_EXPR = RellcUtils.transformer("X_GenericTypeExpr");
    private static final RellcTransformer TRANS_XAT_EXPR_FROM_ITEM = RellcUtils.transformer("X_AtExprFromItem");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_15 = RellcUtils.transformer("X_CommaSeparated_15");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_16 = RellcUtils.transformer("X_CommaSeparated_16");
    private static final RellcTransformer TRANS_XAT_EXPR_FROM = RellcUtils.transformer("X_AtExprFrom");
    private static final RellcTransformer TRANS_XAT_EXPR_AT_0 = RellcUtils.transformer("X_AtExprAt_0");
    private static final RellcTransformer TRANS_XAT_EXPR_AT_1 = RellcUtils.transformer("X_AtExprAt_1");
    private static final RellcTransformer TRANS_XAT_EXPR_AT_2 = RellcUtils.transformer("X_AtExprAt_2");
    private static final RellcTransformer TRANS_XAT_EXPR_AT_3 = RellcUtils.transformer("X_AtExprAt_3");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_19 = RellcUtils.transformer("X_CommaSeparated_19");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_20 = RellcUtils.transformer("X_CommaSeparated_20");
    private static final RellcTransformer TRANS_XAT_EXPR_WHERE = RellcUtils.transformer("X_AtExprWhere");
    private static final RellcTransformer TRANS_XAT_EXPR_WHAT_SIMPLE = RellcUtils.transformer("X_AtExprWhatSimple");
    private static final RellcTransformer TRANS_XAT_EXPR_WHAT_COMPLEX_ITEM = RellcUtils.transformer("X_AtExprWhatComplexItem");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_17 = RellcUtils.transformer("X_CommaSeparated_17");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_18 = RellcUtils.transformer("X_CommaSeparated_18");
    private static final RellcTransformer TRANS_XAT_EXPR_WHAT_COMPLEX = RellcUtils.transformer("X_AtExprWhatComplex");
    private static final RellcTransformer TRANS_XAT_EXPR_MODIFIERS_0 = RellcUtils.transformer("X_AtExprModifiers_0");
    private static final RellcTransformer TRANS_XAT_EXPR_MODIFIERS_1 = RellcUtils.transformer("X_AtExprModifiers_1");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_AT = RellcUtils.transformer("X_BaseExprTailAt");
    private static final RellcTransformer TRANS_XAT_EXPR = RellcUtils.transformer("X_AtExpr");
    private static final RellcTransformer TRANS_XNAME_EXPR = RellcUtils.transformer("X_NameExpr");
    private static final RellcTransformer TRANS_XDOLLAR_EXPR = RellcUtils.transformer("X_DollarExpr");
    private static final RellcTransformer TRANS_XATTR_EXPR = RellcUtils.transformer("X_AttrExpr");
    private static final RellcTransformer TRANS_XBASE_EXPR_HEAD_10 = RellcUtils.transformer("X_BaseExprHead_10");
    private static final RellcTransformer TRANS_XBASE_EXPR_HEAD_11 = RellcUtils.transformer("X_BaseExprHead_11");
    private static final RellcTransformer TRANS_XTUPLE_EXPR_FIELD = RellcUtils.transformer("X_TupleExprField");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_13 = RellcUtils.transformer("X_CommaSeparated_13");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_14 = RellcUtils.transformer("X_CommaSeparated_14");
    private static final RellcTransformer TRANS_XTUPLE_EXPR = RellcUtils.transformer("X_TupleExpr");
    private static final RellcTransformer TRANS_XCREATE_EXPR_ARG = RellcUtils.transformer("X_CreateExprArg");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_25 = RellcUtils.transformer("X_CommaSeparated_25");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_26 = RellcUtils.transformer("X_CommaSeparated_26");
    private static final RellcTransformer TRANS_XCREATE_EXPR_ARGS = RellcUtils.transformer("X_CreateExprArgs");
    private static final RellcTransformer TRANS_XCREATE_EXPR = RellcUtils.transformer("X_CreateExpr");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_21 = RellcUtils.transformer("X_CommaSeparated_21");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_22 = RellcUtils.transformer("X_CommaSeparated_22");
    private static final RellcTransformer TRANS_XLIST_LITERAL_EXPR = RellcUtils.transformer("X_ListLiteralExpr");
    private static final RellcTransformer TRANS_XEMPTY_MAP_LITERAL_EXPR = RellcUtils.transformer("X_EmptyMapLiteralExpr");
    private static final RellcTransformer TRANS_XMAP_LITERAL_EXPR_ENTRY = RellcUtils.transformer("X_MapLiteralExprEntry");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_23 = RellcUtils.transformer("X_CommaSeparated_23");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_24 = RellcUtils.transformer("X_CommaSeparated_24");
    private static final RellcTransformer TRANS_XNON_EMPTY_MAP_LITERAL_EXPR = RellcUtils.transformer("X_NonEmptyMapLiteralExpr");
    private static final RellcTransformer TRANS_XMIRROR_STRUCT_EXPR = RellcUtils.transformer("X_MirrorStructExpr");
    private static final RellcTransformer TRANS_XVIRTUAL_TYPE_EXPR = RellcUtils.transformer("X_VirtualTypeExpr");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_SUBSCRIPT = RellcUtils.transformer("X_BaseExprTailSubscript");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_NOT_NULL = RellcUtils.transformer("X_BaseExprTailNotNull");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_SAFE_MEMBER = RellcUtils.transformer("X_BaseExprTailSafeMember");
    private static final RellcTransformer TRANS_XUNARY_POSTFIX_OPERATOR_0 = RellcUtils.transformer("X_UnaryPostfixOperator_0");
    private static final RellcTransformer TRANS_XUNARY_POSTFIX_OPERATOR_1 = RellcUtils.transformer("X_UnaryPostfixOperator_1");
    private static final RellcTransformer TRANS_XBASE_EXPR_TAIL_UNARY_POSTFIX_OP = RellcUtils.transformer("X_BaseExprTailUnaryPostfixOp");
    private static final RellcTransformer TRANS_XBASE_EXPR = RellcUtils.transformer("X_BaseExpr");
    private static final RellcTransformer TRANS_XIF_EXPR = RellcUtils.transformer("X_IfExpr");
    private static final RellcTransformer TRANS_XWHEN_CONDITION_EXPR = RellcUtils.transformer("X_WhenConditionExpr");
    private static final RellcTransformer TRANS_XWHEN_CONDITION_ELSE = RellcUtils.transformer("X_WhenConditionElse");
    private static final RellcTransformer TRANS_XWHEN_EXPR_CASE = RellcUtils.transformer("X_WhenExprCase");
    private static final RellcTransformer TRANS_XWHEN_EXPR_CASES = RellcUtils.transformer("X_WhenExprCases");
    private static final RellcTransformer TRANS_XWHEN_EXPR = RellcUtils.transformer("X_WhenExpr");
    private static final RellcTransformer TRANS_XUNARY_EXPR = RellcUtils.transformer("X_UnaryExpr");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_0 = RellcUtils.transformer("X_BinaryOperator_0");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_1 = RellcUtils.transformer("X_BinaryOperator_1");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_2 = RellcUtils.transformer("X_BinaryOperator_2");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_3 = RellcUtils.transformer("X_BinaryOperator_3");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_4 = RellcUtils.transformer("X_BinaryOperator_4");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_5 = RellcUtils.transformer("X_BinaryOperator_5");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_6 = RellcUtils.transformer("X_BinaryOperator_6");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_7 = RellcUtils.transformer("X_BinaryOperator_7");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_8 = RellcUtils.transformer("X_BinaryOperator_8");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_9 = RellcUtils.transformer("X_BinaryOperator_9");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_10 = RellcUtils.transformer("X_BinaryOperator_10");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_11 = RellcUtils.transformer("X_BinaryOperator_11");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_12 = RellcUtils.transformer("X_BinaryOperator_12");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_13 = RellcUtils.transformer("X_BinaryOperator_13");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_14 = RellcUtils.transformer("X_BinaryOperator_14");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_15 = RellcUtils.transformer("X_BinaryOperator_15");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_16 = RellcUtils.transformer("X_BinaryOperator_16");
    private static final RellcTransformer TRANS_XBINARY_OPERATOR_17 = RellcUtils.transformer("X_BinaryOperator_17");
    private static final RellcTransformer TRANS_XBINARY_EXPR_OPERAND = RellcUtils.transformer("X_BinaryExprOperand");
    private static final RellcTransformer TRANS_XEXPRESSION = RellcUtils.transformer("X_Expression");
    private static final RellcTransformer TRANS_XBASE_ATTRIBUTE_DEFINITION = RellcUtils.transformer("X_BaseAttributeDefinition");
    private static final RellcTransformer TRANS_XATTRIBUTE_CLAUSE = RellcUtils.transformer("X_AttributeClause");
    private static final RellcTransformer TRANS_XKEY_INDEX_KIND_0 = RellcUtils.transformer("X_KeyIndexKind_0");
    private static final RellcTransformer TRANS_XKEY_INDEX_KIND_1 = RellcUtils.transformer("X_KeyIndexKind_1");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_8 = RellcUtils.transformer("X_CommaSeparated_8");
    private static final RellcTransformer TRANS_XKEY_INDEX_CLAUSE = RellcUtils.transformer("X_KeyIndexClause");
    private static final RellcTransformer TRANS_XENTITY_BODY_SHORT = RellcUtils.transformer("X_EntityBodyShort");
    private static final RellcTransformer TRANS_XENTITY_DEF = RellcUtils.transformer("X_EntityDef");
    private static final RellcTransformer TRANS_XOBJECT_DEF = RellcUtils.transformer("X_ObjectDef");
    private static final RellcTransformer TRANS_XSTRUCT_KEYWORD_0 = RellcUtils.transformer("X_StructKeyword_0");
    private static final RellcTransformer TRANS_XSTRUCT_KEYWORD_1 = RellcUtils.transformer("X_StructKeyword_1");
    private static final RellcTransformer TRANS_XSTRUCT_DEF = RellcUtils.transformer("X_StructDef");
    private static final RellcTransformer TRANS_XENUM_VALUE = RellcUtils.transformer("X_EnumValue");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_11 = RellcUtils.transformer("X_CommaSeparated_11");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_12 = RellcUtils.transformer("X_CommaSeparated_12");
    private static final RellcTransformer TRANS_XENUM_DEF = RellcUtils.transformer("X_EnumDef");
    private static final RellcTransformer TRANS_XFORMAL_PARAMETER = RellcUtils.transformer("X_FormalParameter");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_35 = RellcUtils.transformer("X_CommaSeparated_35");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_36 = RellcUtils.transformer("X_CommaSeparated_36");
    private static final RellcTransformer TRANS_XFORMAL_PARAMETERS = RellcUtils.transformer("X_FormalParameters");
    private static final RellcTransformer TRANS_XFUNCTION_BODY_SHORT = RellcUtils.transformer("X_FunctionBodyShort");
    private static final RellcTransformer TRANS_XEMPTY_STMT = RellcUtils.transformer("X_EmptyStmt");
    private static final RellcTransformer TRANS_XVAR_VAL_0 = RellcUtils.transformer("X_VarVal_0");
    private static final RellcTransformer TRANS_XVAR_VAL_1 = RellcUtils.transformer("X_VarVal_1");
    private static final RellcTransformer TRANS_XSIMPLE_VAR_DECLARATOR = RellcUtils.transformer("X_SimpleVarDeclarator");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_29 = RellcUtils.transformer("X_CommaSeparated_29");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_30 = RellcUtils.transformer("X_CommaSeparated_30");
    private static final RellcTransformer TRANS_XTUPLE_VAR_DECLARATOR = RellcUtils.transformer("X_TupleVarDeclarator");
    private static final RellcTransformer TRANS_XVAR_STMT = RellcUtils.transformer("X_VarStmt");
    private static final RellcTransformer TRANS_XASSIGN_OP_0 = RellcUtils.transformer("X_AssignOp_0");
    private static final RellcTransformer TRANS_XASSIGN_OP_1 = RellcUtils.transformer("X_AssignOp_1");
    private static final RellcTransformer TRANS_XASSIGN_OP_2 = RellcUtils.transformer("X_AssignOp_2");
    private static final RellcTransformer TRANS_XASSIGN_OP_3 = RellcUtils.transformer("X_AssignOp_3");
    private static final RellcTransformer TRANS_XASSIGN_OP_4 = RellcUtils.transformer("X_AssignOp_4");
    private static final RellcTransformer TRANS_XASSIGN_OP_5 = RellcUtils.transformer("X_AssignOp_5");
    private static final RellcTransformer TRANS_XASSIGN_STMT = RellcUtils.transformer("X_AssignStmt");
    private static final RellcTransformer TRANS_XRETURN_STMT = RellcUtils.transformer("X_ReturnStmt");
    private static final RellcTransformer TRANS_XIF_STMT = RellcUtils.transformer("X_IfStmt");
    private static final RellcTransformer TRANS_XWHEN_STMT_CASE = RellcUtils.transformer("X_WhenStmtCase");
    private static final RellcTransformer TRANS_XWHEN_STMT = RellcUtils.transformer("X_WhenStmt");
    private static final RellcTransformer TRANS_XWHILE_STMT = RellcUtils.transformer("X_WhileStmt");
    private static final RellcTransformer TRANS_XFOR_STMT = RellcUtils.transformer("X_ForStmt");
    private static final RellcTransformer TRANS_XBREAK_STMT = RellcUtils.transformer("X_BreakStmt");
    private static final RellcTransformer TRANS_XCONTINUE_STMT = RellcUtils.transformer("X_ContinueStmt");
    private static final RellcTransformer TRANS_XUPDATE_FROM_SINGLE = RellcUtils.transformer("X_UpdateFromSingle");
    private static final RellcTransformer TRANS_XUPDATE_FROM_ITEM = RellcUtils.transformer("X_UpdateFromItem");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_31 = RellcUtils.transformer("X_CommaSeparated_31");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_32 = RellcUtils.transformer("X_CommaSeparated_32");
    private static final RellcTransformer TRANS_XUPDATE_FROM_MULTI = RellcUtils.transformer("X_UpdateFromMulti");
    private static final RellcTransformer TRANS_XUPDATE_TARGET_AT = RellcUtils.transformer("X_UpdateTargetAt");
    private static final RellcTransformer TRANS_XBASE_EXPR_NO_CALL_NO_AT = RellcUtils.transformer("X_BaseExprNoCallNoAt");
    private static final RellcTransformer TRANS_XUPDATE_TARGET_EXPR = RellcUtils.transformer("X_UpdateTargetExpr");
    private static final RellcTransformer TRANS_XUPDATE_WHAT_NAME_OP = RellcUtils.transformer("X_UpdateWhatNameOp");
    private static final RellcTransformer TRANS_XUPDATE_WHAT_EXPR = RellcUtils.transformer("X_UpdateWhatExpr");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_33 = RellcUtils.transformer("X_CommaSeparated_33");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_34 = RellcUtils.transformer("X_CommaSeparated_34");
    private static final RellcTransformer TRANS_XUPDATE_WHAT = RellcUtils.transformer("X_UpdateWhat");
    private static final RellcTransformer TRANS_XUPDATE_STMT = RellcUtils.transformer("X_UpdateStmt");
    private static final RellcTransformer TRANS_XDELETE_STMT = RellcUtils.transformer("X_DeleteStmt");
    private static final RellcTransformer TRANS_XINCREMENT_STMT = RellcUtils.transformer("X_IncrementStmt");
    private static final RellcTransformer TRANS_XCALL_STMT = RellcUtils.transformer("X_CallStmt");
    private static final RellcTransformer TRANS_XCREATE_STMT = RellcUtils.transformer("X_CreateStmt");
    private static final RellcTransformer TRANS_XGUARD_STMT = RellcUtils.transformer("X_GuardStmt");
    private static final RellcTransformer TRANS_XBLOCK_STMT = RellcUtils.transformer("X_BlockStmt");
    private static final RellcTransformer TRANS_XFUNCTION_BODY_FULL = RellcUtils.transformer("X_FunctionBodyFull");
    private static final RellcTransformer TRANS_XFUNCTION_BODY_NONE = RellcUtils.transformer("X_FunctionBodyNone");
    private static final RellcTransformer TRANS_XFUNCTION_DEF = RellcUtils.transformer("X_FunctionDef");
    private static final RellcTransformer TRANS_XNAMESPACE_DEF = RellcUtils.transformer("X_NamespaceDef");
    private static final RellcTransformer TRANS_XABSOLUTE_IMPORT_MODULE = RellcUtils.transformer("X_AbsoluteImportModule");
    private static final RellcTransformer TRANS_XRELATIVE_IMPORT_MODULE = RellcUtils.transformer("X_RelativeImportModule");
    private static final RellcTransformer TRANS_XUP_IMPORT_MODULE = RellcUtils.transformer("X_UpImportModule");
    private static final RellcTransformer TRANS_XIMPORT_TARGET_EXACT_ITEM = RellcUtils.transformer("X_ImportTargetExactItem");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_37 = RellcUtils.transformer("X_CommaSeparated_37");
    private static final RellcTransformer TRANS_XCOMMA_SEPARATED_38 = RellcUtils.transformer("X_CommaSeparated_38");
    private static final RellcTransformer TRANS_XIMPORT_TARGET_EXACT = RellcUtils.transformer("X_ImportTargetExact");
    private static final RellcTransformer TRANS_XIMPORT_TARGET_WILDCARD = RellcUtils.transformer("X_ImportTargetWildcard");
    private static final RellcTransformer TRANS_XIMPORT_DEF = RellcUtils.transformer("X_ImportDef");
    private static final RellcTransformer TRANS_XOP_DEF = RellcUtils.transformer("X_OpDef");
    private static final RellcTransformer TRANS_XQUERY_DEF = RellcUtils.transformer("X_QueryDef");
    private static final RellcTransformer TRANS_XINCLUDE_DEF = RellcUtils.transformer("X_IncludeDef");
    private static final RellcTransformer TRANS_XCONSTANT_DEF = RellcUtils.transformer("X_ConstantDef");
    private static final RellcTransformer TRANS_XANNOTATED_DEF = RellcUtils.transformer("X_AnnotatedDef");
    private static final RellcTransformer TRANS_XROOT_PARSER = RellcUtils.transformer("X_RootParser");

    public static Object process(AntlrToRellContext ctx, ParserRuleContext node) {
        if (node == null) return null;
        int ruleIndex = node.getRuleIndex();

        if (ruleIndex == RULE_ruleX_tkSTRING) {
            Object a = RellcUtils.tokenString(ctx, node);
            return RellcUtils.tuple(a);
        }

        switch (node.getRuleIndex()) {
            case RULE_ruleX_KeywordModifier0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                var keywordModifierCtx = ((RuleX_KeywordModifier0Context) node);
                RellcTransformer transformer = keywordModifierCtx.ruleX_Modifier_0() != null
                        ? TRANS_XKEYWORD_MODIFIER0_0
                        : TRANS_XKEYWORD_MODIFIER0_1;
                return transformer.transform(ctx, node, tup);
            }
            case RULE_ruleX_KeywordModifier: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_KeywordModifier0Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XKEYWORD_MODIFIER.transform(ctx, node, tup);
            }
            case RULE_ruleX_NameNode: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNAME_NODE.transform(ctx, node, tup);
            }
            case RULE_ruleX_Name: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNAME.transform(ctx, node, tup);
            }
            case RULE_ruleX_IntExpr: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XINT_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_BigIntExpr: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBIG_INT_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_DecimalExpr: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XDECIMAL_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_StringExpr: {
                Object var_0 = RellcUtils.tokenString(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XSTRING_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_BytesExpr: {
                Object var_0 = RellcUtils.tokenBytes(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBYTES_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_LiteralExpr_5: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XLITERAL_EXPR_5.transform(ctx, node, tup);
            }
            case RULE_ruleX_LiteralExpr_6: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XLITERAL_EXPR_6.transform(ctx, node, tup);
            }
            case RULE_ruleX_NullLiteralExpr: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNULL_LITERAL_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_AnnotationArgValue: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_LiteralExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XANNOTATION_ARG_VALUE.transform(ctx, node, tup);
            }
            case RULE_ruleX_QualifiedNameNode: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_NameNodeContext.class));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XQUALIFIED_NAME_NODE.transform(ctx, node, tup);
            }
            case RULE_ruleX_QualifiedName: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameNodeContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XQUALIFIED_NAME.transform(ctx, node, tup);
            }
            case RULE_ruleX_AnnotationArgName: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XANNOTATION_ARG_NAME.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_6: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AnnotationArgContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_6.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_7: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_6Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_7.transform(ctx, node, tup);
            }
            case RULE_ruleX_AnnotationArgs: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_7Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XANNOTATION_ARGS.transform(ctx, node, tup);
            }
            case RULE_ruleX_Annotation: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkATContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AnnotationArgsContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XANNOTATION.transform(ctx, node, tup);
            }
            case RULE_ruleX_Modifiers: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_ModifierContext.class));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XMODIFIERS.transform(ctx, node, tup);
            }
            case RULE_ruleX_ModuleHeader: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ModifiersContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkMODULEContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XMODULE_HEADER.transform(ctx, node, tup);
            }
            case RULE_ruleX_EntityKeyword_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XENTITY_KEYWORD_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_EntityKeyword_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XENTITY_KEYWORD_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_9: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_NameContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_9.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_10: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_9Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_10.transform(ctx, node, tup);
            }
            case RULE_ruleX_EntityAnnotations: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_10Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XENTITY_ANNOTATIONS.transform(ctx, node, tup);
            }
            case RULE_ruleX_ComplexNullableType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMPLEX_NULLABLE_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_4: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_TypeRefContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_4.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_5: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_4Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_5.transform(ctx, node, tup);
            }
            case RULE_ruleX_FunctionType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_5Context.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XFUNCTION_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_2: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_TypeRefContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_2.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_3: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_2Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_3.transform(ctx, node, tup);
            }
            case RULE_ruleX_GenericType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_3Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XGENERIC_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_NameType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNAME_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_TupleTypeField: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XTUPLE_TYPE_FIELD.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_0: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_TupleTypeFieldContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_1: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_0Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_TupleType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_1Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XTUPLE_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_VirtualType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkVIRTUALContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XVIRTUAL_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_MirrorStructType0: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkSTRUCTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkMUTABLEContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeRefContext.class, 0));
                return RellcUtils.tuple(var_0, var_1, var_2);
            }
            case RULE_ruleX_MirrorStructType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_MirrorStructType0Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XMIRROR_STRUCT_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_BasicType: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_PrimaryTypeContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_tkQUESTIONContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBASIC_TYPE.transform(ctx, node, tup);
            }
            case RULE_ruleX_NameTypeAttrHeader: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XNAME_TYPE_ATTR_HEADER.transform(ctx, node, tup);
            }
            case RULE_ruleX_AnonAttrHeader: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkQUESTIONContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XANON_ATTR_HEADER.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPrefixOperator_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_PREFIX_OPERATOR_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPrefixOperator_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_PREFIX_OPERATOR_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPrefixOperator_2: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_PREFIX_OPERATOR_2.transform(ctx, node, tup);
            }
            case RULE_ruleX_IncrementOperator_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XINCREMENT_OPERATOR_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_IncrementOperator_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XINCREMENT_OPERATOR_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPrefixOperator_3: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_IncrementOperatorContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_PREFIX_OPERATOR_3.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailMember: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_TAIL_MEMBER.transform(ctx, node, tup);
            }
            case RULE_ruleX_CallArgValue_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCALL_ARG_VALUE_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_CallArgValue_1: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCALL_ARG_VALUE_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_CallArg: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CallArgValueContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCALL_ARG.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_27: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_CallArgContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_27.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_28: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_27Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_28.transform(ctx, node, tup);
            }
            case RULE_ruleX_CallArgs: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_28Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCALL_ARGS.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailCall: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CallArgsContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_TAIL_CALL.transform(ctx, node, tup);
            }
            case RULE_ruleX_GenericTypeExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_GenericTypeContext.class, 0));

                // Manual fix: the following line is not generated by the grammar
                ParserRuleContext param_1 = ObjectUtils.firstNonNull(node.getRuleContext(RuleX_BaseExprTailMemberContext.class, 0),
                        node.getRuleContext(RuleX_BaseExprTailCallContext.class, 0));

                Object var_1 = RellcUtils.processObject(ctx, param_1);
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XGENERIC_TYPE_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprFromItem: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AnnotationContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XAT_EXPR_FROM_ITEM.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_15: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AtExprFromItemContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_15.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_16: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_15Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_16.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprFrom: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_16Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XAT_EXPR_FROM.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprAt_0: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkATContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkQUESTIONContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_AT_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprAt_1: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkATContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkMULContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_AT_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprAt_2: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkATContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkPLUSContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_AT_2.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprAt_3: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XAT_EXPR_AT_3.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_19: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_ExpressionRefContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_19.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_20: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLCURLContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_19Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_20.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprWhere: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_20Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XAT_EXPR_WHERE.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprWhatSimple: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkDOTContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_NameContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_WHAT_SIMPLE.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprWhatComplexItem: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AnnotationContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XAT_EXPR_WHAT_COMPLEX_ITEM.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_17: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AtExprWhatComplexItemContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_17.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_18: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_17Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_18.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprWhatComplex: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_18Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XAT_EXPR_WHAT_COMPLEX.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprOffset: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                return RellcUtils.tuple(var_0);
            }
            case RULE_ruleX_AtExprModifiers_0: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprOffsetContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_MODIFIERS_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExprLimit: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                return RellcUtils.tuple(var_0);
            }
            case RULE_ruleX_AtExprModifiers_1: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprLimitContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR_MODIFIERS_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailAt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprAtContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprWhereContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprWhatContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprModifiersContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XBASE_EXPR_TAIL_AT.transform(ctx, node, tup);
            }
            case RULE_ruleX_AtExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprFromContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprTailAtContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XAT_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_NameExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNAME_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_DollarExpr: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XDOLLAR_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_AttrExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkDOTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XATTR_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprHead_10: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_HEAD_10.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprHead_11: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_HEAD_11.transform(ctx, node, tup);
            }
            case RULE_ruleX_TupleExprField: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XTUPLE_EXPR_FIELD.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_13: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_TupleExprFieldContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_13.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_14: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_13Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_14.transform(ctx, node, tup);
            }
            case RULE_ruleX_TupleExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_14Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XTUPLE_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_CreateExprArg: {
                // Manual fix, generator used RuleX_tkDOTContext instead of RuleX_NameContext
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CallArgValueContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCREATE_EXPR_ARG.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_25: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_CreateExprArgContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_25.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_26: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_25Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_26.transform(ctx, node, tup);
            }
            case RULE_ruleX_CreateExprArgs: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_26Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCREATE_EXPR_ARGS.transform(ctx, node, tup);
            }
            case RULE_ruleX_CreateExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCREATEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CreateExprArgsContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XCREATE_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_21: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_ExpressionRefContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_21.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_22: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLBRACKContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_21Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_22.transform(ctx, node, tup);
            }
            case RULE_ruleX_ListLiteralExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_22Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XLIST_LITERAL_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_EmptyMapLiteralExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLBRACKContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XEMPTY_MAP_LITERAL_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_MapLiteralExprEntry: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                // Manual fix. Generator passed same argument twice, index was 0 for both var_0 and var_1
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 1));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XMAP_LITERAL_EXPR_ENTRY.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_23: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_MapLiteralExprEntryContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_23.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_24: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLBRACKContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_23Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_24.transform(ctx, node, tup);
            }
            case RULE_ruleX_NonEmptyMapLiteralExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_24Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XNON_EMPTY_MAP_LITERAL_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_MirrorStructExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_MirrorStructType0Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XMIRROR_STRUCT_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_VirtualTypeExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_VirtualTypeContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XVIRTUAL_TYPE_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailSubscript: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLBRACKContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBASE_EXPR_TAIL_SUBSCRIPT.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailNotNull: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_TAIL_NOT_NULL.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailSafeMember: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_TAIL_SAFE_MEMBER.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPostfixOperator_0: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_IncrementOperatorContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_POSTFIX_OPERATOR_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryPostfixOperator_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUNARY_POSTFIX_OPERATOR_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprTailUnaryPostfixOp: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UnaryPostfixOperatorContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBASE_EXPR_TAIL_UNARY_POSTFIX_OP.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprHeadContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_BaseExprTailContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBASE_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_IfExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkIFContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                // Manual fix. indices were all 0 for true and false clause
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 1));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 2));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XIF_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenConditionExpr: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_ExpressionRefContext.class));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XWHEN_CONDITION_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenConditionElse: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XWHEN_CONDITION_ELSE.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenExprCase: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_WhenConditionContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XWHEN_EXPR_CASE.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenExprCases: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_WhenExprCaseContext.class));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_tkSEMIContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XWHEN_EXPR_CASES.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkWHENContext.class, 0));
                // Manual fix: generator used RuleX_tkLPARContext instead of RuleX_ExpressionRefContext
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_WhenExprCasesContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XWHEN_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_UnaryExpr: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_UnaryPrefixOperatorContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_OperandExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XUNARY_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_2: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_2.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_3: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_3.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_4: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_4.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_5: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_5.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_6: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_6.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_7: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_7.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_8: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_8.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_9: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_9.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_10: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_10.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_11: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_11.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_12: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_12.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_13: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_13.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_14: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_14.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_15: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_15.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_16: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkINContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_16.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryOperator_17: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBINARY_OPERATOR_17.transform(ctx, node, tup);
            }
            case RULE_ruleX_BinaryExprOperand: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BinaryOperatorContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UnaryExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBINARY_EXPR_OPERAND.transform(ctx, node, tup);
            }
            case RULE_ruleX_Expression: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UnaryExprContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_BinaryExprOperandContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XEXPRESSION.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseAttributeDefinition: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkMUTABLEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AttrHeaderContext.class, 0));
                // Manual fix. generator puts assign token instead if expression ref
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XBASE_ATTRIBUTE_DEFINITION.transform(ctx, node, tup);
            }
            case RULE_ruleX_AttributeDefinition: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseAttributeDefinitionContext.class, 0));
                return RellcUtils.tuple(var_0);
            }
            case RULE_ruleX_AttributeClause: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AttributeDefinitionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XATTRIBUTE_CLAUSE.transform(ctx, node, tup);
            }
            case RULE_ruleX_KeyIndexKind_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XKEY_INDEX_KIND_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_KeyIndexKind_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XKEY_INDEX_KIND_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_8: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_BaseAttributeDefinitionContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_8.transform(ctx, node, tup);
            }
            case RULE_ruleX_KeyIndexClause: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_KeyIndexKindContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_8Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XKEY_INDEX_CLAUSE.transform(ctx, node, tup);
            }
            case RULE_ruleX_EntityBodyFull: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_RelClauseContext.class));
                return RellcUtils.tuple(var_0);
            }
            case RULE_ruleX_EntityBodyShort: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XENTITY_BODY_SHORT.transform(ctx, node, tup);
            }
            case RULE_ruleX_EntityDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_EntityKeywordContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_EntityAnnotationsContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_EntityBodyContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XENTITY_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_ObjectDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkOBJECTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AttributeClauseContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XOBJECT_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_StructKeyword_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XSTRUCT_KEYWORD_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_StructKeyword_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XSTRUCT_KEYWORD_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_StructDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_StructKeywordContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AttributeClauseContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XSTRUCT_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_EnumValue: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XENUM_VALUE.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_11: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_EnumValueContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_11.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_12: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLCURLContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_11Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_12.transform(ctx, node, tup);
            }
            case RULE_ruleX_EnumDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkENUMContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_12Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XENUM_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_FormalParameter: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AttrHeaderContext.class, 0));
                // Manual fix. generator used RuleX_tkASSIGNContext instead of RuleX_ExpressionContext
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XFORMAL_PARAMETER.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_35: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_FormalParameterContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_35.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_36: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_35Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_36.transform(ctx, node, tup);
            }
            case RULE_ruleX_FormalParameters: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_36Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XFORMAL_PARAMETERS.transform(ctx, node, tup);
            }
            case RULE_ruleX_FunctionBodyShort: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XFUNCTION_BODY_SHORT.transform(ctx, node, tup);
            }
            case RULE_ruleX_EmptyStmt: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XEMPTY_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_VarVal_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XVAR_VAL_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_VarVal_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XVAR_VAL_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_SimpleVarDeclarator: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AttrHeaderContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XSIMPLE_VAR_DECLARATOR.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_29: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_VarDeclaratorContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_29.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_30: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_29Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_30.transform(ctx, node, tup);
            }
            case RULE_ruleX_TupleVarDeclarator: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_30Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XTUPLE_VAR_DECLARATOR.transform(ctx, node, tup);
            }
            case RULE_ruleX_VarStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_VarValContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_VarDeclaratorContext.class, 0));

                // Manual fix, generator uses RuleX_tkASSIGNContext instead of RuleX_ExpressionContext
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));

                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XVAR_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_0: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_0.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_1: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_1.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_2: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_2.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_3: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_3.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_4: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_4.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignOp_5: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XASSIGN_OP_5.transform(ctx, node, tup);
            }
            case RULE_ruleX_AssignStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AssignOpContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XASSIGN_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_ReturnStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkRETURNContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XRETURN_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_IfStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkIFContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_StatementRefContext.class, 0));
                //Manual fix
                var elseContext = node.getRuleContext(RuleX_ElseStmtContext.class, 0);
                var elseStatementRef = elseContext != null ? elseContext.ruleX_StatementRef() : null;
                Object var_3 = RellcUtils.processObject(ctx, elseStatementRef);
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XIF_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenStmtCase: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_WhenConditionContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_StatementRefContext.class, 0));
                Object var_2 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_tkSEMIContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XWHEN_STMT_CASE.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhenStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkWHENContext.class, 0));
                // Manual fix. generator passed RuleX_tkLPARContext instead of RuleX_ExpressionRefContext
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object var_2 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_WhenStmtCaseContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XWHEN_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_WhileStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkWHILEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_StatementRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XWHILE_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_ForStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkFORContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_VarDeclaratorContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_StatementRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XFOR_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_BreakStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkBREAKContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XBREAK_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_ContinueStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCONTINUEContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCONTINUE_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateFromSingle: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUPDATE_FROM_SINGLE.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateFromItem: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XUPDATE_FROM_ITEM.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_31: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_UpdateFromItemContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_31.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_32: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_31Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_32.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateFromMulti: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_32Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUPDATE_FROM_MULTI.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateTargetAt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UpdateFromContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprAtContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AtExprWhereContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XUPDATE_TARGET_AT.transform(ctx, node, tup);
            }
            case RULE_ruleX_BaseExprNoCallNoAt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprHeadContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_BaseExprTailNoCallNoAtContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBASE_EXPR_NO_CALL_NO_AT.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateTargetExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprNoCallNoAtContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUPDATE_TARGET_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateWhatNameOp: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AssignOpContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XUPDATE_WHAT_NAME_OP.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateWhatExpr: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UpdateWhatNameOpContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XUPDATE_WHAT_EXPR.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_33: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_UpdateWhatExprContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_33.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_34: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLPARContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_33Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_34.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateWhat: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_34Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XUPDATE_WHAT.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpdateStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkUPDATEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UpdateTargetContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UpdateWhatContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XUPDATE_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_DeleteStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkDELETEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_UpdateTargetContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XDELETE_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_IncrementStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_IncrementOperatorContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XINCREMENT_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_CallStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BaseExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCALL_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_CreateStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CreateExprContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XCREATE_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_GuardStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkGUARDContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BlockStmtContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XGUARD_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_BlockStmt: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLCURLContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_StatementRefContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XBLOCK_STMT.transform(ctx, node, tup);
            }
            case RULE_ruleX_FunctionBodyFull: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BlockStmtContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XFUNCTION_BODY_FULL.transform(ctx, node, tup);
            }
            case RULE_ruleX_FunctionBodyNone: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XFUNCTION_BODY_NONE.transform(ctx, node, tup);
            }
            case RULE_ruleX_FunctionDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkFUNCTIONContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_FormalParametersContext.class, 0));
                // Manual fix. Generator used RuleX_tkCOLONContext instead of RuleX_TypeContext
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeContext.class, 0));
                Object var_4 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_FunctionBodyContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3, var_4);
                return TRANS_XFUNCTION_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_NamespaceDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkNAMESPACEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLCURLContext.class, 0));
                Object var_3 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AnnotatedDefContext.class));
                Object var_4 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkRCURLContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3, var_4);
                return TRANS_XNAMESPACE_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_AbsoluteImportModule: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XABSOLUTE_IMPORT_MODULE.transform(ctx, node, tup);
            }
            case RULE_ruleX_RelativeImportModule: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkDOTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XRELATIVE_IMPORT_MODULE.transform(ctx, node, tup);
            }
            case RULE_ruleX_UpImportModule: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_tkCARETContext.class));

                // Manual fix. generator was passing RuleX_tkDOTContext instead of RuleX_QualifiedNameContext
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameContext.class, 0));

                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XUP_IMPORT_MODULE.transform(ctx, node, tup);
            }
            case RULE_ruleX_ImportTargetExactItem: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameNodeContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QualifiedNameNodeContext.class, 0));
                // Manual fix. generator used RuleX_tkDOTContext instead of RuleX_tkMULContext
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkMULContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2);
                return TRANS_XIMPORT_TARGET_EXACT_ITEM.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_37: {
                Object var_0 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_ImportTargetExactItemContext.class));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOMMAContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_37.transform(ctx, node, tup);
            }
            case RULE_ruleX_CommaSeparated_38: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkLCURLContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_37Context.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XCOMMA_SEPARATED_38.transform(ctx, node, tup);
            }
            case RULE_ruleX_ImportTargetExact: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_CommaSeparated_38Context.class, 0));
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XIMPORT_TARGET_EXACT.transform(ctx, node, tup);
            }
            case RULE_ruleX_ImportTargetWildcard: {
                Object var_0 = RellcUtils.token(ctx, node);
                Object tup = RellcUtils.tuple(var_0);
                return TRANS_XIMPORT_TARGET_WILDCARD.transform(ctx, node, tup);
            }
            case RULE_ruleX_ImportTarget: {
                // Manual fix. taking first non-null value
                ParserRuleContext param_1 = ObjectUtils.firstNonNull(
                        node.getRuleContext(RuleX_ImportTargetExactContext.class, 0),
                        node.getRuleContext(RuleX_ImportTargetWildcardContext.class, 0));
                Object var_0 = RellcUtils.processObject(ctx, param_1);
                return RellcUtils.tuple(var_0);
            }
            case RULE_ruleX_ImportDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkIMPORTContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ImportModuleContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ImportTargetContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XIMPORT_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_OpDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkOPERATIONContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_FormalParametersContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_BlockStmtContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XOP_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_QueryDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkQUERYContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_FormalParametersContext.class, 0));
                // Manual fix. generator used RuleX_tkCOLONContext instead if RuleX_TypeContext
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_TypeContext.class, 0));
                Object var_4 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_QueryBodyContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3, var_4);
                return TRANS_XQUERY_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_IncludeDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkINCLUDEContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkSTRINGContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XINCLUDE_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_ConstantDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkVALContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_NameContext.class, 0));
                Object var_2 = RellcUtils.processObject(ctx, ((RuleX_ConstantDefContext) node).ruleX_TypeRef());
                //Object var_2 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_tkCOLONContext.class, 0));
                Object var_3 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ExpressionRefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1, var_2, var_3);
                return TRANS_XCONSTANT_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_AnnotatedDef: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ModifiersContext.class, 0));
                Object var_1 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_AnyDefContext.class, 0));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XANNOTATED_DEF.transform(ctx, node, tup);
            }
            case RULE_ruleX_RootParser: {
                Object var_0 = RellcUtils.processObject(ctx, node.getRuleContext(RuleX_ModuleHeaderContext.class, 0));
                Object var_1 = RellcUtils.processList(ctx, node.getRuleContexts(RuleX_AnnotatedDefContext.class));
                Object tup = RellcUtils.tuple(var_0, var_1);
                return TRANS_XROOT_PARSER.transform(ctx, node, tup);
            }
            default: {
                if (node.children != null) {
                    for (var child : node.children) {
                        if (child != null && !(child instanceof ErrorNode)) {
                            if (child instanceof TerminalNode) {
                                Object a = RellcUtils.token(ctx, node);
                                return RellcUtils.tuple(a);
                            } else {
                                return process(ctx, (ParserRuleContext) child);
                            }
                        }
                    }
                }
                // We get here when we expect a child but no child is defined.
                return null;
            }
        }
    }
}