/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode

class NodeInterval : Interval {
    constructor(node: ParserRuleContext) : super(node.start.startIndex, node.stop.stopIndex)
    constructor(node: TerminalNode) : super(node.symbol.startIndex, node.symbol.stopIndex)
}
