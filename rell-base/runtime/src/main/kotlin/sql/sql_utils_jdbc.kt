/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData

interface ResultSetRow {
    val metaData: ResultSetMetaData

    fun wasNull(): Boolean
    fun getString(columnIndex: Int): String?
    fun getBoolean(columnIndex: Int): Boolean
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getBigDecimal(columnIndex: Int): BigDecimal?
    fun getBytes(columnIndex: Int): ByteArray?
    fun getObject(columnIndex: Int): Any?

    companion object {
        fun of(rs: ResultSet): ResultSetRow = DirectResultSetRow(rs)
    }
}

private class DirectResultSetRow(private val rs: ResultSet): ResultSetRow {
    override val metaData: ResultSetMetaData get() = rs.metaData

    override fun wasNull(): Boolean = rs.wasNull()
    override fun getString(columnIndex: Int): String? = rs.getString(columnIndex)
    override fun getBoolean(columnIndex: Int): Boolean = rs.getBoolean(columnIndex)
    override fun getInt(columnIndex: Int): Int = rs.getInt(columnIndex)
    override fun getLong(columnIndex: Int): Long = rs.getLong(columnIndex)
    override fun getBigDecimal(columnIndex: Int): BigDecimal? = rs.getBigDecimal(columnIndex)
    override fun getBytes(columnIndex: Int): ByteArray? = rs.getBytes(columnIndex)
    override fun getObject(columnIndex: Int): Any? = rs.getObject(columnIndex)
}

interface PreparedStatementParams {
    fun setBoolean(parameterIndex: Int, x: Boolean)
    fun setInt(parameterIndex: Int, x: Int)
    fun setLong(parameterIndex: Int, x: Long)
    fun setBigDecimal(parameterIndex: Int, x: BigDecimal?)
    fun setString(parameterIndex: Int, x: String?)
    fun setBytes(parameterIndex: Int, x: ByteArray?)
    fun setObject(parameterIndex: Int, x: Any?)

    companion object {
        fun of (stmt: PreparedStatement): PreparedStatementParams = DirectPreparedStatementParams(stmt)
    }
}

private class DirectPreparedStatementParams(private val stmt: PreparedStatement): PreparedStatementParams {
    override fun setBoolean(parameterIndex: Int, x: Boolean) = stmt.setBoolean(parameterIndex, x)
    override fun setInt(parameterIndex: Int, x: Int) = stmt.setInt(parameterIndex, x)
    override fun setLong(parameterIndex: Int, x: Long) = stmt.setLong(parameterIndex, x)
    override fun setBigDecimal(parameterIndex: Int, x: BigDecimal?) = stmt.setBigDecimal(parameterIndex, x)
    override fun setString(parameterIndex: Int, x: String?) = stmt.setString(parameterIndex, x)
    override fun setBytes(parameterIndex: Int, x: ByteArray?) = stmt.setBytes(parameterIndex, x)
    override fun setObject(parameterIndex: Int, x: Any?) = stmt.setObject(parameterIndex, x)
}
