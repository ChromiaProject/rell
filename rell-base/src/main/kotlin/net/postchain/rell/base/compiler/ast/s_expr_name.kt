/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberTag
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.R_NullType
import net.postchain.rell.base.utils.filterToImmList

class S_NameExpr(val qName: S_QualifiedName): S_Expr(qName.pos) {
    override fun asName() = qName

    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val qNameHand = qName.compile(ctx)
        return compile0(ctx, hint, qNameHand)
    }

    fun compile0(ctx: C_ExprContext, hint: C_ExprHint, qNameHand: C_QualifiedNameHandle): C_Expr {
        val hint0 = if (qNameHand.size == 1) hint else C_ExprHint.DEFAULT
        val res = resolveName(ctx, hint0, qNameHand.first)
        if (res == null) {
            qNameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
            throw C_Error.stop(C_Errors.errUnknownName(qNameHand.first.name))
        }

        var expr: C_Expr = res
        for ((i, nameHand) in qNameHand.parts.withIndex().drop(1)) {
            val hintI = if (i == qNameHand.parts.indices.last) hint else C_ExprHint.DEFAULT
            expr = expr.member(ctx, nameHand, hintI)
        }

        return expr
    }

    private fun resolveName(ctx: C_ExprContext, hint: C_ExprHint, nameHand: C_NameHandle): C_Expr? {
        val loc = resolveNameLocal(ctx, nameHand)
        val glob = resolveNameGlobal(ctx, nameHand, hint)

        val res = when {
            loc != null && glob == null -> loc
            loc == null && glob != null -> glob
            loc != null && glob != null -> when {
                !hint.callable -> loc
                loc.isCallable() -> loc
                glob.isCallable() -> glob
                else -> loc
            }
            else -> null
        }

        return res?.compile()
    }

    private fun resolveNameLocal(ctx: C_ExprContext, nameHand: C_NameHandle): NameRes? {
        val loc = ctx.blkCtx.lookupEntry(nameHand.rName)
        loc ?: return null
        val vExpr = loc.compile(ctx, nameHand.pos)
        val cExpr = C_ValueExpr(vExpr)
        return NameRes_Local(nameHand, loc, cExpr)
    }

    private fun resolveNameGlobal(ctx: C_ExprContext, nameHand: C_NameHandle, hint: C_ExprHint): NameRes? {
        val qNameHand = C_QualifiedNameHandle(nameHand)
        val tag = if (hint.callable) C_NamespaceMemberTag.CALLABLE else C_NamespaceMemberTag.VALUE
        val nameRes = ctx.nsCtx.resolveName(qNameHand, tag.list)
        return if (nameRes.isValid()) NameRes_Global(ctx, nameRes) else null
    }

    override fun compileFromItem(ctx: C_ExprContext, itemCtx: C_AtFromItemContext, alias: C_Name?): C_AtFromItem {
        val qNameHand = qName.compile(ctx)

        val entity = ctx.nsCtx.getEntity(qNameHand, error = false, unknownInfo = false)
        if (entity != null) {
            val atEntityId = ctx.appCtx.nextAtEntityId(itemCtx.fromCtx.atExprId)
            val effectiveAlias = alias ?: qNameHand.last.name

            val cAtEntity = S_AtExpr.makeDbAtEntity(
                ctx.symCtx,
                entity,
                effectiveAlias,
                alias,
                atEntityId,
                itemCtx.comment,
            )

            return if (!itemCtx.isJoin) {
                C_AtFromItem_Entity_Simple(qName.pos, cAtEntity)
            } else {
                C_AtFromItem_Entity_Join(qName.pos, cAtEntity, itemCtx.isOuterJoin, null, null)
            }
        }

        val cExpr = ctx.msgCtx.consumeError {
            compile0(ctx, C_ExprHint.DEFAULT, qNameHand)
        } ?: C_ExprUtils.errorExpr(ctx, qName.pos)

        return exprToFromItem(ctx, itemCtx, alias, cExpr)
    }

    override fun compileWhenEnum(ctx: C_ExprContext, type: R_EnumType): C_Expr {
        val qNameHand = qName.compile(ctx)

        val attr = if (qNameHand.size > 1) null else type.enum.attr(qNameHand.first.str)
        attr ?: return compile0(ctx, C_ExprHint.DEFAULT, qNameHand)

        qNameHand.setIdeInfo(attr.ideInfo)
        val value = type.getValue(attr)
        val vExpr = V_ConstantValueExpr(ctx, startPos, value)
        return C_ValueExpr(vExpr)
    }

    private abstract class NameRes {
        abstract fun isCallable(): Boolean
        abstract fun compile(): C_Expr
    }

    private class NameRes_Local(
            private val nameHand: C_NameHandle,
            private val loc: C_BlockEntryResolution,
            private val expr: C_Expr
    ): NameRes() {
        override fun isCallable() = expr.isCallable()

        override fun compile(): C_Expr {
            val ideInfo = loc.ideSymbolInfo()
            nameHand.setIdeInfo(ideInfo)
            return expr
        }
    }

    private class NameRes_Global(
        private val ctx: C_ExprContext,
        private val nameRes: C_GlobalNameRes,
    ): NameRes() {
        override fun isCallable() = nameRes.isCallable()
        override fun compile(): C_Expr = nameRes.compile(ctx)
    }
}

class S_AttrExpr(pos: S_Pos, private val name: S_Name): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        return compileAttr(ctx, hint, name)
    }

    companion object {
        fun compileAttr(ctx: C_ExprContext, hint: C_ExprHint, name: S_Name): C_Expr {
            val nameHand = name.compile(ctx)
            val cName = nameHand.name

            val members = findMembers(ctx, hint, cName)
            if (members.isEmpty()) {
                nameHand.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
                throw C_Errors.errUnknownAttr(cName)
            }

            if (members.size > 1) {
                val names = members.map { it.fullNameMsg() }
                val namesCode = names.joinToString(",") { it.code }
                val namesMsg = names.joinToString(", ") { it.msg }
                val code = "at_attr_name_ambig:$cName:[$namesCode]"
                val msg = "Multiple attributes with name '$cName': $namesMsg"
                ctx.msgCtx.error(cName.pos, code, msg)
            }

            val attr = members[0]
            return attr.compile(ctx, nameHand)
        }

        private fun findMembers(ctx: C_ExprContext, hint: C_ExprHint, name: C_Name): List<C_AtContextMember> {
            val members = ctx.blkCtx.lookupAtMembers(ctx, name)
            return members
                .filterToImmList { if (hint.callable) it.isCallable() else it.isValue() }
                .ifEmpty { members }
        }
    }
}

class S_MemberExpr(val base: S_Expr, val name: S_Name): S_Expr(base.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val nameHand = name.compile(ctx)

        var resExpr = compileNullMember(ctx, nameHand)

        if (resExpr == null) {
            val cBase = base.compileSafe(ctx)
            resExpr = cBase.member(ctx, nameHand, hint)
        }

        return resExpr
    }

    private fun compileNullMember(ctx: C_ExprContext, cNameHand: C_NameHandle): C_Expr? {
        if (base !is S_NullLiteralExpr) return null

        val cName = cNameHand.name
        val members = Lib_Rell.NULL_EXTENSION_TYPE.valueMembers.getByName(cName.rName)
        if (members.size != 1) return null
        val member = members[0]

        val vBase = base.compileSafe(ctx, C_ExprHint.DEFAULT).vExpr()
        val link = C_MemberLink(vBase, R_NullType, cName.pos, cName, false)
        return member.compile(ctx, link, cNameHand, null)
    }
}

class S_SafeMemberExpr(val base: S_Expr, val name: S_Name): S_Expr(base.startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val cBase = base.compile(ctx)
        val vBase = cBase.vExpr()
        val nameHand = name.compile(ctx)
        return vBase.member(ctx, nameHand, true, hint)
    }
}

class S_DollarExpr(pos: S_Pos): S_Expr(pos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val ph = ctx.blkCtx.lookupAtPlaceholder()
        ph ?: throw C_Errors.errAtPlaceholderNotDefined(startPos)

        val ideInfo = ph.ideSymbolInfo()
        ctx.nameCtx.addSymbol(startPos, ideInfo)

        val vExpr = ph.compile(ctx, startPos)
        return C_ValueExpr(vExpr)
    }
}
