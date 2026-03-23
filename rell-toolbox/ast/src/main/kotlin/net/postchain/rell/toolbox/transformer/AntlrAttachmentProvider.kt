/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.transformer

import org.antlr.v4.runtime.ParserRuleContext
import java.util.function.Supplier

class AntlrAttachmentProvider : Supplier<Any?> {
    var node: ParserRuleContext? = null
    var attachment: AntlrRellNodeAttachment? = null

    override fun get(): Any? {
        return if (attachment != null) {
            attachment
        } else if (node == null) {
            null
        } else {
            attachment = createAttachment(node)
            node = null
            attachment
        }
    }

    companion object {
        private fun createAttachment(node: ParserRuleContext?): AntlrRellNodeAttachment? {
            if (node == null) return null
            return AntlrRellNodeAttachment(node)
        }
    }
}
