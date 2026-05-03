/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:OptIn(RawSqlAccess::class)

package net.postchain.rell.base.sql

import net.postchain.rell.base.model.Index
import net.postchain.rell.base.model.Key
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immMapOf
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.SQLDataType

object SqlGen {
    // (!) When changing a function, change its name e.g. to fn_v2. Functions in the database are not upgraded - a function is created
    // only once, if there is no function with the same name in the database.
    val RELL_SYS_FUNCTIONS = immMapOf(
        genFunctionIntegerPower(),
        genFunctionBigNumberFromText(SqlConstants.FN_BIGINTEGER_FROM_TEXT, "big_integer", "^[-+]?[0-9]+$"),
        genFunctionBigIntegerPower(),
        genFunctionBigNumberFromText(SqlConstants.FN_DECIMAL_FROM_TEXT, "decimal", "^[-+]?([0-9]+([.][0-9]+)?|[.][0-9]+)([Ee][-+]?[0-9]+)?$"),
        genFunctionDecimalToText(),
        genFunctionSubstr1(SqlConstants.FN_BYTEA_SUBSTR1, "BYTEA"),
        genFunctionSubstr2(SqlConstants.FN_BYTEA_SUBSTR2, "BYTEA"),
        genFunctionRepeat(),
        genFunctionSubstr1(SqlConstants.FN_TEXT_SUBSTR1, "TEXT"),
        genFunctionSubstr2(SqlConstants.FN_TEXT_SUBSTR2, "TEXT"),
        genFunctionTextGetChar(),
        genFunctionJsonArrayGet(SqlConstants.FN_JSON_ARRAY_GET),
        genFunctionJsonObjectGet(SqlConstants.FN_JSON_OBJECT_GET),
        genFunctionJsonArrayGet(SqlConstants.FN_JSON_ARRAY_GET_OR_NULL, isNullableVariant = true),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_INTEGER, "BIGINT", INTEGRAL_TEST, "not an integer"),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_INTEGER_OR_NULL, "BIGINT", INTEGRAL_TEST, null),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_BIG_INTEGER, "NUMERIC", INTEGRAL_TEST, "not a big_integer"),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_BIG_INTEGER_OR_NULL, "NUMERIC", INTEGRAL_TEST, null),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_BOOLEAN_OR_NULL, "BOOLEAN", BOOLEAN_TEST, null),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_TEXT, "TEXT", TEXT_TEST, "not text", ::textExtractor),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_TEXT_OR_NULL, "TEXT", TEXT_TEST, null, ::textExtractor),
        genFunctionJsonSize(SqlConstants.FN_JSON_SIZE),
    )

    private fun genFunctionJsonSize(name: String): Pair<String, ExecutableSql> = name to RawSqlStatement("""
        CREATE FUNCTION "$name"(value JSONB) RETURNS BIGINT AS $$
        DECLARE type TEXT;
        BEGIN
            type := JSONB_TYPEOF(value);
            IF type = 'array' THEN RETURN JSONB_ARRAY_LENGTH(value);
            ELSIF type = 'object' THEN RETURN (SELECT COUNT(*) FROM JSONB_OBJECT_KEYS(value)) :: BIGINT;
            ELSE RAISE EXCEPTION 'not a json array or object: %', value;
            END IF;
        END;
        $$ LANGUAGE PLPGSQL IMMUTABLE;
    """.trimIndent())

    private fun genFunctionJsonAsType(
        name: String,
        sqlType: String,
        testCode: String,
        errMsgOrNull: String?,
        valueExtractor: (String) -> String = { "($it :: $sqlType)" },
    ): Pair<String, ExecutableSql> {
        val badValueStmt = if (errMsgOrNull == null) "RETURN NULL" else "RAISE EXCEPTION '$errMsgOrNull: %', value"
        return name to RawSqlStatement("""
            CREATE FUNCTION "$name"(value JSONB) RETURNS $sqlType AS $$
            BEGIN
                IF NOT ($testCode) THEN $badValueStmt; END IF;
                RETURN ${valueExtractor("value")};
            END;
            $$ LANGUAGE PLPGSQL IMMUTABLE;
        """.trimIndent())
    }

    private const val INTEGRAL_TEST = "(value :: TEXT) ~ '^-?\\d+$'"
    private const val BOOLEAN_TEST = "JSONB_TYPEOF(value) = 'boolean'"
    private const val TEXT_TEST = "JSONB_TYPEOF(value) = 'string'"
    private fun textExtractor(value: String): String = "($value #>> '{}')"

    private fun genFunctionJsonArrayGet(name: String, isNullableVariant: Boolean = false): Pair<String, ExecutableSql> {
        val badIndexStmt = if (isNullableVariant) "RETURN NULL" else "RAISE EXCEPTION 'bad index: %', index"
        val outOfBoundsStmt = if (isNullableVariant) "RETURN NULL" else "RAISE EXCEPTION 'out of bounds: %', index"
        // We need to check if the index is within the bounds of PostgreSQL's INT (as opposed to BIGINT which is
        // equivalent to Rell's integer type), because the cast to INT will raise an exception otherwise. That's fine
        // for the throwing variant, but not what we want for the nullable variant.
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(value JSONB, index BIGINT) RETURNS JSONB AS $$
                DECLARE res JSONB;
                BEGIN
                    IF index < 0 OR index > 2147483647 THEN $badIndexStmt; END IF;
                    res := (value -> (index :: INT)) :: JSONB;
                    IF res IS NULL THEN $outOfBoundsStmt; END IF;
                    RETURN res;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionJsonObjectGet(name: String): Pair<String, ExecutableSql> = name to RawSqlStatement("""
            CREATE FUNCTION "$name"(value JSONB, key TEXT) RETURNS JSONB AS $$
            DECLARE res JSONB;
            BEGIN
                res := (value -> key) :: JSONB;
                IF res IS NULL THEN RAISE EXCEPTION 'key not found: %', key; END IF;
                RETURN res;
            END;
            $$ LANGUAGE PLPGSQL IMMUTABLE;
        """.trimIndent())

    private fun genFunctionIntegerPower(): Pair<String, ExecutableSql> {
        val name = SqlConstants.FN_INTEGER_POWER
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(base BIGINT, exp BIGINT) RETURNS BIGINT AS $$
                BEGIN
                    IF exp < 0 THEN RAISE EXCEPTION 'negative exponent: %', exp; END IF;
                    RETURN POWER(base :: NUMERIC, exp) :: BIGINT;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionBigIntegerPower(): Pair<String, ExecutableSql> {
        // ROUND() is needed, because by default POWER() returns a non-integer value ending with ".0000000000000000".
        val name = SqlConstants.FN_BIGINTEGER_POWER
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(base NUMERIC, exp BIGINT) RETURNS NUMERIC AS $$
                BEGIN
                    IF exp < 0 THEN RAISE EXCEPTION 'negative exponent: %', exp; END IF;
                    RETURN ROUND(POWER(base, exp));
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionBigNumberFromText(name: String, type: String, regex: String): Pair<String, ExecutableSql> {
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(s TEXT) RETURNS NUMERIC AS $$
                BEGIN
                    IF s !~ '$regex' THEN RAISE EXCEPTION 'bad $type value: "%"', s; END IF;
                    RETURN s :: NUMERIC;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionDecimalToText(): Pair<String, ExecutableSql> {
        // Using regexp to remove trailing zeros.
        // Clever regexp: can handle special cases like "0.0", "0.000000", etc.
        val name = SqlConstants.FN_DECIMAL_TO_TEXT
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(v NUMERIC) RETURNS TEXT AS $$
                BEGIN
                    RETURN REGEXP_REPLACE(v::TEXT, '(([.][0-9]*[1-9])(0+)$)|([.]0+$)', '\2');
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionRepeat(): Pair<String, ExecutableSql> {
        val name = SqlConstants.FN_TEXT_REPEAT
        val type = "TEXT"
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(v $type, i INT) RETURNS $type AS $$
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    RETURN REPEAT(v, i);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionSubstr1(name: String, type: String): Pair<String, ExecutableSql> {
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(v $type, i INT) RETURNS $type AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    n := LENGTH(v);
                    IF i > n THEN RAISE EXCEPTION '$name: i = %, n = %', i, n; END IF;
                    RETURN SUBSTR(v, i + 1);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionSubstr2(name: String, type: String): Pair<String, ExecutableSql> {
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(v $type, i INT, j INT) RETURNS $type AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 OR j < i THEN RAISE EXCEPTION '$name: i = %, j = %', i, j; END IF;
                    n := LENGTH(v);
                    IF j > n THEN RAISE EXCEPTION '$name: i = %, j = %, n = %', i, j, n; END IF;
                    RETURN SUBSTR(v, i + 1, j - i);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    private fun genFunctionTextGetChar(): Pair<String, ExecutableSql> {
        val name = SqlConstants.FN_TEXT_GETCHAR
        return name to RawSqlStatement("""
                CREATE FUNCTION "$name"(v TEXT, i INT) RETURNS TEXT AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    n := LENGTH(v);
                    IF i >= n THEN RAISE EXCEPTION '$name: i = %, n = %', i, n; END IF;
                    RETURN SUBSTR(v, i + 1, 1);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent())
    }

    fun genRowidSql(chainMapping: Rt_ChainSqlMapping): List<ExecutableSql> {
        val table = chainMapping.rowidTable
        val func = chainMapping.rowidFunction
        return listOf(
            JooqDdlStatement(
                JOOQ_CTX.createTable(DSL.name(table))
                    .column("last_value", SQLDataType.BIGINT.nullable(false))
            ),
            JooqDdlStatement(
                JOOQ_CTX.insertInto(DSL.table(DSL.name(table)), DSL.field(DSL.name("last_value"), SQLDataType.BIGINT))
                    .values(0L)
            ),
            // PL/pgSQL function bodies have no jOOQ DSL — keep as raw text.
            RawSqlStatement("""
                CREATE FUNCTION "$func"() RETURNS BIGINT AS
                'UPDATE "$table" SET last_value = last_value + 1 RETURNING last_value'
                LANGUAGE SQL;
            """.trimIndent()),
        )
    }

    fun genFunctionMakeRowids(chainMapping: Rt_ChainSqlMapping): Pair<String, ExecutableSql> {
        val table = chainMapping.rowidTable
        val name = chainMapping.rowidsFunction
        return name to RawSqlStatement("""
            CREATE FUNCTION "$name"(n BIGINT) RETURNS BIGINT AS $$
            DECLARE v BIGINT;
            BEGIN
                IF n <= 0 OR n >= 1000000000 THEN RAISE EXCEPTION '$name: n = %', n; END IF;
                UPDATE "$table" SET last_value = last_value + n RETURNING last_value INTO v;
                RETURN v - n + 1;
            END;
            $$ LANGUAGE PLPGSQL;
        """.trimIndent())
    }

    fun genEntity(sqlCtx: Rt_SqlContext, entity: RR_EntityDefinition, interpreter: Rt_Interpreter, addFkConstraints: Boolean): List<ExecutableSql> {
        val tableName = entity.sqlMapping.table(sqlCtx)
        return genEntity(sqlCtx, entity, interpreter, tableName, addFkConstraints)
    }

    fun genEntityConstraintsAndIndexes(sqlCtx: Rt_SqlContext, entity: RR_EntityDefinition, interpreter: Rt_Interpreter): List<ExecutableSql> {
        val tableName = entity.sqlMapping.table(sqlCtx)
        val sqls = mutableListOf<ExecutableSql>()

        val fk = genAddAttrConstraintsSql(sqlCtx, entity, interpreter, entity.attributes.values.toList(), true)
        if (fk != null) sqls.add(fk)

        val keyNameGen = keyNameGen(tableName, listOf())
        for (rKey in entity.keys) {
            sqls.add(genCreateKeySql(entity, tableName, rKey, keyNameGen))
        }

        val indexNameGen = indexNameGen(tableName, listOf())
        for (rIndex in entity.indexes) {
            sqls.add(genCreateIndexSql(entity, tableName, rIndex, indexNameGen))
        }

        return sqls
    }

    private fun genEntity(
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
        interpreter: Rt_Interpreter,
        tableName: String,
        addFkConstraints: Boolean,
    ): List<ExecutableSql> {
        val mapping = entity.sqlMapping
        val rowid = mapping.rowidColumn
        val attrs = entity.attributes.values

        val t = JOOQ_CTX.createTable(DSL.name(tableName))

        val constraints = mutableListOf<Constraint>()
        constraints.add(constraint("PK_$tableName").primaryKey(rowid))
        constraints += genAttrConstraints(sqlCtx, entity, interpreter, attrs, addFkConstraints)

        var q = t.column(rowid, SQLDataType.BIGINT.nullable(false))
        q = genAttrColumns(attrs, interpreter, q)

        if (addFkConstraints) {
            val keyNameGen = keyNameGen(tableName, listOf())
            for (rKey in entity.keys) {
                val c = makeConstraint(entity, rKey, keyNameGen)
                constraints.add(c)
            }
        }

        val out = mutableListOf<ExecutableSql>()
        out.add(JooqDdlStatement(q.constraints(*constraints.toTypedArray())))

        if (addFkConstraints) {
            val indexNameGen = indexNameGen(tableName, listOf())
            for (rIndex in entity.indexes) {
                out.add(genCreateIndexSql(entity, tableName, rIndex, indexNameGen))
            }
        }

        return out
    }

    private fun genAttrColumns(
        attrs: Collection<RR_Attribute>,
        interpreter: Rt_Interpreter,
        step: CreateTableElementListStep,
    ): CreateTableElementListStep {
        var q = step
        for (attr in attrs) {
            q = q.column(attr.sqlMapping, getSqlType(attr.type, interpreter))
        }
        return q
    }

    private fun genAttrConstraints(
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
        interpreter: Rt_Interpreter,
        attrs: Collection<RR_Attribute>,
        addFkConstraints: Boolean,
    ): List<Constraint> {
        val sqlTable = entity.sqlMapping.table(sqlCtx)
        val constraints = mutableListOf<Constraint>()

        for (attr in attrs) {
            val attrType = attr.type
            if (attrType is RR_Type.Entity && addFkConstraints) {
                val refEntity = interpreter.rrApp.allEntities[attrType.defIndex]
                val refTable = refEntity.sqlMapping.table(sqlCtx)
                val fkConstraint = constraint("${sqlTable}_${attr.sqlMapping}_FK")
                        .foreignKey(attr.sqlMapping)
                        .references(refTable, refEntity.sqlMapping.rowidColumn)
                constraints.add(fkConstraint)
            }
            val sizeConstraint = attr.sizeConstraint
            if (sizeConstraint != null) {
                val sqlConstraintName = sqlConstraintName(entity.mountName.str(), attr.sqlMapping)
                constraints.add(
                    constraint(sqlConstraintName).check(
                    genSizeCheckCondition(sizeConstraint, attr.sqlMapping)
                ))
            }
        }

        return constraints
    }

    private fun genSizeCheckCondition(
        sizeConstraint: RR_SizeConstraint,
        sqlMapping: String,
    ): Condition {
        val field = DSL.field(DSL.name(sqlMapping), String::class.java)
        // PG's length()/octet_length() return int4; cast to bigint so the comparison happens at
        // Long width. The previous code did `.ge(it.toInt())` / `.le(it.toInt())` which silently
        // truncated the user-declared `Long` size bound, inverting the predicate for any size
        // > 2^31 (e.g. @max_size(3_000_000_000) → CHECK (length(col) <= -1294967296), always
        // false, blocking every insert). Comparing as bigint either passes (when the column never
        // exceeds 2^31) or raises a type-honest out-of-range error.
        val lengthInt = when (sizeConstraint.kind) {
            RR_SizeConstraintKind.BYTE_ARRAY -> DSL.octetLength(field)
            RR_SizeConstraintKind.TEXT -> DSL.length(field)
        }
        val lengthExpr: Field<Long> = lengthInt.cast(SQLDataType.BIGINT)
        val conditions = mutableListOf<Condition>()
        sizeConstraint.min?.let { conditions.add(lengthExpr.ge(it)) }
        sizeConstraint.max?.let { conditions.add(lengthExpr.le(it)) }
        return conditions.reduce { a, b -> a.and(b) }
    }

    fun keyNameGen(table: String, existingNames: Collection<String>): SqlNameGen {
        return SqlNameGen("K_${table}_%d", existingNames)
    }

    fun genCreateKeySql(entity: RR_EntityDefinition, table: String, rKey: Key, nameGen: SqlNameGen): ExecutableSql {
        val c = makeConstraint(entity, rKey, nameGen)
        return JooqDdlStatement(JOOQ_CTX.alterTable(DSL.name(table)).add(c))
    }
    private fun makeConstraint(entity: RR_EntityDefinition, rKey: Key, nameGen: SqlNameGen): Constraint {
        val keyName = nameGen.nextName()
        val attribs = rKey.attribs.map { attrName ->
            entity.attributes[attrName]?.sqlMapping ?: attrName.str
        }
        return constraint(keyName).unique(*attribs.toTypedArray())
    }

    fun indexNameGen(table: String, existingNames: Collection<String>): SqlNameGen {
        return SqlNameGen("IDX_${table}_%d", existingNames)
    }

    fun genCreateIndexSql(entity: RR_EntityDefinition, table: String, index: Index, nameGen: SqlNameGen): ExecutableSql {
        val indexName = nameGen.nextName()
        return if (index.attribs.size == 1 && isJsonAttr(entity, index.attribs[0].str)) {
            // PG-specific GIN index with jsonb_path_ops opclass — jOOQ has no first-class API
            // for opclass, so the SQL is built via a parameterised template with all identifiers
            // wrapped in DSL.name (escape-safe) and only the keywords `USING gin` and the opclass
            // name as literal text.
            val attrName = index.attribs[0]
            val col = entity.attributes[attrName]?.sqlMapping ?: attrName.str
            JooqDdlStatement(
                DSL.query(
                    "CREATE INDEX {0} ON {1} USING gin ({2} jsonb_path_ops)",
                    DSL.name(indexName),
                    DSL.table(DSL.name(table)),
                    DSL.field(DSL.name(col)),
                )
            )
        } else {
            val attribs = index.attribs.map { attrName ->
                entity.attributes[attrName]?.sqlMapping ?: attrName.str
            }
            JooqDdlStatement(
                JOOQ_CTX.createIndex(DSL.name(indexName))
                    .on(DSL.name(table), *attribs.map { DSL.name(it) }.toTypedArray())
            )
        }
    }

    private fun isJsonAttr(entity: RR_EntityDefinition, name: String): Boolean {
        val attr = entity.strAttributes[name]
        return attr?.type == RR_Type.Primitive(RR_PrimitiveKind.JSON)
    }

    fun genAddColumnSql(table: String, attr: RR_Attribute, interpreter: Rt_Interpreter, nullable: Boolean): ExecutableSql {
        val type = getSqlType(attr.type, interpreter).nullable(nullable)
        return JooqDdlStatement(JOOQ_CTX.alterTable(DSL.name(table)).addColumn(attr.sqlMapping, type))
    }

    fun genAddAttrConstraintsSql(
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
        interpreter: Rt_Interpreter,
        attrs: List<RR_Attribute>,
        addFkConstraints: Boolean,
    ): ExecutableSql? {
        val constraints = genAttrConstraints(sqlCtx, entity, interpreter, attrs, addFkConstraints)
        if (constraints.isEmpty()) {
            return null
        }

        val table = entity.sqlMapping.table(sqlCtx)
        return JooqDdlStatement(JOOQ_CTX.alterTable(DSL.name(table)).add(constraints))
    }

    fun genDropColumnsSql(table: String, attrNames: List<String>): ExecutableSql =
        JooqDdlStatement(JOOQ_CTX.alterTable(DSL.name(table)).drop(*attrNames.toTypedArray()))

    /**
     * Builds a SQL CHECK constraint for a size constraint on an attribute. Returns the
     * jOOQ [Constraint] AST node so callers can compose it into `ALTER TABLE … ADD …`
     * via [DSL.alterTable].add(...) without round-tripping through a SQL string.
     */
    fun genSizeCheckConstraint(
        constraintName: String,
        sqlMapping: String,
        sizeConstraint: RR_SizeConstraint,
    ): Constraint = constraint(constraintName).check(genSizeCheckCondition(sizeConstraint, sqlMapping))

}

class SqlNameGen(private val pattern: String, existingNames: Collection<String>) {
    private val existingNames = existingNames.toMutableSet()
    private var nextIndex = 0

    fun nextName(): String {
        while (true) {
            val name = pattern.format(nextIndex)
            if (existingNames.add(name)) {
                return name
            }
            ++nextIndex
        }
    }
}

private fun getSqlType(type: RR_Type, interpreter: Rt_Interpreter): DataType<*> {
    val rtType = interpreter.resolveType(type)

    val sqlType = requireNotNull(rtType.sqlAdapter?.sqlType) {
        "Type ${rtType.name} is not SQL-compatible"
    }

    return sqlType.nullable(false)
}
