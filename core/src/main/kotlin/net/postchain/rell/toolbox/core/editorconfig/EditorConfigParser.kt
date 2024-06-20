package net.postchain.rell.toolbox.core.editorconfig

import io.github.oshai.kotlinlogging.KotlinLogging
import org.ec4j.core.PropertyTypeRegistry
import org.ec4j.core.Resource
import org.ec4j.core.model.EditorConfig
import org.ec4j.core.model.Version
import org.ec4j.core.parser.EditorConfigModelHandler
import org.ec4j.core.parser.EditorConfigParser
import org.ec4j.core.parser.ErrorHandler
import java.io.File
import java.nio.charset.StandardCharsets

object EditorConfigParser {
    private val logger = KotlinLogging.logger {}

    fun parse(configFile: File): EditorConfig? {
        return try {
            val parser = EditorConfigParser.builder().build()
            val handler = EditorConfigModelHandler(PropertyTypeRegistry.default_(), Version.CURRENT)
            parser.parse(
                Resource.Resources.ofPath(configFile.toPath(), StandardCharsets.UTF_8),
                handler,
                ErrorHandler.THROW_SYNTAX_ERRORS_IGNORE_OTHERS
            )
            handler.editorConfig
        } catch (e: Exception) {
            logger.warn(e) { "Could not parse $configFile file" }
            null
        }
    }
}
