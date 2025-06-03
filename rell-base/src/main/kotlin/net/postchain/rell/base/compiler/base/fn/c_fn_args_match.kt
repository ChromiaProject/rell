/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.expr.C_CallArgument
import net.postchain.rell.base.compiler.base.expr.C_CallArgumentValue_Expr
import net.postchain.rell.base.compiler.base.expr.C_CallArgumentValue_Wildcard
import net.postchain.rell.base.compiler.base.expr.C_CallArguments
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_MessageManager
import net.postchain.rell.base.compiler.base.utils.C_ParameterDefaultValue
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.utils.*

class C_ArgMatchParam(
    val index: Int,
    val name: R_Name?,
    val arity: M_ParamArity,
    val defaultValue: C_ParameterDefaultValue?,
) {
    override fun toString(): String {
        return if (name == null) arity.name else "${arity.name}($name)"
    }

    fun nameCodeMsg(): C_CodeMsg {
        val code = if (name != null) "$index:$name" else "$index"
        val msg = if (name != null) "'$name'" else "${index+1}"
        return C_CodeMsg(code, msg)
    }
}

class C_ArgMatchParams(val all: ImmList<C_ArgMatchParam>) {
    val mandatoryAndOptional: ImmList<C_ArgMatchParam>
    val vararg: C_ArgMatchParam? // If the vararg is ONE_MANY, it's also added as the last item of the mandatory.

    init {
        val oneCount = all.countWhile { it.arity == M_ParamArity.ONE }

        var tail = all.subList(oneCount, all.size)
        val zeroOneCount = tail.countWhile { it.arity == M_ParamArity.ZERO_ONE }

        tail = tail.subList(zeroOneCount, tail.size)
        check(tail.size <= 1) { "Invalid arguments: $all" }

        var mandatoryCount = oneCount

        val last = tail.firstOrNull()
        if (last != null) {
            check(last.arity.many) { last }
            if (last.arity == M_ParamArity.ONE_MANY) {
                check(zeroOneCount == 0) { "Invalid parameters: one-many after zero-one $all" }
                mandatoryCount += 1
            }
        }

        mandatoryAndOptional = all.subList(0, mandatoryCount + zeroOneCount)
        vararg = last
    }
}

sealed class C_ArgMatchArg(val param: C_ArgMatchParam)
class C_ArgMatchArg_Expr(param: C_ArgMatchParam, val vExpr: V_Expr): C_ArgMatchArg(param)
class C_ArgMatchArg_Default(param: C_ArgMatchParam, val defaultValue: C_ParameterDefaultValue): C_ArgMatchArg(param)

class C_ArgMatchParamArg(val param: C_ArgMatchParam, val wild: Boolean, val index: Int, val callArg: C_CallArgument?)

class C_ArgMatcherResult(
    val matching: C_ArgMatching?,
    val paramValues: ImmList<Pair<C_ArgMatchParam, V_Expr>>,
)

class C_ArgMatching(
    val exprArgs: ImmList<C_ArgMatchArg>,
    val wildArgs: ImmList<C_ArgMatchParam>,
    val mapping: ImmList<C_ArgMatchParamArg>,
) {
    val exprsToParams: ImmList<Int>

    init {
        checkEquals(mapping.size, exprArgs.size + wildArgs.size)

        val list = MutableList<Int?>(exprArgs.size) { null }
        for ((i, m) in mapping.withIndex()) {
            if (!m.wild) {
                check(list[m.index] == null)
                list[m.index] = i
            }
        }
        exprsToParams = checkNotNull(list.filterNotNullAllOrNull())
    }

    fun <T> valuesToParams(values: List<T>): List<T> {
        checkEquals(values.size, mapping.size)
        return mapping.map {
            check(!it.wild)
            values[it.index]
        }
    }

    fun <T> paramsToValues(params: List<T>): ImmList<T> {
        checkEquals(params.size, mapping.size)
        checkEquals(wildArgs.size, 0)
        return exprsToParams.mapToImmList { params[it] }
    }
}

object C_ArgMatcher {
    fun bind(
        msgMgr: C_MessageManager,
        callInfo: C_FunctionCallInfo,
        params: C_ArgMatchParams,
        args: C_CallArguments,
        partial: Boolean,
    ): C_ArgMatcherResult {
        val errWatcher = msgMgr.errorWatcher()
        val positionalParams = matchPositionalArgs(msgMgr, callInfo, params, args)

        val actualParams = if (positionalParams.size >= params.mandatoryAndOptional.size) {
            positionalParams
        } else {
            params.mandatoryAndOptional
        }

        val builder = MatchBuilder(msgMgr, callInfo, actualParams)

        for ((i, param) in positionalParams.withIndex()) {
            val arg = args.positional[i]
            builder.addArgument(i, param, arg)
        }

        for ((name, arg) in args.named) {
            bindNamedArg(callInfo, params, builder, name, arg)
        }

        for ((i, param) in params.mandatoryAndOptional.withIndex()) {
            if (!builder.isBound(i)) {
                if (param.defaultValue != null) {
                    builder.addDefault(i, param, param.defaultValue)
                } else if (partial && param.arity == M_ParamArity.ONE) {
                    builder.addWildcard(i, param)
                }
            }
        }

        val error = errWatcher.hasNewErrors()
        return builder.build(!error)
    }

    private fun bindNamedArg(
        callInfo: C_FunctionCallInfo,
        params: C_ArgMatchParams,
        builder: MatchBuilder,
        name: C_Name,
        arg: C_CallArgument,
    ) {
        val param = params.all.firstOrNull { it.name == name.rName }

        if (param == null) {
            builder.errNamedArg(name, "unknown_named_arg") {
                val fnMsg = if (callInfo.functionName == null) "Function" else "Function '${callInfo.functionName}'"
                "$fnMsg has no parameter '$name'"
            }
        } else if (param.arity == M_ParamArity.ZERO_MANY) {
            errVararg(builder, name)
        } else if (param.index >= params.mandatoryAndOptional.size) {
            // Must not happen, but handle just in case.
            builder.errNamedArg(name, "named_invalid") { "Parameter '$name' cannot be specified by name" }
        } else if (builder.isBound(param.index)) {
            builder.errNamedArg(name, "named_conflict") { "Parameter '$name' already specified" }
        } else if (param.arity == M_ParamArity.ONE_MANY) {
            // Order of checks is important - handling one-many parameters after isBound() check.
            errVararg(builder, name)
            // Bind the parameter to not also get the "missing argument" error.
            builder.addArgument(param.index, param, arg)
        } else {
            builder.addArgument(param.index, param, arg)
        }
    }

    private fun errVararg(builder: MatchBuilder, name: C_Name) {
        builder.errNamedArg(name, "named_arg_vararg") {
            "Parameter '$name' is variadic, cannot be specified by name"
        }
    }

    private fun matchPositionalArgs(
        msgMgr: C_MessageManager,
        callInfo: C_FunctionCallInfo,
        params: C_ArgMatchParams,
        args: C_CallArguments,
    ): ImmList<C_ArgMatchParam> {
        return if (args.positional.size <= params.mandatoryAndOptional.size) {
            params.mandatoryAndOptional.subList(0, args.positional.size)
        } else if (params.vararg != null) {
            val varargCount = args.positional.size - params.mandatoryAndOptional.size
            val varargParams = generateSequence { params.vararg }.take(varargCount).toList()
            params.mandatoryAndOptional + varargParams
        } else {
            val expCount = params.mandatoryAndOptional.size
            val actCount = args.all.size
            msgMgr.error(callInfo.callPos) {
                val fnNameCode = callInfo.functionNameCode()
                var msg = "Too many arguments"
                if (callInfo.functionName != null) msg += " for function '${callInfo.functionName}'"
                msg += ": $actCount instead of $expCount"
                "expr:call:too_many_args:[$fnNameCode]:$expCount:$actCount" toCodeMsg msg
            }
            params.mandatoryAndOptional
        }
    }

    private class MatchBuilder(
        private val msgMgr: C_MessageManager,
        private val callInfo: C_FunctionCallInfo,
        private val actualParams: ImmList<C_ArgMatchParam>,
    ) {
        private val exprArgs = mutableListOf<C_ArgMatchArg>()
        private val wildArgs = mutableListOf<C_ArgMatchParam>()
        private val mappings = MutableList<C_ArgMatchParamArg?>(actualParams.size) { null }
        private var namedArgError = false

        fun isBound(paramIndex: Int) = mappings[paramIndex] != null

        fun addArgument(paramIndex: Int, param: C_ArgMatchParam, arg: C_CallArgument) {
            checkNotBound(paramIndex)
            when (arg.value) {
                is C_CallArgumentValue_Expr -> {
                    val argMatch = C_ArgMatchArg_Expr(param, arg.value.vExpr)
                    addExpr0(paramIndex, param, argMatch, arg)
                }
                is C_CallArgumentValue_Wildcard -> {
                    addWild0(paramIndex, param, arg)
                }
            }
        }

        fun addDefault(paramIndex: Int, param: C_ArgMatchParam, defaultValue: C_ParameterDefaultValue) {
            checkNotBound(paramIndex)
            addExpr0(paramIndex, param, C_ArgMatchArg_Default(param, defaultValue), null)
        }

        fun addWildcard(paramIndex: Int, param: C_ArgMatchParam) {
            checkNotBound(paramIndex)
            addWild0(paramIndex, param, null)
        }

        fun errNamedArg(name: C_Name, code: String, lazyMsg: () -> String) {
            namedArgError = true
            msgMgr.error(name.pos) {
                val msg = lazyMsg()
                val fnNameCode = callInfo.functionNameCode()
                "expr:call:$code:[$fnNameCode]:$name" toCodeMsg msg
            }
        }

        private fun checkNotBound(paramIndex: Int) {
            check(mappings[paramIndex] == null)
        }

        private fun addExpr0(paramIndex: Int, param: C_ArgMatchParam, arg: C_ArgMatchArg, callArg: C_CallArgument?) {
            val index = exprArgs.size
            exprArgs.add(arg)
            mappings[paramIndex] = C_ArgMatchParamArg(param, false, index, callArg)
        }

        private fun addWild0(paramIndex: Int, param: C_ArgMatchParam, callArg: C_CallArgument?) {
            val index = wildArgs.size
            wildArgs.add(param)
            mappings[paramIndex] = C_ArgMatchParamArg(param, true, index, callArg)
        }

        fun build(valid: Boolean): C_ArgMatcherResult {
            val finalMappings = finishMappings()

            val missingParams = finalMappings.withIndex()
                .filter { it.value == null }
                .map { actualParams[it.index] }
                .filter {
                    // Don't report unnamed parameters, if an error has already been reported, avoid redundant errors.
                    it.name != null || !namedArgError
                }
            processMissingParams(missingParams)

            val resMappings = finalMappings.filterNotNullAllOrNull()
            val matching = if (!valid || namedArgError || resMappings == null) null else {
                C_ArgMatching(exprArgs.toImmList(), wildArgs.toImmList(), resMappings)
            }

            val paramValues = finalMappings
                .filterNotNull()
                .mapNotNullToImmList { m ->
                    if (m.wild) null else {
                        val arg = exprArgs[m.index]
                        when (arg) {
                            is C_ArgMatchArg_Expr -> m.param to arg.vExpr
                            is C_ArgMatchArg_Default -> null
                        }
                    }
                }

            return C_ArgMatcherResult(matching, paramValues)
        }

        private fun finishMappings(): List<C_ArgMatchParamArg?> {
            // Remove optional unresolved parameters.
            var end = mappings.size
            while (end > 0 && mappings[end - 1] == null && actualParams[end - 1].arity == M_ParamArity.ZERO_ONE) {
                --end
            }
            return mappings.subList(0, end).toList()
        }

        private fun processMissingParams(missingParams: List<C_ArgMatchParam>) {
            if (missingParams.isEmpty()) return
            val fnNameCode = callInfo.functionNameCode()
            val listCode = missingParams.joinToString(",") { it.nameCodeMsg().code }
            val missingNames = missingParams.mapNotNull { it.name }
            val listMsg = if (missingNames.isNotEmpty()) missingNames.joinToString(", ") else "n/a"
            val msg = "Missing argument(s): $listMsg"
            msgMgr.error(callInfo.callPos, "expr:call:missing_args:[$fnNameCode]:[$listCode]", msg)
        }
    }
}
