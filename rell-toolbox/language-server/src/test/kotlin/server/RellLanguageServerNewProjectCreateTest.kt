/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.chromia.build.tools.template.DevContainerSupport
import com.chromia.build.tools.template.TemplateOptions
import com.chromia.build.tools.template.TemplateProject
import net.postchain.rell.toolbox.lsp.TestClient
import net.postchain.rell.toolbox.lsp.TestClientServerLauncher
import net.postchain.rell.toolbox.lsp.TestServerModule
import net.postchain.rell.toolbox.lsp.template.AddToProjectParams
import net.postchain.rell.toolbox.lsp.template.CreateNewProjectParams
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellLanguageServerNewProjectCreateTest {
    private lateinit var clientServerLauncher: TestClientServerLauncher
    private lateinit var server: RellLanguageServer
    private lateinit var workspaceManager: RellWorkspaceManager
    private lateinit var testClient: TestClient
    private val serverModule = TestServerModule()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setupBeforeEach() {
        val koinApp = serverModule.startKoin()

        clientServerLauncher = TestClientServerLauncher(koinApp)
        clientServerLauncher.launch()
        testClient = clientServerLauncher.testClient
        server = koinApp.koin.get<RellLanguageServer>()
        workspaceManager = koinApp.koin.get<RellWorkspaceManager>()
    }

    @AfterEach
    fun tearDown() {
        serverModule.stopKoinGlobalContext()
        clientServerLauncher.stop()
    }

    @Test
    fun `New project is successfully created`() {
        clientServerLauncher.initializeServer(tempDir.toURI())
        val template = TemplateProject.PLAIN
        val projectName = "new-project"
        val targetDirUri = tempDir.toURI()
        val params = CreateNewProjectParams(template, projectName, targetDirUri.toString())

        val projectDir = server.createNewProject(params).join()

        val expectedProjectDir = File(tempDir, projectName)
        assertThat(expectedProjectDir.exists()).isTrue()
        assertThat(expectedProjectDir.isDirectory).isTrue()
        assertThat(projectDir).isEqualTo(expectedProjectDir.absolutePath)
    }

    @Test
    fun `Dev container is included with new project`() {
        clientServerLauncher.initializeServer(tempDir.toURI())
        val template = TemplateProject.PLAIN
        val projectName = "new-project"
        val targetDirUri = tempDir.toURI()
        val params = CreateNewProjectParams(
            template,
            projectName,
            targetDirUri.toString(),
            TemplateOptions(includeDevContainer = true)
        )

        val projectDir = server.createNewProject(params).join()

        val expectedProjectDir = File(tempDir, projectName)
        assertThat(projectDir).isEqualTo(expectedProjectDir.absolutePath)
        val devContainerDir = File(expectedProjectDir, DevContainerSupport.DEV_CONTAINER_FOLDER_NAME)
        assertThat(devContainerDir.exists()).isTrue()
        assertThat(devContainerDir.isDirectory).isTrue()
        assertThat(devContainerDir.listFiles()).isNotEmpty()
    }

    @Test
    fun `New project creation fails when directory already exists created`() {
        clientServerLauncher.initializeServer(tempDir.toURI())
        val template = TemplateProject.PLAIN
        val projectName = "new-project"
        val targetDirUri = tempDir.toURI()
        val existingDir = tempDir.resolve(projectName)
        existingDir.mkdirs()
        val params = CreateNewProjectParams(template, projectName, targetDirUri.toString())
        val exception = assertThrows<IllegalStateException> {
            server.createNewProject(params).join()
        }
        assertThat(
            exception.message
        ).isEqualTo("There already exist a directory called \"$projectName\" in the working directory, aborting.")
    }

    @Test
    fun `Project template list isn't empty`() {
        clientServerLauncher.initializeServer(tempDir.toURI())
        val templates = server.listNewProjectTemplates().join()
        assertThat(templates).isNotEmpty()
    }

    @Test
    fun `Adding feature to existing project is successful`() {
        clientServerLauncher.initializeServer(tempDir.toURI())
        val targetDirUri = tempDir.toURI()
        val params = AddToProjectParams(targetDirUri.toString(), options = TemplateOptions(includeDevContainer = true))

        server.addToProject(params).join()

        val expectedDir = File(tempDir, DevContainerSupport.DEV_CONTAINER_FOLDER_NAME)
        assertThat(expectedDir.exists()).isTrue()
        assertThat(expectedDir.isDirectory).isTrue()
        assertThat(expectedDir.listFiles()).isNotEmpty()
    }
}
