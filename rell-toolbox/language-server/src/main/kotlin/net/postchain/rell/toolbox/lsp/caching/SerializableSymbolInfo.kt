package net.postchain.rell.toolbox.lsp.caching

import net.postchain.rell.base.utils.ide.IdeSymbolId
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.ide.IdeSymbolLink

class SerializableSymbolInfo(
    val kind: IdeSymbolKind,
    val defId: IdeSymbolId?,
    val link: IdeSymbolLink?
)
