/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.KeyIndexKind
import net.postchain.rell.base.model.Name

/**
 * Entity or struct attribute (e.g. `mutable name: text = 'default'`).
 * Carries the attribute type, mutability, key/index membership, SQL column mapping,
 * optional default value expression, and optional size constraint for validation.
 */
@JvmRecord data class RR_Attribute(
    val index: Int,
    val rName: Name,
    val type: RR_Type,
    val mutable: Boolean,
    val keyIndexKind: KeyIndexKind?,
    val canSetInCreate: Boolean,
    val sqlMapping: String,
    val defaultExpr: RR_Expr?,
    val isDbModification: Boolean,
    val sizeConstraint: RR_SizeConstraint?,
) {
    val hasDefaultValue: Boolean get() = defaultExpr != null
    val name: String get() = rName.str

    override fun toString() = name
}

/** Whether the size constraint applies to a `byte_array` or `text` value. */
enum class RR_SizeConstraintKind {
    BYTE_ARRAY,
    TEXT,
}

/**
 * Min/max size constraint for `text` and `byte_array` attributes,
 * validated at runtime on `create` and `update`.
 */
@JvmRecord data class RR_SizeConstraint(
    val min: Long?,
    val max: Long?,
    val kind: RR_SizeConstraintKind,
    /** Error code prefix, e.g. "query:foo:parameter:bar" for generating "query:foo:parameter:bar:validator:size:too_small". */
    val codePrefix: String,
) {
    init {
        require(min != null || max != null) { "min and max cannot both be null" }
    }
}
