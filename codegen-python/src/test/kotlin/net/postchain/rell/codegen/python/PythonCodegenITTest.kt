package net.postchain.rell.codegen.python

import assertk.Assert
import assertk.assertThat
import assertk.assertions.support.expected
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.io.File
import java.time.Duration

@Testcontainers

class PythonCodegenITTest {


    companion object {

        private lateinit var pythonContainer: GenericContainer<*>

        @BeforeAll
        @JvmStatic
        fun setup() {
            val projectRootTestResources =  File(this::class.java.getResource("/integration_test_project")!!.toURI())
            val projectRellPath = "$projectRootTestResources/rell"
            val projectFrontendPath = "$projectRootTestResources/frontend/python"
            
            val projectRootInContainer = "/usr/app/integration_test_project"
            val projectRellPathInContainer = "$projectRootInContainer/rell"
            val projectFrontendPathInContainer = "$projectRootInContainer/frontend/python"


            val network = Network.newNetwork()

            GenericContainer("postgres:16.3-alpine3.20").apply {
                withEnv("POSTGRES_USER", "postchain")
                withEnv("POSTGRES_PASSWORD", "postchain")
                withEnv("POSTGRES_DB", "postchain")
                withExposedPorts(5432)
                withNetwork(network)
                withNetworkAliases("postgres")
            }.start()


            GenericContainer("registry.gitlab.com/chromaway/core-tools/chromia-cli/chr:latest").apply {
                withFileSystemBind(
                    projectRellPath,
                    projectRellPathInContainer,
                    BindMode.READ_WRITE
                )
                withWorkingDirectory(projectRellPathInContainer)
                withNetwork(network)
                withNetworkAliases("chromia-node")
                setCommand("chr", "node", "start")

                waitingFor(
                    Wait.forLogMessage(".*Blockchain has been started.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2))
                )
            }.start()


            pythonContainer = GenericContainer("python:3.12").apply {
                withWorkingDirectory(projectFrontendPathInContainer)
                withNetwork(network)
                withCommand("tail -f /dev/null")
            }
            pythonContainer.start()
            pythonContainer.copyFileToContainer(
                MountableFile.forHostPath(projectFrontendPath),
                projectFrontendPathInContainer
            )
            pythonContainer.execInContainer("pip", "install", "-r", "requirements.txt")
        }
    }

    @Test
    fun `Python test suite with client stubs executes without error against running node`() {
        val res = pythonContainer.execInContainer("python", "-m", "pytest", "tests/", "-v")
        assertThat(res).executeSuccessFully()
    }

    private fun Assert<org.testcontainers.containers.Container.ExecResult>.executeSuccessFully() = given { actual ->
        if (actual.exitCode == 0) return
        val errorMessage = buildString {
            appendLine("Python test suite failed with exit code: ${actual.exitCode}")
            appendLine("Standard Output:")
            appendLine(actual.stdout)
            appendLine("Standard Error:")
            appendLine(actual.stderr)
        }

        expected(errorMessage)
    }
} 