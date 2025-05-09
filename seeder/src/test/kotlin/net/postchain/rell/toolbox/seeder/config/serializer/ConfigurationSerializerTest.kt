package net.postchain.rell.toolbox.seeder.config.serializer

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.dsl.configuration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class ConfigurationSerializerTest {
    private val configSerializer = ConfigurationSerializer()

    @TempDir
    private lateinit var tempDir: Path

    @Test
    fun `serialize creates main config file and module files correctly`() {
        val config = configuration {
            module("game.assets") {
                entity("location") {
                    count(203)
                    attributes {
                        attribute("name") {
                            generator("text")
                            min(5)
                            max(20)
                        }
                        attribute("description") {
                            generator("text")
                        }
                    }
                }
                entity("player") {
                    count(50)
                    attributes {
                        attribute("username") {
                            generator("text")
                            min(3)
                            max(15)
                        }
                        attribute("level") {
                            generator("range")
                            min(1)
                            max(100)
                        }
                    }
                }
            }

            module("inventory") {
                entity("item") {
                    count(500)
                    attributes {
                        attribute("name") {
                            generator("text")
                            min(3)
                            max(30)
                        }
                        attribute("value") {
                            generator("range")
                            min(10)
                            max(1000)
                        }
                        attribute("rarity") {
                            generator("predefined")
                            values(listOf("common", "uncommon", "rare", "epic", "legendary"))
                        }
                    }
                }
            }
        }

        val outputPath = configSerializer.serialize(config, tempDir)

        assertThat(outputPath.exists()).isTrue()
        val mainConfigContent = outputPath.readText()
        assertThat(mainConfigContent).isEqualTo(
            """
            modules:
              - "modules/game/assets.yml"
              - "modules/inventory.yml"
            """.trimIndent() + "\n"
        )

        val gameAssetsPath = tempDir.resolve("modules/game/assets.yml")
        assertThat(gameAssetsPath.exists()).isTrue()
        val gameAssetsContent = gameAssetsPath.readText()
        assertThat(gameAssetsContent).isEqualTo(
            """
                module: game.assets
                
                location:
                  count: 203
                  attributes:
                    name:
                      generator: text
                      min: 5
                      max: 20
                    description:
                      generator: text
                
                player:
                  count: 50
                  attributes:
                    username:
                      generator: text
                      min: 3
                      max: 15
                    level:
                      generator: range
                      min: 1
                      max: 100
            """.trimIndent() + "\n"
        )

        val inventoryPath = tempDir.resolve("modules/inventory.yml")
        assertThat(inventoryPath.exists()).isTrue()
        val inventoryContent = inventoryPath.readText()

        assertThat(inventoryContent).isEqualTo(
            """
                module: inventory
                
                item:
                  count: 500
                  attributes:
                    name:
                      generator: text
                      min: 3
                      max: 30
                    value:
                      generator: range
                      min: 10
                      max: 1000
                    rarity:
                      generator: predefined
                      values: [common, uncommon, rare, epic, legendary]
            """.trimIndent() + "\n"
        )
    }

    @Test
    fun `serialize handles different attribute types correctly`() {
        val config = configuration {
            module("test.attributes") {
                entity("complex_entity") {
                    count(10)
                    attributes {
                        attribute("title") {
                            generator("text")
                            min(5)
                            max(20)
                        }

                        attribute("quantity") {
                            generator("range")
                            min(1)
                            max(100)
                        }

                        attribute("status") {
                            generator("predefined")
                            values(listOf("active", "inactive", 1, true))
                        }

                        attribute("email") {
                            generator("email")
                        }
                    }
                }
            }
        }

        configSerializer.serialize(config, tempDir)

        val modulePath = tempDir.resolve("modules/test/attributes.yml")
        assertThat(modulePath.exists()).isTrue()
        val moduleContent = modulePath.readText()

        assertThat(moduleContent).contains(
            """
            |    title:
            |      generator: text
            |      min: 5
            |      max: 20
            |    quantity:
            |      generator: range
            |      min: 1
            |      max: 100
            |    status:
            |      generator: predefined
            |      values: [active, inactive, 1, true]
            |    email:
            |      generator: email
            """.trimMargin()
        )
    }

    @Test
    fun `serialize handles proper path creation for modules with nested namespaces`() {
        val config = configuration {
            module("very.deeply.nested.namespace") {
                entity("test_entity") {
                    count(5)
                    attributes {
                        attribute("name") {
                            generator("text")
                        }
                    }
                }
            }
        }

        configSerializer.serialize(config, tempDir)

        val modulePath = tempDir.resolve("modules/very/deeply/nested/namespace.yml")
        assertThat(modulePath.exists()).isTrue()

        val moduleContent = modulePath.readText()
        assertThat(moduleContent).isEqualTo(
            """
                module: very.deeply.nested.namespace
        
                test_entity:
                  count: 5
                  attributes:
                    name:
                      generator: text
            """.trimIndent() + "\n"
        )
    }

    @Test
    fun `serialize handles empty configuration`() {
        val config = Configuration(emptyMap())

        val outputPath = configSerializer.serialize(config, tempDir)

        assertThat(outputPath.exists()).isTrue()
        val content = outputPath.readText()
        assertThat(content).isEqualTo("modules:\n")
    }
}