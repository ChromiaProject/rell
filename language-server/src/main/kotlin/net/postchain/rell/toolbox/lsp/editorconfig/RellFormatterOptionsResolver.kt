package net.postchain.rell.toolbox.lsp.editorconfig

import io.github.oshai.kotlinlogging.KotlinLogging
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import org.ec4j.core.PropertyTypeRegistry
import org.ec4j.core.Resource
import org.ec4j.core.model.EditorConfig
import org.ec4j.core.model.Version
import org.ec4j.core.parser.EditorConfigModelHandler
import org.ec4j.core.parser.EditorConfigParser
import org.ec4j.core.parser.ErrorHandler
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets

class RellFormatterOptionsResolver(val workspaceManager: RellWorkspaceManager) {

    fun getFormattingOptionsFor(fileUri: URI): FormatterOptions? {
        val workspaceUri = workspaceManager.getIndexerFor(fileUri).workspaceUri
        return extractFormattingOptionsFromFile(workspaceUri)
    }

    private fun extractFormattingOptionsFromFile(workspaceUri: URI): FormatterOptions? {
        val rellFormatFile = findRellFormatFile(workspaceUri) ?: return null
        val editorConfig = parseEditorConfig(rellFormatFile) ?: return null
        return createFormatterOptions(editorConfig)
    }

    private fun findRellFormatFile(projectRootUri: URI): File? {
        val projectRootFolder = File(projectRootUri)
        val inCurrentFolder = projectRootFolder.resolve(RELL_FORMAT_FILE_NAME)
        val inParentFolder = projectRootFolder.parentFile?.resolve(RELL_FORMAT_FILE_NAME)
        val inGrandparentFolder = projectRootFolder.parentFile?.parentFile?.resolve(RELL_FORMAT_FILE_NAME)

        return when {
            inCurrentFolder.exists() && inCurrentFolder.isFile -> inCurrentFolder
            inParentFolder != null && inParentFolder.exists() && inParentFolder.isFile -> inParentFolder
            inGrandparentFolder != null && inGrandparentFolder.exists() && inGrandparentFolder.isFile -> inGrandparentFolder
            else -> null
        }
    }

    private fun createFormatterOptions(editorConfig: EditorConfig): FormatterOptions {
        val formatterOptions = FormatterOptions()
        editorConfig.sections.forEach { section ->
            section.properties.forEach { property ->
                when (property.key) {
                    "max_line_width" -> {
                        formatterOptions.maxLineWidth = property.value.sourceValue.toInt()
                    }

                    "insert_spaces" -> {
                        formatterOptions.insertSpaces = property.value.sourceValue == "true"
                    }

                    "tab_size" -> {
                        formatterOptions.tabSize = property.value.sourceValue.toInt()
                    }
                }
            }
        }
        return formatterOptions
    }

    private fun parseEditorConfig(rellFormatFile: File): EditorConfig? {
        return try {
            val parser = EditorConfigParser.builder().build()
            val handler = EditorConfigModelHandler(PropertyTypeRegistry.default_(), Version.CURRENT)
            parser.parse(
                Resource.Resources.ofPath(rellFormatFile.toPath(), StandardCharsets.UTF_8),
                handler,
                ErrorHandler.THROW_SYNTAX_ERRORS_IGNORE_OTHERS
            )
            handler.editorConfig
        } catch (e: Exception) {
            logger.warn(e) { "Could not parse $rellFormatFile file" }
            null
        }
    }

    companion object {
        const val RELL_FORMAT_FILE_NAME = ".rellformat"
        private val logger = KotlinLogging.logger {}
    }
}