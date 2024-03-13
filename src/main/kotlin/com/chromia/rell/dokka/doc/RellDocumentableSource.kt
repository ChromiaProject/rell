package com.chromia.rell.dokka.doc

import org.jetbrains.dokka.model.DocumentableSource

class RellDocumentableSource: DocumentableSource {
    override val path: String
        get() = ""

    override fun computeLineNumber(): Int? {
        return null
    }

    companion object {
        val NULL = RellDocumentableSource()
    }
}