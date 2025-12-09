/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.namespace.*
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.compiler.base.utils.C_RFullNamePath
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.immMapOf
import net.postchain.rell.base.utils.toImmList

internal object C_LibAdapter {
    fun makeModule(lModule: L_Module): C_LibModule {
        val converter = C_LibNamespaceConverter()

        val namespace = converter.convertNamespace(lModule.moduleName, lModule.namespace)
        val typeDefs = converter.getTypeDefs()
        val extensions = converter.getTypeExtensions()

        return C_LibModule(lModule, typeDefs, namespace, extensions)
    }

    fun convertFunctionCase(
        function: L_Function,
        naming: C_MemberNaming,
        docSymbol: DocSymbol,
        restrictions: C_MemberRestrictions,
    ): C_LibFuncCase<V_GlobalFunctionCall> {
        return C_LibNamespaceConverter.convertFunctionCase(function, naming, docSymbol, restrictions)
    }
}

private class C_LibNamespaceConverter {
    // Important to not convert same definition more than once. Same definition may be used by multiple members
    // because of aliases.
    private val typeDefMap = mutableMapOf<L_TypeDef, C_LibTypeDef>()

    private val memberMap = mutableMapOf<L_NamespaceMember, C_NamespaceMember?>()

    private val typeExtensions = mutableListOf<C_LibTypeExtension>()

    fun convertNamespace(moduleName: R_ModuleName, lNs: L_Namespace): C_LibNamespace {
        val namePath = C_RFullNamePath.of(moduleName)
        val b = C_LibNamespace.Builder(namePath)
        convertMembers(b, lNs)
        return b.build()
    }

    fun getTypeDefs(): ImmList<C_LibTypeDef> = typeDefMap.values.toImmList()
    fun getTypeExtensions(): ImmList<C_LibTypeExtension> = typeExtensions.toImmList()

    private fun convertMembers(b: C_LibNamespace.Maker, lNs: L_Namespace) {
        for (lMember in lNs.members) {
            convertMember(b, lMember)
        }
    }

    private fun convertMember(b: C_LibNamespace.Maker, lMember: L_NamespaceMember) {
        when (lMember) {
            is L_NamespaceMember_Namespace -> {
                convertMemberNamespace(b, lMember)
            }
            is L_NamespaceMember_Alias -> {
                convertMemberAlias(b, lMember)
            }
            is L_NamespaceMember_Function -> {
                val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.FUNCTION, lMember.deprecated)
                val fnCase = convertFunctionCase(lMember, lMember.docSymbol, restrictions)
                val defName = b.basePath.toDefPath().subName(lMember.simpleName)
                val ideCompletion = C_IdeCompletionsUtils.makeIdeCompletion(defName, lMember.docSymbol)
                b.addFunction(lMember.simpleName, fnCase, ideCompletion)
            }
            else -> {
                val cMember = convertMemberCached(lMember)
                if (cMember != null) {
                    b.addMember(lMember.simpleName, cMember)
                }
            }
        }
    }

    private fun convertMemberAlias(b: C_LibNamespace.Maker, lMember: L_NamespaceMember_Alias) {
        val lTargetMember = lMember.finalTargetMember
        if (lTargetMember is L_NamespaceMember_Function) {
            val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.ALIAS, lMember.deprecated)
            val fnCase = convertFunctionCase(lTargetMember, lMember.docSymbol, restrictions)
            val defName = b.basePath.toDefPath().subName(lMember.simpleName)
            val ideCompletion = C_IdeCompletionsUtils.makeIdeCompletion(defName, lMember.docSymbol, lTargetMember.docSymbol)
            b.addFunction(lMember.simpleName, fnCase, ideCompletion)
            return
        }

        val cMember = convertMemberCached(lTargetMember)
        checkNotNull(cMember) {
            "Alias points to a void member: ${lMember.qualifiedName} -> ${lTargetMember.qualifiedName}"
        }

        val ideInfo0 = cMember.ideInfo
        val ideInfo = C_IdeSymbolInfo.direct(ideInfo0.kind, ideInfo0.defId, ideInfo0.link, lMember.docSymbol)
        val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.ALIAS, lMember.deprecated)

        val defName = C_DefinitionName(lMember.fullName)
        val aliasBase = C_NamespaceMemberBase(defName, ideInfo, restrictions)
        b.addMember(lMember.simpleName, C_NamespaceMember_Alias(aliasBase, cMember, null))
    }

    private fun convertMemberNamespace(b: C_LibNamespace.Maker, lMember: L_NamespaceMember_Namespace) {
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_NAMESPACE, doc = lMember.docSymbol)
        val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.NAMESPACE, null)
        b.addNamespace(lMember.simpleName, ideInfo, restrictions) { subB ->
            convertMembers(subB, lMember.namespace)
        }
    }

    private fun convertMemberCached(lMember: L_NamespaceMember): C_NamespaceMember? {
        return memberMap.computeIfAbsent(lMember) {
            convertMember0(lMember)
        }
    }

    private fun convertMember0(lMember: L_NamespaceMember): C_NamespaceMember? {
        val memberFactory = memberFactory(lMember.fullName)

        return when (lMember) {
            is L_NamespaceMember_Type -> {
                convertMemberType(memberFactory, lMember)
            }
            is L_NamespaceMember_TypeExtension -> {
                convertMemberTypeExtension(lMember)
            }
            is L_NamespaceMember_Struct -> {
                val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_STRUCT, doc = lMember.docSymbol)
                val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.STRUCT, null)
                memberFactory.struct(lMember.simpleName, lMember.struct.rStruct, ideInfo, restrictions)
            }
            is L_NamespaceMember_Constant -> {
                convertMemberConstant(memberFactory, lMember)
            }
            is L_NamespaceMember_Property -> {
                convertMemberProperty(memberFactory, lMember)
            }
            is L_NamespaceMember_SpecialProperty -> {
                val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.MEM_SYS_PROPERTY, doc = lMember.docSymbol)
                val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.PROPERTY, null)
                memberFactory.property(lMember.simpleName, lMember.property, ideInfo, restrictions)
            }
            is L_NamespaceMember_SpecialFunction -> {
                val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_FUNCTION_SYSTEM, doc = lMember.docSymbol)
                val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.FUNCTION, null)
                val cFn = C_SpecialLibGlobalFunction(lMember.fn, ideInfo, restrictions)
                memberFactory.function(lMember.simpleName, cFn, ideInfo, C_MemberRestrictions.NULL, null)
            }
            is L_NamespaceMember_Namespace,
            is L_NamespaceMember_Function,
            is L_NamespaceMember_Alias -> throw IllegalArgumentException(lMember.javaClass.simpleName)
        }
    }

    private fun convertMemberType(mf: C_LibNsMemberFactory, lMember: L_NamespaceMember_Type): C_NamespaceMember? {
        val libTypeDef = typeDefMap.computeIfAbsent(lMember.typeDef) {
            convertTypeDef(it)
        }

        return if (libTypeDef.lTypeDef.hidden) null else {
            val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.TYPE, lMember.deprecated)
            mf.type(lMember.simpleName, libTypeDef, restrictions)
        }
    }

    private fun convertMemberTypeExtension(lMember: L_NamespaceMember_TypeExtension): C_NamespaceMember? {
        val namingFactory: (R_Name) -> C_MemberNaming = { name ->
            C_MemberNaming.makeTypeExtensionMember(lMember.qualifiedName, name)
        }

        val typeExt = lMember.typeExt
        val body = C_LibTypeAdapter.makeTypeBody(lMember.fullName, null, typeExt.members, namingFactory)
        typeExtensions.add(C_LibTypeExtension(typeExt, body))

        // Not adding the member to the C-namespace, because extensions can't be used by name in Rell code.
        return null
    }

    private fun convertMemberConstant(mf: C_LibNsMemberFactory, lMember: L_NamespaceMember_Constant): C_NamespaceMember {
        val value = lMember.constant.value
        val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_CONSTANT, doc = lMember.docSymbol)
        val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.CONSTANT, null)
        val varId = C_LibConstantVarId(lMember.fullName, lMember.constant)
        val prop = C_NamespaceProperty_RtValue(value, lMember.constant.rType, varId)
        return mf.property(lMember.simpleName, prop, ideInfo, restrictions)
    }

    private fun convertMemberProperty(mf: C_LibNsMemberFactory, lMember: L_NamespaceMember_Property): C_NamespaceMember {
        val rType = lMember.property.rType
        val ideKind = if (lMember.property.pure) IdeSymbolKind.MEM_SYS_PROPERTY_PURE else IdeSymbolKind.MEM_SYS_PROPERTY
        val ideInfo = C_IdeSymbolInfo.direct(ideKind, doc = lMember.docSymbol)
        val varId = C_LibNamespacePropertyVarId(lMember.fullName, lMember.property)
        val fn = lMember.property.prop.getFunction(rType)
        val prop = C_NamespaceProperty_SysFunction(rType, fn, varId)
        val restrictions = C_MemberRestrictions.makeLib(lMember, C_DeclarationType.PROPERTY, null)
        return mf.property(lMember.simpleName, prop, ideInfo, restrictions)
    }

    private fun convertTypeDef(lTypeDef: L_TypeDef): C_LibTypeDef {
        val namingFactory: (R_Name) -> C_MemberNaming = { simpleName ->
            C_MemberNaming.makeTypeMember(lTypeDef.mGenericType.commonType, simpleName)
        }

        val body = C_LibTypeAdapter.makeTypeBody(lTypeDef.fullName, lTypeDef, lTypeDef.allMembers, namingFactory)
        return C_LibTypeDef(lTypeDef.qualifiedName.str(), lTypeDef, body)
    }

    private fun convertFunctionCase(
        member: L_NamespaceMember_Function,
        docSymbol: DocSymbol,
        restrictions: C_MemberRestrictions,
    ): C_LibFuncCase<V_GlobalFunctionCall> {
        val naming = C_MemberNaming.makeFullName(member.fullName)
        return convertFunctionCase(member.function, naming, docSymbol, restrictions)
    }

    private fun memberFactory(fullName: R_FullName): C_LibNsMemberFactory {
        val parts = fullName.qualifiedName.parts
        val basePath = C_RFullNamePath.of(fullName.moduleName, parts.subList(0, parts.size - 1))
        return C_LibNsMemberFactory(basePath)
    }

    companion object {
        fun convertFunctionCase(
            function: L_Function,
            naming: C_MemberNaming,
            docSymbol: DocSymbol,
            restrictions: C_MemberRestrictions,
        ): C_LibFuncCase<V_GlobalFunctionCall> {
            val ideInfo = C_IdeSymbolInfo.direct(IdeSymbolKind.DEF_FUNCTION_SYSTEM, doc = docSymbol)
            return C_LibFuncCaseUtils.makeGlobalCase(naming, function, immMapOf(), restrictions, ideInfo)
        }
    }
}
