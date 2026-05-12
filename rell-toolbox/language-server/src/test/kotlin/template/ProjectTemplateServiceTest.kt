/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.template

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class ProjectTemplateServiceTest {
    private val service = ProjectTemplateService(LocalDirTemplateRepository(TestTemplateFixture.materialize()))

    @Test
    fun `getAvailableTemplates returns all available templates`() {
        val templates = service.getAvailableTemplates()

        // When this test fails, it is likely because a new template has been added to the TemplateProject enum
        // This test is here to catch that.
        // If a new template is intended for IDE extensions as well, this assertion should be updated to include it.
        // otherwise getAvailableTemplates should be updated to filter out the new template.
        assertThat(templates).containsExactly(
            NewProjectTemplate("PLAIN", "Plain"),
            NewProjectTemplate("PLAIN_MULTI", "Plain Multi"),
            NewProjectTemplate("MINIMAL", "Minimal"),
            NewProjectTemplate("PLAIN_LIBRARY", "Plain Library"),
            NewProjectTemplate("ASSET_MANAGEMENT", "Asset Management")
        )
    }

    @Test
    fun `createNewProjectTemplate creates project directory`(@TempDir targetDir: Path) {
        val template = TemplateProject.PLAIN
        val projectName = "new-project"

        val projectDir = service.createNewProjectTemplate(template, projectName, targetDir)

        assertThat(projectDir.exists()).isTrue()
        assertThat(projectDir.isDirectory()).isTrue()
        assertThat(projectDir.name).isEqualTo(projectName)
    }

    @Test
    fun `createNewProjectTemplate throws exception if directory already exists`(@TempDir targetDir: Path) {
        val template = TemplateProject.PLAIN_MULTI
        val projectName = "existing-project"
        targetDir.resolve(projectName).createDirectories()

        val exception = assertThrows<IllegalStateException> {
            service.createNewProjectTemplate(template, projectName, targetDir)
        }

        assertThat(
            exception.message
        ).isEqualTo("There already exist a directory called \"$projectName\" in the working directory, aborting.")
    }
}
