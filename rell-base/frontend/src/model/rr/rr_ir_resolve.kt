/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.compiler.base.expr.C_MemberAttr_SysProperty
import net.postchain.rell.base.compiler.vexpr.V_MemberFunctionCall_CommonCall
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.stmt.*
import net.postchain.rell.base.utils.*

// toRR() extensions are defined in r_frame.kt

/**
 * Provides index lookups needed to convert R_ trees to RR_ equivalents.
 */
internal interface RR_ResolverContext {
    fun resolveType(rType: R_Type): RR_Type
    fun resolveEntityIndex(def: R_EntityDefinition): Int
    fun resolveObjectIndex(def: R_ObjectDefinition): Int
    fun resolveStructIndexByRStruct(struct: R_Struct): Int?
    fun resolveFunctionIndex(def: R_FunctionDefinition): Int?
    fun resolveFunctionIndexByFnBase(fnBase: R_FunctionBase): Int?
    fun resolveQueryIndex(def: R_QueryDefinition): Int?
    fun resolveOperationIndex(def: R_OperationDefinition): Int?
    fun resolveConstantIndex(def: R_GlobalConstantDefinition): Int?
    fun resolveEnumIndex(def: R_EnumDefinition): Int?

    /** Resolve an R_FunctionBase into a serializable RR_FunctionBase. */
    fun resolveFnBase(fnBase: R_FunctionBase): RR_FunctionBase
}

/**
 * Converts R_Expr/R_Statement/Db_Expr trees to their RR_ equivalents.
 */
internal class RR_IrResolver(
    private val ctx: RR_ResolverContext,
    private val resolverRuntime: RR_ResolverRuntime,
) {

    private fun resolveType(rType: R_Type) = ctx.resolveType(rType)
    private fun resolveEntityIndex(def: R_EntityDefinition) = ctx.resolveEntityIndex(def)
    private fun resolveObjectIndex(def: R_ObjectDefinition) = ctx.resolveObjectIndex(def)
    private fun resolveStructIndexByRStruct(struct: R_Struct) = ctx.resolveStructIndexByRStruct(struct)
    private fun resolveFunctionIndex(def: R_FunctionDefinition) = ctx.resolveFunctionIndex(def)
    private fun resolveOperationIndex(def: R_OperationDefinition) = ctx.resolveOperationIndex(def)
    private fun resolveEnumIndex(def: R_EnumDefinition) = ctx.resolveEnumIndex(def)

    // =========================================================================
    // Expressions
    // =========================================================================

    fun resolveExpr(expr: R_Expr): RR_Expr {
        val type = resolveType(expr.type)
        return when (expr) {
            is R_VarExpr -> RR_Expr.Var(type, expr.ptr.toRR(), expr.name)
            is R_RRConstantValueExpr -> {
                // For enum constants, resolve the placeholder enumDefIndex to the actual index
                // (the AST stores 0 because it doesn't have access to the resolver context).
                val rrValue = if (expr.rrValue is RR_ConstantValue.Enum && expr.type is R_EnumType) {
                    val idx = resolveEnumIndex(expr.type.enum) ?: 0
                    expr.rrValue.copy(enumDefIndex = idx)
                } else {
                    expr.rrValue
                }
                RR_Expr.ConstantValue(type, rrValue)
            }
            is R_BinaryExpr -> {
                val key = binaryOpKey(expr.op)
                val cmpInfo = resolveCmpInfo(expr.op)
                RR_Expr.Binary(type, key, cmpInfo, resolveExpr(expr.left), resolveExpr(expr.right), expr.errPos)
            }

            is R_UnaryExpr -> {
                RR_Expr.Unary(type, expr.op.kindName, resolveExpr(expr.expr), expr.errPos)
            }

            is R_IfExpr -> RR_Expr.If(
                type,
                resolveExpr(expr.cond),
                resolveExpr(expr.trueExpr),
                resolveExpr(expr.falseExpr)
            )

            is R_WhenExpr -> RR_Expr.When(
                type,
                resolveWhenChooser(expr.chooser),
                expr.exprs.mapToImmList { resolveExpr(it) })

            is R_ElvisExpr -> RR_Expr.Elvis(type, resolveExpr(expr.left), resolveExpr(expr.right))
            is R_NotNullExpr -> RR_Expr.NotNull(type, resolveExpr(expr.expr), expr.errPos)
            is R_TupleExpr -> RR_Expr.TupleLiteral(type, expr.exprs.mapToImmList { resolveExpr(it) })
            is R_ListLiteralExpr -> RR_Expr.ListLiteral(type, expr.exprs.mapToImmList { resolveExpr(it) })
            is R_MapLiteralExpr -> RR_Expr.MapLiteral(
                type,
                expr.entries.mapToImmList { resolveExpr(it.first) },
                expr.entries.mapToImmList { resolveExpr(it.second) },
                expr.errPos
            )

            is R_StructExpr -> RR_Expr.StructCreate(
                type,
                resolveStructIndexByRStruct(expr.struct) ?: 0,
                expr.attrs.mapToImmList { resolveCreateAttr(it) })

            is R_RegularCreateExpr -> RR_Expr.RegularCreate(
                type,
                resolveEntityIndex(expr.rEntity),
                expr.errPos,
                expr.attrs.mapToImmList { resolveCreateAttr(it) })

            is R_StructCreateExpr -> RR_Expr.StructEntityCreate(
                type,
                resolveEntityIndex(expr.rEntity),
                expr.errPos,
                resolveStructIndexByRStruct(expr.structType.struct) ?: 0,
                resolveExpr(expr.structExpr)
            )

            is R_StructListCreateExpr -> RR_Expr.StructListCreate(
                type,
                resolveEntityIndex(expr.rEntity),
                expr.errPos,
                resolveStructIndexByRStruct(expr.structType.struct) ?: 0,
                resolveType(expr.resultListType),
                resolveExpr(expr.listExpr)
            )

            is R_FunctionCallExpr -> RR_Expr.FunctionCall(
                type,
                expr.base?.let { resolveExpr(it) },
                resolveFunctionCall(expr.call),
                expr.safe
            )

            is R_MemberExpr -> RR_Expr.MemberAccess(
                type,
                resolveExpr(expr.base),
                resolveMemberCalculator(expr.calculator),
                expr.safe
            )

            is R_AssignExpr -> {
                val key = binaryOpKey(expr.op)
                RR_Expr.Assign(type, key, resolveExpr(expr.dstExpr), resolveExpr(expr.srcExpr), expr.post)
            }

            is R_StatementExpr -> RR_Expr.StatementExpr(type, resolveStmt(expr.stmt))
            is R_GlobalConstantExpr -> RR_Expr.GlobalConstant(type, expr.constId.index)
            is R_ChainHeightExpr -> RR_Expr.ChainHeight(type, expr.chain.index)
            is R_TypeAdapterExpr -> RR_Expr.TypeAdapter(type, resolveExpr(expr.expr), resolveTypeAdapter(expr.adapter))

            is R_ParameterDefaultValueExpr -> {
                val rFrame = expr.initFrameGetter.get()
                RR_Expr.ParameterDefaultValue(
                    type,
                    expr.callFilePos,
                    rFrame.toRR(),
                    rFrame.defId,
                    resolveExpr(expr.exprGetter.get())
                )
            }

            is R_AttributeDefaultValueExpr -> {
                val rFrame = expr.initFrameGetter.get()
                RR_Expr.AttributeDefaultValue(
                    type,
                    expr.attr.index,
                    expr.attr.name,
                    expr.createFilePos,
                    rFrame.toRR(),
                    rFrame.defId,
                    resolveExpr(expr.attr.expr!!)
                )
            }

            is R_ErrorExpr -> RR_Expr.Error(type, expr.message)

            // Subscript expressions — field is `expr` not `index`/`key`
            is R_ListSubscriptExpr -> RR_Expr.ListSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_MapSubscriptExpr -> RR_Expr.MapSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_TextSubscriptExpr -> RR_Expr.TextSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_ByteArraySubscriptExpr -> RR_Expr.ByteArraySubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_VirtualListSubscriptExpr -> RR_Expr.VirtualListSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr)
            )

            is R_VirtualMapSubscriptExpr -> RR_Expr.VirtualMapSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_JsonArraySubscriptExpr -> RR_Expr.JsonArraySubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_JsonObjectSubscriptExpr -> RR_Expr.JsonObjectSubscript(
                type,
                resolveExpr(expr.base),
                resolveExpr(expr.expr),
                expr.errPos
            )

            is R_StructMemberExpr -> RR_Expr.StructMember(type, resolveExpr(expr.base), expr.attr.name, expr.attr.index)

            is R_DbAtExpr -> resolveDbAtExpr(type, expr)
            is R_ColAtExpr -> resolveColAtExpr(type, expr)

            is R_ObjectExpr -> {
                val objDefIndex = resolveObjectIndex(expr.objType.rObject)
                RR_Expr.ObjectValue(type, objDefIndex)
            }

            // R_LazyExpr wraps an inner R_Expr — extract via reflection
            is R_LazyExpr -> RR_Expr.Lazy(type, resolveExpr(expr.innerExpr))

            // R_ObjectAttrExpr wraps a Db_AtExprBase for object attribute access
            is R_ObjectAttrExpr -> {
                val atBase = expr.atBase
                val rObject = expr.rObject
                val objectName = rObject.appLevelName
                val objectDefIndex = ctx.resolveObjectIndex(rObject)
                val from = RR_DbAtFrom(atBase.from.from.mapToImmList { resolveDbAtFromItem(it) })
                val (flatWhat, whatFieldGroups) = resolveAtWhatFieldsWithGroups(atBase.what)
                val where = resolveDbAtWhere(atBase)
                val internals = RR_DbAtInternals(atBase.from.block?.toRR())
                RR_Expr.DbAt(
                    type,
                    from,
                    flatWhat,
                    where,
                    AtCardinality.ONE,
                    null,
                    internals,
                    ErrorPos("", 0),
                    whatFieldGroups,
                    objectName,
                    objectDefIndex
                )
            }

            // Every R_Expr subclass above is handled explicitly. An unhandled case signals a
            // compiler bug (a new R_Expr variant without resolver coverage), so fail loud
            // rather than silently emitting an RR_Expr.Error at resolve time.
            else -> error("Unsupported R_Expr in resolver: ${expr.javaClass.name}")
        }
    }

    // =========================================================================
    // Statements
    // =========================================================================

    fun resolveStmt(stmt: R_Statement): RR_Statement = when (stmt) {
        is R_EmptyStatement -> RR_Statement.Empty
        is R_VarStatement -> RR_Statement.Var(resolveVarDeclarator(stmt.declarator), stmt.expr?.let { resolveExpr(it) })
        is R_ReturnStatement -> RR_Statement.Return(stmt.expr?.let { resolveExpr(it) })
        is R_BlockStatement -> RR_Statement.Block(stmt.stmts.mapToImmList { resolveStmt(it) }, stmt.frameBlock.toRR())
        is R_ExprStatement -> RR_Statement.Expr(resolveExpr(stmt.expr))
        is R_ReplExprStatement -> RR_Statement.ReplExpr(resolveExpr(stmt.expr))
        is R_AssignStatement -> {
            val key = stmt.op?.let { binaryOpKey(it) }
            RR_Statement.Assign(resolveExpr(stmt.dstExpr), resolveExpr(stmt.expr), key)
        }

        is R_IfStatement -> RR_Statement.If(
            resolveExpr(stmt.expr),
            resolveStmt(stmt.trueStmt),
            resolveStmt(stmt.falseStmt)
        )

        is R_WhenStatement -> RR_Statement.When(
            resolveWhenChooser(stmt.chooser),
            stmt.stmts.mapToImmList { resolveStmt(it) })

        is R_WhileStatement -> RR_Statement.While(
            resolveExpr(stmt.expr),
            resolveStmt(stmt.stmt),
            stmt.frameBlock.toRR()
        )

        is R_ForStatement -> RR_Statement.For(
            resolveVarDeclarator(stmt.varDeclarator),
            resolveExpr(stmt.expr),
            resolveIterableAdapter(stmt.iterator),
            resolveStmt(stmt.stmt),
            stmt.frameBlock.toRR()
        )

        is R_BreakStatement -> RR_Statement.Break
        is R_ContinueStatement -> RR_Statement.Continue
        is R_GuardStatement -> RR_Statement.Guard(resolveStmt(stmt.subStmt))
        is R_LambdaStatement -> {
            val argExprs = stmt.args.mapToImmList { resolveExpr(it.first) }
            val argPtrs = stmt.args.mapToImmList { it.second.toRR() }
            RR_Statement.Lambda(argExprs, argPtrs, stmt.block.toRR(), resolveStmt(stmt.stmt))
        }

        is R_UpdateStatement -> resolveUpdateStmt(stmt)
        is R_DeleteStatement -> resolveDeleteStmt(stmt)
        // Every R_Statement subclass above is handled explicitly; fail loud on unknown.
        else -> error("Unsupported R_Statement in resolver: ${stmt.javaClass.name}")
    }

    // =========================================================================
    // Database expressions
    // =========================================================================

    private fun resolveDbExpr(expr: Db_Expr): RR_DbExpr = when (expr) {
        is Db_InterpretedExpr -> RR_DbExpr.Interpreted(resolveExpr(expr.expr))
        is Db_BinaryExpr -> {
            // Special case: BigInteger division uses DIV() function, not infix /
            if (expr.op is Db_BinaryOp_Div_BigInteger) {
                RR_DbExpr.Call(
                    resolveType(expr.type),
                    RR_DbSysFn.Simple("DIV"),
                    immListOf(resolveDbExpr(expr.left), resolveDbExpr(expr.right))
                )
            } else {
                val sqlOp = extractDbBinaryOpSql(expr.op)
                val nullableEq = expr.op.code == "==?" || expr.op.code == "!=?"
                RR_DbExpr.Binary(
                    resolveType(expr.type),
                    sqlOp,
                    resolveDbExpr(expr.left),
                    resolveDbExpr(expr.right),
                    nullableEq
                )
            }
        }

        is Db_UnaryExpr -> {
            val sqlOp = expr.op.sql
            RR_DbExpr.Unary(resolveType(expr.type), sqlOp, resolveDbExpr(expr.expr))
        }

        is Db_EntityExpr -> RR_DbExpr.Entity(resolveEntityIndex(expr.entity.rEntity), expr.entity.id.id.toInt())
        is Db_RelExpr -> RR_DbExpr.Rel(resolveDbExpr(expr.base), expr.attr.name, resolveEntityIndex(expr.rEntity))
        is Db_AttrExpr -> RR_DbExpr.Attr(resolveDbExpr(expr.base), expr.attr.name, resolveType(expr.attr.type))
        is Db_RowidExpr -> RR_DbExpr.Rowid(resolveDbExpr(expr.base))
        is Db_CollectionInterpretedExpr -> RR_DbExpr.CollectionInterpreted(resolveExpr(expr.expr))
        is Db_InExpr -> RR_DbExpr.In(
            resolveDbExpr(expr.keyExpr),
            expr.exprs.mapToImmList { resolveDbExpr(it) },
            expr.not
        )

        is Db_ElvisExpr -> RR_DbExpr.Elvis(resolveType(expr.type), resolveDbExpr(expr.left), resolveDbExpr(expr.right))
        is Db_CallExpr -> {
            RR_DbExpr.Call(resolveType(expr.type), resolveDbSysFn(expr.fn), expr.args.mapToImmList { resolveDbExpr(it) })
        }

        is Db_ExistsExpr -> RR_DbExpr.Exists(resolveDbExpr(expr.subExpr), expr.not)
        is Db_InCollectionExpr -> RR_DbExpr.InCollection(resolveDbExpr(expr.left), resolveExpr(expr.right), expr.not)
        is Db_WhenExpr -> {
            val rrCases = expr.cases.mapToImmList { c ->
                RR_DbWhenCase(c.conds.mapToImmList { resolveDbExpr(it) }, resolveDbExpr(c.expr))
            }
            val elseExpr = resolveDbExpr(expr.elseExpr)
            RR_DbExpr.When(resolveType(expr.type), expr.keyExpr?.let { resolveDbExpr(it) }, rrCases, elseExpr)
        }

        is Db_NestedAtExpr -> {
            // Convert nested at-expression to an inline SubQuery that can be generated
            // as a correlated subquery inside the outer SQL (needed for EXISTS/IN).
            val from =
                RR_DbAtFrom(expr.base.from.from.mapToImmList { resolveDbAtFromItem(it) }, expr.base.from.block?.toRR())
            val what = expr.base.what.flatMapToImmList { field -> resolveAtWhatField(field) }
            val where = resolveDbAtWhere(expr.base)
            val extras =
                RR_AtExtras(expr.extras.limit?.let { resolveExpr(it) }, expr.extras.offset?.let { resolveExpr(it) })
            val internals = RR_DbAtInternals(expr.block.toRR())
            RR_DbExpr.SubQuery(from, what, where, extras, expr.base.isMany, internals)
        }

        // Every Db_Expr subclass above is handled explicitly; fail loud on unknown.
        else -> error("Unsupported Db_Expr in resolver: ${expr.javaClass.name}")
    }

    // =========================================================================
    // Supporting type resolution
    // =========================================================================

    private fun resolveFunctionCall(call: R_FunctionCall): RR_FunctionCall = when (call) {
        is R_FullFunctionCall -> RR_FunctionCall.Full(
            returnType = resolveType(call.returnType),
            target = resolveFunctionCallTarget(
                call.target, fullCallArgTypes(call), call.returnType, call.callPos,
            ),
            args = call.args.mapToImmList { resolveExpr(it) },
            callPos = call.callPos,
            mapping = call.mapping,
        )

        is R_PartialFunctionCall -> RR_FunctionCall.Partial(
            returnType = resolveType(call.returnType),
            target = resolveFunctionCallTarget(
                call.target, partialCallArgTypes(call), call.returnType, callPos = null,
            ),
            args = call.args.mapToImmList { resolveExpr(it) },
            wildArgCount = call.mapping.wildCount,
            mappingValues = call.mapping.args.mapToImmList { if (it.wild) -(it.index + 1) else it.index },
        )

        // R_FunctionCall is sealed at R_FullFunctionCall / R_PartialFunctionCall; fail loud.
        else -> error("Unsupported R_FunctionCall in resolver: ${call.javaClass.name}")
    }

    /** All argument types at the call site, in caller order. */
    private fun fullCallArgTypes(call: R_FullFunctionCall): List<R_Type> =
        call.args.map { it.type }

    /**
     * Full parameter type list for a partial sys-function call. `call.args` only contains
     * bound args, so we reconstruct the signature from the returned function type when the
     * compiler supplied one; otherwise fall back to bound-arg types (rare, and the resulting
     * key is still content-derived — only uniqueness across partial-call overloads degrades).
     */
    private fun partialCallArgTypes(call: R_PartialFunctionCall): List<R_Type> {
        val retFnType = call.returnType as? R_FunctionType
        return retFnType?.params ?: call.args.map { it.type }
    }

    /**
     * Deterministic content-derived key for a sys-function call target.
     *
     * The key is `displayName#kind#signature` where `#` is a separator that does not appear
     * in any Rell type's `strCode()` (type syntax uses `<>,()?: `, not `#`). `kind` is `G`
     * for global calls and `M` for member calls — they must be disambiguated because the
     * interpreter prepends `base` to args for members but not for globals, and the same
     * `fullName` can be emitted from both pathways (e.g. `integer.to_text` is used by
     * both explicit `x.to_text(radix)` and the implicit `text + x` adapter, which disagree
     * on the runtime arg-count convention).
     *
     * Same overload at different call sites produces the same key; distinct overloads and
     * distinct call kinds produce distinct keys. No JVM-local identity information enters
     * the key, so serialized [RR_App] bytes are stable across processes.
     *
     * The prefix is consumed by `sysFnDisplayName` (in runtime/rt_fn_call_dispatch.kt) at the
     * runtime call site to recover the user-facing name for error messages.
     */
    private fun buildSysFnKey(
        fullName: String,
        argTypes: List<R_Type>,
        resultType: R_Type,
        isMember: Boolean,
        callPos: FilePos?,
    ): String {
        val kind = if (isMember) "M" else "G"
        val argSig = argTypes.joinToString(",") { it.strCode() }
        // Include call position so meta-bodies that capture `fnBodyMeta.callPos` (e.g. `log()`,
        // require() messages, stack traces) produce distinct R_SysFunction instances per call
        // site. Same source file+line across independent compilations produces the same key, so
        // the key remains content-addressed and deterministic across JVMs.
        val posSig = callPos?.let { "@${it.file}:${it.line}" } ?: ""
        return "$fullName#$kind#($argSig)->${resultType.strCode()}$posSig"
    }

    /**
     * Resolves a sys-function target used inside an at-expression combiner (group/aggregation).
     * Call-site argument types aren't available here — the runtime values come from the at-clause's
     * group aggregation — so the key uses a `C` kind marker plus the function's display name and
     * result type. This is distinct from direct-call keys ([buildSysFnKey] with `G`/`M`), so the
     * combiner registry never conflicts with the direct-call registry.
     */
    private fun resolveCombinerCallTarget(
        target: R_FunctionCallTarget,
        resultType: R_Type,
    ): RR_FunctionCallTarget = when (target) {
        is R_FunctionCallTarget_SysGlobalFunction -> {
            val fn = target.fn
            val name = "${target.fullName.value}#C#->${resultType.strCode()}"
            resolverRuntime.registerSysFn(name, fn)
            RR_FunctionCallTarget.SysGlobal(name)
        }
        is R_FunctionCallTarget_SysMemberFunction -> {
            val fn = target.fn
            val name = "${target.fullName.value}#C#->${resultType.strCode()}"
            resolverRuntime.registerSysFn(name, fn)
            RR_FunctionCallTarget.SysMember(name)
        }
        // Non-sys targets don't pass through the stdlib registry — fall back to the regular path
        // with no arg types and no callPos (unused for these cases).
        else -> resolveFunctionCallTarget(target, emptyList(), resultType, callPos = null)
    }

    /**
     * @param callPos the file position of the call site, or `null` when not known (e.g. partial
     *   calls or combiner positions). Used only to disambiguate sys-fn keys for meta-bodies that
     *   capture the call position (e.g. `log()`) — without it, first-vs-last-write on the stdlib
     *   map would collapse multiple `log()` calls into sharing one captured position.
     */
    private fun resolveFunctionCallTarget(
        target: R_FunctionCallTarget,
        callArgTypes: List<R_Type>,
        callResultType: R_Type,
        callPos: FilePos?,
    ): RR_FunctionCallTarget = when (target) {
        is R_FunctionCallTarget_RegularUserFunction -> {
            val fn = target.fn
            val idx = when (fn) {
                is R_FunctionDefinition -> resolveFunctionIndex(fn) ?: 0
                is R_QueryDefinition -> ctx.resolveQueryIndex(fn) ?: 0
                else -> 0
            }
            val isQuery = fn is R_QueryDefinition
            if (isQuery) RR_FunctionCallTarget.RegularQuery(idx) else RR_FunctionCallTarget.RegularUser(idx)
        }

        is R_FunctionCallTarget_AbstractUserFunction -> {
            // Resolve the override function, not the abstract base.
            val overrideFnBase = target.overrideGetter.get()
            val overrideIdx = ctx.resolveFunctionIndexByFnBase(overrideFnBase)
            if (overrideIdx != null) {
                RR_FunctionCallTarget.AbstractUser(overrideIdx)
            } else {
                // Override is not a top-level module function — resolve its body into RR_FunctionBase.
                val rrFnBase = ctx.resolveFnBase(overrideFnBase)
                RR_FunctionCallTarget.AbstractOverride(rrFnBase)
            }
        }

        is R_FunctionCallTarget_ExtendableUserFunction -> {
            val combinerKind = resolveExtendableCombinerKind(target.descriptor.combiner)
            val returnType = resolveExtendableReturnType(target.descriptor.combiner, target.baseFn)
            RR_FunctionCallTarget.Extendable(target.descriptor.uid.id, combinerKind, returnType)
        }

        is R_FunctionCallTarget_NativeUserFunction -> RR_FunctionCallTarget.NativeUser(target.fnName)
        is R_FunctionCallTarget_Operation -> RR_FunctionCallTarget.Operation(resolveOperationIndex(target.op) ?: 0)
        is R_FunctionCallTarget_FunctionValue -> RR_FunctionCallTarget.FunctionValue
        is R_FunctionCallTarget_SysGlobalFunction -> {
            val fn = target.fn
            val name = buildSysFnKey(target.fullName.value, callArgTypes, callResultType, isMember = false, callPos)
            resolverRuntime.registerSysFn(name, fn)
            RR_FunctionCallTarget.SysGlobal(name)
        }

        is R_FunctionCallTarget_SysMemberFunction -> {
            val fn = target.fn
            val name = buildSysFnKey(target.fullName.value, callArgTypes, callResultType, isMember = true, callPos)
            resolverRuntime.registerSysFn(name, fn)
            RR_FunctionCallTarget.SysMember(name)
        }

        // R_FunctionCallTarget is an open hierarchy today, but every concrete case above
        // covers the known subclasses. New subclasses must update this resolver.
        else -> error("Unsupported R_FunctionCallTarget in resolver: ${target.javaClass.name}")
    }

    private fun resolveMemberCalculator(calc: R_MemberCalculator): RR_MemberCalculator = when (calc) {
        is R_MemberCalculator_StructAttr -> RR_MemberCalculator.StructAttr(
            resolveType(calc.type),
            calc.attr.index
        )

        is R_MemberCalculator_TupleAttr -> RR_MemberCalculator.TupleAttr(resolveType(calc.type), calc.attrIndex)
        is R_MemberCalculator_VirtualTupleAttr -> RR_MemberCalculator.VirtualTupleAttr(
            resolveType(calc.type),
            calc.fieldIndex
        )

        is R_MemberCalculator_VirtualStructAttr -> RR_MemberCalculator.VirtualStructAttr(
            resolveType(calc.type),
            calc.attr.index,
            calc.attr.name
        )

        is R_MemberCalculator_DataAttribute -> {
            val defaultEntity = calc.atBase.from.from[0].atEntity.rEntity
            val (attrName, attrEntity) = calc.atBase.what.firstNotNullOfOrNull { field ->
                extractAttrInfoFromWhat(field.value)
            } ?: (null to null)
            val entity = attrEntity ?: defaultEntity
            val entityIdx = resolveEntityIndex(entity)
            if (attrName != null && attrEntity == null) {
                // Simple single-level attribute access — use DataAttribute (efficient single SQL query)
                RR_MemberCalculator.DataAttribute(resolveType(calc.type), entityIdx, attrName)
            } else {
                // Complex what-expression — resolve the underlying at-expression into a DbAt expression
                // that the interpreter can evaluate natively.
                // Note: from.block and internals.block are null because the DataAttributeExpr handler
                // enters the lambda block separately (which provides the correct context).
                val from = RR_DbAtFrom(calc.atBase.from.from.mapToImmList { resolveDbAtFromItem(it) })
                val (flatWhat, whatFieldGroups) = resolveAtWhatFieldsWithGroups(calc.atBase.what)
                val where = resolveDbAtWhere(calc.atBase)
                val internals = RR_DbAtInternals(null)
                val dbAtExpr = RR_Expr.DbAt(
                    resolveType(calc.type), from, flatWhat, where,
                    AtCardinality.ONE, null, internals, ErrorPos("", 0),
                    whatFieldGroups = whatFieldGroups,
                )
                RR_MemberCalculator.DataAttributeExpr(
                    resolveType(calc.type), dbAtExpr,
                    calc.lambda.block.toRR(), calc.lambda.varPtr.toRR(),
                )
            }
        }

        is V_MemberFunctionCall_CommonCall.R_MemberCalculator_CommonCall ->
            RR_MemberCalculator.FunctionCall(resolveType(calc.type), resolveFunctionCall(calc.call))

        is R_MemberCalculator_ObjectAttr ->
            RR_MemberCalculator.ExprEval(resolveType(calc.type), resolveExpr(calc.expr))

        is C_MemberAttr_SysProperty.R_MemberCalculator_SysProperty -> {
            val cBody = calc.cBody
            val rFn = cBody.rFn
            // Content-derived key for a sys property: the property's qualified name plus its
            // result type. Using `propertyName` (e.g. "rell.module.name") disambiguates
            // properties that share the same calculator class — without it, all
            // R_MemberCalculator_SysProperty instances would collide under one key.
            val name = "prop:${calc.propertyName}#${calc.type.strCode()}"
            resolverRuntime.registerSysFn(name, rFn)
            RR_MemberCalculator.SysFunction(resolveType(calc.type), name)
        }

        is R_MemberCalculator_Error -> {
            val type = resolveType(calc.type)
            RR_MemberCalculator.ExprEval(type, RR_Expr.Error(type, calc.msg))
        }

        // R_MemberCalculator subclasses are enumerated above; fail loud on unknown.
        else -> error("Unsupported R_MemberCalculator in resolver: ${calc.javaClass.name}")
    }

    private fun resolveWhenChooser(chooser: R_WhenChooser): RR_WhenChooser = when (chooser) {
        is R_IterativeWhenChooser -> RR_WhenChooser.Iterative(
            resolveExpr(chooser.keyExpr),
            chooser.exprs.mapToImmList { RR_WhenCondition(it.index, resolveExpr(it.value)) },
            chooser.elseIdx ?: -1,
        )

        is R_LookupWhenChooser -> RR_WhenChooser.Lookup(
            resolveExpr(chooser.keyExpr),
            chooser.map.keys.toImmList(),
            chooser.map.values.toImmList(),
            chooser.elseIdx ?: -1,
        )
    }

    private fun resolveVarDeclarator(decl: R_VarDeclarator): RR_VarDeclarator = when (decl) {
        is R_SimpleVarDeclarator -> RR_VarDeclarator.Simple(
            decl.ptr.toRR(),
            resolveType(decl.type),
            resolveTypeAdapter(decl.adapter)
        )

        is R_TupleVarDeclarator -> RR_VarDeclarator.Tuple(decl.subDeclarators.mapToImmList { resolveVarDeclarator(it) })
        is R_WildcardVarDeclarator -> RR_VarDeclarator.Wildcard
    }

    private fun resolveTypeAdapter(adapter: R_TypeAdapter): RR_TypeAdapter = when (adapter) {
        is R_TypeAdapter_Direct -> RR_TypeAdapter.Direct
        is R_TypeAdapter_IntegerToBigInteger -> RR_TypeAdapter.IntegerToBigInteger
        is R_TypeAdapter_IntegerToDecimal -> RR_TypeAdapter.IntegerToDecimal
        is R_TypeAdapter_BigIntegerToDecimal -> RR_TypeAdapter.BigIntegerToDecimal
        is R_TypeAdapter_Nullable -> RR_TypeAdapter.Nullable(resolveTypeAdapter(adapter.innerAdapter))
    }

    private fun resolveIterableAdapter(adapter: R_IterableAdapter): RR_IterableAdapterKind = when (adapter) {
        is R_IterableAdapter_Direct -> RR_IterableAdapterKind.DIRECT
        is R_IterableAdapter_LegacyMap -> RR_IterableAdapterKind.LEGACY_MAP
    }

    private fun resolveCreateAttr(attr: R_CreateExprAttr): RR_CreateAttr =
        RR_CreateAttr(attr.attr.index, attr.attr.name, resolveExpr(attr.expr))

    // --- At-expression helpers ---

    private fun resolveDbAtExpr(type: RR_Type, expr: R_DbAtExpr): RR_Expr.DbAt {
        val base = expr.base
        val from = RR_DbAtFrom(base.from.from.mapToImmList { resolveDbAtFromItem(it) }, base.from.block?.toRR())
        val (what, whatFieldGroups) = resolveAtWhatFieldsWithGroups(base.what)
        val where = resolveDbAtWhere(base)
        val cardinality = expr.cardinality
        val extras = expr.extras.let {
            RR_AtExtras(
                it.limit?.let { l -> resolveExpr(l) },
                it.offset?.let { o -> resolveExpr(o) })
        }
        val internals = RR_DbAtInternals(expr.internals.block.toRR())
        return RR_Expr.DbAt(type, from, what, where, cardinality, extras, internals, expr.errPos, whatFieldGroups)
    }

    /**
     * Resolves what-fields and produces grouping metadata describing how flat DB columns
     * map back to logical what-values.
     *
     * Returns a pair of (flatFields, groups). Groups is null when every logical what-value
     * maps to exactly one DB column (the common case).
     */
    private fun resolveAtWhatFieldsWithGroups(
        whatFields: ImmList<Db_AtWhatField>,
    ): Pair<ImmList<RR_DbAtWhatField>, ImmList<RR_DbAtWhatFieldGroup>?> {
        val flatFields = mutableListOf<RR_DbAtWhatField>()
        val groups = mutableListOf<RR_DbAtWhatFieldGroup>()
        var needsGrouping = false

        for (field in whatFields) {
            if (field.flags.omit) {
                // Omit fields still get resolved (for sort/group), but don't contribute to groups.
                val resolved = resolveAtWhatField(field)
                flatFields.addAll(resolved)
                continue
            }
            val group = resolveAtWhatValueGroup(field.value, field)
            flatFields.addAll(group.first)
            groups.add(group.second)
            if (group.second.columnCount != 1 || group.second.combiner !is RR_DbAtFieldCombiner.Single) {
                needsGrouping = true
            }
        }

        return flatFields.toImmList() to (if (needsGrouping) groups.toImmList() else null)
    }

    /**
     * Resolves a single Db_AtWhatValue into flat RR_DbAtWhatField entries and a group descriptor.
     */
    private fun resolveAtWhatValueGroup(
        value: Db_AtWhatValue,
        field: Db_AtWhatField,
    ): Pair<ImmList<RR_DbAtWhatField>, RR_DbAtWhatFieldGroup> {
        val fields = resolveAtWhatField(field)
        return when (value) {
            is Db_AtWhatValue_DbExpr -> {
                fields to RR_DbAtWhatFieldGroup(1, RR_DbAtFieldCombiner.Single)
            }

            is Db_AtWhatValue_RExpr -> {
                // Check if the expression result type can be represented as a SQL column parameter.
                // Types like tuples, structs, lists cannot be SQL parameters, so they must be
                // evaluated at interpretation time (after the SQL query).
                val canBeSql = canTypeBeSqlParam(value.expr.type)
                if (canBeSql) {
                    // SQL-compatible: the expression is evaluated during SQL generation
                    // as an Interpreted expression (entity frame variables are bound).
                    fields to RR_DbAtWhatFieldGroup(1, RR_DbAtFieldCombiner.Single)
                } else {
                    // Non-SQL-compatible: produce 0 DB columns, evaluate at interpretation time.
                    val rrExpr = resolveExpr(value.expr)
                    immListOf<RR_DbAtWhatField>() to RR_DbAtWhatFieldGroup(
                        0, RR_DbAtFieldCombiner.Single,
                        rExprs = immListOf(rrExpr),
                        itemOrder = immListOf(false to 0),
                    )
                }
            }

            is Db_AtWhatValue_ToStruct -> {
                val structIndex = ctx.resolveStructIndexByRStruct(value.rStruct) ?: 0
                fields to RR_DbAtWhatFieldGroup(value.exprs.size, RR_DbAtFieldCombiner.Struct(structIndex))
            }

            is Db_AtWhatValue_Complex -> {
                // Complex values produce DB columns from sub-values only.
                // R-level expressions are stored in the group for interpretation-time evaluation.
                val combiner = resolveComplexCombiner(value)
                val dbColumnCount = fields.count { !it.flags.omit }
                val hasRExprs = value.rExprs.isNotEmpty()
                val rExprs = if (hasRExprs) value.rExprs.mapToImmList { resolveExpr(it) } else null
                val itemOrder = if (hasRExprs) value.items else null

                // Recursively resolve sub-groups for each sub-value.
                // This allows nested Complex values (e.g. tuple of function calls) to reduce
                // their DB columns before the outer combiner processes them.
                val subGroups = value.subWhatValues.mapToImmList { subValue ->
                    resolveSubValueGroup(subValue)
                }

                fields to RR_DbAtWhatFieldGroup(dbColumnCount, combiner, rExprs, itemOrder, subGroups)
            }
        }
    }

    /**
     * Resolves a [Db_AtWhatValue] into just a [RR_DbAtWhatFieldGroup] descriptor, without producing
     * flat what-fields. Used for resolving sub-groups of Complex values.
     */
    private fun resolveSubValueGroup(value: Db_AtWhatValue): RR_DbAtWhatFieldGroup {
        val dummyField = Db_AtWhatField(R_AtWhatFieldFlags.DEFAULT, value)
        return resolveAtWhatValueGroup(value, dummyField).second
    }

    /**
     * Returns true if the given R_Type can be represented as a SQL parameter.
     * Simple scalar types (int, text, bool, etc.) and entity types can; composite types (tuple, struct, list, etc.) cannot.
     */
    private fun canTypeBeSqlParam(type: R_Type): Boolean = when (type) {
        is R_BooleanType, is R_IntegerType, is R_BigIntegerType, is R_DecimalType,
        is R_TextType, is R_ByteArrayType, is R_RowidType, is R_JsonType,
        is R_GtvType, is R_GUIDType, is R_SignerType, is R_EntityType,
        is R_EnumType, is R_NullType, is R_UnitType -> true

        is R_NullableType -> canTypeBeSqlParam(type.valueType)
        else -> false
    }

    /**
     * Determines the combiner kind for a [Db_AtWhatValue_Complex] by querying the evaluator's
     * [Db_ComplexAtWhatEvaluator.combinerInfo] metadata.
     */
    private fun resolveComplexCombiner(complex: Db_AtWhatValue_Complex): RR_DbAtFieldCombiner = when (val info = complex.evaluator.combinerInfo()) {
        null -> error("Evaluator ${complex.evaluator.javaClass.name} must override combinerInfo()")
        is Db_AtWhatCombinerInfo.Tuple -> RR_DbAtFieldCombiner.Tuple(ctx.resolveType(info.type))
        is Db_AtWhatCombinerInfo.Struct -> {
            val structIdx = ctx.resolveStructIndexByRStruct(info.struct) ?: 0
            RR_DbAtFieldCombiner.Struct(structIdx, info.attrMapping)
        }
        is Db_AtWhatCombinerInfo.ListLiteral -> RR_DbAtFieldCombiner.ListLiteral(resolveType(info.type))
        is Db_AtWhatCombinerInfo.MapLiteral -> RR_DbAtFieldCombiner.MapLiteral(resolveType(info.type))
        is Db_AtWhatCombinerInfo.FunctionCall -> {
            val rrTarget = resolveCombinerCallTarget(info.target, info.returnType)
            val rrCall = RR_FunctionCall.Full(
                returnType = resolveType(info.returnType),
                target = rrTarget,
                args = immListOf(),
                callPos = info.callFilePos,
                mapping = info.paramsToExprs,
            )
            RR_DbAtFieldCombiner.FunctionCall(rrCall)
        }
        is Db_AtWhatCombinerInfo.PartialFunctionCall -> {
            val rrTarget = resolveCombinerCallTarget(info.target, info.returnType)
            val rrCall = RR_FunctionCall.Partial(
                returnType = resolveType(info.returnType), target = rrTarget,
                args = immListOf(),
                wildArgCount = info.mapping.wildCount,
                mappingValues = info.mapping.args.mapToImmList { if (it.wild) -(it.index + 1) else it.index },

            )
            RR_DbAtFieldCombiner.FunctionCall(rrCall)
        }
        is Db_AtWhatCombinerInfo.MemberAccess -> {
            val rrCalc = resolveMemberCalculator(info.calculator)
            RR_DbAtFieldCombiner.MemberAccess(rrCalc, info.safe)
        }
        is Db_AtWhatCombinerInfo.Lazy -> RR_DbAtFieldCombiner.Lazy(resolveType(info.type))
    }

    /**
     * Resolves a single [Db_AtFromItem] into an [RR_DbAtEntity], preserving the join WHERE condition
     * and outer flag from join at-expressions.
     */
    private fun resolveDbAtFromItem(item: Db_AtFromItem): RR_DbAtEntity {
        val joinWhere = item.where?.let { resolveDbExpr(it) }
        return RR_DbAtEntity(
            ctx.resolveEntityIndex(item.atEntity.rEntity),
            item.atEntity.id.id.toInt(),
            joinWhere,
            item.isOuter,
            item.rBlock?.toRR()
        )
    }

    /**
     * Resolves the full WHERE clause for a DbAt expression, including:
     * - The user-specified WHERE clause
     * - Extra WHERE conditions from entity SQL mappings (e.g., block_height checks for external entities)
     */
    private fun resolveDbAtWhere(base: Db_AtExprBase): RR_DbExpr? {
        val userWhere = base.where?.let { resolveDbExpr(it) }

        // Collect extra WHERE expressions from entity SQL mappings.
        val extraWheres = base.from.from.mapNotNull { item ->
            val atEntity = item.atEntity
            val extraExpr = atEntity.rEntity.sqlMapping.extraWhereExpr(atEntity)
            extraExpr?.let { resolveDbExpr(it) }
        }

        val allWheres = listOfNotNull(userWhere) + extraWheres
        return when (allWheres.size) {
            0 -> null
            1 -> allWheres[0]
            else -> allWheres.reduce { acc, expr ->
                RR_DbExpr.Binary(RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN), "and", acc, expr)
            }
        }
    }

    private fun resolveAtWhatField(field: Db_AtWhatField): ImmList<RR_DbAtWhatField> {
        val flags = RR_AtWhatFieldFlags(
            field.flags.omit,
            field.flags.sort?.let { if (it.asc) 1 else -1 } ?: 0,
            field.flags.group,
            field.flags.aggregate,
        )
        return when (val value = field.value) {
            is Db_AtWhatValue_DbExpr -> immListOf(
                RR_DbAtWhatField(
                    flags,
                    resolveDbExpr(value.expr),
                    resolveType(value.resultType)
                )
            )

            is Db_AtWhatValue_RExpr -> {
                val canBeSql = canTypeBeSqlParam(value.expr.type)
                if (canBeSql) {
                    immListOf(
                        RR_DbAtWhatField(
                            flags,
                            RR_DbExpr.Interpreted(resolveExpr(value.expr)),
                            resolveType(value.expr.type)
                        )
                    )
                } else {
                    immListOf()  // Non-SQL types produce 0 DB columns; evaluated by the field group
                }
            }

            is Db_AtWhatValue_ToStruct -> value.exprs.mapToImmList { dbExpr ->
                RR_DbAtWhatField(flags, resolveDbExpr(dbExpr), resolveType(dbExpr.type))
            }

            is Db_AtWhatValue_Complex -> {
                // Only resolve DB sub-values as SQL columns. R-level expressions are stored
                // in the field group and evaluated at interpretation time.
                value.subWhatValues.flatMapToImmList { subValue ->
                    resolveAtWhatField(Db_AtWhatField(field.flags, subValue))
                }
            }
        }
    }

    private fun resolveColAtExpr(type: RR_Type, expr: R_ColAtExpr): RR_Expr.ColAt {
        val param = RR_ColAtParam(resolveType(expr.param.type), expr.param.ptr.toRR())
        val from = RR_ColAtFrom(
            resolveExpr(expr.from.expr),
            expr.from.block?.toRR(),
            resolveIterableAdapter(expr.from.iterableAdapter)
        )
        val what = RR_ColAtWhat(
            expr.what.fields.mapToImmList {
                RR_ColAtWhatField(
                    resolveExpr(it.expr),
                    RR_AtWhatFieldFlags(
                        it.flags.omit,
                        it.flags.sort?.let { s -> if (s.asc) 1 else -1 } ?: 0,
                        it.flags.group))
            },
            expr.what.extras.fieldCount,
            expr.what.extras.selectedFields,
            if (expr.what.extras.groupFields.isEmpty()) null else expr.what.extras.groupFields,
        )
        val where = resolveExpr(expr.where)
        val summarization = when (expr.summarization) {
            is R_ColAtSummarization_None -> RR_ColAtSummarizationKind.NONE
            else -> if (expr.what.extras.groupFields.isNotEmpty()) RR_ColAtSummarizationKind.GROUP else RR_ColAtSummarizationKind.ALL
        }
        val cardinality = expr.cardinality
        val extras =
            RR_AtExtras(expr.extras.limit?.let { resolveExpr(it) }, expr.extras.offset?.let { resolveExpr(it) })

        // Build serializable per-field summarization info
        val fieldSummarizations = expr.what.fields.mapToImmList { field ->
            resolveFieldSummarization(field.summarization)
        }

        // Build serializable sort entries from what-field sort flags
        val sorting = expr.what.extras.sorting.mapToImmList { entry ->
            RR_ColAtSortEntry(entry.fieldIndex, entry.ascending)
        }

        return RR_Expr.ColAt(
            type,
            expr.block.toRR(),
            param,
            from,
            what,
            where,
            summarization,
            expr.errPos,
            cardinality,
            extras,
            fieldSummarizations,
            sorting
        )
    }

    private fun resolveFieldSummarization(summarization: R_ColAtFieldSummarization): RR_ColAtFieldSummarizationInfo =
        when (summarization) {
            is R_ColAtFieldSummarization_None -> RR_ColAtFieldSummarizationInfo(RR_ColAtFieldSummarizationKind.NONE)
            is R_ColAtFieldSummarization_Group -> RR_ColAtFieldSummarizationInfo(RR_ColAtFieldSummarizationKind.GROUP)
            is R_ColAtFieldSummarization_Aggregate -> resolveAggregateSummarization(summarization)
        }

    private fun resolveAggregateSummarization(agg: R_ColAtFieldSummarization_Aggregate): RR_ColAtFieldSummarizationInfo =
        when (agg) {
            is R_ColAtFieldSummarization_Aggregate_Sum -> {
                val opKey = binaryOpKey(agg.op)
                RR_ColAtFieldSummarizationInfo(
                    RR_ColAtFieldSummarizationKind.SUM,
                    binaryOpKey = opKey,
                    zeroValue = agg.zeroValue,
                )
            }

            is R_ColAtFieldSummarization_Aggregate_MinMax -> {
                val rCmpOp = agg.rCmpOp
                val isMin = rCmpOp is R_CmpOp_Lt || rCmpOp is R_CmpOp_Le
                RR_ColAtFieldSummarizationInfo(
                    if (isMin) RR_ColAtFieldSummarizationKind.MIN else RR_ColAtFieldSummarizationKind.MAX,
                    isMin = isMin
                )
            }

            is R_ColAtFieldSummarization_Aggregate_List -> {
                val listType = agg.listType
                RR_ColAtFieldSummarizationInfo(
                    RR_ColAtFieldSummarizationKind.LIST,
                    collectionType = resolveType(listType)
                )
            }

            is R_ColAtFieldSummarization_Aggregate_Set -> {
                val setType = agg.setType
                RR_ColAtFieldSummarizationInfo(
                    RR_ColAtFieldSummarizationKind.SET,
                    collectionType = resolveType(setType)
                )
            }

            is R_ColAtFieldSummarization_Aggregate_Map -> {
                val mapType = agg.mapType
                RR_ColAtFieldSummarizationInfo(
                    RR_ColAtFieldSummarizationKind.MAP,
                    collectionType = resolveType(mapType)
                )
            }
        }

    // --- Update/Delete helpers ---

    private fun resolveUpdateTargetKind(target: R_UpdateTarget): RR_UpdateTargetKind = when (target) {
        is R_UpdateTarget_Simple -> RR_UpdateTargetKind.SIMPLE
        is R_UpdateTarget_Expr_One -> RR_UpdateTargetKind.EXPR_ONE
        is R_UpdateTarget_Expr_Many -> RR_UpdateTargetKind.EXPR_MANY
        is R_UpdateTarget_Object -> RR_UpdateTargetKind.OBJECT
    }

    private fun resolveUpdateTargetCardinality(target: R_UpdateTarget): AtCardinality? = when (target) {
        is R_UpdateTarget_Simple -> target.cardinality
        is R_UpdateTarget_Object -> AtCardinality.ONE
        else -> null
    }

    private fun resolveUpdateStmt(stmt: R_UpdateStatement): RR_Statement.Update {
        val targetEntity = stmt.target.entity()
        val entity = RR_DbAtEntity(resolveEntityIndex(targetEntity.rEntity), targetEntity.id.id.toInt())
        val extrasList = stmt.target.extraEntities()
        val extras = if (extrasList.isEmpty()) null else extrasList.mapToImmList {
            RR_DbAtEntity(
                resolveEntityIndex(it.rEntity),
                it.id.id.toInt()
            )
        }
        val where = stmt.target.where()?.let { resolveDbExpr(it) }
        val what = stmt.what.mapToImmList { RR_UpdateWhat(it.attr.name, it.attr.index, resolveDbExpr(it.expr)) }
        val (lambdaBlock, lambdaVarPtr, lambdaExpr) = extractTargetLambda(stmt.target)
        val targetKind = resolveUpdateTargetKind(stmt.target)
        val cardinality = resolveUpdateTargetCardinality(stmt.target)
        val (isExprSet, exprListType) = extractExprManyInfo(stmt.target)
        return RR_Statement.Update(
            entity,
            extras,
            where,
            what,
            stmt.fromBlock.toRR(),
            stmt.errPos,
            lambdaBlock,
            lambdaVarPtr,
            lambdaExpr,
            targetKind,
            cardinality,
            isExprSet,
            exprListType
        )
    }

    private fun resolveDeleteStmt(stmt: R_DeleteStatement): RR_Statement.Delete {
        val targetEntity = stmt.target.entity()
        val entity = RR_DbAtEntity(resolveEntityIndex(targetEntity.rEntity), targetEntity.id.id.toInt())
        val extrasList = stmt.target.extraEntities()

        val extras = if (extrasList.isEmpty()) null else extrasList.mapToImmList {
            RR_DbAtEntity(
                resolveEntityIndex(it.rEntity),
                it.id.id.toInt()
            )
        }

        val where = stmt.target.where()?.let { resolveDbExpr(it) }
        val (lambdaBlock, lambdaVarPtr, lambdaExpr) = extractTargetLambda(stmt.target)
        val targetKind = resolveUpdateTargetKind(stmt.target)
        val cardinality = resolveUpdateTargetCardinality(stmt.target)
        val (isExprSet, exprListType) = extractExprManyInfo(stmt.target)

        return RR_Statement.Delete(
            entity,
            extras,
            where,
            stmt.fromBlock.toRR(),
            stmt.errPos,
            lambdaBlock,
            lambdaVarPtr,
            lambdaExpr,
            targetKind,
            cardinality,
            isExprSet,
            exprListType,
        )
    }

    private fun extractExprManyInfo(target: R_UpdateTarget): Pair<Boolean, RR_Type?> {
        if (target is R_UpdateTarget_Expr_Many) {
            return Pair(target.set, resolveType(target.listType))
        }
        return Pair(false, null)
    }

    private fun extractTargetLambda(target: R_UpdateTarget): Triple<RR_FrameBlock?, RR_VarPtr?, RR_Expr?> {
        if (target is R_UpdateTarget_Expr) {
            val lambda = target.lambda
            return Triple(lambda.block.toRR(), lambda.varPtr.toRR(), resolveExpr(target.expr))
        }
        return Triple(null, null, null)
    }

    /** Extract attribute name and owning entity from a what-value for DataAttribute resolution. */
    private fun extractAttrInfoFromWhat(whatValue: Db_AtWhatValue): Pair<String, R_EntityDefinition?>? =
        if (whatValue is Db_AtWhatValue_DbExpr) {
            extractAttrInfoFromDbExpr(whatValue.expr)
        } else {
            null
        }

    /** Returns (attrName, owningEntity) from a Db_Expr. owningEntity is non-null for path expressions. */
    private fun extractAttrInfoFromDbExpr(expr: Db_Expr): Pair<String, R_EntityDefinition?>? = when (expr) {
        is Db_AttrExpr -> {
            if (expr.base is Db_RelExpr) {
                // Path expression: entity.rel.attr — the attr belongs to the rel's target entity
                val relExpr = expr.base
                Pair(expr.attr.name, relExpr.rEntity)
            } else {
                Pair(expr.attr.name, null) // Simple entity.attr — uses default FROM entity
            }
        }

        is Db_RelExpr -> {
            // For multi-step entity paths (e.g., e5.e4.e3), when base is a Db_RelExpr,
            // the owning entity is base.rEntity (the intermediate entity in the path).
            // For a single-step path (base is Db_EntityExpr), owning entity matches
            // the default FROM entity, so return null to let the caller use the default.
            val ownerEntity = if (expr.base is Db_RelExpr) expr.base.rEntity else null
            Pair(expr.attr.name, ownerEntity)
        }

        is Db_RowidExpr -> {
            // For multi-step paths ending in .rowid (e.g., company.boss.rowid),
            // the base is a Db_RelExpr and the rowid belongs to the target entity of that relation.
            val ownerEntity = if (expr.base is Db_RelExpr) expr.base.rEntity else null
            Pair("rowid", ownerEntity)
        }

        else -> null
    }

    private fun resolveExtendableReturnType(
        combiner: R_ExtendableFunctionCombiner,
        baseFn: R_FunctionDefinition
    ): RR_Type = when (combiner) {
        is R_ExtendableFunctionCombiner_List -> resolveType(combiner.type)
        is R_ExtendableFunctionCombiner_Map -> resolveType(combiner.mapType)

        else -> {
            // For unit/boolean/nullable, the return type is from the function header
            val header = baseFn.fnBase.getHeader()
            resolveType(header.type)
        }
    }

    private fun resolveExtendableCombinerKind(combiner: R_ExtendableFunctionCombiner): RR_ExtendableCombinerKind =
        when (combiner) {
            is R_ExtendableFunctionCombiner_Unit -> RR_ExtendableCombinerKind.UNIT
            is R_ExtendableFunctionCombiner_Boolean -> RR_ExtendableCombinerKind.BOOLEAN
            is R_ExtendableFunctionCombiner_Nullable -> RR_ExtendableCombinerKind.NULLABLE
            is R_ExtendableFunctionCombiner_List -> RR_ExtendableCombinerKind.LIST
            is R_ExtendableFunctionCombiner_Map -> RR_ExtendableCombinerKind.MAP
        }

    /** Extract the SQL operator string from a Db_BinaryOp at resolution time. */
    private fun extractDbBinaryOpSql(op: Db_BinaryOp): String = op.sql

    /** Convert a Db_SysFunction to its serializable RR_ representation. */
    private fun resolveDbSysFn(fn: Db_SysFunction): RR_DbSysFn = when (fn) {
        is Db_SysFn_Simple -> RR_DbSysFn.Simple(fn.sql)
        is Db_SysFn_Template -> RR_DbSysFn.Template(
            fn.fragments.mapToImmList { (text, argIdx) ->
                if (text != null) RR_DbSysFnFragment.Text(text) else RR_DbSysFnFragment.Arg(argIdx!!)
            }
        )
    }

    companion object {
        /** Unique content-addressed key for a binary operator — sourced from [R_BinaryOp.kindName]. */
        fun binaryOpKey(op: R_BinaryOp): String = op.kindName

        /** Extract cmpInfo for comparison operators; null for all other ops. */
        fun resolveCmpInfo(op: R_BinaryOp): RR_CmpBinaryOp? = when (op) {
            is R_BinaryOp_Cmp -> RR_CmpBinaryOp(op.cmpOp.kindName, op.cmpType.kindName)
            else -> null
        }
    }
}
