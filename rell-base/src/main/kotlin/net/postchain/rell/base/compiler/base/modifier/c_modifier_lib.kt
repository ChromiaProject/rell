/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.modifier

import net.postchain.rell.base.compiler.ast.S_KeywordModifierKind
import net.postchain.rell.base.compiler.base.core.C_QualifiedNameHandle
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.model.expr.R_AtWhatSort

enum class C_AtSummarizationKind(val annotation: String) {
    GROUP("group"),
    SUM("sum"),
    MIN("min"),
    MAX("max"),
    LIST("list"),
    SET("set"),
    MAP("map"),
}

object C_Annotations {
    const val SORT = "sort"
    const val SORT_DESC = "sort_desc"
    const val TEST = "test"
}

object C_ModifierFields {
    val MOUNT = C_Annotation_Mount.FIELD

    val EXTERNAL_MODULE = C_Annotation_External.FIELD_EXTERNAL_MODULE
    val EXTERNAL_CHAIN = C_Annotation_External.FIELD_EXTERNAL_CHAIN

    val LOG = C_ModifierField.flagAnnotation("log")
    val TEST = C_ModifierField.flagAnnotation(C_Annotations.TEST)
    val DEPRECATED = C_Annotation_Deprecated.FIELD

    val ABSTRACT = C_ModifierField.flagKeyword(S_KeywordModifierKind.ABSTRACT)
    val OVERRIDE = C_ModifierField.flagKeyword(S_KeywordModifierKind.OVERRIDE)
    val EXTENDABLE = C_ModifierField.flagAnnotation("extendable")
    val EXTEND = C_Annotation_Extend.FIELD

    val OMIT = C_ModifierField.flagAnnotation("omit")
    val SORT = C_ModifierField.choiceAnnotations(mapOf(C_Annotations.SORT to R_AtWhatSort.ASC, C_Annotations.SORT_DESC to R_AtWhatSort.DESC))
    val SUMMARIZATION = C_ModifierField.choiceAnnotations(C_AtSummarizationKind.values().associateBy { it.annotation })
}

private object C_Annotation_Deprecated {
    val FIELD = C_ModifierField.valueAnnotation("deprecated", Evaluator, hidden = true)

    private object Evaluator: C_ModifierEvaluator<C_Deprecated>() {
        override fun evaluate(ctx: C_ModifierContext, modLink: C_ModifierLink, args: List<C_AnnotationArg>): C_Deprecated {
            C_AnnUtils.checkArgsNone(ctx, modLink.name, args)
            return C_Deprecated(useInstead = null, error = true)
        }
    }
}

private object C_Annotation_Extend {
    val FIELD = C_ModifierField.valueAnnotation("extend", Evaluator)

    private object Evaluator: C_ModifierEvaluator<C_QualifiedNameHandle>() {
        override fun evaluate(
            ctx: C_ModifierContext,
            modLink: C_ModifierLink,
            args: List<C_AnnotationArg>,
        ): C_QualifiedNameHandle? {
            val arg = C_AnnUtils.checkArgsOne(ctx, modLink.name, args)
            return arg?.name(ctx)
        }
    }
}
