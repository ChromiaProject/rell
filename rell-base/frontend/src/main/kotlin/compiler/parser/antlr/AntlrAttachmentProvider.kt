/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser.antlr

import org.antlr.v4.runtime.ParserRuleContext

/**
 * Provides an `AntlrRellNodeAttachment` for each `S_Node` constructed during a visit.
 *
 * The `node` field is mutated by `RellAntlrVisitor` as it pushes/pops contexts. Each
 * `S_Node` constructor calls this provider once, getting an attachment wrapping the
 * current top-of-stack context.
 */
internal class AntlrAttachmentProvider: () -> Any? {
    var node: ParserRuleContext? = null

    override fun invoke(): Any? = node?.let(::AntlrRellNodeAttachment)
}
