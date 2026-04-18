/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.schema

import assertk.Assert
import assertk.assertThat
import assertk.assertions.*
import net.postchain.rell.base.model.R_EnumType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class SchemaReaderTest {

    private val testResourcesDir = File(javaClass.getResource("/seeder-test/src")!!.toURI())
    private lateinit var schemaReader: SchemaReader

    @BeforeEach
    fun setup() {
        schemaReader = SchemaReader()
    }

    @Test
    fun `correctly reads all entities from source directory from specified modules`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("game.entities"))
        val entityNames = schema.entities.map { it.simpleName }

        assertThat(
            entityNames
        ).containsExactlyInAnyOrder(
            "player", "npc", "item", "quest", "location", "inventory", "inventory_item", "enemy", "loot_table",
        )
    }

    @Test
    fun `entities should be sorted in topological order`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("topological_order"))
        val entityNames = schema.entities.map { it.simpleName }
        assertThat(entityNames).containsExactly(
            "grand_father",
            "parent",
            "child",
            "orphan"
        )
    }

    @Test
    fun `correctly reads predefined alias types`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("type_aliases"))

        val entityWithAliases = schema.entities.find { it.simpleName == "entity_typealiases" }
        assertThat(entityWithAliases).isNotNull()
        assertThat(entityWithAliases!!.simpleName).isEqualTo("entity_typealiases")

        val attributeTypes = entityWithAliases.attributes.associate { it.name to it.type.name }

        assertThat(attributeTypes["my_pub_key"]).isEqualTo("byte_array")
        assertThat(attributeTypes["my_created_at"]).isEqualTo("integer")
        assertThat(attributeTypes["my_tuid"]).isEqualTo("text")
        assertThat(attributeTypes["my_name"]).isEqualTo("text")

        assertThat(attributeTypes["tuid"]).isEqualTo("text")
        assertThat(attributeTypes["name"]).isEqualTo("text")
        assertThat(attributeTypes["pubkey"]).isEqualTo("byte_array")
        assertThat(attributeTypes["timestamp"]).isEqualTo("integer")
    }

    @Test
    fun `correctly reads nested namespaces`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("namespace.nested"))

        val ecommerceEntities = schema.entities.filter {
            it.qualifiedName.startsWith("ecommerce")
        }

        val shopEntity = ecommerceEntities.first { it.qualifiedName == "ecommerce.shop" }
        shopEntity.assertNames("shop", "ecommerce.shop", "namespace.nested:ecommerce.shop", "namespace.nested")

        val shopProduct = ecommerceEntities.first { it.qualifiedName == "ecommerce.product.shop_product" }
        shopProduct.assertNames(
            "shop_product",
            "ecommerce.product.shop_product",
            "namespace.nested:ecommerce.product.shop_product",
            "namespace.nested"
        )

        val shopOrder = ecommerceEntities.first { it.qualifiedName == "ecommerce.shop_order" }
        shopOrder.assertNames(
            "shop_order",
            "ecommerce.shop_order",
            "namespace.nested:ecommerce.shop_order",
            "namespace.nested"
        )

        val orderItem = ecommerceEntities.first { it.qualifiedName == "ecommerce.product.order_item" }
        orderItem.assertNames(
            "order_item",
            "ecommerce.product.order_item",
            "namespace.nested:ecommerce.product.order_item",
            "namespace.nested"
        )
    }

    @Test
    fun `correctly reads entity indices and keys`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("keys_and_indices"))

        val houseEntity = schema.entities.first { it.simpleName == "house" }

        val compositeKey = houseEntity.keys[0].strAttribs
        assertThat(compositeKey).containsExactly("street", "number")
        val key = houseEntity.keys[1]
        assertThat(key.strAttribs).containsExactly("floor_area")

        val index = houseEntity.indices[0]
        assertThat(index.strAttribs).containsExactly("street")

        val compositeIndex = houseEntity.indices[1]
        assertThat(compositeIndex.strAttribs).containsExactly("number_of_rooms", "floor_area")
    }

    @Test
    fun `entities only read from specified modules`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("namespace.nested"))

        val entityNames = schema.entities.map { it.simpleName }
        assertThat(entityNames).containsExactlyInAnyOrder(
            "product_with_constraints",
            "shop",
            "order_item",
            "shop_order",
            "shop_product"
        )
    }

    @Test
    fun `attributes should be correctly extracted`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("entity_with_all_types"))

        val entity = schema.entities.first { it.simpleName == "employee" }

        val attributes = entity.attributes.map { it.name to it.type.name }
        assertThat(attributes).containsExactly(
            "age" to "integer",
            "active" to "boolean",
            "salary" to "big_integer",
            "weight" to "decimal",
            "name" to "text",
            "photo" to "byte_array",
            "row_count" to "rowid",
            "meta" to "json",
            "type" to "entity_with_all_types:type",
            "address" to "entity_with_all_types:address"
        )
    }

    @Test
    fun `throws SchemaReaderException when compilation fails`() {
        assertThrows<SchemaReaderException> {
            schemaReader.readSchema(testResourcesDir, listOf("with_errors"))
        }
    }

    @Test
    fun `correctly reads enum references`() {
        val schema = schemaReader.readSchema(testResourcesDir, listOf("entity_with_enum"))

        val person = schema.entities.first { it.simpleName == "person" }

        val shirtSizeAttr = person.attributes.first { it.name == "shirt_size" }
        val enumType = shirtSizeAttr.type
        assertThat(enumType).isInstanceOf(R_EnumType::class).hasValues(listOf("small", "medium", "large"))
    }

    @Test
    fun `throws SchemaReaderException when module name is invalid`() {
        assertThrows<SchemaReaderException> {
            schemaReader.readSchema(testResourcesDir, listOf("invalid.module.name"))
        }
    }

    @Test
    fun `successfully handles empty modules list`() {
        val schema = schemaReader.readSchema(testResourcesDir, emptyList())

        assertThat(schema.entities).isEmpty()
    }

    private fun Entity.assertNames(simpleName: String, qualifiedName: String, uniqueName: String, moduleName: String) {
        assertThat(this.simpleName).isEqualTo(simpleName)
        assertThat(this.qualifiedName).isEqualTo(qualifiedName)
        assertThat(this.moduleName).isEqualTo(moduleName)
        assertThat(this.uniqueName).isEqualTo(uniqueName)
    }

    private fun Assert<R_EnumType>.hasValues(values: List<String>) = prop("values", R_EnumType::values)
        .transform { enumValues -> enumValues.map { it.str() } }
        .containsExactlyInAnyOrder(*values.toTypedArray())

}