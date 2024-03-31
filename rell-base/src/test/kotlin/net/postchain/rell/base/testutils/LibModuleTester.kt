/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lmodel.dsl.Ld_ModuleDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class LibModuleTester(
    private val tst: RellCodeTester,
    vararg importedModules: C_LibModule,
    private val moduleName: String = "mod",
) {
    private val importedModules = importedModules.toImmList()

    private var curModRef: AtomicReference<C_LibModule>? = null

    fun extraModule(block: Ld_ModuleDsl.() -> Unit) {
        check(curModRef == null)
        val modRefLoc = AtomicReference<C_LibModule>()
        curModRef = modRefLoc
        try {
            val mod = C_LibModule.make(moduleName, *importedModules.toTypedArray(), requireSince = false) {
                block(this)
            }
            tst.extraMod = mod
            modRefLoc.set(mod)
        } finally {
            curModRef = null
        }
    }

    fun setRTypeFactory(dsl: Ld_TypeDefDsl, typeName: String = dsl.typeSimpleName, genericCount: Int = 0) {
        val modRef = curModRef!!
        setRTypeFactory(dsl, modRef::get, typeName, genericCount)
    }

    companion object {
        fun setRTypeFactory(
            dsl: Ld_TypeDefDsl,
            modGetter: () -> C_LibModule,
            typeName: String = dsl.typeSimpleName,
            genericCount: Int = 0,
        ) {
            val typeTag = Any()
            dsl.rTypeFactory { args ->
                checkEquals(args.size, genericCount)
                R_TestType(typeName, typeTag, modGetter, args.toImmList())
            }
        }
    }

    private class R_TestType(
        private val typeName: String,
        private val typeTag: Any,
        private val modGetter: () -> C_LibModule,
        private val typeArgs: List<R_Type>,
    ): R_Type(typeName, C_DefinitionName("", typeName)) {
        override fun equals0(other: R_Type): Boolean {
            return other is R_TestType && typeTag === other.typeTag && typeArgs == other.typeArgs
        }

        override fun hashCode0(): Int {
            return Objects.hash(typeTag, typeArgs)
        }

        override fun strCode(): String {
            return if (typeArgs.isEmpty()) name else "$name<${typeArgs.joinToString(",") { it.strCode() }}>"
        }

        override fun toMetaGtv() = name.toGtv()
        override fun isReference() = true
        override fun isDirectPure() = false
        override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None

        override fun getLibType0(): C_LibType {
            val mod = modGetter()
            val typeDef = mod.getTypeDef(typeName)
            return C_LibType.make(typeDef, *typeArgs.toTypedArray())
        }
    }
}
