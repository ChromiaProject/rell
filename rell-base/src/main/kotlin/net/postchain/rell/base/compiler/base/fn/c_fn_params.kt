/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.ast.S_FormalParameter
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.C_AttrUtils
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.expr.C_VarFact
import net.postchain.rell.base.compiler.base.expr.C_VarFacts
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_ParameterDefaultValue
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.Nullable
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocDeclaration
import net.postchain.rell.base.utils.doc.DocFunctionParam
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

class C_FormalParameter(
    val name: C_Name,
    val type: R_Type,
    val ideInfo: C_IdeSymbolInfo,
    val docParam: DocFunctionParam,
    private val index: Int,
    private val defaultValue: C_ParameterDefaultValue?,
    docSymbolGetter: C_LateGetter<Nullable<DocSymbol>>,
    private val docDeclarationGetter: C_LateGetter<DocDeclaration>,
) {
    val rParam = R_FunctionParam(name.rName, type, docSymbolGetter, name.pos.toDocPos())

    val docDeclaration: DocDeclaration get() = docDeclarationGetter.get()

    fun toCallParameter(): C_FunctionCallParameter {
        return C_FunctionCallParameter(name.rName, type, index, defaultValue, C_MemberRestrictions.NULL)
    }

    fun createMirrorAttr(mutable: Boolean): R_Attribute {
        val keyIndexKind: R_KeyIndexKind? = null

        val mirIdeKind = C_AttrUtils.getIdeSymbolKind(false, mutable, keyIndexKind)
        val mirIdeInfo = ideInfo.update(kind = mirIdeKind, defId = null)

        return R_Attribute(
            index,
            name.rName,
            type,
            mutable = mutable,
            keyIndexKind = keyIndexKind,
            ideInfo = mirIdeInfo,
            docSourcePos = name.pos.toDocPos(),
            exprGetter = defaultValue?.rGetter,
        )
    }
}

class C_FormalParameters(list: List<C_FormalParameter>) {
    val list = list.toImmList()
    val map = list.associateBy { it.name.str }.toMap().toImmMap()

    val callParameters by lazy {
        val params = this.list.map { it.toCallParameter() }
        C_FunctionCallParameters(params)
    }

    val argIdeInfos: Map<R_Name, C_IdeSymbolInfo> by lazy {
        this.list
            .map {
                val ideInfo = it.ideInfo.update(kind = IdeSymbolKind.EXPR_CALL_ARG)
                it.name.rName to ideInfo
            }
            .toImmMap()
    }

    val docParams: List<DocFunctionParam> by lazy {
        this.list.map { it.docParam }.toImmList()
    }

    val docParamDeclarations: List<DocDeclaration> by lazy {
        this.list.map { it.docDeclaration }.toImmList()
    }

    fun compile(frameCtx: C_FrameContext): C_ActualParameters {
        val inited = mutableMapOf<C_VarUid, C_VarFact>()
        val names = mutableSetOf<String>()
        val rParams = mutableListOf<R_FunctionParam>()
        val rParamVars = mutableListOf<R_ParamVar>()

        val blkCtx = frameCtx.rootBlkCtx

        for (param in list) {
            val name = param.name
            val nameStr = name.str

            if (!names.add(nameStr)) {
                frameCtx.msgCtx.error(name.pos, "dup_param_name:$nameStr", "Duplicate parameter: '$nameStr'")
            } else if (param.type.isNotError()) {
                val cVarRef = blkCtx.addLocalVar(name, param.type, false, null, param.ideInfo)
                inited[cVarRef.target.uid] = C_VarFact.YES
                rParams.add(param.rParam)
                rParamVars.add(R_ParamVar(param.type, cVarRef.ptr))
            }
        }

        val varFacts = C_VarFacts.of(inited = inited.toMap())
        val stmtCtx = C_StmtContext.createRoot(blkCtx)
                .updateFacts(varFacts)

        return C_ActualParameters(stmtCtx, rParams, rParamVars)
    }

    companion object {
        val EMPTY = C_FormalParameters(listOf())

        fun compile(defCtx: C_DefinitionContext, params: List<S_FormalParameter>, gtv: Boolean): C_FormalParameters {
            val cParams = mutableListOf<C_FormalParameter>()

            for ((index, param) in params.withIndex()) {
                val cParam = param.compile(defCtx, index)
                cParams.add(cParam)
            }

            if (gtv && defCtx.globalCtx.compilerOptions.gtv && cParams.isNotEmpty()) {
                defCtx.executor.onPass(C_CompilerPass.VALIDATION) {
                    for (cExtParam in cParams) {
                        checkGtvParam(defCtx.msgCtx, cExtParam)
                    }
                }
            }

            return C_FormalParameters(cParams)
        }

        private fun checkGtvParam(msgCtx: C_MessageContext, param: C_FormalParameter) {
            val nameStr = param.name.str
            C_Utils.checkGtvCompatibility(msgCtx, param.name.pos, param.type, true, "param_nogtv:$nameStr",
                    "Type of parameter '$nameStr'")
        }
    }
}

class C_ActualParameters(val stmtCtx: C_StmtContext, rParams: List<R_FunctionParam>, rParamVars: List<R_ParamVar>) {
    val rParams = rParams.toImmList()
    val rParamVars = rParamVars.toImmList()

    init {
        checkEquals(this.rParamVars.size, this.rParams.size)
    }
}
