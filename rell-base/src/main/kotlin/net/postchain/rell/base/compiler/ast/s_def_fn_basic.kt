/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.*
import net.postchain.rell.base.compiler.base.fn.C_FormalParameters
import net.postchain.rell.base.compiler.base.fn.C_FunctionUtils
import net.postchain.rell.base.compiler.base.modifier.C_AnnUtils
import net.postchain.rell.base.compiler.base.modifier.C_ModifierFields
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierValues
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceMemberBase
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_FunctionExtension
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocDeclaration_Function
import net.postchain.rell.base.utils.doc.DocFunctionHeader
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.ide.IdeOutlineNodeType
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import kotlin.math.min

class S_FunctionDefinition(
    base: S_DefinitionBase,
    private val qualifiedName: S_QualifiedName?,
    val params: List<S_FormalParameter>,
    val retType: S_Type?,
    val body: S_FunctionBody?,
): S_BasicDefinition(base) {
    val fnPos = qualifiedName?.pos ?: kwPos
    val typePos = retType?.pos ?: fnPos

    override fun compileBasic(ctx: C_MountContext) {
        ctx.checkNotExternal(fnPos, C_DeclarationType.FUNCTION)

        val cQualifiedNameHand = qualifiedName?.compile(ctx.symCtx.nameCtx, def = true)

        val simpleName = cQualifiedNameHand?.last?.name
        val mods = C_ModifierValues(C_ModifierTargetType.FUNCTION, simpleName)
        val modAbstract = mods.field(C_ModifierFields.ABSTRACT)
        val modOverride = mods.field(C_ModifierFields.OVERRIDE)
        val modExtendable = mods.field(C_ModifierFields.EXTENDABLE)
        val modExtend = mods.field(C_ModifierFields.EXTEND)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)

        val docModifiers = modifiers.compile(ctx, mods)
        C_AnnUtils.checkModsZeroOne(ctx.msgCtx, modAbstract, modOverride, modExtendable, modExtend)

        val abstract = modAbstract.hasValue()
        val override = modOverride.hasValue()
        val extendable = modExtendable.hasValue()
        val extend = modExtend.value()
        val deprecated = modDeprecated.value()

        val base = C_FunctionCompilerBase(ctx, this, cQualifiedNameHand, deprecated, docModifiers)

        val compiler = when {
            abstract -> C_FunctionCompiler_Abstract(base)
            override -> C_FunctionCompiler_Override(base)
            extendable -> C_FunctionCompiler_Extendable(base)
            extend != null -> C_FunctionCompiler_Extend(base, extend)
            else -> C_FunctionCompiler_Regular(base)
        }

        val defCtx = compiler.cDefBase.defCtx(ctx)
        compiler.compile(defCtx)
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        if (qualifiedName != null) {
            val name = qualifiedName.last
            b.node(this, name, IdeOutlineNodeType.FUNCTION)
        }
    }
}

private class C_FunctionCompilerBase(
    private val mntCtx: C_MountContext,
    val sFn: S_FunctionDefinition,
    val qualifiedNameHand: C_QualifiedNameHandle?,
    val deprecated: C_Deprecated?,
    val docModifiers: DocModifiers,
) {
    val qualifiedName = qualifiedNameHand?.cName

    val executor = mntCtx.executor

    fun definitionBase(ideKind: IdeSymbolKind, docCommentGetter: C_LateGetter<DocComment?>): C_UserDefinitionBase {
        val cName = if (qualifiedName != null) {
            C_StringQualifiedName.of(qualifiedName)
        } else {
            val id = mntCtx.modCtx.nextNamelessFunctionId(mntCtx.nsCtx.namespacePath)
            val simpleName = "function#$id"
            C_StringQualifiedName.of(simpleName)
        }

        val comBase = mntCtx.defBaseCommon(
            C_DefinitionType.FUNCTION,
            ideKind,
            cName,
            mountName = null,
            extChain = null,
            commentProvider = mntCtx.symCtx.commentProvider(docCommentGetter),
        )

        return comBase.userBase(sFn.fnPos)
    }
}

private abstract class C_FunctionCompiler(
    protected val base: C_FunctionCompilerBase,
    ideKind: IdeSymbolKind,
) {
    protected val sFn = base.sFn
    protected val fnPos = sFn.fnPos
    protected val typePos = sFn.typePos

    private val docCommentLate = C_LateInit(C_CompilerPass.EXPRESSIONS, null as DocComment?)

    val cDefBase = base.definitionBase(ideKind, docCommentLate.getter)

    abstract fun compile(defCtx: C_DefinitionContext)

    protected fun compileSimpleName(defCtx: C_DefinitionContext): C_Name? {
        val simpleNameHand = checkSimpleName(defCtx.msgCtx)
        simpleNameHand?.setIdeInfo(cDefBase.ideDefInfo)
        return simpleNameHand?.name
    }

    protected fun registerFunction(defCtx: C_DefinitionContext, simpleName: C_Name?, cFn: C_UserGlobalFunction) {
        if (simpleName != null) {
            defCtx.mntCtx.nsBuilder.addFunction(nsMemBase(cDefBase), simpleName, cFn)
        }
    }

    protected fun nameErrCode() = cDefBase.appLevelName

    protected fun nsMemBase(cDefBase: C_UserDefinitionBase): C_NamespaceMemberBase {
        return cDefBase.nsMemBase(deprecated = base.deprecated)
    }

    protected fun compileHeader0(defCtx: C_DefinitionContext): C_UserFunctionHeader {
        return C_FunctionUtils.compileFunctionHeader(defCtx, fnPos, sFn.params, sFn.retType, sFn.body, sFn.comment)
    }

    protected fun compileHeaderCommon(defCtx: C_DefinitionContext, cFn: C_UserGlobalFunction): C_UserFunctionHeader {
        val header = compileHeader0(defCtx)
        cFn.setHeader(header)
        docCommentLate.set(header.docComment, allowEarly = true)
        return header
    }

    protected fun compileBodyCommon(
        header: C_UserFunctionHeader,
        rFnBase: R_FunctionBase,
        simpleName: R_Name?,
        hasBody: Boolean? = null,
    ) {
        if (simpleName != null) {
            base.executor.onPass(C_CompilerPass.DOCS) {
                val docType = L_TypeUtils.docType(header.deepHeader.returnType().mType)
                val docHeader = DocFunctionHeader(immListOf(), docType, header.params.docParams)
                val doc = DocDeclaration_Function(
                    base.docModifiers,
                    simpleName,
                    docHeader,
                    header.params.docParamDeclarations,
                    hasBody = hasBody,
                )
                cDefBase.setDocDeclaration(doc)
            }
        }

        if (header.fnBody != null) {
            val rBody = header.fnBody.compile()
            rFnBase.setBody(rBody)
            val rHeader = R_FunctionHeader(rBody.type, rBody.params)
            rFnBase.setHeader(rHeader)
        } else {
            // Actually needed only for body-less extendable functions - rell.get_app_structure fails for them
            // without this special handling (entire "else" part was added).
            val type = header.deepHeader.returnType()
            val params = header.params.list.map { it.rParam }.toImmList()
            val rHeader = R_FunctionHeader(type, params)
            rFnBase.setHeader(rHeader)
        }
    }

    protected fun checkHasBody(msgCtx: C_MessageContext) {
        if (sFn.body == null) {
            val nameCode = nameErrCode()
            msgCtx.error(fnPos, "fn:no_body:$nameCode", "Function must have a body")
        }
    }

    protected fun checkQualifiedName(msgCtx: C_MessageContext): C_QualifiedNameHandle? {
        return if (base.qualifiedNameHand != null) base.qualifiedNameHand else {
            msgCtx.error(fnPos, "fn:no_name", "Function needs a name")
            null
        }
    }

    protected fun checkSimpleName(msgCtx: C_MessageContext): C_NameHandle? {
        val qualifiedName = checkQualifiedName(msgCtx)
        return when {
            qualifiedName == null -> null
            qualifiedName.parts.size >= 2 -> {
                val qName = qualifiedName.str()
                qualifiedName.setIdeInfo(C_IdeSymbolInfo.UNKNOWN)
                msgCtx.error(fnPos, "fn:qname_no_override:$qName",
                        "Invalid function name: '$qName' (qualified names allowed only for override)")
                null
            }
            else -> qualifiedName.last
        }
    }
}

private class C_FunctionCompiler_Regular(
    base: C_FunctionCompilerBase,
): C_FunctionCompiler(base, IdeSymbolKind.DEF_FUNCTION) {
    override fun compile(defCtx: C_DefinitionContext) {
        checkHasBody(defCtx.msgCtx)

        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        val rFnBase = R_FunctionBase(rDefBase.defName)
        val rFn = R_FunctionDefinition(rDefBase, rFnBase)
        val cFn = C_RegularUserGlobalFunction(rFn, null)

        val simpleName = compileSimpleName(defCtx)
        registerFunction(defCtx, simpleName, cFn)

        defCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeaderCommon(defCtx, cFn)
            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBodyCommon(header, rFnBase, simpleName?.rName)
            }
        }
    }
}

private class C_FunctionCompiler_Abstract(
    base: C_FunctionCompilerBase,
): C_FunctionCompiler(base, IdeSymbolKind.DEF_FUNCTION_ABSTRACT) {
    override fun compile(defCtx: C_DefinitionContext) {
        if (!defCtx.modCtx.abstract) {
            val mName = defCtx.modCtx.moduleName.str()
            val nameCode = nameErrCode()
            defCtx.msgCtx.error(fnPos, "fn:abstract:non_abstract_module:$mName:$nameCode",
                    "Abstract function can be defined only in abstract module")
        }

        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        val rFnBase = R_FunctionBase(rDefBase.defName)
        val rFn = R_FunctionDefinition(rDefBase, rFnBase)
        val cFn = C_AbstractUserGlobalFunction(fnPos, rFn, sFn.body != null, rFnBase)

        val simpleName = compileSimpleName(defCtx)
        registerFunction(defCtx, simpleName, cFn)
        defCtx.mntCtx.fileCtx.addAbstractFunction(cFn.descriptor)

        defCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeaderCommon(defCtx, cFn)
            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBodyCommon(header, rFnBase, simpleName?.rName, hasBody = header.fnBody != null)
                cFn.compileOverride()
            }
        }

    }
}

private class C_FunctionCompiler_Override(
    base: C_FunctionCompilerBase,
): C_FunctionCompiler(base, IdeSymbolKind.DEF_FUNCTION) {
    override fun compile(defCtx: C_DefinitionContext) {
        checkHasBody(defCtx.msgCtx)

        if (defCtx.modCtx.repl) {
            defCtx.msgCtx.error(fnPos, "fn:override:repl", "Cannot override a function in REPL")
        }

        val rFnBase = R_FunctionBase(defCtx.defName)
        val descriptor = C_OverrideFunctionDescriptor(fnPos, rFnBase)
        defCtx.mntCtx.fileCtx.addOverrideFunction(descriptor)

        defCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            compileHeader(defCtx, rFnBase, descriptor)
        }
    }

    private fun compileHeader(
        defCtx: C_DefinitionContext,
        rFnBase: R_FunctionBase,
        overDescriptor: C_OverrideFunctionDescriptor,
    ) {
        val header = compileHeader0(defCtx)

        val absDescriptor = getAbstractDescriptor(defCtx)
        overDescriptor.setAbstract(absDescriptor)

        defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
            compileBody(defCtx.mntCtx, rFnBase, header, absDescriptor)
        }
    }

    private fun getAbstractDescriptor(defCtx: C_DefinitionContext): C_AbstractFunctionDescriptor? {
        val qualifiedName = checkQualifiedName(defCtx.msgCtx)
        qualifiedName ?: return null

        val fn = defCtx.nsCtx.getFunction(qualifiedName)
        fn ?: return null

        val desc = fn.getAbstractDescriptor()

        if (desc == null) {
            val qName = fn.getFunctionDefinition()?.appLevelName ?: qualifiedName.str()
            defCtx.msgCtx.error(fnPos, "fn:override:not_abstract:[$qName]", "Function is not abstract: '$qName'")
        }

        return desc
    }

    private fun compileBody(
        ctx: C_MountContext,
        rFnBase: R_FunctionBase,
        overHeader: C_UserFunctionHeader,
        absDescriptor: C_AbstractFunctionDescriptor?,
    ) {
        compileBodyCommon(overHeader, rFnBase, null)

        if (absDescriptor != null) {
            val absHeader = absDescriptor.header()
            checkSignature(ctx.msgCtx, sFn, absHeader, overHeader, "override", cDefBase.defName)
        }
    }

    companion object {
        fun checkSignature(
            msgCtx: C_MessageContext,
            sFn: S_FunctionDefinition,
            baseHeader: C_UserFunctionHeader,
            subHeader: C_UserFunctionHeader,
            subCode: String,
            subDefName: R_DefinitionName,
        ) {
            checkOverrideType(msgCtx, baseHeader, subHeader, sFn.typePos, subCode, subDefName)
            checkOverrideParams(msgCtx, baseHeader.params, subHeader.params, sFn.fnPos, subCode, subDefName)
        }

        private fun checkOverrideType(
                msgCtx: C_MessageContext,
                baseHeader: C_UserFunctionHeader,
                subHeader: C_UserFunctionHeader,
                subTypePos: S_Pos,
                subCode: String,
                subDefName: R_DefinitionName
        ) {
            val baseType = baseHeader.deepHeader.returnType()
            val subType = subHeader.deepHeader.returnType()
            val nameCode = subDefName.appLevelName
            C_Types.matchOpt(msgCtx, baseType, subType, subTypePos) {
                "fn:$subCode:ret_type:$nameCode" toCodeMsg "Return type mismatch"
            }
        }

        private fun checkOverrideParams(
                msgCtx: C_MessageContext,
                baseParams: C_FormalParameters,
                subParams: C_FormalParameters,
                subPos: S_Pos,
                subCode: String,
                subDefName: R_DefinitionName
        ) {
            val baseParamsList = baseParams.list
            val subParamsList = subParams.list

            if (baseParamsList.size != subParamsList.size) {
                val nameCode = subDefName.appLevelName
                msgCtx.error(subPos, "fn:$subCode:param_cnt:$nameCode:${baseParamsList.size}:${subParamsList.size}",
                        "Wrong number of parameters: ${subParamsList.size} instead of ${baseParamsList.size}")
            }

            for (i in 0 until min(subParamsList.size, baseParamsList.size)) {
                val baseParam = baseParamsList[i]
                val subParam = subParamsList[i]
                val baseType = baseParam.type
                val subType = subParam.type

                if (subType != baseType) {
                    C_Errors.errTypeMismatch(msgCtx, subParam.name.pos, subType, baseType) {
                        val nameCode = subDefName.appLevelName
                        "fn:$subCode:param_type:$nameCode:$i:${baseParam.name}" toCodeMsg
                        "Parameter '${baseParam.name}' (${i+1}) type mismatch "
                    }
                }
            }
        }
    }
}

private class C_FunctionCompiler_Extendable(
    base: C_FunctionCompilerBase,
): C_FunctionCompiler(base, IdeSymbolKind.DEF_FUNCTION_EXTENDABLE) {
    override fun compile(defCtx: C_DefinitionContext) {
        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        val rFnBase = R_FunctionBase(rDefBase.defName)
        val rFn = R_FunctionDefinition(rDefBase, rFnBase)

        val simpleName = compileSimpleName(defCtx)
        val fullName = if (simpleName != null) defCtx.nsCtx.getFullName(simpleName.rName) else null
        val moduleName = defCtx.modCtx.moduleName

        val baseExt = if (sFn.body == null) null else R_FunctionExtension(rFnBase)
        val extFnUid = defCtx.appCtx.extendableFunctionCompiler.addExtendableFunction(cDefBase.appLevelName, baseExt)
        val cFn = C_ExtendableUserGlobalFunction(defCtx.appCtx, rFn, extFnUid, moduleName, fullName, typePos)

        registerFunction(defCtx, simpleName, cFn)

        defCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeaderCommon(defCtx, cFn)
            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBodyCommon(header, rFnBase, simpleName?.rName, hasBody = header.fnBody != null)
                cFn.compileDefinition()
            }
        }

        defCtx.mntCtx.checkNotTest(fnPos) {
            "fn_extendable:${cDefBase.appLevelName}" toCodeMsg "extendable function"
        }
    }
}

private class C_FunctionCompiler_Extend(
    base: C_FunctionCompilerBase,
    private val extendNameHand: C_QualifiedNameHandle,
): C_FunctionCompiler(base, IdeSymbolKind.DEF_FUNCTION_EXTEND) {
    override fun compile(defCtx: C_DefinitionContext) {
        checkHasBody(defCtx.msgCtx)

        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)
        val rFnBase = R_FunctionBase(rDefBase.defName)
        val rFn = R_FunctionDefinition(rDefBase, rFnBase)
        val cFn = C_RegularUserGlobalFunction(rFn, null)

        val simpleName = if (base.qualifiedName == null) null else compileSimpleName(defCtx)
        registerFunction(defCtx, simpleName, cFn)

        defCtx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeader(defCtx, cFn, rFnBase, extendNameHand)
            defCtx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBody(defCtx.msgCtx, header, rFnBase, simpleName?.rName)
            }
        }
    }

    private fun compileHeader(
        defCtx: C_DefinitionContext,
        cFn: C_UserGlobalFunction,
        rFnBase: R_FunctionBase,
        extendNameHand: C_QualifiedNameHandle,
    ): C_ExtendFunctionHeader {
        val cBaseFn = defCtx.nsCtx.getFunction(extendNameHand)
        val cExtDescriptor = cBaseFn?.getExtendableDescriptor()

        if (cExtDescriptor == null) {
            if (cBaseFn != null) {
                val qName = extendNameHand.str()
                val msg = "Function is not extendable: '$qName'"
                defCtx.msgCtx.error(extendNameHand.pos, "fn:extend:not_extendable:$qName", msg)
            }
        } else {
            val ok = defCtx.mntCtx.checkNotTest(fnPos) {
                val nameCode = nameErrCode()
                "fn_extend:$nameCode" toCodeMsg "extend function"
            }
            if (ok) {
                val sameModule = defCtx.modCtx.moduleName == cExtDescriptor.moduleName
                val isTestDep = defCtx.modCtx.isTestDependency
                val useTestDep = defCtx.globalCtx.compilerOptions.useTestDependencyExtensions
                if (sameModule || !isTestDep || useTestDep) {
                    val ext = R_FunctionExtension(rFnBase)
                    defCtx.appCtx.extendableFunctionCompiler.addExtension(cExtDescriptor.uid, ext)
                }
            }
        }

        val regHeader = compileHeaderCommon(defCtx, cFn)
        return C_ExtendFunctionHeader(regHeader, cExtDescriptor)
    }

    private fun compileBody(
        msgCtx: C_MessageContext,
        header: C_ExtendFunctionHeader,
        rFnBase: R_FunctionBase,
        simpleName: R_Name?,
    ) {
        compileBodyCommon(header.regHeader, rFnBase, simpleName)

        header.extDescriptor ?: return
        val baseHeader = header.extDescriptor.header()
        C_FunctionCompiler_Override.checkSignature(msgCtx, sFn, baseHeader, header.regHeader, "extend", cDefBase.defName)
    }

    private class C_ExtendFunctionHeader(
        val regHeader: C_UserFunctionHeader,
        val extDescriptor: C_ExtendableFunctionDescriptor?,
    )
}
