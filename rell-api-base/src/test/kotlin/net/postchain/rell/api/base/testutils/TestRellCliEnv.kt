/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base.testutils

import net.postchain.rell.api.base.PrinterRellCliEnv
import net.postchain.rell.api.base.RellCliEnv

class TestRellCliEnv: RellCliEnv by PrinterRellCliEnv(::println)
