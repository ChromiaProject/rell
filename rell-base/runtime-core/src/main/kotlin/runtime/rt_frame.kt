/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.ErrorPos

/**
 * Marker interface for an activation record handed back to the interpreter implementation.
 *
 * The tree-walking [Rt_Interpreter] implements this with `Rt_CallFrame` (in `runtime-interpreter`),
 * which holds an `Array<Rt_Value?>` and block-scope tracking. A future Truffle backend would supply
 * its own `VirtualFrame`-backed implementation. Code in `runtime-core` only needs an opaque handle
 * to thread frames through the interpreter API, plus the small surface declared here.
 */
interface Rt_Frame {
    /**
     * Reports a runtime error tagged with a source position. Used by stdlib helpers (e.g. list
     * index-bounds check) that participate in expression evaluation and want the resulting
     * [Rt_Exception] to carry the calling site's stack frame.
     */
    fun error(pos: ErrorPos, code: String, msg: String): Nothing
}
