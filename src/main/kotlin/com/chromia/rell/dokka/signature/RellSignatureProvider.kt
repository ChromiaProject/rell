package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.dri.DriOfUnit
import com.chromia.rell.dokka.dri.isAlias
import com.chromia.rell.dokka.model.IsPure
import com.chromia.rell.dokka.model.IsStatic
import com.chromia.rell.dokka.model.IsTuple
import com.chromia.rell.dokka.model.IsVararg
import com.chromia.rell.dokka.model.IsZeroOne
import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isQuery
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.parametersBlock
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.model.TypeConstructor
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.Void
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.pages.TokenStyle
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.utilities.DokkaLogger

class RellSignatureProvider internal constructor(
        ctcc: CommentsToContentConverter,
        logger: DokkaLogger
) : SignatureProvider {
    private val contentBuilder = PageContentBuilder(ctcc, this, logger)

    constructor(context: DokkaContext) : this(
            context.plugin<DokkaBase>().querySingle { commentsToContentConverter },
            context.logger
    )

    override fun signature(documentable: Documentable): List<ContentNode> {
        return when (documentable) {
            is DFunction -> functionSignature(documentable)
            is DProperty -> propertySignature(documentable)
            else -> listOf()
        }
    }

    private fun propertySignature(d: DProperty): List<ContentNode> {
        return d.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(d, ContentKind.Symbol, setOf(TextStyle.Monospace), sourceSets = setOf(sourceSet)) {
                if (d.isMutable()) keyword("var ") else keyword("val ")
                link(d.name, d.dri) // TODO: set styles if deprecated
                operator(": ")
                signatureForProjection(d.type)
                if (!d.isMutable()) {
                    // Set default value
                }
            }
        }

    }

    private fun DProperty.isMutable(): Boolean {
        return this.extra[IsVar] != null || this.setter != null
    }

    private fun Documentable.isAlias(): Boolean = this.dri.isAlias()

    private fun DFunction.isPure(): Boolean {
        return this.extra[IsPure] != null
    }

    private fun DFunction.isStatic(): Boolean {
        return this.extra[IsStatic] != null
    }

    private fun DParameter.isVararg(): Boolean {
        return this.extra[IsVararg] != null
    }

    private fun DParameter.isZeroOne(): Boolean {
        return this.extra[IsZeroOne] != null
    }

    private fun GenericTypeConstructor.isTuple(): Boolean = this.extra[IsTuple] != null

    private fun functionSignature(d: DFunction): List<ContentNode> {
        return d.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(d, ContentKind.Symbol, setOf(TextStyle.Monospace), sourceSets = setOf(sourceSet)) {
                if (d.isAlias()) punctuation("(alias) ")
                if (d.isPure()) keyword("pure ")
                if (d.isStatic()) keyword("static ")
                when {
                    d.isConstructor -> keyword("constructor")
                    d.isQuery() -> keyword("query ")
                    d.isOperation() -> keyword("operation ")
                    else -> keyword("function ")
                }
                if (!d.isConstructor) link(d.name, d.dri)

                punctuation("(")
                if (d.parameters.isNotEmpty()) {
                    parametersBlock(d) { p ->
                        if (p.isZeroOne()) punctuation("[")
                        text(p.name!!)
                        operator(": ")
                        signatureForProjection(p.type)
                        if (p.isZeroOne()) punctuation("]")
                        if (p.isVararg()) punctuation("...")
                    }
                }

                punctuation(")")
                if (!d.isOperation()) {
                    operator(": ")
                    signatureForProjection(d.type)
                }
            }
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.signatureForReceiver(p: TypeParameter) {
        link(p.name, p.dri)
    }

    private fun PageContentBuilder.DocumentableContentBuilder.signatureForProjection(
            p: Projection, showFullyQualifiedName: Boolean = false
    ) {
        when (p) {

            is UnresolvedBound -> {
                text(p.name)
            }

            is TypeParameter -> {
                p.presentableName?.let { text("$it: ") }
                link(p.name, p.dri)
            }

            is Dynamic -> {
            }

            is GenericTypeConstructor -> {
                group(styles = emptySet()) {
                    p.presentableName?.let { link(it, p.dri) }
                    list(
                            p.projections,
                            prefix = if (p.isTuple()) "(" else "<",
                            suffix = if (p.isTuple()) ")" else ">",
                            separatorStyles = mainStyles + TokenStyle.Punctuation,
                            surroundingCharactersStyle = mainStyles + TokenStyle.Operator)
                    {
                        signatureForProjection(it, showFullyQualifiedName)
                    }
                }
            }

            is FunctionalTypeConstructor -> {
                group(styles = emptySet()) {
                    if (p.projections.size == 1) punctuation("()")
                    list(p.projections.dropLast(1), prefix = "(", suffix = ")", separatorStyles = mainStyles + TokenStyle.Punctuation,
                            surroundingCharactersStyle = mainStyles + TokenStyle.Operator) {
                        signatureForProjection(it, showFullyQualifiedName)
                    }
                operator(" -> ")
                signatureForProjection(p.projections.last(), showFullyQualifiedName)
                }
            }

            is Nullable -> {
                signatureForProjection(p.inner)
                operator("?")
            }

            is Void -> link("unit", DriOfUnit)
            else -> TODO(p.toString())
        }
    }

    private fun DFunction.documentReturnType() = when {
        this.isConstructor -> false
        this.type is TypeConstructor && (this.type as TypeConstructor).dri == DriOfUnit -> false
        this.type is Void -> false
        else -> true
    }
}