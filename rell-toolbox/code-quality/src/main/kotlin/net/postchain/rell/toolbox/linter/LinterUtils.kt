/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import org.antlr.v4.runtime.ParserRuleContext

fun ParserRuleContext.isUnderscore(): Boolean = this.text == "_"
