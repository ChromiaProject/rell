/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.exporter

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.generator.EntityRecord
import net.postchain.rell.toolbox.seeder.generator.GeneratedData
import java.io.FileWriter
import java.math.BigDecimal
import java.nio.file.Path

class RellDataExporter : BaseDataExporter() {
    companion object {
        const val DEFAULT_BATCH_SIZE = 1000
        private val seederModuleWarning = """
        /*
         * WARNING: SEEDER MODULE - TEST DATA ONLY
         *
         * This module contains seed_data operation intended for testing and development purposes only.
         * Including this module in production blockchain environments can lead to unintended consequences.
         *
         * RECOMMENDED ACTION:
         * Ensure this module is not imported in any production blockchain modules.
         *
         */
        """.trimIndent()
    }

    private val objectMapper = ObjectMapper().apply {
        val module = SimpleModule()
        module.addSerializer(
            WrappedByteArray::class.java,
            object : JsonSerializer<WrappedByteArray>() {
                override fun serialize(value: WrappedByteArray, gen: JsonGenerator, serializers: SerializerProvider) {
                    val hexString = value.data.joinToString("") { "%02x".format(it) }
                    gen.writeString(hexString)
                }
            }
        )
        module.addSerializer(
            BigDecimal::class.java,
            object : JsonSerializer<BigDecimal>() {
                override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider) {
                    gen.writeString(value.toString())
                }
            }
        )
        registerModule(module)
    }

    private val importsMap = mutableMapOf<String, String>()

    override fun export(data: GeneratedData, schema: RellSchema, outputPath: Path, mountName: String) {
        val outputFile = prepareOutputFile(outputPath)
        FileWriter(outputFile).use { writer ->
            writer.write("$seederModuleWarning\n")
            writer.write("@mount(\"$mountName\")\n")
            writer.write("module;\n\n")

            generateImports(data, schema, writer)

            writer.write("${getEntityIdFunctionString(data)}\n\n")

            for ((uniqueEntityName, records) in data.entityData) {
                writer.write(generateEntitySeederFunction(uniqueEntityName, records, data))
                writer.write("\n\n")
            }

            // Seed data operation
            writer.write(generateSeedDataOperation(data, outputFile.nameWithoutExtension))
            writer.write("\n\n")

            // Helper functions
            writer.write("${iterationStateStructString()}\n\n")
            writer.write("${textInsertRowidFunctionString()}\n\n")
            writer.write("${resolveRefFunctionString()}\n\n")
            writer.write("${resolveTransactionFunctionString()}\n\n")
            writer.write("${resolveBlockFunctionString()}\n")
        }
    }

    private fun generateImports(data: GeneratedData, schema: RellSchema, writer: FileWriter) {
        var importCounter = 1
        schema.entities.groupBy { it.moduleName }.forEach { (moduleName, entities) ->
            val importStatement = "import mod$importCounter: $moduleName;"
            writer.write("$importStatement\n")
            for (entity in entities) {
                val qualifiedName = entity.qualifiedName
                importsMap[entity.uniqueName] = "mod$importCounter.$qualifiedName"
            }
            importCounter++
        }
        writer.write("\n")
    }

    private fun generateEntitySeederFunction(
        uniqueEntityName: String,
        records: List<EntityRecord>,
        data: GeneratedData
    ): String {
        val entityMap = getEntityMap(data)
        val referenceData = mutableListOf<List<Long>>()
        val processedData = records.map { record ->
            record.fields.map {
                if (it.value?.isReference == true) {
                    referenceData.add(
                        listOf(entityMap[it.value!!.entityReferenceType]!!.toLong(), (it.value!!.value as Long))
                    )
                    "%REF"
                } else {
                    it.value?.value
                }
            }
        }

        val refData = toJson(referenceData)
        val jsonData = toJson(processedData)

        val simpleEntityName = importsMap[uniqueEntityName]
        val functionName = getFunctionName(uniqueEntityName)

        return """
            |function seed_$functionName(existing_data: map<integer, gtv>, batch_size: integer = $DEFAULT_BATCH_SIZE) {
            |    val ref_data = '$refData';
            |    val json_data_without_ref = '$jsonData';
            |    val json_data_resolved_ref = resolve_refs(existing_data, json_data_without_ref, ref_data);
            |    val json_data_resolved_tx = resolve_tx(json_data_resolved_ref);
            |    val json_data = resolve_block(json_data_resolved_tx);
            |    val records = list<struct<$simpleEntityName>>.from_gtv_pretty(gtv.from_json(json_data));
            |    var batch = list<struct<$simpleEntityName>>();
            |    var persisted = list<$simpleEntityName>();
            |
            |    var count = 0;
            |    for (r in records) {
            |        batch.add(r);
            |        count++;
            |        if (count % batch_size == 0) {
            |            persisted.add_all(create $simpleEntityName(batch));
            |            batch.clear();
            |        }
            |    }
            |    if (count % batch_size != 0) {
            |        persisted.add_all(create $simpleEntityName(batch));
            |    }
            |
            |    existing_data[get_entity_id(rell.meta($simpleEntityName).full_name)] = persisted.to_gtv();
            |}
        """.trimMargin()
    }

    private fun getFunctionName(uniqueEntityName: String): String {
        val functionName = uniqueEntityName.replace(":", "_").replace(".", "_")
        return functionName
    }

    private fun generateSeedDataOperation(data: GeneratedData, fileName: String): String {
        val seedCalls = data.entityData.keys.joinToString("\n    ") { entityName ->
            val functionName = getFunctionName(entityName)
            "seed_$functionName(existing_data, batch_size);"
        }

        return """
            |operation seed_data(batch_size: integer = $DEFAULT_BATCH_SIZE) {
            |    val existing_data = map<integer, gtv>();
            |    $seedCalls
            |}
        """.trimMargin()
    }

    private fun toJson(data: Any): String {
        return objectMapper.writeValueAsString(data).replace("'", "\\'").replace("\\\"", "\\\\\"")
    }

    private fun getEntityMap(data: GeneratedData) =
        data.entityData.keys.withIndex().associate { it.value to it.index }

    private fun getEntityIdFunctionString(data: GeneratedData): String {
        val entityMappingString = getEntityMap(data).entries.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "'${it.key}': ${it.value}" }

        return """
            |function get_entity_id(name: text): integer {
            |    val all_entities: map<text, integer> = $entityMappingString;
            |    return all_entities[name];
            |}
        """.trimMargin()
    }

    private fun iterationStateStructString(): String {
        return """
            |struct iteration_state {
            |    mutable current: integer;
            |    data: list<integer>;
            |}
        """.trimMargin()
    }

    // TODO: Evaluate data.size() operation for 10_000 records, or if we should add size to itteration_state
    private fun textInsertRowidFunctionString(): String {
        return """
            |function text_insert_rowid(value: text, state: iteration_state): text {
            |    val value_with_rowid = if (state.current < state.data.size())
            |        value + state.data[state.current]
            |    else
            |        value;
            |    state.current++;
            |    return value_with_rowid;
            |}
        """.trimMargin()
    }

    private fun resolveRefFunctionString(): String {
        return """
        |function resolve_refs(existing_data: map<integer, gtv>, json_data: text, ref_data: text): text {
        |    val ref_gtv = list<(integer, integer)>.from_gtv_pretty(gtv.from_json(ref_data));
        |    val replacements: list<integer> = [];
        |    for (ref in ref_gtv) {
        |        val rowids = list<integer>.from_gtv_pretty(existing_data[ref[0]]);
        |        replacements.add(rowids[ref[1]]);
        |    }
        |    val parts = json_data.split('"%REF"');
        |    val state = iteration_state(current = 0, data = replacements);
        |    return parts.join_to_text(' ', '', '', null, '', text_insert_rowid(*, state));
        |}
        """.trimMargin()
    }

    private fun resolveTransactionFunctionString(): String {
        return """
        |function resolve_tx(json_data: text): text {
        |    val tx = op_context.transaction;
        |    val tx_as_text = tx.to_gtv().to_json().to_text();
        |    return json_data.replace('"%TX_ENTITY"', tx_as_text);
        |}
        """.trimMargin()
    }

    private fun resolveBlockFunctionString(): String {
        return """
        |function resolve_block(json_data: text): text {
        |    val block = op_context.transaction.block;
        |    val block_as_text = block.to_gtv().to_json().to_text();
        |    return json_data.replace('"%BLOCK_ENTITY"', block_as_text);
        |}
        """.trimMargin()
    }
}
