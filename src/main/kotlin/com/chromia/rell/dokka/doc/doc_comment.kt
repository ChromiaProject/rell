package com.chromia.rell.dokka.doc

import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocSymbol
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.*

fun DocSymbol.toDocumentationNode() = comment?.formatDescription() ?: DocumentationNode(listOf())

fun DocComment.formatDescription() = DocumentationNode(
        buildList {
            add(Description(Text(description.substringBefore("\n")))) // Short text on main page
            add(Description(P(listOf(Text(description)))))                    // Full text on site
            tags[DocCommentTag.SEE]?.forEach { add(See(P(listOf(Text(it.text))), name = it.text, address = null)) }
            tags[DocCommentTag.PARAM]?.forEach { add(Param(P(listOf(Text(it.text))), it.key!!)) }
            tags[DocCommentTag.RETURNS]?.let { add(Return(P(listOf(Text(it.first().text))))) }
            tags[DocCommentTag.SINCE]?.let { add(Since(P(listOf(Text(it.first().text))))) }
        }
)