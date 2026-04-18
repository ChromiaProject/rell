/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.mtype.utils

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.betterParse.parser.parseToEnd
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*

internal object MTestParser {
    fun parseType(code: String, scope: MTestScope): M_Type {
        return parseTypeEx(code, scope).type
    }

    fun parseTypeEx(code: String, scope: MTestScope): MTestParsedType {
        val res = parse0(scope, code, MTestGrammar.type) { ctx, s -> s.compile(ctx) }
        res.type.validate()
        return res
    }

    fun parseTypeSetMap(code: String, scope: MTestScope): Map<M_TypeParam, M_TypeSet> {
        return parse0(scope, code, MTestGrammar.typeSetMap) { ctx, s ->
            s
                .mapKeys { scope.typeParams[it.key] ?: M_TypeParam(it.key) }
                .mapValues { it.value.compile(ctx).set }
        }
    }

    fun parseTypeDef(code: String, scope: MTestScope): M_GenericType {
        return parse0(scope, code, MTestGrammar.typeDef) { ctx, s -> s.compile(ctx) }
    }

    fun parseTypeParam(code: String, scope: MTestScope): M_TypeParam {
        return parse0(scope, code, MTestGrammar.typeParam) { ctx, s -> s.compile(ctx) }
    }

    fun parseTypeParams(code: String, scope: MTestScope): ImmList<M_TypeParam> {
        return parse0(scope, code, MTestGrammar.typeParams) { ctx, s -> MsTypeParam.compileAll(ctx, s) }
    }

    fun parseFunctionHeader(code: String, scope: MTestScope): M_FunctionHeader {
        return parse0(scope, code, MTestGrammar.funHeader) { ctx, s ->
            s.compile(ctx)
        }
    }

    fun parseFunctionCall(code: String, scope: MTestScope): Pair<ImmList<M_Type>, M_Type?> {
        return parse0(scope, code, MTestGrammar.funCall) { ctx, s ->
            val args = s.first.mapToImmList { it.compile(ctx).type }
            val res = s.second?.compile(ctx)?.type
            args to res
        }
    }

    private fun <S, R> parse0(
        scope: MTestScope,
        code: String,
        parser: Parser<S>,
        block: (MsTypeCtx, S) -> R,
    ): R {
        val s = parser.parseToEnd(MTestGrammar.tokenizer.tokenize(code))
        val msCtx = MsTypeCtx(types = scope.types)
        return block(msCtx, s)
    }
}

data class MTestParsedType(val type: M_Type, val params: ImmSet<M_TypeParam>)
data class MTestParsedSet(val set: M_TypeSet, val params: ImmSet<M_TypeParam>)

private object MTestGrammar: M_TypeGrammar<MsType>() {
    private val AT by literalToken("@")
    private val EQ by literalToken("=")

    override val name by NAME map {
        it.text
    }

    val type: Parser<MsType> by type0 map {
        MTestGrammarUtils.convertType(it)
    }

    val typeSet: Parser<MsTypeSet> by typeSet0 map {
        MTestGrammarUtils.convertTypeSet(it)
    }

    private val typeSetMapEntry by name * -EQ * typeSet map { (name, set) -> name to set }
    val typeSetMap by separatedTerms(typeSetMapEntry, COMMA) map { it.toMap() }

    private val typeParamVariance by (MINUS map { M_TypeVariance.OUT }) or (PLUS map { M_TypeVariance.IN })

    private val typeParamBound by (MINUS or PLUS) * type map {
        (sign, type) ->
        when (sign.text) {
            "-" -> MsTypeSet_Range(null, type)
            "+" -> MsTypeSet_Range(type, null)
            else -> throw IllegalStateException(sign.text)
        }
    }

    val typeParam by optional(typeParamVariance) * name * optional(-COLON * typeParamBound) map {
        (variance, name, bounds) ->
        val mVariance = variance ?: M_TypeVariance.NONE
        MsTypeParam(name, mVariance, bounds ?: MsTypeSet_Range(null, null))
    }

    val typeParams by separatedTerms(typeParam, COMMA)

    private val typeDefParams by -LT * typeParams * -GT

    private val parentType by name * optional(-LT * separatedTerms(type, COMMA) * -GT) map { (name, args) ->
        MsParentType(name, args.orEmpty().toImmList())
    }

    val typeDef by name * optional(typeDefParams) * optional(-COLON * parentType) map { (name, params, parent) ->
        MsTypeDef(name, params.orEmpty().toImmList(), parent)
    }

    private val annotation by -AT * name

    private val funParam by zeroOrMore(annotation) * type map { (anns, type) -> MsFunParam(anns.toImmList(), type) }
    private val funParams by -LPAREN * separatedTerms(funParam, COMMA, true) * -RPAREN

    val funHeader by optional(-LT * typeParams * -GT) * funParams * -COLON * type map {
        (typeParams, params, result) ->
        MsFunHeader(typeParams.orEmpty().toImmList(), params.toImmList(), result)
    }

    val funCall by separatedTerms(type, COMMA, true) * optional(-COLON * type) map { (args, res) -> args to res }

    override val rootParser by type
}

private object MTestGrammarUtils {
    fun convertType(type: M_AstType): MsType {
        return when (type) {
            is M_AstType_Name -> MsType_Name(type.name)
            is M_AstType_Function -> MsType_Function(
                convertType(type.result),
                type.params.mapToImmList { convertType(it) })
            is M_AstType_Generic -> MsType_Generic(type.name, type.args.mapToImmList { convertTypeSet(it) })
            is M_AstType_Nullable -> MsType_Nullable(convertType(type.valueType))
            is M_AstType_Tuple -> MsType_Tuple(type.fields.mapToImmList { it.first to convertType(it.second) })
        }
    }

    fun convertTypeSet(typeSet: M_AstTypeSet): MsTypeSet {
        return when (typeSet) {
            M_AstTypeSet_All -> MsTypeSet_Range(null, null)
            is M_AstTypeSet_One -> MsTypeSet_Simple(convertType(typeSet.type))
            is M_AstTypeSet_SubOf -> MsTypeSet_Range(null, convertType(typeSet.type))
            is M_AstTypeSet_SuperOf -> MsTypeSet_Range(convertType(typeSet.type), null)
        }
    }
}

private class MsTypeCtx(val types: ImmMap<String, MTestTypeDef>) {
    fun nested(types: Map<String, MTestTypeDef>): MsTypeCtx {
        return MsTypeCtx(types = this.types + types)
    }

    fun nestedTypeParams(params: List<M_TypeParam>): MsTypeCtx {
        val subScopeB = MTestScope.Builder()
        for (param in params) {
            subScopeB.simpleType(param.name, M_Types.param(param))
        }
        val paramTypes = subScopeB.build().types
        return nested(paramTypes)
    }
}

private abstract class MsTypeSet {
    abstract fun compile(ctx: MsTypeCtx): MTestParsedSet
}

private class MsTypeSet_Simple(private val type: MsType): MsTypeSet() {
    override fun compile(ctx: MsTypeCtx): MTestParsedSet {
        val pType = type.compile(ctx)
        return MTestParsedSet(M_TypeSets.one(pType.type), pType.params)
    }
}

private class MsTypeSet_Range(private val lower: MsType?, private val upper: MsType?): MsTypeSet() {
    override fun compile(ctx: MsTypeCtx): MTestParsedSet {
        val pLower = lower?.compile(ctx)
        val pUpper = upper?.compile(ctx)
        return when {
            pLower == null && pUpper == null -> MTestParsedSet(M_TypeSets.ALL, immSetOf())
            pLower != null -> MTestParsedSet(M_TypeSets.superOf(pLower.type), pLower.params)
            pUpper != null -> MTestParsedSet(M_TypeSets.subOf(pUpper.type), pUpper.params)
            else -> throw IllegalStateException("$pLower, $pUpper")
        }
    }
}

private sealed class MsType {
    abstract fun compile(ctx: MsTypeCtx): MTestParsedType
}

private class MsType_Name(private val name: String): MsType() {
    override fun compile(ctx: MsTypeCtx): MTestParsedType {
        val typeDef = ctx.types[name]
        return if (typeDef != null) {
            val mType = typeDef.mType(listOf())
            MTestParsedType(mType, immSetOfNotNull(typeDef.typeParam()))
        } else if (name.matches(Regex("[A-Z][0-9]?"))) {
            val param = M_TypeParam(name)
            val mType = M_Types.param(param)
            MTestParsedType(mType, immSetOf(param))
        } else {
            throw IllegalStateException("Unknown type: $name")
        }
    }
}

private class MsType_Nullable(private val valueType: MsType): MsType() {
    override fun compile(ctx: MsTypeCtx): MTestParsedType {
        val pValueType = valueType.compile(ctx)
        val mType = M_Types.nullable(pValueType.type)
        return MTestParsedType(mType, pValueType.params)
    }
}

private class MsType_Tuple(private val fields: ImmList<Pair<String?, MsType>>): MsType() {
    override fun compile(ctx: MsTypeCtx): MTestParsedType {
        val fieldNames = M_TupleTypeUtils.makeNames(fields) { it.first }
        val pFieldTypes = fields.map { it.second.compile(ctx) }
        val mFieldTypes = pFieldTypes.map { it.type }
        val params = pFieldTypes.flatMap { it.params }.toImmSet()
        val mType = M_Types.tuple(mFieldTypes, fieldNames)
        return MTestParsedType(mType, params)
    }
}

private class MsType_Generic(private val name: String, private val args: ImmList<MsTypeSet>): MsType() {
    override fun compile(ctx: MsTypeCtx): MTestParsedType {
        val typeDef = ctx.types.getValue(name)
        val pTypeArgs = args.map { it.compile(ctx) }
        val typeArgs = pTypeArgs.map { it.set }
        val params = pTypeArgs.flatMap { it.params }.toImmSet()
        return MTestParsedType(typeDef.mType(typeArgs), params)
    }
}

private class MsType_Function(private val result: MsType, private val params: ImmList<MsType>): MsType() {
    override fun compile(ctx: MsTypeCtx): MTestParsedType {
        val pParams = params.map { it.compile(ctx) }
        val pResult = result.compile(ctx)
        val mType = M_Types.function(pResult.type, pParams.map { it.type })
        val typeParams = (listOf(pResult) + pParams).flatMap { it.params }.toImmSet()
        return MTestParsedType(mType, typeParams)
    }
}

private class MsTypeParam(
    private val name: String,
    private val variance: M_TypeVariance,
    private val bounds: MsTypeSet,
) {
    fun compile(ctx: MsTypeCtx): M_TypeParam {
        val pBounds = bounds.compile(ctx)
        check(pBounds.set is M_TypeSet_Many) { pBounds.set }
        return M_TypeParam(name, variance, pBounds.set)
    }

    companion object {
        fun compileAll(ctx: MsTypeCtx, sParams: List<MsTypeParam>): ImmList<M_TypeParam> {
            val res = mutableListOf<M_TypeParam>()
            var curCtx = ctx
            for (sParam in sParams) {
                val param = sParam.compile(curCtx)
                res.add(param)
                curCtx = curCtx.nested(mapOf(param.name to MTestTypeDef.makeParam(param)))
            }
            return res.toImmList()
        }
    }
}

private class MsParentType(private val name: String, private val args: ImmList<MsType>) {
    fun compile(ctx: MsTypeCtx): M_GenericTypeParent {
        val typeDef = ctx.types.getValue(name)
        val genType = checkNotNull(typeDef.genericType()) { name }
        val mArgs = args.mapToImmList { it.compile(ctx).type }
        return M_GenericTypeParent(genType, mArgs)
    }
}

private class MsTypeDef(
    private val name: String,
    private val params: ImmList<MsTypeParam>,
    private val parent: MsParentType?,
) {
    fun compile(ctx: MsTypeCtx): M_GenericType {
        val mParams = MsTypeParam.compileAll(ctx, params)
        val subCtx = ctx.nestedTypeParams(mParams)
        val mParent = parent?.compile(subCtx)
        return M_GenericType.make(name, mParams, mParent)
    }
}

private class MsFunParam(private val annotations: ImmList<String>, private val type: MsType) {
    fun compile(ctx: MsTypeCtx, name: String): M_FunctionParam {
        val annMap = MsParamAnn.entries.map { it.name.lowercase() to it }.toImmMap()
        val anns = annotations.map { annMap.getValue(it) }.toImmSet()
        val mType = type.compile(ctx)
        return M_FunctionParam(
            name = name,
            type = mType.type,
            arity = M_ParamArity.ONE,
            exact = MsParamAnn.EXACT in anns,
            nullable = MsParamAnn.NULLABLE in anns,
        )
    }

    private enum class MsParamAnn {
        EXACT,
        NULLABLE,
    }
}

private class MsFunHeader(val typeParams: ImmList<MsTypeParam>, val params: ImmList<MsFunParam>, val result: MsType) {
    fun compile(ctx: MsTypeCtx): M_FunctionHeader {
        val mTypeParams = MsTypeParam.compileAll(ctx, typeParams)
        val subCtx = ctx.nestedTypeParams(mTypeParams)
        val mResType = result.compile(subCtx)
        val mParams = params.mapIndexedToImmList { i, param ->
            val name = ('a' + i).toString()
            param.compile(subCtx, name)
        }
        return M_FunctionHeader(typeParams = mTypeParams, resultType = mResType.type, params = mParams)
    }
}
