/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.model.R_KeyIndexKind
import net.postchain.rell.base.model.expr.R_AtCardinality
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import kotlin.reflect.KProperty

object S_Keywords {
    const val ABSTRACT = "abstract"
    const val OVERRIDE = "override"
}

object S_Grammar {
    private val rellTokens = arrayListOf<RellToken>()

    private val LPAR by relltok("(")
    private val RPAR by relltok(")")
    private val LCURL by relltok("{")
    private val RCURL by relltok("}")
    private val LBRACK by relltok("[")
    private val RBRACK by relltok("]")
    private val AT by relltok("@")
    private val DOLLAR by relltok("$")
    private val COLON by relltok(":")
    private val SEMI by relltok(";")
    private val COMMA by relltok(",")
    private val DOT by relltok(".")
    private val ELVIS by relltok("?:")
    private val SAFECALL by relltok("?.")
    private val NOTNULL by relltok("!!")
    private val QUESTION by relltok("?")
    private val DOUBLEQUESTION by relltok("??")
    private val ARROW by relltok("->")
    private val CARET by relltok("^")

    private val EQ by relltok("==")
    private val NE by relltok("!=")
    private val LT by relltok("<")
    private val GT by relltok(">")
    private val LE by relltok("<=")
    private val GE by relltok(">=")
    private val EQ_REF by relltok("===")
    private val NE_REF by relltok("!==")

    private val PLUS by relltok("+")
    private val MINUS by relltok("-")
    private val MUL by relltok("*")
    private val DIV by relltok("/")
    private val MOD by relltok("%")
    private val PLUSPLUS by relltok("++")
    private val MINUSMINUS by relltok("--")

    private val AND by relltok("and")
    private val OR by relltok("or")
    private val NOT by relltok("not")

    private val ASSIGN by relltok("=")
    private val PLUS_ASSIGN by relltok("+=")
    private val MINUS_ASSIGN by relltok("-=")
    private val MUL_ASSIGN by relltok("*=")
    private val DIV_ASSIGN by relltok("/=")
    private val MOD_ASSIGN by relltok("%=")

    private val ABSTRACT by relltok(S_Keywords.ABSTRACT)
    private val BREAK by relltok("break")
    private val CLASS by relltok("class")
    private val CONTINUE by relltok("continue")
    private val CREATE by relltok("create")
    private val DELETE by relltok("delete")
    private val ELSE by relltok("else")
    private val ENTITY by relltok("entity")
    private val ENUM by relltok("enum")
    private val FALSE by relltok("false")
    private val FOR by relltok("for")
    private val FUNCTION by relltok("function")
    private val GUARD by relltok("guard")
    private val IF by relltok("if")
    private val IMPORT by relltok("import")
    private val IN by relltok("in")
    private val INCLUDE by relltok("include")
    private val INDEX by relltok("index")
    private val KEY by relltok("key")
    private val LIMIT by relltok("limit")
    private val MODULE by relltok("module")
    private val MUTABLE by relltok("mutable")
    private val NAMESPACE by relltok("namespace")
    private val NULL by relltok("null")
    private val OBJECT by relltok("object")
    private val OFFSET by relltok("offset")
    private val OPERATION by relltok("operation")
    private val OVERRIDE by relltok(S_Keywords.OVERRIDE)
    private val QUERY by relltok("query")
    private val RECORD by relltok("record")
    private val RETURN by relltok("return")
    private val STRUCT by relltok("struct")
    private val TRUE by relltok("true")
    private val UPDATE by relltok("update")
    private val VAL by relltok("val")
    private val VAR by relltok("var")
    private val VIRTUAL by relltok("virtual")
    private val WHEN by relltok("when")
    private val WHILE by relltok("while")

    private val NUMBER by relltok(RellTokenizer.INTEGER) // Must be exactly INT for Eclipse coloring, but then Xtext assumes it's a decimal Integer
    private val BIG_INTEGER by relltok(RellTokenizer.BIG_INTEGER)
    private val DECIMAL by relltok(RellTokenizer.DECIMAL)
    private val BYTES by relltok(RellTokenizer.BYTEARRAY)
    private val STRING by relltok(RellTokenizer.STRING) // Must be exactly STRING for Eclipse coloring
    private val ID by relltok(RellTokenizer.IDENTIFIER)

    private val LR_PAR = TokenPair(LPAR, RPAR)
    private val LR_CURL = TokenPair(LCURL, RCURL)
    private val LR_BRACK = TokenPair(LBRACK, RBRACK)

    val tokenizer: RellTokenizer by lazy { RellTokenizer(rellTokens) }

    private val _commaSeparatedParsers = mutableListOf<Parser<*>>()

    val commaSeparatedParsers: List<Parser<*>> get() = _commaSeparatedParsers.toImmList()

    private val nameNode by ID map {
        val sName = S_Name(it.pos, RellTokenizer.decodeName(it.pos, it.text))
        G_Node(sName, it)
    }

    private val name by nameNode map { it.value }

    private val qualifiedNameNode by separatedTerms(nameNode, DOT, false) map { nameNodes ->
        val sNames = nameNodes.map { it.value }
        G_Node(S_QualifiedName(sNames), nameNodes.first().firstToken)
    }

    private val qualifiedName by qualifiedNameNode map { it.value }

    private val typeRef by parser(this::type)
    private val expressionRef by parser(this::expression)
    private val statementRef by parser(this::statement)

    private val nameType by qualifiedName map { S_NameType(it) }

    private val tupleTypeField by optional(nameNode * -COLON) * typeRef map {
        (name, type) ->
        S_GenericTupleAttr(name?.value, type, name?.firstToken?.comment)
    }

    private val tupleType by commaSeparatedOneMany(tupleTypeField) map {
        val single = getTupleSingleField(it)
        if (single != null) single else S_TupleType(it.pos, it.items)
    }

    private val genericType by qualifiedName * commaSeparatedOneMany(typeRef, TokenPair(LT, GT)) map {
        (name, args) ->
        S_GenericType(name, args.items)
    }

    private val virtualType by VIRTUAL * -LT * typeRef * -GT map { (kw, type) -> S_VirtualType(kw.pos, type) }

    private val mirrorStructType0 by STRUCT * -LT * optional(MUTABLE) * typeRef * -GT

    private val mirrorStructType by mirrorStructType0 map {
        (kw, mutable, paramType) ->
        S_MirrorStructType(kw.pos, mutable != null, paramType)
    }

    private val primaryType by (
            genericType
            or nameType
            or tupleType
            or virtualType
            or mirrorStructType
    )

    private val basicType by primaryType * zeroOrMore(QUESTION) map { (base, nulls) ->
        var res = base
        for (n in nulls) res = S_NullableType(n.pos, res)
        res
    }

    private val complexNullableType by LPAR * typeRef * -RPAR * -QUESTION map {
        (pos, type) ->
        S_NullableType(pos.pos, type)
    }

    private val functionType by commaSeparatedZeroMany(typeRef) * -ARROW * typeRef map {
        (params, res) ->
        S_FunctionType(params.pos, params.items, res)
    }

    private val type: Parser<S_Type> by (
            complexNullableType
            or functionType
            or basicType
    )

    private val annotationArgValue by parser(S_Grammar::literalExpr) map {
        S_AnnotationArg_Value(it)
    }

    private val annotationArgName by qualifiedName map {
        S_AnnotationArg_Name(it)
    }

    private val annotationArg by annotationArgValue or annotationArgName
    private val annotationArgs by commaSeparatedZeroMany(annotationArg) map { it.items }

    private val annotation by AT * name * optional(annotationArgs) map {
        (at, name, args) ->
        G_Node(S_Annotation(name, args ?: immListOf()), at)
    }

    private val keywordModifier0 by (
        ( ABSTRACT map { it to S_KeywordModifierKind.ABSTRACT } )
        or ( OVERRIDE map { it to S_KeywordModifierKind.OVERRIDE } )
    )

    private val keywordModifier by keywordModifier0 map { (token, value) ->
        val cName = C_Name.make(token.pos, token.text)
        G_Node(S_KeywordModifier(cName, value), token)
    }

    private val modifier: Parser<G_Node<S_Modifier>> by keywordModifier or annotation

    private val modifiers: Parser<G_Node<S_Modifiers>?> by zeroOrMore(modifier) map { modifierNodes ->
        if (modifierNodes.isEmpty()) null else {
            val modifiers = modifierNodes.map { it.value }
            val firstToken = modifierNodes.first().firstToken
            G_Node(S_Modifiers(modifiers), firstToken)
        }
    }

    private val nameTypeAttrHeader by nameNode * -COLON * type map {
        (name, type) ->
        G_Node(S_NamedAttrHeader(name.value, type), name.firstToken)
    }

    private val anonAttrHeader by qualifiedNameNode * optional(QUESTION) map {
        (name, nullable) ->
        G_Node(S_AnonAttrHeader(name.value, nullable != null), name.firstToken)
    }

    private val attrHeader by nameTypeAttrHeader or anonAttrHeader

    private val baseAttributeDefinition by optional(MUTABLE) * attrHeader * optional(-ASSIGN * expressionRef) map {
        (mutable, header, expr) ->
        val firstToken = mutable ?: header.firstToken
        G_Node(S_AttributeDefinition(mutable?.pos, header.value, expr), firstToken)
    }

    private val attributeDefinition by baseAttributeDefinition * -SEMI

    private val attributeClause by attributeDefinition map {
        S_AttributeClause(it.value, it.firstToken.comment)
    }

    private val keyIndexKind by (
        ( KEY map { G_Node(R_KeyIndexKind.KEY, it) } )
        or ( INDEX map { G_Node(R_KeyIndexKind.INDEX, it) } )
    )

    private val keyIndexClause by keyIndexKind * commaSeparatedOneMany0(baseAttributeDefinition) * -SEMI map {
        (kind, attrs) ->
        val kindToken = kind.firstToken
        val rawAttrs = attrs.items.map { it.value }
        S_KeyIndexClause(kindToken.pos, kind.value, rawAttrs, kindToken.comment)
    }

    private val entityAnnotations by commaSeparatedOneMany(name) map { it.items }

    private val relClause by attributeClause or keyIndexClause
    private val entityBodyFull by -LCURL * zeroOrMore(relClause) * -RCURL
    private val entityBodyShort by SEMI map { null }
    private val entityBody by entityBodyFull or entityBodyShort

    private val entityKeyword by
        (ENTITY map { it to false }) or
        (CLASS map { it to true })

    private val entityDef by entityKeyword * name * optional(entityAnnotations) * optional(entityBody) map {
        (kwDeprecated, name, annotations2, clauses) ->
        val (kw, deprecated) = kwDeprecated
        val deprecatedKwPos = if (deprecated) kw.pos else null
        AnnotatedDef(kw) {
            S_EntityDefinition(it, deprecatedKwPos, name, annotations2 ?: listOf(), clauses)
        }
    }

    private val objectDef by OBJECT * name * -LCURL * zeroOrMore(attributeClause) * -RCURL map {
        (kw, name, attrs) ->
        AnnotatedDef(kw) { S_ObjectDefinition(it, name, attrs) }
    }

    private val structKeyword by
        (STRUCT map { it to false }) or
        (RECORD map { it to true })

    private val structDef by structKeyword * name * -LCURL * zeroOrMore(attributeClause) * -RCURL map {
        (posDeprecated, name, attrs) ->
        val (kw, deprecated) = posDeprecated
        val deprecatedKwPos = if (deprecated) kw.pos else null
        AnnotatedDef(kw) { S_StructDefinition(it, deprecatedKwPos, name, attrs) }
    }

    private val enumValue by nameNode map {
        S_EnumValue(it.value, it.firstToken.comment)
    }

    private val enumDef by ENUM * name * commaSeparatedZeroMany(enumValue, LR_CURL) map {
        (kw, name, values) ->
        AnnotatedDef(kw) { S_EnumDefinition(it, name, values.items) }
    }

    private val binaryOperator = (
            ( EQ mapNode { S_BinaryOp.EQ } )
            or ( NE mapNode { S_BinaryOp.NE } )
            or ( LE mapNode { S_BinaryOp.LE } )
            or ( GE mapNode { S_BinaryOp.GE } )
            or ( LT mapNode { S_BinaryOp.LT } )
            or ( GT mapNode { S_BinaryOp.GT } )
            or ( EQ_REF mapNode { S_BinaryOp.EQ_REF } )
            or ( NE_REF mapNode { S_BinaryOp.NE_REF } )

            or ( PLUS mapNode { S_BinaryOp.PLUS } )
            or ( MINUS mapNode { S_BinaryOp.MINUS } )
            or ( MUL mapNode { S_BinaryOp.MUL } )
            or ( DIV mapNode { S_BinaryOp.DIV } )
            or ( MOD mapNode { S_BinaryOp.MOD } )

            or ( AND mapNode { S_BinaryOp.AND } )
            or ( OR mapNode { S_BinaryOp.OR } )

            or ( IN mapNode { S_BinaryOp.IN } )
            or ( -NOT * IN mapNode { S_BinaryOp.NOT_IN } )
            or ( ELVIS mapNode { S_BinaryOp.ELVIS } )
    )

    private val incrementOperator = (
            ( PLUSPLUS mapNode { true }  )
            or ( MINUSMINUS mapNode { false }  )
    )

    private val unaryPrefixOperator = (
            ( PLUS mapNode { S_UnaryOp_Plus } )
            or ( MINUS mapNode { S_UnaryOp_Minus }  )
            or ( NOT mapNode { S_UnaryOp_Not }  )
            or ( incrementOperator map { S_PosValue(it.pos, S_UnaryOp_IncDec(it.value, false)) } )
    )

    private val unaryPostfixOperator = (
            ( incrementOperator map { S_PosValue(it.pos, S_UnaryOp_IncDec(it.value, true)) } )
            or ( DOUBLEQUESTION mapNode { S_UnaryOp_IsNull } )
    )

    private val nameExpr by name map { S_NameExpr(S_QualifiedName(it)) }
    private val dollarExpr by DOLLAR map { S_DollarExpr(it.pos) }
    private val attrExpr by DOT * name map { (pos, name) -> S_AttrExpr(pos.pos, name) }

    private val intExpr by NUMBER map { S_IntegerLiteralExpr(it.pos, RellTokenizer.decodeInteger(it.pos, it.text)) }

    private val bigIntExpr by BIG_INTEGER map { S_CommonLiteralExpr(it.pos, RellTokenizer.decodeBigInteger(it.pos, it.text)) }

    private val decimalExpr by DECIMAL map { S_CommonLiteralExpr(it.pos, RellTokenizer.decodeDecimal(it.pos, it.text)) }

    private val stringExpr = STRING map { S_StringLiteralExpr(it.pos, RellTokenizer.decodeString(it.pos, it.text)) }

    private val bytesExpr by BYTES map { S_ByteArrayLiteralExpr(it.pos, RellTokenizer.decodeByteArray(it.pos, it.text)) }

    private val booleanLiteralExpr by
            ( FALSE map { S_BooleanLiteralExpr(it.pos, false) } ) or
            ( TRUE map { S_BooleanLiteralExpr(it.pos, true) } )

    private val nullLiteralExpr by NULL map { S_NullLiteralExpr(it.pos) }

    private val literalExpr by
        intExpr or
        bigIntExpr or
        decimalExpr or
        stringExpr or
        bytesExpr or
        booleanLiteralExpr or
        nullLiteralExpr

    private val tupleExprField by optional(nameNode * -ASSIGN) * expressionRef map {
        (name, expr) -> S_GenericTupleAttr(name?.value, expr, name?.firstToken?.comment)
    }

    private val tupleExpr by commaSeparatedOneMany(tupleExprField) map {
        val single = getTupleSingleField(it)
        if (single != null) S_ParenthesesExpr(it.pos, single) else S_TupleExpr(it.pos, it.items)
    }

    private val atExprFromItem by zeroOrMore(annotation) * optional(nameNode * -COLON) * expressionRef map {
        (annotations, alias, expr) ->
        val annotations2 = annotations.map { it.value }
        val firstToken = annotations.firstOrNull()?.firstToken ?: alias?.firstToken
        S_AtExprFromItem(S_Modifiers(annotations2), alias?.value, expr, firstToken?.comment)
    }

    private val atExprFrom by commaSeparatedOneMany(atExprFromItem) map {
        S_PosValue(it.pos, it.items)
    }

    private val atExprAt by (
            ( AT * QUESTION map { S_PosValue(it.t1.pos, R_AtCardinality.ZERO_ONE) } )
            or ( AT * MUL map { S_PosValue(it.t1.pos, R_AtCardinality.ZERO_MANY) } )
            or ( AT * PLUS map { S_PosValue(it.t1.pos, R_AtCardinality.ONE_MANY) } )
            or ( AT map { S_PosValue(it.pos, R_AtCardinality.ONE) } )
    )

    private val atExprWhatSimple by DOT * separatedTerms(name, DOT) map {
        (dot, path) ->
        S_AtExprWhat_Simple(dot.pos, path)
    }

    private val atExprWhatComplexItem by zeroOrMore(annotation) * optional(nameNode * -ASSIGN) * expressionRef map {
        (annotations, name, expr) ->
        val annotations2 = annotations.map { it.value }
        val modifiers = S_Modifiers(annotations2)
        val firstToken = annotations.firstOrNull()?.firstToken ?: name?.firstToken
        S_AtExprWhatComplexField(name?.value, expr, modifiers, firstToken?.comment)
    }

    private val atExprWhatComplex by commaSeparatedOneMany(atExprWhatComplexItem) map {
        S_AtExprWhat_Complex(it.pos, it.items)
    }

    private val atExprWhat by atExprWhatSimple or atExprWhatComplex

    private val atExprWhere by commaSeparatedZeroMany(expressionRef, LR_CURL) map {
        S_AtExprWhere(it.items)
    }

    private val atExprLimit by -LIMIT * expressionRef
    private val atExprOffset by -OFFSET * expressionRef

    private val atExprModifiers by (
            ((atExprLimit * optional(atExprOffset)) map { (limit, offset) -> AtExprMods(limit, offset) })
            or ((atExprOffset * optional(atExprLimit)) map { (offset, limit) -> AtExprMods(limit, offset) })
    )

    private val listLiteralExpr by commaSeparatedZeroMany(expressionRef, LR_BRACK) map {
        S_ListLiteralExpr(it.pos, it.items)
    }

    private val mapLiteralExprEntry by expressionRef * -COLON * expressionRef map { (key, value) -> Pair(key, value) }
    private val emptyMapLiteralExpr by LBRACK * -COLON * -RBRACK map { pos -> S_MapLiteralExpr(pos.pos, listOf()) }
    private val nonEmptyMapLiteralExpr by commaSeparatedOneMany(mapLiteralExprEntry, LR_BRACK) map {
        S_MapLiteralExpr(it.pos, it.items)
    }
    private val mapLiteralExpr by emptyMapLiteralExpr or nonEmptyMapLiteralExpr

    private val mirrorStructExpr by mirrorStructType0 map {
        (kw, mutable, type) ->
        S_MirrorStructExpr(kw.pos, mutable != null, type)
    }
    private val callArgValue by (
            ( MUL map { S_CallArgumentValue_Wildcard(it.pos) } )
            or ( expressionRef map { S_CallArgumentValue_Expr(it) } )
    )

    private val createExprArg by optional(-optional(DOT) * name * -ASSIGN) * callArgValue map {
        (name, value) ->
        S_CallArgument(name, value)
    }

    private val createExprArgs by commaSeparatedZeroMany(createExprArg) map { it.items }

    private val createExpr by CREATE * qualifiedName * createExprArgs map {
        (kw, entityName, args) ->
        S_CreateExpr(kw.pos, entityName, args)
    }

    private val virtualTypeExpr by virtualType map { S_SpecialTypeExpr(it) }

    private val callArg by optional(name * -ASSIGN) * callArgValue map {
        (name, value) ->
        S_CallArgument(name, value)
    }

    private val callArgs by commaSeparatedZeroMany(callArg) map { it.items }

    private val baseExprTailMember by -DOT * name map { name -> G_BaseExprTail_Member(name) }
    private val baseExprTailSubscript by LBRACK * expressionRef * -RBRACK map { (pos, expr) -> G_BaseExprTail_Subscript(pos.pos, expr) }
    private val baseExprTailNotNull by NOTNULL map { G_BaseExprTail_NotNull(it.pos) }
    private val baseExprTailSafeMember by -SAFECALL * name map { name -> G_BaseExprTail_SafeMember(name) }
    private val baseExprTailUnaryPostfixOp by unaryPostfixOperator map { G_BaseExprTail_UnaryPostfixOp(it.pos, it.value) }

    private val baseExprTailCall by callArgs map { args ->
        G_BaseExprTail_Call(args)
    }

    private val baseExprTailAt by atExprAt * atExprWhere * optional(atExprWhat) * optional(atExprModifiers) map {
        ( cardinality, where, whatOpt, mods ) ->
        val what = whatOpt ?: S_AtExprWhat_Default()
        G_BaseExprTail_At(cardinality.pos, cardinality.value, where, what, mods?.limit, mods?.offset)
    }

    private val atExpr by atExprFrom * baseExprTailAt map {
        (from, tail) ->
        tail.toExpr(S_AtExprFrom_Complex(from.pos, from.value))
    }

    private val baseExprTailNoCallNoAt by (
            baseExprTailMember
            or baseExprTailSubscript
            or baseExprTailNotNull
            or baseExprTailSafeMember
            or baseExprTailUnaryPostfixOp
    )

    private val baseExprTail by baseExprTailNoCallNoAt or baseExprTailCall or baseExprTailAt

    private val genericTypeExpr by genericType * (baseExprTailMember or baseExprTailCall) map {
        (type, tail) ->
        val baseExpr = S_GenericTypeExpr(type)
        tail.toExpr(baseExpr)
    }

    private val baseExprHead by (
            genericTypeExpr
            or atExpr
            or nameExpr
            or dollarExpr
            or attrExpr
            or literalExpr
            or tupleExpr
            or createExpr
            or listLiteralExpr
            or mapLiteralExpr
            or mirrorStructExpr
            or virtualTypeExpr
    )

    private val baseExpr: Parser<S_Expr> by baseExprHead * zeroOrMore(baseExprTail) map {
        ( head, tails ) ->
        G_BaseExprTail.tailsToExpr(head, tails)
    }

    private val baseExprNoCallNoAt by baseExprHead * zeroOrMore(baseExprTailNoCallNoAt) map {
        ( head, tails ) ->
        G_BaseExprTail.tailsToExpr(head, tails)
    }

    private val ifExpr by IF * -LPAR * expressionRef * -RPAR * expressionRef * -ELSE * expressionRef map {
        (pos, cond, trueExpr, falseExpr) ->
        S_IfExpr(pos.pos, cond, trueExpr, falseExpr)
    }

    private val whenConditionExpr by separatedTerms(expressionRef, COMMA, false) * -optional(COMMA) map {
        S_WhenConditionExpr(it)
    }
    private val whenConditionElse by ELSE map { S_WhenCondtiionElse(it.pos) }
    private val whenCondition by whenConditionExpr or whenConditionElse

    private val whenExprCase by whenCondition * -ARROW * expressionRef map {
        (cond, expr) ->
        S_WhenExprCase(cond, expr)
    }

    private val whenExprCases by separatedTerms(whenExprCase, oneOrMore(SEMI), false) * zeroOrMore(SEMI) map {
        (cases, _) -> cases
    }

    private val whenExpr by WHEN * optional(-LPAR * expressionRef * -RPAR) * -LCURL * whenExprCases * -RCURL map {
        (pos, expr, cases) ->
        S_WhenExpr(pos.pos, expr, cases)
    }

    private val operandExpr: Parser<S_Expr> by ( baseExpr or ifExpr or whenExpr )

    private val unaryExpr by zeroOrMore(unaryPrefixOperator) * operandExpr map { (ops, expr) ->
        var res = expr
        for (op in ops.reversed()) {
            res = S_UnaryExpr(op.pos, S_PosValue(op.pos, op.value), res)
        }
        res
    }

    private val binaryExprOperand by binaryOperator * unaryExpr map { ( op, expr ) -> S_BinaryExprTail(op, expr) }

    private val binaryExpr by unaryExpr * zeroOrMore(binaryExprOperand) map { ( head, tail ) ->
        if (tail.isEmpty()) head else S_BinaryExpr(head, tail)
    }

    private val expression: Parser<S_Expr> by binaryExpr

    private val emptyStmt by SEMI map { S_EmptyStatement(it.pos) }

    private val varVal by (
            ( VAL map { G_Node(false, it) } )
            or ( VAR map { G_Node(true, it) } )
    )

    private val simpleVarDeclarator by attrHeader map { S_SimpleVarDeclarator(it.value) }

    private val tupleVarDeclarator by commaSeparatedOneMany(parser(this::varDeclarator)) map {
        S_TupleVarDeclarator(it.pos, it.items)
    }

    private val varDeclarator: Parser<S_VarDeclarator> by simpleVarDeclarator or tupleVarDeclarator

    private val varStmt by varVal * varDeclarator * optional(-ASSIGN * expression) * -SEMI map {
        (mutableNode, declarator, expr) ->
        val kw = mutableNode.firstToken
        S_VarStatement(kw.pos, declarator, expr, mutableNode.value, kw.comment)
    }

    private val returnStmt by RETURN * optional(expression) * -SEMI map { ( kw, expr ) ->
        S_ReturnStatement(kw.pos, expr)
    }

    private val assignOp by (
            ( ASSIGN mapNode { S_AssignOpCode.EQ })
            or ( PLUS_ASSIGN mapNode { S_AssignOpCode.PLUS })
            or ( MINUS_ASSIGN mapNode { S_AssignOpCode.MINUS })
            or ( MUL_ASSIGN mapNode { S_AssignOpCode.MUL })
            or ( DIV_ASSIGN mapNode { S_AssignOpCode.DIV })
            or ( MOD_ASSIGN mapNode { S_AssignOpCode.MOD })
    )

    private val assignStmt by baseExpr * assignOp * expression * -SEMI map {
        (expr1, op, expr2) ->
        S_AssignStatement(expr1, op, expr2)
    }

    private val incrementStmt by incrementOperator * baseExpr * -SEMI map {
        (op, expr) ->
        S_ExprStatement(S_UnaryExpr(op.pos, S_PosValue(op.pos, S_UnaryOp_IncDec(op.value, false)), expr))
    }

    private val blockStmt by LCURL * zeroOrMore(statementRef) * -RCURL map {
        (pos, statements) ->
        S_BlockStatement(pos.pos, statements)
    }

    private val ifStmt by IF * -LPAR * expression * -RPAR * statementRef * optional(-ELSE * statementRef) map {
        (pos, expr, trueStmt, falseStmt) ->
        S_IfStatement(pos.pos, expr, trueStmt, falseStmt)
    }

    private val whenStmtCase by whenCondition * -ARROW * statementRef * zeroOrMore(SEMI) map {
        (cond, stmt) ->
        S_WhenStatementCase(cond, stmt)
    }

    private val whenStmt by WHEN * optional(-LPAR * expressionRef * -RPAR) * -LCURL * zeroOrMore(whenStmtCase) * -RCURL map {
        (pos, expr, cases) ->
        S_WhenStatement(pos.pos, expr, cases)
    }

    private val whileStmt by WHILE * -LPAR * expression * -RPAR * statementRef map {
        (pos, expr, stmt) ->
        S_WhileStatement(pos.pos, expr, stmt)
    }

    private val forStmt by FOR * -LPAR * varDeclarator * -IN * expression * -RPAR * statementRef map {
        (pos, declarator, expr, stmt) ->
        S_ForStatement(pos.pos, declarator, expr, stmt)
    }

    private val breakStmt by BREAK * -SEMI map { S_BreakStatement(it.pos) }
    private val continueStmt by CONTINUE * -SEMI map { S_ContinueStatement(it.pos) }

    private val callStmt by baseExpr * -SEMI map { expr -> S_ExprStatement(expr) }

    private val createStmt by createExpr * -SEMI map { expr -> S_ExprStatement(expr) }

    private val updateFromSingle by qualifiedName map {
        S_PosValue(it.pos, listOf(S_UpdateFromItem(null, it, null)))
    }

    private val updateFromItem by optional(nameNode * -COLON) * qualifiedName map {
        (alias, entityName) ->
        S_UpdateFromItem(alias?.value, entityName, alias?.firstToken?.comment)
    }

    private val updateFromMulti by commaSeparatedOneMany(updateFromItem) map {
        S_PosValue(it.pos, it.items)
    }

    private val updateFrom by updateFromSingle or updateFromMulti

    private val updateTargetAt by updateFrom * atExprAt * atExprWhere map {
        (from, cardinality, where) ->
        S_UpdateTarget_Simple(cardinality.value, from.value, where)
    }

    private val updateTargetExpr by baseExprNoCallNoAt map { expr -> S_UpdateTarget_Expr(expr) }

    private val updateTarget by updateTargetAt or updateTargetExpr

    private val updateWhatNameOp by -optional(DOT) * name * assignOp map { (name, op) -> Pair(name, op) }

    private val updateWhatExpr by optional(updateWhatNameOp) * expression map {
        (nameOp, expr) ->
        if (nameOp == null) {
            S_UpdateWhat(expr.startPos, null, null, expr)
        } else {
            val (name, op) = nameOp
            S_UpdateWhat(name.pos, name, op.value, expr)
        }
    }

    private val updateWhat by commaSeparatedOneMany(updateWhatExpr) map { it.items }

    private val updateStmt by UPDATE * updateTarget * updateWhat * -SEMI map {
        (kw, target, what) ->
        S_UpdateStatement(kw.pos, target, what)
    }

    private val deleteStmt by DELETE * updateTarget * -SEMI map {
        (kw, target) ->
        S_DeleteStatement(kw.pos, target)
    }

    private val guardStmt by GUARD * blockStmt map {
        (kw, stmt) -> S_GuardStatement(kw.pos, stmt)
    }

    private val statementNoExpr by (
            emptyStmt
            or varStmt
            or assignStmt
            or returnStmt
            or blockStmt
            or ifStmt
            or whenStmt
            or whileStmt
            or forStmt
            or breakStmt
            or continueStmt
            or updateStmt
            or deleteStmt
    )

    private val statement: Parser<S_Statement> by (
            statementNoExpr
            or incrementStmt
            or callStmt
            or createStmt
            or guardStmt
    )

    private val formalParameter by attrHeader * optional(-ASSIGN * expression) map {
        (attr, expr) -> S_FormalParameter(attr.value, expr, attr.firstToken.comment)
    }

    private val formalParameters by commaSeparatedZeroMany(formalParameter) map { it.items }

    private val opDef by OPERATION * name * formalParameters * blockStmt map {
        (kw, name, params, body) -> AnnotatedDef(kw) { S_OperationDefinition(it, name, params, body) }
    }

    private val functionBodyShort by -ASSIGN * expression * -SEMI map { S_FunctionBodyShort(it) }
    private val functionBodyFull by blockStmt map { stmt -> S_FunctionBodyFull(stmt) }
    private val functionBodyNone by SEMI map { null }
    private val functionBody by functionBodyShort or functionBodyFull or functionBodyNone

    private val queryBody by functionBodyShort or functionBodyFull

    private val queryDef by QUERY * name * formalParameters * optional(-COLON * type) * queryBody map {
        (kw, name, params, type, body) ->
        AnnotatedDef(kw) { S_QueryDefinition(it, name, params, type, body) }
    }

    private val functionDef by FUNCTION * optional(qualifiedName) * formalParameters * optional(-COLON * type) * functionBody map {
        (kw, name, params, type, body) ->
        AnnotatedDef(kw) { S_FunctionDefinition(it, name, params, type, body) }
    }

    private val namespaceDef by NAMESPACE * optional(qualifiedName) * LCURL * zeroOrMore(parser(this::annotatedDef)) * RCURL map {
        (kw, name, lcurl, defs, rcurl) ->
        val bodyPosRange = S_PosRange(lcurl.pos, rcurl.pos)
        AnnotatedDef(kw) { S_NamespaceDefinition(it, bodyPosRange, name, defs) }
    }

    private val absoluteImportModule by qualifiedName map { S_ImportModulePath(null, it) }

    private val relativeImportModule by DOT * optional(qualifiedName) map {
        (pos, moduleName) ->
        S_ImportModulePath(S_RelativeImportModulePath(pos.pos, 0), moduleName)
    }

    private val upImportModule by oneOrMore(CARET) * optional(-DOT * qualifiedName) map {
        (carets, moduleName) ->
        S_ImportModulePath(S_RelativeImportModulePath(carets[0].pos, carets.size), moduleName)
    }

    private val importModule by absoluteImportModule or relativeImportModule or upImportModule

    private val importTargetExactItem by optional(nameNode * -COLON) * qualifiedNameNode * optional(-DOT * MUL) map {
        (alias, name, wildcard) ->
        val firstToken = alias?.firstToken ?: name.firstToken
        S_ExactImportTargetItem(alias?.value, name.value, wildcard != null, firstToken.comment)
    }
    private val importTargetExact by commaSeparatedOneMany(importTargetExactItem, LR_CURL) map {
        items -> S_ExactImportTarget(items.items)
    }
    private val importTargetWildcard by MUL map { S_WildcardImportTarget }
    private val importTarget by -DOT * (importTargetExact or importTargetWildcard)

    private val importDef by IMPORT * optional( name * -COLON) * importModule * optional(importTarget) * -SEMI map {
        (kw, alias, module, target) ->
        AnnotatedDef(kw) { S_ImportDefinition(it, alias, module, target ?: S_DefaultImportTarget) }
    }

    private val includeDef by INCLUDE * STRING * -SEMI map {
        (kw, _) ->
        AnnotatedDef(kw) { S_IncludeDefinition(it) }
    }

    private val constantDef by VAL * name * optional(-COLON * typeRef) * -ASSIGN * expressionRef * -SEMI map {
        (kw, name, type, expr) ->
        AnnotatedDef(kw) { S_GlobalConstantDefinition(it, name, type, expr) }
    }

    private val replDef: Parser<AnnotatedDef> by (
            entityDef
            or objectDef
            or structDef
            or enumDef
            or functionDef
            or namespaceDef
            or importDef
            or opDef
            or queryDef
            or includeDef
    )

    private val anyDef: Parser<AnnotatedDef> by (
            replDef
            or constantDef
    )

    private val annotatedDef by modifiers * anyDef map {
        (modifiers, def) -> def.createDef(modifiers)
    }

    private val moduleHeader by modifiers * MODULE * -SEMI map {
        (modifiersNode, kw) ->
        val firstToken = modifiersNode?.firstToken ?: kw
        val modifiers = modifiersNode?.value ?: S_Modifiers()
        S_ModuleHeader(modifiers, kw.pos, firstToken.comment)
    }

    private val defReplStep by modifiers * replDef map {
        (modifiers, def) ->
        S_DefinitionReplStep(def.createDef(modifiers))
    }

    private val replExprStatement by expression * -SEMI map {
        expr -> S_ExprStatement(expr)
    }

    private val stmtReplStep by statementNoExpr or replExprStatement map {
        stmt -> S_StatementReplStep(stmt)
    }

    private val replStep by defReplStep or stmtReplStep

    private val replCommand by zeroOrMore(replStep) * optional(expression) map {
        (steps, expr) -> S_ReplCommand(steps, expr)
    }

    val replParser: Parser<S_ReplCommand> by replCommand

    val rootParser by optional(moduleHeader) * zeroOrMore(annotatedDef) map {
        (header, defs) ->
        S_RellFile(header, defs)
    }

    private fun <T> getTupleSingleField(list: S_CommaSeparatedList<S_GenericTupleAttr<T>>): T? {
        val first = list.items.first()
        val isSingle = list.items.size == 1 && !list.trailingComma && first.name == null
        return if (isSingle) first.value else null
    }

    private inline fun <reified T> commaSeparatedZeroMany(
        item: Parser<T>,
        brackets: TokenPair = LR_PAR,
    ): Parser<S_CommaSeparatedList<T>> {
        val parser = brackets.first * optional(commaSeparatedOneMany0(item)) * -brackets.second map {
            (lBracket, values) ->
            S_CommaSeparatedList(lBracket.pos, values?.items ?: immListOf(), values?.trailingComma ?: false)
        }
        _commaSeparatedParsers.add(parser)
        return parser
    }

    private inline fun <reified T> commaSeparatedOneMany(
        item: Parser<T>,
        brackets: TokenPair = LR_PAR,
    ): Parser<S_CommaSeparatedList<T>> {
        val parser = brackets.first * commaSeparatedOneMany0(item) * -brackets.second map {
            (lBracket, values) ->
            S_CommaSeparatedList(lBracket.pos, values.items, values.trailingComma)
        }
        _commaSeparatedParsers.add(parser)
        return parser
    }

    private inline fun <reified T> commaSeparatedOneMany0(item: Parser<T>): Parser<S_CommaSeparatedValues<T>> {
        val parser = separatedTerms(item, COMMA, false) * optional(COMMA) map {
            (items, comma) ->
            S_CommaSeparatedValues(items, comma != null)
        }
        _commaSeparatedParsers.add(parser)
        return parser
    }

    private fun relltok(s: String): RellToken {
        val t = Token(null, s)
        return RellToken(t.name ?: "", t)
    }

    private operator fun RellToken.provideDelegate(thisRef: S_Grammar, property: KProperty<*>): RellToken {
        val ex = if (token.name != null) this else RellToken(property.name, token)
        rellTokens.add(ex)
        return ex
    }

    private operator fun <T> Parser<T>.getValue(thisRef: S_Grammar, property: KProperty<*>): Parser<T> = this
}

private class AnnotatedDef(
    private val kw: RellTokenMatch,
    private val creator: (S_DefinitionBase) -> S_Definition,
) {
    fun createDef(modifiersNode: G_Node<S_Modifiers>?): S_Definition {
        val modifiers = modifiersNode?.value ?: S_Modifiers()
        val firstToken = modifiersNode?.firstToken ?: kw
        val base = S_DefinitionBase(kw.pos, modifiers, firstToken.comment)
        return creator(base)
    }
}

private class AtExprMods(val limit: S_Expr?, val offset: S_Expr?)

private class S_CommaSeparatedValues<T>(val items: List<T>, val trailingComma: Boolean)
private class S_CommaSeparatedList<T>(val pos: S_Pos, val items: List<T>, val trailingComma: Boolean)

private class TokenPair(val first: Parser<RellTokenMatch>, val second: Parser<RellTokenMatch>)

private infix fun <T> Parser<RellTokenMatch>.mapNode(
    transform: (RellTokenMatch) -> T,
): Parser<S_PosValue<T>> = MapCombinator(this) {
    S_PosValue(it, transform(it))
}
