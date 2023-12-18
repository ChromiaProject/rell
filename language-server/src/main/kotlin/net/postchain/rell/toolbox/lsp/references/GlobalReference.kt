package net.postchain.rell.toolbox.lsp.references

import net.postchain.rell.base.utils.ide.IdeSymbolId
import java.net.URI

class GlobalReference(val fileUri: URI, val symbolId: IdeSymbolId) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GlobalReference

        if (fileUri != other.fileUri) return false
        if (symbolId != other.symbolId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileUri.hashCode()
        result = 31 * result + symbolId.hashCode()
        return result
    }
}