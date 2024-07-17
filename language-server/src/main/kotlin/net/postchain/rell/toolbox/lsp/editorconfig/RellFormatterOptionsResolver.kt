package net.postchain.rell.toolbox.lsp.editorconfig

import net.postchain.rell.toolbox.formatter.FormatterOptions
import java.io.File
import java.net.URI

class RellFormatterOptionsResolver {

    fun getWorkspaceFormattingOptions(workspaceUri: URI): FormatterOptions {
        return getWorkspaceFormattingOptionsOrNull(workspaceUri) ?: FormatterOptions()
    }

    fun getWorkspaceFormattingOptionsOrNull(workspaceUri: URI): FormatterOptions? {
        return extractFormattingOptionsFromFile(workspaceUri)
    }

    private fun extractFormattingOptionsFromFile(workspaceUri: URI): FormatterOptions? {
        val rellFormatFile = findRellFormatFile(workspaceUri) ?: return null
        return FormatterOptions().apply {
            updateOptionsFromFile(rellFormatFile)
        }
    }

    private fun findRellFormatFile(projectRootUri: URI): File? {
        val projectRootFolder = File(projectRootUri)
        return findRellFormatConfigFile(projectRootFolder, FormatterOptions.PREFERRED_RELL_FORMAT_FILE_NAME)
            ?: findRellFormatConfigFile(projectRootFolder, FormatterOptions.DEPRECATED_RELL_FORMAT_FILE_NAME)
    }

    private fun findRellFormatConfigFile(projectRootFolder: File, configFileName: String): File? {
        val inCurrentFolder = projectRootFolder.resolve(configFileName)
        val inParentFolder = projectRootFolder.parentFile?.resolve(configFileName)
        val inGrandparentFolder =
            projectRootFolder.parentFile?.parentFile?.resolve(configFileName)

        return when {
            inCurrentFolder.exists() && inCurrentFolder.isFile -> inCurrentFolder
            inParentFolder != null && inParentFolder.exists() && inParentFolder.isFile -> inParentFolder
            inGrandparentFolder != null && inGrandparentFolder.exists() && inGrandparentFolder.isFile -> inGrandparentFolder
            else -> null
        }
    }
}
