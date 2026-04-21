/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.GtvEncoder
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.model.rr.RR_ResolverRuntime
import java.util.*

/**
 * Per-compilation resolver runtime. Each `compile()` call creates a fresh instance; sys-function
 * registrations emitted by the resolver are collected locally rather than into a process-global
 * pool. After resolve, [collectedSysFns] yields the compilation-local registry that pairs with
 * the produced [net.postchain.rell.base.model.rr.RR_App] when constructing [Rt_Interpreter].
 *
 * Isolation is critical: stdlib meta-bodies (e.g. `rell.gtv_ext(T).to_gtv`, `log()`) capture
 * compile-specific state (`Rt_Type`, `FilePos`). Merging registrations from independent compiles
 * into a single shared map would let test A's interpreter dispatch to test B's closure and vice
 * versa — corrupting attribute layouts, entity references, and call-position metadata.
 */
class Rt_ResolverRuntime: RR_ResolverRuntime {
    private val sysFns = mutableMapOf<String, R_SysFunction>()

    override fun registerSysFn(name: String, fn: Any) {
        // Last-write-wins within one compilation is harmless: meta-bodies that share a key
        // are produced from the same DSL definition with the same meta, so the final closure
        // is behaviorally equivalent to any earlier one.
        sysFns[name] = fn as R_SysFunction
    }

    override fun constantMetaGtvJson(value: RR_ConstantValue, type: R_Type): String? {
        val rtValue = rrConstantToRtValue(value) ?: return null
        val gtvConversion = rTypeToRtType(type).gtvConversion ?: return null
        return try {
            val gtv = gtvConversion.rtToGtv(rtValue, true)
            Base64.getEncoder().encodeToString(GtvEncoder.encodeGtv(gtv))
        } catch (_: Throwable) {
            null
        }
    }

    /** Snapshot the sys functions registered during this compilation. */
    override fun collectedSysFns(): Map<String, Any> = sysFns.toMap()
}
