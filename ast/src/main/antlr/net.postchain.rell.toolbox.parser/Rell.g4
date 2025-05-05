grammar Rell;

@header {
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
}

@parser::members {
    private static final int MAX_ALLOWED_DIGITS = 131072;
    private static final int MAX_ALLOWED_HEX_DIGITS = 999;
    private static final BigInteger MAX_VALUE = BigInteger.TEN.pow(MAX_ALLOWED_DIGITS);
    private static final BigInteger BIG_INTEGER_MAX_VALUE = MAX_VALUE.subtract(BigInteger.ONE);
    private static final BigInteger BIG_INTEGER_MIN_VALUE = MAX_VALUE.add(BigInteger.ONE).negate();
    private static final int DECIMAL_FRAC_DIGITS = 20;
    private static final int DECIMAL_PRECISION = MAX_ALLOWED_DIGITS + DECIMAL_FRAC_DIGITS;
    private static final BigDecimal DECIMAL_MIN_VALUE = BigDecimal.ONE.divide(BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS));
    private static final BigDecimal DECIMAL_MAX_VALUE = BigDecimal.TEN.pow(DECIMAL_PRECISION).subtract(BigDecimal.ONE)
          .divide(BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS));
    private static final int MAX_DECIMAL_LITERAL_LENGTH = 1000;
    private static final BigDecimal POSITIVE_MIN = BigDecimal.ONE.divide(BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS + 1));
    private static final BigDecimal NEGATIVE_MIN = POSITIVE_MIN.negate();
    private static final BigDecimal UPPER_LIMIT = BigDecimal.TEN.pow(MAX_ALLOWED_DIGITS);
    private static final BigDecimal LOWER_LIMIT = UPPER_LIMIT.negate();

    public boolean hasHexPrefix(String text) {
        return text.startsWith("0x");
    }

    public boolean isIntegerOutOfRange(String text) {
        try {
            boolean isHex = hasHexPrefix(text);
            if (isHex) {
                Long.parseLong(text.substring(2), 16);
            } else {
                Long.parseLong(text, 10);
            }
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public boolean isBigIntegerOutOfRange(String text) {
        if (text.endsWith("L")) {
    	    text = text.substring(0, text.length() - 1);
    	}
        boolean isHex = hasHexPrefix(text);
        if (text.length() > MAX_ALLOWED_HEX_DIGITS) {
            return true;
        }
        BigInteger parsedBigInteger = isHex ? new BigInteger(text.substring(2), 16) : new BigInteger(text, 10);
        return parsedBigInteger.compareTo(BIG_INTEGER_MAX_VALUE) == 1 ||
               parsedBigInteger.compareTo(BIG_INTEGER_MIN_VALUE) == -1;
    }

    public BigDecimal scale(BigDecimal v) {
        BigDecimal t = v;
        if (t.compareTo(NEGATIVE_MIN) >= 0 && t.compareTo(POSITIVE_MIN) <= 0) {
            return BigDecimal.ZERO;
        } else if (t.compareTo(LOWER_LIMIT) <= 0 || t.compareTo(UPPER_LIMIT) >= 0) {
            return null;
        }

        int scale = t.scale();
        if (scale > DECIMAL_FRAC_DIGITS) {
            t = v.setScale(DECIMAL_FRAC_DIGITS, RoundingMode.HALF_UP);
            if (t.compareTo(LOWER_LIMIT) <= 0 || t.compareTo(UPPER_LIMIT) >= 0) {
                return null;
            }
        }
        return t;
    }

    public boolean isDecimalOutOfRange(String text) {
        int len = text.length();
        if (len > MAX_DECIMAL_LITERAL_LENGTH) {
            return true;
        }
        BigDecimal parsedDecimal = null;
        try {
            parsedDecimal = new BigDecimal(text);
        } catch (NumberFormatException e) {
            return true;
        }
        return scale(parsedDecimal) == null;
    }
}

// Rule X_RootParser
ruleX_RootParser:
	ruleX_ModuleHeader
	?
	ruleX_AnnotatedDef
	*
	EOF
;

// Rule X_ModuleHeader
ruleX_ModuleHeader:
	ruleX_Modifiers
	ruleX_tkMODULE
	';'
;

// Rule X_Modifiers
ruleX_Modifiers:
	ruleX_Modifier
	*
;

// Rule X_Modifier
ruleX_Modifier:
	(
		ruleX_KeywordModifier
		    |
		ruleX_Annotation
	)
;

// Rule X_KeywordModifier
ruleX_KeywordModifier:
	ruleX_KeywordModifier0
;

// Rule X_KeywordModifier0
ruleX_KeywordModifier0:
	(
		ruleX_Modifier_0
		    |
		ruleX_Modifier_1
	)
;

ruleX_Modifier_0: 'abstract';
ruleX_Modifier_1: 'override';


// Rule X_Annotation
ruleX_Annotation:
	ruleX_tkAT
	ruleX_Name
	ruleX_AnnotationArgs
	?
;

// Rule X_Name
ruleX_Name:
	ruleX_NameNode
;

// Rule X_NameNode
ruleX_NameNode:
	RULE_ID
;

// Rule X_AnnotationArgs
ruleX_AnnotationArgs:
	ruleX_CommaSeparated_7
;

// Rule X_CommaSeparated_7
ruleX_CommaSeparated_7:
	ruleX_tkLPAR
	ruleX_CommaSeparated_6
	?
	ruleX_tkRPAR
;

// Rule X_tkLPAR
ruleX_tkLPAR:
	'('
;

// Rule X_CommaSeparated_6
ruleX_CommaSeparated_6:
	ruleX_AnnotationArg
	(
		','
		ruleX_AnnotationArg
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_AnnotationArg
ruleX_AnnotationArg:
	(
		ruleX_AnnotationArgValue
		    |
		ruleX_AnnotationArgName
	)
;

// Rule X_AnnotationArgValue
ruleX_AnnotationArgValue:
	ruleX_LiteralExpr
;

// Rule X_LiteralExpr
ruleX_LiteralExpr:
	(
		ruleX_IntExpr
		    |
		ruleX_BigIntExpr
		    |
		ruleX_DecimalExpr
		    |
		ruleX_StringExpr
		    |
		ruleX_BytesExpr
		    |
		ruleX_LiteralExpr_5
		    |
		ruleX_LiteralExpr_6
		    |
		ruleX_NullLiteralExpr
	)
;

ruleX_LiteralExpr_5: 'false';
ruleX_LiteralExpr_6: 'true';


// Rule X_IntExpr
ruleX_IntExpr:
	RULE_NUMBER { if (isIntegerOutOfRange($RULE_NUMBER.getText())) notifyErrorListeners("Integer literal out of range: " + $RULE_NUMBER.getText()); }
;

// Rule X_BigIntExpr
ruleX_BigIntExpr:
	RULE_BIG_INTEGER { if (isBigIntegerOutOfRange($RULE_BIG_INTEGER.getText())) notifyErrorListeners("Big integer literal too long: " + $RULE_BIG_INTEGER.getText()); }
;

// Rule X_DecimalExpr
ruleX_DecimalExpr:
	RULE_DECIMAL { if (isDecimalOutOfRange($RULE_DECIMAL.getText())) notifyErrorListeners("Decimal literal value out of range: " + $RULE_DECIMAL.getText()); }
;

// Rule X_StringExpr
ruleX_StringExpr:
	RULE_STRING
;

// Rule X_BytesExpr
ruleX_BytesExpr:
	RULE_BYTES
;

// Rule X_NullLiteralExpr
ruleX_NullLiteralExpr:
	'null'
;

// Rule X_AnnotationArgName
ruleX_AnnotationArgName:
	ruleX_QualifiedName
;

// Rule X_QualifiedName
ruleX_QualifiedName:
	ruleX_QualifiedNameNode
;

// Rule X_QualifiedNameNode
ruleX_QualifiedNameNode:
	ruleX_NameNode
	(
		'.'
		ruleX_NameNode
	)*
;

// Rule X_tkCOMMA
ruleX_tkCOMMA:
	','
;

// Rule X_tkRPAR
ruleX_tkRPAR:
	')'
;

// Rule X_tkMODULE
ruleX_tkMODULE:
	'module'
;

// Rule X_AnnotatedDef
ruleX_AnnotatedDef:
	ruleX_Modifiers
	ruleX_AnyDef
;

// Rule X_AnyDef
ruleX_AnyDef:
	(
		ruleX_EntityDef
		    |
		ruleX_ObjectDef
		    |
		ruleX_StructDef
		    |
		ruleX_EnumDef
		    |
		ruleX_FunctionDef
		    |
		ruleX_NamespaceDef
		    |
		ruleX_ImportDef
		    |
		ruleX_OpDef
		    |
		ruleX_QueryDef
		    |
		ruleX_IncludeDef
		    |
		ruleX_ConstantDef
	)
;

// Rule X_EntityDef
ruleX_EntityDef:
	ruleX_EntityKeyword
	ruleX_Name
	ruleX_EntityAnnotations
	?
	ruleX_EntityBody
	?
;

// Rule X_EntityKeyword
ruleX_EntityKeyword:
	(
		ruleX_EntityKeyword_0
		    |
		ruleX_EntityKeyword_1
	)
;
ruleX_EntityKeyword_0: 'entity';
ruleX_EntityKeyword_1: 'class';

// Rule X_EntityAnnotations
ruleX_EntityAnnotations:
	ruleX_CommaSeparated_10
;

// Rule X_CommaSeparated_10
ruleX_CommaSeparated_10:
	ruleX_tkLPAR
	ruleX_CommaSeparated_9
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_9
ruleX_CommaSeparated_9:
	ruleX_Name
	(
		','
		ruleX_Name
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_EntityBody
ruleX_EntityBody:
	(
		ruleX_EntityBodyFull
		    |
		ruleX_EntityBodyShort
	)
;

// Rule X_EntityBodyFull
ruleX_EntityBodyFull:
	'{'
	ruleX_RelClause
	*
	'}'
;

// Rule X_RelClause
ruleX_RelClause:
	(
		ruleX_AttributeClause
		    |
		ruleX_KeyIndexClause
	)
;

// Rule X_AttributeClause
ruleX_AttributeClause:
	ruleX_AttributeDefinition
;

// Rule X_AttributeDefinition
ruleX_AttributeDefinition:
	ruleX_BaseAttributeDefinition
	';'
;

// Rule X_BaseAttributeDefinition
ruleX_BaseAttributeDefinition:
	ruleX_tkMUTABLE
	?
	ruleX_AttrHeader
	(
		'='
		ruleX_ExpressionRef
	)?
;

// Rule X_tkMUTABLE
ruleX_tkMUTABLE:
	'mutable'
;

// Rule X_AttrHeader
ruleX_AttrHeader:
	(
		ruleX_NameTypeAttrHeader
		    |
		ruleX_AnonAttrHeader
	)
;

// Rule X_NameTypeAttrHeader
ruleX_NameTypeAttrHeader:
	ruleX_NameNode
	':'
	ruleX_Type
;

// Rule X_Type
ruleX_Type:
	(
		ruleX_ComplexNullableType
		    |
		ruleX_FunctionType
		    |
		ruleX_BasicType
	)
;

// Rule X_ComplexNullableType
ruleX_ComplexNullableType:
	ruleX_tkLPAR
	ruleX_TypeRef
	')'
	'?'
;

// Rule X_TypeRef
ruleX_TypeRef:
	ruleX_Type
;

// Rule X_FunctionType
ruleX_FunctionType:
	ruleX_CommaSeparated_5
	'->'
	ruleX_TypeRef
;

// Rule X_CommaSeparated_5
ruleX_CommaSeparated_5:
	ruleX_tkLPAR
	ruleX_CommaSeparated_4
	?
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_4
ruleX_CommaSeparated_4:
	ruleX_TypeRef
	(
		','
		ruleX_TypeRef
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_BasicType
ruleX_BasicType:
	ruleX_PrimaryType
	ruleX_tkQUESTION
	*
;

// Rule X_PrimaryType
ruleX_PrimaryType:
	(
		ruleX_GenericType
		    |
		ruleX_NameType
		    |
		ruleX_TupleType
		    |
		ruleX_VirtualType
		    |
		ruleX_MirrorStructType
	)
;

// Rule X_GenericType
ruleX_GenericType:
	ruleX_QualifiedName
	ruleX_CommaSeparated_3
;

// Rule X_CommaSeparated_3
ruleX_CommaSeparated_3:
	ruleX_tkLT
	ruleX_CommaSeparated_2
	ruleX_tkGT
;

// Rule X_tkLT
ruleX_tkLT:
	'<'
;

// Rule X_CommaSeparated_2
ruleX_CommaSeparated_2:
	ruleX_TypeRef
	(
		','
		ruleX_TypeRef
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_tkGT
ruleX_tkGT:
	'>'
;

// Rule X_NameType
ruleX_NameType:
	ruleX_QualifiedName
;

// Rule X_TupleType
ruleX_TupleType:
	ruleX_CommaSeparated_1
;

// Rule X_CommaSeparated_1
ruleX_CommaSeparated_1:
	ruleX_tkLPAR
	ruleX_CommaSeparated_0
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_0
ruleX_CommaSeparated_0:
	ruleX_TupleTypeField
	(
		','
		ruleX_TupleTypeField
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_TupleTypeField
ruleX_TupleTypeField:
	(
		ruleX_NameNode
		':'
	)?
	ruleX_TypeRef
;

// Rule X_VirtualType
ruleX_VirtualType:
	ruleX_tkVIRTUAL
	'<'
	ruleX_TypeRef
	'>'
;

// Rule X_tkVIRTUAL
ruleX_tkVIRTUAL:
	'virtual'
;

// Rule X_MirrorStructType
ruleX_MirrorStructType:
	ruleX_MirrorStructType0
;

// Rule X_MirrorStructType0
ruleX_MirrorStructType0:
	ruleX_tkSTRUCT
	'<'
	ruleX_tkMUTABLE
	?
	ruleX_TypeRef
	'>'
;

// Rule X_tkSTRUCT
ruleX_tkSTRUCT:
	'struct'
;

// Rule X_tkQUESTION
ruleX_tkQUESTION:
	'?'
;

// Rule X_AnonAttrHeader
ruleX_AnonAttrHeader:
	ruleX_QualifiedNameNode
	ruleX_tkQUESTION
	?
;

// Rule X_ExpressionRef
ruleX_ExpressionRef:
	ruleX_Expression
;

// Rule X_Expression
ruleX_Expression:
	ruleX_UnaryExpr
	ruleX_BinaryExprOperand
	*
;

// Rule X_UnaryExpr
ruleX_UnaryExpr:
	ruleX_UnaryPrefixOperator
	*
	ruleX_OperandExpr
;

// Rule X_UnaryPrefixOperator
ruleX_UnaryPrefixOperator:
	(
		ruleX_UnaryPrefixOperator_0
		    |
		ruleX_UnaryPrefixOperator_1
		    |
		ruleX_UnaryPrefixOperator_2
		    |
		ruleX_UnaryPrefixOperator_3
	)
;

ruleX_UnaryPrefixOperator_0: '+';
ruleX_UnaryPrefixOperator_1: '-';
ruleX_UnaryPrefixOperator_2: 'not';
ruleX_UnaryPrefixOperator_3: ruleX_IncrementOperator;

// Rule X_IncrementOperator
ruleX_IncrementOperator:
	(
		ruleX_IncrementOperator_0
		    |
		ruleX_IncrementOperator_1
	)
;

ruleX_IncrementOperator_0: '++';
ruleX_IncrementOperator_1: '--';

// Rule X_OperandExpr
ruleX_OperandExpr:
	(
		ruleX_BaseExpr
		    |
		ruleX_IfExpr
		    |
		ruleX_WhenExpr
	)
;

// Rule X_BaseExpr
ruleX_BaseExpr:
	ruleX_BaseExprHead
	ruleX_BaseExprTail
	*
;

// Rule X_BaseExprHead
ruleX_BaseExprHead:
	(
		ruleX_GenericTypeExpr
		    |
		ruleX_AtExpr
		    |
		ruleX_NameExpr
		    |
		ruleX_DollarExpr
		    |
		ruleX_AttrExpr
		    |
		ruleX_IntExpr
		    |
		ruleX_BigIntExpr
		    |
		ruleX_DecimalExpr
		    |
		ruleX_StringExpr
		    |
		ruleX_BytesExpr
		    |
		ruleX_BaseExprHead_10
		    |
		ruleX_BaseExprHead_11
		    |
		ruleX_NullLiteralExpr
		    |
		ruleX_TupleExpr
		    |
		ruleX_CreateExpr
		    |
		ruleX_ListLiteralExpr
		    |
		ruleX_EmptyMapLiteralExpr
		    |
		ruleX_NonEmptyMapLiteralExpr
		    |
		ruleX_MirrorStructExpr
		    |
		ruleX_VirtualTypeExpr
	)
;

ruleX_BaseExprHead_10: 'false';
ruleX_BaseExprHead_11: 'true';

// Rule X_GenericTypeExpr
ruleX_GenericTypeExpr:
	ruleX_GenericType
	(
		ruleX_BaseExprTailMember
		    |
		ruleX_BaseExprTailCall
	)
;

// Rule X_BaseExprTailMember
ruleX_BaseExprTailMember:
	'.'
	ruleX_Name
;

// Rule X_BaseExprTailCall
ruleX_BaseExprTailCall:
	ruleX_CallArgs
;

// Rule X_CallArgs
ruleX_CallArgs:
	ruleX_CommaSeparated_28
;

// Rule X_CommaSeparated_28
ruleX_CommaSeparated_28:
	ruleX_tkLPAR
	ruleX_CommaSeparated_27
	?
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_27
ruleX_CommaSeparated_27:
	ruleX_CallArg
	(
		','
		ruleX_CallArg
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_CallArg
ruleX_CallArg:
	(
		ruleX_Name
		'='
	)?
	ruleX_CallArgValue
;

// Rule X_CallArgValue
ruleX_CallArgValue:
	(
		ruleX_CallArgValue_0
		    |
		ruleX_CallArgValue_1
	)
;

ruleX_CallArgValue_0: '*';
ruleX_CallArgValue_1: ruleX_ExpressionRef;

// Rule X_AtExpr
ruleX_AtExpr:
	ruleX_AtExprFrom
	ruleX_BaseExprTailAt
;

// Rule X_AtExprFrom
ruleX_AtExprFrom:
	ruleX_CommaSeparated_16
;

// Rule X_CommaSeparated_16
ruleX_CommaSeparated_16:
	ruleX_tkLPAR
	ruleX_CommaSeparated_15
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_15
ruleX_CommaSeparated_15:
	ruleX_AtExprFromItem
	(
		','
		ruleX_AtExprFromItem
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_AtExprFromItem
ruleX_AtExprFromItem:
	ruleX_Annotation
	*
	(
		ruleX_NameNode
		':'
	)?
	ruleX_ExpressionRef
;

// Rule X_BaseExprTailAt
ruleX_BaseExprTailAt:
	ruleX_AtExprAt
	ruleX_AtExprWhere
	ruleX_AtExprWhat
	?
	ruleX_AtExprModifiers
	?
;

// Rule X_AtExprAt
ruleX_AtExprAt:
	(
    		ruleX_AtExprAt_0
    		    |
    		ruleX_AtExprAt_1
    		    |
    		ruleX_AtExprAt_2
    		    |
    		ruleX_AtExprAt_3
    )
;

ruleX_AtExprAt_0: ruleX_tkAT ruleX_tkQUESTION;
ruleX_AtExprAt_1: ruleX_tkAT ruleX_tkMUL;
ruleX_AtExprAt_2: ruleX_tkAT ruleX_tkPLUS;
ruleX_AtExprAt_3: '@';


// Rule X_tkAT
ruleX_tkAT:
	'@'
;

// Rule X_tkMUL
ruleX_tkMUL:
	'*'
;

// Rule X_tkPLUS
ruleX_tkPLUS:
	'+'
;

// Rule X_AtExprWhere
ruleX_AtExprWhere:
	ruleX_CommaSeparated_20
;

// Rule X_CommaSeparated_20
ruleX_CommaSeparated_20:
	ruleX_tkLCURL
	ruleX_CommaSeparated_19
	?
	ruleX_tkRCURL
;

// Rule X_tkLCURL
ruleX_tkLCURL:
	'{'
;

// Rule X_CommaSeparated_19
ruleX_CommaSeparated_19:
	ruleX_ExpressionRef
	(
		','
		ruleX_ExpressionRef
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_tkRCURL
ruleX_tkRCURL:
	'}'
;

// Rule X_AtExprWhat
ruleX_AtExprWhat:
	(
		ruleX_AtExprWhatSimple
		    |
		ruleX_AtExprWhatComplex
	)
;

// Rule X_AtExprWhatSimple
ruleX_AtExprWhatSimple:
	ruleX_tkDOT
	ruleX_Name
	(
		'.'
		ruleX_Name
	)*
;

// Rule X_tkDOT
ruleX_tkDOT:
	'.'
;

// Rule X_AtExprWhatComplex
ruleX_AtExprWhatComplex:
	ruleX_CommaSeparated_18
;

// Rule X_CommaSeparated_18
ruleX_CommaSeparated_18:
	ruleX_tkLPAR
	ruleX_CommaSeparated_17
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_17
ruleX_CommaSeparated_17:
	ruleX_AtExprWhatComplexItem
	(
		','
		ruleX_AtExprWhatComplexItem
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_AtExprWhatComplexItem
ruleX_AtExprWhatComplexItem:
	ruleX_AtExprWhatModifiers
	(
		ruleX_NameNode
		'='
	)?
	ruleX_ExpressionRef
;

// Rule X_AtExprWhatModifiers
ruleX_AtExprWhatModifiers:
	ruleX_Annotation
	*
;

// Rule X_AtExprOffset
ruleX_AtExprOffset:
	'offset'
	ruleX_ExpressionRef
;

// Rule X_AtExprLimit
ruleX_AtExprLimit:
	'limit'
	ruleX_ExpressionRef
;

// Rule X_NameExpr
ruleX_NameExpr:
	ruleX_Name
;

// Rule X_DollarExpr
ruleX_DollarExpr:
	'$'
;

// Rule X_AttrExpr
ruleX_AttrExpr:
	ruleX_tkDOT
	ruleX_Name
;

// Rule X_TupleExpr
ruleX_TupleExpr:
	ruleX_CommaSeparated_14
;

// Rule X_CommaSeparated_14
ruleX_CommaSeparated_14:
	ruleX_tkLPAR
	ruleX_CommaSeparated_13
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_13
ruleX_CommaSeparated_13:
	ruleX_TupleExprField
	(
		','
		ruleX_TupleExprField
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_TupleExprField
ruleX_TupleExprField:
	(
		ruleX_NameNode
		ruleX_tkASSIGN
	)?
	ruleX_ExpressionRef
;

// Rule X_tkASSIGN
ruleX_tkASSIGN:
	'='
;

// Rule X_CreateExpr
ruleX_CreateExpr:
	ruleX_tkCREATE
	ruleX_QualifiedName
	ruleX_CreateExprArgs
;

// Rule X_tkCREATE
ruleX_tkCREATE:
	'create'
;

// Rule X_CreateExprArgs
ruleX_CreateExprArgs:
	ruleX_CommaSeparated_26
;

// Rule X_CommaSeparated_26
ruleX_CommaSeparated_26:
	ruleX_tkLPAR
	ruleX_CommaSeparated_25
	?
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_25
ruleX_CommaSeparated_25:
	ruleX_CreateExprArg
	(
		','
		ruleX_CreateExprArg
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_CreateExprArg
ruleX_CreateExprArg:
	(
		'.'?
		ruleX_Name
		'='
	)?
	ruleX_CallArgValue
;

// Rule X_ListLiteralExpr
ruleX_ListLiteralExpr:
	ruleX_CommaSeparated_22
;

// Rule X_CommaSeparated_22
ruleX_CommaSeparated_22:
	ruleX_tkLBRACK
	ruleX_CommaSeparated_21
	?
	ruleX_tkRBRACK
;

// Rule X_tkLBRACK
ruleX_tkLBRACK:
	'['
;

// Rule X_CommaSeparated_21
ruleX_CommaSeparated_21:
	ruleX_ExpressionRef
	(
		','
		ruleX_ExpressionRef
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_tkRBRACK
ruleX_tkRBRACK:
	']'
;

// Rule X_EmptyMapLiteralExpr
ruleX_EmptyMapLiteralExpr:
	ruleX_tkLBRACK
	':'
	']'
;

// Rule X_NonEmptyMapLiteralExpr
ruleX_NonEmptyMapLiteralExpr:
	ruleX_CommaSeparated_24
;

// Rule X_CommaSeparated_24
ruleX_CommaSeparated_24:
	ruleX_tkLBRACK
	ruleX_CommaSeparated_23
	ruleX_tkRBRACK
;

// Rule X_CommaSeparated_23
ruleX_CommaSeparated_23:
	ruleX_MapLiteralExprEntry
	(
		','
		ruleX_MapLiteralExprEntry
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_MapLiteralExprEntry
ruleX_MapLiteralExprEntry:
	ruleX_ExpressionRef
	':'
	ruleX_ExpressionRef
;

// Rule X_MirrorStructExpr
ruleX_MirrorStructExpr:
	ruleX_MirrorStructType0
;

// Rule X_VirtualTypeExpr
ruleX_VirtualTypeExpr:
	ruleX_VirtualType
;

// Rule X_BaseExprTail
ruleX_BaseExprTail:
	(
		ruleX_BaseExprTailMember
		    |
		ruleX_BaseExprTailSubscript
		    |
		ruleX_BaseExprTailNotNull
		    |
		ruleX_BaseExprTailSafeMember
		    |
		ruleX_BaseExprTailUnaryPostfixOp
		    |
		ruleX_BaseExprTailCall
		    |
		ruleX_BaseExprTailAt
	)
;

// Rule X_BaseExprTailSubscript
ruleX_BaseExprTailSubscript:
	ruleX_tkLBRACK
	ruleX_ExpressionRef
	']'
;

// Rule X_BaseExprTailNotNull
ruleX_BaseExprTailNotNull:
	'!!'
;

// Rule X_BaseExprTailSafeMember
ruleX_BaseExprTailSafeMember:
	'?.'
	ruleX_Name
;

// Rule X_BaseExprTailUnaryPostfixOp
ruleX_BaseExprTailUnaryPostfixOp:
	ruleX_UnaryPostfixOperator
;

// Rule X_UnaryPostfixOperator
ruleX_UnaryPostfixOperator:
	(
		ruleX_UnaryPostfixOperator_0
		    |
		ruleX_UnaryPostfixOperator_1
	)
;

ruleX_UnaryPostfixOperator_0: ruleX_IncrementOperator;
ruleX_UnaryPostfixOperator_1: '??';

// Rule X_AtExprModifiers
ruleX_AtExprModifiers:
	(
        ruleX_AtExprModifiers_0
		    |
		ruleX_AtExprModifiers_1
	)
;

ruleX_AtExprModifiers_0: 'limit' ruleX_ExpressionRef ruleX_AtExprOffset ?;
ruleX_AtExprModifiers_1: 'offset' ruleX_ExpressionRef ruleX_AtExprLimit?;

// Rule X_IfExpr
ruleX_IfExpr:
	ruleX_tkIF
	'('
	ruleX_ExpressionRef
	')'
	ruleX_ExpressionRef
	'else'
	ruleX_ExpressionRef
;

// Rule X_tkIF
ruleX_tkIF:
	'if'
;

// Rule X_WhenExpr
ruleX_WhenExpr:
	ruleX_tkWHEN
	(
		'('
		ruleX_ExpressionRef
		')'
	)?
	'{'
	ruleX_WhenExprCase
	(
		';'
		ruleX_WhenExprCase
	)*
	';'?
	'}'
;

// Rule X_tkWHEN
ruleX_tkWHEN:
	'when'
;

// Rule X_WhenExprCase
ruleX_WhenExprCase:
	ruleX_WhenCondition
	'->'
	ruleX_ExpressionRef
;

// Rule X_WhenCondition
ruleX_WhenCondition:
	(
		ruleX_WhenConditionExpr
		    |
		ruleX_WhenConditionElse
	)
;

// Rule X_WhenConditionExpr
ruleX_WhenConditionExpr:
	ruleX_ExpressionRef
	(
		','
		ruleX_ExpressionRef
	)*
	','?
;

// Rule X_WhenConditionElse
ruleX_WhenConditionElse:
	'else'
;

// Rule X_tkSEMI
ruleX_tkSEMI:
	';'
;

// Rule X_BinaryExprOperand
ruleX_BinaryExprOperand:
	ruleX_BinaryOperator
	ruleX_UnaryExpr
;

// Rule X_BinaryOperator
ruleX_BinaryOperator:
	(
		ruleX_BinaryOperator_0
		    |
		ruleX_BinaryOperator_1
		    |
		ruleX_BinaryOperator_2
		    |
		ruleX_BinaryOperator_3
		    |
		ruleX_BinaryOperator_4
		    |
		ruleX_BinaryOperator_5
		    |
		ruleX_BinaryOperator_6
		    |
		ruleX_BinaryOperator_7
		    |
		ruleX_BinaryOperator_8
		    |
		ruleX_BinaryOperator_9
		    |
		ruleX_BinaryOperator_10
		    |
		ruleX_BinaryOperator_11
		    |
		ruleX_BinaryOperator_12
		    |
		ruleX_BinaryOperator_13
		    |
		ruleX_BinaryOperator_14
		    |
		ruleX_BinaryOperator_15
		    |
		ruleX_BinaryOperator_16
		    |
		ruleX_BinaryOperator_17
	)
;

ruleX_BinaryOperator_0: '==';
ruleX_BinaryOperator_1: '!=';
ruleX_BinaryOperator_2: '<=';
ruleX_BinaryOperator_3: '>=';
ruleX_BinaryOperator_4: '<';
ruleX_BinaryOperator_5: '>';
ruleX_BinaryOperator_6: '===';
ruleX_BinaryOperator_7: '!==';
ruleX_BinaryOperator_8: '+';
ruleX_BinaryOperator_9: '-';
ruleX_BinaryOperator_10: '*';
ruleX_BinaryOperator_11: '/';
ruleX_BinaryOperator_12: '%';
ruleX_BinaryOperator_13: 'and';
ruleX_BinaryOperator_14: 'or';
ruleX_BinaryOperator_15: 'in';
ruleX_BinaryOperator_16: 'not' ruleX_tkIN;
ruleX_BinaryOperator_17: '?:';

// Rule X_tkIN
ruleX_tkIN:
	'in'
;

// Rule X_KeyIndexClause
ruleX_KeyIndexClause:
	ruleX_KeyIndexKind
	ruleX_CommaSeparated_8
	';'
;

// Rule X_CommaSeparated_8
ruleX_CommaSeparated_8:
	ruleX_BaseAttributeDefinition
	(
		','
		ruleX_BaseAttributeDefinition
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_KeyIndexKind
ruleX_KeyIndexKind:
	(
		ruleX_KeyIndexKind_0
		    |
		ruleX_KeyIndexKind_1
	)
;

ruleX_KeyIndexKind_0: 'key';
ruleX_KeyIndexKind_1: 'index';

// Rule X_EntityBodyShort
ruleX_EntityBodyShort:
	';'
;

// Rule X_ObjectDef
ruleX_ObjectDef:
	ruleX_tkOBJECT
	ruleX_Name
	'{'
	ruleX_AttributeClause
	*
	'}'
;

// Rule X_tkOBJECT
ruleX_tkOBJECT:
	'object'
;

// Rule X_StructDef
ruleX_StructDef:
	ruleX_StructKeyword
	ruleX_Name
	'{'
	ruleX_AttributeClause
	*
	'}'
;

// Rule X_StructKeyword
ruleX_StructKeyword:
	(
		ruleX_StructKeyword_0
		    |
		ruleX_StructKeyword_1
	)
;

ruleX_StructKeyword_0: 'struct';
ruleX_StructKeyword_1: 'record';

// Rule X_EnumDef
ruleX_EnumDef:
	ruleX_tkENUM
	ruleX_Name
	ruleX_CommaSeparated_12
;

// Rule X_tkENUM
ruleX_tkENUM:
	'enum'
;

// Rule X_CommaSeparated_12
ruleX_CommaSeparated_12:
	ruleX_tkLCURL
	ruleX_CommaSeparated_11
	?
	ruleX_tkRCURL
;

// Rule X_CommaSeparated_11
ruleX_CommaSeparated_11:
	ruleX_EnumValue
	(
		','
		ruleX_EnumValue
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_EnumValue
ruleX_EnumValue:
	ruleX_NameNode
;

// Rule X_FunctionDef
ruleX_FunctionDef:
	ruleX_tkFUNCTION
	ruleX_QualifiedName
	?
	ruleX_FormalParameters
	(
		':'
		ruleX_Type
	)?
	ruleX_FunctionBody
;

// Rule X_tkFUNCTION
ruleX_tkFUNCTION:
	'function'
;

// Rule X_FormalParameters
ruleX_FormalParameters:
	ruleX_CommaSeparated_36
;

// Rule X_CommaSeparated_36
ruleX_CommaSeparated_36:
	ruleX_tkLPAR
	ruleX_CommaSeparated_35
	?
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_35
ruleX_CommaSeparated_35:
	ruleX_FormalParameter
	(
		','
		ruleX_FormalParameter
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_FormalParameter
ruleX_FormalParameter:
	ruleX_AttrHeader
	(
		'='
		ruleX_Expression
	)?
;

// Rule X_FunctionBody
ruleX_FunctionBody:
	(
		ruleX_FunctionBodyShort
		    |
		ruleX_FunctionBodyFull
		    |
		ruleX_FunctionBodyNone
	)
;

// Rule X_FunctionBodyShort
ruleX_FunctionBodyShort:
	ruleX_tkASSIGN
	ruleX_Expression
	ruleX_tkSEMI
;


// Rule X_FunctionBodyFull
ruleX_FunctionBodyFull:
	ruleX_BlockStmt
;

// Rule X_BlockStmt
ruleX_BlockStmt:
	ruleX_tkLCURL
	ruleX_StatementRef
	*
	ruleX_tkRCURL
;

// Rule X_StatementRef
ruleX_StatementRef:
	ruleX_Statement
;

// Rule X_Statement
ruleX_Statement:
	(
		ruleX_EmptyStmt
		    |
		ruleX_VarStmt
		    |
		ruleX_AssignStmt
		    |
		ruleX_ReturnStmt
		    |
		ruleX_BlockStmt
		    |
		ruleX_IfStmt
		    |
		ruleX_WhenStmt
		    |
		ruleX_WhileStmt
		    |
		ruleX_ForStmt
		    |
		ruleX_BreakStmt
		    |
		ruleX_ContinueStmt
		    |
		ruleX_UpdateStmt
		    |
		ruleX_DeleteStmt
		    |
		ruleX_IncrementStmt
		    |
		ruleX_CallStmt
		    |
		ruleX_CreateStmt
		    |
		ruleX_GuardStmt
	)
;

// Rule X_EmptyStmt
ruleX_EmptyStmt:
	';'
;

// Rule X_VarStmt
ruleX_VarStmt:
	ruleX_VarVal
	ruleX_VarDeclarator
	(
		'='
		ruleX_Expression
	)?
	ruleX_tkSEMI
;

// Rule X_VarVal
ruleX_VarVal:
	(
		ruleX_VarVal_0
		    |
		ruleX_VarVal_1
	)
;

ruleX_VarVal_0: 'val';
ruleX_VarVal_1: 'var';

// Rule X_VarDeclarator
ruleX_VarDeclarator:
	(
		ruleX_SimpleVarDeclarator
		    |
		ruleX_TupleVarDeclarator
	)
;

// Rule X_SimpleVarDeclarator
ruleX_SimpleVarDeclarator:
	ruleX_AttrHeader
;

// Rule X_TupleVarDeclarator
ruleX_TupleVarDeclarator:
	ruleX_CommaSeparated_30
;

// Rule X_CommaSeparated_30
ruleX_CommaSeparated_30:
	ruleX_tkLPAR
	ruleX_CommaSeparated_29
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_29
ruleX_CommaSeparated_29:
	ruleX_VarDeclarator
	(
		','
		ruleX_VarDeclarator
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_AssignStmt
ruleX_AssignStmt:
	ruleX_BaseExpr
	ruleX_AssignOp
	ruleX_Expression
	ruleX_tkSEMI
;

// Rule X_AssignOp
ruleX_AssignOp:
	(
		ruleX_AssignOp_0
		    |
		ruleX_AssignOp_1
		    |
		ruleX_AssignOp_2
		    |
		ruleX_AssignOp_3
		    |
		ruleX_AssignOp_4
		    |
		ruleX_AssignOp_5
	)
;

ruleX_AssignOp_0: '=';
ruleX_AssignOp_1: '+=';
ruleX_AssignOp_2: '-=';
ruleX_AssignOp_3: '*=';
ruleX_AssignOp_4: '/=';
ruleX_AssignOp_5: '%=';

// Rule X_ReturnStmt
ruleX_ReturnStmt:
	ruleX_tkRETURN
	ruleX_Expression
	?
	ruleX_tkSEMI
;

// Rule X_tkRETURN
ruleX_tkRETURN:
	'return'
;

// Rule X_IfStmt
ruleX_IfStmt:
	ruleX_tkIF
	'('
	ruleX_Expression
	')'
	ruleX_StatementRef
	ruleX_ElseStmt?
;

ruleX_ElseStmt: ruleX_tkELSE ruleX_StatementRef;
ruleX_tkELSE: 'else';

// Rule X_WhenStmt
ruleX_WhenStmt:
	ruleX_tkWHEN
	(
		'('
		ruleX_ExpressionRef
		')'
	)?
	'{'
	ruleX_WhenStmtCase
	*
	ruleX_tkRCURL
;

// Rule X_WhenStmtCase
ruleX_WhenStmtCase:
	ruleX_WhenCondition
	'->'
	ruleX_StatementRef
	ruleX_tkSEMI?
;

// Rule X_WhileStmt
ruleX_WhileStmt:
	ruleX_tkWHILE
	'('
	ruleX_Expression
	')'
	ruleX_StatementRef
;

// Rule X_tkWHILE
ruleX_tkWHILE:
	'while'
;

// Rule X_ForStmt
ruleX_ForStmt:
	ruleX_tkFOR
	'('
	ruleX_VarDeclarator
	'in'
	ruleX_Expression
	ruleX_tkRPAR
	ruleX_StatementRef
;

// Rule X_tkFOR
ruleX_tkFOR:
	'for'
;

// Rule X_BreakStmt
ruleX_BreakStmt:
	ruleX_tkBREAK
	ruleX_tkSEMI
;

// Rule X_tkBREAK
ruleX_tkBREAK:
	'break'
;

// Rule X_ContinueStmt
ruleX_ContinueStmt:
	ruleX_tkCONTINUE
	ruleX_tkSEMI
;

// Rule X_tkCONTINUE
ruleX_tkCONTINUE:
	'continue'
;

// Rule X_UpdateStmt
ruleX_UpdateStmt:
	ruleX_tkUPDATE
	ruleX_UpdateTarget
	ruleX_UpdateWhat
	ruleX_tkSEMI
;

// Rule X_tkUPDATE
ruleX_tkUPDATE:
	'update'
;

// Rule X_UpdateTarget
ruleX_UpdateTarget:
	(
		ruleX_UpdateTargetAt
		    |
		ruleX_UpdateTargetExpr
	)
;

// Rule X_UpdateTargetAt
ruleX_UpdateTargetAt:
	ruleX_UpdateFrom
	ruleX_AtExprAt
	ruleX_AtExprWhere
;

// Rule X_UpdateFrom
ruleX_UpdateFrom:
	(
		ruleX_UpdateFromSingle
		    |
		ruleX_UpdateFromMulti
	)
;

// Rule X_UpdateFromSingle
ruleX_UpdateFromSingle:
	ruleX_QualifiedName
;

// Rule X_UpdateFromMulti
ruleX_UpdateFromMulti:
	ruleX_CommaSeparated_32
;

// Rule X_CommaSeparated_32
ruleX_CommaSeparated_32:
	ruleX_tkLPAR
	ruleX_CommaSeparated_31
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_31
ruleX_CommaSeparated_31:
	ruleX_UpdateFromItem
	(
		','
		ruleX_UpdateFromItem
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_UpdateFromItem
ruleX_UpdateFromItem:
	(
		ruleX_NameNode
		':'
	)?
	ruleX_QualifiedName
;

// Rule X_UpdateTargetExpr
ruleX_UpdateTargetExpr:
	ruleX_BaseExprNoCallNoAt
;

// Rule X_BaseExprNoCallNoAt
ruleX_BaseExprNoCallNoAt:
	ruleX_BaseExprHead
	ruleX_BaseExprTailNoCallNoAt
	*
;

// Rule X_BaseExprTailNoCallNoAt
ruleX_BaseExprTailNoCallNoAt:
	(
		ruleX_BaseExprTailMember
		    |
		ruleX_BaseExprTailSubscript
		    |
		ruleX_BaseExprTailNotNull
		    |
		ruleX_BaseExprTailSafeMember
		    |
		ruleX_BaseExprTailUnaryPostfixOp
	)
;

// Rule X_UpdateWhat
ruleX_UpdateWhat:
	ruleX_CommaSeparated_34
;

// Rule X_CommaSeparated_34
ruleX_CommaSeparated_34:
	ruleX_tkLPAR
	ruleX_CommaSeparated_33
	ruleX_tkRPAR
;

// Rule X_CommaSeparated_33
ruleX_CommaSeparated_33:
	ruleX_UpdateWhatExpr
	(
		','
		ruleX_UpdateWhatExpr
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_UpdateWhatExpr
ruleX_UpdateWhatExpr:
	ruleX_UpdateWhatNameOp
	?
	ruleX_Expression
;

// Rule X_UpdateWhatNameOp
ruleX_UpdateWhatNameOp:
	'.'?
	ruleX_Name
	ruleX_AssignOp
;

// Rule X_DeleteStmt
ruleX_DeleteStmt:
	ruleX_tkDELETE
	ruleX_UpdateTarget
	ruleX_tkSEMI
;

// Rule X_tkDELETE
ruleX_tkDELETE:
	'delete'
;

// Rule X_IncrementStmt
ruleX_IncrementStmt:
	ruleX_IncrementOperator
	ruleX_BaseExpr
	ruleX_tkSEMI
;

// Rule X_CallStmt
ruleX_CallStmt:
	ruleX_BaseExpr
	ruleX_tkSEMI
;

// Rule X_CreateStmt
ruleX_CreateStmt:
	ruleX_CreateExpr
	ruleX_tkSEMI
;

// Rule X_GuardStmt
ruleX_GuardStmt:
	ruleX_tkGUARD
	ruleX_BlockStmt
;

// Rule X_tkGUARD
ruleX_tkGUARD:
	'guard'
;

// Rule X_FunctionBodyNone
ruleX_FunctionBodyNone:
	';'
;

// Rule X_NamespaceDef
ruleX_NamespaceDef:
	ruleX_tkNAMESPACE
	ruleX_QualifiedName
	?
	ruleX_tkLCURL
	ruleX_AnnotatedDef
	*
	ruleX_tkRCURL
;

// Rule X_tkNAMESPACE
ruleX_tkNAMESPACE:
	'namespace'
;

// Rule X_ImportDef
ruleX_ImportDef:
	ruleX_tkIMPORT
	(
		ruleX_Name
		':'
	)?
	ruleX_ImportModule
	ruleX_ImportTarget
	?
	';'
;

// Rule X_tkIMPORT
ruleX_tkIMPORT:
	'import'
;

// Rule X_ImportModule
ruleX_ImportModule:
	(
		ruleX_AbsoluteImportModule
		    |
		ruleX_RelativeImportModule
		    |
		ruleX_UpImportModule
	)
;

// Rule X_AbsoluteImportModule
ruleX_AbsoluteImportModule:
	ruleX_QualifiedName
;

// Rule X_RelativeImportModule
ruleX_RelativeImportModule:
	ruleX_tkDOT
	ruleX_QualifiedName
	?
;

// Rule X_UpImportModule
ruleX_UpImportModule:
	ruleX_tkCARET
	+
	(
		'.'
		ruleX_QualifiedName
	)?
;

// Rule X_tkCARET
ruleX_tkCARET:
	'^'
;

// Rule X_ImportTarget
ruleX_ImportTarget:
	'.'
	(
		ruleX_ImportTargetExact
		    |
		ruleX_ImportTargetWildcard
	)
;

// Rule X_ImportTargetExact
ruleX_ImportTargetExact:
	ruleX_CommaSeparated_38
;

// Rule X_CommaSeparated_38
ruleX_CommaSeparated_38:
	ruleX_tkLCURL
	ruleX_CommaSeparated_37
	ruleX_tkRCURL
;

// Rule X_CommaSeparated_37
ruleX_CommaSeparated_37:
	ruleX_ImportTargetExactItem
	(
		','
		ruleX_ImportTargetExactItem
	)*
	ruleX_tkCOMMA
	?
;

// Rule X_ImportTargetExactItem
ruleX_ImportTargetExactItem:
	(
		ruleX_NameNode
		':'
	)?
	ruleX_QualifiedNameNode
	(
		'.'
		ruleX_tkMUL
	)?
;

// Rule X_ImportTargetWildcard
ruleX_ImportTargetWildcard:
	'*'
;

// Rule X_OpDef
ruleX_OpDef:
	ruleX_tkOPERATION
	ruleX_Name
	ruleX_FormalParameters
	ruleX_BlockStmt
;

// Rule X_tkOPERATION
ruleX_tkOPERATION:
	'operation'
;

// Rule X_QueryDef
ruleX_QueryDef:
	ruleX_tkQUERY
	ruleX_Name
	ruleX_FormalParameters
	(
		':'
		ruleX_Type
	)?
	ruleX_QueryBody
;

// Rule X_tkQUERY
ruleX_tkQUERY:
	'query'
;

// Rule X_QueryBody
ruleX_QueryBody:
	(
		ruleX_FunctionBodyShort
		    |
		ruleX_FunctionBodyFull
	)
;

// Rule X_IncludeDef
ruleX_IncludeDef:
	ruleX_tkINCLUDE
	ruleX_tkSTRING
	';'
;

// Rule X_tkINCLUDE
ruleX_tkINCLUDE:
	'include'
;

// Rule X_tkSTRING
ruleX_tkSTRING:
	RULE_STRING
;

// Rule X_ConstantDef
ruleX_ConstantDef:
	ruleX_tkVAL
	ruleX_Name
	(
		':'
		ruleX_TypeRef
	)?
	'='
	ruleX_ExpressionRef
	';'
;

// Rule X_tkVAL
ruleX_tkVAL:
	'val'
;

//Sets comment to hardcorded channel 2, since HIDDEN default gets value 1.
//We also set this value in RellCustomTokenChannels
//Do introduce channel names properly we would need to separate parser and lexer into
//two different g4 files:
//https://github.com/antlr/antlr4/issues/1555
//https://stackoverflow.com/questions/28197609/extra-channels-in-antlr-4-5
RULE_ML_COMMENT : '/*' .*? '*/'    -> channel(2);
RULE_SL_COMMENT : '//' ~[\r\n]*    -> channel(2);

//RULE_WS : (' '|'\t'|'\r'|'\n')+ {skip();};
RULE_WS : (' '|'\t'|'\r'|'\n')+ -> channel(HIDDEN);

RULE_ID : ('A'..'Z'|'a'..'z'|'_') ('A'..'Z'|'a'..'z'|'_'|'0'..'9')*;

fragment RULE_DECNUM : ('0'..'9')+;

fragment RULE_EXPONENT : ('E'|'e') ('+'|'-')? RULE_DECNUM;

RULE_DECIMAL : (RULE_DECNUM? '.' RULE_DECNUM RULE_EXPONENT?|RULE_DECNUM RULE_EXPONENT);

fragment RULE_HEXDIG : ('0'..'9'|'A'..'F'|'a'..'f');

fragment RULE_COMMON_INT : (RULE_DECNUM|'0' 'x' RULE_HEXDIG+);

RULE_BIG_INTEGER : RULE_COMMON_INT 'L';

RULE_NUMBER : RULE_COMMON_INT;

RULE_BYTES : 'x' ('\'' (RULE_HEXDIG RULE_HEXDIG)* '\''|'"' (RULE_HEXDIG RULE_HEXDIG)* '"');

fragment RULE_STRCHAR : ('\t'|'\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\''|'\\'|'u' RULE_HEXDIG RULE_HEXDIG RULE_HEXDIG RULE_HEXDIG));

fragment RULE_STRBAD : ('\\'|'\u0000'..'\u001F');

//RULE_STRING : ('"' (RULE_STRCHAR|~(('"'|RULE_STRBAD)))* '"'|'\'' (RULE_STRCHAR|~(('\''|RULE_STRBAD)))* '\'');

RULE_STRING : ('"' (RULE_STRCHAR|~('"'|'\\'|'\u0000'..'\u001F'))* '"'|'\'' (RULE_STRCHAR|~('\''|'\\'|'\u0000'..'\u001F'))* '\'');

// Validations
INVALID_DECIMAL : ('0'..'9')+ '.' ('a'..'z'|'A'..'Z')+;

ERROR : . ;