/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.nativ

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

interface RellNativeEnvironment {
    val config: Gtv
    val blockchainRid: BlockchainRid
}
