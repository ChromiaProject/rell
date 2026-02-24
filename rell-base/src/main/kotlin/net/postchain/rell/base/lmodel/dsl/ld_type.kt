/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lmodel.L_TypeParams
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

class Ld_TypeParam(
    val name: R_Name,
    val memberHeader: Ld_MemberHeader,
    private val variance: M_TypeVariance,
    private val bounds: Ld_TypeParamBound?,
) {
    fun finish(ctx: Ld_TypeFinishContext): M_TypeParam {
        val mBounds = bounds?.finish(ctx) ?: M_TypeSets.ALL
        return M_TypeParam(name.str, variance = variance, bounds = mBounds)
    }

    companion object {
        fun make(
            name: String,
            subOf: String?,
            superOf: String?,
            variance: M_TypeVariance = M_TypeVariance.NONE,
            hdr: Ld_MemberHeader,
            block: Ld_MemberDsl.() -> Unit,
        ): Ld_TypeParam {
            val rName = R_Name.of(name)

            check(subOf == null || superOf == null)
            val bound = when {
                subOf != null -> Ld_TypeParamBound(Ld_Type.parse(subOf), true)
                superOf != null -> Ld_TypeParamBound(Ld_Type.parse(superOf), false)
                else -> null
            }

            val memberHeader = Ld_MemberHeader.make(hdr, block)
            return Ld_TypeParam(rName, memberHeader, variance = variance, bound)
        }

        fun finishList(
            ctx: Ld_TypeFinishContext,
            typeParams: List<Ld_TypeParam>,
        ): L_TypeParams {
            val mTypeParams = mutableListOf<M_TypeParam>()
            val mTypeMap = mutableMapOf<R_Name, M_Type>()

            for (param in typeParams) {
                val subCtx = ctx.subCtx(typeParams = mTypeMap)
                val mParam = param.finish(subCtx)
                mTypeParams.add(mParam)
                mTypeMap[param.name] = M_Types.param(mParam)
            }

            return L_TypeParams(mTypeParams.toImmList(), mTypeMap.toImmMap())
        }
    }
}

sealed class Ld_TypeSet {
    abstract fun finish(ctx: Ld_TypeFinishContext): M_TypeSet
}

class Ld_TypeSet_One(val type: Ld_Type): Ld_TypeSet() {
    override fun finish(ctx: Ld_TypeFinishContext): M_TypeSet {
        val mType = type.finish(ctx)
        return M_TypeSets.one(mType)
    }
}

class Ld_TypeSet_SubOf(val type: Ld_Type): Ld_TypeSet() {
    override fun finish(ctx: Ld_TypeFinishContext): M_TypeSet {
        val mType = type.finish(ctx)
        return M_TypeSets.subOf(mType)
    }
}

sealed class Ld_Type {
    abstract fun finish(ctx: Ld_TypeFinishContext): M_Type

    fun finishR(ctx: Ld_TypeFinishContext): R_Type {
        val mType = finish(ctx)
        return L_TypeUtils.getRType(mType)
    }

    companion object {
        fun parse(code: String): Ld_Type {
            return Ld_Parser.parseType(code)
        }
    }
}

class Ld_Type_Name(val typeName: Ld_FullName, private val pos: Exception): Ld_Type() {
    override fun finish(ctx: Ld_TypeFinishContext): M_Type {
        val mType = ctx.getType(typeName, pos)
        return mType
    }
}

class Ld_Type_Generic(
    val typeName: Ld_FullName,
    val args: ImmList<Ld_TypeSet>,
    private val pos: Exception,
): Ld_Type() {
    override fun finish(ctx: Ld_TypeFinishContext): M_Type {
        val typeDef = ctx.getTypeDef(typeName, pos)
        val mArgs = args.mapToImmList { it.finish(ctx) }
        val mType = typeDef.mGenericType.getType(mArgs)
        mType.validate()
        return mType
    }
}

class Ld_Type_Nullable(private val valueType: Ld_Type): Ld_Type() {
    override fun finish(ctx: Ld_TypeFinishContext): M_Type {
        val mValueType = valueType.finish(ctx)
        return M_Types.nullable(mValueType)
    }
}

class Ld_Type_Tuple(private val fields: ImmList<Pair<String?, Ld_Type>>): Ld_Type() {
    override fun finish(ctx: Ld_TypeFinishContext): M_Type {
        val mFieldNames = fields.map { it.first }
        val mFieldTypes = fields.map { it.second.finish(ctx) }
        return M_Types.tuple(mFieldTypes, mFieldNames)
    }
}

class Ld_Type_Function(private val result: Ld_Type, private val params: ImmList<Ld_Type>): Ld_Type() {
    override fun finish(ctx: Ld_TypeFinishContext): M_Type {
        val mResult = result.finish(ctx)
        val mParams = params.map { it.finish(ctx) }
        return M_Types.function(mResult, mParams)
    }
}
