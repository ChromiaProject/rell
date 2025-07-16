/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.doc

import net.postchain.rell.base.lmodel.L_TypeDefDocCodeStrategy
import net.postchain.rell.base.mtype.M_TypeVariance
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals

sealed class DocType {
    internal abstract fun genCode(b: DocCode.Builder, nullable: Boolean = false)

    fun toCode(): DocCode = DocCode.builder().also { genCode(it) }.build()

    internal companion object {
        val ANYTHING: DocType = DocType_Simple("anything")
        val NOTHING: DocType = DocType_Simple("nothing")
        val ANY: DocType = DocType_Simple("any")
        val NULL: DocType = DocType_Keyword("null")

        fun raw(s: String): DocType = DocType_Raw(s)
        fun name(name: String): DocType = DocType_Simple(name)
        fun nullable(valueType: DocType): DocType = DocType_Nullable(valueType)
        fun function(resultType: DocType, paramTypes: ImmList<DocType>): DocType = DocType_Function(resultType, paramTypes)

        fun tuple(fieldTypes: ImmList<DocType>, fieldNames: ImmList<String?>): DocType {
            return DocType_Tuple(fieldTypes, fieldNames)
        }

        fun generic(docCodeStrategy: L_TypeDefDocCodeStrategy, args: ImmList<DocTypeSet>): DocType {
            return DocType_Generic(docCodeStrategy, args)
        }
    }
}

internal sealed class DocTypeSet {
    abstract fun genCode(b: DocCode.Builder)

    companion object {
        val ALL: DocTypeSet = DocTypeSet_All

        fun one(type: DocType): DocTypeSet = DocTypeSet_Basic(type, "")
        fun superOf(type: DocType): DocTypeSet = DocTypeSet_Basic(type, "+")
        fun subOf(type: DocType): DocTypeSet = DocTypeSet_Basic(type, "-")
    }
}

internal class DocTypeParam(
    val name: String,
    val variance: M_TypeVariance,
    val bounds: DocTypeSet,
)

private data object DocTypeSet_All: DocTypeSet() {
    override fun genCode(b: DocCode.Builder) {
        b.raw("*")
    }
}

private class DocTypeSet_Basic(private val type: DocType, private val op: String): DocTypeSet() {
    override fun genCode(b: DocCode.Builder) {
        b.raw(op)
        type.genCode(b)
    }
}

private class DocType_Simple(private val name: String): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        b.link(name)
    }
}

private class DocType_Keyword(private val name: String): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        b.keyword(name)
    }
}

private class DocType_Raw(private val s: String): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        b.raw(s)
    }
}

private class DocType_Nullable(private val valueType: DocType): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        valueType.genCode(b, true)
        b.raw("?")
    }
}

private class DocType_Function(
    private val resultType: DocType,
    private val paramTypes: ImmList<DocType>,
): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        if (nullable) {
            b.raw("(")
        }

        b.raw("(")
        for ((i, paramType) in paramTypes.withIndex()) {
            if (i > 0) b.sep(", ")
            paramType.genCode(b)
        }
        b.raw(") -> ")

        resultType.genCode(b)

        if (nullable) {
            b.raw(")")
        }
    }
}

private class DocType_Tuple(
    private val fieldTypes: ImmList<DocType>,
    private val fieldNames: ImmList<String?>,
): DocType() {
    init {
        checkEquals(fieldNames.size, fieldTypes.size)
    }

    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        b.raw("(")

        for ((i, fieldType) in fieldTypes.withIndex()) {
            if (i > 0) b.sep(", ")
            val name = fieldNames[i]
            if (name != null) {
                b.raw(name)
                b.sep(": ")
            }
            fieldType.genCode(b)
        }

        b.raw(")")
    }
}

private class DocType_Generic(
    private val docCodeStrategy: L_TypeDefDocCodeStrategy,
    private val args: ImmList<DocTypeSet>,
): DocType() {
    override fun genCode(b: DocCode.Builder, nullable: Boolean) {
        val docArgs = args.map { arg ->
            DocCode.builder()
                .also { arg.genCode(it) }
                .build()
        }

        val docCode = docCodeStrategy.docCode(docArgs)
        b.append(docCode)
    }
}
