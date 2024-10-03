/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.L_TypeDefFlags
import net.postchain.rell.base.lmodel.L_TypeDefParent
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.ide.IdeCompletionParam
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

abstract class DocDeclaration {
    val code: DocCode by lazy { getCode0() }
    val completion: Completion? by lazy { getCompletion0() }

    open fun isDeprecated(): Boolean = false
    protected open fun getCompletion0(): Completion? = null
    protected abstract fun getCode0(): DocCode

    final override fun toString() = code.strCode()

    class Completion(
        val params: List<IdeCompletionParam>?,
        val result: String?,
    )

    companion object {
        val NONE: DocDeclaration = DocDeclaration_None
    }
}

private object DocDeclaration_None: DocDeclaration() {
    override fun getCode0() = DocCode.EMPTY
}

abstract class DocDeclaration_Annotated(
    private val modifiers: DocModifiers,
): DocDeclaration() {
    protected abstract fun genCode0(b: DocCode.Builder)

    final override fun getCode0(): DocCode {
        val b = DocCode.builder()
        modifiers.appendTo(b)
        genCode0(b)
        return b.build()
    }

    final override fun isDeprecated() = modifiers.isDeprecated()
}

class DocDeclaration_Module(
    modifiers: DocModifiers,
): DocDeclaration_Annotated(modifiers) {
    override fun genCode0(b: DocCode.Builder) {
        b.keyword("module")
    }
}

class DocDeclaration_ImportModule(
    modifiers: DocModifiers,
    private val moduleName: R_ModuleName,
    private val alias: R_Name?,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = Completion(null, moduleName.str())

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("import")
        b.raw(" ")
        if (alias != null) {
            b.raw(alias.str)
            b.sep(": ")
        }
        DocDecUtils.appendModuleName(b, moduleName)
    }
}

class DocDeclaration_ImportWildcard(
    modifiers: DocModifiers,
    private val moduleName: R_ModuleName,
    private val aliasName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = Completion(null, "${moduleName.str()}.*")

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("import")
        b.raw(" ")
        b.raw(aliasName.str)
        b.sep(": ")
        DocDecUtils.appendModuleName(b, moduleName)
        b.raw(".*")
    }
}

class DocDeclaration_ImportExactModule(
    modifiers: DocModifiers,
    private val moduleName: R_ModuleName,
    private val aliasName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0(): Completion {
        val result = "${moduleName.str()}.{...}"
        return Completion(null, result)
    }

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("import")
        b.raw(" ")
        b.raw(aliasName.str)
        b.sep(": ")
        DocDecUtils.appendModuleName(b, moduleName)
        b.raw(".")
        b.raw("{...}")
    }
}

sealed class DocDeclaration_ImportExactAlias(
    modifiers: DocModifiers,
    protected val moduleName: R_ModuleName,
    protected val qualifiedName: R_QualifiedName,
    private val namespaceAlias: R_Name?,
    protected val exactAliasName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    protected abstract val wildcard: Boolean

    final override fun genCode0(b: DocCode.Builder) {
        b.keyword("import")
        b.raw(" ")

        if (namespaceAlias != null) {
            b.raw(namespaceAlias.str)
            b.sep(": ")
        }

        DocDecUtils.appendModuleName(b, moduleName)
        b.raw(".")
        b.raw("{")
        b.raw(exactAliasName.str)
        b.sep(": ")
        b.link(qualifiedName.str())

        if (wildcard) {
            b.raw(".*")
        }

        b.raw("}")
    }
}

class DocDeclaration_ImportExactAlias_Single(
    modifiers: DocModifiers,
    moduleName: R_ModuleName,
    qualifiedName: R_QualifiedName,
    namespaceAlias: R_Name?,
    exactAliasName: R_Name,
    private val targetDeclaration: DocDeclaration,
): DocDeclaration_ImportExactAlias(modifiers, moduleName, qualifiedName, namespaceAlias, exactAliasName) {
    override val wildcard = false

    override fun getCompletion0(): Completion {
        val targetComp = targetDeclaration.completion
        val result = targetComp?.result ?: "${moduleName.str()}:${qualifiedName.str()}"
        return Completion(targetComp?.params, result)
    }
}

class DocDeclaration_ImportExactAlias_Wildcard(
    modifiers: DocModifiers,
    moduleName: R_ModuleName,
    qualifiedName: R_QualifiedName,
    namespaceAlias: R_Name?,
    exactAliasName: R_Name,
): DocDeclaration_ImportExactAlias(modifiers, moduleName, qualifiedName, namespaceAlias, exactAliasName) {
    override val wildcard = true

    override fun getCompletion0(): Completion {
        val result = "${moduleName.str()}:${qualifiedName.str()}.*"
        return Completion(null, result)
    }
}

class DocDeclaration_Namespace(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("namespace")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclaration_Constant(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
    private val type: DocType,
    private val value: DocValue?,
) : DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("val")
        b.raw(" ")
        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)

        if (value != null) {
            b.sep(" = ")
            value.genCode(b)
        }
    }
}

class DocDeclaration_Property(
    private val simpleName: R_Name,
    private val type: DocType,
    private val pure: Boolean,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        if (pure) {
            b.keyword("pure")
            b.raw(" ")
        }

        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)

        return b.build()
    }
}

class DocDeclaration_SpecialProperty(
    private val simpleName: R_Name,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName.str)
        return b.build()
    }
}

class DocDeclaration_Enum(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("enum")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclaration_EnumValue(
    private val simpleName: R_Name,
    private val type: DocType,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(type)
    override fun getCode0() = DocCode.raw(simpleName.str)
}

class DocDeclaration_Entity(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("entity")
        b.sep(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclaration_EntityAttribute(
    private val simpleName: R_Name,
    private val type: DocType,
    private val isMutable: Boolean,
    private val keyIndexKind: R_KeyIndexKind?,
    private val expr: DocExpr? = null,
    private val keys: Collection<R_Key> = immListOf(),
    private val indices: Collection<R_Index> = immListOf(),
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        if (isMutable) {
            b.keyword("mutable")
            b.raw(" ")
        }

        if (keyIndexKind != null) {
            b.keyword(keyIndexKind.code)
            b.raw(" ")
        }

        b.raw(simpleName.str)

        b.sep(": ")
        type.genCode(b)

        if (expr != null) {
            b.sep(" = ")
            expr.genCode(b)
        }

        if (keys.isNotEmpty() || indices.isNotEmpty()) {
            b.newline()
            appendKeysIndices(b, keys, "key")
            appendKeysIndices(b, indices, "index")
        }

        return b.build()
    }

    private fun appendKeysIndices(b: DocCode.Builder, col: Collection<R_KeyIndex>, kw: String) {
        for (ki in col) {
            b.newline()
            b.keyword(kw)
            b.raw(" ")
            for ((i, name) in ki.attribs.withIndex()) {
                if (i > 0) b.sep(", ")
                b.link(name.str)
            }
        }
    }
}

class DocDeclaration_Object(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("object")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclaration_Struct(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("struct")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclaration_StructAttribute(
    private val simpleName: R_Name,
    private val type: DocType,
    private val isMutable: Boolean,
): DocDeclaration() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        if (isMutable) {
            b.keyword("mutable")
            b.raw(" ")
        }

        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)

        return b.build()
    }
}

class DocDeclaration_Parameter(
    private val param: DocFunctionParam,
    private val isLazy: Boolean,
    private val implies: L_ParamImplication?,
    private val expr: DocExpr?,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(param.type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        if (implies != null) {
            b.raw("@implies(")
            b.raw(implies.kind.name)
            b.raw(") ")
        }

        if (param.exact) b.keyword("exact").raw(" ")
        if (param.nullable) b.keyword("nullable").raw(" ")
        if (isLazy) b.keyword("lazy").raw(" ")

        val arity = when (param.arity) {
            M_ParamArity.ONE -> null
            M_ParamArity.ZERO_ONE -> "zero_one"
            M_ParamArity.ZERO_MANY -> "zero_many"
            M_ParamArity.ONE_MANY -> "one_many"
        }
        if (arity != null) b.keyword(arity).raw(" ")

        b.raw(param.name)
        b.sep(": ")

        param.type.genCode(b)

        if (expr != null) {
            b.sep(" = ")
            expr.genCode(b)
        }

        return b.build()
    }
}

class DocDeclaration_Function(
    docModifiers: DocModifiers,
    private val simpleName: R_Name,
    private val header: DocFunctionHeader,
    private val params: List<DocDeclaration>,
    private val hasBody: Boolean? = null,
): DocDeclaration_Annotated(docModifiers) {
    init {
        checkEquals(params.size, header.params.size) { simpleName }
    }

    override fun getCompletion0(): Completion {
        val paramsMap = params.mapIndexed { i, param -> header.params[i].name to param }.toImmMap()
        return DocDecUtils.completion(header.resultType, paramsMap)
    }

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("function")
        b.raw(" ")

        if (header.typeParams.isNotEmpty()) {
            DocDecUtils.appendTypeParams(b, header.typeParams)
            b.raw(" ")
        }

        b.raw(simpleName.str)

        DocDecUtils.appendFunctionParams(b, params)

        b.sep(": ")
        header.resultType.genCode(b)

        if (hasBody != null) {
            if (hasBody) {
                b.newline()
                b.raw("{...}")
            } else {
                b.raw(";")
            }
        }
    }
}

class DocDeclaration_SpecialFunction(
    private val simpleName: R_Name,
    private val isStatic: Boolean,
): DocDeclaration() {
    override fun getCompletion0() = Completion(immListOf(), null)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        if (isStatic) b.keyword("static").raw(" ")
        b.keyword("function")
        b.raw(" ")
        b.raw(simpleName.str)
        b.raw("(...)")
        return b.build()
    }
}

class DocDeclaration_Operation(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
    private val paramNames: List<String>,
    private val params: List<DocDeclaration>,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0(): Completion {
        val paramsMap = params.mapIndexed { i, param -> paramNames[i] to param }.toImmMap()
        return DocDecUtils.completion(null, paramsMap)
    }

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("operation")
        b.raw(" ")
        b.raw(simpleName.str)
        DocDecUtils.appendFunctionParams(b, params)
    }
}

class DocDeclaration_Query(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
    private val resultType: DocType,
    private val paramNames: List<String>,
    private val params: List<DocDeclaration>,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0(): Completion {
        val paramsMap = params.mapIndexed { i, param -> paramNames[i] to param }.toImmMap()
        return DocDecUtils.completion(resultType, paramsMap)
    }

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("query")
        b.raw(" ")
        b.raw(simpleName.str)
        DocDecUtils.appendFunctionParams(b, params)
        b.sep(": ")
        resultType.genCode(b)
    }
}

class DocDeclaration_Type(
    private val simpleName: R_Name,
    private val typeParams: List<DocTypeParam>,
    private val lParent: L_TypeDefParent?,
    private val flags: L_TypeDefFlags,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        if (flags.abstract) b.keyword("abstract").raw(" ")
        if (flags.hidden) b.keyword("internal").raw(" ")

        b.keyword("type")
        b.raw(" ")
        b.raw(simpleName.str)

        DocDecUtils.appendTypeParams(b, typeParams)

        if (lParent != null) {
            b.sep(": ")
            docCodeParent(b, lParent)
        }

        return b.build()
    }

    private fun docCodeParent(b: DocCode.Builder, lParent: L_TypeDefParent) {
        b.link(lParent.typeDef.simpleName.str)

        if (lParent.args.isNotEmpty()) {
            b.raw("<")
            for ((i, mArgType) in lParent.args.withIndex()) {
                if (i > 0) b.sep(", ")
                val docArgType = L_TypeUtils.docType(mArgType)
                docArgType.genCode(b)
            }
            b.raw(">")
        }
    }
}

class DocDeclaration_TypeConstructor(
    private val simpleName: R_Name,
    private val typeParams: List<DocTypeParam>,
    private val paramNames: List<String>,
    private val params: List<DocDeclaration>,
    private val deprecated: C_Deprecated?,
    private val pure: Boolean,
): DocDeclaration() {
    override fun getCompletion0(): Completion {
        val paramsMap = params.mapIndexed { i, param -> paramNames[i] to param }.toImmMap()
        return DocDecUtils.completion(null, paramsMap)
    }

    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        DocDecUtils.appendDeprecated(b, deprecated)

        if (pure) {
            b.keyword("pure")
            b.raw(" ")
        }

        b.keyword("constructor")
        DocDecUtils.appendTypeParams(b, typeParams)
        DocDecUtils.appendFunctionParams(b, params)

        return b.build()
    }
}

class DocDeclaration_TypeSpecialConstructor: DocDeclaration() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.keyword("constructor")
        b.raw("(...)")
        return b.build()
    }
}

class DocDeclaration_TypeExtension(
    private val simpleName: R_Name,
    private val typeParams: List<DocTypeParam>,
    private val selfType: DocType,
): DocDeclaration() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()

        b.keyword("extension")
        b.raw(" ")
        b.raw(simpleName.str)

        DocDecUtils.appendTypeParams(b, typeParams)

        b.sep(": ")
        selfType.genCode(b)

        return b.build()
    }
}

class DocDeclaration_Alias(
    modifiers: DocModifiers,
    private val simpleName: R_Name,
    private val targetFullName: R_FullName,
    private val targetDeclaration: DocDeclaration,
    private val targetQualifiedName: R_QualifiedName = targetFullName.qualifiedName,
): DocDeclaration_Annotated(modifiers) {
    override fun getCompletion0(): Completion {
        val targetComp = targetDeclaration.completion
        val result = targetComp?.result ?: targetFullName.str()
        return Completion(targetComp?.params, result)
    }

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("alias")
        b.raw(" ")
        b.raw(simpleName.str)
        b.sep(" = ")
        b.link(targetQualifiedName.str())

        if (targetDeclaration != NONE) {
            b.newline()
            b.newline()
            b.append(targetDeclaration.code)
        }
    }
}

class DocDeclaration_TupleAttribute(
    private val simpleName: R_Name,
    private val type: DocType,
): DocDeclaration() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)
        return b.build()
    }
}

class DocDeclaration_AtVariable(
    private val simpleName: String,
    private val type: DocType,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName)
        b.sep(": ")
        type.genCode(b)
        return b.build()
    }
}

class DocDeclaration_Variable(
    private val simpleName: R_Name,
    private val type: DocType,
    private val isMutable: Boolean,
): DocDeclaration() {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.keyword(if (isMutable) "var" else "val")
        b.raw(" ")
        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)
        return b.build()
    }
}

sealed class DocModifier {
    abstract fun genCode(b: DocCode.Builder)

    companion object {
        val PURE: DocModifier = DocModifier_Keyword("pure")
        val STATIC: DocModifier = DocModifier_Keyword("static")

        val DEPRECATED: DocModifier = DocModifier_Annotation(R_Name.of("deprecated"), immListOf())

        private val DEPRECATED_ERROR: DocModifier = DocModifier_Annotation(
            R_Name.of("deprecated"),
            immListOf(DocAnnotationArg.makeRaw("ERROR")),
        )

        fun deprecated(error: Boolean): DocModifier = if (error) DEPRECATED_ERROR else DEPRECATED
    }
}

class DocModifier_Keyword(private val kw: String): DocModifier() {
    override fun genCode(b: DocCode.Builder) {
        b.keyword(kw)
        b.raw(" ")
    }
}

sealed class DocAnnotationArg {
    abstract fun genCode(b: DocCode.Builder)

    companion object {
        fun makeRaw(s: String): DocAnnotationArg = DocAnnotationArg_Raw(s)
        fun makeName(qualifiedName: R_QualifiedName): DocAnnotationArg = DocAnnotationArg_Name(qualifiedName)
        fun makeValue(value: DocValue?): DocAnnotationArg = DocAnnotationArg_Value(value)
    }
}

private class DocAnnotationArg_Raw(private val s: String): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(s)
    }
}

private class DocAnnotationArg_Name(private val qualifiedName: R_QualifiedName): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        b.link(qualifiedName.str())
    }
}

private class DocAnnotationArg_Value(private val value: DocValue?): DocAnnotationArg() {
    override fun genCode(b: DocCode.Builder) {
        if (value == null) {
            DocExpr.UNKNOWN.genCode(b)
        } else {
            value.genCode(b)
        }
    }
}

class DocModifier_Annotation(
    private val simpleName: R_Name,
    private val args: List<DocAnnotationArg>,
): DocModifier() {
    override fun genCode(b: DocCode.Builder) {
        b.raw("@")
        b.raw(simpleName.str)

        if (args.isNotEmpty()) {
            b.raw("(")
            for ((i, arg) in args.withIndex()) {
                if (i > 0) b.sep(", ")
                arg.genCode(b)
            }
            b.raw(")")
        }

        b.newline()
    }
}

class DocModifiers private constructor(
    private val modifiers: List<DocModifier>,
) {
    fun isDeprecated(): Boolean = DocModifier.DEPRECATED in modifiers

    fun appendTo(b: DocCode.Builder) {
        for (mod in modifiers) {
            mod.genCode(b)
        }
    }

    companion object {
        val NONE: DocModifiers = DocModifiers(immListOf())

        fun make(vararg modifiers: DocModifier?): DocModifiers {
            val list = modifiers.filterNotNull().toImmList()
            return make(list)
        }

        fun make(modifiers: List<DocModifier>): DocModifiers {
            return if (modifiers.isEmpty()) NONE else DocModifiers(modifiers)
        }
    }
}

private object DocDecUtils {
    fun appendModuleName(b: DocCode.Builder, moduleName: R_ModuleName) {
        val moduleStr = if (moduleName.isEmpty()) "''" else moduleName.str()
        b.link(moduleStr)
    }

    fun appendTypeParams(b: DocCode.Builder, typeParams: List<DocTypeParam>) {
        if (typeParams.isEmpty()) {
            return
        }

        b.raw("<")

        for ((i, param) in typeParams.withIndex()) {
            if (i > 0) b.sep(", ")
            b.raw(param.name)
            if (param.bounds != DocTypeSet.ALL) {
                b.sep(": ")
                param.bounds.genCode(b)
            }
        }

        b.raw(">")
    }

    fun appendDeprecated(b: DocCode.Builder, deprecated: C_Deprecated?) {
        if (deprecated != null) {
            b.raw("@deprecated")
            if (deprecated.error) {
                b.raw("(ERROR)")
            }
            b.newline()
        }
    }

    fun appendFunctionParams(b: DocCode.Builder, params: List<DocDeclaration>) {
        b.raw("(")

        var sep = ""
        for (param in params) {
            b.sep(sep)
            b.newline()
            b.tab()
            b.append(param.code)
            sep = ","
        }

        if (params.isNotEmpty()) {
            b.newline()
        }

        b.raw(")")
    }

    fun completion(
        type: DocType? = null,
        params: Map<String, DocDeclaration>? = null,
    ): DocDeclaration.Completion {
        val ideParams = params?.map { (name, param) ->
            val code = C_IdeCompletionsUtils.docCodeToStr(param.code)
            IdeCompletionParam(name, code)
        }

        val typeStr = if (type == null) null else {
            val typeCode = type.toCode()
            C_IdeCompletionsUtils.docCodeToStr(typeCode)
        }

        return DocDeclaration.Completion(ideParams, typeStr)
    }
}
