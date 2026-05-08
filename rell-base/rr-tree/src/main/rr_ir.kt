/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf

sealed interface RR_Expr {
    val type: RR_Type

    @JvmRecord
    data class Var(override val type: RR_Type, val ptr: RR_VarPtr, val name: String): RR_Expr
    @JvmRecord
    data class ConstantValue(override val type: RR_Type, val value: RR_ConstantValue): RR_Expr

    @JvmRecord
    data class Binary(
        override val type: RR_Type,
        val op: RR_BinaryOp,
        val cmpInfo: RR_CmpBinaryOp?,
        val left: RR_Expr,
        val right: RR_Expr,
        val errPos: ErrorPos?,
    ): RR_Expr

    @JvmRecord
    data class Unary(override val type: RR_Type, val op: RR_UnaryOp, val expr: RR_Expr, val errPos: ErrorPos):
        RR_Expr

    @JvmRecord
    data class If(override val type: RR_Type, val cond: RR_Expr, val trueExpr: RR_Expr, val falseExpr: RR_Expr):
        RR_Expr

    @JvmRecord
    data class When(override val type: RR_Type, val chooser: RR_WhenChooser, val exprs: ImmList<RR_Expr>): RR_Expr
    @JvmRecord
    data class Elvis(override val type: RR_Type, val left: RR_Expr, val right: RR_Expr): RR_Expr
    @JvmRecord
    data class NotNull(override val type: RR_Type, val expr: RR_Expr, val errPos: ErrorPos): RR_Expr
    @JvmRecord
    data class TupleLiteral(override val type: RR_Type, val exprs: ImmList<RR_Expr>): RR_Expr
    @JvmRecord
    data class ListLiteral(override val type: RR_Type, val exprs: ImmList<RR_Expr>): RR_Expr

    @JvmRecord
    data class MapLiteral(
        override val type: RR_Type,
        val keys: ImmList<RR_Expr>,
        val values: ImmList<RR_Expr>,
        val errPos: ErrorPos,
    ): RR_Expr

    @JvmRecord
    data class StructCreate(override val type: RR_Type, val structDefIndex: Int, val attrs: ImmList<RR_CreateAttr>):
        RR_Expr

    @JvmRecord
    data class RegularCreate(
        override val type: RR_Type,
        val entityDefIndex: Int,
        val errPos: ErrorPos,
        val attrs: ImmList<RR_CreateAttr>,
    ): RR_Expr

    @JvmRecord
    data class StructEntityCreate(
        override val type: RR_Type,
        val entityDefIndex: Int,
        val errPos: ErrorPos,
        val structDefIndex: Int,
        val structExpr: RR_Expr,
    ): RR_Expr

    @JvmRecord
    data class StructListCreate(
        override val type: RR_Type,
        val entityDefIndex: Int,
        val errPos: ErrorPos,
        val structDefIndex: Int,
        val resultListType: RR_Type,
        val listExpr: RR_Expr,
    ): RR_Expr

    @JvmRecord
    data class FunctionCall(
        override val type: RR_Type,
        val base: RR_Expr?,
        val call: RR_FunctionCall,
        val safe: Boolean,
    ): RR_Expr

    @JvmRecord
    data class MemberAccess(
        override val type: RR_Type,
        val base: RR_Expr,
        val calculator: RR_MemberCalculator,
        val safe: Boolean,
    ): RR_Expr

    @JvmRecord
    data class Assign(
        override val type: RR_Type,
        val op: RR_BinaryOp?,
        val dstExpr: RR_Expr,
        val srcExpr: RR_Expr,
        val post: Boolean,
    ): RR_Expr

    @JvmRecord
    data class StatementExpr(override val type: RR_Type, val stmt: RR_Statement): RR_Expr
    @JvmRecord
    data class GlobalConstant(override val type: RR_Type, val constDefIndex: Int): RR_Expr
    @JvmRecord
    data class ChainHeight(override val type: RR_Type, val chainIndex: Int): RR_Expr
    @JvmRecord
    data class TypeAdapter(override val type: RR_Type, val expr: RR_Expr, val adapter: RR_TypeAdapter): RR_Expr

    @JvmRecord
    data class ParameterDefaultValue(
        override val type: RR_Type,
        val callFilePos: FilePos,
        val initFrame: RR_FrameDescriptor,
        val defId: DefinitionId,
        val innerExpr: RR_Expr,
    ): RR_Expr

    @JvmRecord
    data class AttributeDefaultValue(
        override val type: RR_Type,
        val attrIndex: Int,
        val attrName: String,
        val createFilePos: FilePos?,
        val initFrame: RR_FrameDescriptor,
        val defId: DefinitionId,
        val innerExpr: RR_Expr,
    ): RR_Expr

    @JvmRecord
    data class DbAt(
        override val type: RR_Type,
        val from: RR_DbAtFrom,
        val what: ImmList<RR_DbAtWhatField>,
        val where: RR_DbExpr?,
        val cardinality: AtCardinality,
        val extras: RR_AtExtras?,
        val internals: RR_DbAtInternals,
        val errPos: ErrorPos,
        val whatFieldGroups: ImmList<RR_DbAtWhatFieldGroup>? = null,
        val objectName: String? = null,
        val objectDefIndex: Int? = null,
    ): RR_Expr

    @JvmRecord
    data class ColAt(
        override val type: RR_Type,
        val block: RR_FrameBlock,
        val param: RR_ColAtParam,
        val from: RR_ColAtFrom,
        val what: RR_ColAtWhat,
        val where: RR_Expr,
        val summarization: RR_ColAtSummarizationKind,
        val errPos: ErrorPos,
        val cardinality: AtCardinality,
        val extras: RR_AtExtras?,
        val fieldSummarizations: ImmList<RR_ColAtFieldSummarizationInfo>,
        val sorting: ImmList<RR_ColAtSortEntry>,
    ): RR_Expr

    @JvmRecord
    data class Error(override val type: RR_Type, val message: String): RR_Expr

    @JvmRecord
    data class ListSubscript(override val type: RR_Type, val base: RR_Expr, val index: RR_Expr, val errPos: ErrorPos):
        RR_Expr

    @JvmRecord
    data class MapSubscript(override val type: RR_Type, val base: RR_Expr, val key: RR_Expr, val errPos: ErrorPos):
        RR_Expr

    @JvmRecord
    data class TextSubscript(override val type: RR_Type, val base: RR_Expr, val index: RR_Expr, val errPos: ErrorPos):
        RR_Expr

    @JvmRecord
    data class ByteArraySubscript(
        override val type: RR_Type,
        val base: RR_Expr,
        val index: RR_Expr,
        val errPos: ErrorPos,
    ): RR_Expr

    @JvmRecord
    data class VirtualListSubscript(override val type: RR_Type, val base: RR_Expr, val index: RR_Expr): RR_Expr

    @JvmRecord
    data class VirtualMapSubscript(
        override val type: RR_Type,
        val base: RR_Expr,
        val key: RR_Expr,
        val errPos: ErrorPos,
    ): RR_Expr

    @JvmRecord
    data class JsonArraySubscript(
        override val type: RR_Type,
        val base: RR_Expr,
        val index: RR_Expr,
        val errPos: ErrorPos,
    ): RR_Expr

    @JvmRecord
    data class JsonObjectSubscript(
        override val type: RR_Type,
        val base: RR_Expr,
        val key: RR_Expr,
        val errPos: ErrorPos,
    ): RR_Expr

    @JvmRecord
    data class StructMember(override val type: RR_Type, val base: RR_Expr, val attrName: String, val attrIndex: Int):
        RR_Expr

    @JvmRecord
    data class ObjectValue(override val type: RR_Type, val objectDefIndex: Int): RR_Expr
    @JvmRecord
    data class Lazy(override val type: RR_Type, val innerExpr: RR_Expr): RR_Expr
}

// =============================================================================
// Statement IR
// =============================================================================

sealed interface RR_Statement {
    data object Empty: RR_Statement
    @JvmRecord
    data class Var(val declarator: RR_VarDeclarator, val expr: RR_Expr?): RR_Statement
    @JvmRecord
    data class Return(val expr: RR_Expr?): RR_Statement
    @JvmRecord
    data class Block(val stmts: ImmList<RR_Statement>, val frameBlock: RR_FrameBlock): RR_Statement
    @JvmRecord
    data class Expr(val expr: RR_Expr): RR_Statement
    @JvmRecord
    data class ReplExpr(val expr: RR_Expr): RR_Statement

    @JvmRecord
    data class Assign(val dstExpr: RR_Expr, val expr: RR_Expr, val op: RR_BinaryOp?): RR_Statement

    @JvmRecord
    data class If(val cond: RR_Expr, val trueStmt: RR_Statement, val falseStmt: RR_Statement): RR_Statement
    @JvmRecord
    data class When(val chooser: RR_WhenChooser, val stmts: ImmList<RR_Statement>): RR_Statement
    @JvmRecord
    data class While(val cond: RR_Expr, val body: RR_Statement, val frameBlock: RR_FrameBlock): RR_Statement

    @JvmRecord
    data class For(
        val varDeclarator: RR_VarDeclarator,
        val expr: RR_Expr,
        val iterableAdapter: RR_IterableAdapterKind,
        val body: RR_Statement,
        val frameBlock: RR_FrameBlock
    ): RR_Statement

    data object Break: RR_Statement
    data object Continue: RR_Statement
    @JvmRecord
    data class Guard(val body: RR_Statement): RR_Statement

    @JvmRecord
    data class Lambda(
        val argExprs: ImmList<RR_Expr>,
        val argPtrs: ImmList<RR_VarPtr>,
        val block: RR_FrameBlock,
        val body: RR_Statement
    ): RR_Statement

    @JvmRecord
    data class Update(
        val entity: RR_DbAtEntity,
        val extraEntities: ImmList<RR_DbAtEntity>?,
        val where: RR_DbExpr?,
        val what: ImmList<RR_UpdateWhat>,
        val fromBlock: RR_FrameBlock,
        val errPos: ErrorPos,
        val lambdaBlock: RR_FrameBlock? = null,
        val lambdaVarPtr: RR_VarPtr? = null,
        val lambdaExpr: RR_Expr? = null,
        val targetKind: RR_UpdateTargetKind = RR_UpdateTargetKind.SIMPLE,
        val cardinality: AtCardinality? = null,
        val isExprSet: Boolean = false,
        val exprListType: RR_Type? = null
    ): RR_Statement

    @JvmRecord
    data class Delete(
        val entity: RR_DbAtEntity,
        val extraEntities: ImmList<RR_DbAtEntity>?,
        val where: RR_DbExpr?,
        val fromBlock: RR_FrameBlock,
        val errPos: ErrorPos,
        val lambdaBlock: RR_FrameBlock? = null,
        val lambdaVarPtr: RR_VarPtr? = null,
        val lambdaExpr: RR_Expr? = null,
        val targetKind: RR_UpdateTargetKind = RR_UpdateTargetKind.SIMPLE,
        val cardinality: AtCardinality? = null,
        val isExprSet: Boolean = false,
        val exprListType: RR_Type? = null
    ): RR_Statement
}

// =============================================================================
// Database expression IR
// =============================================================================

sealed interface RR_DbExpr {
    @JvmRecord
    data class Interpreted(val expr: RR_Expr): RR_DbExpr

    @JvmRecord
    data class Binary(
        val type: RR_Type,
        val op: RR_DbBinaryOp,
        val left: RR_DbExpr,
        val right: RR_DbExpr,
        val nullableEq: Boolean = false,
    ): RR_DbExpr

    @JvmRecord
    data class Unary(val type: RR_Type, val op: RR_DbUnaryOp, val expr: RR_DbExpr): RR_DbExpr
    @JvmRecord
    data class Entity(val entityDefIndex: Int, val entityId: Int): RR_DbExpr
    @JvmRecord
    data class Rel(val base: RR_DbExpr, val attrName: String, val targetEntityDefIndex: Int): RR_DbExpr
    @JvmRecord
    data class Attr(val base: RR_DbExpr, val attrName: String, val type: RR_Type): RR_DbExpr
    @JvmRecord
    data class Rowid(val base: RR_DbExpr): RR_DbExpr
    @JvmRecord
    data class CollectionInterpreted(val expr: RR_Expr): RR_DbExpr
    @JvmRecord
    data class In(val keyExpr: RR_DbExpr, val exprs: ImmList<RR_DbExpr>, val not: Boolean): RR_DbExpr
    @JvmRecord
    data class Elvis(val type: RR_Type, val left: RR_DbExpr, val right: RR_DbExpr): RR_DbExpr
    @JvmRecord
    data class Call(val type: RR_Type, val fn: RR_DbSysFn, val args: ImmList<RR_DbExpr>): RR_DbExpr
    @JvmRecord
    data class Exists(val subExpr: RR_DbExpr, val not: Boolean): RR_DbExpr
    @JvmRecord
    data class InCollection(val left: RR_DbExpr, val right: RR_Expr, val not: Boolean): RR_DbExpr

    @JvmRecord
    data class When(
        val type: RR_Type,
        val keyExpr: RR_DbExpr?,
        val cases: ImmList<RR_DbWhenCase>,
        val elseExpr: RR_DbExpr?,
    ): RR_DbExpr

    @JvmRecord
    data class NestedAt(val type: RR_Type, val inner: RR_DbExpr): RR_DbExpr

    /**
     * Inline subquery that generates SQL directly within the outer query context.
     * Used for EXISTS/IN subqueries where the inner query must be a correlated subquery
     * that can reference entity aliases from the outer query.
     */
    @JvmRecord
    data class SubQuery(
        val from: RR_DbAtFrom,
        val what: ImmList<RR_DbAtWhatField>,
        val where: RR_DbExpr?,
        val extras: RR_AtExtras?,
        val isMany: Boolean,
        val internals: RR_DbAtInternals,
    ): RR_DbExpr
}

// =============================================================================
// Supporting types
// =============================================================================

// --- Operators ---
typealias RR_BinaryOp = String
typealias RR_UnaryOp = String
typealias RR_DbBinaryOp = String
typealias RR_DbUnaryOp = String

@JvmRecord
data class RR_CmpBinaryOp(val cmpOp: String, val cmpType: String)

// --- DB system functions ---

sealed interface RR_DbSysFn {
    @JvmRecord
    data class Simple(val sql: String): RR_DbSysFn
    @JvmRecord
    data class Template(val fragments: ImmList<RR_DbSysFnFragment>): RR_DbSysFn
}

sealed interface RR_DbSysFnFragment {
    @JvmRecord
    data class Text(val text: String): RR_DbSysFnFragment
    @JvmRecord
    data class Arg(val index: Int): RR_DbSysFnFragment
}

/** A single WHEN case in a DB CASE expression: one or more conditions mapping to a single result expression. */
@JvmRecord
data class RR_DbWhenCase(val conds: ImmList<RR_DbExpr>, val expr: RR_DbExpr)

// --- Function call target ---

sealed interface RR_FunctionCallTarget {
    @JvmRecord
    data class RegularUser(val fnDefIndex: Int): RR_FunctionCallTarget
    @JvmRecord
    data class RegularQuery(val queryDefIndex: Int): RR_FunctionCallTarget
    @JvmRecord
    data class AbstractUser(val fnDefIndex: Int): RR_FunctionCallTarget

    /** Abstract function with an override body inlined as [RR_FunctionBase]. */
    @JvmRecord
    data class AbstractOverride(val fnBase: RR_FunctionBase): RR_FunctionCallTarget

    /** Extendable function — calls all extensions via UID, combines results. */
    @JvmRecord
    data class Extendable(
        val extendableUidId: Int,
        val combinerKind: RR_ExtendableCombinerKind,
        val returnType: RR_Type
    ): RR_FunctionCallTarget

    @JvmRecord
    data class NativeUser(val fullName: FullName): RR_FunctionCallTarget
    @JvmRecord
    data class Operation(val opDefIndex: Int): RR_FunctionCallTarget
    data object FunctionValue: RR_FunctionCallTarget
    @JvmRecord
    data class SysGlobal(val fnName: String): RR_FunctionCallTarget
    @JvmRecord
    data class SysMember(val fnName: String): RR_FunctionCallTarget
}

// --- Function call ---

sealed interface RR_FunctionCall {
    val returnType: RR_Type
    val target: RR_FunctionCallTarget
    val args: ImmList<RR_Expr>

    @JvmRecord
    data class Full(
        override val returnType: RR_Type,
        override val target: RR_FunctionCallTarget,
        override val args: ImmList<RR_Expr>,
        val callPos: FilePos,
        val mapping: ImmList<Int>,
    ): RR_FunctionCall

    @JvmRecord
    data class Partial(
        override val returnType: RR_Type,
        override val target: RR_FunctionCallTarget,
        override val args: ImmList<RR_Expr>,
        val wildArgCount: Int,
        val mappingValues: ImmList<Int>,
    ): RR_FunctionCall
}

// --- Member calculator ---

sealed interface RR_MemberCalculator {
    val type: RR_Type

    @JvmRecord
    data class StructAttr(override val type: RR_Type, val attrIndex: Int): RR_MemberCalculator
    @JvmRecord
    data class TupleAttr(override val type: RR_Type, val attrIndex: Int): RR_MemberCalculator
    @JvmRecord
    data class VirtualTupleAttr(override val type: RR_Type, val fieldIndex: Int): RR_MemberCalculator

    @JvmRecord
    data class VirtualStructAttr(override val type: RR_Type, val attrDefIndex: Int, val attrName: String):
        RR_MemberCalculator

    @JvmRecord
    data class DataAttribute(override val type: RR_Type, val entityDefIndex: Int, val attrName: String):
        RR_MemberCalculator

    /** Complex data attribute access (e.g., to_struct, multistep paths) — needs a lambda block to bind the base entity value. */
    @JvmRecord
    data class DataAttributeExpr(
        override val type: RR_Type,
        val expr: RR_Expr,
        val lambdaBlock: RR_FrameBlock,
        val lambdaVarPtr: RR_VarPtr,
    ): RR_MemberCalculator

    @JvmRecord
    data class SysFunction(override val type: RR_Type, val fnName: String): RR_MemberCalculator
    @JvmRecord
    data class FunctionCall(override val type: RR_Type, val call: RR_FunctionCall): RR_MemberCalculator

    /** Evaluates an expression independent of the base value. Used for `object` attribute access. */
    @JvmRecord
    data class ExprEval(override val type: RR_Type, val expr: RR_Expr): RR_MemberCalculator
}

// --- When chooser ---

sealed interface RR_WhenChooser {
    @JvmRecord
    data class Iterative(val keyExpr: RR_Expr, val conditions: ImmList<RR_WhenCondition>, val elseIndex: Int):
        RR_WhenChooser

    @JvmRecord
    data class Lookup(
        val keyExpr: RR_Expr,
        val keys: ImmList<RR_ConstantValue>,
        val values: ImmList<Int>,
        val elseIndex: Int,
    ): RR_WhenChooser
}

@JvmRecord
data class RR_WhenCondition(val index: Int, val expr: RR_Expr)

// --- Variable declarator ---

sealed interface RR_VarDeclarator {
    @JvmRecord
    data class Simple(val ptr: RR_VarPtr, val type: RR_Type, val adapter: RR_TypeAdapter?): RR_VarDeclarator
    @JvmRecord
    data class Tuple(val subDeclarators: ImmList<RR_VarDeclarator>): RR_VarDeclarator
    data object Wildcard: RR_VarDeclarator
}

// --- Type adapter ---

sealed interface RR_TypeAdapter {
    data object Direct: RR_TypeAdapter
    data object IntegerToBigInteger: RR_TypeAdapter
    data object IntegerToDecimal: RR_TypeAdapter
    data object BigIntegerToDecimal: RR_TypeAdapter
    @JvmRecord
    data class Nullable(val inner: RR_TypeAdapter): RR_TypeAdapter
}

// --- Constant value ---

sealed interface RR_ConstantValue {
    data object Null: RR_ConstantValue
    data object Unit: RR_ConstantValue

    @JvmRecord
    data class Bool(val value: Boolean): RR_ConstantValue

    @JvmRecord
    data class Int(val value: Long): RR_ConstantValue

    @JvmRecord
    data class Text(val value: String): RR_ConstantValue

    @JvmRecord
    data class ByteArray(val value: kotlin.ByteArray): RR_ConstantValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ByteArray
            return value.contentEquals(other.value)
        }

        override fun hashCode(): kotlin.Int = value.contentHashCode()
    }

    @JvmRecord
    data class Decimal(val value: String): RR_ConstantValue

    @JvmRecord
    data class BigInteger(val value: String): RR_ConstantValue

    @JvmRecord
    data class Rowid(val value: Long): RR_ConstantValue

    @JvmRecord
    data class Enum(
        val enumDefIndex: kotlin.Int,
        val enumValue: kotlin.Int,
        /** Display-only: enum type name (e.g., "E"). Not used for equality/hashing. */
        val enumTypeName: String = "",
        /** Display-only: enum attribute name (e.g., "A"). Not used for equality/hashing. */
        val enumAttrName: String = "",
    ): RR_ConstantValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Enum) return false
            return enumDefIndex == other.enumDefIndex && enumValue == other.enumValue
        }

        override fun hashCode(): kotlin.Int = enumDefIndex * 31 + enumValue
    }

    @JvmRecord
    data class Gtv(val json: String): RR_ConstantValue

    /** Struct constant: stores structDefIndex + ordered field values. */
    @JvmRecord
    data class Struct(val structDefIndex: kotlin.Int, val fieldValues: ImmList<RR_ConstantValue>): RR_ConstantValue

    /** Collection constant (list/set): stores element type + element values. */
    @JvmRecord
    data class Collection(val elementType: RR_Type, val elementValues: ImmList<RR_ConstantValue>): RR_ConstantValue

    /** Map constant: stores key/value types + entries. */
    @JvmRecord
    data class MapConstant(
        val keyType: RR_Type,
        val valueType: RR_Type,
        val keys: ImmList<RR_ConstantValue>,
        val values: ImmList<RR_ConstantValue>
    ): RR_ConstantValue

    /** Tuple constant: stores field values. */
    @JvmRecord
    data class TupleConstant(val tupleType: RR_Type, val fieldValues: ImmList<RR_ConstantValue>): RR_ConstantValue

    /** Meta constant: stores rell.meta definition metadata. */
    @JvmRecord
    data class Meta(
        val kind: String,
        val moduleName: String,
        val fullName: String,
        val simpleName: String,
        val mountName: String
    ): RR_ConstantValue
}

// --- Create expression attribute ---

@JvmRecord
data class RR_CreateAttr(val attrIndex: Int, val attrName: String, val expr: RR_Expr)

// --- At-expression support ---


@JvmRecord
data class RR_AtExtras(val limit: RR_Expr?, val offset: RR_Expr?)

@JvmRecord
data class RR_DbAtEntity(
    val entityDefIndex: Int,
    val entityId: Int,
    val joinWhere: RR_DbExpr? = null,
    val isOuter: Boolean = false,
    val joinBlock: RR_FrameBlock? = null
)

@JvmRecord
data class RR_DbAtFrom(val entities: ImmList<RR_DbAtEntity>, val block: RR_FrameBlock? = null)

@JvmRecord
data class RR_DbAtWhatField(val flags: RR_AtWhatFieldFlags, val expr: RR_DbExpr, val resultType: RR_Type)

@JvmRecord
data class RR_AtWhatFieldFlags(val omit: Boolean, val sort: Int, val group: Boolean, val aggregate: Boolean = false)

@JvmRecord
data class RR_DbAtInternals(val block: RR_FrameBlock?)

/**
 * Describes how a group of flat DB columns should be combined into a single logical value.
 * Used when `Db_AtWhatValue_Complex` or `Db_AtWhatValue_ToStruct` produces multiple DB columns
 * for one logical what-value.
 */
@JvmRecord
data class RR_DbAtWhatFieldGroup(
    /** Number of flat DB columns this group consumes. */
    val columnCount: Int,
    /** How to combine the columns into a single value. */
    val combiner: RR_DbAtFieldCombiner,
    /**
     * R-level expressions to evaluate at interpretation time (not included in SQL SELECT).
     * Null when there are no R-level values.
     */
    val rExprs: ImmList<RR_Expr>? = null,
    /**
     * Interleaving order: true=DB sub-value (by index), false=R expression (by index).
     * Null when there are no R-level values (all items are DB columns in order).
     */
    val itemOrder: ImmList<Pair<Boolean, Int>>? = null,
    /**
     * Sub-groups for complex values: each sub-group reduces a portion of the flat DB columns
     * into a single value. When non-null, the DB columns are first reduced through sub-groups,
     * then interleaved with R expressions per [itemOrder], then combined by [combiner].
     * Null when there are no sub-groups (each DB column maps 1:1 to an input value).
     */
    val subGroups: ImmList<RR_DbAtWhatFieldGroup>? = null,
)

/** Describes how to combine flat DB columns into a single logical value. */
sealed interface RR_DbAtFieldCombiner {
    /** Single column, returned as-is. */
    data object Single: RR_DbAtFieldCombiner

    /** Combine columns into a tuple. */
    @JvmRecord
    data class Tuple(val tupleType: RR_Type): RR_DbAtFieldCombiner

    /** Combine columns into a struct. Mapping maps value index → struct attribute index. Empty when values are already in struct field order. */
    @JvmRecord
    data class Struct(val structDefIndex: Int, val attrMapping: ImmList<Int> = immListOf()): RR_DbAtFieldCombiner

    /** Combine columns by calling a function. The columns are passed as arguments. */
    @JvmRecord
    data class FunctionCall(val call: RR_FunctionCall): RR_DbAtFieldCombiner

    /** Combine columns into a list. */
    @JvmRecord
    data class ListLiteral(val listType: RR_Type): RR_DbAtFieldCombiner

    /** Combine columns into a map (alternating key, value). */
    @JvmRecord
    data class MapLiteral(val mapType: RR_Type): RR_DbAtFieldCombiner

    /** Evaluate a member access on the base value (first column). */
    @JvmRecord
    data class MemberAccess(val calculator: RR_MemberCalculator, val safe: Boolean): RR_DbAtFieldCombiner

    /** Wrap the value in a lazy container. */
    @JvmRecord
    data class Lazy(val type: RR_Type): RR_DbAtFieldCombiner
}

// --- Column at-expression support ---

@JvmRecord
data class RR_ColAtParam(val type: RR_Type, val ptr: RR_VarPtr)

enum class RR_IterableAdapterKind { DIRECT, LEGACY_MAP }

@JvmRecord
data class RR_ColAtFrom(val expr: RR_Expr, val block: RR_FrameBlock?, val iterableAdapter: RR_IterableAdapterKind)

@JvmRecord
data class RR_ColAtWhatField(val expr: RR_Expr, val flags: RR_AtWhatFieldFlags)

@JvmRecord
data class RR_ColAtWhat(
    val fields: ImmList<RR_ColAtWhatField>,
    val fieldCount: Int,
    val selectedFields: ImmList<Int>,
    val groupFields: ImmList<Int>?
)

enum class RR_ColAtSummarizationKind { NONE, GROUP, ALL }

/** Serializable per-field summarization kind for ColAt expressions. */
enum class RR_ColAtFieldSummarizationKind { NONE, GROUP, SUM, MIN, MAX, LIST, SET, MAP }

/**
 * Serializable field summarization info: kind + the binary op key needed for SUM,
 * and the comparator direction needed for MIN/MAX.
 */
@JvmRecord
data class RR_ColAtFieldSummarizationInfo(
    val kind: RR_ColAtFieldSummarizationKind,
    /** For SUM: the binary op key (e.g. "R_BinaryOp_Add_Integer"). For others: null. */
    val binaryOpKey: RR_BinaryOp? = null,
    /** For SUM: the zero value. For others: null. */
    val zeroValue: RR_ConstantValue? = null,
    /** For MIN: true, for MAX: false. For others: null. */
    val isMin: Boolean? = null,
    /** For LIST/SET/MAP: the collection element/key/value type info. */
    val collectionType: RR_Type? = null,
    /** For MAP: the map value type. */
    val mapValueType: RR_Type? = null,
)

/** Serializable sort entry: field index + ascending flag. */
@JvmRecord
data class RR_ColAtSortEntry(val fieldIndex: Int, val ascending: Boolean)

// --- Update statement support ---

/** Update/delete target variant kind. */
enum class RR_UpdateTargetKind { SIMPLE, EXPR_ONE, EXPR_MANY, OBJECT }

@JvmRecord
data class RR_UpdateWhat(val attrName: String, val attrIndex: Int, val expr: RR_DbExpr)

// --- Extendable function combiner kind ---

enum class RR_ExtendableCombinerKind { UNIT, BOOLEAN, NULLABLE, LIST, MAP }
