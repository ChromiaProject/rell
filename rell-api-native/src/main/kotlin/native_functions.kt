/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.nativ

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv

public interface RellNativeEnvironment {
    public val config: Gtv
    public val blockchainRid: BlockchainRid
}
