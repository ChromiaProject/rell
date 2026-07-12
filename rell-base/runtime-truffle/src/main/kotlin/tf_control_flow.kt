/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.nodes.ControlFlowException
import net.postchain.rell.base.runtime.Rt_Value

/**
 * Control-flow escapes for `return` / `break` / `continue`, following Truffle's conventional
 * [ControlFlowException] design: statement nodes throw, loops catch `break`/`continue` around
 * their body, and the function-body root catches [Tf_ReturnException]. The exceptions are
 * stackless (no stack trace, no suppression); [Tf_BreakException] and [Tf_ContinueException] are
 * singletons, and PE's escape analysis removes a [Tf_ReturnException] allocation whenever the
 * throw and the catch land in one compilation unit.
 *
 * History: an earlier exception-based design was once replaced by integer status codes after
 * FT4-style profiles (many small Rell calls, PE-inlining blocked at stdlib boundaries) showed
 * unfolded exception handling dominating `rule_eval`. The exceptions are back deliberately: value
 * blocks (`{ stmt; ...; result }` as if/when expression arms) need an exception channel anyway -
 * an expression-shaped `execute` has no status to return - and one conventional mechanism for all
 * control flow beats two coexisting ones. The ergonomics were judged worth the cost.
 */
internal class Tf_ReturnException(
    @JvmField val value: Rt_Value?,
): ControlFlowException()

/** `break` - caught by the nearest enclosing loop node. */
internal class Tf_BreakException private constructor(): ControlFlowException() {
    companion object {
        @JvmField val INSTANCE = Tf_BreakException()
    }
}

/** `continue` - caught by the nearest enclosing loop node. */
internal class Tf_ContinueException private constructor(): ControlFlowException() {
    companion object {
        @JvmField val INSTANCE = Tf_ContinueException()
    }
}
