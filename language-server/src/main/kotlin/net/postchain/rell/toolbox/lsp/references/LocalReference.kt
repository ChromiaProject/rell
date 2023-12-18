package net.postchain.rell.toolbox.lsp.references

import org.antlr.v4.runtime.misc.Interval
import java.net.URI

class LocalReference(val fileUri: URI, val interval: Interval) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocalReference

        if (fileUri != other.fileUri) return false
        if (interval != other.interval) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileUri.hashCode()
        result = 31 * result + interval.hashCode()
        return result
    }
}