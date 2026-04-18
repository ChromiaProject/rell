/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.hover

import net.postchain.rell.base.utils.doc.DocCommentTag
import net.postchain.rell.base.utils.doc.DocDeclaration
import net.postchain.rell.base.utils.doc.DocSymbol

fun formatDocSymbol(docSymbol: DocSymbol?): String {
    if (docSymbol == null) return ""
    val declaration = createDocDeclaration(docSymbol.declaration)
    return buildString {
        append(declaration)
        docSymbol.comment?.let { comment ->
            val mappedTags = comment.tags.mapKeys { it.key.code }
            appendLine()
            appendLine(comment.description)
            mappedTags[DocCommentTag.SINCE.code]?.let { tag -> appendLine("\n*since:* ${tag.first().text}") }
            mappedTags[DocCommentTag.SEE.code]?.let { tag ->
                appendLine(
                    "\n*See also:* ${tag.joinToString(", ") { it.text }}"
                )
            }
            mappedTags[DocCommentTag.PARAM.code]?.let { items ->
                items.forEach {
                    appendLine("\n*@param* `${it.key}` - ${it.text}")
                }
            }
            mappedTags[DocCommentTag.RETURN.code]?.let { tag -> appendLine("\n*@return* - ${tag.first().text}") }
        }
    }
}

private fun createDocDeclaration(decl: DocDeclaration): String {
    if (decl == DocDeclaration.NONE) return ""
    return buildString {
        // We use typescript as language here since it provides similar semantic keyword colors.
        append("```typescript")
        appendLine()
        decl.code.visit(DocDeclarationVisitor(this))
        appendLine()
        appendLine("```")
    }
}
