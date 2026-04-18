/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.testutils

import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.formatEx
import net.postchain.rell.module.RellPostchainModuleFactory

fun main() {
    val code = (0 .. 999)
        .flatMap { i ->
            listOf(
                "val s$i = '$i';",
                "val i$i = $i;",
                "val b$i = x'${"%08x".formatEx(i)}';",
            )
        }
        .joinToString("\n")

    val baseConfig = PostchainGtvUtils.jsonToGtv("""
        {
            "gtx":{
                "rell":{
                    "version":"0.14.0",
                    "modules":["main"],
                    "sources":{
                        "main.rell":"module; $code"
                    }
                }
            }
        }
    """)

    (0 .. 100).toList().parallelStream().forEach {
        val cfg = baseConfig.asDict().toMutableMap()
        cfg["make_unique"] = GtvFactory.gtv(it.toLong())
        RellPostchainModuleFactory().makeModule(GtvFactory.gtv(cfg), BlockchainRid.buildRepeat(it.toByte()))
    }
}
