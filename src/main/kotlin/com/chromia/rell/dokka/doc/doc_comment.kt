package com.chromia.rell.dokka.doc

import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.*

fun DocSymbol.toDocumentationNode(additionalTags: TagWrapper? = null) = comment?.formatDescription(additionalTags) ?: DocumentationNode(listOf())

fun DocComment.formatDescription(additionalTags: TagWrapper? = null) = DocumentationNode(
        RellMarkdownParser().parse(description).children +
                buildList {
                    val p = RellMarkdownParser()

                    tags[DocCommentTag.SEE]?.forEach { add(See(P(listOf(Text(it.text))), name = it.text, address = null)) }
                    tags[DocCommentTag.PARAM]?.forEach { add(Param(p.parseStringToDocNode(it.text), it.key!!)) }
                    tags[DocCommentTag.RETURNS]?.let { add(Return(p.parseStringToDocNode(it.first().text))) }
                    //tags[DocCommentTag.RETURNS]?.let { add(Return(P(listOf(Text(it.first().text))))) }
                    tags[DocCommentTag.SINCE]?.let { add(Since(p.parseStringToDocNode(it.first().text))) }
                    additionalTags?.let { add(it) }
                }
)

fun simpleDocumentationNode(shortDescription: String, longDescription: String = shortDescription) = DocumentationNode(
        listOf(
                Description(Text(shortDescription)),
                Description(P(listOf(Text(longDescription))))
        )
)
