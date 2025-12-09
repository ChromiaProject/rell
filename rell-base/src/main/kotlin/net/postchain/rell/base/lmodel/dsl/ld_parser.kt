/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.Parser
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.mapToImmList

object Ld_Parser {
    fun parseType(code: String): Ld_Type {
        val astType = Ld_Grammar.parseToEnd(code)
        return convertType(astType, Exception())
    }

    private fun convertType(type: M_AstType, pos: Exception): Ld_Type {
        return when (type) {
            is M_AstType_Name -> Ld_Type_Name(Ld_FullName.parse(type.name), pos)
            is M_AstType_Generic -> {
                val args = type.args.mapToImmList { convertTypeSet(it, pos) }
                Ld_Type_Generic(Ld_FullName.parse(type.name), args, pos)
            }
            is M_AstType_Function -> {
                val ldResult = convertType(type.result, pos)
                val ldParams = type.params.mapToImmList { convertType(it, pos) }
                Ld_Type_Function(ldResult, ldParams)
            }
            is M_AstType_Nullable -> Ld_Type_Nullable(convertType(type.valueType, pos))
            is M_AstType_Tuple -> {
                val ldFields = type.fields.mapToImmList { it.first to convertType(it.second, pos) }
                Ld_Type_Tuple(ldFields)
            }
        }
    }

    private fun convertTypeSet(typeSet: M_AstTypeSet, pos: Exception): Ld_TypeSet {
        return when (typeSet) {
            is M_AstTypeSet_One -> Ld_TypeSet_One(convertType(typeSet.type, pos))
            is M_AstTypeSet_SubOf -> Ld_TypeSet_SubOf(convertType(typeSet.type, pos))
            else -> throw IllegalArgumentException()
        }
    }
}

private object Ld_Grammar: M_TypeGrammar<M_AstType>() {
    private val simpleName: Parser<String> by NAME map { it.text }

    private val qualifiedName: Parser<String> by separatedTerms(simpleName, DOT) map {
        it.joinToString(".")
    }

    override val name by optional(qualifiedName * -COLON_COLON) * qualifiedName map {
        (module, name) ->
        if (module == null) name else "$module::$name"
    }

    override val rootParser by type0
}
