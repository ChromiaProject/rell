/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.*
import com.github.h0tk3y.betterParse.parser.*
import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.model.AtCardinality
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.plus

internal class G_Node<out T>(val value: T, val firstToken: RellTokenMatch)

internal sealed class G_BaseExprTail {
    abstract fun toExpr(base: S_Expr): S_Expr

    companion object {
        fun tailsToExpr(head: S_Expr, tails: List<G_BaseExprTail>): S_Expr {
            val (head2, tails2) = combineNames(head, tails)
            var expr = head2
            for (tail in tails2) {
                expr = tail.toExpr(expr)
            }
            return expr
        }

        private fun combineNames(head: S_Expr, tails: List<G_BaseExprTail>): Pair<S_Expr, List<G_BaseExprTail>> {
            //TODO consider a cleaner way, without type casts
            if (head !is S_NameExpr) return Pair(head, tails)
            val members = tails.map { (it as? G_BaseExprTail_Member)?.name }.takeWhile { it != null }.map { it!! }
            if (members.isEmpty()) return Pair(head, tails)
            val qName = S_QualifiedName(head.qName.parts + members)
            val head2 = S_NameExpr(qName)
            val tails2 = tails.drop(members.size)
            return Pair(head2, tails2)
        }
    }
}

internal class G_BaseExprTail_Member(val name: S_Name): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_MemberExpr(base, name)
}

internal class G_BaseExprTail_SafeMember(val name: S_Name): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_SafeMemberExpr(base, name)
}

internal class G_BaseExprTail_Subscript(val pos: S_Pos, val expr: S_Expr): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_SubscriptExpr(pos, base, expr)
}

internal class G_BaseExprTail_Call(val args: S_CallArguments): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_CallExpr(base, args)
}

internal class G_BaseExprTail_NotNull(val pos: S_Pos): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_UnaryExpr(base.startPos, S_PosValue(pos, S_UnaryOp_NotNull), base)
}

internal class G_BaseExprTail_UnaryPostfixOp(val pos: S_Pos, val op: S_UnaryOp): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_UnaryExpr(base.startPos, S_PosValue(pos, op), base)
}

internal class G_BaseExprTail_At(
    val pos: S_Pos,
    val cardinality: AtCardinality,
    val where: S_AtExprWhere,
    val what: S_AtExprWhat,
    val limit: S_Expr?,
    val offset: S_Expr?,
): G_BaseExprTail() {
    override fun toExpr(base: S_Expr): S_Expr {
        val from = S_AtExprFrom_Simple(base)
        return toExpr(from)
    }

    fun toExpr(from: S_AtExprFrom): S_Expr {
        return S_AtExpr(from, S_PosValue(pos, cardinality), where, what, limit, offset)
    }
}

class RellToken(
    val name: String,
    val pattern: String,
    val until: R_LangVersion?,
): Parser<RellTokenMatch> {
    val token: Token = LiteralToken(null, pattern)

    fun isEnabled(version: R_LangVersion): Boolean = until == null || version < until

    override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int): ParseResult<RellTokenMatch> {
        val match = tokens[fromPosition]
        match ?: return UnexpectedEof(token)

        val t = match.type
        val rellInput = match.input as RellTokenInput

        return when {
            t == noneMatched -> NoMatchingToken(match)
            t == token -> RellParsedValue(rellInput.match, match.nextPosition)
            t.ignored -> this.tryParse(tokens, match.nextPosition)
            rellInput.isValidToken(token) -> MismatchedToken(token, match)
            else -> throw IllegalArgumentException("Token $name ($pattern) not in lexer tokens")
        }
    }
}

class RellTokenMatch(
    val pos: S_Pos,
    val text: String,
    val comment: S_Comment?,
)

internal class RellParsedValue<out T>(override val value: T, override val nextPosition: Int): Parsed<T>()

class RellTokenInput(
    private val text: String,
    val token: RellToken,
    val match: RellTokenMatch,
    private val validTokens: ImmSet<Token>,
): CharSequence {
    override val length get() = text.length
    override fun get(index: Int) = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int) = text.substring(startIndex, endIndex)
    override fun toString() = text

    fun isValidToken(token: Token) = token in validTokens
}

interface RellTokenProducer: TokenProducer {
    /** The maximum reached token position. */
    fun getEndPos(): S_Pos
}

internal class LegacyCombinator<T>(val innerParser: Parser<T>): Parser<T> {
    override fun tryParse(tokens: TokenMatchesSequence, fromPosition: Int) = innerParser.tryParse(tokens, fromPosition)
}
