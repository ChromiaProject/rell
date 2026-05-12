/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class DevContainerSupport(private val templatesRoot: Path) {

    companion object {
        const val DEV_CONTAINER_FOLDER_NAME = ".devcontainer"
    }

    fun addToProject(projectDir: Path) {
        require(projectDir.exists() && projectDir.isDirectory()) {
            "Provided path is not a valid directory: $projectDir"
        }
        FileBuilder(
            projectDir / DEV_CONTAINER_FOLDER_NAME,
            DEV_CONTAINER_FOLDER_NAME,
            templatesRoot,
        ).moveFolder("")
    }
}
