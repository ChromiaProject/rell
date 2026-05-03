/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.runtime.GtvToRtContext
import net.postchain.rell.base.runtime.PostchainGtvUtils
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.mapValuesToImmMap

object GtvTestUtils {
    fun decodeGtvStr(s: String) = PostchainGtvUtils.jsonToGtv(s)
    fun encodeGtvStr(gtv: Gtv) = PostchainGtvUtils.gtvToJson(gtv)

    fun decodeGtvStrQueryArgs(params: List<Rt_ValueClass<*>>, args: List<String>): List<Rt_Value> {
        return decodeGtvStrArgs(params, args, true)
    }

    fun decodeGtvQueryArgs(params: List<Rt_ValueClass<*>>, args: List<Gtv>): List<Rt_Value> {
        return decodeGtvArgs(params, args, true)
    }

    fun decodeGtvOpArgs(params: List<Rt_ValueClass<*>>, args: List<Gtv>): List<Rt_Value> {
        checkEquals(args.size, params.size)
        return decodeGtvArgs(params, args, false)
    }

    fun decodeGtvStrOpArgs(params: List<Rt_ValueClass<*>>, args: List<String>): List<Rt_Value> {
        return decodeGtvStrArgs(params, args, false)
    }

    private fun decodeGtvStrArgs(params: List<Rt_ValueClass<*>>, args: List<String>, pretty: Boolean): List<Rt_Value> {
        checkEquals(args.size, params.size)
        val gtvArgs = args.map { decodeGtvStr(it) }
        return decodeGtvArgs(params, gtvArgs, pretty)
    }

    private fun decodeGtvArgs(params: List<Rt_ValueClass<*>>, args: List<Gtv>, pretty: Boolean): List<Rt_Value> {
        checkEquals(args.size, params.size)
        val ctx = GtvToRtContext.make(pretty = pretty)
        return args.mapIndexed { i, arg ->
            params[i].gtvConversion!!.gtvToRt(ctx, arg)
        }
    }

    fun gtvToStr(gtv: Gtv): String {
        val s = encodeGtvStr(gtv)
        return s.replace('"', '\'').replace("\\u003c", "<").replace("\\u003e", ">").replace("\\u003d", "=")
    }

    fun strToGtv(s: String): Gtv {
        val s2 = s.replace('\'', '"')
        return decodeGtvStr(s2)
    }

    private fun merge(v1: Gtv, v2: Gtv): Gtv {
        checkEquals(v2.type, v1.type)
        return when (v1.type) {
            GtvType.ARRAY -> GtvFactory.gtv(v1.asArray().toList() + v2.asArray().toList())
            GtvType.DICT -> {
                val m1 = v1.asDict()
                val m2 = v2.asDict()
                val m = (m1.keys + m2.keys).toSet().map {
                    val x1 = m1[it]
                    val x2 = m2[it]
                    val x = if (x1 == null) x2 else if (x2 == null) x1 else merge(x1, x2)
                    it to x!!
                }.toMap()
                GtvFactory.gtv(m)
            }
            else -> v2
        }
    }

    fun moduleArgsToGtv(moduleArgs: Map<String, String>): Gtv {
        val map = moduleArgsToMap(moduleArgs)
        return GtvFactory.gtv(map)
    }

    fun moduleArgsToMap(moduleArgs: Map<String, String>): ImmMap<String, Gtv> {
        return moduleArgs.mapValuesToImmMap { (_, v) -> decodeGtvStr(v) }
    }
}
