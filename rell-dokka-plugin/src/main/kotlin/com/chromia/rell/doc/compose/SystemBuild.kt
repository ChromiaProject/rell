/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.doc.compose

import com.chromia.rell.doc.model.*
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.mtype.M_TypeParam

/**
 * Builds a `Doc_Module` from the in-process Rell standard library (`Lib_Rell.MODULE` +
 * `Lib_RellTest.MODULE`). No source files are read — we walk the `L_NamespaceMember` graph the
 * compiler already exposes.
 *
 * Blacklisted leaves (`guid`, `signer`, `tuid`) are filtered to match the historical Dokka-plugin
 * output. Empty namespaces are dropped: they have no defs to render.
 */
internal object SystemBuild {
    private val BLACKLISTED_TYPES = setOf("guid", "signer")
    private val BLACKLISTED_ALIASES = setOf("tuid")

    fun build(title: String, slug: String, moduleDocs: ModuleDocs?): Doc_Module {
        // Flat bucket: parent-qname → defs. The walk recursively descends into nested
        // namespaces, but every def lands in *its own* namespace's bucket (so e.g.
        // `rell.test.keypairs.alice` ends up in the "rell.test.keypairs" bucket, not the
        // "rell.test" bucket). Both `Lib_Rell` and `Lib_RellTest` write to the same buckets so
        // contributions from both libs to the same namespace merge.
        val buckets = LinkedHashMap<String, MutableList<Doc_Def>>()
        walkLib(Lib_Rell.MODULE.lModule.namespace, parentQname = "", buckets = buckets, includeAliases = true)
        walkLib(Lib_RellTest.MODULE.lModule.namespace, parentQname = "", buckets = buckets, includeAliases = false)

        val rootDoc = moduleDocs?.moduleDoc(title) ?: ""

        val packages = buckets.entries
            .filter { it.value.isNotEmpty() }
            .map { (qname, defs) ->
                val mdName = qname.ifEmpty { "[root]" }
                val docMd = moduleDocs?.packageDoc(mdName)?.takeUnless { it.isBlank() } ?: ""
                Doc_Package(qname = qname, docMd = docMd, defs = defs.toList())
            }

        return Doc_Module(
            name = title,
            slug = slug,
            docMd = rootDoc,
            packages = packages,
            system = true,
        )
    }

    private fun walkLib(
        ns: net.postchain.rell.base.lmodel.L_Namespace,
        parentQname: String,
        buckets: MutableMap<String, MutableList<Doc_Def>>,
        includeAliases: Boolean,
    ) {
        fun bucketFor(qn: String): MutableList<Doc_Def> = buckets.computeIfAbsent(qn) { mutableListOf() }

        for (member in ns.members) {
            when (member) {
                is L_NamespaceMember_Type -> if (member.simpleName.str !in BLACKLISTED_TYPES) {
                    bucketFor(parentQname).add(typeToClass(member, parentQname))
                }
                is L_NamespaceMember_Struct -> bucketFor(parentQname).add(structToClass(member, parentQname))
                is L_NamespaceMember_Function -> bucketFor(parentQname).add(functionToDoc(member, parentQname))
                is L_NamespaceMember_SpecialFunction -> specialFunctionToDoc(member, parentQname)?.let { bucketFor(parentQname).add(it) }
                is L_NamespaceMember_Property -> bucketFor(parentQname).add(propertyToDoc(member, parentQname))
                is L_NamespaceMember_Constant -> bucketFor(parentQname).add(constantToDoc(member, parentQname))
                is L_NamespaceMember_Alias -> if (includeAliases && member.simpleName.str !in BLACKLISTED_ALIASES) {
                    aliasToDoc(member, parentQname)?.let { bucketFor(parentQname).add(it) }
                }
                is L_NamespaceMember_Namespace -> {
                    val childQname = member.qualifiedName.str()
                    walkLib(member.namespace, parentQname = childQname, buckets = buckets, includeAliases = includeAliases)
                }
                else -> Unit  // L_NamespaceMember_TypeExtension and friends are not rendered.
            }
        }
    }

    // ─── L_* → Doc_* projections ─────────────────────────────────────────────

    private fun typeToClass(t: L_NamespaceMember_Type, parentQname: String): Doc_Class {
        val qname = joinQname(parentQname, t.simpleName.str)
        val typeDef = t.typeDef
        val typeParams = typeDef.mGenericType.params.map { it.toDocTypeParam() }
        val superTypes = listOfNotNull(typeDef.mGenericType.parent?.let { p ->
            Doc_Type.Named(
                text = p.genericType.name,
                qname = p.genericType.name.replace(':', '.'),
                args = p.args.map { Doc_Type.Arg.Invariant(it.toDocType()) },
            )
        })
        val members = mutableListOf<Doc_Def>()
        for (m in typeDef.allMembers.all) {
            when (m) {
                is L_TypeDefMember_Constructor -> members.add(constructorToDoc(m.constructor, qname))
                is L_TypeDefMember_SpecialConstructor -> members.add(specialConstructorToDoc(m, qname))
                is L_TypeDefMember_Function -> members.add(typeFunctionToDoc(m, qname))
                is L_TypeDefMember_Property -> members.add(typePropertyToDoc(m, qname))
                is L_TypeDefMember_Constant -> members.add(typeConstantToDoc(m, qname))
                is L_TypeDefMember_Alias -> typeAliasMember(m, qname)?.let(members::add)
                else -> Unit
            }
        }
        return Doc_Class(
            name = t.simpleName.str,
            qname = qname,
            docMd = t.docSymbol.markdown(),
            source = null,
            deprecated = t.deprecated?.toDocDeprecated(),
            kind = Doc_ClassKind.TYPE,
            typeParams = typeParams,
            superTypes = superTypes,
            members = members,
            hidden = typeDef.hidden,
            abstract = typeDef.abstract,
        )
    }

    private fun structToClass(s: L_NamespaceMember_Struct, parentQname: String): Doc_Class {
        val qname = joinQname(parentQname, s.simpleName.str)
        val members = s.struct.rStruct.attributes.map { (name, attr) ->
            Doc_Property(
                name = name.str,
                qname = joinQname(qname, name.str),
                docMd = attr.docSymbol.markdown(),
                source = null,
                deprecated = null,
                type = attr.type.toDocType(),
            )
        }
        return Doc_Class(
            name = s.simpleName.str,
            qname = qname,
            docMd = s.docSymbol.markdown(),
            source = null,
            deprecated = null,
            kind = Doc_ClassKind.STRUCT,
            typeParams = emptyList(),
            superTypes = emptyList(),
            members = members,
        )
    }

    private fun functionToDoc(f: L_NamespaceMember_Function, parentQname: String): Doc_Function =
        functionCommon(
            simpleName = f.simpleName.str,
            qname = joinQname(parentQname, f.simpleName.str),
            function = f.function,
            docSymbol = f.docSymbol,
            deprecated = f.deprecated,
        )

    private fun specialFunctionToDoc(f: L_NamespaceMember_SpecialFunction, parentQname: String): Doc_Function? {
        // Faithful to the previous Dokka plugin: only "exists" / "empty" are documented,
        // with a hand-written `(arg: T?) -> boolean` signature. Other special functions are
        // skipped (the old code emitted TODO()).
        if (f.simpleName.str !in setOf("exists", "empty")) return null

        return Doc_Function(
            name = f.simpleName.str,
            qname = joinQname(parentQname, f.simpleName.str),
            docMd = f.docSymbol.markdown(),
            source = null,
            deprecated = null,
            kind = Doc_FunctionKind.FUNCTION,
            params = listOf(
                Doc_Param(
                    name = "arg",
                    type = Doc_Type.Nullable(Doc_Type.TypeParam("T")),
                    docMd = "",
                ),
            ),
            returnType = Doc_Type.Named(text = "boolean", qname = "boolean"),
            typeParams = listOf(Doc_TypeParam("T")),
        )
    }

    private fun propertyToDoc(p: L_NamespaceMember_Property, parentQname: String): Doc_Property =
        Doc_Property(
            name = p.simpleName.str,
            qname = joinQname(parentQname, p.simpleName.str),
            docMd = p.docSymbol.markdown(),
            source = null,
            deprecated = null,
            type = p.property.type.toDocType(),
        )

    private fun constantToDoc(c: L_NamespaceMember_Constant, parentQname: String): Doc_Property =
        Doc_Property(
            name = c.simpleName.str,
            qname = joinQname(parentQname, c.simpleName.str),
            docMd = c.docSymbol.markdown(),
            source = null,
            deprecated = null,
            type = c.constant.type.toDocType(),
            defaultValueText = c.constant.docSource.toLiteralText(),
        )

    private fun aliasToDoc(a: L_NamespaceMember_Alias, parentQname: String): Doc_Def? =
        when (val target = a.finalTargetMember) {
            is L_NamespaceMember_Function -> {
                val qname = joinQname(parentQname, a.simpleName.str)
                val targetQname = target.fullName.qualifiedName.str()
                val extraSuffix = "**Alias of** [$targetQname]"
                val baseFn = functionCommon(
                    simpleName = a.simpleName.str,
                    qname = qname,
                    function = target.function,
                    docSymbol = target.docSymbol,
                    deprecated = a.deprecated ?: target.deprecated,
                    docExtraSuffix = extraSuffix,
                )
                baseFn.copy(aliasOfQname = targetQname)
            }
            is L_NamespaceMember_Type -> Doc_TypeAlias(
                name = a.simpleName.str,
                qname = joinQname(parentQname, a.simpleName.str),
                docMd = target.docSymbol.markdown("**Alias of** [${target.fullName.qualifiedName.str()}]"),
                source = null,
                deprecated = a.deprecated?.toDocDeprecated(),
                targetQname = target.fullName.qualifiedName.str(),
            )
            else -> null
        }

    private fun constructorToDoc(c: L_Constructor, parentQname: String): Doc_Function {
        val targetSimple = parentQname.substringAfterLast('.', parentQname)
        return Doc_Function(
            name = targetSimple,
            qname = joinQname(parentQname, targetSimple),
            docMd = "",
            source = null,
            deprecated = c.deprecated?.toDocDeprecated(),
            kind = Doc_FunctionKind.CONSTRUCTOR,
            params = c.header.params.map { it.toDocParam() },
            returnType = null,
            typeParams = c.header.typeParams.map { it.toDocTypeParam() },
            pure = c.pure,
        )
    }

    private fun specialConstructorToDoc(c: L_TypeDefMember_SpecialConstructor, parentQname: String): Doc_Function {
        val targetSimple = parentQname.substringAfterLast('.', parentQname)
        return Doc_Function(
            name = targetSimple,
            qname = joinQname(parentQname, targetSimple),
            docMd = c.docSymbol.markdown(),
            source = null,
            deprecated = null,
            kind = Doc_FunctionKind.SPECIAL_CONSTRUCTOR,
            params = listOf(
                Doc_Param(name = "type", type = Doc_Type.TypeParam("T"), docMd = ""),
            ),
            returnType = null,
            typeParams = listOf(Doc_TypeParam("T")),
        )
    }

    private fun typeFunctionToDoc(f: L_TypeDefMember_Function, parentQname: String): Doc_Function =
        functionCommon(
            simpleName = f.simpleName.str,
            qname = joinQname(parentQname, f.simpleName.str),
            function = f.function,
            docSymbol = f.docSymbol,
            deprecated = f.deprecated,
        ).copy(static = f.function.flags.isStatic, pure = f.function.flags.isPure)

    private fun typePropertyToDoc(p: L_TypeDefMember_Property, parentQname: String): Doc_Property =
        Doc_Property(
            name = p.simpleName.str,
            qname = joinQname(parentQname, p.simpleName.str),
            docMd = p.docSymbol.markdown(),
            source = null,
            deprecated = null,
            type = p.property.type.toDocType(),
        )

    private fun typeConstantToDoc(c: L_TypeDefMember_Constant, parentQname: String): Doc_Property =
        Doc_Property(
            name = c.constant.simpleName.str,
            qname = joinQname(parentQname, c.constant.simpleName.str),
            docMd = c.docSymbol.markdown(),
            source = null,
            deprecated = null,
            type = c.constant.type.toDocType(),
            defaultValueText = c.constant.docSource.toLiteralText(),
        )

    private fun typeAliasMember(a: L_TypeDefMember_Alias, parentQname: String): Doc_Def? =
        when (val target = a.targetMember) {
            is L_TypeDefMember_Function -> {
                val qname = joinQname(parentQname, a.simpleName.str)
                val targetQname = joinQname(parentQname, target.simpleName.str)
                val baseFn = functionCommon(
                    simpleName = a.simpleName.str,
                    qname = qname,
                    function = target.function,
                    docSymbol = target.docSymbol,
                    deprecated = a.deprecated ?: target.deprecated,
                    docExtraSuffix = "**Alias of** [$targetQname]",
                )
                baseFn.copy(
                    aliasOfQname = targetQname,
                    static = target.function.flags.isStatic,
                    pure = target.function.flags.isPure,
                )
            }
            else -> null
        }

    private fun functionCommon(
        simpleName: String,
        qname: String,
        function: L_Function,
        docSymbol: net.postchain.rell.base.utils.doc.DocSymbol,
        deprecated: C_Deprecated?,
        docExtraSuffix: String? = null,
    ): Doc_Function {
        val params = function.header.params.map { it.toDocParam() }
        val typeParams = function.header.typeParams.map { it.toDocTypeParam() }
        val returnType = function.header.resultType.toDocType()
        return Doc_Function(
            name = simpleName,
            qname = qname,
            docMd = docSymbol.markdown(docExtraSuffix),
            source = null,
            deprecated = deprecated?.toDocDeprecated(),
            kind = Doc_FunctionKind.FUNCTION,
            params = params,
            returnType = returnType,
            typeParams = typeParams,
            pure = function.flags.isPure,
            static = function.flags.isStatic,
        )
    }

    private fun L_FunctionParam.toDocParam(): Doc_Param = Doc_Param(
        name = name.str,
        type = type.toDocType(),
        docMd = docSymbol.markdown(),
        zeroOne = arity == M_ParamArity.ZERO_ONE,
        vararg = arity.many,
    )

    private fun M_TypeParam.toDocTypeParam(): Doc_TypeParam = Doc_TypeParam(name = name)

    private fun joinQname(parent: String, simple: String): String =
        if (parent.isEmpty()) simple else "$parent.$simple"
}

/** Renders a stdlib constant's `L_ConstantDocSource` into the literal source text. */
private fun net.postchain.rell.base.lmodel.L_ConstantDocSource.toLiteralText(): String = when (val s = this) {
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Null -> "null"
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Unit -> "unit"
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Bool -> s.value.toString()
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Int -> s.value.toString()
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.BigInt -> s.value.toString()
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Decimal -> s.value.toString()
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Text -> "\"${s.value}\""
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Bytes -> bytesHex(s.value)
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Rowid -> "rowid(${s.value})"
    is net.postchain.rell.base.lmodel.L_ConstantDocSource.Complex -> s.fallbackStr
}

private fun bytesHex(bytes: ByteArray): String = buildString {
    append("x\"")
    bytes.forEach { append("%02X".format(it)) }
    append('"')
}
