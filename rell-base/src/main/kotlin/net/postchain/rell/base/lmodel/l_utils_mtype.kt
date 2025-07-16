/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*

internal object L_TypeUtils {
    fun makeMType(
        rType: R_Type,
        parent: M_GenericTypeParent? = null,
        docCodeStrategy: L_TypeDefDocCodeStrategy?,
    ): M_Type {
        val genType = makeMGenericType(rType, rType.name, parent, docCodeStrategy)
        return genType.getType(immListOf())
    }

    fun makeMGenericType(
        rType: R_Type,
        name: String,
        parent: M_GenericTypeParent?,
        docCodeStrategy: L_TypeDefDocCodeStrategy?,
    ): M_GenericType {
        check(rType != R_NullType)
        check(rType !is R_NullableType) { rType }
        check(rType !is R_FunctionType) { rType }
        check(rType !is R_CollectionType) { rType }
        check(rType !is R_MapType) { rType }
        check(rType !is R_TupleType) { rType }
        check(rType !is R_VirtualType) { rType }

        val docCodeStrategy2 = docCodeStrategy ?: makeDocCodeStrategy(name)
        val addon = C_MGenericTypeAddon_Simple(rType, docCodeStrategy2)

        return M_GenericType.make(name, immListOf(), parent, addon)
    }

    fun makeMGenericType(
        name: R_FullName,
        params: List<M_TypeParam>,
        parent: M_GenericTypeParent?,
        rTypeFactory: L_TypeDefRTypeFactory?,
        docCodeStrategy: L_TypeDefDocCodeStrategy?,
        supertypeStrategy: L_TypeDefSupertypeStrategy,
    ): M_GenericType {
        val nameStr = name.qualifiedName.str()
        val addon = C_MGenericTypeAddon_LTypeDef(
            rTypeFactory = rTypeFactory,
            docCodeStrategy = docCodeStrategy ?: makeDocCodeStrategy(nameStr),
            supertypeStrategy = supertypeStrategy,
        )
        return M_GenericType.make(nameStr, params, parent = parent, addon = addon)
    }

    private fun makeDocCodeStrategy(typeName: String): L_TypeDefDocCodeStrategy {
        return L_TypeDefDocCodeStrategy { args ->
            val b = DocCode.builder()
            b.link(typeName)
            if (args.isNotEmpty()) {
                b.raw("<")
                for ((i, arg) in args.withIndex()) {
                    if (i > 0) b.sep(", ")
                    b.append(arg)
                }
                b.raw(">")
            }
            b.build()
        }
    }

    fun getRType(mType: M_Type): R_Type? {
        return when (mType) {
            M_Types.ANYTHING -> null
            M_Types.NOTHING -> null
            M_Types.ANY -> null
            M_Types.NULL -> R_NullType
            is M_Type_Param -> null
            is M_Type_Nullable -> getRTypeForNullable(mType)
            is M_Type_Function -> getRTypeForFunction(mType)
            is M_Type_Tuple -> getRTypeForTuple(mType)
            is M_Type_Generic -> getRTypeForGeneric(mType)
            else -> null
        }
    }

    fun getRTypeNotNull(mType: M_Type): R_Type {
        val rType = getRType(mType)
        checkNotNull(rType) { "No R_Type: ${mType.strCode()}" }
        return rType
    }

    private fun getRTypeForNullable(mType: M_Type_Nullable): R_Type? {
        val rValueType = getRType(mType.valueType)
        return if (rValueType == null || rValueType == R_UnitType) null else C_Types.toNullable(rValueType)
    }

    private fun getRTypeForFunction(mType: M_Type_Function): R_Type? {
        val rResult = getRType(mType.resultType)
        val rParams = if (rResult == null) null else mType.paramTypes.mapNotNullAllOrNull { getRType(it) }
        return if (rResult == null || rParams == null) null else R_FunctionType(rParams, rResult)
    }

    private fun getRTypeForTuple(mType: M_Type_Tuple): R_TupleType? {
        val rFields = mType.fieldTypes.indices.mapNotNullAllOrNull { i ->
            val rFieldType = getRType(mType.fieldTypes[i])
            if (rFieldType == null) null else {
                val mName = mType.fieldNames[i]
                val rName = if (mName == null) null else R_IdeName(R_Name.of(mName), C_IdeSymbolInfo.MEM_TUPLE_ATTR)
                R_TupleField(i, rName, rFieldType)
            }
        }
        return if (rFields == null) null else R_TupleType(rFields)
    }

    private fun getRTypeForGeneric(mType: M_Type_Generic): R_Type? {
        val addon = getTypeAddon(mType)
        val mArgs = mType.typeArgs.mapNotNullAllOrNull { it.getExactType() }
        return if (mArgs == null) null else addon.getRType(mArgs)
    }

    fun getExpectedType(typeParams: ImmList<M_TypeParam>, mPatternType: M_Type, hint: C_TypeHint): M_Type? {
        val resTypes = hint.mTypes.mapNotNull { matchExpectedType(typeParams, mPatternType, it) }
        val resType = resTypes.singleOrNull()
        return resType
    }

    private fun matchExpectedType(typeParams: ImmList<M_TypeParam>, mPatternType: M_Type, mHintType: M_Type): M_Type? {
        val resolver = M_TypeParamsResolver(typeParams)
        val match = resolver.matchTypeParamsOut(mPatternType, mHintType)
        if (!match) {
            return null
        }

        val typeArgs = resolver.resolve()
        typeArgs ?: return null

        val typeSets = typeArgs.mapValues { M_TypeSets.one(it.value) }
        val typeRep = mPatternType.replaceParams(typeSets, false)
        return typeRep.type()
    }

    fun getTypeAdapter(conversion: M_Conversion): C_TypeAdapter? {
        return when (conversion) {
            M_Conversion_Direct -> C_TypeAdapter_Direct
            is M_Conversion_Nullable -> {
                val subAdapter = getTypeAdapter(conversion.valueConversion)
                val resultType = getRType(conversion.resultType)
                if (subAdapter == null || resultType == null) null else C_TypeAdapter_Nullable(resultType, subAdapter)
            }
            is M_Conversion_Generic -> {
                (conversion as M_Conversion_CTypeAdapter).adapter
            }
        }
    }

    fun docType(mType: M_Type): DocType {
        return when (mType) {
            M_Types.ANYTHING -> DocType.ANYTHING
            M_Types.NOTHING -> DocType.NOTHING
            M_Types.ANY -> DocType.ANY
            M_Types.NULL -> DocType.NULL
            is M_Type_Param -> DocType.name(mType.param.name)
            is M_Type_Nullable -> {
                val docValueType = docType(mType.valueType)
                DocType.nullable(docValueType)
            }
            is M_Type_Function -> docTypeFunction(mType)
            is M_Type_Tuple -> docTypeTuple(mType)
            is M_Type_Generic -> {
                val addon = getTypeAddon(mType)
                addon.docType(mType.typeArgs)
            }
            else -> {
                // Shall not happen, detect this case in unit tests and investigate (using strCode() is inefficient).
                CommonUtils.failIfUnitTest()
                val s = mType.strCode()
                DocType.raw(s)
            }
        }
    }

    private fun docTypeFunction(mType: M_Type_Function): DocType {
        val resultType = docType(mType.resultType)
        val paramTypes = mType.paramTypes.mapToImmList { docType(it) }
        return DocType.function(resultType, paramTypes)
    }

    private fun docTypeTuple(mType: M_Type_Tuple): DocType {
        val fieldTypes = mType.fieldTypes.mapToImmList { docType(it) }
        return DocType.tuple(fieldTypes, mType.fieldNames)
    }

    fun docTypeSet(mTypeSet: M_TypeSet): DocTypeSet {
        return when (mTypeSet) {
            M_TypeSet_All -> DocTypeSet.ALL
            is M_TypeSet_One -> DocTypeSet.one(docType(mTypeSet.type))
            is M_TypeSet_SubOf -> DocTypeSet.subOf(docType(mTypeSet.boundType))
            is M_TypeSet_SuperOf -> DocTypeSet.superOf(docType(mTypeSet.boundType))
        }
    }

    fun docFunctionHeader(mHeader: M_FunctionHeader): DocFunctionHeader {
        val docTypeParams = docTypeParams(mHeader.typeParams)
        val docResultType = docType(mHeader.resultType)
        val docParams = mHeader.params.mapToImmList { docFunctionParam(it) }
        return DocFunctionHeader(docTypeParams, docResultType, docParams)
    }

    fun docTypeParams(mTypeParams: List<M_TypeParam>): ImmList<DocTypeParam> {
        return mTypeParams.mapToImmList {
            val docBounds = docTypeSet(it.bounds)
            DocTypeParam(it.name, it.variance, docBounds)
        }
    }

    fun docFunctionParam(mParam: M_FunctionParam): DocFunctionParam {
        val docType = docType(mParam.type)
        return DocFunctionParam(mParam.name, docType, mParam.arity, mParam.exact, mParam.nullable)
    }

    private fun getTypeAddon(mType: M_Type_Generic): C_MGenericTypeAddon {
        val addon = mType.genericType.addon
        check(addon is C_MGenericTypeAddon) { "$mType ${addon.javaClass.canonicalName}" }
        return addon
    }
}

private class M_Conversion_CTypeAdapter(val adapter: C_TypeAdapter): M_Conversion_Generic()

private sealed class C_MGenericTypeAddon(
    private val docCodeStrategy: L_TypeDefDocCodeStrategy,
): M_GenericTypeAddon() {
    abstract fun getRType(args: List<M_Type>): R_Type?

    final override fun strCode(typeName: String, args: List<M_TypeSet>): String {
        val docArgs = args.map { mTypeSet ->
            DocCode.builder()
                .also { L_TypeUtils.docTypeSet(mTypeSet).genCode(it) }
                .build()
        }
        val docCode = docCodeStrategy.docCode(docArgs)
        return docCode.strRaw()
    }

    fun docType(args: List<M_TypeSet>): DocType {
        val docArgs = args.mapToImmList { L_TypeUtils.docTypeSet(it) }
        return DocType.generic(docCodeStrategy, docArgs)
    }
}

private class C_MGenericTypeAddon_Simple(
    private val rType: R_Type,
    docCodeStrategy: L_TypeDefDocCodeStrategy,
): C_MGenericTypeAddon(docCodeStrategy) {
    override fun getConversion(sourceType: M_Type): M_Conversion_Generic? {
        val rSourceType = L_TypeUtils.getRType(sourceType)
        rSourceType ?: return null

        val adapter = rType.getTypeAdapter(rSourceType)
        adapter ?: return null

        return M_Conversion_CTypeAdapter(adapter)
    }

    override fun getRType(args: List<M_Type>): R_Type {
        checkEquals(args.size, 0)
        return rType
    }
}

private class C_MGenericTypeAddon_LTypeDef(
    private val rTypeFactory: L_TypeDefRTypeFactory?,
    docCodeStrategy: L_TypeDefDocCodeStrategy,
    private val supertypeStrategy: L_TypeDefSupertypeStrategy,
): C_MGenericTypeAddon(docCodeStrategy) {
    override fun isSpecialSuperTypeOf(type: M_Type): Boolean {
        return supertypeStrategy.isSpecialSuperTypeOf(type)
    }

    override fun isPossibleSpecialCompositeSuperTypeOf(type: M_Type_Composite): Boolean {
        return supertypeStrategy.isPossibleSpecialCompositeSuperTypeOf(type)
    }

    override fun getRType(args: List<M_Type>): R_Type? {
        rTypeFactory ?: return null
        val rArgs = args.mapNotNullAllOrNull { L_TypeUtils.getRType(it) }
        rArgs ?: return null
        return rTypeFactory.getRType(rArgs)
    }
}
