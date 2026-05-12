/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

class PlainLibraryTemplateFactory(templatesRoot: Path) : AbstractTemplateFactory(templatesRoot) {
    override fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?) {
        val projectFileName = snakeCaseName(projectName)
        with(FileBuilder(targetDir, "plain-library", templatesRoot)) {
            createGitIgnore { "!src/lib/$projectFileName" }
            createChromiaConfig(projectName) {
                it.replace("PROJECT_MODULE_NAME", projectFileName)
            }
            createFormatterConfig()
            createLinterConfig()
            createFile("src/lib/plain-library/module.rell", "src/lib/$projectFileName/module.rell")
            createFile("src/tests/test_lib_plain_library.rell", "src/tests/test_lib_$projectFileName.rell")
        }
    }
}
