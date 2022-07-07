package net.postchain.rell.plugin

import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory


class RellGenPluginTest {

    @Test
    fun test() {
        val testProjectDir = createTempDirectory().toFile()
        val rellModule = File(testProjectDir, "src/main/rell/test/module.rell")
        rellModule.parentFile.mkdirs()
        rellModule.writeText(
            """
            module;
            
            query get_foo() = 1;
        """.trimIndent()
        )
        val buildFile = File(testProjectDir, "build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'net.postchain.rell.rellgen'
            }
            
            rellgen {
                mainModule = "test"
                packageName = "com.example"
            }
        """
        )
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("rellgen")
            .build()
        val rellgenTask = result.task(":rellgen")
        assertk.assert(rellgenTask).isNotNull()
        assertk.assert(rellgenTask!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertk.assert(File(testProjectDir, "build/generated/test/test.kt")).exists()
    }
}