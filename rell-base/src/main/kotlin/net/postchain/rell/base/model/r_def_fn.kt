/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.lib.type.R_OperationType
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.stmt.R_BlockStatement
import net.postchain.rell.base.model.stmt.R_GuardStatement
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.model.stmt.R_StatementResult_Return
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSourcePos
import net.postchain.rell.base.utils.doc.DocSymbol

class R_FunctionParam internal constructor(
    val name: R_Name,
    val type: R_Type,
    private val initFrameGetter: C_LateGetter<R_CallFrame> = C_LateGetter.const(R_CallFrame.ERROR),
    private val exprGetter: C_LateGetter<R_Expr>? = null,
    private val docGetter: C_LateGetter<DocSymbol?> = C_LateGetter.const(null),
    override val docSourcePos: DocSourcePos? = null,
    internal val validator: R_AttrValidator? = null,
): DocDefinition() {
    override val docSymbol: DocSymbol get() = docGetter.get() ?: DocSymbol.NONE

    internal fun toMetaGtv(): Gtv = mapOf(
        "name" to name.str.toGtv(),
        "type" to type.toMetaGtv(),
    ).toGtv()

    fun getDefaultValueEvaluator(): ((Rt_DefinitionContext) -> Rt_Value)? {
        exprGetter ?: return null
        return { defCtx ->
            val expr = exprGetter.get()
            Rt_Utils.evaluateInNewFrame(defCtx, null, expr, initFrameGetter)
        }
    }

    fun validate(value: Rt_Value): R_AttrValidator.Error? = validator?.check(value)
}

internal class R_ParamVar(val type: R_Type, val ptr: R_VarPtr)

sealed class R_RoutineDefinition(
    base: R_DefinitionBase,
): R_Definition(base) {
    abstract fun params(): List<R_FunctionParam>
    internal abstract fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value

    override fun getDocMembers0(): ImmMap<String, DocDefinition> {
        val params = params()
        return params.associateByToImmMap { it.name.str }
    }
}

sealed class R_MountedRoutineDefinition(
    base: R_DefinitionBase,
    val mountName: R_MountName,
): R_RoutineDefinition(base)

class R_OperationDefinition internal constructor(
    base: R_DefinitionBase,
    mountName: R_MountName,
    val modifiers: R_OperationModifiers,
): R_MountedRoutineDefinition(base, mountName) {
    internal val type: R_Type = R_OperationType(this)
    internal val mirrorStructs = R_MirrorStructs(base, C_DefinitionType.OPERATION, type)

    private val internals = C_LateInit(C_CompilerPass.EXPRESSIONS, ERROR_INTERNALS)

    internal fun setInternals(
        params: ImmList<R_FunctionParam>,
        paramVars: ImmList<R_ParamVar>,
        body: R_Statement,
        frame: R_CallFrame,
    ) {
        checkEquals(paramVars.size, params.size)
        internals.set(Internals(params, paramVars, body, frame))
    }

    override fun params() = internals.get().params

    fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value? {
        val rtFrame = processCallArgs(exeCtx, args)
        execute(rtFrame)
        return null
    }

    override fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        throw Rt_Exception.common("call:operation", "Calling operation is not allowed")
    }

    private fun processCallArgs(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_CallFrame {
        val ints = internals.get()

        val defCtx = Rt_DefinitionContext(exeCtx, true, defId)
        val rtFrame = ints.frame.createRtFrame(defCtx, null)

        checkCallArgs(this, ints.params, args)
        processArgs(ints.paramVars, ints.params, args, rtFrame)

        return rtFrame
    }

    private fun execute(rtFrame: Rt_CallFrame) {
        val ints = internals.get()
        val res = ints.body.execute(rtFrame)
        if (res != null) {
            check(res is R_StatementResult_Return && res.value == null)
        }
    }

    fun executeGuard(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>) {
        val guardBody = internals.get().guardBody
        if (guardBody != null) {
            val rtFrame = processCallArgs(exeCtx, args)
            guardBody.execute(rtFrame)
        }
    }

    override fun toMetaGtv(): Gtv {
        return mapOf(
            "mount" to mountName.str().toGtv(),
            "parameters" to params().map { it.toMetaGtv() }.toGtv(),
        ).toGtv()
    }

    private class Internals(
        val params: ImmList<R_FunctionParam>,
        val paramVars: ImmList<R_ParamVar>,
        val body: R_Statement,
        val frame: R_CallFrame,
    ) {
        val guardBody: R_Statement? by lazy {
            when (body) {
                is R_GuardStatement -> body
                is R_BlockStatement -> body.getGuardStmts()
                else -> null
            }
        }
    }

    companion object {
        private val ERROR_INTERNALS = Internals(
            params = immListOf(),
            paramVars = immListOf(),
            body = C_ExprUtils.ERROR_STATEMENT,
            frame = R_CallFrame.ERROR,
        )
    }
}

class R_OperationModifiers private constructor(val isCompound: Boolean, val isSingular: Boolean) {
    companion object {
        private val DEFAULT_INSTANCE = R_OperationModifiers(isCompound = false, isSingular = false)

        fun getInstance(isCompound: Boolean, isSingular: Boolean): R_OperationModifiers {
            return if (!isCompound && !isSingular) DEFAULT_INSTANCE else R_OperationModifiers(isCompound, isSingular)
        }
    }
}

internal sealed class R_QueryBody(
    val retType: R_Type,
    val params: ImmList<R_FunctionParam>,
) {
    internal abstract fun call(defCtx: Rt_DefinitionContext, args: List<Rt_Value>): Rt_Value
}

internal class R_UserQueryBody(
    retType: R_Type,
    params: ImmList<R_FunctionParam>,
    private val paramVars: ImmList<R_ParamVar>,
    private val body: R_Statement,
    private val frame: R_CallFrame,
): R_QueryBody(retType, params) {
    override fun call(defCtx: Rt_DefinitionContext, args: List<Rt_Value>): Rt_Value {
        val rtFrame = frame.createRtFrame(defCtx, null)

        processArgs(paramVars, params, args, rtFrame)

        val res = body.execute(rtFrame)
        check(res is R_StatementResult_Return) { "${res?.javaClass?.name}" }

        check(res.value != null)
        return res.value
    }

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

internal class R_SysQueryBody(
    retType: R_Type,
    params: ImmList<R_FunctionParam>,
    private val fn: R_SysFunction,
): R_QueryBody(retType, params) {
    override fun call(defCtx: Rt_DefinitionContext, args: List<Rt_Value>): Rt_Value {
        val callCtx = Rt_CallContext(defCtx, dbUpdateAllowed = false)
        return fn.call(callCtx, args)
    }
}

class R_QueryDefinition internal constructor(
    base: R_DefinitionBase,
    mountName: R_MountName,
): R_MountedRoutineDefinition(base, mountName) {
    private val bodyLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_UserQueryBody.ERROR)

    internal fun setBody(body: R_QueryBody) {
        bodyLate.set(body)
    }

    fun type(): R_Type = bodyLate.get().retType
    override fun params() = bodyLate.get().params

    fun call(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>): Rt_Value {
        val body = bodyLate.get()
        checkCallArgs(this, body.params, args)
        val defCtx = Rt_DefinitionContext(exeCtx, false, defId)
        val res = body.call(defCtx, args)
        return res
    }

    fun call(defCtx: Rt_DefinitionContext, args: List<Rt_Value>): Rt_Value {
        val body = bodyLate.get()
        checkCallArgs(this, body.params, args)
        val res = body.call(defCtx, args)
        return res
    }

    override fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        val body = bodyLate.get()
        checkCallArgs(this, body.params, args)

        val subDefCtx = Rt_DefinitionContext(callCtx.defCtx.exeCtx, false, defId)
        val res = body.call(subDefCtx, args)
        return res
    }

    override fun toMetaGtv(): Gtv {
        return mapOf(
            "mount" to mountName.str().toGtv(),
            "type" to type().toMetaGtv(),
            "parameters" to params().map { it.toMetaGtv() }.toGtv(),
        ).toGtv()
    }
}

class R_FunctionHeader(val type: R_Type, val params: ImmList<R_FunctionParam>) {
    companion object {
        val ERROR = R_FunctionHeader(R_CtErrorType, immListOf())
    }
}

internal class R_FunctionBody(
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

class R_FunctionBase(private val defName: R_DefinitionName) {
    private val headerLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_FunctionHeader.ERROR)
    private val bodyLate = C_LateInit(C_CompilerPass.EXPRESSIONS, R_FunctionBody.ERROR)

    internal fun setHeader(header: R_FunctionHeader) {
        headerLate.set(header)
    }

    internal fun setBody(body: R_FunctionBody) {
        bodyLate.set(body)
    }

    internal fun getHeader() = headerLate.get()
    internal fun getBody() = bodyLate.get()

    internal fun callTop(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>, dbUpdateAllowed: Boolean): Rt_Value {
        val body = bodyLate.get()
        val defCtx = Rt_DefinitionContext(exeCtx, dbUpdateAllowed, body.frame.defId)
        val callCtx = Rt_CallContext(defCtx, dbUpdateAllowed)
        val res = call0(callCtx, args)
        return res
    }

    internal fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        return call0(callCtx, args)
    }

    private fun call0(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        val body = bodyLate.get()
        val rtSubFrame = createCallFrame(callCtx, body.frame)
        processArgs(body.paramVars, body.params, args, rtSubFrame)

        val res = body.body.execute(rtSubFrame)

        val retVal = if (res is R_StatementResult_Return) res.value else null
        return retVal ?: Rt_UnitValue
    }

    override fun toString() = defName.appLevelName

    companion object {
        internal fun createCallFrame(callCtx: Rt_CallContext, targetFrame: R_CallFrame): Rt_CallFrame {
            val dbUpdateAllowed = callCtx.dbUpdateAllowed()
            val subDefCtx = Rt_DefinitionContext(callCtx.defCtx.exeCtx, dbUpdateAllowed, targetFrame.defId)
            return targetFrame.createRtFrame(subDefCtx, null)
        }
    }
}

class R_FunctionDefinition internal constructor(
    base: R_DefinitionBase,
    private val fnBase: R_FunctionBase,
    val isTest: Boolean = false,
    val disabled: Boolean = false,
): R_RoutineDefinition(base) {
    override fun params() = fnBase.getBody().params

    fun callTop(exeCtx: Rt_ExecutionContext, args: List<Rt_Value>, dbUpdateAllowed: Boolean = false): Rt_Value {
        return fnBase.callTop(exeCtx, args, dbUpdateAllowed)
    }

    override fun call(callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
        return fnBase.call(callCtx, args)
    }

    override fun toMetaGtv(): Gtv {
        val header = fnBase.getHeader()
        return mapOf(
            "type" to header.type.toMetaGtv(),
            "parameters" to header.params.map { it.toMetaGtv() }.toGtv(),
        ).toGtv()
    }
}

private fun checkCallArgs(routine: R_RoutineDefinition, params: List<R_FunctionParam>, args: List<Rt_Value>) {
    val name = routine.appLevelName

    if (args.size != params.size) {
        throw Rt_Exception.common("fn_wrong_arg_count:$name:${params.size}:${args.size}",
                "Wrong number of arguments for '$name': ${args.size} instead of ${params.size}")
    }

    for (i in params.indices) {
        val param = params[i]
        val argType = args[i].type()
        if (!param.type.isAssignableFrom(argType)) {
            throw Rt_Exception.common("fn_wrong_arg_type:$name:${param.type.strCode()}:${argType.strCode()}",
                    "Wrong type of argument '${param.name}' for '$name': " +
                            "${argType.strCode()} instead of ${param.type.strCode()}")
        }
    }
}

private fun processArgs(
    paramVars: List<R_ParamVar>,
    params: List<R_FunctionParam>,
    args: List<Rt_Value>,
    frame: Rt_CallFrame,
) {
    checkEquals(args.size, paramVars.size)
    checkEquals(args.size, params.size)
    for (i in paramVars.indices) {
        val paramVar = paramVars[i]
        val param = params[i]
        val arg = args[i]
        param.validator?.check(arg)?.raise()
        frame.set(paramVar.ptr, paramVar.type, arg, false)
    }
}
