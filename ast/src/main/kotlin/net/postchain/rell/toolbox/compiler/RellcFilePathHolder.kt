package net.postchain.rell.toolbox.compiler

import java.util.function.Supplier

object RellcFilePathHolder {

    private val CURRENT_FILE_LOCAL = ThreadLocal<RellcFilePath>()

    fun <T> overrideCurrentFile(path: RellcFilePath, code: Supplier<T>): T {
        val oldPath = CURRENT_FILE_LOCAL.get()
        CURRENT_FILE_LOCAL.set(path)
        return try {
            code.get()
        } finally {
            CURRENT_FILE_LOCAL.set(oldPath)
        }
    }

    val currentFile: RellcFilePath get() = CURRENT_FILE_LOCAL.get()
}
