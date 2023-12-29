package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import net.postchain.rell.toolbox.core.compiler.AntlrPos
import org.antlr.v4.runtime.misc.Interval
import java.util.*

fun intervalCompare(intervalA: Interval, intervalB: Interval): Int {
    return if (intervalA == intervalB ||
        intervalA.properlyContains(intervalB) ||
        intervalB.properlyContains(intervalA)
    ) {
        0
    } else if (intervalA.startsAfter(intervalB)) {
        1
    } else {
        -1
    }
}

fun createLocationInfo(symbolInfos: Map<S_Pos, IdeSymbolInfo>): Map<Interval, IdeSymbolInfoWithInterval> {
    val intervalMap = symbolInfos.map {
        val node = NodeInterval((it.key as AntlrPos).node)
        node to IdeSymbolInfoWithInterval(it.value, node)
    }
    val locationInfo = TreeMap<Interval, IdeSymbolInfoWithInterval>(::intervalCompare)
    locationInfo.putAll(intervalMap)
    return locationInfo
}