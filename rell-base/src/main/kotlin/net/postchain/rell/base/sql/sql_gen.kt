/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.lib.type.R_JsonType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.utils.toImmMap
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.SQLDataType

object SqlGen {
    // Needed to disable jooq output.
    @Suppress("unused")
    private val disableLogo = run {
        System.setProperty("org.jooq.no-logo", "true")
        System.setProperty("org.jooq.no-tips", "true")
    }

    val DSL_CTX: DSLContext = DSL.using(SQLDialect.POSTGRES)

    // (!) When changing a function, change its name e.g. to fn_v2. Functions in the database are not upgraded - a function is created
    // only once, if there is no function with the same name in the database.
    val RELL_SYS_FUNCTIONS = mapOf(
        genFunctionIntegerPower(),
        genFunctionBigNumberFromText(SqlConstants.FN_BIGINTEGER_FROM_TEXT, "big_integer", "^[-+]?[0-9]+$"),
        genFunctionBigIntegerPower(),
        genFunctionBigNumberFromText(SqlConstants.FN_DECIMAL_FROM_TEXT, "decimal", "^[-+]?([0-9]+([.][0-9]+)?|[.][0-9]+)([Ee][-+]?[0-9]+)?$"),
        genFunctionDecimalToText(SqlConstants.FN_DECIMAL_TO_TEXT),
        genFunctionSubstr1(SqlConstants.FN_BYTEA_SUBSTR1, "BYTEA"),
        genFunctionSubstr2(SqlConstants.FN_BYTEA_SUBSTR2, "BYTEA"),
        genFunctionRepeat(SqlConstants.FN_TEXT_REPEAT, "TEXT"),
        genFunctionSubstr1(SqlConstants.FN_TEXT_SUBSTR1, "TEXT"),
        genFunctionSubstr2(SqlConstants.FN_TEXT_SUBSTR2, "TEXT"),
        genFunctionTextGetChar(SqlConstants.FN_TEXT_GETCHAR),
        genFunctionJsonArrayGet(SqlConstants.FN_JSON_ARRAY_GET),
        genFunctionJsonObjectGet(SqlConstants.FN_JSON_OBJECT_GET),
        genFunctionJsonArrayGet(SqlConstants.FN_JSON_ARRAY_GET_OR_NULL, isNullableVariant = true),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_INTEGER, "BIGINT", INTEGER_TEST, "not an integer"),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_INTEGER_OR_NULL, "BIGINT", INTEGER_TEST, null),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_BOOLEAN_OR_NULL, "BOOLEAN", BOOLEAN_TEST, null),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_TEXT, "TEXT", TEXT_TEST, "not text", ::textExtractor),
        genFunctionJsonAsType(SqlConstants.FN_JSON_AS_TEXT_OR_NULL, "TEXT", TEXT_TEST, null, ::textExtractor),
        genFunctionJsonSize(SqlConstants.FN_JSON_SIZE),
    ).toImmMap()

    fun genFunctionJsonSize(name: String): Pair<String, String> {
        return name to """
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
        """.trimIndent()
    }

    fun genFunctionJsonAsType(
        name: String,
        sqlType: String,
        testCode: String,
        errMsgOrNull: String?,
        valueExtractor: (String) -> String = { "($it :: $sqlType)" },
    ): Pair<String, String> {
        val badValueStmt = if (errMsgOrNull == null) "RETURN NULL" else "RAISE EXCEPTION '$errMsgOrNull: %', value"
        return name to """
            CREATE FUNCTION "$name"(value JSONB) RETURNS $sqlType AS $$
            BEGIN
                IF NOT ($testCode) THEN $badValueStmt; END IF;
                RETURN ${valueExtractor("value")};
            END;
            $$ LANGUAGE PLPGSQL IMMUTABLE;
        """.trimIndent()
    }

    private const val INTEGER_TEST = "(value :: TEXT) ~ '^-?\\d+$'"
    private const val BOOLEAN_TEST = "JSONB_TYPEOF(value) = 'boolean'"
    private const val TEXT_TEST = "JSONB_TYPEOF(value) = 'string'"
    private fun textExtractor(value: String): String = "($value #>> '{}')"

    fun genFunctionJsonArrayGet(name: String, isNullableVariant: Boolean = false): Pair<String, String> {
        val badIndexStmt = if (isNullableVariant) "RETURN NULL" else "RAISE EXCEPTION 'bad index: %', index"
        val outOfBoundsStmt = if (isNullableVariant) "RETURN NULL" else "RAISE EXCEPTION 'out of bounds: %', index"
        // We need to check if the index is within the bounds of PostgreSQL's INT (as opposed to BIGINT which is
        // equivalent to Rell's integer type), because the cast to INT will raise an exception otherwise. That's fine
        // for the throwing variant, but not what we want for the nullable variant.
        return name to """
                CREATE FUNCTION "$name"(value JSONB, index BIGINT) RETURNS JSONB AS $$
                DECLARE res JSONB;
                BEGIN
                    IF index < 0 OR index > 2147483647 THEN $badIndexStmt; END IF;
                    res := (value -> (index :: INT)) :: JSONB;
                    IF res IS NULL THEN $outOfBoundsStmt; END IF;
                    RETURN res;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    fun genFunctionJsonObjectGet(name: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(value JSONB, key TEXT) RETURNS JSONB AS $$
                DECLARE res JSONB;
                BEGIN
                    res := (value -> key) :: JSONB;
                    IF res IS NULL THEN RAISE EXCEPTION 'key not found: %', key; END IF;
                    RETURN res;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionIntegerPower(): Pair<String, String> {
        val name = SqlConstants.FN_INTEGER_POWER
        return name to """
                CREATE FUNCTION "$name"(base BIGINT, exp BIGINT) RETURNS BIGINT AS $$
                BEGIN
                    IF exp < 0 THEN RAISE EXCEPTION 'negative exponent: %', exp; END IF;
                    RETURN POWER(base :: NUMERIC, exp) :: BIGINT;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionBigIntegerPower(): Pair<String, String> {
        // ROUND() is needed, because by default POWER() returns a non-integer value ending with ".0000000000000000".
        val name = SqlConstants.FN_BIGINTEGER_POWER
        return name to """
                CREATE FUNCTION "$name"(base NUMERIC, exp BIGINT) RETURNS NUMERIC AS $$
                BEGIN
                    IF exp < 0 THEN RAISE EXCEPTION 'negative exponent: %', exp; END IF;
                    RETURN ROUND(POWER(base, exp));
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionBigNumberFromText(name: String, type: String, regex: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(s TEXT) RETURNS NUMERIC AS $$
                BEGIN
                    IF s !~ '$regex' THEN RAISE EXCEPTION 'bad $type value: "%"', s; END IF;
                    RETURN s :: NUMERIC;
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionDecimalToText(name: String): Pair<String, String> {
        // Using regexp to remove trailing zeros.
        // Clever regexp: can handle special cases like "0.0", "0.000000", etc.
        return name to """
                CREATE FUNCTION "$name"(v NUMERIC) RETURNS TEXT AS $$
                BEGIN
                    RETURN REGEXP_REPLACE(v::TEXT, '(([.][0-9]*[1-9])(0+)$)|([.]0+$)', '\2');
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionRepeat(name: String, type: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(v $type, i INT) RETURNS $type AS $$
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    RETURN REPEAT(v, i);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionSubstr1(name: String, type: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(v $type, i INT) RETURNS $type AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    n := LENGTH(v);
                    IF i > n THEN RAISE EXCEPTION '$name: i = %, n = %', i, n; END IF;
                    RETURN SUBSTR(v, i + 1);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionSubstr2(name: String, type: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(v $type, i INT, j INT) RETURNS $type AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 OR j < i THEN RAISE EXCEPTION '$name: i = %, j = %', i, j; END IF;
                    n := LENGTH(v);
                    IF j > n THEN RAISE EXCEPTION '$name: i = %, j = %, n = %', i, j, n; END IF;
                    RETURN SUBSTR(v, i + 1, j - i);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    private fun genFunctionTextGetChar(name: String): Pair<String, String> {
        return name to """
                CREATE FUNCTION "$name"(v TEXT, i INT) RETURNS TEXT AS $$
                DECLARE n INT;
                BEGIN
                    IF i < 0 THEN RAISE EXCEPTION '$name: i = %', i; END IF;
                    n := LENGTH(v);
                    IF i >= n THEN RAISE EXCEPTION '$name: i = %, n = %', i, n; END IF;
                    RETURN SUBSTR(v, i + 1, 1);
                END;
                $$ LANGUAGE PLPGSQL IMMUTABLE;
            """.trimIndent()
    }

    fun genRowidSql(chainMapping: Rt_ChainSqlMapping): String {
        val table = chainMapping.rowidTable
        val func = chainMapping.rowidFunction
        return """
            CREATE TABLE "$table"( last_value bigint not null);
            INSERT INTO "$table"(last_value) VALUES (0);
            CREATE FUNCTION "$func"() RETURNS BIGINT AS
            'UPDATE "$table" SET last_value = last_value + 1 RETURNING last_value'
            LANGUAGE SQL;
        """.trimIndent()
    }

    fun genFunctionMakeRowids(chainMapping: Rt_ChainSqlMapping): Pair<String, String> {
        val table = chainMapping.rowidTable
        val name = chainMapping.rowidsFunction
        return name to """
            CREATE FUNCTION "$name"(n BIGINT) RETURNS BIGINT AS $$
            DECLARE v BIGINT;
            BEGIN
                IF n <= 0 OR n >= 1000000000 THEN RAISE EXCEPTION '$name: n = %', n; END IF;
                UPDATE "$table" SET last_value = last_value + n RETURNING last_value INTO v;
                RETURN v - n + 1;
            END;
            $$ LANGUAGE PLPGSQL;
        """.trimIndent()
    }

    fun genEntity(sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition): String {
        val tableName = rEntity.sqlMapping.table(sqlCtx)
        return genEntity(sqlCtx, rEntity, tableName)
    }

    private fun genEntity(sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, tableName: String): String {
        val mapping = rEntity.sqlMapping
        val rowid = mapping.rowidColumn()
        val attrs = rEntity.attributes.values

        val t = DSL_CTX.createTable(tableName)

        val constraints = mutableListOf<Constraint>()
        constraints.add(constraint("PK_$tableName").primaryKey(rowid))
        constraints += genAttrConstraints(sqlCtx, tableName, attrs)

        var q = t.column(rowid, SQLDataType.BIGINT.nullable(false))
        q = genAttrColumns(attrs, q)

        val keyNameGen = keyNameGen(tableName, listOf())
        for (rKey in rEntity.keys) {
            val c = makeConstraint(rKey, keyNameGen)
            constraints.add(c)
        }

        var ddl = q.constraints(*constraints.toTypedArray()).toString() + ";\n"

        val indexNameGen = indexNameGen(tableName, listOf())
        for (rIndex in rEntity.indexes) {
            val indexSql = genCreateIndexSql(rEntity, tableName, rIndex, indexNameGen)
            ddl += "$indexSql;\n"
        }

        return ddl
    }

    private fun genAttrColumns(
        attrs: Collection<R_Attribute>,
        step: CreateTableElementListStep,
    ): CreateTableElementListStep {
        var q = step
        for (attr in attrs) {
            q = q.column(attr.sqlMapping, getSqlType(attr.type))
        }
        return q
    }

    private fun genAttrConstraints(sqlCtx: Rt_SqlContext, sqlTable: String, attrs: Collection<R_Attribute>): List<Constraint> {
        val constraints = mutableListOf<Constraint>()

        for (attr in attrs) {
            if (attr.type is R_EntityType) {
                val refEntity = attr.type.rEntity
                val refTable = refEntity.sqlMapping.table(sqlCtx)
                val constraint = constraint("${sqlTable}_${attr.sqlMapping}_FK")
                        .foreignKey(attr.sqlMapping)
                        .references(refTable, refEntity.sqlMapping.rowidColumn())
                constraints.add(constraint)
            }
        }

        return constraints
    }

    fun keyNameGen(table: String, existingNames: Collection<String>): SqlNameGen {
        return SqlNameGen("K_${table}_%d", existingNames)
    }

    fun genCreateKeySql(table: String, rKey: R_Key, nameGen: SqlNameGen): String {
        val c = makeConstraint(rKey, nameGen)
        return DSL_CTX.alterTable(table).add(c).toString()
    }

    private fun makeConstraint(rKey: R_Key, nameGen: SqlNameGen): Constraint {
        val keyName = nameGen.nextName()
        val attribs = rKey.attribs.map { it.str }
        return constraint(keyName).unique(*attribs.toTypedArray())
    }

    fun indexNameGen(table: String, existingNames: Collection<String>): SqlNameGen {
        return SqlNameGen("IDX_${table}_%d", existingNames)
    }

    fun genCreateIndexSql(rEntity: R_EntityDefinition, table: String, index: R_Index, nameGen: SqlNameGen): String {
        val indexName = nameGen.nextName()
        return if (index.attribs.size == 1 && isJsonAttr(rEntity, index.attribs[0].str)) {
            val attrName = index.attribs[0]
            """CREATE INDEX "$indexName" ON "$table" USING gin ("$attrName" jsonb_path_ops)"""
        } else {
            val attribs = index.attribs.map { it.str }
            DSL_CTX.createIndex(indexName).on(table, *attribs.toTypedArray()).toString()
        }
    }

    private fun isJsonAttr(rEntity: R_EntityDefinition, name: String): Boolean {
        val attr = rEntity.strAttributes[name]
        return attr?.type == R_JsonType
    }

    fun genAddColumnSql(table: String, attr: R_Attribute, nullable: Boolean): String {
        val type = getSqlType(attr.type).nullable(nullable)
        val b = DSL_CTX.alterTable(table).addColumn(attr.sqlMapping, type)
        return b.toString()
    }

    fun genAddAttrConstraintsSql(sqlCtx: Rt_SqlContext, table: String, attrs: List<R_Attribute>): String {
        val constraints = genAttrConstraints(sqlCtx, table, attrs)
        if (constraints.isEmpty()) {
            return ""
        }

        val b = DSL_CTX.alterTable(table)
        for (c in constraints) {
            b.add(c)
        }

        return b.toString()
    }

    fun joinSqls(sqls: List<String>) = sqls.joinToString("\n") + "\n"
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

private fun getSqlType(t: R_Type): DataType<*> {
    val sqlType = t.sqlAdapter.sqlType
    sqlType ?: throw Exception("Type $t is not SQL-compatible")
    return sqlType.nullable(false)
}
