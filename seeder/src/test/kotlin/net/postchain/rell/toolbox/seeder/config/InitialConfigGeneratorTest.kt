package net.postchain.rell.toolbox.seeder.config

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.SchemaReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class InitialConfigGeneratorTest {
    private val generator = InitialConfigGenerator()
    private val testResourcesDir = File(javaClass.getResource("/seeder-test/src")!!.toURI())

    private val schema = createTestSchema()

    @Test
    fun `generate creates correct directory structure`(@TempDir tempDir: Path) {
        generator.generate(schema, tempDir)

        assertThat(tempDir.resolve("seeder.yml").isRegularFile()).isTrue()
        assertThat(tempDir.resolve("modules").isDirectory()).isTrue()
    }

    @Test
    fun `generate creates config file with correct entity paths`(@TempDir tempDir: Path) {
        generator.generate(schema, tempDir)

        val configContent = tempDir.resolve("seeder.yml").readText()
        val yaml = Yaml()
        val config = yaml.load<Map<String, Any>>(configContent)

        @Suppress("UNCHECKED_CAST")
        val modules = config["modules"] as List<String>

        assertThat(
            modules
        ).containsExactlyInAnyOrder("modules/main.yml", "modules/game/entities.yml", "modules/namespace/nested.yml")
    }

    @Test
    fun `generate creates module files with correct module name`(@TempDir tempDir: Path) {
        generator.generate(schema, tempDir)

        val modulesFolder = tempDir.resolve("modules")
        assertThat(modulesFolder.resolve("main.yml").exists()).isTrue()
        assertThat(modulesFolder.resolve("game/entities.yml").exists()).isTrue()
        assertThat(modulesFolder.resolve("namespace/nested.yml").exists()).isTrue()
    }

    private fun createTestSchema(): RellSchema {
        return SchemaReader().readSchema(testResourcesDir, listOf("main", "game.entities", "namespace.nested"))
    }
}
