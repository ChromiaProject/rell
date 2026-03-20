package net.postchain.rell.toolbox.seeder

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationValidationException
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SeeederApiTest {

    @Test
    fun `configuration validation messages are correctly propagated`(@TempDir tempDir: Path) {
        val config = configFile(tempDir) {
            configuration {
                module("main") {
                    entity("employee") {
                        count(10)
                        attributes {
                            attribute("first_name") {
                                generator("text")
                            }
                            attribute("last_name") {
                                generator("range")
                                min(1)
                                max(10)
                            }
                        }
                    }
                }
            }
        }
"""
    Error: Unable to parse seeder config for module “main”
      ↳ Entity   : “employee”
      ↳ Attribute: “last_name”
        • Found  type: text
        • Expected type: numeric (required for range generator)

""".trimIndent()
        val testData = testData(tempDir) {
            addMainFile(
                """
                module;
                entity employee {
                    first_name: text;
                    last_name: text;
                }
                """.trimIndent()
            )
        }

        val seederParams = SeederParams.Builder()
            .modules(listOf("main"))
            .sourceDir(testData.sourceFolder)
            .rootConfig(config)
            .outputPath(testData.sourceFolder.toPath())
            .mountName("blockchain_1")
            .build()

        val exception = assertThrows<ConfigurationValidationException> {
            SeederApi.generate(seederParams)
        }
        val expectedMessage = "Invalid seeder configuration for module 'main'. Attribute 'employee:last_name' type is 'text' but generator is configured as a numeric range type"
        assertThat(exception.message).isEqualTo(expectedMessage)
    }
}
