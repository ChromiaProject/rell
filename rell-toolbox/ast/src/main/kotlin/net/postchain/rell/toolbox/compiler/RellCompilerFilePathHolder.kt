/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.compiler

object RellCompilerFilePathHolder {
    private val CURRENT_FILE_LOCAL = ThreadLocal<RellCompilerFilePath>()

    fun <T> overrideCurrentFile(path: RellCompilerFilePath, code: () -> T): T {
        val oldPath = CURRENT_FILE_LOCAL.get()
        CURRENT_FILE_LOCAL.set(path)
        return try {
            code()
        } finally {
            CURRENT_FILE_LOCAL.set(oldPath)
        }
    }

    val currentFile: RellCompilerFilePath
        get() = CURRENT_FILE_LOCAL.get()
}
