/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.parser.ErrorResult
import com.github.h0tk3y.betterParse.parser.ParseResult
import com.github.h0tk3y.betterParse.parser.Parsed
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.model.expr.R_AtCardinality

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

class RellToken(val name: String, val token: Token): Parser<RellTokenMatch> {
    override fun tryParse(tokens: Sequence<TokenMatch>): ParseResult<RellTokenMatch> {
        val r = token.tryParse(tokens)
        if (r is ErrorResult) {
            return r
        }

        r as Parsed<TokenMatch>

        val t = r.value
        val file = C_Parser.currentFile()
        val pos = S_BasicPos(file, t.row, t.column)

        return Parsed(RellTokenMatch(pos, t.text), r.remainder)
    }
}

class RellTokenMatch(val pos: S_Pos, val text: String)
