/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.transformer

import com.google.common.collect.ImmutableList
import net.postchain.rell.base.compiler.ast.S_Node
import net.postchain.rell.base.compiler.base.utils.C_Error
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStream

class AntlrToRellContext(val tokenStream: TokenStream) {

    private val attachmentProvider = AntlrAttachmentProvider()
    private val errors = mutableListOf<C_Error>()

    fun addError(error: C_Error) {
        errors.add(error)
    }

    fun <T> runWithAttachment(node: ParserRuleContext?, code: () -> T): T {
        attachmentProvider.node = node
        attachmentProvider.attachment = null
        return try {
            code()
        } finally {
            attachmentProvider.node = null
            attachmentProvider.attachment = null
        }
    }

    companion object {
        fun <T> runWithContext(tokenStream: TokenStream, code: (AntlrToRellContext) -> T): Pair<T, List<C_Error>> {
            val list = mutableListOf<T?>()
            val ctx = AntlrToRellContext(tokenStream)
            S_Node.runWithAttachmentProvider(ctx.attachmentProvider) {
                val res0 = try {
                    code(ctx)
                } catch (error: C_Error) {
                    ctx.addError(error)
                    null
                }
                list.add(res0)
            }
            check(list.size == 1) { "${list.size}" }
            val res = list[0]!!
            return Pair(res, ImmutableList.copyOf(ctx.errors))
        }
    }
}
