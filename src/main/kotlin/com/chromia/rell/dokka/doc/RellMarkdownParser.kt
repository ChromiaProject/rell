package com.chromia.rell.dokka.doc

import com.chromia.rell.dokka.dri.toDRI
import net.postchain.rell.base.model.R_QualifiedName
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.markdown.jb.MarkdownParser

@OptIn(InternalDokkaApi::class)
class RellMarkdownParser(sourceLocation: String? = null) : MarkdownParser(
        {
            try {
                R_QualifiedName.of(it).toDRI()
            } catch (e: Exception) {
                null
            }
        },
        sourceLocation
)