/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.lexer.noneMatched
import com.github.h0tk3y.betterParse.parser.*
import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.model.expr.R_AtCardinality

class G_Node<out T>(val value: T, val firstToken: RellTokenMatch)

sealed class G_BaseExprTail {
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

class G_BaseExprTail_Member(val name: S_Name): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_MemberExpr(base, name)
}

class G_BaseExprTail_SafeMember(val name: S_Name): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_SafeMemberExpr(base, name)
}

class G_BaseExprTail_Subscript(val pos: S_Pos, val expr: S_Expr): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_SubscriptExpr(pos, base, expr)
}

class G_BaseExprTail_Call(val args: List<S_CallArgument>): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_CallExpr(base, args)
}

class G_BaseExprTail_NotNull(val pos: S_Pos): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_UnaryExpr(base.startPos, S_PosValue(pos, S_UnaryOp_NotNull), base)
}

class G_BaseExprTail_UnaryPostfixOp(val pos: S_Pos, val op: S_UnaryOp): G_BaseExprTail() {
    override fun toExpr(base: S_Expr) = S_UnaryExpr(base.startPos, S_PosValue(pos, op), base)
}

class G_BaseExprTail_At(
    val pos: S_Pos,
    val cardinality: R_AtCardinality,
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

/**
 * A little hack to provide context information to tokens: Better Parse passes only `Sequence<TokenMatch>` to parsers,
 * and our tokens cast it to [RellTokenSequence] and get needed information (which is [RellTokenMatch]).
 *
 * This interface must not be used as a sequence - calling `iterator()` will fail.
 */
interface RellTokenSequence: Sequence<TokenMatch> {
    fun isValidToken(token: Token): Boolean
    fun nextOrNull(): Next?

    /**
     * The maximum reached position among this sequence and all derived sequences.
     * Mutable, changes when [nextOrNull] is called.
     */
    fun getEndPos(): S_Pos

    class Next(val match: TokenMatch, val rellMatch: RellTokenMatch, val tail: RellTokenSequence)
}

class RellToken(val name: String, val token: Token): Parser<RellTokenMatch> {
    override fun tryParse(tokens: Sequence<TokenMatch>): ParseResult<RellTokenMatch> {
        val rellTokens = tokens as RellTokenSequence
        val next = rellTokens.nextOrNull()
        val t = next?.match?.type
        return when {
            t == null -> UnexpectedEof(token)
            t == noneMatched -> NoMatchingToken(next.match)
            t == token -> Parsed(next.rellMatch, next.tail)
            t.ignored -> this.tryParse(next.tail)
            rellTokens.isValidToken(token) -> MismatchedToken(token, next.match)
            else ->throw IllegalArgumentException("Token $this not in lexer tokens")
        }
    }
}

class RellTokenMatch(val pos: S_Pos, val text: String, comment: String?) {
    val comment = if (comment == null) null else S_Comment(pos, comment)
}
