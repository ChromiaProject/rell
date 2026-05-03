/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv

sealed class Rt_VirtualCollectionValue(gtv: Gtv): Rt_VirtualValue(gtv) {
    abstract fun size(): Int

    companion object: Rt_ValueClass<Rt_VirtualCollectionValue> {
        override val name
            get() = "virtual_collection"

        override val klass = Rt_VirtualCollectionValue::class
    }
}
