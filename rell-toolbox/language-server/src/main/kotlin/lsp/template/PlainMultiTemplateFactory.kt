/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

class PlainMultiTemplateFactory(templatesRoot: Path) : AbstractTemplateFactory(templatesRoot) {
    override fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?) {
        val projectFileName = snakeCaseName(projectName)
        with(FileBuilder(targetDir, "plain-multi", templatesRoot)) {
            createChromiaConfig(projectName) {
                it.replace("PROJECT_MODULE_NAME", projectFileName)
            }
            createFile("src/main.rell") {
                it.replace("plain_multi", projectFileName)
            }
            createGitIgnore()
            createFormatterConfig()
            createLinterConfig()
            createFile("src/development.rell")
            createFile("src/plain-multi/module.rell", "src/$projectFileName/module.rell")
            createFile(
                "src/plain-multi/test/plain_multi_test.rell",
                "src/$projectFileName/test/${projectFileName}_test.rell"
            )
            createFile(
                "src/test/plain_multi_test.rell",
                "src/${projectFileName}_test/blockchain_${projectFileName}_test.rell"
            ) {
                it.replace("PROJECT_NAME", projectName)
            }
        }
    }
}
