/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.config

import net.postchain.rell.api.base.RellCliEnv

/**
 * Global state registry for the Rell Dokka plugin to bypass serialization
 *
 * The Dokka configuration is serialized and deserialized during documentation generation,
 * which can cause non-serializable objects to be lost. This singleton provides persistent
 * storage for state that needs to survive across the entire documentation process.
 *
 * It handles:
 * 1. Hidden packages (modules excluded from UI navigation)
 * 2. RellCliEnv for error handling and logging
 */
object RellDokkaGlobalState {
    private val hiddenPackages = mutableListOf<String>()
    private var cliEnv: RellCliEnv? = null

    fun hidePackages(moduleNames: Collection<String>) {
        hiddenPackages.addAll(moduleNames)
    }

    fun getHiddenPackages(): List<String> = hiddenPackages

    fun setCliEnv(env: RellCliEnv?) {
        cliEnv = env
    }

    fun getCliEnv(): RellCliEnv? = cliEnv
}