package net.postchain.rell.toolbox.seeder.config

import net.postchain.rell.toolbox.seeder.RellSchema
import java.nio.file.Path
import net.postchain.rell.base.lib.type.R_BigIntegerType
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_JsonType
import net.postchain.rell.base.lib.type.R_RowidType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.associateNotNullValues
import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.Entity
import net.postchain.rell.toolbox.seeder.config.serializer.ConfigurationSerializer

class InitialConfigGenerator {

    private val configSerializer = ConfigurationSerializer()

    fun generate(rellSchema: RellSchema, outputFolderPath: Path) {
        val outputFolder = outputFolderPath.toFile().apply { mkdirs() }
        val modulesFolder = outputFolder.resolve("modules").apply { mkdirs() }

        val moduleConfigs = mutableMapOf<String, ModuleConfig>()

        rellSchema.entities.groupBy { it.moduleName }.forEach { (moduleName, entities) ->
            val moduleFile = modulesFolder.resolve("${modulePath(moduleName)}.yml")
            val moduleConfig = createModuleConfig(moduleName, entities)
            moduleConfigs[moduleFile.relativeTo(outputFolder).path] = moduleConfig
        }

        configSerializer.serialize(Configuration(moduleConfigs), outputFolderPath)
    }

    private fun modulePath(moduleName: String): String {
        return moduleName.replace(".", "/")
    }

    private fun createModuleConfig(moduleName: String, entities: List<Entity>): ModuleConfig {
        val entityConfigs = entities.associate { entity ->
            entity.qualifiedName to createEntityConfig(entity)
        }
        return ModuleConfig(moduleName, entityConfigs)
    }

    private fun createEntityConfig(entity: Entity): EntityConfig {
        return EntityConfig(
            name = entity.qualifiedName,
            attributes = createAttributeConfig(entity.attributes),
            count = DEFAULT_ENTITY_COUNT
        )
    }

    private fun createAttributeConfig(attributes: List<Attribute>): Map<String, AttributeConfig> {
        return attributes.associateNotNullValues { attribute ->
            attribute.name to createFieldConfig(attribute.type)
        }
    }

    private fun createFieldConfig(type: R_Type): AttributeConfig? {
        return when (type) {
            is R_IntegerType -> AttributeConfig.Range(0, 1000)
            is R_BigIntegerType -> AttributeConfig.Range(0, 1000)
            is R_DecimalType -> AttributeConfig.Range(0, 1000)
            is R_TextType -> AttributeConfig.TextConfig()
            is R_BooleanType -> AttributeConfig.DataPatternConfig("random.boolean")
            is R_ByteArrayType -> AttributeConfig.ByteArrayConfig() // Default handling
            is R_RowidType -> AttributeConfig.Range(1, 1000)
            is R_JsonType -> AttributeConfig.DataPatternConfig("random.json")
            is R_EnumType -> AttributeConfig.DataPatternConfig("random.enum")
            else -> null // Skipping generating default generator for other types
        }
    }

    companion object {
        const val DEFAULT_ENTITY_COUNT = 10
    }
}
