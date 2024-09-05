package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.utils.ide.IdeSymbolInfo
import org.antlr.v4.runtime.misc.Interval

data class IdeSymbolInfoWithInterval(val ideSymbolInfo: IdeSymbolInfo, val interval: Interval)