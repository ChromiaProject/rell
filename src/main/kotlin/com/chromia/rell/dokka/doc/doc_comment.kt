package com.chromia.rell.dokka.doc

import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.TagWrapper
import org.jetbrains.dokka.model.doc.Param
import org.jetbrains.dokka.model.doc.Return
import org.jetbrains.dokka.model.doc.Since
import org.jetbrains.dokka.model.doc.Author
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Text

fun DocSymbol.toDocumentationNode(additionalTags: TagWrapper? = null) = comment?.formatDescription(additionalTags) ?: DocumentationNode(listOf())

fun DocComment.formatDescription(additionalTags: TagWrapper? = null) = DocumentationNode(
        RellMarkdownParser().parse(description).children +
                buildList {
                    val p = RellMarkdownParser()

                    tags[DocCommentTag.SEE]?.forEach { add(p.parseStringToSeeTag(it.text)) }
                    tags[DocCommentTag.PARAM]?.forEach { add(Param(p.parseStringToDocNode(it.text), it.key!!)) }
                    tags[DocCommentTag.RETURN]?.let { add(Return(p.parseStringToDocNode(it.first().text))) }
                    //tags[DocCommentTag.RETURNS]?.let { add(Return(P(listOf(Text(it.first().text))))) }
                    tags[DocCommentTag.SINCE]?.let { add(Since(p.parseStringToDocNode(it.first().text))) }
                    tags[DocCommentTag.AUTHOR]?.let { add(Author(p.parseStringToDocNode(it.first().text))) }
                    tags[DocCommentTag.THROWS]?.let { it.forEach { item -> add(p.parseStringToThrowTag(item.text)) }  }
                    additionalTags?.let { add(it) }
                }
)

fun simpleDocumentationNode(shortDescription: String, longDescription: String = shortDescription) = DocumentationNode(
        listOf(
                Description(Text(shortDescription)),
                Description(P(listOf(Text(longDescription))))
        )
)
