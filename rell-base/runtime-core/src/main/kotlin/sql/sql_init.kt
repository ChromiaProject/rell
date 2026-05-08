/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import mu.KLogger
import mu.KLogging
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.rr.RR_Attribute
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_ObjectDefinition
import net.postchain.rell.base.model.rr.RR_SizeConstraint
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Messages
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlUtils.getExistingSizeConstraints
import net.postchain.rell.base.sql.SqlUtils.recordsExist
import net.postchain.rell.base.utils.*
import org.jooq.Field
import org.jooq.impl.DSL

private val ORD_TABLES = SqlInitStepOrder.TABLES
private val ORD_RECORDS = SqlInitStepOrder.RECORDS

class SqlInitLogging(
    val header: Boolean = false,
    val plan: Boolean = false,
    val planEmptyDb: Boolean = false,
    val step: Boolean = false,
    val stepEmptyDb: Boolean = false,
    val metaNoCode: Boolean = false,
    val allOther: Boolean = false,
) {
    companion object {
        const val LOG_NONE = 0
        const val LOG_HEADER = 1000
        const val LOG_STEP_COMPLEX = 2000
        const val LOG_STEP_SIMPLE = 3000
        const val LOG_PLAN_COMPLEX = 4000
        const val LOG_PLAN_SIMPLE = 5000
        const val LOG_ALL = Integer.MAX_VALUE

        fun ofLevel(level: Int) = SqlInitLogging(
            header = level >= LOG_HEADER,
            plan = level >= LOG_PLAN_COMPLEX,
            planEmptyDb = level >= LOG_PLAN_SIMPLE,
            step = level >= LOG_STEP_COMPLEX,
            stepEmptyDb = level >= LOG_STEP_SIMPLE,
            metaNoCode = level > LOG_NONE,
            allOther = level >= LOG_ALL,
        )
    }
}

interface SqlInitProjExt: ProjExt {
    fun initExtra(exeCtx: Rt_ExecutionContext)
}

object NullSqlInitProjExt: SqlInitProjExt {
    override fun initExtra(exeCtx: Rt_ExecutionContext) {
        // Do nothing.
    }
}

class SqlInit private constructor(
    private val exeCtx: Rt_ExecutionContext,
    private val projExt: SqlInitProjExt,
    private val logging: SqlInitLogging,
    isSnapshot: Boolean,
) {
    private val sqlCtx = exeCtx.sqlCtx

    private val initCtx = SqlInitCtx(logger, logging, SqlObjectsInit(exeCtx), isSnapshot)

    companion object : KLogging() {
        /**
         * Runs the database init plan.
         *
         * Multi-statement steps (e.g. ALTER TABLE … ADD COLUMN; UPDATE; SET NOT NULL) are now
         * dispatched as one round-trip per statement (no batch join, since [ExecutableSql] can
         * carry binds). The caller MUST wrap this call in a transaction so that a mid-step failure
         * rolls back the entire init plan; otherwise a partial DDL apply is possible.
         */
        fun init(
            exeCtx: Rt_ExecutionContext,
            adapter: SqlInitProjExt,
            logging: SqlInitLogging,
            isSnapshot: Boolean = false,
        ): List<String> {
            val obj = SqlInit(exeCtx, adapter, logging, isSnapshot)
            return obj.init()
        }
    }

    private fun init(): List<String> {
        log(logging.header, "Initializing database (chain_iid = ${sqlCtx.mainChainMapping().chainId})")

        if (initCtx.isSnapshot) {
            dropEntityTables()
        }

        val dbEmpty = SqlInitPlanner.plan(exeCtx, initCtx)
        initCtx.checkErrors()

        projExt.initExtra(exeCtx)

        exeCtx.appCtx.objectsInitialization(initCtx.objsInit) {
            executePlan(dbEmpty)
        }

        return initCtx.msgs.warningCodes()
    }

    private fun dropEntityTables() {
        val sqlDefs = exeCtx.appCtx.rrApp.sqlDefs
        val allEntities = sqlDefs.entities + sqlDefs.objects.map { it.rEntity }

        for (entity in allEntities) {
            val tableName = entity.sqlMapping.table(sqlCtx)
            val drop = JOOQ_CTX.dropTableIfExists(DSL.name(tableName)).cascade()
            exeCtx.sysSqlExec.execute(JooqDdlStatement(drop))
        }

        val mapping = sqlCtx.mainChainMapping()
        for (metaTable in listOf(mapping.metaEntitiesTable, mapping.metaAttributesTable)) {
            val del = JOOQ_CTX.deleteFrom(DSL.table(DSL.name(metaTable)))
            exeCtx.sysSqlExec.execute(JooqDdlStatement(del))
        }
    }

    private fun executePlan(dbEmpty: Boolean) {
        val steps = initCtx.steps()
        if (steps.isEmpty()) {
            log(logging.allOther, "Nothing to do")
            return
        }

        val stepLogAllowed = if (dbEmpty) logging.stepEmptyDb else logging.step
        log(stepLogAllowed, "Database init plan: ${steps.size} step(s)")

        val planLogAllowed = if (dbEmpty) logging.planEmptyDb else logging.plan
        for (step in steps) {
            log(planLogAllowed, "    ${step.title}")
        }

        val stepCtx = SqlStepCtx(logger, exeCtx, initCtx.objsInit)
        for (step in steps) {
            log(stepLogAllowed, "Step: ${step.title}")
            step.action.run(stepCtx)
        }

        log(logging.allOther, "Database initialization done")
    }

    private fun log(allowed: Boolean, s: String) {
        if (allowed) {
            logger.info(s)
        }
    }
}

private class SqlInitPlanner private constructor(
    private val exeCtx: Rt_ExecutionContext,
    private val initCtx: SqlInitCtx,
) {
    private val sqlCtx = exeCtx.sqlCtx
    private val mapping = sqlCtx.mainChainMapping()

    companion object {
        fun plan(exeCtx: Rt_ExecutionContext, initCtx: SqlInitCtx): Boolean {
            val obj = SqlInitPlanner(exeCtx, initCtx)
            return obj.plan()
        }
    }

    private fun plan(): Boolean {
        val tables = exeCtx.sysSqlExec.connection { con ->
            SqlUtils.getExistingChainTables(con, mapping)
        }

        val functions = SqlUtils.getExistingFunctions(exeCtx.sysSqlExec).toImmSet()

        val metaExists = SqlMeta.checkMetaTablesExisting(mapping, tables, initCtx.msgs)
        initCtx.checkErrors()

        val metaData = processMeta(metaExists, tables)
        initCtx.checkErrors()

        processFunctions(functions)
        initCtx.checkErrors()

        SqlEntityIniter.processEntities(exeCtx, initCtx, metaData, tables)
        return !metaExists
    }

    private fun processMeta(metaExists: Boolean, tables: Map<String, SqlTable>): ImmMap<String, MetaEntity> {
        if (!metaExists) {
            initCtx.step(ORD_TABLES, "Create ROWID table and function", SqlStepAction_ExecSql(SqlGen.genRowidSql(mapping).toImmList()))
            initCtx.step(ORD_TABLES, "Create meta tables", SqlStepAction_ExecSql(SqlMeta.genMetaTablesCreate(sqlCtx)))
        }

        val metaData = if (!metaExists) immMapOf() else SqlMeta.loadMetaData(exeCtx.sysSqlExec, mapping, initCtx.msgs)
        initCtx.checkErrors()

        SqlMeta.checkDataTables(sqlCtx, tables, metaData, initCtx.msgs)
        initCtx.checkErrors()

        return metaData
    }

    private fun processFunctions(functions: Set<String>) {
        // Chain-specific rowid function — still created here, per chain.
        val (rowidsFn, rowidsSql) = SqlGen.genFunctionMakeRowids(sqlCtx.mainChainMapping())
        if (rowidsFn !in functions) {
            initCtx.step(ORD_TABLES, "Create function: '$rowidsFn'", SqlStepAction_ExecSql(rowidsSql))
        }

        // Global system functions are now created by RellGlobalStorageInitializer at node startup,
        // in a committed transaction before any blockchain starts, to avoid deadlocks when multiple
        // blockchains initialize concurrently. See RellGlobalStorageInitializer.
    }
}

private class SqlEntityIniter private constructor(
    private val exeCtx: Rt_ExecutionContext,
    private val initCtx: SqlInitCtx,
    private val metaData: ImmMap<String, MetaEntity>,
    private val sqlTables: ImmMap<String, SqlTable>,
) {
    private val sqlCtx = exeCtx.sqlCtx
    private val interpreter = exeCtx.appCtx.interpreter

    private var nextMetaEntityId = 1 + (metaData.values.maxOfOrNull { it.id } ?: -1)

    private fun processEntities() {
        val appDefs = sqlCtx.appDefs

        for (entity in appDefs.topologicalEntities) {
            if (entity.sqlMapping.autoCreateTable) {
                processEntity(entity, MetaEntityType.ENTITY)
            }
        }

        for (obj in appDefs.objects) {
            processObject(obj)
        }

        val codeEntities = appDefs.entities
                .plus(appDefs.objects.map { it.rEntity })
                .map { it.sqlMapping.metaName }
                .toSet()

        for (metaEntity in metaData.values.filter { it.name !in codeEntities }) {
            if (initCtx.logging.metaNoCode) {
                // No need to print this warning in a Console App.
                initCtx.msgs.warning("dbinit:no_code:${metaEntity.type}:${metaEntity.name}",
                        "Table for undefined ${metaEntity.type.en} '${metaEntity.name}' found")
            }
        }
    }

    private fun processObject(obj: RR_ObjectDefinition) {
        val metaEntity = processEntity(obj.rEntity, MetaEntityType.OBJECT)
        if (metaEntity == null) {
            val entityName = msgEntityName(obj.rEntity)
            initCtx.step(ORD_RECORDS, "Create record for object $entityName", SqlStepAction_InsertObject(obj))
            initCtx.objsInit.addObject(obj)
        }
    }

    private fun processEntity(entity: RR_EntityDefinition, type: MetaEntityType): MetaEntity? {
        val metaEntity = metaData[entity.sqlMapping.metaName]
        if (metaEntity == null) {
            processNewEntity(entity, type)
        } else {
            processExistingEntity(entity, type, metaEntity)
        }
        return metaEntity
    }

    private fun processNewEntity(entity: RR_EntityDefinition, type: MetaEntityType) {
        val sqls = mutableListOf<ExecutableSql>()
        sqls += SqlGen.genEntity(sqlCtx, entity, interpreter, !initCtx.isSnapshot)

        val id = nextMetaEntityId++
        sqls += SqlMeta.genMetaEntityInserts(sqlCtx, id, entity, interpreter, type)

        val entityName = msgEntityName(entity)
        initCtx.step(ORD_TABLES, "Create table and meta for $entityName", SqlStepAction_ExecSql(sqls.toImmList()))
    }

    private fun processExistingEntity(entity: RR_EntityDefinition, type: MetaEntityType, metaEnt: MetaEntity) {
        val metaName = entity.sqlMapping.metaName
        if (type != metaEnt.type) {
            val clsName = msgEntityName(entity)
            initCtx.msgs.error("meta:entity:diff_type:$metaName:${metaEnt.type}:$type",
                    "Cannot initialize database: $clsName was ${metaEnt.type.en}, now ${type.en}")
        }

        val newLog = entity.flags.log
        if (newLog != metaEnt.log) {
            val oldLog = metaEnt.log
            val clsName = msgEntityName(entity)
            initCtx.msgs.error("meta:entity:diff_log:$metaName:$oldLog:$newLog",
                    "Log annotation of $clsName was $oldLog, now $newLog")
        }

        checkAttrTypes(entity, metaEnt)

        val entityCols = entity.attributes.values.map { it.sqlMapping }.toSet()
        val removedAttrs = metaEnt.attrs.keys.filter { it !in entityCols }
        if (removedAttrs.isNotEmpty()) {
            if (REMOVED_ATTRS_DROP_COLUMNS_SWITCH.isActive(exeCtx.globalCtx.compilerOptions)) {
                processRemovedAttrs(entity, metaEnt.id, removedAttrs)
            } else {
                checkOldAttrs(entity, metaEnt, removedAttrs)
            }
        }

        val metaCols = metaEnt.attrs.keys.toSet()
        val newAttrs = entity.attributes.values.map { it.sqlMapping }.filter { it !in metaCols }
        if (newAttrs.isNotEmpty()) {
            processNewAttrs(entity, metaEnt.id, newAttrs)
        }

        checkOldConstraints(entity, metaEnt)

        processIndexes(entity)
    }

    private fun checkAttrTypes(entity: RR_EntityDefinition, metaEntity: MetaEntity) {
        val metaName = entity.sqlMapping.metaName
        for (attr in entity.attributes.values) {
            val metaAttr = metaEntity.attrs[attr.sqlMapping]
            if (metaAttr != null) {
                val oldType = metaAttr.type
                val newType = checkNotNull(interpreter.resolveType(attr.type).sqlAdapter) {
                    "No SQL adapter for attribute '${attr.name}' of type ${attr.type}"
                }.metaName(sqlCtx)
                if (newType != oldType) {
                    val entityName = msgEntityName(entity)
                    initCtx.msgs.error("meta:attr:diff_type:$metaName:${attr.name}:$oldType:$newType",
                            "Type of attribute '${attr.name}' of entity $entityName changed: was $oldType, now $newType")
                }
            }
        }
    }

    private fun checkOldAttrs(entity: RR_EntityDefinition, metaEntity: MetaEntity, oldAttrs: List<String>) {
        if (initCtx.logging.metaNoCode) {
            val oldAttrsSorted = oldAttrs.sorted()
            val codeList = oldAttrsSorted.joinToString(",")
            val msgList = oldAttrsSorted.joinToString(", ")
            val entityName = msgEntityName(entity)
            initCtx.msgs.warning("dbinit:no_code:attrs:${entity.sqlMapping.metaName}:$codeList",
                    "Table columns for undefined attributes of ${metaEntity.type.en} $entityName found: $msgList")
        }
    }

    private fun processRemovedAttrs(entity: RR_EntityDefinition, metaEntityId: Int, removedAttrs: List<String>) {
        val attrsStr = removedAttrs.joinToString()
        val entityName = msgEntityName(entity)

        val action = SqlStepAction_DropColumns(entity, removedAttrs.toImmList())
        initCtx.step(ORD_RECORDS, "Drop table columns for $entityName: $attrsStr", action)

        val metaSql = SqlMeta.genMetaAttrsDeletes(sqlCtx, metaEntityId, removedAttrs)
        initCtx.step(ORD_TABLES, "Delete meta attributes for $entityName: $attrsStr", SqlStepAction_ExecSql(metaSql))
    }

    private fun checkOldConstraints(entity: RR_EntityDefinition, metaEntity: MetaEntity) {
        val metaName = entity.sqlMapping.metaName
        val entityName = msgEntityName(entity)
        val constraintAttrs = getExistingSizeConstraints(exeCtx.sysSqlExec, "c0.$metaName")
            .filterValues { it.first != null || it.second != null }
        val recordsExist = recordsExist(exeCtx.sysSqlExec, sqlCtx, entity)

        for ((attrName, constraints) in constraintAttrs) {
            val oldMin = constraints.first
            val oldMax = constraints.second
            val rrAttr = entity.strAttributes[attrName] ?: continue // If null then attr was deleted from entity.
            val sizeConstraint = rrAttr.sizeConstraint

            if (sizeConstraint == null) {
                dropDbConstraint(entity, attrName)
                continue
            }

            if (sizeConstraint.min == oldMin && sizeConstraint.max == oldMax) {
                continue
            }

            val scMin = sizeConstraint.min
            if (recordsExist && ((oldMin == null && scMin != null) ||
                (oldMin != null && scMin != null && oldMin < scMin))) {
                val defType = metaEntity.type.en
                val entityAttrCode = "$metaName:$attrName"
                initCtx.msgs.error("dbinit:attr:min_size_constraint_increased_records_exist:$entityAttrCode",
                    "$defType $entityName attribute $attrName: minimum size constraint increased but " +
                    "$entityName records already exist. Minimum size constraints can only be increased on " +
                    "an existing $defType attribute if the $defType has no records.")
                continue
            }

            val scMax = sizeConstraint.max
            if (recordsExist && ((oldMax == null && scMax != null) ||
                (oldMax != null && scMax != null && oldMax > scMax))) {
                val defType = metaEntity.type.en
                val entityAttrCode = "$metaName:$attrName"
                initCtx.msgs.error("dbinit:attr:max_size_constraint_decreased_records_exist:$entityAttrCode",
                    "$defType $entityName attribute $attrName: maximum size constraint decreased but " +
                    "$entityName records already exist. Maximum size constraints can only be decreased on " +
                    "an existing $defType attribute if the $defType has no records.")
                continue
            }

            dropDbConstraint(entity, attrName)
            addDbConstraint(entity, attrName, sizeConstraint)
        }

        entity.strAttributes.filterValues { it.sizeConstraint != null }.forEach { (attrName, rrAttr) ->
            if (constraintAttrs[attrName] == null) {
                val sizeConstraint = rrAttr.sizeConstraint!!
                if (recordsExist) {
                    val defType = metaEntity.type.en
                    initCtx.msgs.error("dbinit:attr:size_constraint_added_records_exist:$metaName:$attrName",
                        "$defType $entityName attribute $attrName: size constraints added but $entityName records " +
                        "already exist. Size constraints can only be added to an existing $defType attribute if it " +
                        "has no records.")
                } else if (metaEntity.attrs.containsKey(attrName)) {
                    // Only add the constraint here if it's a pre-existing attribute. Constraints for new  attributes
                    // are added elsewhere.
                    addDbConstraint(entity, attrName, sizeConstraint)
                }
            }
        }
    }

    private fun addDbConstraint(entity: RR_EntityDefinition, attrName: String, sizeConstraint: RR_SizeConstraint) {
        val metaName = entity.sqlMapping.metaName
        val sqlEntityName = "c0.$metaName"
        val msgEntityName = msgEntityName(entity)
        val rrAttr = entity.strAttributes.getValue(attrName)
        val constraintObj = SqlGen.genSizeCheckConstraint(
            sqlConstraintName(entity.mountName.str(), attrName),
            rrAttr.sqlMapping,
            sizeConstraint,
        )
        val stmt = JooqDdlStatement(JOOQ_CTX.alterTable(DSL.name(sqlEntityName)).add(constraintObj))
        initCtx.step(ORD_RECORDS, "Add attribute size constraint for $msgEntityName: $attrName",
            SqlStepAction_ExecSql(stmt))
    }

    private fun dropDbConstraint(entity: RR_EntityDefinition, attrName: String) {
        val metaName = entity.sqlMapping.metaName
        val sqlEntityName = "c0.$metaName"
        val msgEntityName = msgEntityName(entity)
        val constraintName = sqlConstraintName(entity.mountName.str(), attrName)
        val stmt = JooqDdlStatement(
            JOOQ_CTX.alterTable(DSL.name(sqlEntityName)).dropConstraint(DSL.name(constraintName))
        )
        initCtx.step(ORD_RECORDS, "Drop attribute size constraint for $msgEntityName: $attrName",
            SqlStepAction_ExecSql(stmt))
    }

    private fun processNewAttrs(entity: RR_EntityDefinition, metaEntityId: Int, newAttrs: List<String>) {
        val attrsStr = newAttrs.joinToString()

        val recs = recordsExist(exeCtx.sysSqlExec, sqlCtx, entity)

        val entityName = msgEntityName(entity)

        val exprAttrs = makeCreateExprAttrs(entity, newAttrs, recs)
        if (exprAttrs.size == newAttrs.size) {
            val action = SqlStepAction_AddColumns_AlterTable(entity, exprAttrs.toImmList(), recs, initCtx.isSnapshot, interpreter)
            val details = if (recs) "records exist" else "no records"
            initCtx.step(ORD_RECORDS, "Add table columns for $entityName ($details): $attrsStr", action)
        }

        val rrAttrs = newAttrs.map { name ->
            checkNotNull(entity.attributes.values.find { it.sqlMapping == name }) {
                "Attribute with sql mapping '$name' not found in entity ${entity.base.appLevelName}"
            }
        }
        val metaSql = SqlMeta.genMetaAttrsInserts(sqlCtx, metaEntityId, rrAttrs, interpreter)
        initCtx.step(ORD_TABLES, "Add meta attributes for $entityName: $attrsStr", SqlStepAction_ExecSql(metaSql))
    }

    private fun makeCreateExprAttrs(
        entity: RR_EntityDefinition,
        newAttrs: List<String>,
        existingRecs: Boolean,
    ): List<RR_CreateExprAttr> {
        val metaName = entity.sqlMapping.metaName
        val entityName = msgEntityName(entity)
        val res = mutableListOf<RR_CreateExprAttr>()

        val keys = entity.keys.flatMap { it.attribs }.map { it.str }.toSet()
        val indexes = entity.indexes.flatMap { it.attribs }.map { it.str }.toSet()
        val keyIndexChangesEnabled = KEY_INDEX_CHANGE_SWITCH.isActive(exeCtx.globalCtx.compilerOptions)

        for (name in newAttrs) {
            val attr = checkNotNull(entity.attributes.values.find { it.sqlMapping == name }) {
                "Attribute with sql mapping '$name' not found in entity ${entity.base.appLevelName}"
            }

            if (attr.hasDefaultValue || !existingRecs) {
                res.add(RR_CreateExprAttr(attr))
            } else {
                initCtx.msgs.error("meta:attr:new_no_def_value:$metaName:$name",
                    "New attribute '$name' of entity $entityName has no default value")
            }

            if (!keyIndexChangesEnabled && name in keys) {
                initCtx.msgs.error("meta:attr:new_key:$metaName:$name",
                    "New attribute '$name' of entity $entityName is a key, adding key attributes not supported")
            }
            if (!keyIndexChangesEnabled && name in indexes) {
                initCtx.msgs.error("meta:attr:new_index:$metaName:$name",
                    "New attribute '$name' of entity $entityName is an index, adding index attributes not supported")
            }
        }

        return res.toList()
    }

    private fun processIndexes(entity: RR_EntityDefinition) {
        val entityName = msgEntityName(entity)
        val tableName = entity.sqlMapping.table(sqlCtx)
        val sqlTable = sqlTables.getValue(tableName)

        // First dropping old indexes, then creating new indexes - supposedly this way is more efficient.
        processMissingIndexes(entity, sqlTable, tableName, entityName)
        processNewIndexes(entity, sqlTable, tableName, entityName)
    }

    private fun processMissingIndexes(
        entity: RR_EntityDefinition,
        table: SqlTable,
        tableName: String,
        entityName: String,
    ) {
        val codeIndexIds0 = entity.keys.map { SqlIndexId(true, keyIndexColumns(entity, it.attribs)) } +
                entity.indexes.map { SqlIndexId(false, keyIndexColumns(entity, it.attribs)) }
        val codeIndexIds = codeIndexIds0.toSet()

        val rowidCols = listOf(SqlConstants.ROWID_COLUMN)
        val (sqlKeys, sqlIndexes) = table.indexes
            .filterNot { it.unique && it.cols == rowidCols }
            .sortedWith(::compareIndexes)
            .partition { it.unique }

        for (sqlKey in sqlKeys) {
            if (SqlIndexId(sqlKey) !in codeIndexIds) {
                val stmt = JooqDdlStatement(
                    JOOQ_CTX.alterTable(DSL.name(tableName)).dropConstraint(DSL.name(sqlKey.name))
                )
                val msg = "Drop key of entity $entityName: ${sqlKey.name} ${sqlKey.cols}"
                initCtx.step(ORD_RECORDS, msg, SqlStepAction_ExecSql(stmt))
                processIndexDiff(entity, sqlKey.cols, "key", "database", "code")
            }
        }

        for (sqlIndex in sqlIndexes) {
            if (SqlIndexId(sqlIndex) !in codeIndexIds) {
                val stmt = JooqDdlStatement(JOOQ_CTX.dropIndex(DSL.name(sqlIndex.name)))
                val msg = "Drop index of entity $entityName: ${sqlIndex.name} ${sqlIndex.cols}"
                initCtx.step(ORD_RECORDS, msg, SqlStepAction_ExecSql(stmt))
                processIndexDiff(entity, sqlIndex.cols, "index", "database", "code")
            }
        }
    }

    private fun processNewIndexes(
        entity: RR_EntityDefinition,
        sqlTable: SqlTable,
        tableName: String,
        entityName: String,
    ) {
        val sqlIndexNames = sqlTable.indexes.map { it.name }
        val sqlIndexIds = sqlTable.indexes.map { SqlIndexId(it) }.toSet()

        val keyNameGen = SqlGen.keyNameGen(tableName, sqlIndexNames)
        for (rKey in entity.keys) {
            if (SqlIndexId(true, keyIndexColumns(entity, rKey.attribs)) !in sqlIndexIds) {
                val sql = SqlGen.genCreateKeySql(entity, tableName, rKey, keyNameGen)
                val msg = "Create key of entity $entityName: ${rKey.attribs}"
                initCtx.step(ORD_RECORDS, msg, SqlStepAction_ExecSql(sql))
                processIndexDiff(entity, rKey.attribs.mapToImmList { attrToColumn(entity, it) }, "key", "code", "database")
            }
        }

        val indexNameGen = SqlGen.indexNameGen(tableName, sqlIndexNames)
        for (rIndex in entity.indexes) {
            if (SqlIndexId(false, keyIndexColumns(entity, rIndex.attribs)) !in sqlIndexIds) {
                val sql = SqlGen.genCreateIndexSql(entity, tableName, rIndex, indexNameGen)
                val msg = "Create index of entity $entityName: ${rIndex.attribs}"
                initCtx.step(ORD_RECORDS, msg, SqlStepAction_ExecSql(sql))

                val cols = rIndex.attribs.mapToImmList { attrToColumn(entity, it) }
                processIndexDiff(entity, cols, "index", "code", "database")
            }
        }
    }

    private fun processIndexDiff(
        entity: RR_EntityDefinition,
        cols: List<String>,
        indexType: String,
        place1: String,
        place2: String,
    ) {
        if (!KEY_INDEX_CHANGE_SWITCH.isActive(exeCtx.globalCtx.compilerOptions)) {
            val entityName = msgEntityName(entity)
            val colsShort = cols.joinToString(",")
            initCtx.msgs.error("dbinit:index_diff:${entity.sqlMapping.metaName}:$place1:$indexType:$colsShort",
                "Entity $entityName: $indexType $cols exists in $place1, but not in $place2")
        }
    }

    private fun attrToColumn(entity: RR_EntityDefinition, attrName: Name): String {
        return entity.attributes[attrName]?.sqlMapping ?: attrName.str
    }

    private fun keyIndexColumns(entity: RR_EntityDefinition, attrs: ImmList<Name>): ImmList<String> {
        return attrs.mapToImmList { attrToColumn(entity, it) }
    }

    private fun compareIndexes(index1: SqlIndex, index2: SqlIndex): Int {
        var d = -index1.unique.compareTo(index2.unique)
        if (d == 0) d = CommonUtils.compareLists(index1.cols, index2.cols)
        return d
    }

    private data class SqlIndexId(val unique: Boolean, val cols: ImmList<String>) {
        constructor(sqlIndex: SqlIndex): this(sqlIndex.unique, sqlIndex.cols)
    }

    companion object {
        private val KEY_INDEX_CHANGE_SWITCH = C_FeatureSwitch("0.13.9")
        private val REMOVED_ATTRS_DROP_COLUMNS_SWITCH = C_FeatureSwitch("0.15.1", false)

        fun processEntities(
            exeCtx: Rt_ExecutionContext,
            initCtx: SqlInitCtx,
            metaData: ImmMap<String, MetaEntity>,
            sqlTables: ImmMap<String, SqlTable>,
        ) {
            val obj = SqlEntityIniter(exeCtx, initCtx, metaData, sqlTables)
            obj.processEntities()
        }
    }
}

private enum class SqlInitStepOrder {
    TABLES,
    RECORDS,
}

private class SqlInitCtx(
    logger: KLogger,
    val logging: SqlInitLogging,
    val objsInit: SqlObjectsInit,
    val isSnapshot: Boolean,
) {
    val msgs = Rt_Messages(logger)

    private val steps = mutableListOf<SqlInitStep>()

    fun checkErrors() = msgs.checkErrors()

    fun step(order: SqlInitStepOrder, title: String, action: SqlStepAction) {
        steps.add(SqlInitStep(order, steps.size, title, action))
    }

    fun steps() = steps.toList().sorted()
}

private class SqlInitStep(
    val order: SqlInitStepOrder,
    val order2: Int,
    val title: String,
    val action: SqlStepAction,
): Comparable<SqlInitStep> {
    override fun compareTo(other: SqlInitStep): Int {
        var d = order.compareTo(other.order)
        if (d == 0) d = order2.compareTo(other.order2)
        return d
    }
}

private class SqlStepCtx(val logger: KLogger, val exeCtx: Rt_ExecutionContext, val objsInit: SqlObjectsInit) {
    val sqlCtx = exeCtx.sqlCtx
    val sqlExec = exeCtx.sysSqlExec
}

private sealed class SqlStepAction {
    abstract fun run(ctx: SqlStepCtx)
}

private class SqlStepAction_ExecSql(private val sqls: ImmList<ExecutableSql>): SqlStepAction() {
    constructor(sql: ExecutableSql): this(immListOf(sql))

    override fun run(ctx: SqlStepCtx) {
        for (s in sqls) ctx.sqlExec.execute(s)
    }
}

private class SqlStepAction_InsertObject(private val rrObject: RR_ObjectDefinition): SqlStepAction() {
    override fun run(ctx: SqlStepCtx) {
        try {
            ctx.objsInit.initObject(rrObject)
        } catch (e: Rt_Exception) {
            ctx.logger.error {
                val head = "Failed to insert record for object '${rrObject.base.appLevelName}': ${e.message}"
                Rt_Utils.appendStackTrace(head, e.info.stack)
            }
            throw e
        }
    }
}

/** Lightweight wrapper around an [RR_Attribute] for the add-columns step action. */
private class RR_CreateExprAttr(val attr: RR_Attribute)

private class SqlStepAction_AddColumns_AlterTable(
    private val entity: RR_EntityDefinition,
    private val attrs: ImmList<RR_CreateExprAttr>,
    private val existingRecs: Boolean,
    private val isSnapshot: Boolean,
    private val interpreter: Rt_Interpreter,
): SqlStepAction() {
    override fun run(ctx: SqlStepCtx) {
        val statements = buildAddColumnsStatements(ctx.exeCtx, entity, attrs, existingRecs, isSnapshot, interpreter)
        for (s in statements) ctx.exeCtx.sysSqlExec.execute(s)
    }
}

/**
 * Builds the sequence of statements for adding columns to an existing entity table:
 * one ALTER TABLE … ADD COLUMN per attr; if rows exist, an UPDATE that fills defaults followed by
 * one ALTER TABLE … SET NOT NULL per attr; finally a single ALTER TABLE … ADD CONSTRAINT … for
 * any constraints attached to the new attrs. Each statement runs on its own round-trip.
 */
private fun buildAddColumnsStatements(
    exeCtx: Rt_ExecutionContext,
    entity: RR_EntityDefinition,
    attrs: List<RR_CreateExprAttr>,
    existingRecs: Boolean,
    isSnapshot: Boolean,
    interpreter: Rt_Interpreter,
): List<ExecutableSql> {
    val sqlCtx = exeCtx.sqlCtx
    val table = entity.sqlMapping.table(sqlCtx)

    val statements = mutableListOf<ExecutableSql>()
    for (exprAttr in attrs) {
        statements += SqlGen.genAddColumnSql(table, exprAttr.attr, interpreter, existingRecs)
    }

    if (existingRecs) {
        val tableRef = DSL.table(DSL.name(table))
        val updateMap = LinkedHashMap<Field<*>, Field<*>>()
        // Plain-SQL `?` field: jOOQ treats it as opaque text (not a tracked Param), so the literal
        // `?` flows through `renderJooq` (INDEXED ParamType) into the rendered SQL where the
        // runtime's parallel `binds` list is bound positionally via JDBC. Same convention is used
        // in sql_init_obj.kt and rt_utils_sql.kt.
        val placeholder: Field<Any> = DSL.field("?", Any::class.java)
        val binds = mutableListOf<Rt_Value>()
        for (exprAttr in attrs) {
            val attr = exprAttr.attr
            checkNotNull(attr.defaultExpr) {
                "Attribute '${attr.name}' has no default value but records exist"
            }
            // Original sql_init code constructed an outer frame with dbUpdateAllowed = true; pass
            // through here to preserve that behaviour.
            binds.add(interpreter.evaluateAttributeDefault(entity.base.defId, attr.index, exeCtx, dbUpdateAllowed = true))
            updateMap[DSL.field(DSL.name(attr.sqlMapping))] = placeholder
        }
        val updateQ = JOOQ_CTX.updateQuery(tableRef)
        updateQ.addValues(updateMap)
        statements += ParameterizedSql(renderJooq(updateQ), binds.toImmList())

        if (!isSnapshot) {
            for (exprAttr in attrs) {
                statements += JooqDdlStatement(
                    JOOQ_CTX.alterTable(DSL.name(table))
                        .alter(DSL.field(DSL.name(exprAttr.attr.sqlMapping)))
                        .setNotNull()
                )
            }
        }
    }

    val constraintsSql = SqlGen.genAddAttrConstraintsSql(sqlCtx, entity, interpreter, attrs.map { it.attr }, !isSnapshot)
    if (constraintsSql != null) statements += constraintsSql

    return statements
}

private class SqlStepAction_DropColumns(
    private val entity: RR_EntityDefinition,
    private val attrNames: ImmList<String>,
): SqlStepAction() {
    override fun run(ctx: SqlStepCtx) {
        val tableName = entity.sqlMapping.table(ctx.sqlCtx)
        ctx.sqlExec.execute(SqlGen.genDropColumnsSql(tableName, attrNames))
    }
}

private fun msgEntityName(entity: RR_EntityDefinition): String {
    val meta = entity.sqlMapping.metaName
    val app = entity.base.appLevelName
    return if (meta == app) {
        "'$app'"
    } else {
        "'$app' (meta: $meta)"
    }
}

// sqlConstraintName moved to runtime-core/sql/sql_constraint_name.kt
