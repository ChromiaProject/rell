/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_TypeStaticMember
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty_RtValue
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_EnumAttr
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

object Lib_Type_Enum {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("enum", abstract = true, hidden = true, since = "0.7.0") {
            comment("""
                An enum is a set of member constants who share a type.

                The declaration `enum example { A, B, C }` defines a type `example`, with three member constants,
                `example.A`, `example.B` and `example.C`.

                Enum names and member constant names follow the same rules as all identifiers in Rell. However, by
                convention, enum names use `snake_case`, and member constants use `UPPER_SNAKE_CASE`.

                Examples:
                - `enum primary_color { RED, BLUE, GREEN }`
                - `enum error { TIMEOUT, MALFORMED_RESPONSE, NOT_FOUND, UNAUTHORIZED, UNKNOWN }`
                - `enum cardinal_direction { NORTH, EAST, SOUTH, WEST }`
            """)
            supertypeStrategySpecial { mType ->
                L_TypeUtils.getRType(mType) is R_EnumType
            }
        }

        namespace("rell") {
            extension("enum_ext", type = "T", since = "0.7.0") {
                generic("T", subOf = "enum")

                property("name", type = "text", pure = true, since = "0.7.0") {
                    comment("Get the declared name of the given enum member constant.")
                    value { a ->
                        val attr = a.asEnum()
                        Rt_TextValue.get(attr.name)
                    }
                }

                // Db-function is effectively a no-op, as enums are represented by their numeric values on SQL level.
                property("value", type = "integer", since = "0.7.0", body = C_SysFunctionBody.simple(
                    pure = true,
                    dbFn = Db_SysFunction.template("enum_value", 1, "(#0)")
                ) { a ->
                    val attr = a.asEnum()
                    Rt_IntValue.get(attr.value.toLong())
                }) {
                    comment("""
                        Get the ordinal value of an enum member constant.

                        Enum member constants are assigned integer values in the order in which they are declared,
                        starting with `0`.

                        With the definition `enum example { A, B, C }`, we have:
                        - `example.A.value` returns `0`
                        - `example.B.value` returns `1`
                        - `example.C.value` returns `2`
                    """)
                }

                staticFunction("values", result = "list<T>", pure = true, since = "0.7.0") {
                    comment("""
                        Get all member constants of this enum as a list, in the order in which they are defined.

                        For example, `primary_color.values()` returns `[RED, BLUE, GREEN]` where `primary_color` is
                        defined `enum primary_color { RED, BLUE, GREEN }`
                    """)
                    bodyMeta {
                        val listType = fnBodyMeta.rResultType as R_ListType
                        val enumType = listType.elementType as R_EnumType
                        //val enumType = bm.rTypeArgs.getValue("T") as R_EnumType
                        //val listType = R_ListType(enumType)
                        body { ->
                            val list = enumType.enum.values().toMutableList()
                            Rt_ListValue(listType, list)
                        }
                    }
                }

                staticFunction("value", result = "T", pure = true, since = "0.7.0") {
                    comment("""
                        Get an enum member constant value by its name.

                        With the definition `enum example { A, B, C }`, we have:
                        - `example.value("A")` returns `A`
                        - `example.value("B")` returns `B`
                        - `example.value("C")` returns `C`
                        - `example.value("D")` throws an exception
                        @throws exception if there is no member constant in this enum with the given name
                    """)
                    param("name", type = "text", comment = "the name of the enum member constant")
                    bodyMeta {
                        val enumType = fnBodyMeta.rResultType as R_EnumType
                        val enum = enumType.enum
                        body { a ->
                            val name = a.asString()
                            val attr = enum.attr(name)
                            if (attr == null) {
                                throw Rt_Exception.common(
                                    "enum_badname:${enum.appLevelName}:$name",
                                    "Enum '${enum.simpleName}' has no value '$name'",
                                )
                            }
                            enum.type.getValue(attr)
                        }
                    }
                }

                staticFunction("value", result = "T", pure = true, since = "0.7.0") {
                    comment("""
                        Get an enum member constant by its ordinal value.

                        With the definition `enum example { A, B, C }`, we have:
                        - `example.value(0)` returns `A`
                        - `example.value(1)` returns `B`
                        - `example.value(2)` returns `C`
                        - `example.value(3)` throws an exception
                        @throws exception if there is no member constant with the given ordinal value in this enum, i.e.
                        if `value` is greater than the number of constants defined in this enum
                    """)
                    param("value", type = "integer", comment = "the ordinal value of an enum member constant")
                    bodyMeta {
                        val enumType = fnBodyMeta.rResultType as R_EnumType
                        val enum = enumType.enum
                        body { a ->
                            val value = a.asInteger()
                            val attr = enum.attr(value)
                            if (attr == null) {
                                throw Rt_Exception.common(
                                    "enum_badvalue:${enum.appLevelName}:$value",
                                    "Enum '${enum.simpleName}' has no value $value",
                                )
                            }
                            enum.type.getValue(attr)
                        }
                    }
                }
            }
        }
    }

    internal fun getStaticMembers(type: R_EnumType): ImmList<C_TypeStaticMember> {
        val defPath = type.enum.cDefName.toPath()
        return type.enum.attrs.mapToImmList { attr ->
            val defName = defPath.subName(attr.rName)
            val prop = C_NamespaceProperty_RtValue(type.getValue(attr), type, C_EnumValueVarId(type, attr))
            val restrictions = C_MemberRestrictions.NULL
            C_TypeStaticMember.makeProperty(defName, attr.rName, prop, type, attr.ideInfo, restrictions)
        }
    }

    private data class C_EnumValueVarId(private val enumType: R_EnumType, private val attr: R_EnumAttr): C_VarId() {
        override fun nameMsg() = "${enumType.defName.appLevelName}.${attr.name}"
    }
}
