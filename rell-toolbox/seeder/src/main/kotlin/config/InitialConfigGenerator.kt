/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.associateNotNullValues
import net.postchain.rell.toolbox.seeder.schema.Attribute
import net.postchain.rell.toolbox.seeder.schema.Entity
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import net.postchain.rell.toolbox.seeder.config.serializer.ConfigurationSerializer
import java.nio.file.Path

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

    private fun createFieldConfig(type: RR_Type): AttributeConfig? {
        return when (type) {
            is RR_Type.Primitive -> when (type.kind) {
                RR_PrimitiveKind.INTEGER -> AttributeConfig.Range(0, 1000)
                RR_PrimitiveKind.BIG_INTEGER -> AttributeConfig.Range(0, 1000)
                RR_PrimitiveKind.DECIMAL -> AttributeConfig.Range(0, 1000)
                RR_PrimitiveKind.TEXT -> AttributeConfig.TextConfig()
                RR_PrimitiveKind.BOOLEAN -> AttributeConfig.DataPatternConfig("random.boolean")
                RR_PrimitiveKind.BYTE_ARRAY -> AttributeConfig.ByteArrayConfig()
                RR_PrimitiveKind.ROWID -> AttributeConfig.Range(1, 1000)
                RR_PrimitiveKind.JSON -> AttributeConfig.DataPatternConfig("random.json")
                else -> null
            }
            is RR_Type.Enum -> AttributeConfig.DataPatternConfig("random.enum")
            else -> null // Skipping generating default generator for other types
        }
    }

    companion object {
        const val DEFAULT_ENTITY_COUNT = 10
    }
}
