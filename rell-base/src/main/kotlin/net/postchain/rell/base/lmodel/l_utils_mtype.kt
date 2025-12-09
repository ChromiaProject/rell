/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*

internal object L_TypeUtils {
    private val R_ANYTHING: R_Type = R_GenericType("anything", isAbstract = true)
    private val R_ANY: R_Type = R_GenericType("any", isAbstract = true)
    private val R_NOTHING: R_Type = R_GenericType("nothing", isAbstract = true)

    fun makeMType(
        rType: R_Type,
        name: String,
        parent: M_GenericTypeParent? = null,
        docCodeStrategy: L_TypeDefDocCodeStrategy?,
    ): M_Type {
        val genType = makeMGenericType(rType, name, parent, docCodeStrategy)
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
        rTypeMeta: R_TypeMeta?,
        docCodeStrategy: L_TypeDefDocCodeStrategy?,
        supertypeStrategy: L_TypeDefSupertypeStrategy,
    ): M_GenericType {
        val nameStr = name.qualifiedName.str()
        val addon = C_MGenericTypeAddon_LTypeDef(
            rTypeMeta = rTypeMeta,
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

    fun getRTypeOrNull(mType: M_Type): R_Type? {
        return when (mType) {
            M_Types.ANYTHING -> R_ANYTHING
            M_Types.NOTHING -> R_NOTHING
            M_Types.ANY -> R_ANY
            M_Types.NULL -> R_NullType
            is M_Type_Param -> R_VariableType(mType.param.name)
            is M_Type_Nullable -> getRTypeForNullable(mType)
            is M_Type_Function -> getRTypeForFunction(mType)
            is M_Type_Tuple -> getRTypeForTuple(mType)
            is M_Type_Generic -> getRTypeForGeneric(mType)
            else -> null
        }
    }

    fun getRType(mType: M_Type): R_Type {
        val rType = getRTypeOrNull(mType)
        checkNotNull(rType) { "No R_Type: ${mType.strCode()}" }
        return rType
    }

    private fun getRTypeForNullable(mType: M_Type_Nullable): R_Type? {
        val rValueType = getRTypeOrNull(mType.valueType)
        return if (rValueType == null) null else C_Types.toNullable(rValueType)
    }

    private fun getRTypeForFunction(mType: M_Type_Function): R_Type? {
        val rResult = getRTypeOrNull(mType.resultType)
        val rParams = if (rResult == null) null else mType.paramTypes.mapNotNullAllOrNull { getRTypeOrNull(it) }
        return if (rResult == null || rParams == null) null else R_FunctionType(rParams, rResult)
    }

    private fun getRTypeForTuple(mType: M_Type_Tuple): R_TupleType? {
        val rFields = mType.fieldTypes.indices.mapNotNullAllOrNull { i ->
            val rFieldType = getRTypeOrNull(mType.fieldTypes[i])
            if (rFieldType == null) null else {
                val mName = mType.fieldNames[i]
                val rName = if (mName == null) null else R_IdeName(R_Name.of(mName), C_IdeSymbolInfo.MEM_TUPLE_ATTR)
                R_TupleField(i, rName, rFieldType)
            }
        }
        return if (rFields == null) null else R_TupleType(rFields)
    }

    private fun getRTypeForGeneric(mType: M_Type_Generic): R_Type? {
        val rArgs = mType.typeArgs.withIndex().mapNotNullAllOrNull { (i, mArg) ->
            when (mArg) {
                is M_TypeSet_One -> {
                    val rType = getRTypeOrNull(mArg.type)
                    when (mType.genericType.params[i].variance) {
                        M_TypeVariance.OUT -> rType?.let { R_SubType(it) }
                        else -> rType
                    }
                }
                is M_TypeSet_SubOf -> getRTypeOrNull(mArg.boundType)?.let { R_SubType(it) }
                M_TypeSet_All -> getRTypeOrNull(M_Types.ANYTHING)?.let { R_SubType(it) }
                else -> null
            }
        }
        rArgs ?: return null

        val addon = getTypeAddon(mType)
        val rType = addon.getRType(rArgs)
        return rType ?: R_GenericType(
            mType.genericType.name,
            rArgs,
            isAbstract = true,
            parentMType = mType.getParentType(),
            addon = addon,
        )
    }

    fun docType(mType: M_Type): DocType {
        val rType = getRTypeOrNull(mType)
        return when {
            rType != null -> rType.docType()
            else -> {
                val s = mType.strCode()
                DocType.raw(s)
            }
        }
    }

    fun docTypeSet(mTypeSet: M_TypeSet): DocTypeSet {
        return when (mTypeSet) {
            is M_TypeSet_One -> DocTypeSet.one(docType(mTypeSet.type))
            is M_TypeSet_SubOf -> DocTypeSet.subOf(docType(mTypeSet.boundType))
            else -> DocTypeSet.ALL
        }
    }

    fun docFunctionHeader(header: L_FunctionHeader): DocFunctionHeader {
        val docTypeParams = docTypeParams(header.typeParams)
        val docResultType = header.rResultType.docType()
        val docParams = header.params.mapToImmList { docFunctionParam(it) }
        return DocFunctionHeader(docTypeParams, docResultType, docParams)
    }

    fun docTypeParams(mTypeParams: List<M_TypeParam>): ImmList<DocTypeParam> {
        return mTypeParams.mapToImmList {
            val docBounds = docTypeSet(it.bounds)
            DocTypeParam(it.name, docBounds)
        }
    }

    fun docFunctionParam(param: L_FunctionParam): DocFunctionParam {
        return docFunctionParam(param.name, param.rType, param.arity, exact = param.exact, nullable = param.nullable)
    }

    fun docFunctionParam(
        name: R_Name,
        type: R_Type,
        arity: M_ParamArity,
        exact: Boolean,
        nullable: Boolean,
    ): DocFunctionParam {
        val docType = type.docType()
        return DocFunctionParam(name.str, docType, arity, exact, nullable)
    }

    private fun getTypeAddon(mType: M_Type_Generic): C_MGenericTypeAddon {
        val addon = mType.genericType.addon
        check(addon is C_MGenericTypeAddon) { "$mType ${addon.javaClass.canonicalName}" }
        return addon
    }
}

private sealed class C_MGenericTypeAddon(
    private val docCodeStrategy: L_TypeDefDocCodeStrategy,
): M_GenericTypeAddon() {
    abstract fun getRType(args: ImmList<R_Type>): R_Type?

    final override fun strCode(typeName: String, args: ImmList<M_TypeSet>): String {
        val docArgs = args.map { mTypeSet ->
            DocCode.builder()
                .also { L_TypeUtils.docTypeSet(mTypeSet).genCode(it) }
                .build()
        }
        val docCode = docCodeStrategy.docCode(docArgs)
        return docCode.strRaw()
    }
}

private class C_MGenericTypeAddon_Simple(
    private val rType: R_Type,
    docCodeStrategy: L_TypeDefDocCodeStrategy,
): C_MGenericTypeAddon(docCodeStrategy) {
    override fun getRType(args: ImmList<R_Type>): R_Type {
        checkEquals(args.size, 0)
        return rType
    }
}

private class C_MGenericTypeAddon_LTypeDef(
    private val rTypeMeta: R_TypeMeta?,
    docCodeStrategy: L_TypeDefDocCodeStrategy,
    private val supertypeStrategy: L_TypeDefSupertypeStrategy,
): C_MGenericTypeAddon(docCodeStrategy) {
    override fun isSpecialSuperTypeOf(type: M_Type): Boolean {
        return supertypeStrategy.isSpecialSuperTypeOf(type)
    }

    override fun isPossibleSpecialCompositeSuperTypeOf(type: M_Type_Composite): Boolean {
        return supertypeStrategy.isPossibleSpecialCompositeSuperTypeOf(type)
    }

    override fun getRType(args: ImmList<R_Type>): R_Type? {
        rTypeMeta ?: return null
        return rTypeMeta.getTypeOrNull(args)
    }
}
