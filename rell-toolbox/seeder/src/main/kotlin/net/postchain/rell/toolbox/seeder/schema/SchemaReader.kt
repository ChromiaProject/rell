/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.schema

import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions
import java.io.File

class SchemaReader {
    fun readSchema(
        sourceDir: File,
        modules: List<String>? = null,
        rellVersion: R_LangVersion = RellVersions.VERSION,
        isLibrary: Boolean = false,
    ): RellSchema {
        val app = compileApp(sourceDir, modules, rellVersion, isLibrary)
        return buildSchema(app)
    }

    private fun compileApp(sourceDir: File, appModules: List<String>?, rellVersion: R_LangVersion, isLibrary: Boolean):
        R_App {
        val conf = RellApiCompile.Config.Builder()
            .moduleArgsMissingError(false)
            .mountConflictError(true)
            .docSymbolsEnabled(false)
            .version(rellVersion)
            .quiet(true)
            .build()
        return try {
            if (isLibrary) {
                RellApiCompile.compileApp(conf, sourceDir, null)
            } else {
                RellApiCompile.compileApp(conf, sourceDir, appModules)
            }
        } catch (e: Exception) {
            throw SchemaReaderException("Compilation failed", e)
        }
    }

    private fun buildSchema(app: R_App): RellSchema {
        val entities = app.sqlDefs.topologicalEntities.map(::Entity)
        return RellSchema(entities)
    }
}

class SchemaReaderException(message: String, cause: Throwable) : RuntimeException(message, cause)
