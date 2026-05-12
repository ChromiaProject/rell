/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path

class AssetManagementTemplateFactory(templatesRoot: Path) : AbstractTemplateFactory(templatesRoot) {
    override fun createProjectFiles(targetDir: Path, projectName: String, options: TemplateOptions?) {
        with(FileBuilder(targetDir, "asset-management", templatesRoot)) {
            moveFolder("")
            createFormatterConfig()
            createLinterConfig()
        }
    }
}
