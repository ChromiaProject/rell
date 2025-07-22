/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_OperationGlobalFunction
import net.postchain.rell.base.compiler.base.def.C_OperationHeader
import net.postchain.rell.base.compiler.base.def.C_QueryGlobalFunction
import net.postchain.rell.base.compiler.base.def.C_QueryHeader
import net.postchain.rell.base.compiler.base.fn.C_FormalParameters
import net.postchain.rell.base.compiler.base.fn.C_FunctionUtils
import net.postchain.rell.base.compiler.base.modifier.C_ModifierFields
import net.postchain.rell.base.compiler.base.modifier.C_ModifierTargetType
import net.postchain.rell.base.compiler.base.modifier.C_ModifierValues
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocDeclarationProto_Operation
import net.postchain.rell.base.utils.doc.DocDeclarationProto_Query
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeOutlineNodeType
import net.postchain.rell.base.utils.ide.IdeOutlineTreeBuilder
import net.postchain.rell.base.utils.ide.IdeSymbolKind

class S_OperationDefinition(
    base: S_DefinitionBase,
    val name: S_Name,
    val params: ImmList<S_FormalParameter>,
    val body: S_Statement,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        ctx.checkNotExternal(name.pos, C_DeclarationType.OPERATION)
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.OPERATION)

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.OPERATION, cName)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val modCompound = mods.field(C_ModifierFields.COMPOUND)
        val modSingular = mods.field(C_ModifierFields.SINGULAR)
        val docModifiers = modifiers.compile(ctx, mods)

        val mountName = ctx.mountName(modMount, cName)
        checkSysMountNameConflict(ctx, name.pos, C_DeclarationType.OPERATION, mountName, C_ReservedMountNames.OPERATIONS)

        val docCommentLate = C_LateInit(C_CompilerPass.MEMBERS, null as DocComment?)

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.OPERATION,
            IdeSymbolKind.DEF_OPERATION,
            mountName,
            commentProvider = ctx.symCtx.commentProvider(docCommentLate.getter),
        )
        val defCtx = cDefBase.defCtx(ctx)
        val defBase = cDefBase.rBase(defCtx.initFrameGetter)

        val rOpMods = R_OperationModifiers.getInstance(modCompound.hasValue(), modSingular.hasValue())
        val rOperation = R_OperationDefinition(defBase, mountName, rOpMods)
        ctx.appCtx.defsAdder.addStruct(rOperation.mirrorStructs.immutable)
        ctx.appCtx.defsAdder.addStruct(rOperation.mirrorStructs.mutable)

        val cOperation = C_OperationGlobalFunction(rOperation)

        ctx.appCtx.defsAdder.addOperation(rOperation)
        ctx.nsBuilder.addOperation(cDefBase.nsMemBase(modDeprecated), cName, cOperation)
        ctx.mntBuilder.addOperation(cName, rOperation)

        val ideCompsLate = C_LateInit(C_CompilerPass.VALIDATION, immMultimapOf<String, IdeCompletion>())

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeader(defCtx, cName, cOperation, rOperation.mirrorStructs)
            docCommentLate.set(header.docComment)

            ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBody(defCtx, rOperation, header, ideCompsLate)
            }

            ctx.executor.onPass(C_CompilerPass.DOCS) {
                val paramNames = header.params.list.mapToImmList { it.name.str }
                val docDec = DocDeclarationProto_Operation(
                    docModifiers,
                    cName.rName,
                    paramNames,
                    header.params.docParamDeclarations,
                )
                cDefBase.setDocDeclaration(docDec)
            }
        }

        return ideCompsLate.getter
    }

    private fun compileHeader(
        defCtx: C_DefinitionContext,
        cName: C_Name,
        cOperation: C_OperationGlobalFunction,
        mirrorStructs: R_MirrorStructs,
    ): C_OperationHeader {
        val header = C_FunctionUtils.compileOperationHeader(defCtx, cName.pos, params, comment)
        cOperation.setHeader(header)
        compileMirrorStructAttrs(mirrorStructs, header.params, false)
        compileMirrorStructAttrs(mirrorStructs, header.params, true)
        return header
    }

    private fun compileMirrorStructAttrs(mirrorStructs: R_MirrorStructs, forParams: C_FormalParameters, mutable: Boolean) {
        val struct = mirrorStructs.getStruct(mutable)

        val attrMapMut = mutableMapOf<R_Name, R_Attribute>()
        val attrNames = forParams.list.map { it.name.rName }.toImmSet()

        for (param in forParams.list) {
            val attr = param.createMirrorAttr(mutable)
            var name = attr.rName
            if (name in attrMapMut) {
                // A workaround to handle parameter name conflict (multiple parameters with same name).
                // Without it, there would be less struct attributes than parameters, what violates R_Struct's contract
                // (see MirrorStructOperationTest.testBugParameterNameConflict).
                var ctr = 0
                while (true) {
                    name = R_Name.of("${attr.name}__$ctr")
                    if (name !in attrMapMut && name !in attrNames) {
                        break
                    }
                    ctr += 1
                }
            }
            attrMapMut[name] = attr
        }

        val attrMap = attrMapMut.toImmMap()
        struct.setAttributes(attrMap)
    }

    private fun compileBody(
        defCtx: C_DefinitionContext,
        rOperation: R_OperationDefinition,
        header: C_OperationHeader,
        ideCompsLate: C_LateInit<ImmMultimap<String, IdeCompletion>>,
    ) {
        val statementVars = processStatementVars()
        val fnCtx = C_FunctionContext(defCtx, rOperation.appLevelName, null, statementVars)
        val frameCtx = C_FrameContext.create(fnCtx)

        val actParams = header.params.compile(frameCtx)
        val cBody = body.compile(actParams.stmtCtx)
        val rBody = cBody.rStmt
        val callFrame = frameCtx.makeCallFrame(cBody.guardBlock)

        rOperation.setInternals(actParams.rParams, actParams.rParamVars, rBody, callFrame.rFrame)

        defCtx.executor.onPass(C_CompilerPass.VALIDATION) {
            val comps = frameCtx.ideCompCtx.finish()
            ideCompsLate.set(comps)
        }
    }

    private fun processStatementVars(): ImmTypedKeyMap {
        val map = MutableTypedKeyMap()
        body.discoverVars(map)
        return map.toImmTypedKeyMap()
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        b.node(this, name, IdeOutlineNodeType.OPERATION)
    }
}

class S_QueryDefinition(
    base: S_DefinitionBase,
    val name: S_Name,
    val params: ImmList<S_FormalParameter>,
    val retType: S_Type?,
    val body: S_FunctionBody,
): S_BasicDefinition(base) {
    override fun compileBasic(ctx: C_MountContext): C_LateGetter<Multimap<String, IdeCompletion>> {
        ctx.checkNotExternal(name.pos, C_DeclarationType.QUERY)
        ctx.checkNotReplOrTest(name.pos, C_DeclarationType.QUERY)

        val nameHand = name.compile(ctx, def = true)
        val cName = nameHand.name

        val mods = C_ModifierValues(C_ModifierTargetType.QUERY, cName)
        val modMount = mods.field(C_ModifierFields.MOUNT)
        val modDeprecated = mods.field(C_ModifierFields.DEPRECATED)
        val docModifiers = modifiers.compile(ctx, mods)

        val mountName = ctx.mountName(modMount, cName)
        checkSysMountNameConflict(ctx, name.pos, C_DeclarationType.QUERY, mountName, C_ReservedMountNames.QUERIES)

        val docCommentLate = C_LateInit(C_CompilerPass.MEMBERS, null as DocComment?)

        val cDefBase = ctx.defBase(
            nameHand,
            C_DefinitionType.QUERY,
            IdeSymbolKind.DEF_QUERY,
            mountName,
            commentProvider = ctx.symCtx.commentProvider(docCommentLate.getter),
        )
        val defCtx = cDefBase.defCtx(ctx)
        val rDefBase = cDefBase.rBase(defCtx.initFrameGetter)

        val rQuery = R_QueryDefinition(rDefBase, mountName)
        val cQuery = C_QueryGlobalFunction(rQuery)

        ctx.appCtx.defsAdder.addQuery(rQuery)
        ctx.nsBuilder.addQuery(cDefBase.nsMemBase(modDeprecated), cName, cQuery)
        ctx.mntBuilder.addQuery(cName, rQuery)

        val ideCompsLate = C_LateInit(C_CompilerPass.VALIDATION, immMultimapOf<String, IdeCompletion>())

        ctx.executor.onPass(C_CompilerPass.MEMBERS) {
            val header = compileHeader(defCtx, cName, cQuery, ideCompsLate)
            docCommentLate.set(header.docComment)

            ctx.executor.onPass(C_CompilerPass.EXPRESSIONS) {
                compileBody(ctx, cDefBase, cName, header, rQuery, docModifiers)
            }
        }

        return ideCompsLate.getter
    }

    private fun compileHeader(
        defCtx: C_DefinitionContext,
        cName: C_Name,
        cQuery: C_QueryGlobalFunction,
        ideCompsLate: C_LateInit<ImmMultimap<String, IdeCompletion>>,
    ): C_QueryHeader {
        val header = C_FunctionUtils.compileQueryHeader(defCtx, cName, params, retType, body, comment, ideCompsLate)
        cQuery.setHeader(header)
        return header
    }

    private fun compileBody(
        ctx: C_MountContext,
        defBase: C_UserDefinitionBase,
        cName: C_Name,
        header: C_QueryHeader,
        rQuery: R_QueryDefinition,
        docModifiers: DocModifiers,
    ) {
        if (header.queryBody == null) return

        val rBody = header.queryBody.compile()

        ctx.executor.onPass(C_CompilerPass.VALIDATION) {
            checkMixedTuples(ctx.msgCtx, rBody.retType)
            if (ctx.globalCtx.compilerOptions.gtv) {
                checkGtvResult(ctx.msgCtx, rBody.retType)
            }
        }

        ctx.executor.onPass(C_CompilerPass.DOCS) {
            val docType = L_TypeUtils.docType(rBody.retType.mType)
            val paramNames = header.params.list.mapToImmList { it.name.str }
            val docDec = DocDeclarationProto_Query(
                docModifiers,
                cName.rName,
                docType,
                paramNames,
                header.params.docParamDeclarations,
            )
            defBase.setDocDeclaration(docDec)
        }

        rQuery.setBody(rBody)
    }

    private fun checkGtvResult(msgCtx: C_MessageContext, rType: R_Type) {
        C_Utils.checkGtvCompatibility(msgCtx, name.pos, rType, false, "result_nogtv:$name",
                "Return type of query '$name'")
    }

    private fun checkMixedTuples(msgCtx: C_MessageContext, rType: R_Type) {
        if (MIXED_TUPLES_SWITCH.isActive(msgCtx.globalCtx) && rType.completeFlags().mixedTuple) {
            msgCtx.warning(name.pos, "query:result_mixed_tuple:$name:${rType.strCode()}",
                "Query return type contains a mixed tuple (with named and unnamed fields): ${rType.str()}")
        }
    }

    override fun ideBuildOutlineTree(b: IdeOutlineTreeBuilder) {
        b.node(this, name, IdeOutlineNodeType.QUERY)
    }

    companion object {
        private val MIXED_TUPLES_SWITCH = C_FeatureSwitch("0.13.11")
    }
}
