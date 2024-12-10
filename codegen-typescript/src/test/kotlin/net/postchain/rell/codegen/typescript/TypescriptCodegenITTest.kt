package net.postchain.rell.codegen.typescript

import assertk.Assert
import assertk.assertThat
import assertk.assertions.support.expected
import assertk.assertions.support.show
import java.io.File
import net.postchain.rell.codegen.SingleFileRellApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile

@Testcontainers

class TypescriptCodegenITTest {


    companion object {

        private lateinit var nodeContainer: GenericContainer<*>

        @BeforeAll
        @JvmStatic
        fun setup() {
            val projectRootTestResources =  File(this::class.java.getResource("/integration_test_project")!!.toURI())
            val projectRootInContainer = "/usr/app/integration_test_project"
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
                    "$projectRootTestResources/rell",
                    "$projectRootInContainer/rell",
                    BindMode.READ_WRITE
                )
                withWorkingDirectory(
                    "$projectRootInContainer/rell"
                )
                withNetwork(network)
                //Network alias, same value as `nodeUrlPool: ["http://chromia-node:7740"]` in testResources/integration_test_project/frontend/src/client.ts
                withNetworkAliases("chromia-node")
                setCommand("chr", "node", "start")
            }.start()


            nodeContainer = GenericContainer("node:20").apply {
                withWorkingDirectory(
                    "$projectRootInContainer/frontend"
                )
                withNetwork(network)
                withCommand("tail -f /dev/null")
            }
            nodeContainer.start()
            nodeContainer.copyFileToContainer(
                MountableFile.forHostPath("$projectRootTestResources/frontend"),
                "$projectRootInContainer/frontend"
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
        expected("to execute successfully but but exit code was ${show(actual.exitCode)}: ${actual.stdout}")
    }
}
