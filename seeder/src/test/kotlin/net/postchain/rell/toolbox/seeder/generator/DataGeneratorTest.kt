package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.size
import net.postchain.rell.toolbox.seeder.SchemaReader
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.dsl.configFile
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DataGeneratorTest {

    private val schemaReader = SchemaReader()
    private val configParser = ConfigurationParser()
    private val dataGenerator = DataGenerator()

    @TempDir
    private lateinit var tempDir: Path

    /*
        is AttributeConfig.PredefinedValues -> generateFromPredefinedValues(attributeConfig, existingRecords.size)
            - should return predefined value from the predefinedValues list
            - if attributeConfig.values.size == 1 -> should return the first element
            - if no distribution is set -> should do the sequential one
            Distribution.SEQUENTIAL -> config.values[index % config.values.size]
            Distribution.RANDOM -> config.values[random.nextInt(config.values.size)]
            Distribution.WEIGHTED -> selectWeightedValue(config)

        is AttributeConfig.Range -> generateFromRange(attributeConfig, attribute)
            - should return number between min and max
            is R_IntegerType -> random.nextInt(min.toInt(), max.toInt() + 1)
            is R_BigIntegerType -> BigInteger( random.nextLong(min.toLong(), max.toLong()).toString() )
            is R_DecimalType -> BigDecimal( random.nextDouble(min.toDouble(), max.toDouble()) )

        is AttributeConfig.TextConfig -> generateString(attributeConfig)
            val length = random.nextInt(
                config.min ?: DataGenerator.TEXT_MIN_DEFAULT,
                (config.max ?: DataGenerator.TEXT_MAX_DEFAULT) + 1
            )
            val value = fakeDataProvider.generateRandomString(length)

        is AttributeConfig.NamedConfig -> FieldValue(fakeDataProvider.generateFakeString(attributeConfig.name))
                  "email" -> faker.internet.email()
                    "first_name" -> faker.name.firstName()
                    "last_name" -> faker.name.lastName()
                    "name" -> faker.name.name()
                    "phone" -> faker.phoneNumber.phoneNumber()
                    "department" -> faker.company.department()
                else -> throw IllegalArgumentException("Invalid generator '$generator'")

        else -> generateDefaultValue(attribute, entity, rellSchema, existingData)
                referenceType or primitiveType
                is R_IntegerType -> random.nextInt(-1000, 1000)
                is R_BigIntegerType -> BigInteger.valueOf(random.nextLong(-1000, 1000))
                is R_DecimalType -> BigDecimal(random.nextDouble(-1000.0, 1000.0))
                is R_BooleanType -> random.nextBoolean()
                is R_TextType -> fakeDataProvider.generateRandomText()
                is R_ByteArrayType -> generateRandomBytes()
                is R_RowidType -> random.nextLong(1, 1000000)
                else -> throw IllegalArgumentException("Unsupported type: $type")
     */
}