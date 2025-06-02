/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.core.C_MessageContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.expr.C_ExprHint
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.mtype.M_TypeSet
import net.postchain.rell.base.utils.*

class C_TypeMemberReplacement(
    val selfType: M_Type?,
    val map: ImmMap<M_TypeParam, M_TypeSet>,
) {
    fun isEmpty(): Boolean = selfType == null && map.isEmpty()
}

abstract class C_TypeMember(val optionalName: R_Name?) {
    abstract fun kindMsg(): String
    abstract fun isValue(): Boolean
    abstract fun isCallable(): Boolean
    abstract fun replaceTypeParams(rep: C_TypeMemberReplacement): C_TypeMember

    companion object {
        fun <MemberT: C_TypeMember> getMember(
            msgCtx: C_MessageContext,
            members: List<MemberT>,
            hint: C_ExprHint,
            memberName: C_Name,
            selfType: R_Type,
            scopeCode: String,
        ): MemberT? {
            val filteredMembers = members
                .filter { if (hint.callable) it.isCallable() else it.isValue() }
                .ifEmpty { members }

            if (filteredMembers.isEmpty()) {
                C_Errors.errUnknownMember(msgCtx, selfType, memberName)
                return null
            }

            if (filteredMembers.size > 1) {
                val kinds = filteredMembers.map { it.kindMsg() }
                val listCode = kinds.joinToString(",")
                val listMsg = kinds.joinToString()
                msgCtx.error(
                    memberName.pos,
                    "$scopeCode:ambig:$memberName:[$listCode]",
                    "Name '$memberName' is ambiguous: $listMsg",
                )
            }

            return filteredMembers[0]
        }
    }
}

sealed class C_LibTypeMembers<MemberT: C_TypeMember> {
    abstract fun isEmpty(): Boolean
    abstract fun getAll(): List<MemberT>
    abstract fun getValues(): List<MemberT>
    abstract fun getByName(name: R_Name): List<MemberT>

    companion object {
        private val EMPTY: C_LibTypeMembers<C_TypeMember> = C_LibTypeMembers_Simple(immListOf())

        @Suppress("UNCHECKED_CAST")
        fun <MemberT: C_TypeMember> empty(): C_LibTypeMembers<MemberT> {
            return EMPTY as C_LibTypeMembers<MemberT>
        }

        fun <MemberT: C_TypeMember> simple(members: List<MemberT>): C_LibTypeMembers<MemberT> {
            return if (members.isEmpty()) empty() else C_LibTypeMembers_Simple(members.toImmList())
        }

        fun <MemberT: C_TypeMember> combined(members: Iterable<C_LibTypeMembers<MemberT>>): C_LibTypeMembers<MemberT> {
            val members2 = members.filter { !it.isEmpty() }
            return when {
                members2.isEmpty() -> empty()
                members2.size == 1 -> members2[0]
                else -> C_LibTypeMembers_Combined(members2.toImmList())
            }
        }

        fun <MemberT: C_TypeMember> replace(
            members: C_LibTypeMembers<MemberT>,
            rep: C_TypeMemberReplacement,
            replacer: (MemberT, C_TypeMemberReplacement) -> MemberT,
        ): C_LibTypeMembers<MemberT> {
            return when {
                members.isEmpty() -> empty()
                rep.isEmpty() -> members
                else -> C_LibTypeMembers_Replace(members, rep, replacer)
            }
        }
    }
}

private class C_LibTypeMembers_Simple<MemberT: C_TypeMember>(
    private val allMembers: ImmList<MemberT>,
): C_LibTypeMembers<MemberT>() {
    private val byNameMembers: ImmMap<R_Name, ImmList<MemberT>> by lazy {
        this.allMembers
            .mapNotNull { if (it.optionalName == null) null else (it.optionalName to it) }
            .groupBy({ it.first }, { it.second })
            .mapValuesToImmMap { it.value.toImmList() }
    }

    private val memberValues: ImmList<MemberT> by lazy {
        this.allMembers
            .filterToImmList { it.isValue() }
    }

    override fun isEmpty() = allMembers.isEmpty()
    override fun getAll() = allMembers
    override fun getValues() = memberValues
    override fun getByName(name: R_Name) = byNameMembers[name].orEmpty()
}

private class C_LibTypeMembers_Combined<MemberT: C_TypeMember>(
    private val parts: ImmList<C_LibTypeMembers<MemberT>>,
): C_LibTypeMembers<MemberT>() {
    init {
        for (part in parts) {
            require(!part.isEmpty())
        }
    }

    private val allMembersLazy: ImmList<MemberT> by lazy {
        parts.flatMapToImmList { it.getAll() }
    }

    private val memberValuesLazy: ImmList<MemberT> by lazy {
        parts.flatMapToImmList { it.getValues() }
    }

    override fun isEmpty() = false
    override fun getAll() = allMembersLazy
    override fun getValues() = memberValuesLazy

    override fun getByName(name: R_Name): List<MemberT> {
        return parts.flatMapToImmList { it.getByName(name) }
    }
}

private class C_LibTypeMembers_Replace<MemberT: C_TypeMember>(
    private val members: C_LibTypeMembers<MemberT>,
    private val rep: C_TypeMemberReplacement,
    private val replacer: (MemberT, C_TypeMemberReplacement) -> MemberT,
): C_LibTypeMembers<MemberT>() {
    init {
        require(!members.isEmpty())
        require(!rep.isEmpty())
    }

    override fun isEmpty() = false

    override fun getAll(): List<MemberT> {
        val res = members.getAll()
        return replaceMembers(res)
    }

    override fun getValues(): List<MemberT> {
        val res = members.getValues()
        return replaceMembers(res)
    }

    override fun getByName(name: R_Name): List<MemberT> {
        val res = members.getByName(name)
        return replaceMembers(res)
    }

    private fun replaceMembers(mems: List<MemberT>): List<MemberT> {
        return if (mems.isEmpty()) mems else {
            mems.map { replacer(it, rep) }
        }
    }
}
