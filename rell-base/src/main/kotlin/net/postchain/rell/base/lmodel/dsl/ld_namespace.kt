/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.futures.FcFuture
import net.postchain.rell.base.utils.futures.component1
import net.postchain.rell.base.utils.futures.component2
import java.math.BigDecimal
import java.math.BigInteger

abstract class Ld_NamespaceMember(
    private val docKind: DocSymbolKind,
    val simpleName: R_Name,
    private val memberHeader: Ld_MemberHeader,
) {
    open val conflictKind: Ld_ConflictMemberKind = Ld_ConflictMemberKind.OTHER

    open fun getAliases(): List<Ld_Alias> = immListOf()

    protected abstract fun process0(
        ctx: Ld_NamespaceContext,
        hdr: Ld_MemberHeader.Finish,
    ): FcFuture<List<L_NamespaceMember>>

    fun process(ctx: Ld_NamespaceContext): FcFuture<List<L_NamespaceMember>> {
        val fullName = ctx.getFullName(simpleName)
        val fHeader = memberHeader.finish(ctx.modCfg, fullName, docKind)
        return process0(ctx, fHeader)
    }
}

interface Ld_CommonNamespaceMaker {
    fun constant(
        name: String,
        type: String,
        value: Ld_ConstantValue,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    )

    fun constant(name: String, type: String, hdr: Ld_MemberHeader, block: Ld_ConstantDsl.() -> Ld_BodyResult)
}

interface Ld_NamespaceMaker: Ld_CommonNamespaceMaker, Ld_MemberHeaderMaker {
    fun include(namespace: Ld_Namespace)

    fun alias(
        name: String?,
        target: String,
        deprecated: C_Deprecated?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    )

    fun namespace(name: String, hdr: Ld_MemberHeader, block: Ld_NamespaceDsl.() -> Unit)

    fun type(
        name: String,
        abstract: Boolean,
        hidden: Boolean,
        rType: R_Type?,
        hdr: Ld_MemberHeader,
        block: Ld_TypeDefDsl.() -> Unit,
    )

    fun extension(name: String, type: String, hdr: Ld_MemberHeader, block: Ld_TypeExtensionDsl.() -> Unit)

    fun struct(name: String, hdr: Ld_MemberHeader, block: Ld_StructDsl.() -> Unit)

    fun property(
        name: String,
        type: String,
        pure: Boolean,
        hdr: Ld_MemberHeader,
        block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult,
    )

    fun property(name: String, property: C_NamespaceProperty, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

    fun function(
        name: String,
        result: String?,
        pure: Boolean?,
        hdr: Ld_MemberHeader,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    )

    fun function(name: String, fn: C_SpecialLibGlobalFunctionBody, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)
}

class Ld_CommonNamespaceDslImpl(
    private val maker: Ld_CommonNamespaceMaker,
): Ld_CommonNamespaceDsl {
    override fun constant(
        name: String,
        value: Long,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        constant0(name, type = "integer", value = Rt_IntValue.get(value), hdr = hdr, block = block)
    }

    override fun constant(
        name: String,
        value: BigInteger,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        constant0(name, type = "big_integer", value = Rt_BigIntegerValue.get(value), hdr = hdr, block = block)
    }

    override fun constant(
        name: String,
        value: BigDecimal,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        constant0(name, type = "decimal", value = Rt_DecimalValue.get(value), hdr = hdr, block = block)
    }

    override fun constant(
        name: String,
        type: String,
        value: Rt_Value,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        constant0(name, type = type, value = value, hdr = hdr, block = block)
    }

    private fun constant0(
        name: String,
        type: String,
        value: Rt_Value,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val ldValue = Ld_ConstantValue.make(value)
        maker.constant(name, type, ldValue, hdr, block)
    }

    override fun constant(
        name: String,
        type: String,
        since: String?,
        comment: String?,
        block: Ld_ConstantDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.constant(name, type, hdr, block)
    }
}

class Ld_NamespaceBodyDslImpl(
    private val maker: Ld_NamespaceMaker,
): Ld_NamespaceBodyDsl, Ld_CommonNamespaceDsl by Ld_CommonNamespaceDslImpl(maker) {
    override fun include(namespace: Ld_Namespace) {
        maker.include(namespace)
    }

    override fun alias(
        name: String?,
        target: String,
        deprecated: C_Deprecated?,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.alias(name, target, deprecated, hdr, block)
    }

    override fun alias(
        name: String?,
        target: String,
        deprecated: C_MessageType,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val cDeprecated = C_Deprecated.makeOrNull(deprecated, useInstead = target)
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.alias(name, target, cDeprecated, hdr, block)
    }

    override fun namespace(
        name: String,
        since: String?,
        comment: String?,
        block: Ld_NamespaceDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.namespace(name, hdr, block)
    }

    override fun type(
        name: String,
        abstract: Boolean,
        hidden: Boolean,
        rType: R_Type?,
        since: String?,
        comment: String?,
        block: Ld_TypeDefDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.type(name, abstract, hidden, rType, hdr, block)
    }

    override fun extension(
        name: String,
        type: String,
        since: String?,
        comment: String?,
        block: Ld_TypeExtensionDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.extension(name, type, hdr, block)
    }

    override fun struct(
        name: String,
        since: String?,
        comment: String?,
        block: Ld_StructDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.struct(name, hdr, block)
    }

    override fun property(
        name: String,
        type: String,
        pure: Boolean,
        since: String?,
        comment: String?,
        block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.property(name, type, pure, hdr, block)
    }

    override fun property(
        name: String,
        property: C_NamespaceProperty,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.property(name, property, hdr, block)
    }

    override fun function(
        name: String,
        result: String?,
        pure: Boolean?,
        since: String?,
        comment: String?,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.function(name, result, pure, hdr, block)
    }

    override fun function(
        name: String,
        fn: C_SpecialLibGlobalFunctionBody,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.function(name, fn, hdr, block)
    }
}

class Ld_NamespaceDslImpl(
    private val maker: Ld_NamespaceMaker,
): Ld_NamespaceDsl, Ld_NamespaceBodyDsl by Ld_NamespaceBodyDslImpl(maker), Ld_MemberDsl by Ld_MemberDslImpl(maker)

class Ld_NamespaceBuilder(
    baseNamespace: Ld_Namespace = Ld_Namespace.EMPTY
): Ld_MemberHeaderBuilder(baseNamespace.memberHeader.update(since = null)), Ld_NamespaceMaker {
    private val baseSince = baseNamespace.memberHeader.since
    private val conflictChecker = Ld_MemberConflictChecker(baseNamespace.nameKinds)
    private val namespaces: MutableMap<R_Name, Ld_Namespace> = baseNamespace.namespaces.toMutableMap()
    private val members: MutableList<Ld_NamespaceMember> = baseNamespace.members.toMutableList()

    override fun include(namespace: Ld_Namespace) {
        header(namespace.memberHeader)
        for ((simpleName, ns) in namespace.namespaces) {
            namespace0(R_QualifiedName.of(simpleName)) { subBuilder ->
                subBuilder.include(ns)
            }
        }
        for (member in namespace.members) {
            addMember(member)
        }
    }

    override fun namespace(name: String, hdr: Ld_MemberHeader, block: Ld_NamespaceDsl.() -> Unit) {
        val qName = R_QualifiedName.of(name)
        namespace0(qName) { subBuilder ->
            subBuilder.header(hdr)
            val subDsl = Ld_NamespaceDslImpl(subBuilder)
            block(subDsl)
        }
    }

    private fun namespace0(namespaceName: R_QualifiedName, block: (Ld_NamespaceBuilder) -> Unit) {
        val simpleName = namespaceName.first
        conflictChecker.addMember(simpleName, Ld_ConflictMemberKind.NAMESPACE)

        val oldNs = namespaces[simpleName] ?: Ld_Namespace.EMPTY
        val subBuilder = Ld_NamespaceBuilder(oldNs)

        if (namespaceName.size() > 1) {
            val subQualifiedName = R_QualifiedName(namespaceName.parts.drop(1).toImmList())
            subBuilder.namespace0(subQualifiedName, block)
        } else {
            block(subBuilder)
        }

        val ns = subBuilder.build()
        namespaces[simpleName] = ns
    }

    override fun alias(
        name: String?,
        target: String,
        deprecated: C_Deprecated?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val targetName = R_QualifiedName.of(target)
        val simpleName = if (name != null) getSimpleName(name) else targetName.last
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        addMember(Ld_NamespaceMember_Alias(simpleName, memberHeader, targetName, deprecated, Exception()))
    }

    override fun type(
        name: String,
        abstract: Boolean,
        hidden: Boolean,
        rType: R_Type?,
        hdr: Ld_MemberHeader,
        block: Ld_TypeDefDsl.() -> Unit,
    ) {
        val simpleName = getSimpleName(name)

        val flags = L_TypeDefFlags(
            abstract = abstract,
            hidden = hidden,
        )

        val def = Ld_TypeDef.make(
            simpleName,
            hdr,
            flags = flags,
            rType = rType,
            block = block,
        )

        val member = Ld_NamespaceMember_Type(simpleName, def.header, def.def)
        addMember(member)
    }

    override fun extension(name: String, type: String, hdr: Ld_MemberHeader, block: Ld_TypeExtensionDsl.() -> Unit) {
        val simpleName = getSimpleName(name)

        val ldType = Ld_Type.parse(type)

        // Using a type def internally to collect type members. No obvious reason to create a separate class for
        // extensions - no problems using L_TypeDef.

        val typeDefBlock: Ld_TypeDefDsl.() -> Unit = {
            val typeDsl: Ld_TypeDefDsl = this
            val extDsl = object: Ld_TypeExtensionDsl, Ld_CommonTypeDsl by typeDsl {}
            block(extDsl)
        }

        val def = Ld_TypeDef.make(
            simpleName,
            hdr,
            flags = L_TypeDefFlags(abstract = true, hidden = true),
            rType = null,
            block = typeDefBlock,
        )

        val member = Ld_NamespaceMember_TypeExtension(simpleName, def.header, ldType, def.def)
        addMember(member)
    }

    override fun struct(name: String, hdr: Ld_MemberHeader, block: Ld_StructDsl.() -> Unit) {
        val simpleName = getSimpleName(name)

        val builder = Ld_StructDslImpl(hdr)
        block(builder)

        val def = builder.build()
        val member = Ld_NamespaceMember_Struct(simpleName, def.header, def.def)
        addMember(member)
    }

    override fun constant(
        name: String,
        type: String,
        value: Ld_ConstantValue,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val simpleName = getSimpleName(name)
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        val ldType = Ld_Type.parse(type)
        val constant = Ld_Constant(ldType, value)
        addMember(Ld_NamespaceMember_Constant(simpleName, memberHeader, constant))
    }

    override fun constant(
        name: String,
        type: String,
        hdr: Ld_MemberHeader,
        block: Ld_ConstantDsl.() -> Ld_BodyResult,
    ) {
        val simpleName = getSimpleName(name)
        val ldType = Ld_Type.parse(type)
        val builder = Ld_ConstantDslImpl(hdr, ldType)
        val def = builder.build(block)
        addMember(Ld_NamespaceMember_Constant(simpleName, def.header, def.def))
    }

    override fun property(
        name: String,
        type: String,
        pure: Boolean,
        hdr: Ld_MemberHeader,
        block: Ld_NamespacePropertyDsl.() -> Ld_BodyResult,
    ) {
        val simpleName = getSimpleName(name)
        val ldType = Ld_Type.parse(type)

        val builder = Ld_NamespacePropertyDslImpl(hdr, ldType, pure = pure)
        val def = builder.build(block)

        val member = Ld_NamespaceMember_Property(simpleName, def.header, def.def)
        addMember(member)
    }

    override fun property(
        name: String,
        property: C_NamespaceProperty,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val simpleName = getSimpleName(name)
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        val member = Ld_NamespaceMember_SpecialProperty(simpleName, memberHeader, property)
        addMember(member)
    }

    override fun function(
        name: String,
        result: String?,
        pure: Boolean?,
        hdr: Ld_MemberHeader,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    ) {
        val simpleName = getSimpleName(name)

        val def = Ld_FunctionBuilder.build(
            hdr,
            simpleName = simpleName,
            result = result,
            pure = pure,
            outerTypeParams = immSetOf(),
            block = block,
        )

        conflictChecker.addMember(simpleName, Ld_ConflictMemberKind.FUNCTION)
        addMember(Ld_NamespaceMember_Function(simpleName, def.header, def.def, false))
    }

    override fun function(
        name: String,
        fn: C_SpecialLibGlobalFunctionBody,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val simpleName = getSimpleName(name)
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        val member = Ld_NamespaceMember_SpecialFunction(simpleName, memberHeader, fn)
        addMember(member)
    }

    private fun addMember(member: Ld_NamespaceMember) {
        val kind = member.conflictKind
        conflictChecker.addMember(member.simpleName, kind)
        for (alias in member.getAliases()) {
            conflictChecker.addMember(alias.simpleName, kind)
        }
        members.add(member)
    }

    private fun getSimpleName(name: String): R_Name {
        return R_Name.of(name)
    }

    fun build(): Ld_Namespace {
        var memberHeader = buildMemberHeader()
        val since = memberHeader.since
        if (baseSince != null && (since == null || baseSince < since)) {
            memberHeader = memberHeader.update(since = baseSince)
        }

        return Ld_Namespace(
            memberHeader = memberHeader,
            namespaces = namespaces.toImmMap(),
            members = members.toImmList(),
            nameKinds = conflictChecker.finish(),
        )
    }
}

class Ld_Namespace(
    val memberHeader: Ld_MemberHeader,
    val namespaces: ImmMap<R_Name, Ld_Namespace>,
    val members: ImmList<Ld_NamespaceMember>,
    val nameKinds: ImmMap<R_Name, Ld_ConflictMemberKind>,
) {
    class Result(val ns: L_Namespace, val memberHeader: Ld_MemberHeader)

    fun process(ctx: Ld_NamespaceContext): FcFuture<Result> {
        val namespaceFutures = namespaces.mapValues {
            val subCtx = ctx.nestedNamespaceContext(it.key)
            it.value.process(subCtx)
        }

        val membersFutures = members.map {
            processMember(ctx, it)
        }

        return ctx.fcExec.future()
            .after(namespaceFutures)
            .after(membersFutures)
            .compute { (lNamespaces, lOtherMembers) ->
                val lNsMembers = lNamespaces.mapToImmList { (simpleName, nsResult) ->
                    val fullName = ctx.getFullName(simpleName)
                    val hdr = nsResult.memberHeader.finish(ctx.modCfg, fullName, DocSymbolKind.NAMESPACE)
                    val doc = hdr.docSymbol(DocDeclaration_Namespace(DocModifiers.NONE, hdr.simpleName))
                    L_NamespaceMember_Namespace(fullName, hdr.lHeader, doc, nsResult.ns)
                }

                val lAllMembers = lNsMembers + lOtherMembers.flatten()
                val ns = L_Namespace(lAllMembers)
                Result(ns, memberHeader)
            }
    }

    private fun processMember(ctx: Ld_NamespaceContext, member: Ld_NamespaceMember): FcFuture<List<L_NamespaceMember>> {
        val future = member.process(ctx)
        val fullName = ctx.getFullName(member.simpleName)
        ctx.declareMember(fullName.qualifiedName, future)

        val futures = mutableListOf(future)

        for (alias in member.getAliases()) {
            val aliasFullName = fullName.replaceLast(alias.simpleName)
            val fHeader = alias.memberHeader.finish(ctx.modCfg, aliasFullName, DocSymbolKind.ALIAS)
            val aliasFuture = ctx.fcExec.future().after(future).compute { targetMembers ->
                targetMembers.map {
                    Ld_NamespaceMember_Alias.finishMember(fHeader, it, alias.deprecated)
                }
            }
            futures.add(aliasFuture)
        }

        return ctx.fcExec.future().after(futures.toImmList()).compute { lists ->
            lists.flatten()
        }
    }

    companion object {
        val EMPTY = Ld_Namespace(
            memberHeader = Ld_MemberHeader.NULL,
            namespaces = immMapOf(),
            members = immListOf(),
            nameKinds = immMapOf(),
        )
    }
}

private class Ld_NamespaceMember_Alias(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val targetName: R_QualifiedName,
    private val deprecated: C_Deprecated?,
    private val errPos: Exception,
): Ld_NamespaceMember(DocSymbolKind.ALIAS, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        return ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finCtx ->
            finish(finCtx, hdr)
        }
    }

    private fun finish(ctx: Ld_NamespaceFinishContext, hdr: Ld_MemberHeader.Finish): List<L_NamespaceMember> {
        val fullName = hdr.fullName
        val members = ctx.getNamespaceMembers(targetName)
        if (members.isEmpty()) {
            val msg = "Alias target not found: ${fullName.qualifiedName} -> $targetName"
            throw Ld_Exception("alias_target_not_found:[$fullName]:[$targetName]", msg, errPos)
        }

        return members.map {
            finishMember(hdr, it, deprecated)
        }
    }

    companion object {
        fun finishMember(
            hdr: Ld_MemberHeader.Finish,
            targetMember: L_NamespaceMember,
            deprecated: C_Deprecated?,
        ): L_NamespaceMember {
            val targetMembersChain = CommonUtils.chainToList(targetMember) {
                (it as? L_NamespaceMember_Alias)?.targetMember
            }
            val finalTargetMember = targetMembersChain.last()

            val doc = makeDocSymbol(hdr, targetMember, deprecated)
            return L_NamespaceMember_Alias(hdr.fullName, hdr.lHeader, doc, targetMember, finalTargetMember, deprecated)
        }

        private fun makeDocSymbol(
            hdr: Ld_MemberHeader.Finish,
            targetMember: L_NamespaceMember,
            deprecated: C_Deprecated?,
        ): DocSymbol {
            val docDec = DocDeclaration_Alias(
                C_DocUtils.docModifiers(deprecated),
                hdr.simpleName,
                targetMember.fullName,
                targetMember.docSymbol.declaration,
            )
            return hdr.docSymbol(docDec)
        }
    }
}

private class Ld_NamespaceMember_Function(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val function: Ld_Function,
    private val isStatic: Boolean,
): Ld_NamespaceMember(DocSymbolKind.FUNCTION, simpleName, memberHeader) {
    override val conflictKind = Ld_ConflictMemberKind.FUNCTION

    override fun getAliases(): List<Ld_Alias> = function.aliases

    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        return ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finCtx ->
            finish(finCtx, hdr)
        }
    }

    private fun finish(ctx: Ld_NamespaceFinishContext, hdr: Ld_MemberHeader.Finish): List<L_NamespaceMember> {
        val finFunction = function.finish(ctx.typeCtx, hdr.fullName, hdr.lHeader, isStatic)
        val lFunction = finFunction.lFunction

        val doc = Ld_DocSymbols.function(
            hdr,
            header = lFunction.header,
            flags = lFunction.flags,
            deprecated = function.deprecated,
            comment = finFunction.comment,
        )

        val member = L_NamespaceMember_Function(hdr.fullName, hdr.lHeader, doc, lFunction, function.deprecated)
        return immListOf(member)
    }
}

private class Ld_NamespaceMember_SpecialFunction(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val fn: C_SpecialLibGlobalFunctionBody,
): Ld_NamespaceMember(DocSymbolKind.FUNCTION, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val doc = Ld_DocSymbols.specialFunction(hdr, isStatic = false)
        return ctx.fcExec.future().compute {
            val member = L_NamespaceMember_SpecialFunction(hdr.fullName, hdr.lHeader, doc, fn)
            immListOf(member)
        }
    }
}

private class Ld_NamespaceMember_Struct(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val struct: Ld_Struct,
): Ld_NamespaceMember(DocSymbolKind.STRUCT, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val fullName = hdr.fullName
        val structFuture = struct.process(ctx, fullName)
        return ctx.fcExec.future().after(structFuture).compute { lStruct ->
            val doc = hdr.docSymbol(DocDeclaration_Struct(DocModifiers.NONE, hdr.simpleName))
            val member = L_NamespaceMember_Struct(fullName, hdr.lHeader, doc, lStruct)
            immListOf(member)
        }
    }
}

private class Ld_NamespaceMember_Constant(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val constant: Ld_Constant,
): Ld_NamespaceMember(DocSymbolKind.CONSTANT, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val future = constant.process(ctx, simpleName)
        return ctx.fcExec.future().after(future).compute { lConstant ->
            val doc = Ld_DocSymbols.constant(hdr, lConstant.type, lConstant.value)
            val member = L_NamespaceMember_Constant(hdr.fullName, hdr.lHeader, doc, lConstant)
            immListOf(member)
        }
    }
}

private class Ld_NamespaceMember_Property(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val property: Ld_NamespaceProperty,
): Ld_NamespaceMember(DocSymbolKind.PROPERTY, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        return ctx.fcExec.future().after(ctx.finishCtxFuture).compute { finCtx ->
            val lProperty = property.finish(finCtx.typeCtx)
            val doc = Ld_DocSymbols.property(hdr, lProperty.type, lProperty.pure)
            val member = L_NamespaceMember_Property(hdr.fullName, hdr.lHeader, doc, lProperty)
            immListOf(member)
        }
    }
}

private class Ld_NamespaceMember_SpecialProperty(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val property: C_NamespaceProperty,
): Ld_NamespaceMember(DocSymbolKind.PROPERTY, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val doc = hdr.docSymbol(declaration = DocDeclaration_SpecialProperty(hdr.simpleName))
        return ctx.fcExec.future().compute {
            val member = L_NamespaceMember_SpecialProperty(hdr.fullName, hdr.lHeader, doc, property)
            immListOf(member)
        }
    }
}
