/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.core.C_DefinitionPath
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty_RtValue
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_IdeName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolKind

object C_LibTypeAdapter {
    fun makeConstructor(lType: L_Type, constructors: C_LibTypeConstructors): C_GlobalFunction? {
        return C_LibTypeAdapterInternal.makeConstructor(lType, constructors)
    }

    fun makeTypeBody(
        typeName: R_FullName,
        lTypeDef: L_TypeDef?,
        members: L_TypeDefMembers,
        namingFactory: (R_Name) -> C_MemberNaming,
    ): C_LibTypeBody {
        val b = C_LibTypeBodyBuilder(typeName, namingFactory, lTypeDef)
        val cache = mutableMapOf<L_TypeDefMember, Item>()

        for (member in members.all) {
            val item = convertTypeMemberCached(typeName.qualifiedName, member, cache)
            item.member.addToBuilder(b, item.header)
        }

        return b.build()
    }

    private fun convertTypeMemberCached(
        typeName: R_QualifiedName,
        member: L_TypeDefMember,
        cache: MutableMap<L_TypeDefMember, Item>,
    ): Item {
        return cache.computeIfAbsent(member) {
            convertTypeMember(typeName, member, cache)
        }
    }

    private fun convertTypeMember(
        typeName: R_QualifiedName,
        member: L_TypeDefMember,
        cache: MutableMap<L_TypeDefMember, Item>,
    ): Item {
        return when (member) {
            is L_TypeDefMember_Constant -> {
                val m = Member_Constant(member.constant)
                makeItem(member, C_DeclarationType.CONSTANT, null, m)
            }
            is L_TypeDefMember_Property -> {
                val m = Member_Property(member.property)
                makeItem(member, C_DeclarationType.PROPERTY, null, m)
            }
            is L_TypeDefMember_Constructor -> {
                val m = Member_Constructor(member.constructor)
                makeItem(member, C_DeclarationType.CONSTRUCTOR, member.constructor.deprecated, m)
            }
            is L_TypeDefMember_SpecialConstructor -> {
                val m = Member_SpecialConstructor(member.fn)
                makeItem(member, C_DeclarationType.CONSTRUCTOR, null, m)
            }
            is L_TypeDefMember_ValueSpecialFunction -> {
                val m = Member_ValueSpecialFunction(member.fn)
                makeItem(member, C_DeclarationType.FUNCTION, null, m)
            }
            is L_TypeDefMember_StaticSpecialFunction -> {
                val m = Member_StaticSpecialFunction(member.fn)
                makeItem(member, C_DeclarationType.FUNCTION, null, m)
            }
            is L_TypeDefMember_Function -> {
                val m = Member_Function(member.function)
                makeItem(member, C_DeclarationType.FUNCTION, member.deprecated, m)
            }
            is L_TypeDefMember_Alias -> {
                val targetItem = convertTypeMemberCached(typeName, member.targetMember, cache)
                makeItem(member, C_DeclarationType.ALIAS, member.deprecated, targetItem.member)
            }
        }
    }

    private fun makeItem(
        lMember: L_TypeDefMember,
        declarationType: C_DeclarationType,
        deprecated: C_Deprecated?,
        member: Member,
    ): Item {
        val simpleName = lMember.fullName.last
        val restrictions = C_MemberRestrictions.makeLib(lMember, declarationType, deprecated)
        val header = C_LibTypeMemberHeader(simpleName, lMember.docSymbol, restrictions)
        return Item(header, member)
    }

    private class Item(val header: C_LibTypeMemberHeader, val member: Member)

    private abstract class Member {
        abstract fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader)
    }

    private class Member_Constant(private val constant: L_Constant): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addConstant(header, constant)
        }
    }

    private class Member_Property(private val property: L_TypeProperty): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addProperty(header, property)
        }
    }

    private class Member_Constructor(private val constructor: L_Constructor): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addConstructor(header, constructor)
        }
    }

    private class Member_SpecialConstructor(private val fn: C_SpecialLibGlobalFunctionBody): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addConstructor(header, fn)
        }
    }

    private class Member_Function(private val function: L_Function): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addFunction(header, function)
        }
    }

    private class Member_StaticSpecialFunction(private val fn: C_SpecialLibGlobalFunctionBody): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addFunction(header, fn)
        }
    }

    private class Member_ValueSpecialFunction(private val fn: C_SpecialLibMemberFunctionBody): Member() {
        override fun addToBuilder(b: C_LibTypeBodyBuilder, header: C_LibTypeMemberHeader) {
            b.addFunction(header, fn)
        }
    }
}

private object C_LibTypeAdapterInternal {
    fun makeRawConstructor(lTypeDef: L_TypeDef, constructors: C_LibTypeConstructors): C_GlobalFunction? {
        val mGenericType = lTypeDef.mGenericType
        val naming = C_MemberNaming.makeFullName(lTypeDef.fullName)
        return constructorsToFunction(lTypeDef, mGenericType.params, null, constructors, naming)
    }

    fun makeConstructor(lType: L_Type, constructors: C_LibTypeConstructors): C_GlobalFunction? {
        val mSpecificType = lType.mType
        val naming = C_MemberNaming.makeConstructor(mSpecificType)
        val lTypeDef = lType.getTypeDefOrNull()
        return if (lTypeDef == null) null else {
            constructorsToFunction(lTypeDef, listOf(), mSpecificType, constructors, naming)
        }
    }

    private fun constructorsToFunction(
        lTypeDef: L_TypeDef,
        typeParams: List<M_TypeParam>,
        mSpecificType: M_Type?,
        constructors: C_LibTypeConstructors,
        naming: C_MemberNaming,
    ): C_LibGlobalFunction? {
        val cases = constructorsToCases(lTypeDef, constructors, typeParams, mSpecificType, naming)
        return if (cases.isNotEmpty()) {
            check(constructors.specialConstructors.isEmpty())
            C_LibFunctionUtils.makeGlobalFunction(naming, cases)
        } else {
            val con = constructors.specialConstructors.firstOrNull()
            if (con == null) null else {
                val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_TYPE, doc = con.docSymbol)
                C_SpecialLibGlobalFunction(con.member, ideInfo, con.restrictions)
            }
        }
    }

    private fun constructorsToCases(
        lTypeDef: L_TypeDef,
        constructors: C_LibTypeConstructors,
        typeParams: List<M_TypeParam>,
        mSpecificType: M_Type?,
        naming: C_MemberNaming,
    ): ImmList<C_LibFuncCase<V_GlobalFunctionCall>> {
        // Trying with and without validation. If there are no constructors when validation is enabled, take all
        // constructors that don't pass the validation, as they will be validated again when called anyway.
        for (validate in listOf(true, false)) {
            val cases = constructors.constructors.mapNotNullToImmList { con ->
                constructorToCase(
                    lTypeDef.fullName,
                    con,
                    typeParams,
                    lTypeDef.mGenericType.commonType,
                    mSpecificType,
                    naming,
                    validate = validate,
                )
            }

            if (cases.isNotEmpty()) {
                return cases
            }
        }

        return immListOf()
    }

    private fun constructorToCase(
        typeName: R_FullName,
        mem: C_LibTypeItem<L_Constructor>,
        outerTypeParams: List<M_TypeParam>,
        selfType: M_Type,
        specificSelfType: M_Type?,
        naming: C_MemberNaming,
        validate: Boolean,
    ): C_LibFuncCase<V_GlobalFunctionCall>? {
        val con = mem.member

        val mHeader = M_FunctionHeader(
            typeParams = (outerTypeParams + con.header.typeParams).toImmList(),
            resultType = selfType,
            params = con.header.params.mapToImmList { it.mParam },
        )

        var header = L_FunctionHeader(mHeader, params = con.header.params)

        val outerTypeArgs = if (specificSelfType == null) immMapOf() else M_TypeUtils.getTypeArgs(specificSelfType)

        if (outerTypeArgs.isNotEmpty()) {
            header = header.replaceTypeParams(outerTypeArgs)
            if (validate) {
                try {
                    header.validate()
                } catch (e: M_TypeException) {
                    return null
                }
            }
        }

        val outerTypeArgTypes = outerTypeArgs
            .mapKeys { R_Name.of(it.key.name) }
            .mapValuesToImmMap { it.value.captureType() }

        val flags = L_FunctionFlags(isPure = con.pure, isStatic = false)
        val function = L_Function(typeName, header, flags, con.body)
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_TYPE, doc = mem.docSymbol)
        return C_LibFuncCaseUtils.makeGlobalCase(naming, function, outerTypeArgTypes, mem.restrictions, ideInfo)
    }
}

private class C_LibTypeMemberHeader(
    val simpleName: R_Name,
    val docSymbol: DocSymbol,
    val restrictions: C_MemberRestrictions,
) {
    fun <T> toItem(member: T): C_LibTypeItem<T> {
        return C_LibTypeItem(simpleName, docSymbol, restrictions, member)
    }
}

private class C_LibTypeBodyBuilder(
    private val typeName: R_FullName,
    private val namingFactory: (R_Name) -> C_MemberNaming,
    private val typeDef: L_TypeDef?,
) {
    private val defPath = C_DefinitionPath(typeName.moduleName, typeName.qualifiedName.parts.mapToImmList { it.str })

    private val valueMembers = mutableListOf<C_TypeValueMember>()
    private val staticMembers = mutableListOf<C_TypeStaticMember>()
    private val constructors = mutableListOf<C_LibTypeItem<L_Constructor>>()
    private val specialConstructors = mutableListOf<C_LibTypeItem<C_SpecialLibGlobalFunctionBody>>()
    private val valueFunctions = mutableMultimapOf<R_Name, C_LibTypeItem<L_Function>>()
    private val staticFunctions = mutableMultimapOf<R_Name, C_LibTypeItem<L_Function>>()

    fun addConstant(header: C_LibTypeMemberHeader, constant: L_Constant) {
        val defName = defPath.subName(header.simpleName)
        val rType = L_TypeUtils.getRTypeNotNull(constant.type)
        val varId = C_LibConstantVarId(typeName.append(header.simpleName), constant)
        val prop = C_NamespaceProperty_RtValue(constant.value, rType, varId)
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_CONSTANT, doc = header.docSymbol)
        val cMember = C_TypeStaticMember.makeProperty(defName, header.simpleName, prop, rType, ideInfo, header.restrictions)
        staticMembers.add(cMember)
    }

    fun addProperty(header: C_LibTypeMemberHeader, property: L_TypeProperty) {
        val ideKind = if (property.pure) IdeSymbolKind.MEM_SYS_PROPERTY_PURE else IdeSymbolKind.MEM_SYS_PROPERTY
        val ideInfo = C_IdeSymbolInfo.direct(ideKind, doc = header.docSymbol)
        val ideName = R_IdeName(header.simpleName, ideInfo)
        val rResType = L_TypeUtils.getRTypeNotNull(property.type)
        val naming = namingFactory(header.simpleName)
        val attr: C_MemberAttr = C_MemberAttr_SysProperty(
            ideName,
            rResType,
            property.fn,
            naming,
            header.restrictions,
            pure = property.pure,
        )
        val cMember = C_TypeValueMember_BasicAttr(attr)
        valueMembers.add(cMember)
    }

    fun addConstructor(header: C_LibTypeMemberHeader, constructor: L_Constructor) {
        checkTypeDef()
        constructors.add(header.toItem(constructor))
    }

    fun addConstructor(header: C_LibTypeMemberHeader, fn: C_SpecialLibGlobalFunctionBody) {
        checkTypeDef()
        specialConstructors.add(header.toItem(fn))
    }

    private fun checkTypeDef() {
        check(typeDef != null) { "Cannot add a constructor without a typedef" }
    }

    fun addFunction(header: C_LibTypeMemberHeader, function: L_Function) {
        val map = if (function.flags.isStatic) staticFunctions else valueFunctions
        map.put(header.simpleName, header.toItem(function))
    }

    fun addFunction(header: C_LibTypeMemberHeader, fn: C_SpecialLibGlobalFunctionBody) {
        val defName = defPath.subName(header.simpleName)
        val naming = namingFactory(header.simpleName)
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_FUNCTION_SYSTEM, doc = header.docSymbol)
        val cFn = C_SpecialLibGlobalFunction(fn, ideInfo, header.restrictions)
        val cMember = C_TypeStaticMember.makeFunction(defName, header.simpleName, naming, cFn, ideInfo)
        staticMembers.add(cMember)
    }

    fun addFunction(header: C_LibTypeMemberHeader, fn: C_SpecialLibMemberFunctionBody) {
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_FUNCTION_SYSTEM, doc = header.docSymbol)
        val cFn = C_SpecialLibMemberFunction(fn, ideInfo, header.restrictions)
        val naming = namingFactory(header.simpleName)
        val cMember = C_TypeValueMember_Function(header.simpleName, cFn, naming)
        valueMembers.add(cMember)
    }

    fun build(): C_LibTypeBody {
        val typeConstructors = C_LibTypeConstructors(constructors.toImmList(), specialConstructors.toImmList())
        val rawConstructor = if (typeDef == null) null else {
            C_LibTypeAdapterInternal.makeRawConstructor(typeDef, typeConstructors)
        }

        val valueMembers = buildValueMembers()
        val staticMembers = buildStaticMembers()
        return C_LibTypeBody(typeConstructors, rawConstructor, staticMembers, valueMembers)
    }

    private fun buildValueMembers(): C_LibTypeMembers<C_TypeValueMember> {
        val cFunctions = valueFunctions.asMap().entries
            .map { (name, mems) ->
                val naming = namingFactory(name)
                val cases = mems.mapToImmList { m ->
                    val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_FUNCTION_SYSTEM, doc = m.docSymbol)
                    C_LibFuncCaseUtils.makeMemberCase(m.member, ideInfo, naming, m.restrictions)
                }
                val fn = C_LibFunctionUtils.makeMemberFunction(cases)
                C_TypeValueMember_Function(name, fn, naming)
            }

        val all = (valueMembers + cFunctions).toImmList()
        return C_LibTypeMembers.simple(all)
    }

    private fun buildStaticMembers(): C_LibTypeMembers<C_TypeStaticMember> {
        val cFunctions = staticFunctions.asMap().entries.map { (simpleName, mems) ->
            val defName = defPath.subName(simpleName)
            val naming = namingFactory(simpleName)
            val cases = mems.mapToImmList { m ->
                C_LibAdapter.convertFunctionCase(m.member, naming, m.docSymbol, m.restrictions)
            }
            val cFn = C_LibFunctionUtils.makeGlobalFunction(naming, cases)
            val ideInfo = cases.first().ideInfo
            C_TypeStaticMember.makeFunction(defName, simpleName, naming, cFn, ideInfo)
        }

        val all = (staticMembers + cFunctions).toImmList()
        return C_LibTypeMembers.simple(all)
    }
}
