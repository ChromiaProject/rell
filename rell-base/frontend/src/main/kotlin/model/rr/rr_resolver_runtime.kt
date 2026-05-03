/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.R_Type

/**
 * Runtime operations needed by the R_ -> RR_ resolver.
 * Defined in the model/rr module, implemented in runtime.
 * Breaks the model/rr -> runtime dependency for the resolver.
 */
interface RR_ResolverRuntime {
    /** Register a system function by name for RR_FunctionCallTarget lookup. */
    fun registerSysFn(name: String, fn: Any)

    /** Encode a constant value as Base64 GTV JSON for metadata. Returns null if not encodable. */
    fun constantMetaGtvJson(value: RR_ConstantValue, type: R_Type): String?

    /**
     * Snapshot the sys-function registrations collected during this compilation. The map is
     * compilation-local: meta-bodies in the stdlib capture compile-specific state
     * (`Rt_ValueClass`/`FilePos`) that must not leak across unrelated compilations. Called after
     * `resolve()` completes; the returned map pairs with the produced `RR_App`.
     */
    fun collectedSysFns(): Map<String, Any>
}
