package net.postchain.rell.toolbox.lsp.editorconfig

import net.postchain.rell.toolbox.linter.LinterOptions
import java.io.File
import java.net.URI

class RellLinterOptionsResolver {

    fun getLinterConfig(workspaceUri: URI): LinterOptions {
        return findLinterConfigFile(workspaceUri)?.let {
            LinterOptions().apply {
                updateOptionsFromFile(it)
            }
        } ?: LinterOptions()
    }

    fun findLinterConfigFile(projectRootUri: URI): File? {
        val projectRootFolder = File(projectRootUri)
        val inCurrentFolder = projectRootFolder.resolve(LinterOptions.CONFIG_FILE_NAME)
        val inParentFolder = projectRootFolder.parentFile?.resolve(LinterOptions.CONFIG_FILE_NAME)
        val inGrandparentFolder = projectRootFolder.parentFile?.parentFile?.resolve(LinterOptions.CONFIG_FILE_NAME)

        return when {
            inCurrentFolder.exists() && inCurrentFolder.isFile -> inCurrentFolder
            inParentFolder != null && inParentFolder.exists() && inParentFolder.isFile -> inParentFolder
            inGrandparentFolder != null && inGrandparentFolder.exists() && inGrandparentFolder.isFile -> inGrandparentFolder
            else -> null
        }
    }
}