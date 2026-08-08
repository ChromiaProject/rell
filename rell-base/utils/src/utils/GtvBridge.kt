/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.gtv.Gtv as PcGtv
import net.postchain.gtv.GtvFactory as PcGtvFactory
import net.postchain.gtv.GtvType as PcGtvType
import net.postchain.gtv.GtvVirtual as PcGtvVirtual
import net.postchain.gtvmin.Gtv as MinGtv
import net.postchain.gtvmin.GtvFactory as MinGtvFactory
import net.postchain.gtvmin.GtvType as MinGtvType
import net.postchain.gtvmin.GtvVirtual as MinGtvVirtual

/**
 * Translates GTV values across the seam between Postchain and Rell.
 *
 * Rell's runtime is built on `net.postchain.gtvmin`, a reimplementation of the format that carries no
 * dependencies; Postchain's own interfaces — module configuration, queries, operation arguments, events —
 * speak `net.postchain.gtv`. The two libraries are deliberately relocated so both can sit on one classpath,
 * and this is the only place that knows about both.
 *
 * The mapping is total and structural: every GTV type has the same meaning and the same DER encoding in both
 * libraries, and both sort dictionary keys on construction, so a value's merkle hash is unchanged by a round
 * trip. That equivalence is what makes converting safe in consensus code, and it is asserted byte for byte by
 * the differential suite in postchain-gtv-min rather than assumed here.
 */
object GtvBridge {

    /** Postchain GTV to Rell GTV. */
    fun toRell(gtv: PcGtv): MinGtv {
        // Virtual GTV carries a merkle proof tree, not just a value. Rell builds virtuals itself while
        // decoding arguments, so one arriving here would mean the seam moved.
        if (gtv is PcGtvVirtual) throw IllegalArgumentException("Cannot convert a virtual GTV to Rell GTV")

        return when (gtv.type) {
            PcGtvType.NULL -> net.postchain.gtvmin.GtvNull
            PcGtvType.INTEGER -> MinGtvFactory.gtv(gtv.asInteger())
            PcGtvType.BIGINTEGER -> MinGtvFactory.gtv(gtv.asBigInteger())
            PcGtvType.STRING -> MinGtvFactory.gtv(gtv.asString())
            PcGtvType.BYTEARRAY -> MinGtvFactory.gtv(gtv.asByteArray())
            PcGtvType.ARRAY -> MinGtvFactory.gtv(gtv.asArray().map { toRell(it) })
            PcGtvType.DICT -> MinGtvFactory.gtv(gtv.asDict().mapValues { toRell(it.value) })
        }
    }

    /** Rell GTV to Postchain GTV. */
    fun toPostchain(gtv: MinGtv): PcGtv {
        if (gtv is MinGtvVirtual) throw IllegalArgumentException("Cannot convert a virtual GTV to Postchain GTV")

        return when (gtv.type) {
            MinGtvType.NULL -> net.postchain.gtv.GtvNull
            MinGtvType.INTEGER -> PcGtvFactory.gtv(gtv.asInteger())
            MinGtvType.BIGINTEGER -> PcGtvFactory.gtv(gtv.asBigInteger())
            MinGtvType.STRING -> PcGtvFactory.gtv(gtv.asString())
            MinGtvType.BYTEARRAY -> PcGtvFactory.gtv(gtv.asByteArray())
            MinGtvType.ARRAY -> PcGtvFactory.gtv(gtv.asArray().map { toPostchain(it) })
            MinGtvType.DICT -> PcGtvFactory.gtv(gtv.asDict().mapValues { toPostchain(it.value) })
        }
    }
}
