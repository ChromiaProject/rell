/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

data class GeneratedData(
    val entityData: Map<String, List<EntityRecord>>
)

data class EntityRecord(
    val uniqueName: String,
    val entityName: String,
    val fields: Map<String, FieldValue<Any>?>
)

data class FieldValue<out T>(
    val value: T,
    val isReference: Boolean = false,
    val entityReferenceType: String? = null,
)
