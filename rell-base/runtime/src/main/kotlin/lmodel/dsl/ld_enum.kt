/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.lmodel.L_Enum
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.utils.toImmList

class Ld_EnumDslImpl(
    hdr: Ld_MemberHeader,
    private val memberBuilder: Ld_MemberHeaderBuilder = Ld_MemberHeaderBuilder(hdr),
): Ld_EnumDsl, Ld_MemberDsl by Ld_MemberDslImpl(memberBuilder) {
    private val values = mutableListOf<String>()

    override fun value(name: String) {
        check(name !in values) { "Duplicate enum value: $name" }
        values.add(name)
    }

    fun build(): Ld_MemberDef<Ld_Enum> {
        val memberHeader = memberBuilder.buildMemberHeader()
        return Ld_MemberDef(memberHeader, Ld_Enum(values.toImmList()))
    }
}

class Ld_Enum(
    private val values: List<String>,
) {
    fun process(fullName: FullName): L_Enum {
        val rEnum = C_Utils.createSysEnum(fullName.qualifiedName.str(), values)
        return L_Enum(fullName.last, rEnum)
    }
}
