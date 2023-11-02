package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.utils.ide.IdeModuleInfo
import net.postchain.rell.toolbox.core.parser.RellParser

data class Resource(val parseTree: RellParser.RuleX_RootParserContext, val moduleInfo: IdeModuleInfo) {
    val rName = moduleInfo.name
    val imports = moduleInfo.imports
}