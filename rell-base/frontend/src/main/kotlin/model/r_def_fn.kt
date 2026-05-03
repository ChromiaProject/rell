/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.lateInit
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_FunctionParam(
        val name: Name,
        val type: R_Type,
        val initFrameGetter: C_LateGetter<R_CallFrame> = C_LateGetter.const(R_CallFrame.ERROR),
        private val exprGetter: C_LateGetter<R_Expr>? = null,
        private val docGetter: C_LateGetter<DocSymbol?> = C_LateGetter.const(null),
        override val docSourcePos: DocSourcePos? = null,
        val validator: R_AttrValidator? = null,
): DocDefinition() {
    override val docSymbol: DocSymbol get() = docGetter.get() ?: DocSymbol.NONE

    /** Resolves and returns the default value expression, or null if none. Forces the `C_LateGetter`. */
    fun resolveDefaultExpr(): R_Expr? = exprGetter?.get()
}

class R_ParamVar(val type: R_Type, val ptr: R_VarPtr)

sealed class R_RoutineDefinition(
    base: R_DefinitionBase,
): R_Definition(base) {
    abstract fun params(): List<R_FunctionParam>

    override fun getDocMembers0(): ImmMap<String, DocDefinition> {
        val params = params()
        return params.associateByToImmMap { it.name.str }
    }
}

sealed class R_MountedRoutineDefinition(
    base: R_DefinitionBase,
    val mountName: MountName,
): R_RoutineDefinition(base)

class R_OperationDefinition(
        executor: C_CompilerExecutor,
        base: R_DefinitionBase,
        mountName: MountName,
        val modifiers: OperationModifiers,
): R_MountedRoutineDefinition(base, mountName) {
    val type: R_Type = R_OperationType(this)
    val mirrorStructs = R_MirrorStructs(base, type, "OPERATION")

    val internals = executor.lateInit(C_CompilerPass.EXPRESSIONS, ERROR_INTERNALS)

    fun setInternals(
        params: ImmList<R_FunctionParam>,
        paramVars: ImmList<R_ParamVar>,
        body: R_Statement,
        frame: R_CallFrame,
    ) {
        checkEquals(paramVars.size, params.size)
        internals.set(Internals(params, paramVars, body, frame))
    }

    override fun params() = internals.get().params

    class Internals(
        val params: ImmList<R_FunctionParam>,
        val paramVars: ImmList<R_ParamVar>,
        val body: R_Statement,
        val frame: R_CallFrame,
    )

    companion object {
        private val ERROR_INTERNALS = Internals(
            params = immListOf(),
            paramVars = immListOf(),
            body = C_ExprUtils.ERROR_STATEMENT,
            frame = R_CallFrame.ERROR,
        )
    }
}

sealed class R_QueryBody(
    val retType: R_Type,
    val params: ImmList<R_FunctionParam>,
)

class R_UserQueryBody(
    retType: R_Type,
    params: ImmList<R_FunctionParam>,
    val paramVars: ImmList<R_ParamVar>,
    val body: R_Statement,
    val frame: R_CallFrame,
): R_QueryBody(retType, params) {
    companion object {
        val ERROR: R_QueryBody = R_UserQueryBody(
            R_CtErrorType,
            immListOf(),
            immListOf(),
            C_ExprUtils.ERROR_STATEMENT,
            R_CallFrame.ERROR,
        )
    }
}

class R_SysQueryBody(
    retType: R_Type,
    params: ImmList<R_FunctionParam>,
    /** Opaque R_SysFunction reference — resolved by runtime dispatch. */
    val fn: Any,
    /**
     * Deterministic, content-derived key for the sys-query function. Used as the lookup key in
     * [net.postchain.rell.base.runtime.Rt_StdlibEnv]. Must be unique per sys query and stable
     * across JVMs/builds — do NOT derive from `fn::class.name` (synthetic lambda names vary).
     */
    val key: String,
): R_QueryBody(retType, params)

class R_QueryDefinition(
    executor: C_CompilerExecutor,
    base: R_DefinitionBase,
    mountName: MountName,
): R_MountedRoutineDefinition(base, mountName) {
    val bodyLate = executor.lateInit(C_CompilerPass.EXPRESSIONS, R_UserQueryBody.ERROR)

    fun setBody(body: R_QueryBody) {
        bodyLate.set(body)
    }

    fun type(): R_Type = bodyLate.get().retType
    override fun params() = bodyLate.get().params
}

class R_FunctionHeader(val type: R_Type, val params: ImmList<R_FunctionParam>) {
    companion object {
        val ERROR = R_FunctionHeader(R_CtErrorType, immListOf())
    }
}

class R_FunctionBody(
    val type: R_Type,
    val params: ImmList<R_FunctionParam>,
    val paramVars: ImmList<R_ParamVar>,
    val body: R_Statement,
    val frame: R_CallFrame,
) {
    init {
        checkEquals(paramVars.size, params.size)
    }

    companion object {
        val ERROR = R_FunctionBody(
            R_CtErrorType,
            immListOf(),
            immListOf(),
            C_ExprUtils.ERROR_STATEMENT,
            R_CallFrame.ERROR,
        )
    }
}

class R_FunctionBase(executor: C_CompilerExecutor, val defName: DefinitionName) {
    private val headerLate = executor.lateInit(C_CompilerPass.EXPRESSIONS, R_FunctionHeader.ERROR)
    private val bodyLate = executor.lateInit(C_CompilerPass.EXPRESSIONS, R_FunctionBody.ERROR)

    fun setHeader(header: R_FunctionHeader) {
        headerLate.set(header)
    }

    fun setBody(body: R_FunctionBody) {
        bodyLate.set(body)
    }

    fun getHeader() = headerLate.get()
    fun getBody() = bodyLate.get()

    override fun toString() = defName.appLevelName
}

class R_FunctionDefinition(
    base: R_DefinitionBase,
    val fnBase: R_FunctionBase,
    val isTest: Boolean = false,
    val disabled: Boolean = false,
): R_RoutineDefinition(base) {
    override fun params() = fnBase.getBody().params
}
