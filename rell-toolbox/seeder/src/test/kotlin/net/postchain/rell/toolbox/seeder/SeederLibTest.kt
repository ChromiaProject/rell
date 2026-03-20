package net.postchain.rell.toolbox.seeder

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isTrue
import net.postchain.rell.toolbox.seeder.config.InitialConfigGenerator
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test

class SeederLibTest {
    private val generator = InitialConfigGenerator()
    private val testResourcesDir = File(javaClass.getResource("/seeder-lib-test/src")!!.toURI())
    val schema = SchemaReader().readSchema(testResourcesDir, listOf("lib.my_lib"), isLibrary = true)

    @Test
    fun `generate creates correct directory structure`(@TempDir tempDir: Path) {
        generator.generate(schema, tempDir)

        assertThat(tempDir.resolve("seeder.yml").isRegularFile()).isTrue()
        assertThat(tempDir.resolve("modules").isDirectory()).isTrue()
    }

    @Test
    fun `test seeder configuration generator for library`(@TempDir tempDir: Path) {
        generator.generate(schema, tempDir)

        val configContent = tempDir.resolve("seeder.yml").readText()
        val yaml = Yaml()
        val config = yaml.load<Map<String, Any>>(configContent)

        @Suppress("UNCHECKED_CAST")
        val modules = config["modules"] as List<String>

        assertThat(
            modules
        ).containsExactlyInAnyOrder("modules/lib/my_lib/module_a.yml", "modules/lib/my_lib/module_b.yml")
    }
}