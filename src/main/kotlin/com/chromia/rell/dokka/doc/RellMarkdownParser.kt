package com.chromia.rell.dokka.doc

import com.chromia.rell.dokka.dri.toDRI
import net.postchain.rell.base.model.R_QualifiedName
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.markdown.jb.MarkdownParser
import org.jetbrains.dokka.model.doc.See
import org.jetbrains.dokka.model.doc.Throws

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
) {
    fun parseStringToSeeTag(content: String): See {
        val referencedName = content.substringBefore(' ')
        return See(
            root = this.parseStringToDocNode(content.substringAfter(' ')),
            name = referencedName,
            address = null
        )
    }

    fun parseStringToThrowTag(content: String): Throws {
        val exceptionName = content.substringBefore(' ')
        val description = content.substringAfter(' ').takeIf { it != exceptionName } ?: ""

        return Throws(
            this.parseStringToDocNode(description),
            exceptionName,
            null
        )
    }
}
