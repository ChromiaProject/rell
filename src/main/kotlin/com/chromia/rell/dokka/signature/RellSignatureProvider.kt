package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isQuery
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.parametersBlock
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DriOfUnit
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.model.TypeConstructor
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.Void
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.utilities.DokkaLogger

class RellSignatureProvider internal constructor(
        ctcc: CommentsToContentConverter,
        logger: DokkaLogger
): SignatureProvider {
    private val contentBuilder = PageContentBuilder(ctcc, this, logger)

    constructor(context: DokkaContext) : this(
            context.plugin<DokkaBase>().querySingle { commentsToContentConverter },
            context.logger
    )
    override fun signature(documentable: Documentable): List<ContentNode> {
        return when (documentable) {
            is DFunction -> functionSignature(documentable)
            else -> listOf()
        }
    }

    private fun functionSignature(dFunction: DFunction): List<ContentNode> {
        return dFunction.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(dFunction, ContentKind.Symbol, setOf(TextStyle.Monospace), sourceSets = setOf(sourceSet)) {
                when {
                    dFunction.isConstructor -> keyword("constructor")
                    dFunction.isQuery() -> keyword("query ")
                    dFunction.isOperation() -> keyword("operation ")
                    else -> keyword("function ")
                }
                if (!dFunction.isConstructor) link(dFunction.name, dFunction.dri)

                punctuation("(")
                if (dFunction.parameters.isNotEmpty()) {
                   parametersBlock(dFunction) { p ->
                       text(p.name!!)
                       operator(": ")
                       signatureForProjection(p.type)
                   }
                }

                punctuation(")")
                if (!dFunction.isOperation() && dFunction.type is TypeParameter) {
                    operator(": ")
                    signatureForReceiver(dFunction.type as TypeParameter)
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
        return when (p) {
            is TypeParameter -> {
                link(p.name, p.dri)
            }
            is Dynamic -> {
            }
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