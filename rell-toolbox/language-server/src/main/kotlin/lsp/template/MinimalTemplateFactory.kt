/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

class MinimalTemplateFactory(templatesRoot: Path) : AbstractTemplateFactory(templatesRoot) {
    override fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?) {
        with(FileBuilder(targetDir, "minimal", templatesRoot)) {
            createChromiaConfig(projectName)
            createGitIgnore()
            createFormatterConfig()
            createLinterConfig()
            createFile("src/main.rell")
            createFile("src/test/arithmetic_test.rell")
            createFile("src/test/data_test.rell")
        }
    }
}
