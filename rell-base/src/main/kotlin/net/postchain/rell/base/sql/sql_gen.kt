/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.lib.type.R_JsonType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_ChainSqlMapping
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.utils.toImmMap
import org.jooq.Constraint
import org.jooq.CreateTableColumnStep
import org.jooq.DataType
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.SQLDataType

private val disableLogo = run {
    System.setProperty("org.jooq.no-logo", "true")
}

object SqlGen {
    private val disableLogo2 = disableLogo

    val DSL_CTX = DSL.using(SQLDialect.POSTGRES)

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
    ).toImmMap()

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

    fun genEntity(sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition): String {
        val tableName = rEntity.sqlMapping.table(sqlCtx)
        return genEntity(sqlCtx, rEntity, tableName)
    }

    fun genEntity(sqlCtx: Rt_SqlContext, rEntity: R_EntityDefinition, tableName: String): String {
        val mapping = rEntity.sqlMapping
        val rowid = mapping.rowidColumn()
        val attrs = rEntity.attributes.values

        val t = DSL_CTX.createTable(tableName)

        val constraints = mutableListOf<Constraint>()
        constraints.add(constraint("PK_" + tableName).primaryKey(rowid))
        constraints += genAttrConstraints(sqlCtx, tableName, attrs)

        var q = t.column(rowid, SQLDataType.BIGINT.nullable(false))
        q = genAttrColumns(attrs, q)

        for ((kidx, key) in rEntity.keys.withIndex()) {
            val attribs = key.attribs.map { it.str }
            constraints.add(constraint("K_${tableName}_${kidx}").unique(*attribs.toTypedArray()))
        }

        var ddl = q.constraints(*constraints.toTypedArray()).toString() + ";\n"

        val jsonAttribSet = attrs.filter { it.type is R_JsonType }.map { it.name }.toSet()

        for ((iidx, index) in rEntity.indexes.withIndex()) {
            val indexName = "IDX_${tableName}_${iidx}"
            val indexSql : String
            if (index.attribs.size == 1 && jsonAttribSet.contains(index.attribs[0].str)) {
                val attrName = index.attribs[0]
                indexSql = """CREATE INDEX "$indexName" ON "$tableName" USING gin ("${attrName}" jsonb_path_ops)"""
            } else {
                val attribs = index.attribs.map { it.str }
                indexSql = (DSL_CTX.createIndex(indexName).on(tableName, *attribs.toTypedArray())).toString();
            }
            ddl += indexSql + ";\n";
        }

        return ddl
    }

    private fun genAttrColumns(attrs: Collection<R_Attribute>, step: CreateTableColumnStep): CreateTableColumnStep {
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

private fun getSqlType(t: R_Type): DataType<*> {
    val sqlType = t.sqlAdapter.sqlType
    sqlType ?: throw Exception("Type $t is not SQL-compatible")
    return sqlType.nullable(false)
}
