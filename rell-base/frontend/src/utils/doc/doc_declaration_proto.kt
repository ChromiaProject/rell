/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocDeclaration.Completion
import net.postchain.rell.base.utils.ide.IdeCompletionParam

abstract class DocDeclarationProto {
    abstract fun makeDeclaration(): DocDeclaration

    fun toLazyDeclaration(): Lazy<DocDeclaration> = lazy { makeDeclaration() }

    companion object {
        val NONE: DocDeclarationProto = DocDeclarationProto_None
    }
}

private object DocDeclarationProto_None: DocDeclarationProto() {
    override fun makeDeclaration() = DocDeclaration.NONE
}

abstract class DocDeclarationProto_Base: DocDeclarationProto() {
    protected open fun isDeprecated(): Boolean = false
    protected open fun getCompletion0(): Completion? = null
    protected abstract fun getCode0(): DocCode

    final override fun makeDeclaration(): DocDeclaration {
        val code = getCode0()
        val completion = getCompletion0()
        val deprecated = isDeprecated()
        return DocDeclaration(code, completion, deprecated)
    }
}

abstract class DocDeclarationProto_Annotated(
    private val modifiers: DocModifiers,
): DocDeclarationProto_Base() {
    protected abstract fun genCode0(b: DocCode.Builder)

    final override fun getCode0(): DocCode {
        val b = DocCode.builder()
        modifiers.appendTo(b)
        genCode0(b)
        return b.build()
    }

    final override fun isDeprecated() = modifiers.isDeprecated()
}

class DocDeclarationProto_Module(
    modifiers: DocModifiers,
): DocDeclarationProto_Annotated(modifiers) {
    override fun genCode0(b: DocCode.Builder) {
        b.keyword("module")
    }
}

class DocDeclarationProto_ImportModule(
    modifiers: DocModifiers,
    private val moduleName: ModuleName,
    private val alias: Name?,
): DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_ImportWildcard(
    modifiers: DocModifiers,
    private val moduleName: ModuleName,
    private val aliasName: Name,
): DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_ImportExactModule(
    modifiers: DocModifiers,
    private val moduleName: ModuleName,
    private val aliasName: Name,
): DocDeclarationProto_Annotated(modifiers) {
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

sealed class DocDeclarationProto_ImportExactAlias(
    modifiers: DocModifiers,
    protected val moduleName: ModuleName,
    protected val qualifiedName: QualifiedName,
    private val namespaceAlias: Name?,
    protected val exactAliasName: Name,
): DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_ImportExactAlias_Single(
    modifiers: DocModifiers,
    moduleName: ModuleName,
    qualifiedName: QualifiedName,
    namespaceAlias: Name?,
    exactAliasName: Name,
    private val targetDeclaration: DocDeclaration,
): DocDeclarationProto_ImportExactAlias(modifiers, moduleName, qualifiedName, namespaceAlias, exactAliasName) {
    override val wildcard = false

    override fun getCompletion0(): Completion {
        val targetComp = targetDeclaration.completion
        val result = targetComp?.result ?: "${moduleName.str()}:${qualifiedName.str()}"
        return Completion(targetComp?.params, result)
    }
}

class DocDeclarationProto_ImportExactAlias_Wildcard(
    modifiers: DocModifiers,
    moduleName: ModuleName,
    qualifiedName: QualifiedName,
    namespaceAlias: Name?,
    exactAliasName: Name,
): DocDeclarationProto_ImportExactAlias(modifiers, moduleName, qualifiedName, namespaceAlias, exactAliasName) {
    override val wildcard = true

    override fun getCompletion0(): Completion {
        val result = "${moduleName.str()}:${qualifiedName.str()}.*"
        return Completion(null, result)
    }
}

class DocDeclarationProto_Namespace(
        modifiers: DocModifiers,
        private val simpleName: Name,
): DocDeclarationProto_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("namespace")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclarationProto_Constant(
        modifiers: DocModifiers,
        private val simpleName: Name,
        private val type: DocType,
        private val value: DocValue?,
) : DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_Property(
        private val simpleName: Name,
        private val type: DocType,
        private val pure: Boolean,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_SpecialProperty(
        private val simpleName: Name,
): DocDeclarationProto_Base() {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName.str)
        return b.build()
    }
}

class DocDeclarationProto_Enum(
        modifiers: DocModifiers,
        private val simpleName: Name,
): DocDeclarationProto_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("enum")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclarationProto_EnumValue(
        private val simpleName: Name,
        private val type: DocType,
): DocDeclarationProto_Base() {
    override fun getCompletion0() = DocDecUtils.completion(type)
    override fun getCode0() = DocCode.raw(simpleName.str)
}

class DocDeclarationProto_Entity(
        modifiers: DocModifiers,
        private val simpleName: Name,
): DocDeclarationProto_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("entity")
        b.sep(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclarationProto_EntityAttribute(
    private val simpleName: Name,
    private val type: DocType,
    private val isMutable: Boolean,
    private val keyIndexKind: KeyIndexKind?,
    private val expr: DocExpr? = null,
    private val keys: Collection<R_Key> = immListOf(),
    private val indices: Collection<R_Index> = immListOf(),
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_Object(
        modifiers: DocModifiers,
        private val simpleName: Name,
): DocDeclarationProto_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("object")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclarationProto_Struct(
        modifiers: DocModifiers,
        private val simpleName: Name,
): DocDeclarationProto_Annotated(modifiers) {
    override fun getCompletion0() = DocDecUtils.completion()

    override fun genCode0(b: DocCode.Builder) {
        b.keyword("struct")
        b.raw(" ")
        b.raw(simpleName.str)
    }
}

class DocDeclarationProto_StructAttribute(
        private val simpleName: Name,
        private val type: DocType,
        private val isMutable: Boolean,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_Parameter(
    private val param: DocFunctionParam,
    private val isLazy: Boolean,
    private val implies: L_ParamImplication?,
    private val expr: DocExpr?,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_Function(
        docModifiers: DocModifiers,
        private val simpleName: Name,
        private val header: DocFunctionHeader,
        private val params: ImmList<DocDeclaration>,
        private val hasBody: Boolean? = null,
): DocDeclarationProto_Annotated(docModifiers) {
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

class DocDeclarationProto_SpecialFunction(
        private val simpleName: Name,
        private val isStatic: Boolean,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_Operation(
        modifiers: DocModifiers,
        private val simpleName: Name,
        private val paramNames: ImmList<String>,
        private val params: ImmList<DocDeclaration>,
): DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_Query(
        modifiers: DocModifiers,
        private val simpleName: Name,
        private val resultType: DocType,
        private val paramNames: ImmList<String>,
        private val params: ImmList<DocDeclaration>,
): DocDeclarationProto_Annotated(modifiers) {
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

class DocDeclarationProto_Type(
        private val simpleName: Name,
        private val typeParams: ImmList<DocTypeParam>,
        private val lParent: L_TypeDefParent?,
        private val flags: L_TypeDefFlags,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_TypeConstructor(
        private val simpleName: Name,
        private val typeParams: ImmList<DocTypeParam>,
        private val paramNames: ImmList<String>,
        private val params: ImmList<DocDeclaration>,
        private val deprecated: C_Deprecated?,
        private val pure: Boolean,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_TypeSpecialConstructor: DocDeclarationProto_Base() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.keyword("constructor")
        b.raw("(...)")
        return b.build()
    }
}

class DocDeclarationProto_TypeExtension(
        private val simpleName: Name,
        private val typeParams: ImmList<DocTypeParam>,
        private val selfType: DocType,
): DocDeclarationProto_Base() {
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

class DocDeclarationProto_Alias(
    modifiers: DocModifiers,
    private val simpleName: Name,
    private val targetFullName: FullName,
    private val targetDeclaration: DocDeclaration,
    private val targetQualifiedName: QualifiedName = targetFullName.qualifiedName,
): DocDeclarationProto_Annotated(modifiers) {
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

        if (targetDeclaration != DocDeclaration.NONE) {
            b.newline()
            b.newline()
            b.append(targetDeclaration.code)
        }
    }
}

class DocDeclarationProto_TupleAttribute(
        private val simpleName: Name,
        private val type: DocType,
): DocDeclarationProto_Base() {
    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName.str)
        b.sep(": ")
        type.genCode(b)
        return b.build()
    }
}

class DocDeclarationProto_AtVariable(
    private val simpleName: String,
    private val type: DocType,
): DocDeclarationProto_Base() {
    override fun getCompletion0() = DocDecUtils.completion(type)

    override fun getCode0(): DocCode {
        val b = DocCode.builder()
        b.raw(simpleName)
        b.sep(": ")
        type.genCode(b)
        return b.build()
    }
}

class DocDeclarationProto_Variable(
        private val simpleName: Name,
        private val type: DocType,
        private val isMutable: Boolean,
): DocDeclarationProto_Base() {
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

private object DocDecUtils {
    fun appendModuleName(b: DocCode.Builder, moduleName: ModuleName) {
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
    ): Completion {
        val ideParams = params?.mapToImmList { (name, param) ->
            val code = C_IdeCompletionsUtils.docCodeToStr(param.code)
            IdeCompletionParam(name, code)
        }

        val typeStr = if (type == null) null else {
            val typeCode = type.toCode()
            C_IdeCompletionsUtils.docCodeToStr(typeCode)
        }

        return Completion(ideParams, typeStr)
    }
}
