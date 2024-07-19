/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_DocUtils
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.*
import net.postchain.rell.base.utils.futures.FcFuture

class Ld_TypeDefParent(private val typeName: Ld_FullName, private val args: List<Ld_Type>) {
    fun finish(ctx: Ld_TypeFinishContext): L_TypeDefParent {
        val typeDef = ctx.getTypeDef(typeName)
        Ld_Exception.check(typeDef.abstract) {
            "type_parent_not_abstract:$typeName" to "Parent type is not abstract: $typeName"
        }
        val mArgs = args.map { it.finish(ctx) }.toImmList()
        return L_TypeDefParent(typeDef, mArgs)
    }
}

sealed class Ld_TypeDefMember {
    abstract fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember>
}

private class Ld_TypeDefMember_Constructor(
    private val constructor: Ld_Constructor,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val finish = constructor.finish(ctx, typeName)
        val doc = makeDoc(typeName, finish.lConstructor, finish.comment)
        val member = L_TypeDefMember_Constructor(typeName, finish.memberHeader, doc, finish.lConstructor)
        return immListOf(member)
    }

    private fun makeDoc(typeName: R_FullName, lConstructor: L_Constructor, comment: DocComment?): DocSymbol {
        val docDeclaration = DocDeclaration_TypeConstructor(
            typeName.last,
            L_TypeUtils.docTypeParams(lConstructor.header.typeParams),
            lConstructor.header.params.map { it.docSymbol.declaration }.toImmList(),
            deprecated = lConstructor.deprecated,
            pure = lConstructor.pure,
        )

        return Ld_DocSymbols.docSymbol(
            kind = DocSymbolKind.CONSTRUCTOR,
            symbolName = DocSymbolName.global(typeName),
            declaration = docDeclaration,
            comment = comment,
        )
    }
}

private class Ld_TypeDefMember_SpecialConstructor(
    private val memberHeader: Ld_MemberHeader,
    private val fn: C_SpecialLibGlobalFunctionBody,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val lMemberHeader = memberHeader.finish(ctx.modCfg, typeName)
        val doc = makeDoc(typeName, lMemberHeader)
        return immListOf(L_TypeDefMember_SpecialConstructor(typeName, lMemberHeader, doc, fn))
    }

    private fun makeDoc(typeName: R_FullName, lMemberHeader: L_MemberHeader): DocSymbol {
        return Ld_DocSymbols.docSymbol(
            kind = DocSymbolKind.CONSTRUCTOR,
            symbolName = DocSymbolName.global(typeName),
            declaration = DocDeclaration_TypeSpecialConstructor(),
            comment = lMemberHeader.docComment,
        )
    }
}

private class Ld_TypeDefMember_Constant(
    private val simpleName: R_Name,
    private val constant: Ld_Constant,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = typeName.append(simpleName)
        val lConstant = constant.finish(ctx, simpleName)
        val lMemberHeader = constant.memberHeader.finish(ctx.modCfg, fullName)
        val doc = Ld_DocSymbols.constant(fullName, lMemberHeader, lConstant.type, lConstant.value)
        return immListOf(L_TypeDefMember_Constant(fullName, lMemberHeader, doc, lConstant))
    }
}

private class Ld_TypeDefMember_Property(
    private val property: Ld_TypeProperty,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = typeName.append(property.simpleName)
        val lProperty = property.finish(ctx)
        val lMemberHeader = property.memberHeader.finish(ctx.modCfg, fullName)
        val doc = Ld_DocSymbols.property(fullName, lMemberHeader, lProperty.type, lProperty.pure)
        return immListOf(L_TypeDefMember_Property(fullName, lMemberHeader, doc, lProperty))
    }
}

private class Ld_TypeDefMember_Function(
    private val simpleName: R_Name,
    private val function: Ld_Function,
    private val isStatic: Boolean,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = typeName.append(simpleName)
        val finFunction = function.finish(ctx, fullName, isStatic)
        val lFunction = finFunction.lFunction

        val docSymbol = Ld_DocSymbols.function(
            fullName,
            lFunction.header,
            lFunction.flags,
            function.deprecated,
            comment = finFunction.comment,
        )

        val member = L_TypeDefMember_Function(
            fullName,
            finFunction.memberHeader,
            docSymbol,
            lFunction,
            deprecated = function.deprecated,
        )

        val res = mutableListOf<L_TypeDefMember>(member)
        for (alias in function.aliases) {
            val aliasFullName = fullName.replaceLast(alias.simpleName)
            val lMemberHeader = alias.memberHeader.finish(ctx.modCfg, aliasFullName)
            val aliasDocSymbol = makeAliasDocSymbol(fullName, aliasFullName, lMemberHeader, alias, docSymbol)
            val aliasMember = L_TypeDefMember_Alias(
                aliasFullName,
                lMemberHeader,
                aliasDocSymbol,
                member,
                alias.deprecated,
            )
            res.add(aliasMember)
        }

        return res.toImmList()
    }

    private fun makeAliasDocSymbol(
        fullName: R_FullName,
        aliasFullName: R_FullName,
        memberHeader: L_MemberHeader,
        alias: Ld_Alias,
        targetDocSymbol: DocSymbol,
    ): DocSymbol {
        val docDec = DocDeclaration_Alias(
            C_DocUtils.docModifiers(alias.deprecated),
            aliasFullName.last,
            fullName,
            targetDocSymbol.declaration,
            R_QualifiedName.of(fullName.last),
        )

        return Ld_DocSymbols.docSymbol(
            DocSymbolKind.ALIAS,
            DocSymbolName.global(aliasFullName),
            declaration = docDec,
            comment = memberHeader.docComment,
        )
    }
}

private class Ld_TypeDefMember_ValueSpecialFunction(
    private val memberHeader: Ld_MemberHeader,
    private val simpleName: R_Name,
    private val fn: C_SpecialLibMemberFunctionBody,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = typeName.append(simpleName)
        val lMemberHeader = memberHeader.finish(ctx.modCfg, fullName)
        val doc = Ld_DocSymbols.specialFunction(fullName, lMemberHeader, isStatic = false)
        return immListOf(L_TypeDefMember_ValueSpecialFunction(fullName, lMemberHeader, doc, fn))
    }
}

private class Ld_TypeDefMember_StaticSpecialFunction(
    private val memberHeader: Ld_MemberHeader,
    private val simpleName: R_Name,
    private val fn: C_SpecialLibGlobalFunctionBody,
): Ld_TypeDefMember() {
    override fun finish(ctx: Ld_TypeFinishContext, typeName: R_FullName): List<L_TypeDefMember> {
        val fullName = typeName.append(simpleName)
        val lMemberHeader = memberHeader.finish(ctx.modCfg, fullName)
        val doc = Ld_DocSymbols.specialFunction(fullName, lMemberHeader, isStatic = true)
        return immListOf(L_TypeDefMember_StaticSpecialFunction(fullName, lMemberHeader, doc, fn))
    }
}

class Ld_TypeDef(
    val simpleName: R_Name,
    private val memberHeader: Ld_MemberHeader,
    private val flags: L_TypeDefFlags,
    private val typeParams: List<Ld_TypeParam>,
    private val parent: Ld_TypeDefParent?,
    private val rTypeFactory: L_TypeDefRTypeFactory?,
    private val docCodeStrategy: L_TypeDefDocCodeStrategy?,
    private val supertypeStrategy: L_TypeDefSupertypeStrategy,
    private val members: List<Ld_TypeDefMember>,
) {
    class Result(
        val typeDef: L_TypeDef,
        val memberHeader: L_MemberHeader,
        val membersFuture: FcFuture<L_TypeDefMembers>,
    )

    fun process(ctx: Ld_NamespaceContext, fullName: R_FullName): FcFuture<Result> {
        return ctx.fcExec.future()
            .name("type $fullName")
            .attachment(fullName)
            .after(ctx.finishCtxFuture)
            .compute { finCtx ->
                finish(finCtx, fullName)
            }
    }

    private fun finish(ctx: Ld_NamespaceFinishContext, fullName: R_FullName): Result {
        val typeCtx = ctx.typeCtx
        val lTypeParams = Ld_TypeParam.finishList(typeCtx, typeParams)

        val subTypeCtx = typeCtx.subCtx(typeParams = lTypeParams.map)

        val lParent = parent?.finish(subTypeCtx)

        val mParent = if (lParent == null) null else M_GenericTypeParent(lParent.typeDef.mGenericType, lParent.args)
        val mGenericType = makeGenericType(fullName, lTypeParams.list, mParent)

        // Members must be computed in a separate future, not blocking the type def, because they may depend on this
        // type recursively.
        val membersF = ctx.fcExec.future().compute {
            val lMembers = members.flatMap { it.finish(subTypeCtx, fullName) }.toImmList()
            L_TypeDefMembers(lMembers)
        }

        val lMemberHeader = memberHeader.finish(ctx.modCfg, fullName)
        val docSym = makeDocSymbol(fullName, lMemberHeader, lTypeParams.list, lParent)

        val typeDef = L_TypeDef(
            fullName,
            flags = flags,
            mGenericType = mGenericType,
            parent = lParent,
            rTypeFactory = rTypeFactory,
            membersFuture = membersF,
            docSymbol = docSym,
        )

        return Result(typeDef, lMemberHeader, membersF)
    }

    private fun makeGenericType(
        fullName: R_FullName,
        mTypeParams: List<M_TypeParam>,
        mParent: M_GenericTypeParent?,
    ): M_GenericType {
        if (mTypeParams.isEmpty() && rTypeFactory != null) {
            val rType = rTypeFactory.getRType(immListOf())
            if (rType != null) {
                val name = fullName.qualifiedName.str()
                return L_TypeUtils.makeMGenericType(rType, name, mParent, docCodeStrategy)
            }
        }

        return L_TypeUtils.makeMGenericType(
            fullName,
            mTypeParams,
            mParent,
            rTypeFactory = rTypeFactory,
            docCodeStrategy = docCodeStrategy,
            supertypeStrategy = supertypeStrategy,
        )
    }

    private fun makeDocSymbol(
        fullName: R_FullName,
        lMemberHeader: L_MemberHeader,
        mTypeParams: List<M_TypeParam>,
        lParent: L_TypeDefParent?,
    ): DocSymbol {
        val docTypeParams = L_TypeUtils.docTypeParams(mTypeParams)
        return Ld_DocSymbols.docSymbol(
            kind = DocSymbolKind.TYPE,
            symbolName = DocSymbolName.global(fullName),
            declaration = DocDeclaration_Type(fullName.last, docTypeParams, lParent, flags),
            comment = lMemberHeader.docComment,
        )
    }

    companion object {
        fun make(
            simpleName: R_Name,
            hdr: Ld_MemberHeader,
            flags: L_TypeDefFlags,
            rType: R_Type?,
            block: Ld_TypeDefDsl.() -> Unit,
        ): Ld_TypeDef {
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

class Ld_NamespaceMember_Type(
    simpleName: R_Name,
    private val typeDef: Ld_TypeDef,
): Ld_NamespaceMember(simpleName) {
    override fun process(ctx: Ld_NamespaceContext): FcFuture<List<L_NamespaceMember>> {
        val fullName = ctx.getFullName(simpleName)
        val resultF = typeDef.process(ctx, fullName)

        return ctx.fcExec.future().after(resultF).compute { result ->
            val member = L_NamespaceMember_Type(fullName, result.memberHeader, result.typeDef, null)
            immListOf(member)
        }
    }
}

interface Ld_TypeDefMaker: Ld_CommonNamespaceMaker, Ld_MemberHeaderMaker {
    fun generic(name: String, subOf: String?, superOf: String?, hdr: Ld_MemberHeader, block: Ld_MemberDsl.() -> Unit)

    fun parent(type: String)

    fun rTypeFactory(factory: L_TypeDefRTypeFactory)
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

class Ld_TypeDefDslImpl(
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
        maker.rTypeFactory { rType }
    }

    override fun rType(factory: (R_Type) -> R_Type?) {
        maker.rTypeFactory { args ->
            checkEquals(args.size, 1)
            factory(args[0])
        }
    }

    override fun rType(factory: (R_Type, R_Type) -> R_Type?) {
        maker.rTypeFactory { args ->
            checkEquals(args.size, 2)
            factory(args[0], args[1])
        }
    }

    override fun rType(factory: (R_Type, R_Type, R_Type) -> R_Type?) {
        maker.rTypeFactory { args ->
            checkEquals(args.size, 3)
            factory(args[0], args[1], args[2])
        }
    }

    override fun rTypeFactory(factory: L_TypeDefRTypeFactory) {
        maker.rTypeFactory(factory)
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
    private var rTypeFactory: L_TypeDefRTypeFactory? = null
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
        check(rTypeFactory == null) { "Trying to add a type parameter after R_Type" }
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

    override fun rTypeFactory(factory: L_TypeDefRTypeFactory) {
        check(rTypeFactory == null) { "R_Type already set" }
        check(members.isEmpty()) { "Trying to set R_Type after a member" }
        rTypeFactory = factory
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
        val constant = Ld_Constant(memberHeader, ldType, value)
        members.add(Ld_TypeDefMember_Constant(rName, constant))
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
        val constant = dsl.build(block)
        members.add(Ld_TypeDefMember_Constant(rName, constant))
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
        val property = builder.build(block)
        members.add(Ld_TypeDefMember_Property(property))
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
        val value = Ld_PropertyValue.typeProp { body }
        val property = Ld_TypeProperty(rName, memberHeader, ldType, value)
        members.add(Ld_TypeDefMember_Property(property))
    }

    override fun constructor(pure: Boolean?, hdr: Ld_MemberHeader, block: Ld_ConstructorDsl.() -> Ld_BodyResult) {
        checkCanHaveConstructor()

        val bodyBuilder = Ld_FunctionBodyBuilder(simpleName, pure = pure)
        val conBuilder = Ld_ConstructorBuilder(hdr, outerTypeParams = typeParams.keys.toImmSet(), bodyBuilder)
        val bodyDslBuilder = Ld_FunctionBodyDslImpl(bodyBuilder)
        val dsl = Ld_ConstructorDslImpl(conBuilder, bodyDslBuilder)

        val bodyRes = block(dsl)

        val constructor = conBuilder.build(bodyRes)
        members.add(Ld_TypeDefMember_Constructor(constructor))
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

        val fn = Ld_FunctionBuilder.build(
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
        for (alias in fn.aliases) {
            conflictChecker.addMember(alias.simpleName, kind)
        }

        members.add(Ld_TypeDefMember_Function(rName, fn, isStatic = isStatic))
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
        members.add(Ld_TypeDefMember_StaticSpecialFunction(header, rName, fn))
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
        members.add(Ld_TypeDefMember_ValueSpecialFunction(memberHeader, rName, fn))
    }

    fun build(): Ld_TypeDef {
        return Ld_TypeDef(
            simpleName,
            memberHeader = buildMemberHeader(),
            flags = flags,
            typeParams = typeParams.values.toImmList(),
            parent = parentType,
            rTypeFactory = rTypeFactory,
            docCodeStrategy = docCodeStrategy,
            supertypeStrategy = supertypeStrategy ?: L_TypeDefSupertypeStrategy_None,
            members = members.toImmList(),
        )
    }
}
