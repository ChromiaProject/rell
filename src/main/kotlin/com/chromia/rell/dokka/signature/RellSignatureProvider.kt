package com.chromia.rell.dokka.signature

import com.chromia.rell.dokka.dri.DriOfUnit
import com.chromia.rell.dokka.dri.isAlias
import com.chromia.rell.dokka.model.isEntity
import com.chromia.rell.dokka.model.isHidden
import com.chromia.rell.dokka.model.isMutable
import com.chromia.rell.dokka.model.isObject
import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isPure
import com.chromia.rell.dokka.model.isQuery
import com.chromia.rell.dokka.model.isStatic
import com.chromia.rell.dokka.model.isStruct
import com.chromia.rell.dokka.model.isTuple
import com.chromia.rell.dokka.model.isType
import com.chromia.rell.dokka.model.isVararg
import com.chromia.rell.dokka.model.isZeroOne
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.annotations
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.annotationsBlock
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.annotationsInline
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.driOrNull
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.parametersBlock
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.stylesIfDeprecated
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.base.transformers.documentables.isDeprecated
import org.jetbrains.dokka.base.transformers.pages.comments.CommentsToContentConverter
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DriOfAny
import org.jetbrains.dokka.links.sureClassNames
import org.jetbrains.dokka.links.withTargetToDeclaration
import org.jetbrains.dokka.model.Bound
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.DTypeParameter
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.Nullable
import org.jetbrains.dokka.model.PrimaryConstructorExtra
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.model.TypeAliased
import org.jetbrains.dokka.model.TypeConstructor
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.Variance
import org.jetbrains.dokka.model.Void
import org.jetbrains.dokka.model.WithAbstraction
import org.jetbrains.dokka.model.WithConstructors
import org.jetbrains.dokka.model.WithGenerics
import org.jetbrains.dokka.model.WithSupertypes
import org.jetbrains.dokka.model.withDri
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
            is DTypeAlias -> typeAliasSignature(documentable)
            is DClasslike -> classLikeSignature(documentable)
            is DFunction -> functionSignature(documentable)
            is DProperty -> propertySignature(documentable)
            is DTypeParameter -> typeParameterSignature(documentable)
            else -> listOf()
        }
    }

    private fun typeAliasSignature(a: DTypeAlias): List<ContentNode> {
        return a.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(a, sourceSets = setOf(sourceSet)) {
                a.underlyingType.entries.groupBy ({ it.value }, { it.key }).map { (type, platforms) ->
                    +contentBuilder.contentFor(a, ContentKind.Symbol,
                            setOf(TextStyle.Monospace),
                            sourceSets = platforms.toSet()) {
                        keyword("alias ")
                        group(styles = mainStyles + a.stylesIfDeprecated(sourceSet)) {
                            signatureForProjection(a.type)
                        }
                        operator(" = ")
                        signatureForTypealiasTarget(a, type)
                    }
                }
            }
        }
    }

    private fun PageContentBuilder.DocumentableContentBuilder.signatureForTypealiasTarget(
            typeAlias: DTypeAlias, bound: Bound
    ) {
        signatureForProjection(
                p = bound,
                showFullyQualifiedName = bound.driOrNull?.classNames == typeAlias.dri.classNames
        )
    }

    private fun classLikeSignature(c: DClasslike) = c.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(c, ContentKind.Symbol,
                    setOf(TextStyle.Monospace),
                    sourceSets = setOf(sourceSet)) {
                if (c.isHidden()) keyword("hidden ")
                if (c is WithAbstraction) {
                    modifier(c, sourceSet)
                }
                when (c) {
                    is DClass -> {
                        if (c.isEntity()) keyword("entity ")
                        if (c.isObject()) keyword("object ")
                        if (c.isStruct()) keyword("struct ")
                        if (c.isType()) keyword("type ")
                    }
                    is DInterface -> keyword("entity ")
                    is DObject -> keyword("object ")
                    is DEnum -> keyword("enum ")
                    else -> TODO("Type $c not treated")
                }

                link(c.name!!, c.dri, styles = mainStyles) // + deprecationStyles)
                if (c is WithGenerics) {
                    list(c.generics, prefix = "<", suffix = ">",
                            separatorStyles = mainStyles + TokenStyle.Punctuation,
                            surroundingCharactersStyle = mainStyles + TokenStyle.Operator) {
                        //annotationsInline(it)
                        +buildSignature(it)
                    }
                }
                if (c is WithConstructors) {
                    val pConstructor = c.constructors.singleOrNull { it.extra[PrimaryConstructorExtra] != null }
                    if (pConstructor?.sourceSets?.contains(sourceSet) == true) {
                        if (pConstructor.annotations().values.any { it.isNotEmpty() }) {
                            text(Typography.nbsp.toString())
                            annotationsInline(pConstructor)
                            keyword("constructor")
                        }

                        // for primary constructor, opening and closing parentheses
                        // should be present only if it has parameters. If there are
                        // no parameters, it should result in `class Example`
                        if (pConstructor.parameters.isNotEmpty()) {
                            val parameterPropertiesByName = c.properties
                                    //.filter { it.isAlsoParameter(sourceSet) }
                                    .associateBy { it.name }

                            punctuation("(")
                            parametersBlock(pConstructor) { param ->
                                annotationsInline(param)
                                parameterPropertiesByName[param.name]?.let { property ->
                                    property.setter?.let { keyword("var ") } ?: keyword("val ")
                                }
                                text(param.name.orEmpty())
                                operator(": ")
                                signatureForProjection(param.type)
                                //defaultValueAssign(param, sourceSet)
                            }
                            punctuation(")")
                        }
                    }
                }
                if (c is WithSupertypes) {
                    c.supertypes.filter { it.key == sourceSet }.map { (s, typeConstructors) ->
                        list(typeConstructors, prefix = " : ", sourceSets = setOf(s)) {
                            link(it.typeConstructor.dri.sureClassNames, it.typeConstructor.dri, sourceSets = setOf(s))
                            list(it.typeConstructor.projections, prefix = "<", suffix = "> ",
                                    separatorStyles = mainStyles + TokenStyle.Punctuation,
                                    surroundingCharactersStyle = mainStyles + TokenStyle.Operator) {
                                signatureForProjection(it)
                            }
                        }
                    }
                }
            }
    }
    private fun <T> PageContentBuilder.DocumentableContentBuilder.modifier(
            documentable: T,
            sourceSet: DokkaConfiguration.DokkaSourceSet
    ) where T : Documentable, T : WithAbstraction {
        val modifier = documentable.modifier[sourceSet] ?: return
        keyword("${modifier.name} ")
    }

    private fun typeParameterSignature(t: DTypeParameter) = t.sourceSets.map {
        contentBuilder.contentFor(t, sourceSets = setOf(it)) {
            group(styles = mainStyles + t.stylesIfDeprecated(it)) {
                signatureForProjection(t.variantTypeParameter.withDri(t.dri.withTargetToDeclaration()))
            }
            list(
                    elements = t.nontrivialBounds,
                    prefix = " : ",
                    surroundingCharactersStyle = mainStyles + TokenStyle.Operator
            ) { bound ->
                signatureForProjection(bound)
            }
        }
    }

    private val DTypeParameter.nontrivialBounds: List<Bound>
        get() = bounds.filterNot { it is Nullable && it.inner.driOrNull == DriOfAny }

    private fun propertySignature(d: DProperty): List<ContentNode> {
        val deprecationStyles = if (d.isDeprecated()) setOf(TextStyle.Strikethrough) else setOf()
        return d.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(d, ContentKind.Symbol, setOf(TextStyle.Monospace), sourceSets = setOf(sourceSet)) {
                if (d.isMutable()) keyword("var ") else keyword("val ")
                link(d.name, d.dri, styles = mainStyles + deprecationStyles)
                operator(": ")
                signatureForProjection(d.type)
                // Set default value
            }
        }

    }

    private fun functionSignature(d: DFunction): List<ContentNode> {
        val deprecationStyles = if (d.isDeprecated()) setOf(TextStyle.Strikethrough) else setOf()
        return d.sourceSets.map { sourceSet ->
            contentBuilder.contentFor(d, ContentKind.Symbol, setOf(TextStyle.Monospace), sourceSets = setOf(sourceSet)) {
                if (d.dri.isAlias()) punctuation("(alias) ")
                if (d.isPure()) keyword("pure ")
                if (d.isStatic()) keyword("static ")
                when {
                    d.isConstructor -> keyword("constructor")
                    d.isQuery() -> keyword("query ")
                    d.isOperation() -> keyword("operation ")
                    else -> keyword("function ")
                }

                list(d.generics, prefix = "<", suffix = "> ",
                        separatorStyles = mainStyles + TokenStyle.Punctuation,
                        surroundingCharactersStyle = mainStyles + TokenStyle.Operator) {
                    +buildSignature(it)
                }

                if (!d.isConstructor) link(d.name, d.dri, styles = mainStyles + deprecationStyles)

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
                if (d.documentReturnType()) {
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
            is TypeParameter -> {
                p.presentableName?.let {
                    text(it)
                    operator(": ")
                }
                link(p.name, p.dri)
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

            is Variance<*> -> group(styles = emptySet()) {
                p.takeIf { it.toString().isNotEmpty() }?.let { keyword("$it ") } // Will never happen??
                signatureForProjection(p.inner, showFullyQualifiedName)
            }

            is Nullable -> group(styles = emptySet()) {
                signatureForProjection(p.inner, showFullyQualifiedName)
                operator("?")
            }

            is TypeAliased -> signatureForProjection(p.typeAlias)
            is Void -> link("unit", DriOfUnit)
            is UnresolvedBound -> text(p.name)
            is Dynamic -> { }
            else -> TODO(p.toString())
        }
    }

    private fun DFunction.documentReturnType() = when {
        this.isOperation() -> false
        this.isConstructor -> false
        this.type is TypeConstructor && (this.type as TypeConstructor).dri == DriOfUnit -> false
        this.type is Void -> false
        else -> true
    }
}