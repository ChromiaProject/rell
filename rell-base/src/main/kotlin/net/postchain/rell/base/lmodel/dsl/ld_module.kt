/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.utils.C_RFullNamePath
import net.postchain.rell.base.lmodel.L_Module
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.doc.DocDeclarationProto_Module
import net.postchain.rell.base.utils.doc.DocModifiers
import net.postchain.rell.base.utils.doc.DocSymbolKind
import net.postchain.rell.base.utils.doc.DocSymbolName
import net.postchain.rell.base.utils.futures.FcManager
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

class Ld_ModuleDslImpl private constructor(
    private val moduleName: R_ModuleName,
    private val modCfg: Ld_ModuleConfig,
    private val nsBuilder: Ld_NamespaceBuilder,
): Ld_ModuleDsl, Ld_NamespaceBodyDsl by Ld_NamespaceBodyDslImpl(nsBuilder) {
    private val imports = mutableMapOf<R_ModuleName, L_Module>()
    private val allImports = mutableMapOf<R_ModuleName, L_Module>()

    private var finished = false

    override fun imports(module: L_Module) {
        check(!finished)

        checkImport(module)

        for (module2 in module.allImports) {
            checkImport(module2)
        }

        if (module.moduleName !in imports) {
            imports[module.moduleName] = module
        }

        if (module.moduleName !in allImports) {
            allImports[module.moduleName] = module
        }

        for (module2 in module.allImports) {
            if (module2.moduleName !in allImports) {
                allImports[module2.moduleName] = module2
            }
        }
    }

    private fun checkImport(module: L_Module) {
        val modName = module.moduleName
        val oldModule = allImports[modName]
        Ld_Exception.check(oldModule == null || oldModule === module) {
            "import_module_conflict:$modName" to "Different imported modules with same name: [$modName]"
        }
    }

    private fun build(): L_Module {
        check(!finished)
        finished = true

        val ns = nsBuilder.build()
        val resImports = imports.toImmMap()

        val fcManager = FcManager.create()

        val finCtxP = fcManager.promise<Ld_NamespaceFinishContext>()

        val nsF = fcManager.future().delegate {
            val modCtx = Ld_ModuleContext(moduleName, modCfg, fcManager, finCtxP.future())
            val nsCtx = Ld_NamespaceContext(modCtx, C_RFullNamePath.of(moduleName))
            val nsF = ns.process(nsCtx)

            val finCtx = modCtx.finish(resImports)
            finCtxP.setResult(finCtx)
            nsF
        }

        fcManager.finish()

        val lNs = nsF.getResult().ns

        val doc = Ld_DocSymbols.docSymbol(
            kind = DocSymbolKind.MODULE,
            symbolName = DocSymbolName.module(moduleName.str()),
            declaration = DocDeclarationProto_Module(DocModifiers.NONE).toLazyDeclaration(),
            comment = null,
        )

        return L_Module(
            moduleName = moduleName,
            namespace = lNs,
            allImports = allImports.values.sortedBy { it.moduleName }.toImmList(),
            docSymbol = doc,
        )
    }

    companion object {
        fun make(name: String, modCfg: Ld_ModuleConfig, block: Ld_ModuleDsl.() -> Unit): L_Module {
            val rModuleName = R_ModuleName.of(name)
            val nsBuilder = Ld_NamespaceBuilder()
            val dslBuilder = Ld_ModuleDslImpl(rModuleName, modCfg, nsBuilder)
            block(dslBuilder)
            return dslBuilder.build()
        }
    }
}
