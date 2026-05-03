grammar RellManual;

@header {
import java.math.BigInteger;
}

@parser::members {
    private static final int MAX_ALLOWED_DIGITS = 131072;
    private static final int MAX_ALLOWED_HEX_DIGITS = 999;
    private static final int DECIMAL_FRAC_DIGITS = 20;
    private static final int MAX_DECIMAL_LITERAL_LENGTH = 1000;

    private static final BigInteger BIG_INTEGER_MAX_VALUE =
        BigInteger.TEN.pow(MAX_ALLOWED_DIGITS).subtract(BigInteger.ONE);
    private static final BigInteger BIG_INTEGER_MIN_VALUE = BIG_INTEGER_MAX_VALUE.negate();

    private static final java.math.BigDecimal POSITIVE_MIN =
        java.math.BigDecimal.ONE.divide(java.math.BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS + 1));
    private static final java.math.BigDecimal NEGATIVE_MIN = POSITIVE_MIN.negate();
    private static final java.math.BigDecimal UPPER_LIMIT = java.math.BigDecimal.TEN.pow(MAX_ALLOWED_DIGITS);
    private static final java.math.BigDecimal LOWER_LIMIT = UPPER_LIMIT.negate();

    private boolean isIntegerOutOfRange(String text) {
        try {
            if (text.startsWith("0x")) {
                Long.parseLong(text.substring(2), 16);
            } else {
                Long.parseLong(text, 10);
            }
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean isBigIntegerOutOfRange(String text) {
        if (text.endsWith("L")) {
            text = text.substring(0, text.length() - 1);
        }
        boolean isHex = text.startsWith("0x");
        if (text.length() > MAX_ALLOWED_HEX_DIGITS) {
            return true;
        }
        BigInteger v = isHex ? new BigInteger(text.substring(2), 16) : new BigInteger(text, 10);
        return v.compareTo(BIG_INTEGER_MAX_VALUE) > 0 || v.compareTo(BIG_INTEGER_MIN_VALUE) < 0;
    }

    private static java.math.BigDecimal scale(java.math.BigDecimal v) {
        java.math.BigDecimal t = v;
        if (t.compareTo(NEGATIVE_MIN) >= 0 && t.compareTo(POSITIVE_MIN) <= 0) {
            return java.math.BigDecimal.ZERO;
        }
        if (t.compareTo(LOWER_LIMIT) <= 0 || t.compareTo(UPPER_LIMIT) >= 0) {
            return null;
        }
        if (t.scale() > DECIMAL_FRAC_DIGITS) {
            t = v.setScale(DECIMAL_FRAC_DIGITS, java.math.RoundingMode.HALF_UP);
            if (t.compareTo(LOWER_LIMIT) <= 0 || t.compareTo(UPPER_LIMIT) >= 0) {
                return null;
            }
        }
        return t;
    }

    private boolean isDecimalOutOfRange(String text) {
        if (text.length() > MAX_DECIMAL_LITERAL_LENGTH) {
            return true;
        }
        java.math.BigDecimal parsed;
        try {
            parsed = new java.math.BigDecimal(text);
        } catch (NumberFormatException e) {
            return true;
        }
        return scale(parsed) == null;
    }
}

// ===========================================================================
// Top-level
// ===========================================================================

file
    : moduleHeader? annotatedDef* EOF
    ;

// REPL entry: zero or more steps (each is either an annotated definition or a
// statement) followed by an optional trailing expression without a semicolon.
// The trailing expression is what makes the REPL print a value.
replCommand
    : replStep* expression? EOF
    ;

replStep
    : modifiers replDef                                                         # defReplStep
    | statement                                                                 # stmtReplStep
    | expression ';'                                                            # exprReplStep
    ;

// Subset of `anyDef` available in REPL — excludes `constantDef` so that
// `val x = 123;` parses as a var statement, not a global constant. This
// matches the better-parse grammar's `replDef` rule.
replDef
    : entityDef
    | objectDef
    | structDef
    | enumDef
    | functionDef
    | namespaceDef
    | importDef
    | opDef
    | queryDef
    | includeDef
    ;

moduleHeader
    : modifiers 'module' ';'
    ;

modifiers
    : modifier*
    ;

modifier
    : 'abstract'
    | 'mutable'
    | 'override'
    | annotation
    ;

annotation
    : '@' RULE_ID annotationArgs?
    ;

qualifiedName
    : RULE_ID ('.' RULE_ID)*
    ;

annotationArgs
    : '(' (annotationArg (',' annotationArg)* ','?)? ')'
    ;

annotationArg
    : RULE_NUMBER
    | RULE_BIG_INTEGER
    | RULE_DECIMAL
    | RULE_STRING
    | RULE_BYTES
    | 'false'
    | 'true'
    | 'null'
    | qualifiedName
    ;

annotatedDef
    : modifiers anyDef
    ;

anyDef
    : entityDef
    | objectDef
    | structDef
    | enumDef
    | functionDef
    | namespaceDef
    | importDef
    | opDef
    | queryDef
    | includeDef
    | constantDef
    ;

// ===========================================================================
// Definitions
// ===========================================================================

entityDef
    : ('entity' | 'class') RULE_ID entityAnnotations? entityBody?
    ;

entityAnnotations
    : '(' RULE_ID (',' RULE_ID)* ','? ')'
    ;

entityBody
    : ';'
    | '{' relClause* '}'
    ;

relClause
    : keyIndexClause
    | attributeClause
    ;

keyIndexClause
    : ('key' | 'index') baseAttributeDefinition (',' baseAttributeDefinition)* ','? ';'
    ;

attributeClause
    : baseAttributeDefinition ';'
    ;

baseAttributeDefinition
    : modifiers attrHeader ('=' expression)?
    ;

attrHeader
    : RULE_ID ':' type        # nameTypeAttrHeader
    | qualifiedName '?'?      # anonAttrHeader
    ;

objectDef
    : 'object' RULE_ID '{' attributeClause* '}'
    ;

structDef
    : ('struct' | 'record') RULE_ID '{' attributeClause* '}'
    ;

enumDef
    : 'enum' RULE_ID '{' (RULE_ID (',' RULE_ID)* ','?)? '}'
    ;

functionDef
    : 'function' qualifiedName? formalParameters (':' type)? functionBody
    ;

formalParameters
    : '(' (formalParameter (',' formalParameter)* ','?)? ')'
    ;

formalParameter
    : modifiers attrHeader ('=' expression)?
    ;

functionBody
    : ';'
    | '=' expression ';'
    | blockStmt
    ;

namespaceDef
    : 'namespace' qualifiedName? '{' annotatedDef* '}'
    ;

importDef
    : 'import' (RULE_ID ':')? importModule importTarget? ';'
    ;

importModule
    : qualifiedName                      # absoluteImportModule
    | '.' qualifiedName?                 # relativeImportModule
    | '^'+ ('.' qualifiedName)?          # upImportModule
    ;

importTarget
    : '.' (importTargetExact | '*')
    ;

importTargetExact
    : '{' importTargetExactItem (',' importTargetExactItem)* ','? '}'
    ;

importTargetExactItem
    : (RULE_ID ':')? qualifiedName ('.' '*')?
    ;

opDef
    : 'operation' RULE_ID formalParameters blockStmt
    ;

queryDef
    : 'query' RULE_ID formalParameters (':' type)? queryBody
    ;

queryBody
    : '=' expression ';'
    | blockStmt
    ;

includeDef
    : 'include' RULE_STRING ';'
    ;

constantDef
    : 'val' RULE_ID (':' type)? '=' expression ';'
    ;

// ===========================================================================
// Types
// ===========================================================================

// `(int)?` was matched by both `complexNullableType` and `tupleType '?'` in
// the old grammar; ANTLR resolved the tie via alt-order. Dropping the
// redundant `complexNullableType` shrinks the SLL prediction DFA at the
// leading `(` of a type without changing the language.
type
    : '(' (type (',' type)* ','?)? ')' '->' type                # functionType
    | primaryType '?'*                                          # basicTypeAlt
    ;

// `tupleTypeField` was a single-use leaf rule (`(RULE_ID ':')? type`);
// inlined here to skip the per-field method call + context allocation
// in the tuple-type loop.
primaryType
    : qualifiedName ('<' type (',' type)* ','? '>')?                   # genericOrNameType
    | '(' ((RULE_ID ':')? type) (',' (RULE_ID ':')? type)* ','? ')'    # tupleType
    | 'virtual' '<' type '>'                                           # virtualType
    | 'struct' '<' 'mutable'? type '>'                                 # mirrorStructType
    ;

// ===========================================================================
// Statements
// ===========================================================================

statement
    : ';'                                                                       # emptyStmt
    | ('val' | 'var') varDeclarator ('=' expression)? ';'                       # varStmtAlt
    | 'return' expression? ';'                                                  # returnStmtAlt
    | blockStmt                                                                 # blockStmtAlt
    | 'if' '(' expression ')' statement ('else' statement)?                     # ifStmtAlt
    | 'when' ('(' expression ')')? '{' (whenCondition '->' statement ';'?)* '}' # whenStmtAlt
    | 'while' '(' expression ')' statement                                      # whileStmtAlt
    | 'for' '(' varDeclarator 'in' expression ')' statement                     # forStmtAlt
    | 'break' ';'                                                               # breakStmtAlt
    | 'continue' ';'                                                            # continueStmtAlt
    | 'update' updateTarget '('
          (('.'? RULE_ID ('=' | '+=' | '-=' | '*=' | '/=' | '%='))? expression)
          (',' (('.'? RULE_ID ('=' | '+=' | '-=' | '*=' | '/=' | '%='))? expression))* ','? ')' ';'   # updateStmtAlt
    | 'delete' updateTarget ';'                                                 # deleteStmtAlt
    | 'guard' blockStmt                                                         # guardStmtAlt
    | ('++' | '--') baseExpr ';'                                                # incrementStmtAlt
    | baseExpr (('=' | '+=' | '-=' | '*=' | '/=' | '%=') expression)? ';'       # exprStmtAlt
    ;

blockStmt
    : '{' statement* '}'
    ;

varDeclarator
    : attrHeader                                                   # simpleVarDeclarator
    | '(' varDeclarator (',' varDeclarator)* ','? ')'              # tupleVarDeclarator
    ;

whenCondition
    : 'else'                                       # whenConditionElse
    | expression (',' expression)* ','?            # whenConditionExpr
    ;

// `updateTarget` is the only spot in the grammar where the choice between
// "from-clause-style at-expression" and "expression target" lives. The
// `# updateTargetAt` alt cannot be replaced by reusing `baseExprHead`'s
// `# atExpr` alt because the latter greedily consumes a trailing
// `atExprWhat?` (i.e. a `(x = 1)` selector list) — which is exactly the
// `updateWhat` parens of the enclosing `update` statement. Keeping the
// alternative narrow ensures `update (a, b) @ {} (x = 1) ;` parses with
// `updateWhat = (x = 1)`, not `atExprWhat = (x = 1)`.
//
// `updateFromItem` body is inlined into the `updateTargetAt` alt so the
// loop body is matched without a per-item method call.
updateTarget
    : (qualifiedName | '(' ((RULE_ID ':')? qualifiedName) (',' (RULE_ID ':')? qualifiedName)* ','? ')')
        atExprAt atExprWhere                       # updateTargetAt
    | baseExprHead baseExprTailNoCallNoAt*         # updateTargetExpr
    ;

// ===========================================================================
// Expressions
// ===========================================================================

expression
    : ('+' | '-' | 'not' | '++' | '--')* (ifExpr | whenExpr | baseExpr)
      (
        ('==' | '!=' | '<=' | '>=' | '<' | '>' | '===' | '!==' | '+' | '-' | '*' | '/'
         | '%'  | 'and' | 'or' | '&'  | 'in' | 'not' 'in' | '?:')
        ('+' | '-' | 'not' | '++' | '--')* (ifExpr | whenExpr | baseExpr)
      )*
    ;

ifExpr
    : 'if' '(' expression ')' expression 'else' expression
    ;

whenExpr
    : 'when' ('(' expression ')')? '{' (whenCondition '->' expression) (';' whenCondition '->' expression)* ';'? '}'
    ;

baseExpr
    : baseExprHead (baseExprTailNoCallNoAt | callArgs | atExprAt atExprWhere atExprWhat? atExprModifiers?)*
    ;

// Order matters: keyword-led alternatives go first so SLL prediction commits
// on a single-token lookahead. The `qualifiedName` head and the two `(`-led
// alts (`# atExpr` vs `# tupleHead`) need LL: the former because of the
// optional `<...>` overlap between `genericTypeExpr` and `nameExpr`, the
// latter because both alts open with `(` and adaptive prediction has to
// look past the closing `)` for `@` to commit (see comment block below).
//
// The `# atExpr` alt is the inlined body of the former `atExprWithFrom`
// rule: a `(from-items)` parenthesized list followed by the `@cardinality
// {where} ...` tail.
baseExprHead
    : 'create' qualifiedName
        '(' ((('.'? RULE_ID '=')? ('*' | expression)) (',' ('.'? RULE_ID '=')? ('*' | expression))* ','?)? ')'  # createExpr
    | 'struct' '<' 'mutable'? type '>'                            # mirrorStructExpr
    | 'virtual' '<' type '>'                                      # virtualTypeExpr
    | qualifiedName '<' type (',' type)* ','? '>' (callArgs | '.' RULE_ID)   # genericTypeExpr
    | qualifiedName                                                       # nameExpr
    | '(' (annotation* (RULE_ID ':')? expression) (',' annotation* (RULE_ID ':')? expression)* ','? ')'
      atExprAt atExprWhere atExprWhat? atExprModifiers?    # atExpr
    | '(' ((RULE_ID '=')? expression) (',' (RULE_ID '=')? expression)* ','? ')'   # tupleHead
    | '$'                                                         # dollarExpr
    | '.' RULE_ID                                                 # attrExpr
    | RULE_NUMBER
        { if (isIntegerOutOfRange($RULE_NUMBER.getText()))
              notifyErrorListeners("Integer literal out of range: " + $RULE_NUMBER.getText()); }   # intExpr
    | RULE_BIG_INTEGER
        { if (isBigIntegerOutOfRange($RULE_BIG_INTEGER.getText()))
              notifyErrorListeners("Big integer literal too long: " + $RULE_BIG_INTEGER.getText()); }   # bigIntExpr
    | RULE_DECIMAL
        { if (isDecimalOutOfRange($RULE_DECIMAL.getText()))
              notifyErrorListeners("Decimal literal value out of range: " + $RULE_DECIMAL.getText()); }   # decimalExpr
    | RULE_STRING                                                 # stringExpr
    | RULE_BYTES                                                  # bytesExpr
    | 'false'                                                     # falseExpr
    | 'true'                                                      # trueExpr
    | 'null'                                                      # nullExpr
    | '[' ':' ']'                                                 # emptyMapLiteralExpr
    | '[' expression ':' expression (',' expression ':' expression)* ','? ']'   # nonEmptyMapLiteralExpr
    | '[' (expression (',' expression)* ','?)? ']'                # listLiteralExpr
    ;

callArgs
    : '(' (((RULE_ID '=')? ('*' | expression)) (',' (RULE_ID '=')? ('*' | expression))* ','?)? ')'
    ;

baseExprTailNoCallNoAt
    : '.' RULE_ID                          # baseExprTailMember
    | '[' expression ']'                # baseExprTailSubscript
    | '!!'                              # baseExprTailNotNull
    | '?.' RULE_ID                         # baseExprTailSafeMember
    | ('++' | '--' | '??')              # baseExprTailUnaryPostfixOp
    ;

atExprAt
    : '@' ('?' | '*' | '+')?
    ;

atExprWhere
    : '{' (expression (',' expression)* ','?)? '}'
    ;

atExprWhat
    : '.' RULE_ID ('.' RULE_ID)*                                                # atExprWhatSimple
    | '(' (annotation* (RULE_ID '=')? expression) (',' annotation* (RULE_ID '=')? expression)* ','? ')'   # atExprWhatComplex
    ;

atExprModifiers
    : 'limit' expression ('offset' expression)?
    | 'offset' expression ('limit' expression)?
    ;

// ===========================================================================
// Lexer (kept identical to Rell.g4 for token-stream compatibility).
// ===========================================================================

// Channel 2 matches Rell.g4 and RellCustomTokenChannels.COMMENTS; HIDDEN is 1.
RULE_ML_COMMENT : '/*' .*? '*/'    -> channel(2);
RULE_SL_COMMENT : '//' ~[\r\n]*    -> channel(2);

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

RULE_STRING : ('"' (RULE_STRCHAR|~('"'|'\\'|'\u0000'..'\u001F'))* '"'|'\'' (RULE_STRCHAR|~('\''|'\\'|'\u0000'..'\u001F'))* '\'');

INVALID_DECIMAL : ('0'..'9')+ '.' ('a'..'z'|'A'..'Z')+;

ERROR : . ;
