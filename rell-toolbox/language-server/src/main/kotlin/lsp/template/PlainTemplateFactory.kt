/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

class PlainTemplateFactory(templatesRoot: Path) : AbstractTemplateFactory(templatesRoot) {
    override fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?) {
        val projectFileName = snakeCaseName(projectName)
        with(FileBuilder(targetDir, "plain", templatesRoot)) {
            createChromiaConfig(projectName)
            createGitIgnore()
            createFormatterConfig()
            createLinterConfig()
            createFile("src/main.rell")
            createFile("src/test/plain_test.rell", "src/test/${projectFileName}_test.rell")
        }
    }
}
