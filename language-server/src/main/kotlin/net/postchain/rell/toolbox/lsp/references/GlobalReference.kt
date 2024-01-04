package net.postchain.rell.toolbox.lsp.references

import net.postchain.rell.base.utils.ide.IdeSymbolId
import java.net.URI

data class GlobalReference(val fileUri: URI, val symbolId: IdeSymbolId)
