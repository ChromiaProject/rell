/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.transformer;

import kotlin.jvm.functions.Function1;
import net.postchain.rell.base.compiler.base.utils.C_Error;
import net.postchain.rell.base.compiler.parser.RellTokenizerDecodingException;
import net.postchain.rell.lsp.grammar.AntlrActionEx;
import org.antlr.v4.runtime.ParserRuleContext;
final class RellcTransformer {

    private final Function1<Object, Object> transform;

    RellcTransformer(AntlrActionEx action) {
        transform = action.getTransform();
    }

    Object transform(AntlrToRellContext ctx, ParserRuleContext eObject, Object value) {
        return ctx.runWithAttachment(eObject, () -> {
            try {
                return transform.invoke(value);
            } catch (RellTokenizerDecodingException e) {
                var err = e.toCError();
                ctx.addError(err);
                return null;
            } catch (C_Error e) {
                throw e;
            } catch (RuntimeException e) {
                // Can be a normal situation: if there is a syntax error, some AST sub-nodes may be nulls, causing transformer code to fail.
                // Returning null in such case.
                return null;
            }
        });
    }
}

