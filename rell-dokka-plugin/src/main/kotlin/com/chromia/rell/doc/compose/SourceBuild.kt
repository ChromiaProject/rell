/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:OptIn(InternalRellApi::class)

package com.chromia.rell.doc.compose

import com.chromia.rell.doc.model.*
import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.InternalRellApi
import net.postchain.rell.api.base.RellApiBaseInternal
import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_RRConstantValueExpr
import net.postchain.rell.base.runtime.rrConstantToRtValue
import net.postchain.rell.base.runtime.rtValueToGtv
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import java.nio.file.Path

/**
 * Runs the Rell compiler over a project's source tree and projects the resulting `R_Module`
 * graph into a `Doc_Module`. The compiler is driven via `RellApiBaseInternal.compileApp` with
 * `docSymbolsEnabled(true)` so that every definition carries its `DocSymbol` (and the doc
 * comments survive the compile).
 *
 * The site-level model wraps a single `Doc_Module` per CLI invocation (titled with the dapp
 * title); within that module each compiled `R_Module` becomes its own `Doc_Package`, and any
 * Rell `namespace { ... }` block inside it becomes a sibling `Doc_Package` keyed under the
 * dotted prefix (e.g. module `lib.lib1` with a `namespace nested { ... }` produces packages
 * `lib.lib1` and `lib.lib1.nested`).
 */
internal class SourceBuild private constructor(
    private val moduleDocs: ModuleDocs?,
    private val analysis: Analysis,
) {

    /**
     * Holds the side outputs of `RellApiBaseInternal.compileApp` so the builder can resolve
     * cross-references (`@extend(target)`) and identify extendable functions.
     */
    class Analysis(
        val modules: List<R_Module>,
        val testModules: List<R_Module>,
        val extensionFunctionsByModule: Map<String, List<ExtensionFunction>>,
        val extendableFunctionAppLevelNames: Set<String>,
        val functionAppLevelNameToQname: Map<String, String>,
        val extensionFunctionAppLevelNames: Set<String>,
    )

    /** Lightweight projection of `R_FunctionExtensionsTable` entries (which live deep in `rell-base`). */
    data class ExtensionFunction(
        val targetAppLevelName: String,
        val module: String,
        val qualifiedName: String,
        val simpleName: String,
        val params: List<R_FunctionParam>,
        val resultType: R_Type,
        val docSourcePos: net.postchain.rell.base.utils.doc.DocSourcePos?,
        val docSymbol: net.postchain.rell.base.utils.doc.DocSymbol,
        val anonymous: Boolean,
    )

    private fun buildModule(rModules: List<R_Module>): List<Doc_Package> {
        val packages = mutableListOf<Doc_Package>()
        for (rm in rModules) packages += buildOnePackage(rm)
        return packages
    }

    private fun buildOnePackage(rModule: R_Module): List<Doc_Package> {
        val moduleName = rModule.name.str()
        val rootDefs = mutableListOf<Doc_Def>()
        val nested = LinkedHashMap<String, MutableList<Doc_Def>>()

        fun routeDef(localName: String, def: Doc_Def) {
            val dot = localName.indexOf('.')
            if (dot < 0) rootDefs.add(def)
            else nested.computeIfAbsent("$moduleName.${localName.substringBeforeLast('.')}") { mutableListOf() }.add(def)
        }

        for ((local, d) in rModule.constants) routeDef(local, constantToDoc(d, moduleName))
        for ((local, d) in rModule.entities) routeDef(local, entityToClass(d, moduleName))
        for ((local, d) in rModule.structs) routeDef(local, structToClass(d, moduleName))
        for ((local, d) in rModule.objects) routeDef(local, objectToClass(d, moduleName))
        for ((local, d) in rModule.enums) routeDef(local, enumToClass(d, moduleName))
        for ((local, d) in rModule.functions) {
            val doc = functionToDoc(d, moduleName) ?: continue
            routeDef(local, doc)
        }
        for ((local, d) in rModule.operations) routeDef(local, operationToDoc(d, moduleName))
        for ((local, d) in rModule.queries) routeDef(local, queryToDoc(d, moduleName))

        for (ext in analysis.extensionFunctionsByModule[moduleName].orEmpty()) {
            val doc = extensionFunctionToDoc(ext) ?: continue
            val localPart = ext.qualifiedName
            routeDef(localPart, doc)
        }

        val base = Doc_Package(qname = moduleName, docMd = rModule.docSymbol.markdown(), defs = rootDefs)

        val result = mutableListOf<Doc_Package>()
        result += attachModuleDoc(base)
        for ((qname, defs) in nested) {
            result += attachModuleDoc(Doc_Package(qname, docMd = "", defs = defs))
        }
        return result
    }

    private fun attachModuleDoc(pkg: Doc_Package): Doc_Package {
        val doc = moduleDocs?.packageDoc(pkg.qname) ?: return pkg
        return pkg.copy(docMd = if (pkg.docMd.isBlank()) doc else "${pkg.docMd}\n\n$doc")
    }

    // ─── R_* → Doc_* projections ─────────────────────────────────────────────

    private fun constantToDoc(d: R_GlobalConstantDefinition, moduleName: String): Doc_Property {
        val body = d.bodyGetter.get()
        val defaultText = body.value?.let { rrConstantToRtValue(it) }?.let { rt ->
            val gtv = if (body.type.completeFlags().gtv.toGtv) rtValueToGtv(body.type, rt, pretty = true) else null
            gtv?.toRenderText() ?: rt.toString()
        }
        return Doc_Property(
            name = d.simpleName,
            qname = qualifyDef(moduleName, d),
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            type = body.type.toDocType(),
            defaultValueText = defaultText,
        )
    }

    private fun entityToClass(d: R_EntityDefinition, moduleName: String): Doc_Class {
        val qname = qualifyDef(moduleName, d)
        val members = d.attributes.map { (_, attr) -> attrToProperty(attr, qname) }
        return Doc_Class(
            name = d.simpleName,
            qname = qname,
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_ClassKind.ENTITY,
            typeParams = emptyList(),
            superTypes = emptyList(),
            members = members,
        )
    }

    private fun structToClass(d: R_StructDefinition, moduleName: String): Doc_Class {
        val qname = qualifyDef(moduleName, d)
        val members = d.struct.attributes.map { (_, attr) -> attrToProperty(attr, qname) }
        return Doc_Class(
            name = d.simpleName,
            qname = qname,
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_ClassKind.STRUCT,
            typeParams = emptyList(),
            superTypes = emptyList(),
            members = members,
        )
    }

    private fun objectToClass(d: R_ObjectDefinition, moduleName: String): Doc_Class {
        val qname = qualifyDef(moduleName, d)
        val members = d.rEntity.attributes.map { (_, attr) -> attrToProperty(attr, qname) }
        return Doc_Class(
            name = d.simpleName,
            qname = qname,
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_ClassKind.OBJECT,
            typeParams = emptyList(),
            superTypes = emptyList(),
            members = members,
        )
    }

    private fun enumToClass(d: R_EnumDefinition, moduleName: String): Doc_Class {
        val qname = qualifyDef(moduleName, d)
        return Doc_Class(
            name = d.simpleName,
            qname = qname,
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_ClassKind.ENUM,
            typeParams = emptyList(),
            superTypes = emptyList(),
            members = emptyList(),
            entries = d.attrs.map { it.name },
        )
    }

    private fun attrToProperty(attr: R_Attribute, parentQname: String): Doc_Property {
        val defaultGtv = attr.expr?.constantValueOrNull()?.let { rrConstantToRtValue(it) }?.let { rt ->
            if (attr.type.completeFlags().gtv.toGtv) rtValueToGtv(attr.type, rt, pretty = true) else null
        }
        return Doc_Property(
            name = attr.name,
            qname = "$parentQname.${attr.name}",
            docMd = attr.docSymbol.markdown(),
            source = null,
            deprecated = null,
            type = attr.type.toDocType(),
            mutable = attr.mutable,
            key = attr.keyIndexKind == KeyIndexKind.KEY,
            index = attr.keyIndexKind == KeyIndexKind.INDEX,
            defaultValueText = defaultGtv?.toRenderText(),
        )
    }

    private fun functionToDoc(d: R_FunctionDefinition, moduleName: String): Doc_Function? {
        // `@extendable` functions document themselves; concrete `@extend(target)` functions hide
        // unless they happen to also be `@extendable` (chained extensions).
        val isExtendable = d.appLevelName in analysis.extendableFunctionAppLevelNames
        if (!isExtendable && d.appLevelName in analysis.extensionFunctionAppLevelNames) return null

        val header = d.fnBase.getHeader()
        return Doc_Function(
            name = d.simpleName,
            qname = qualifyDef(moduleName, d),
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_FunctionKind.FUNCTION,
            params = header.params.map { it.toDocParam() },
            returnType = header.type.takeUnless { it is R_UnitType }?.toDocType(),
            typeParams = emptyList(),
            extendable = isExtendable,
        )
    }

    private fun operationToDoc(d: R_OperationDefinition, moduleName: String): Doc_Function =
        Doc_Function(
            name = d.simpleName,
            qname = qualifyDef(moduleName, d),
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_FunctionKind.OPERATION,
            params = d.params().map { it.toDocParam() },
            returnType = null,
            typeParams = emptyList(),
            mountName = mountNameIfDistinct(d),
        )

    private fun queryToDoc(d: R_QueryDefinition, moduleName: String): Doc_Function =
        Doc_Function(
            name = d.simpleName,
            qname = qualifyDef(moduleName, d),
            docMd = d.docSymbol.markdown(),
            source = d.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_FunctionKind.QUERY,
            params = d.params().map { it.toDocParam() },
            returnType = d.type().toDocType(),
            typeParams = emptyList(),
            mountName = mountNameIfDistinct(d),
        )

    private fun mountNameIfDistinct(d: R_MountedRoutineDefinition): String? {
        val m = d.mountName.str()
        return if (m == d.simpleName) null else m
    }

    private fun extensionFunctionToDoc(ext: ExtensionFunction): Doc_Function? {
        val targetQname = analysis.functionAppLevelNameToQname[ext.targetAppLevelName] ?: return null
        return Doc_Function(
            name = ext.simpleName,
            qname = "${ext.module}.${ext.qualifiedName}",
            docMd = ext.docSymbol.markdown(),
            source = ext.docSourcePos.toDocSource(),
            deprecated = null,
            kind = Doc_FunctionKind.FUNCTION,
            params = ext.params.map { it.toDocParam() },
            returnType = ext.resultType.takeUnless { it is R_UnitType }?.toDocType(),
            typeParams = emptyList(),
            extendTargetQname = targetQname,
            anonymous = ext.anonymous,
        )
    }

    private fun R_FunctionParam.toDocParam(): Doc_Param = Doc_Param(
        name = name.str,
        type = type.toDocType(),
        docMd = docSymbol.markdown(),
    )

    private fun qualifyDef(moduleName: String, d: R_Definition): String {
        val qn = d.defName.qualifiedName
        return if (qn.isEmpty()) moduleName else "$moduleName.$qn"
    }

    private fun net.postchain.rell.base.utils.doc.DocSourcePos?.toDocSource(): Doc_Source? =
        this?.let { Doc_Source(path = it.path, line = it.line) }

    private fun R_Expr.constantValueOrNull(): net.postchain.rell.base.model.rr.RR_ConstantValue? =
        if (this is R_RRConstantValueExpr) rrValue else null

    private fun Gtv.toRenderText(): String = toString()

    companion object {
        fun build(
            title: String,
            slug: String,
            projectRoot: Path,
            entryPointModules: List<String>,
            additionalModules: List<String>,
            moduleDocs: ModuleDocs?,
            cliEnv: RellCliEnv?,
        ): Doc_Module {
            val analysis = compile(projectRoot, entryPointModules, additionalModules, cliEnv)
            val builder = SourceBuild(moduleDocs, analysis)
            val packages = builder.buildModule(analysis.modules + analysis.testModules)
            val titleDoc = moduleDocs?.moduleDoc(title) ?: ""
            return Doc_Module(name = title, slug = slug, docMd = titleDoc, packages = packages, system = false)
        }

        fun compile(
            projectRoot: Path,
            entryPointModules: List<String>,
            additionalModules: List<String>,
            cliEnv: RellCliEnv?,
        ): Analysis {
            val configBuilder = RellApiCompile.Config.Builder().apply {
                mountConflictError(false)
                includeTestSubModules(true)
                moduleArgsMissingError(false)
                docSymbolsEnabled(true)
                appModuleInTestsError(false)
                if (cliEnv != null) cliEnv(cliEnv)
            }
            val config = configBuilder.build()
            val options = RellApiBaseInternal.makeCompilerOptions(config)
            val moduleNames = (entryPointModules + additionalModules).distinct().mapToImmList { ModuleName.of(it) }
            val (apiRes, _) = RellApiBaseInternal.compileApp(
                config,
                options,
                C_SourceDir.diskDir(projectRoot.toFile()),
                moduleNames,
                immListOf(),
            )
            val app = checkNotNull(apiRes.cRes.app) { "Rell compilation failed" }
            val modules = app.modules.filterNot { it.test }
            val testModules = app.modules.filter { it.test }

            val extByTarget = app.functionExtensions.list.associate { e -> e.uid.name to e.extensions }
            val extensions = extByTarget.flatMap { (target, list) ->
                list.map { ext ->
                    val header = ext.fnBase.getHeader()
                    val defName = ext.fnBase.defName
                    ExtensionFunction(
                        targetAppLevelName = target,
                        module = defName.module,
                        qualifiedName = defName.qualifiedName,
                        simpleName = defName.simpleName,
                        params = header.params,
                        resultType = header.type,
                        docSourcePos = null,
                        docSymbol = net.postchain.rell.base.utils.doc.DocSymbol.NONE,
                        anonymous = defName.simpleName.startsWith("function#"),
                    )
                }
            }
            val extByModule = extensions.groupBy { it.module }
            val functionByAppLevelName: Map<String, R_FunctionDefinition> = app.modules
                .flatMap { it.functions.values }
                .associateBy { it.appLevelName }
            val functionAppLevelNameToQname = functionByAppLevelName.mapValues { (_, fn) ->
                val mn = fn.defName.module
                val qn = fn.defName.qualifiedName
                if (qn.isEmpty()) mn else "$mn.$qn"
            }
            val extensionAppLevelNames = extensions.map { "${it.module}:${it.qualifiedName}" }.toSet()

            return Analysis(
                modules = modules,
                testModules = testModules,
                extensionFunctionsByModule = extByModule,
                extendableFunctionAppLevelNames = extByTarget.keys,
                functionAppLevelNameToQname = functionAppLevelNameToQname,
                extensionFunctionAppLevelNames = extensionAppLevelNames,
            )
        }


    }
}
