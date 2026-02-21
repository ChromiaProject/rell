/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.utils.Rt_Messages
import net.postchain.rell.base.utils.*
import org.jooq.conf.ParamType
import org.jooq.impl.DSL

private const val MSG_BROKEN_META = "Broken metadata"

object SqlMeta {
    private val CREATE_TABLE_META_ENTITIES = """
    CREATE TABLE "%s"(
        "id" INT NOT NULL PRIMARY KEY,
        "name" TEXT NOT NULL UNIQUE,
        "type" TEXT NOT NULL,
        "log" BOOLEAN NOT NULL
    );
    """.trimIndent()

    private val CREATE_TABLE_META_ATTRIBUTES = """
    CREATE TABLE "%s"(
        "class_id" INT NOT NULL,
        "name" TEXT NOT NULL,
        "type" TEXT NOT NULL
    );
    """.trimIndent()

    fun checkMetaTablesExisting(
        mapping: Rt_ChainSqlMapping,
        tables: ImmMap<String, SqlTable>,
        msgs: Rt_Messages,
    ): Boolean {
        val metaTables = metaTables(mapping)
        val metaExists = metaTables.any { it in tables }
        if (metaExists) {
            val metaMissing = metaTables.filter { it !in tables }
            msgs.errorIfNotEmpty(metaMissing, "meta:notables", "$MSG_BROKEN_META: missing table(s)")
        }

        val entityChk = SqlTableChecker(tables, mapping.metaEntitiesTable)
        entityChk.checkColumn("id", "int4")
        entityChk.checkColumn("name", "text")
        entityChk.checkColumn("type", "text")
        entityChk.checkColumn("log", "bool")
        entityChk.finish(msgs)

        val attrChk = SqlTableChecker(tables, mapping.metaAttributesTable)
        attrChk.checkColumn("class_id", "int4")
        attrChk.checkColumn("name", "text")
        attrChk.checkColumn("type", "text")
        attrChk.finish(msgs)

        return metaExists
    }

    private fun metaTables(mapping: Rt_ChainSqlMapping): List<String> {
        return listOf(mapping.metaEntitiesTable, mapping.metaAttributesTable)
    }

    fun loadMetaData(sqlExec: SqlExecutor, mapping: Rt_ChainSqlMapping, msgs: Rt_Messages): ImmMap<String, MetaEntity> {
        val metaEntities = selectMetaEntities(mapping, sqlExec, msgs)
        val metaAttrs = selectMetaAttrs(mapping, sqlExec, msgs)
        msgs.checkErrors()

        val entityMap = metaEntities.associateBy { it.id }
        val attrMap = metaAttrs.groupBy { it.classId }

        for (classId in attrMap.keys.sorted()) {
            if (classId !in entityMap) {
                val msg = "$MSG_BROKEN_META: attributes without entity (class_id = $classId)"
                msgs.error("meta:attr_no_entity:$classId", msg)
            }
        }
        msgs.checkErrors()

        val res = mutableMapOf<String, MetaEntity>()
        for (entityRec in metaEntities) {
            val type = decodeEntityType(entityRec, msgs)
            if (type == null) continue
            val attrs = attrMap[entityRec.id].orEmpty()
            val resAttrMap = attrs.associateToImmMap { it.name to MetaAttr(it.name, it.type) }
            res[entityRec.name] = MetaEntity(entityRec.id, entityRec.name, type, entityRec.log, resAttrMap)
        }

        return res.toImmMap()
    }

    private fun decodeEntityType(rec: RecMetaEntity, msgs: Rt_Messages): MetaEntityType? {
        for (type in MetaEntityType.entries) {
            if (rec.type == type.code) {
                return type
            }
        }

        msgs.error("meta:entity:bad_type:${rec.id}:${rec.name}:${rec.type}",
                "$MSG_BROKEN_META: Invalid type of meta-class '${rec.name}' (ID = ${rec.id}): '${rec.type}'")
        return null
    }

    private fun selectMetaEntities(
        mapping: Rt_ChainSqlMapping,
        sqlExec: SqlExecutor,
        msgs: Rt_Messages,
    ): List<RecMetaEntity> {
        val table = mapping.metaEntitiesTable
        val res = mutableListOf<RecMetaEntity>()

        val sql = """SELECT T."id", T."name", T."type", T."log" FROM "$table" T ORDER BY T."id";"""
        sqlExec.executeQuery(sql, {}) { rs ->
            val id = rs.getInt(1)
            val name = rs.getString(2)!!
            val type = rs.getString(3)!!
            val log = rs.getBoolean(4)
            res.add(RecMetaEntity(id, name, type, log))
        }

        checkUniqueKeys(msgs, table, res) { "${it.id}" }
        checkUniqueKeys(msgs, table, res) { it.name }

        return res
    }

    private fun selectMetaAttrs(
        mapping: Rt_ChainSqlMapping,
        sqlExec: SqlExecutor,
        msgs: Rt_Messages,
    ): List<RecMetaAttr> {
        val table = mapping.metaAttributesTable
        val res = mutableListOf<RecMetaAttr>()

        val sql = """SELECT T."class_id", T."name", T."type" FROM "$table" T ORDER BY T."class_id", T."name";"""
        sqlExec.executeQuery(sql, {}) { rs ->
            val classId = rs.getInt(1)
            val name = rs.getString(2)!!
            val type = rs.getString(3)!!
            res.add(RecMetaAttr(classId, name, type))
        }

        checkUniqueKeys(msgs, table, res) { "${it.classId}:${it.name}" }
        return res
    }

    private fun <T> checkUniqueKeys(msgs: Rt_Messages, table: String, list: List<T>, getter: (T) -> String) {
        val unique = mutableSetOf<String>()
        val dup = mutableSetOf<String>()
        for (rec in list) {
            val key = getter(rec)
            if (!unique.add(key)) {
                dup.add(key)
            }
        }
        msgs.errorIfNotEmpty(dup, "meta:dupkeys:$table", "$MSG_BROKEN_META: duplicate value(s) in table $table")
    }

    fun checkDataTables(
        sqlCtx: Rt_SqlContext,
        tables: Map<String, SqlTable>,
        metaData: Map<String, MetaEntity>,
        msgs: Rt_Messages,
    ) {
        val mapping = sqlCtx.mainChainMapping()

        val metaTables = metaData.keys.map { mapping.fullName(it) }.toSet()
        val missingTables = metaTables.filter { it !in tables }

        val dataTables = (tables.keys - metaTables(mapping)).filter { !mapping.isSystemTable(it) }
        val missingEntities = dataTables.filter { it !in metaTables }

        msgs.errorIfNotEmpty(missingTables, "meta:no_data_tables", "Missing tables for existing metadata entities")
        msgs.errorIfNotEmpty(missingEntities, "meta:no_meta_entities", "Missing metadata entities for existing tables")

        for (entity in metaData.values) {
            val table = mapping.fullName(entity.name)
            val sqlTable = tables[table]
            if (sqlTable != null) {
                checkDataTable(table, sqlTable, entity, msgs)
            }
        }
    }

    private fun checkDataTable(table: String, sqlTable: SqlTable, metaEntity: MetaEntity, msgs: Rt_Messages) {
        val missingCols = (metaEntity.attrs.keys + SqlConstants.ROWID_COLUMN).filter { it !in sqlTable.cols }
        val missingAttrs = (sqlTable.cols.keys - SqlConstants.ROWID_COLUMN).filter { it !in metaEntity.attrs }

        msgs.errorIfNotEmpty(missingCols, "meta:no_data_columns:$table",
                "Missing columns for existing meta attributes in table $table")

        msgs.errorIfNotEmpty(missingAttrs, "meta:no_meta_attrs:$table",
                "Missing meta attributes for existing columns in table $table")
    }

    fun genMetaTablesCreate(sqlCtx: Rt_SqlContext): ImmList<String> {
        val sqls = mutableListOf<String>()
        val mainChainMapping = sqlCtx.mainChainMapping()
        sqls += CREATE_TABLE_META_ENTITIES.formatEx(mainChainMapping.metaEntitiesTable)
        sqls += CREATE_TABLE_META_ATTRIBUTES.formatEx(mainChainMapping.metaAttributesTable)
        return sqls.toImmList()
    }

    fun genMetaEntityInserts(
        sqlCtx: Rt_SqlContext,
        classId: Int,
        entity: R_EntityDefinition,
        entityType: MetaEntityType,
    ): List<String> {
        val sqls = mutableListOf<String>()

        val entityTable = DSL.table(DSL.name(sqlCtx.mainChainMapping().metaEntitiesTable))

        sqls += SqlGen.DSL_CTX.insertInto(
            entityTable,
            DSL.field("id"),
            DSL.field("name"),
            DSL.field("type"),
            DSL.field("log"),
        ).values(
            classId,
            entity.metaName,
            entityType.code,
            entity.flags.log,
        ).getSQL(ParamType.INLINED) + ";"

        sqls += genMetaAttrsInserts(sqlCtx, classId, entity.attributes.values)

        return sqls
    }

    fun genMetaAttrsInserts(sqlCtx: Rt_SqlContext, classId: Int, attrs: Collection<R_Attribute>): ImmList<String> {
        val sqls = mutableListOf<String>()

        val attrTable = DSL.table(DSL.name(sqlCtx.mainChainMapping().metaAttributesTable))

        for (attr in attrs) {
            sqls += SqlGen.DSL_CTX.insertInto(
                attrTable,
                DSL.field("class_id"),
                DSL.field("name"),
                DSL.field("type"),
            ).values(
                classId,
                attr.sqlMapping,
                attr.type.sqlAdapter.metaName(sqlCtx),
            ).getSQL(ParamType.INLINED) + ";"
        }

        return sqls.toImmList()
    }

    fun genMetaAttrsDeletes(sqlCtx: Rt_SqlContext, classId: Int, attrNames: List<String>): ImmList<String> {
        val sqls = mutableListOf<String>()

        val attrTable = DSL.table(DSL.name(sqlCtx.mainChainMapping().metaAttributesTable))

        for (attrName in attrNames) {
            sqls += SqlGen.DSL_CTX
                .deleteFrom(attrTable)
                .where(
                    DSL.field("class_id").eq(classId)
                        .and(DSL.field("name").eq(attrName))
                ).getSQL(ParamType.INLINED) + ";"
        }

        return sqls.toImmList()
    }
}

private class SqlTableChecker(private val tables: ImmMap<String, SqlTable>, private val table: String) {
    private val missingCols = mutableListOf<String>()
    private val checkedCols = mutableListOf<String>()
    private val diffCols = mutableMapOf<String, Pair<String, String>>()

    fun checkColumn(name: String, type: String) {
        val t = tables[table]
        t ?: return

        checkedCols.add(name)

        val col = t.cols[name]
        if (col == null) {
            missingCols.add(name)
        } else if (col.type != type) {
            diffCols[name] = Pair(type, col.type)
        }
    }

    fun finish(msgs: Rt_Messages) {
        val t = tables[table]
        t ?: return

        msgs.errorIfNotEmpty(missingCols, "meta:nocols:$table", "$MSG_BROKEN_META: missing column(s) in table $table")

        val extraCols = (t.cols.keys - checkedCols).toList()
        msgs.errorIfNotEmpty(extraCols, "meta:extracols:$table", "$MSG_BROKEN_META: wrong column(s) in table $table")

        for ((col, types) in diffCols) {
            val (expType, actType) = types
            msgs.error("meta:coltype:$table:$col:$expType:$actType",
                    "$MSG_BROKEN_META: wrong type of column $table.$col: $actType instead of $expType")
        }
    }
}

private class RecMetaEntity(val id: Int, val name: String, val type: String, val log: Boolean)

private class RecMetaAttr(val classId: Int, val name: String, val type: String)

enum class MetaEntityType(val code: String, val en: String) {
    ENTITY("class", "entity"),
    OBJECT("object", "object")
    ;
}

class MetaEntity(
    val id: Int,
    val name: String,
    val type: MetaEntityType,
    val log: Boolean,
    val attrs: ImmMap<String, MetaAttr>,
)

class MetaAttr(val name: String, val type: String)
