/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_TypeMeta
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Composite
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.utils.doc.DocCode

@RellLibDsl
interface Ld_CommonTypeDsl<T : Rt_Value>: Ld_MemberDsl {
    fun generic(
        name: String,
        subOf: String? = null,
        superOf: String? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun property(
        name: String,
        type: String,
        pure: Boolean = false,
        since: String? = null,
        comment: String? = null,
        block: Ld_TypePropertyDsl.() -> Ld_BodyResult,
    )

    fun property(
        name: String,
        type: String,
        body: C_SysFunctionBody,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun function(
        name: String,
        result: String? = null,
        pure: Boolean? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MethodDsl<T>.() -> Ld_BodyResult,
    )

    fun function(
        name: String,
        fn: C_SpecialLibMemberFunctionBody,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun staticFunction(
        name: String,
        result: String? = null,
        pure: Boolean? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    )

    fun staticFunction(
        name: String,
        fn: C_SpecialLibGlobalFunctionBody,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )
}

@RellLibDsl
interface Ld_TypeDefDsl<T : Rt_Value>: Ld_CommonTypeDsl<T>, Ld_CommonNamespaceDsl {
    val typeSimpleName: String

    fun parent(type: String)

    fun rrType(rrType: RR_Type)

    fun rType(rType: R_Type)
    fun rType(factory: (R_Type) -> R_Type?)
    fun rType(factory: (R_Type, R_Type) -> R_Type?)
    fun rType(factory: (R_Type, R_Type, R_Type) -> R_Type?)
    fun rTypeMeta(rTypeMeta: R_TypeMeta)

    fun docCode(calculator: (DocCode) -> DocCode)
    fun docCode(calculator: (DocCode, DocCode) -> DocCode)
    fun docCode(calculator: (DocCode, DocCode, DocCode) -> DocCode)

    fun supertypeStrategySpecial(predicate: (M_Type) -> Boolean)
    fun supertypeStrategyComposite(predicate: (M_Type_Composite) -> Boolean)

    fun constructor(
        pure: Boolean? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_ConstructorDsl.() -> Ld_BodyResult,
    )

    fun constructor(
        fn: C_SpecialLibGlobalFunctionBody,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )
}

@RellLibDsl
interface Ld_TypeExtensionDsl<T : Rt_Value>: Ld_CommonTypeDsl<T>

@RellLibDsl
interface Ld_ConstructorDsl: Ld_CommonFunctionDsl

@RellLibDsl
interface Ld_TypePropertyDsl: Ld_MemberDsl {
    fun value(getter: (Rt_Value) -> Rt_Value): Ld_BodyResult
    fun value(getter: (Rt_Value, R_Type) -> Rt_Value): Ld_BodyResult

    /**
     * Like [value], but the receiver is cast to [self] up front, so the getter sees a typed
     * `self` instead of a raw [Rt_Value]: `value(Rt_GtvValue) { self -> ... }`.
     */
    fun <T : Rt_Value> value(self: Rt_ValueClass<T>, getter: (T) -> Rt_Value): Ld_BodyResult
    fun <T : Rt_Value> value(self: Rt_ValueClass<T>, getter: (T, R_Type) -> Rt_Value): Ld_BodyResult
}
