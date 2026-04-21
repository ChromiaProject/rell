/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.core

import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsOptions
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.base.utils.toImmMap

enum class C_AtAttrShadowing {
    FULL,
    PARTIAL,
    NONE,
    ;

    companion object {
        val DEFAULT = FULL
    }
}

data class C_CompilerOptions(
    val compatibility: R_LangVersion?,
    val gtv: Boolean,
    val deprecatedError: Boolean,
    val atAttrShadowing: C_AtAttrShadowing,
    val defaultLib: Boolean,
    val testLib: Boolean,
    val hiddenLib: Boolean,
    val allowDbModificationsInObjectExprs: Boolean,
    val symbolInfoFile: C_SourcePath?,
    val complexWhatEnabled: Boolean,
    val mountConflictError: Boolean,
    val appModuleInTestsError: Boolean,
    val useTestDependencyExtensions: Boolean,
    val allowLibNamedArgsAnyVersion: Boolean,
    val allowOlderCompatibilityVersion: Boolean,
    val ide: Boolean,
    val ideDocSymbolsEnabled: Boolean,
    val ideDefIdConflictError: Boolean,
    val ideCompletions: C_IdeCompletionsOptions?,
) {
    init {
        if (!allowOlderCompatibilityVersion) {
            RellVersions.checkCompatibilityVersion(compatibility) { IllegalArgumentException(it) }
        }
    }

    fun toBuilder() = Builder(this)

    fun toPojoMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "gtv" to gtv,
            "deprecatedError" to deprecatedError,
            "ide" to ide,
            "atAttrShadowing" to atAttrShadowing.name,
            "testLib" to testLib,
            "hiddenLib" to hiddenLib,
            "allowDbModificationsInObjectExprs" to allowDbModificationsInObjectExprs,
            "complexWhatEnabled" to complexWhatEnabled,
            "mountConflictError" to mountConflictError,
            "appModuleInTestsError" to appModuleInTestsError,
            "useTestDependencyExtensions" to useTestDependencyExtensions,
        )

        putNotDefault(map, "defaultLib") { it.defaultLib }
        putNotDefault(map, "allowLibNamedArgsAnyVersion") { it.allowLibNamedArgsAnyVersion }
        putNotDefault(map, "allowOlderCompatibilityVersion") { it.allowOlderCompatibilityVersion }

        putNotNull(map, "symbolInfoFile", symbolInfoFile?.str())
        putNotNull(map, "compatibility", compatibility?.str())
        putNotDefault(map, "ideDocSymbolsEnabled") { it.ideDocSymbolsEnabled }
        putNotDefault(map, "ideDefIdConflictError") { it.ideDefIdConflictError }
        putNotNull(map, "ideCompletions", ideCompletions?.toPojo())

        return map.toImmMap()
    }

    private fun <K, V: Any> putNotNull(map: MutableMap<K, V>, key: K, value: V?) {
        if (value != null) {
            map[key] = value
        }
    }

    private fun <K, V: Any> putNotDefault(map: MutableMap<K, V>, key: K, getter: (C_CompilerOptions) -> V) {
        val value = getter(this)
        val defaultValue = getter(DEFAULT)
        if (value != defaultValue) {
            map[key] = value
        }
    }

    override fun toString(): String = javaClass.simpleName

    companion object {
        @JvmField val DEFAULT = C_CompilerOptions(
            compatibility = null,
            gtv = true,
            deprecatedError = false,
            atAttrShadowing = C_AtAttrShadowing.DEFAULT,
            defaultLib = true,
            testLib = false,
            hiddenLib = false,
            allowDbModificationsInObjectExprs = true,
            symbolInfoFile = null,
            complexWhatEnabled = true,
            mountConflictError = true,
            appModuleInTestsError = false,
            useTestDependencyExtensions = false,
            allowLibNamedArgsAnyVersion = false,
            allowOlderCompatibilityVersion = false,
            ide = false,
            ideDocSymbolsEnabled = false,
            ideDefIdConflictError = false,
            ideCompletions = null,
        )

        @JvmStatic fun builder() = Builder()

        @JvmStatic fun builder(options: C_CompilerOptions) = Builder(options)

        @Suppress("UNCHECKED_CAST")
        @JvmStatic fun fromPojoMap(map: Map<String, Any>): C_CompilerOptions {
            return C_CompilerOptions(
                compatibility = (map["compatibility"] as String?)?.let { R_LangVersion.of(it) },
                gtv = map.getValue("gtv") as Boolean,
                deprecatedError = map.getValue("deprecatedError") as Boolean,
                atAttrShadowing = (map["atAttrShadowing"] as String?)
                    ?.let { C_AtAttrShadowing.valueOf(it) } ?: DEFAULT.atAttrShadowing,
                ide = getBoolOpt(map, "ide", DEFAULT.ide),
                defaultLib = getBoolOpt(map, "defaultLib", DEFAULT.defaultLib),
                testLib = getBoolOpt(map, "testLib", DEFAULT.testLib),
                hiddenLib = getBoolOpt(map, "hiddenLib", DEFAULT.hiddenLib),
                allowDbModificationsInObjectExprs =
                    getBoolOpt(map, "allowDbModificationsInObjectExprs", DEFAULT.allowDbModificationsInObjectExprs),
                symbolInfoFile = (map["symbolInfoFile"] as String?)?.let { C_SourcePath.parse(it) },
                complexWhatEnabled = getBoolOpt(map, "complexWhatEnabled", DEFAULT.complexWhatEnabled),
                mountConflictError = getBoolOpt(map, "mountConflictError", DEFAULT.mountConflictError),
                appModuleInTestsError = getBoolOpt(map, "appModuleInTestsError", DEFAULT.appModuleInTestsError),
                useTestDependencyExtensions =
                    getBoolOpt(map, "useTestDependencyExtensions", DEFAULT.useTestDependencyExtensions),
                allowLibNamedArgsAnyVersion =
                    getBoolOpt(map, "allowLibNamedArgsAnyVersion", DEFAULT.allowLibNamedArgsAnyVersion),
                allowOlderCompatibilityVersion =
                    getBoolOpt(map, "allowOlderCompatibilityVersion", DEFAULT.allowOlderCompatibilityVersion),
                ideDocSymbolsEnabled = getBoolOpt(map, "ideDocSymbolsEnabled", DEFAULT.ideDocSymbolsEnabled),
                ideDefIdConflictError = getBoolOpt(map, "ideDefIdConflictError", DEFAULT.ideDefIdConflictError),
                ideCompletions = (map["ideCompletions"] as Map<String, Any>?)
                    ?.let { C_IdeCompletionsOptions.fromPojo(it) } ?: DEFAULT.ideCompletions,
            )
        }

        fun forLangVersion(version: R_LangVersion): C_CompilerOptions {
            return Builder().compatibility(version).build()
        }

        private fun getBoolOpt(map: Map<String, Any>, key: String, def: Boolean): Boolean = (map[key] as Boolean?) ?: def
    }

    class Builder(proto: C_CompilerOptions = DEFAULT) {
        private var compatibility = proto.compatibility
        private var gtv = proto.gtv
        private var deprecatedError = proto.deprecatedError
        private var atAttrShadowing = proto.atAttrShadowing
        private var defaultLib = proto.defaultLib
        private var testLib = proto.testLib
        private var hiddenLib = proto.hiddenLib
        private var allowDbModificationsInObjectExprs = proto.allowDbModificationsInObjectExprs
        private var symbolInfoFile = proto.symbolInfoFile
        private var complexWhatEnabled = proto.complexWhatEnabled
        private var mountConflictError = proto.mountConflictError
        private var appModuleInTestsError = proto.appModuleInTestsError
        private var useTestDependencyExtensions = proto.useTestDependencyExtensions
        private var allowLibNamedArgsAnyVersion = proto.allowLibNamedArgsAnyVersion
        private var allowOlderCompatibilityVersion = proto.allowOlderCompatibilityVersion
        private var ide = proto.ide
        private var ideDocSymbolsEnabled = proto.ideDocSymbolsEnabled
        private var ideDefIdConflictError = proto.ideDefIdConflictError
        private var ideCompletions = proto.ideCompletions

        @Suppress("UNUSED") fun compatibility(v: R_LangVersion) = apply { compatibility = v }
        @Suppress("UNUSED") fun gtv(v: Boolean) = apply { gtv = v }
        @Suppress("UNUSED") fun deprecatedError(v: Boolean) = apply { deprecatedError = v }
        @Suppress("UNUSED") fun atAttrShadowing(v: C_AtAttrShadowing) = apply { atAttrShadowing = v }
        @Suppress("UNUSED") fun defaultLib(v: Boolean) = apply { defaultLib = v }
        @Suppress("UNUSED") fun testLib(v: Boolean) = apply { testLib = v }
        @Suppress("UNUSED") fun hiddenLib(v: Boolean) = apply { hiddenLib = v }
        @Suppress("UNUSED") fun allowDbModificationsInObjectExprs(v: Boolean) = apply { allowDbModificationsInObjectExprs = v }
        @Suppress("UNUSED") fun symbolInfoFile(v: C_SourcePath?) = apply { symbolInfoFile = v }
        @Suppress("UNUSED") fun complexWhatEnabled(v: Boolean) = apply { complexWhatEnabled = v }
        @Suppress("UNUSED") fun mountConflictError(v: Boolean) = apply { mountConflictError = v }
        @Suppress("UNUSED") fun appModuleInTestsError(v: Boolean) = apply { appModuleInTestsError = v }
        @Suppress("UNUSED") fun useTestDependencyExtensions(v: Boolean) = apply { useTestDependencyExtensions = v }
        @Suppress("UNUSED") fun allowLibNamedArgsAnyVersion(v: Boolean) = apply { allowLibNamedArgsAnyVersion = v }
        @Suppress("UNUSED") fun allowOlderCompatibilityVersion(v: Boolean) = apply { allowOlderCompatibilityVersion = v }
        @Suppress("UNUSED") fun ide(v: Boolean) = apply { ide = v }
        @Suppress("UNUSED") fun ideDocSymbolsEnabled(v: Boolean) = apply { ideDocSymbolsEnabled = v }
        @Suppress("UNUSED") fun ideDefIdConflictError(v: Boolean) = apply { ideDefIdConflictError = v }
        @Suppress("UNUSED") fun ideCompletions(v: C_IdeCompletionsOptions) = apply { ideCompletions = v }


        fun build() = C_CompilerOptions(
            compatibility = compatibility,
            gtv = gtv,
            deprecatedError = deprecatedError,
            atAttrShadowing = atAttrShadowing,
            defaultLib = defaultLib,
            testLib = testLib,
            hiddenLib = hiddenLib,
            allowDbModificationsInObjectExprs = allowDbModificationsInObjectExprs,
            symbolInfoFile = symbolInfoFile,
            complexWhatEnabled = complexWhatEnabled,
            mountConflictError = mountConflictError,
            appModuleInTestsError = appModuleInTestsError,
            useTestDependencyExtensions = useTestDependencyExtensions,
            allowLibNamedArgsAnyVersion = allowLibNamedArgsAnyVersion,
            allowOlderCompatibilityVersion = allowOlderCompatibilityVersion,
            ide = ide,
            ideDocSymbolsEnabled = ideDocSymbolsEnabled,
            ideDefIdConflictError = ideDefIdConflictError,
            ideCompletions = ideCompletions,
        )
    }
}
