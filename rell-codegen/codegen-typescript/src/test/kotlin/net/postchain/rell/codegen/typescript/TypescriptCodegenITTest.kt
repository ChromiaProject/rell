/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript

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
class TypescriptCodegenITTest {

    @Test
    fun `integration test project uses latest postchain-client`() {
        val latest = fetchLatestNpmVersion("postchain-client")
        val packageJsonContent = readClasspathResourceText("/integration_test_project/frontend/typescript/package.json")
        val spec = parseNpmDependencySpecFromPackageJson(packageJsonContent, "postchain-client")

        if (!isUpToDateNpmSpec(spec, latest)) {
            throw AssertionError(
                "Please update the TypeScript integration-test client to the latest postchain-client.\n" +
                    "Declared in package.json: $spec\n" +
                    "Latest on npm: $latest\n\n"
            )
        }
    }


    companion object {

        private lateinit var nodeContainer: GenericContainer<*>

        @BeforeAll
        @JvmStatic
        fun setup() {
            val projectRootTestResources =  File(this::class.java.getResource("/integration_test_project")!!.toURI())
            val projectRellPath = "$projectRootTestResources/rell"
            val projectFrontendPath = "$projectRootTestResources/frontend/typescript"

            val projectRootInContainer = "/usr/app/integration_test_project"
            val projectRellPathInContainer = "$projectRootInContainer/rell"
            val projectFrontendPathInContainer = "$projectRootInContainer/frontend/typescript"


            val network = Network.newNetwork()

            GenericContainer("postgres:16.3-alpine3.20").apply {
                withEnv("POSTGRES_USER", "postchain")
                withEnv("POSTGRES_PASSWORD", "postchain")
                withEnv("POSTGRES_DB", "postchain")
                withExposedPorts(5432)
                withNetwork(network)
                //Network alias same value as property `database.host` in /integration_test_project/rell/chromia.yml
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
                //Network alias, same value as `nodeUrlPool: ["http://chromia-node:7740"]` in testResources/integration_test_project/frontend/src/client.ts
                withNetworkAliases("chromia-node")
                setCommand("chr", "node", "start")

                waitingFor(
                    Wait.forLogMessage(".*Blockchain has been started.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2))
                )
            }.start()


            nodeContainer = GenericContainer("node:20").apply {
                withWorkingDirectory(projectFrontendPathInContainer)
                withNetwork(network)
                withCommand("tail -f /dev/null")
            }
            nodeContainer.start()
            nodeContainer.copyFileToContainer(
                MountableFile.forHostPath(projectFrontendPath),
                projectFrontendPathInContainer
            )
            nodeContainer.execInContainer("npm", "install")

        }
    }

    @Test
    fun `Node test suite with client stubs executes without error against running node`() {
        val res = nodeContainer.execInContainer("npm", "run", "test")
        assertThat(res).executeSuccessFully()
    }

    private fun Assert<org.testcontainers.containers.Container.ExecResult>.executeSuccessFully() = given { actual ->
        if (actual.exitCode == 0) return
        val errorMessage = buildString {
            appendLine("Node test suite failed with exit code: ${actual.exitCode}")
            appendLine("Standard Output:")
            appendLine(actual.stdout)
            appendLine("Standard Error:")
            appendLine(actual.stderr)
        }

        expected(errorMessage)
    }

    private fun readClasspathResourceText(path: String): String {
        val url = this::class.java.getResource(path)
            ?: throw IllegalStateException("$path not found on test classpath")
        return File(url.toURI()).readText()
    }

    private fun parseNpmDependencySpecFromPackageJson(packageJson: String, packageName: String): String {
        val regex = """"$packageName"\s*:\s*"([^"]+)""""
            .toRegex(setOf(RegexOption.MULTILINE))
        return regex.find(packageJson)?.groupValues?.get(1)
            ?: throw AssertionError("Could not find \"$packageName\": \"...\" in package.json")
    }

    private fun isUpToDateNpmSpec(spec: String, latest: String): Boolean {
        val normalized = spec.trim()
        if (normalized == "latest") return true
        val base = normalized.removePrefix("^").removePrefix("~").trim()
        return base == latest
    }

    private fun fetchLatestNpmVersion(packageName: String): String {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://registry.npmjs.org/$packageName/latest"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw AssertionError("Could not fetch latest $packageName version: HTTP ${response.statusCode()}")
        }
        val versionRegex = """"version"\s*:\s*"([^"]+)"""".toRegex()
        return versionRegex.find(response.body())
            ?.groupValues?.get(1)
            ?: throw AssertionError("Could not parse version from npm registry response for $packageName")
    }
}
