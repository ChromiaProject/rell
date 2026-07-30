/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

internal data class GeneratedData(
    val entityData: Map<String, List<EntityRecord>>
)

internal data class EntityRecord(
    val uniqueName: String,
    val entityName: String,
    val fields: Map<String, FieldValue<Any>?>
)

internal data class FieldValue<out T>(
    val value: T,
    val isReference: Boolean = false,
    val entityReferenceType: String? = null,
)
