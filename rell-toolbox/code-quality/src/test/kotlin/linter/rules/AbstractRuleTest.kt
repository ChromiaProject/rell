/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.base.utils.C_SourceFile
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.toolbox.chromia.ChromiaModelProvider
import net.postchain.rell.toolbox.indexer.RellResourceFactory
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.parser.AntlrRellParser
import java.net.URI

open class AbstractRuleTest {
    private val rellLinter = RellLinter()
    private val resourceFactory = RellResourceFactory(
        javaClass.getResource("/linter/")!!.toURI(),
        AntlrRellParser(),
        ChromiaModelProvider(null)
    )

    protected fun lint(
        fileName: String,
        config: LinterOptions,
        importedModuleFileNames: List<String> = listOf()
    ): List<LinterIssue> {
        val fileMap: MutableMap<C_SourcePath, C_SourceFile> = mutableMapOf()
        importedModuleFileNames.forEach { moduleFileName ->
            val dependencyUri = getFileUri(moduleFileName)
            resourceFactory.buildRellResource(dependencyUri, fileMap)
        }
        val fileUri = getFileUri(fileName)
        val resource = resourceFactory.buildRellResource(fileUri, fileMap)
        return rellLinter.lint(config, resource)
    }

    private fun getFileUri(fileName: String): URI {
        return javaClass.getResource("/linter/$fileName")!!.toURI()
    }
}
