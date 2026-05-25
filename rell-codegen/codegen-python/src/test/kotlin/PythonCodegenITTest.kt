/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import assertk.Assert
import assertk.assertThat
import assertk.assertions.support.expected
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class PythonCodegenITTest {

    @Test
    fun `integration test project uses latest postchain-client-py`() {
        val latest = fetchLatestPyPiVersion("postchain-client-py")
        val reqContent = readClasspathResourceText("/integration_test_project/frontend/python/requirements.txt")
        val pinned = parsePinnedRequirement(reqContent, "postchain-client-py")

        if (pinned != latest) {
            throw AssertionError(
                "Please update the Python integration-test client to the latest postchain-client-py.\n" +
                    "Pinned in requirements.txt: $pinned\n" +
                    "Latest on PyPI: $latest\n\n"
            )
        }
    }


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

            GenericContainer("postgres:16.14-alpine3.23").apply {
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

    private fun Assert<org.testcontainers.containers.Container.ExecResult>.executeSuccessFully(): Unit = given { actual ->
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

    private fun readClasspathResourceText(path: String): String {
        val url = checkNotNull(this::class.java.getResource(path)) { "$path not found on test classpath" }
        return File(url.toURI()).readText()
    }

    private fun parsePinnedRequirement(requirementsTxt: String, packageName: String): String {
        val regex = """(?m)^\s*${Regex.escape(packageName)}==([0-9]+\.[0-9]+\.[0-9]+)\s*$""".toRegex()
        return regex.find(requirementsTxt)?.groupValues?.get(1)
            ?: throw AssertionError("Expected $packageName to be pinned as '$packageName==<version>' in requirements.txt")
    }

    private fun fetchLatestPyPiVersion(packageName: String): String {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://pypi.org/pypi/$packageName/json"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw AssertionError("Could not fetch latest $packageName version: HTTP ${response.statusCode()}")
        }
        val versionRegex = """"info"\s*:\s*\{[\s\S]*?"version"\s*:\s*"([^"]+)""""
            .toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
        return versionRegex.find(response.body())
            ?.groupValues?.get(1)
            ?: throw AssertionError("Could not parse info.version from PyPI response for $packageName")
    }
}