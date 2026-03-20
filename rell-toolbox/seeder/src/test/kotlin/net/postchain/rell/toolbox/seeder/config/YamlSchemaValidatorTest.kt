package net.postchain.rell.toolbox.seeder.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YamlSchemaValidatorTest {

    @TempDir
    private lateinit var tempDir: Path
    private val moduleSchemaContent = File(javaClass.getResource("/chromia-seeder-module-schema.json")!!.toURI()).readText()

    private val validator = YamlSchemaValidator()


    @Test
    fun `validate returns empty set when YAML is valid against schema`() {
        val yamlContent = """
            module: some.module
            person:
              count: 40
              attributes:
                full_name:
                  generator: text
                  min: 20
                  max: 30
                age:
                  generator: range
                  min: 18
                  max: 65
        """.trimIndent()
        val yamlFile = createTempFile("valid.yml", yamlContent)

        val validationMessages = validator.validate(yamlFile, moduleSchemaContent)

        assertThat(validationMessages).isEmpty()
    }

    @Test
    fun `validate returns validation messages when YAML is invalid against schema`() {
        val yamlContent = """
            module: some.module
            person:
              attributes:
                full_name:
                  generator: text
                age:
                  generator: invalid_generator
                shirt:
                    generator: predefined
                    values: [1.2, true, xl]
        """.trimIndent()
        val yamlFile = createTempFile("valid.yml", yamlContent)

        val validationMessages = validator.validate(yamlFile, moduleSchemaContent)

        assertThat(validationMessages).hasSize(1)
        val firstMessage = validationMessages.map { it.message }.first()
        assertThat(firstMessage).contains("\$.person.attributes.age.generator: does not have a value in the enumeration ")
    }

    @Test
    fun `validate throws exception when YAML file does not exist`() {
        val nonExistentFile = tempDir.resolve("non-existent.yml")

        val exception = assertFailsWith<RuntimeException> {
            validator.validate(nonExistentFile, moduleSchemaContent)
        }
        assertTrue(exception.message!!.contains("Error validating YAML against schema"))
        assertTrue(exception.cause is IllegalArgumentException)
        assertTrue(exception.cause!!.message!!.contains("File not found"))
    }

    @Test
    fun `validate throws exception when schema file has invalid content`() {
        val yamlFile = createTempFile("valid.yml", "name: John")
        val nonExistentFileContent = "Not a valid YAML schema"

        val exception = assertFailsWith<RuntimeException> {
            validator.validate(yamlFile, nonExistentFileContent)
        }
        assertTrue(exception.message!!.contains("Error validating YAML against schema"))
    }

    @Test
    fun `validate handles empty schema file content`() {
        val yamlFile = createTempFile("valid.yml", "name: John")
        val nonExistentFileContent = ""

        val exception = assertFailsWith<RuntimeException> {
            validator.validate(yamlFile, nonExistentFileContent)
        }
        assertTrue(exception.message!!.contains("Error validating YAML against schema"))
    }

    @Test
    fun `validate throws exception when YAML is malformed`() {
        val invalidYamlContent = """
            module: somemodule
            
            person:
                attributes:
                    name: "John Doe
                    age: 30
        """.trimIndent()

        val yamlFile = createTempFile("malformed.yml", invalidYamlContent)

        assertFailsWith<RuntimeException> {
            validator.validate(yamlFile, moduleSchemaContent)
        }
    }

    private fun createTempFile(fileName: String, content: String): Path {
        val filePath = tempDir.resolve(fileName)
        Files.write(filePath, content.toByteArray())
        return filePath
    }
}