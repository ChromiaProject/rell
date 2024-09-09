package net.postchain.rell.toolbox.compiler

import java.util.function.Supplier

object RellCompilerFilePathHolder {

    private val CURRENT_FILE_LOCAL = ThreadLocal<RellCompilerFilePath>()

    fun <T> overrideCurrentFile(path: RellCompilerFilePath, code: Supplier<T>): T {
        val oldPath = CURRENT_FILE_LOCAL.get()
        CURRENT_FILE_LOCAL.set(path)
        return try {
            code.get()
        } finally {
            CURRENT_FILE_LOCAL.set(oldPath)
        }
    }

    val currentFile: RellCompilerFilePath get() = CURRENT_FILE_LOCAL.get()
}
