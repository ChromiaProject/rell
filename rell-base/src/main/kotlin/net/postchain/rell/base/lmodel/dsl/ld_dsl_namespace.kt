/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import java.math.BigDecimal
import java.math.BigInteger

@RellLibDsl
interface Ld_CommonNamespaceDsl {
    fun constant(
        name: String,
        value: Long,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun constant(
        name: String,
        value: BigInteger,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun constant(
        name: String,
        value: BigDecimal,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun constant(
        name: String,
        type: String,
        value: Rt_Value,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun constant(
        name: String,
        type: String,
        since: String? = null,
        comment: String? = null,
        block: Ld_ConstantDsl.() -> Ld_BodyResult,
    )
}

@RellLibDsl
interface Ld_NamespaceBodyDsl: Ld_CommonNamespaceDsl {
    fun include(namespace: Ld_Namespace)

    fun alias(
        name: String? = null,
        target: String,
        deprecated: C_Deprecated? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun alias(
        name: String? = null,
        target: String,
        deprecated: C_MessageType,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )

    fun namespace(
        name: String,
        since: String? = null,
        comment: String? = null,
        block: Ld_NamespaceDsl.() -> Unit,
    )

    fun type(
        name: String,
        abstract: Boolean = false,
        hidden: Boolean = false,
        rType: R_Type? = null,
        since: String? = null,
        comment: String? = null,
        block: Ld_TypeDefDsl.() -> Unit = {},
    )

    fun extension(
        name: String,
        type: String,
        since: String? = null,
        comment: String? = null,
        block: Ld_TypeExtensionDsl.() -> Unit,
    )

    fun struct(
        name: String,
        since: String? = null,
        comment: String? = null,
        block: Ld_StructDsl.() -> Unit,
    )

    fun property(
        name: String,
        type: String,
        pure: Boolean = false,
        since: String? = null,
        comment: String? = null,
        block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult,
    )

    fun property(
        name: String,
        property: C_NamespaceProperty,
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
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    )

    fun function(
        name: String,
        fn: C_SpecialLibGlobalFunctionBody,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )
}

@RellLibDsl
interface Ld_NamespaceDsl: Ld_NamespaceBodyDsl, Ld_MemberDsl {
    companion object {
        fun make(block: Ld_NamespaceBodyDsl.() -> Unit): Ld_Namespace {
            val builder = Ld_NamespaceBuilder()
            val dsl = Ld_NamespaceDslImpl(builder)
            block(dsl)
            return builder.build()
        }
    }
}

@RellLibDsl
interface Ld_NamespacePropertyDsl: Ld_MemberDsl {
    fun validate(validator: (C_SysFunctionCtx) -> Unit)
    fun value(block: (Rt_CallContext) -> Rt_Value): Ld_BodyResult
}

@RellLibDsl
interface Ld_StructDsl: Ld_MemberDsl {
    fun attribute(
        name: String,
        type: String,
        mutable: Boolean = false,
        since: String? = null,
        comment: String? = null,
        block: Ld_MemberDsl.() -> Unit = {},
    )
}

@RellLibDsl
interface Ld_ConstantDsl: Ld_MemberDsl {
    fun value(getter: (R_Type) -> Rt_Value): Ld_BodyResult
}
