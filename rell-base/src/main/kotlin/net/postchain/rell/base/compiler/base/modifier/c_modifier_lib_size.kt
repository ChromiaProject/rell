package net.postchain.rell.base.compiler.base.modifier

import net.postchain.rell.base.compiler.base.core.C_DefinitionContext
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.def.C_AttrHeader
import net.postchain.rell.base.compiler.base.def.C_AttrHeaderHandle
import net.postchain.rell.base.compiler.base.def.C_EntityContext
import net.postchain.rell.base.compiler.base.modifier.C_AnnUtils.checkArgsRange
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.model.R_AttributeMetadata
import net.postchain.rell.base.model.R_SizeAttributeValidator
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_AttributeValidator
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import kotlin.math.pow

internal object C_Annotation_Size {
    val FIELD = C_ModifierField.valueAnnotation(Evaluator.annStr, Evaluator)

    private object Evaluator: AbstractSizeEvaluator("size") {
        override fun mkSizeData(value: Long): C_SizeData = C_SizeData(value, value, annStr)
        override fun mkSizeData(ctx: C_ModifierContext, name: C_Name, min: Long, max: Long): C_SizeData {
            return C_SizeData(min = min, max = max, annStr)
        }
    }
}

internal object C_Annotation_MinSize {
    val FIELD = C_ModifierField.valueAnnotation(Evaluator.annStr, Evaluator)

    private object Evaluator: AbstractSizeEvaluator("min_size") {
        override fun mkSizeData(value: Long): C_SizeData = C_SizeData(min = value, max = null, annStr)
    }
}

internal object C_Annotation_MaxSize {
    val FIELD = C_ModifierField.valueAnnotation(Evaluator.annStr, Evaluator)

    private object Evaluator: AbstractSizeEvaluator("max_size") {
        override fun mkSizeData(value: Long): C_SizeData = C_SizeData(min = null, max = value, annStr)
    }
}

private sealed class AbstractSizeEvaluator(val annStr: String): C_ModifierEvaluator<C_SizeData>() {

    abstract fun mkSizeData(value: Long): C_SizeData
    open fun mkSizeData(ctx: C_ModifierContext, name: C_Name, min: Long, max: Long): C_SizeData? {
        ctx.msgCtx.error(name.pos, "ann:$name:arg_count:2", "Wrong number of arguments (expected 1, got 2).")
        return null
    }

    override fun evaluate(ctx: C_ModifierContext, modLink: C_ModifierLink, args: List<C_AnnotationArg>): C_SizeData? {
        checkArgsRange(ctx, modLink.name, args, 1, 2)
        return when (args.size) {
            1 -> handleOneArg(ctx, modLink, args[0])
            2 -> handleTwoArg(ctx, modLink, args[0], args[1])
            else -> null
        }
    }

    private fun handleOneArg(ctx: C_ModifierContext, modLink: C_ModifierLink, arg: C_AnnotationArg): C_SizeData? {
        val value = evalVal(ctx, arg)
        return if (value == null) reportArgEvalErr(ctx, modLink) else mkSizeData(value)
    }

    private fun handleTwoArg(
        ctx: C_ModifierContext,
        modLink: C_ModifierLink,
        arg1: C_AnnotationArg,
        arg2: C_AnnotationArg,
    ): C_SizeData? {
        val value1 = evalVal(ctx, arg1)
        val value2 = evalVal(ctx, arg2)
        return if (value1 == null || value2 == null) {
            reportArgEvalErr(ctx, modLink)
        } else {
            mkSizeData(ctx, modLink.name, value1, value2)
        }
    }

    private fun evalVal(ctx: C_ModifierContext, arg: C_AnnotationArg): Long? {
        val value = arg.value(ctx)
        return if (value != null && value.type() is R_IntegerType) value.asInteger() else null
    }

    private fun reportArgEvalErr(ctx: C_ModifierContext, modLink: C_ModifierLink): C_SizeData? {
        val attr = " " + (modLink.target.name?.str ?: "")
        ctx.msgCtx.error(modLink.name.pos, "modifier:invalid:ann:${modLink.name.str}:argument_value",
            "Erroneous @${modLink.name.str} annotation on attribute$attr: invalid annotation argument.")
        return null
    }
}

data class C_SizeData(val min: Long?, val max: Long?, val annStr: String)

class C_SizeConstraint private constructor(val min: Long?, val max: Long?, val annStrs: ImmList<String>) {
    init {
        check((min != null || max != null) && annStrs.isNotEmpty())
    }

    fun compile(ctx: C_DefinitionContext, header: C_AttrHeader, type: R_Type): R_AttributeValidator? {
        val sizeAdapter = R_SizeAttributeValidator.getSizeAdapter(type)
        return if (sizeAdapter == null) {
            reportTypeError(ctx, header, annStrs, type)
            null
        } else {
            val metadata = R_AttributeMetadata(header.rName, type, ctx.defName, ctx.definitionType)
            R_SizeAttributeValidator(min, max, sizeAdapter, metadata)
        }
    }

    companion object {
        val COLLECTION_SIZE_LIMIT: Long = 2.0.pow(30.0).toLong()

        private fun from(min: Long?, max: Long?, annStrs: ImmList<String>): C_SizeConstraint? {
            return if (min == null && max == null) null else C_SizeConstraint(min, max, annStrs)
        }

        fun checkAndCombine(
            ctx: C_EntityContext,
            attr: C_AttrHeaderHandle,
            vararg sizeData: C_SizeData?,
        ): C_SizeConstraint? {
            val sizes = sizeData.filterNotNull().map { validated(ctx, attr, it) }
            val min = getOnlyOrNull(ctx, attr, sizes, "min") { it.min }
            val max = getOnlyOrNull(ctx, attr, sizes, "max") { it.max }
            if (min != null && max != null && min > max) {
                reportMinGreaterThanMax(ctx, attr, sizes.size, min, max)
            }
            val annStrs = sizeData.filterNotNull().mapToImmList{ it.annStr }
            return from(min, max, annStrs)
        }

        private fun getOnlyOrNull(
            ctx: C_EntityContext,
            attr: C_AttrHeaderHandle,
            sizeData: List<C_SizeData>,
            minMax: String,
            getter: (C_SizeData) -> Long?,
        ): Long? {
            val sizes = sizeData.mapNotNull(getter)
            if (sizes.size > 1) {
                ctx.msgCtx.error(attr.pos, "modifier:invalid:ann:conflict:size_with_${minMax}_size",
                    "Conflicting size annotations (@size and @${minMax}_size) on attribute ${attr.name.str} of " +
                    "${ctx.defCtx.cDefName.str()}.")
            }
            return sizes.firstOrNull()
        }

        private fun validated(
            ctx: C_EntityContext,
            attr: C_AttrHeaderHandle,
            sizeData: C_SizeData
        ): C_SizeData {
            validateValue(ctx, attr, sizeData.annStr, sizeData.min)
            // Don't double-report errors with @size(x) (where x gets copied to both min and max)
            if (sizeData.annStr != "size" || sizeData.min != sizeData.max) {
                validateValue(ctx, attr, sizeData.annStr, sizeData.max)
            }
            return sizeData
        }

        private fun validateValue(ctx: C_EntityContext, attr: C_AttrHeaderHandle, annStr: String, value: Long?) {
            if (value == null) {
                return
            }
            if (value < 0) {
                reportNegative(ctx, attr, annStr, value)
            }
            if (value > COLLECTION_SIZE_LIMIT) {
                reportTooLarge(ctx, attr, annStr, value)
            }
        }

        private fun reportMinGreaterThanMax(
            ctx: C_EntityContext,
            attr: C_AttrHeaderHandle,
            annCount: Int,
            min: Long,
            max: Long,
        ) {
            val subCode = if (annCount == 1) "size" else "min_size_max_size"
            val subMsg = if (annCount == 1) "annotation @size" else "annotations @min_size and @max_size"
            ctx.msgCtx.error(attr.pos, "modifier:invalid:ann:$subCode:min_greater_than_max",
                "Erroneous $subMsg on attribute ${attr.name.str} of ${ctx.defCtx.cDefName.str()}: min greater than " +
                "max (min = $min, max = $max).")
        }

        // This turns out to be dead code because the parser forbids supplying a negative value.
        private fun reportNegative(ctx: C_EntityContext, attr: C_AttrHeaderHandle, annStr: String, value: Long) {
            ctx.msgCtx.error(attr.pos, "modifier:invalid:ann:$annStr:negative",
                "Erroneous @$annStr annotation on attribute ${attr.name.str} of ${ctx.defCtx.cDefName.str()}: " +
                "negative argument (got $value).")
        }

        private fun reportTooLarge(ctx: C_EntityContext, attr: C_AttrHeaderHandle, annStr: String, value: Long) {
            ctx.msgCtx.error(attr.pos, "modifier:invalid:ann:$annStr:too_large",
                "Erroneous @$annStr annotation on attribute ${attr.name.str} of ${ctx.defCtx.cDefName.str()}: " +
                "argument exceeds limit of $COLLECTION_SIZE_LIMIT (got $value).")
        }

        fun reportTypeError(ctx: C_DefinitionContext, header: C_AttrHeader, annStrs: ImmList<String>, type: R_Type) {
            val subCode = annStrs.sortedDescending().joinToString(separator = "_")
            val subMsg = if (annStrs.size == 1) {
                "annotation @${annStrs.first()}"
            } else {
                "annotations @${annStrs.joinToString(", @")}"
            }
            ctx.msgCtx.error(header.pos, "modifier:invalid:ann:$subCode:invalid_type",
                "Erroneous $subMsg on attribute ${header.name.str} of ${ctx.cDefName.str()}: attribute type " +
                "${type.str()} does not support size annotations.")
        }

        fun reportNonStruct(ctx: C_DefinitionContext, header: C_AttrHeaderHandle, annStrs: ImmList<String>) {
            val subCode = annStrs.sortedDescending().joinToString(separator = "_")
            val subMsg = if (annStrs.size == 1) {
                "annotation @${annStrs.first()}"
            } else {
                "annotations @${annStrs.joinToString(", @")}"
            }
            ctx.msgCtx.error(header.pos, "modifier:invalid:ann:$subCode:non_struct",
                "Erroneous $subMsg on attribute ${header.name.str} of ${ctx.cDefName.str()}: Size annotations are " +
                "valid on struct attributes only, but were found on attribute of ${ctx.cDefName.str()}.")
        }
    }
}
