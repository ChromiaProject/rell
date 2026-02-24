/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.futures.FcFuture

class Ld_TypeDefParent(private val typeName: Ld_FullName, private val args: ImmList<Ld_Type>) {
    fun finish(ctx: Ld_TypeFinishContext): L_TypeDefParent {
        val typeDef = ctx.getTypeDef(typeName)
        Ld_Exception.check(typeDef.abstract) {
            "type_parent_not_abstract:$typeName" to "Parent type is not abstract: $typeName"
        }
        val mArgs = args.mapToImmList { it.finish(ctx) }
        return L_TypeDefParent(typeDef, mArgs)
    }
}

sealed class Ld_TypeDefMember(
    private val docKind: DocSymbolKind,
    private val optionalName: R_Name?,
    private val memberHeader: Ld_MemberHeader,
) {
    protected abstract fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember>

    fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = if (optionalName == null) typeName else typeName.append(optionalName)
        val fHeader = memberHeader.finish(ctx.modCfg, fullName, docKind)
        return finish0(ctx, fHeader)
    }
}

private class Ld_TypeDefMember_Constructor(
    memberHeader: Ld_MemberHeader,
    private val constructor: Ld_Constructor,
): Ld_TypeDefMember(DocSymbolKind.CONSTRUCTOR, null, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val typeName = hdr.fullName
        val finish = constructor.finish(ctx, typeName, hdr.lHeader)

        val doc = makeDoc(hdr, finish.lConstructor, finish.comment)
        val member = L_TypeDefMember_Constructor(typeName, hdr.lHeader, doc, finish.lConstructor)
        return immListOf(member)
    }

    private fun makeDoc(hdr: Ld_MemberHeader.Finish, lConstructor: L_Constructor, comment: DocComment?): DocSymbol {
        val docDeclaration = DocDeclarationProto_TypeConstructor(
                hdr.simpleName,
                L_TypeUtils.docTypeParams(lConstructor.header.typeParams),
                lConstructor.header.params.mapToImmList { it.name.str },
                lConstructor.header.params.mapToImmList { it.docSymbol.declaration },
                deprecated = lConstructor.deprecated,
                pure = lConstructor.pure,
            )
            .toLazyDeclaration()
        return hdr.docSymbol(declaration = docDeclaration, comment = comment)
    }
}

private class Ld_TypeDefMember_SpecialConstructor(
    memberHeader: Ld_MemberHeader,
    private val fn: C_SpecialLibGlobalFunctionBody,
): Ld_TypeDefMember(DocSymbolKind.CONSTRUCTOR, null, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val doc = hdr.docSymbol(DocDeclarationProto_TypeSpecialConstructor().toLazyDeclaration())
        return immListOf(L_TypeDefMember_SpecialConstructor(hdr.fullName, hdr.lHeader, doc, fn))
    }
}

private class Ld_TypeDefMember_Constant(
    private val simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val constant: Ld_Constant,
): Ld_TypeDefMember(DocSymbolKind.CONSTANT, simpleName, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val lConstant = constant.finish(ctx, simpleName)
        val doc = Ld_DocSymbols.constant(hdr, lConstant.rType, lConstant.value)
        return immListOf(L_TypeDefMember_Constant(hdr.fullName, hdr.lHeader, doc, lConstant))
    }
}

private class Ld_TypeDefMember_Property(
    memberHeader: Ld_MemberHeader,
    private val property: Ld_TypeProperty,
): Ld_TypeDefMember(DocSymbolKind.PROPERTY, property.simpleName, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val lProperty = property.finish(ctx)
        val doc = Ld_DocSymbols.property(hdr, lProperty.rType, lProperty.pure)
        return immListOf(L_TypeDefMember_Property(hdr.fullName, hdr.lHeader, doc, lProperty))
    }
}

private class Ld_TypeDefMember_Function(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val function: Ld_Function,
    private val isStatic: Boolean,
): Ld_TypeDefMember(DocSymbolKind.FUNCTION, simpleName, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val fullName = hdr.fullName
        val finFunction = function.finish(ctx, fullName, hdr.lHeader, isStatic)
        val lFunction = finFunction.lFunction

        val docSymbol = Ld_DocSymbols.function(
            hdr,
            lFunction.header,
            lFunction.flags,
            function.deprecated,
            comment = finFunction.comment,
        )

        val member = L_TypeDefMember_Function(
            fullName,
            hdr.lHeader,
            docSymbol,
            lFunction,
            deprecated = function.deprecated,
        )

        val res = mutableListOf<L_TypeDefMember>(member)
        for (alias in function.aliases) {
            val aliasFullName = fullName.replaceLast(alias.simpleName)
            val aliasHdr = alias.memberHeader.finish(ctx.modCfg, aliasFullName, DocSymbolKind.ALIAS)
            val aliasDocSymbol = makeAliasDocSymbol(aliasHdr, fullName, alias, docSymbol)
            val aliasMember = L_TypeDefMember_Alias(
                aliasFullName,
                aliasHdr.lHeader,
                aliasDocSymbol,
                member,
                alias.deprecated,
            )
            res.add(aliasMember)
        }

        return res.toImmList()
    }

    private fun makeAliasDocSymbol(
        hdr: Ld_MemberHeader.Finish,
        targetFullName: R_FullName,
        alias: Ld_Alias,
        targetDocSymbol: DocSymbol,
    ): DocSymbol {
        val docDec = DocDeclarationProto_Alias(
                C_DocUtils.docModifiers(alias.deprecated),
                alias.simpleName,
                targetFullName,
                targetDocSymbol.declaration,
                R_QualifiedName.of(targetFullName.last),
            )
            .toLazyDeclaration()
        return hdr.docSymbol(declaration = docDec)
    }
}

private class Ld_TypeDefMember_ValueSpecialFunction(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val fn: C_SpecialLibMemberFunctionBody,
): Ld_TypeDefMember(DocSymbolKind.FUNCTION, simpleName, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val doc = Ld_DocSymbols.specialFunction(hdr, isStatic = false)
        return immListOf(L_TypeDefMember_ValueSpecialFunction(hdr.fullName, hdr.lHeader, doc, fn))
    }
}

private class Ld_TypeDefMember_StaticSpecialFunction(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val fn: C_SpecialLibGlobalFunctionBody,
): Ld_TypeDefMember(DocSymbolKind.FUNCTION, simpleName, memberHeader) {
    override fun finish0(ctx: Ld_TypeFinishContext, hdr: Ld_MemberHeader.Finish): List<L_TypeDefMember> {
        val doc = Ld_DocSymbols.specialFunction(hdr, isStatic = true)
        return immListOf(L_TypeDefMember_StaticSpecialFunction(hdr.fullName, hdr.lHeader, doc, fn))
    }
}

internal class Ld_TypeDef internal constructor(
    val simpleName: R_Name,
    private val flags: L_TypeDefFlags,
    private val typeParams: ImmList<Ld_TypeParam>,
    private val parent: Ld_TypeDefParent?,
    private val rTypeMeta: R_TypeMeta?,
    private val docCodeStrategy: L_TypeDefDocCodeStrategy?,
    private val supertypeStrategy: L_TypeDefSupertypeStrategy,
    private val members: ImmList<Ld_TypeDefMember>,
) {
    class Result(
        val typeDef: L_TypeDef,
        val membersFuture: FcFuture<L_TypeDefMembers>,
    )

    fun process(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<Result> {
        return ctx.fcExec.future()
            .name("type ${hdr.fullName}")
            .attachment(hdr.fullName)
            .after(ctx.finishCtxFuture)
            .compute { finCtx ->
                finish(finCtx, hdr)
            }
    }

    private fun finish(ctx: Ld_NamespaceFinishContext, hdr: Ld_MemberHeader.Finish): Result {
        val typeCtx = ctx.typeCtx
        val fullName = hdr.fullName

        val lTypeParams = Ld_TypeParam.finishList(typeCtx, typeParams)
        val subTypeCtx = typeCtx.subCtx(typeParams = lTypeParams.map)
        val lParent = parent?.finish(subTypeCtx)

        val mParent = if (lParent == null) null else M_GenericTypeParent(lParent.typeDef.mGenericType, lParent.args)
        val mGenericType = makeGenericType(fullName, lTypeParams.list, mParent)

        // Members must be computed in a separate future, not blocking the type def, because they may depend on this
        // type recursively.
        val membersF = ctx.fcExec.future().compute {
            val lMembers = members.flatMapToImmList { it.finish(subTypeCtx, fullName) }
            L_TypeDefMembers(lMembers)
        }

        val docSym = makeDocSymbol(hdr, lTypeParams.list, lParent)

        val typeDef = L_TypeDef(
            fullName,
            flags = flags,
            mGenericType = mGenericType,
            parent = lParent,
            rTypeMeta = rTypeMeta,
            membersFuture = membersF,
            docSymbol = docSym,
        )

        return Result(typeDef, membersF)
    }

    private fun makeGenericType(
        fullName: R_FullName,
        mTypeParams: List<M_TypeParam>,
        mParent: M_GenericTypeParent?,
    ): M_GenericType {
        if (mTypeParams.isEmpty() && rTypeMeta != null) {
            val rType = rTypeMeta.getTypeOrNull(immListOf())
            if (rType != null) {
                val name = fullName.qualifiedName.str()
                return L_TypeUtils.makeMGenericType(rType, name, mParent, docCodeStrategy)
            }
        }

        return L_TypeUtils.makeMGenericType(
            fullName,
            mTypeParams,
            mParent,
            rTypeMeta = rTypeMeta,
            docCodeStrategy = docCodeStrategy,
            supertypeStrategy = supertypeStrategy,
        )
    }

    private fun makeDocSymbol(
        hdr: Ld_MemberHeader.Finish,
        mTypeParams: List<M_TypeParam>,
        lParent: L_TypeDefParent?,
    ): DocSymbol {
        val docTypeParams = L_TypeUtils.docTypeParams(mTypeParams)
        val docDec = DocDeclarationProto_Type(hdr.simpleName, docTypeParams, lParent, flags).toLazyDeclaration()
        return hdr.docSymbol(docDec)
    }

    companion object {
        fun make(
            simpleName: R_Name,
            hdr: Ld_MemberHeader,
            flags: L_TypeDefFlags,
            rType: R_Type?,
            block: Ld_TypeDefDsl.() -> Unit,
        ): Ld_MemberDef<Ld_TypeDef> {
            val builder = Ld_TypeDefBuilder(simpleName, flags = flags)
            builder.header(hdr)

            val dsl = Ld_TypeDefDslImpl(simpleName.str, builder)
            if (rType != null) {
                dsl.rType(rType)
            }

            block(dsl)
            return builder.build()
        }
    }
}

internal class Ld_NamespaceMember_Type(
    simpleName: R_Name,
    memberHeader: Ld_MemberHeader,
    private val typeDef: Ld_TypeDef,
): Ld_NamespaceMember(DocSymbolKind.TYPE, simpleName, memberHeader) {
    override fun process0(ctx: Ld_NamespaceContext, hdr: Ld_MemberHeader.Finish): FcFuture<List<L_NamespaceMember>> {
        val resultF = typeDef.process(ctx, hdr)
        return ctx.fcExec.future().after(resultF).compute { result ->
            val member = L_NamespaceMember_Type(hdr.fullName, hdr.lHeader, result.typeDef, null)
            immListOf(member)
        }
    }
}

internal interface Ld_TypeDefMaker: Ld_CommonNamespaceMaker, Ld_MemberHeaderMaker {
    fun generic(name: String, subOf: String?, superOf: String?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

    fun parent(type: String)

    fun rTypeMeta(typeMeta: R_TypeMeta)
    fun docCodeStrategy(strategy: L_TypeDefDocCodeStrategy)
    fun supertypeStrategy(strategy: L_TypeDefSupertypeStrategy)

    fun property(
        name: String,
        type: String,
        pure: Boolean,
        hdr: Ld_MemberHeader,
        block: Ld_TypePropertyDsl.() -> Ld_BodyResult,
    )

    fun property(
        name: String,
        type: String,
        body: C_SysFunctionBody,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    )

    fun constructor(pure: Boolean?, hdr: Ld_MemberHeader, block: Ld_ConstructorDsl.() -> Ld_BodyResult)
    fun constructor(fn: C_SpecialLibGlobalFunctionBody, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

    fun function(
        isStatic: Boolean,
        name: String,
        result: String?,
        pure: Boolean?,
        hdr: Ld_MemberHeader,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    )

    fun function(name: String, fn: C_SpecialLibGlobalFunctionBody, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)
    fun function(name: String, fn: C_SpecialLibMemberFunctionBody, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)
}

class Ld_TypeDefDslImpl internal constructor(
    override val typeSimpleName: String,
    private val maker: Ld_TypeDefMaker,
): Ld_TypeDefDsl, Ld_CommonNamespaceDsl by Ld_CommonNamespaceDslImpl(maker), Ld_MemberDsl by Ld_MemberDslImpl(maker) {
    override fun generic(
        name: String,
        subOf: String?,
        superOf: String?,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.generic(name, subOf = subOf, superOf = superOf, hdr = hdr, block = block)
    }

    override fun parent(type: String) {
        maker.parent(type)
    }

    override fun rType(rType: R_Type) {
        maker.rTypeMeta(R_TypeMeta.mkSimple(rType))
    }

    override fun rType(factory: (R_Type) -> R_Type?) {
        maker.rTypeMeta(R_TypeMeta.make(factory))
    }

    override fun rType(factory: (R_Type, R_Type) -> R_Type?) {
        maker.rTypeMeta(R_TypeMeta.make(factory))
    }

    override fun rType(factory: (R_Type, R_Type, R_Type) -> R_Type?) {
        maker.rTypeMeta(R_TypeMeta.make(factory))
    }

    override fun rTypeMeta(rTypeMeta: R_TypeMeta) {
        maker.rTypeMeta(rTypeMeta)
    }

    override fun docCode(calculator: (DocCode) -> DocCode) {
        maker.docCodeStrategy { args ->
            checkEquals(args.size, 1)
            calculator(args[0])
        }
    }

    override fun docCode(calculator: (DocCode, DocCode) -> DocCode) {
        maker.docCodeStrategy { args ->
            checkEquals(args.size, 2)
            calculator(args[0], args[1])
        }
    }

    override fun docCode(calculator: (DocCode, DocCode, DocCode) -> DocCode) {
        maker.docCodeStrategy { args ->
            checkEquals(args.size, 3)
            calculator(args[0], args[1], args[2])
        }
    }

    override fun supertypeStrategySpecial(predicate: (M_Type) -> Boolean) {
        maker.supertypeStrategy(object: L_TypeDefSupertypeStrategy() {
            override fun isSpecialSuperTypeOf(type: M_Type): Boolean {
                val res = predicate(type)
                return res
            }
        })
    }

    override fun supertypeStrategyComposite(predicate: (M_Type_Composite) -> Boolean) {
        maker.supertypeStrategy(object: L_TypeDefSupertypeStrategy() {
            override fun isPossibleSpecialCompositeSuperTypeOf(type: M_Type_Composite): Boolean {
                val res = predicate(type)
                return res
            }
        })
    }

    override fun property(
        name: String,
        type: String,
        pure: Boolean,
        since: String?,
        comment: String?,
        block: Ld_TypePropertyDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.property(name, type, pure, hdr, block)
    }

    override fun property(
        name: String,
        type: String,
        body: C_SysFunctionBody,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.property(name, type, body, hdr, block)
    }

    override fun constructor(
        pure: Boolean?,
        since: String?,
        comment: String?,
        block: Ld_ConstructorDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.constructor(pure = pure, hdr = hdr, block = block)
    }

    override fun constructor(
        fn: C_SpecialLibGlobalFunctionBody,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.constructor(fn, hdr, block)
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
        maker.function(isStatic = false, name = name, result = result, pure = pure, hdr = hdr, block = block)
    }

    override fun function(
        name: String,
        fn: C_SpecialLibMemberFunctionBody,
        since: String?,
        comment: String?,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.function(name, fn, hdr, block)
    }

    override fun staticFunction(
        name: String,
        result: String?,
        pure: Boolean?,
        since: String?,
        comment: String?,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    ) {
        val hdr = Ld_MemberHeader.make(since, comment)
        maker.function(isStatic = true, name = name, result = result, pure = pure, hdr = hdr, block = block)
    }

    override fun staticFunction(
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

class Ld_TypeParamBound(private val type: Ld_Type, private val subOf: Boolean) {
    fun finish(ctx: Ld_TypeFinishContext): M_TypeSet {
        val mType = type.finish(ctx)
        return if (subOf) M_TypeSets.subOf(mType) else M_TypeSets.superOf(mType)
    }
}

private class Ld_TypeDefBuilder(
    private val simpleName: R_Name,
    private val flags: L_TypeDefFlags,
): Ld_MemberHeaderBuilder(), Ld_TypeDefMaker {
    private val typeParams = mutableMapOf<R_Name, Ld_TypeParam>()
    private var selfType: String? = null
    private var parentType: Ld_TypeDefParent? = null
    private var rTypeMeta: R_TypeMeta? = null
    private var docCodeStrategy: L_TypeDefDocCodeStrategy? = null
    private var supertypeStrategy: L_TypeDefSupertypeStrategy? = null

    private val staticConflictChecker = Ld_MemberConflictChecker(immMapOf())
    private val valueConflictChecker = Ld_MemberConflictChecker(immMapOf())
    private val members = mutableListOf<Ld_TypeDefMember>()

    override fun generic(
        name: String,
        subOf: String?,
        superOf: String?,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        check(selfType == null) { "Trying to add a type parameter after self type was requested" }
        check(parentType == null) { "Trying to add a type parameter after a parent type" }
        check(rTypeMeta == null) { "Trying to add a type parameter after R_Type" }
        check(members.isEmpty()) { "Trying to add a type parameter after a member" }

        val (name0, variance) = parseTypeParamName(name)
        val typeParam = Ld_TypeParam.make(
            name0,
            subOf = subOf,
            superOf = superOf,
            variance = variance,
            hdr = hdr,
            block = block,
        )

        Ld_Exception.check(typeParam.name !in typeParams) {
            "type:type_param_conflict:$name0" to "Name conflict: $name0"
        }

        typeParams[typeParam.name] = typeParam
    }

    private fun parseTypeParamName(s: String): Pair<String, M_TypeVariance> {
        return when {
            s.startsWith("-") -> s.substring(1) to M_TypeVariance.OUT
            s.startsWith("+") -> s.substring(1) to M_TypeVariance.IN
            else -> s to M_TypeVariance.NONE
        }
    }

    override fun parent(type: String) {
        check(parentType == null) { "Parent type already set" }
        check(members.isEmpty()) { "Trying to set parent type after a member" }

        val ldType = Ld_Type.parse(type)
        val ldParentType = convertParentType(ldType)
        ldParentType ?: throw Ld_Exception("typedef:bad_parent_type:$type", "Bad parent type: $type")

        parentType = ldParentType
    }

    override fun rTypeMeta(typeMeta: R_TypeMeta) {
        check(rTypeMeta == null) { "R_Type already set" }
        check(members.isEmpty()) { "Trying to set R_Type after a member" }
        rTypeMeta = typeMeta
    }

    override fun docCodeStrategy(strategy: L_TypeDefDocCodeStrategy) {
        check(docCodeStrategy == null) { "strCode strategy already set" }
        check(members.isEmpty()) { "Trying to set strCode strategy after a member" }
        docCodeStrategy = strategy
    }

    override fun supertypeStrategy(strategy: L_TypeDefSupertypeStrategy) {
        check(supertypeStrategy == null) { "Supertype strategy already set" }
        check(members.isEmpty()) { "Trying to set supertype strategy after a member" }
        supertypeStrategy = strategy
    }

    private fun convertParentType(ldType: Ld_Type): Ld_TypeDefParent? {
        return when (ldType) {
            is Ld_Type_Name -> Ld_TypeDefParent(ldType.typeName, immListOf())
            is Ld_Type_Generic -> {
                val ldArgs = ldType.args.mapNotNullAllOrNull { (it as? Ld_TypeSet_One)?.type }
                if (ldArgs == null) null else Ld_TypeDefParent(ldType.typeName, ldArgs)
            }
            else -> null
        }
    }

    override fun constant(
        name: String,
        type: String,
        value: Ld_ConstantValue,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val rName = R_Name.of(name)
        staticConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)

        val memberHeader = Ld_MemberHeader.make(hdr, block)
        val ldType = Ld_Type.parse(type)
        val constant = Ld_Constant(ldType, value)
        members.add(Ld_TypeDefMember_Constant(rName, memberHeader, constant))
    }

    override fun constant(
        name: String,
        type: String,
        hdr: Ld_MemberHeader,
        block: Ld_ConstantDsl.() -> Ld_BodyResult,
    ) {
        val rName = R_Name.of(name)
        staticConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)

        val ldType = Ld_Type.parse(type)
        val dsl = Ld_ConstantDslImpl(hdr, ldType)
        val def = dsl.build(block)
        members.add(Ld_TypeDefMember_Constant(rName, def.header, def.def))
    }

    override fun property(
        name: String,
        type: String,
        pure: Boolean,
        hdr: Ld_MemberHeader,
        block: Ld_TypePropertyDsl.() -> Ld_BodyResult
    ) {
        val rName = R_Name.of(name)
        valueConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)

        val ldType = Ld_Type.parse(type)
        val builder = Ld_TypePropertyDslImpl(hdr, rName, ldType, pure)
        val def = builder.build(block)
        members.add(Ld_TypeDefMember_Property(def.header, def.def))
    }

    override fun property(
        name: String,
        type: String,
        body: C_SysFunctionBody,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val rName = R_Name.of(name)
        valueConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)

        val memberHeader = Ld_MemberHeader.make(hdr, block)
        val ldType = Ld_Type.parse(type)
        val value = Ld_PropertyValue.typeProp(body)
        val property = Ld_TypeProperty(rName, ldType, value)
        members.add(Ld_TypeDefMember_Property(memberHeader, property))
    }

    override fun constructor(pure: Boolean?, hdr: Ld_MemberHeader, block: Ld_ConstructorDsl.() -> Ld_BodyResult) {
        checkCanHaveConstructor()

        val bodyBuilder = Ld_FunctionBodyBuilder(simpleName, pure = pure)
        val conBuilder = Ld_ConstructorBuilder(hdr, outerTypeParams = typeParams.keys.toImmSet(), bodyBuilder)
        val bodyDslBuilder = Ld_FunctionBodyDslImpl(bodyBuilder)
        val dsl = Ld_ConstructorDslImpl(conBuilder, bodyDslBuilder)

        val bodyRes = block(dsl)

        val def = conBuilder.build(bodyRes)
        members.add(Ld_TypeDefMember_Constructor(def.header, def.def))
    }

    override fun constructor(fn: C_SpecialLibGlobalFunctionBody, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit) {
        checkCanHaveConstructor()
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        members.add(Ld_TypeDefMember_SpecialConstructor(memberHeader, fn))
    }

    private fun checkCanHaveConstructor() {
        Ld_Exception.check(!flags.abstract) {
            "type:abstract_constructor:$simpleName" to "Abstract type cannot have a constructor: $simpleName"
        }
    }

    override fun function(
        isStatic: Boolean,
        name: String,
        result: String?,
        pure: Boolean?,
        hdr: Ld_MemberHeader,
        block: Ld_FunctionDsl.() -> Ld_BodyResult,
    ) {
        val rName = R_Name.of(name)

        val def = Ld_FunctionBuilder.build(
            hdr,
            simpleName = rName,
            result = result,
            pure = pure,
            outerTypeParams = typeParams.keys.toImmSet(),
            block = block,
        )

        val conflictChecker = if (isStatic) staticConflictChecker else valueConflictChecker
        val kind = Ld_ConflictMemberKind.FUNCTION
        conflictChecker.addMember(rName, kind)
        for (alias in def.def.aliases) {
            conflictChecker.addMember(alias.simpleName, kind)
        }

        members.add(Ld_TypeDefMember_Function(rName, def.header, def.def, isStatic = isStatic))
    }

    override fun function(
        name: String,
        fn: C_SpecialLibGlobalFunctionBody,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val rName = R_Name.of(name)
        staticConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)
        val header = Ld_MemberHeader.make(hdr, block)
        members.add(Ld_TypeDefMember_StaticSpecialFunction(rName, header, fn))
    }

    override fun function(
        name: String,
        fn: C_SpecialLibMemberFunctionBody,
        hdr: Ld_MemberHeader,
        block: Ld_MemberDsl.() -> Unit,
    ) {
        val rName = R_Name.of(name)
        valueConflictChecker.addMember(rName, Ld_ConflictMemberKind.OTHER)
        val memberHeader = Ld_MemberHeader.make(hdr, block)
        members.add(Ld_TypeDefMember_ValueSpecialFunction(rName, memberHeader, fn))
    }

    fun build(): Ld_MemberDef<Ld_TypeDef> {
        val memberHeader = buildMemberHeader()

        val typeDef = Ld_TypeDef(
            simpleName,
            flags = flags,
            typeParams = typeParams.values.toImmList(),
            parent = parentType,
            rTypeMeta = rTypeMeta,
            docCodeStrategy = docCodeStrategy,
            supertypeStrategy = supertypeStrategy ?: L_TypeDefSupertypeStrategy_None,
            members = members.toImmList(),
        )

        return Ld_MemberDef(memberHeader, typeDef)
    }
}
