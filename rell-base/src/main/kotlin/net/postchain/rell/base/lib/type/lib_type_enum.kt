/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.lib.C_TypeStaticMember
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty_RtValue
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.toImmList

object Lib_Type_Enum {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("enum", abstract = true, hidden = true) {
            supertypeStrategySpecial { mType ->
                L_TypeUtils.getRType(mType) is R_EnumType
            }
        }

        namespace("rell") {
            extension("enum_ext", type = "T") {
                generic("T", subOf = "enum")

                property("name", type = "text", pure = true) {
                    value { a ->
                        val attr = a.asEnum()
                        Rt_TextValue.get(attr.name)
                    }
                }

                // Db-function is effectively a no-op, as enums are represented by their numeric values on SQL level.
                property("value", type = "integer", body = C_SysFunctionBody.simple(
                    pure = true,
                    dbFn = Db_SysFunction.template("enum_value", 1, "(#0)")
                ) { a ->
                    val attr = a.asEnum()
                    Rt_IntValue.get(attr.value.toLong())
                })

                staticFunction("values", result = "list<T>", pure = true) {
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

                staticFunction("value", result = "T", pure = true) {
                    param("name", type = "text")
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

                staticFunction("value", result = "T", pure = true) {
                    param("value", type = "integer")
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

    fun getStaticMembers(type: R_EnumType): List<C_TypeStaticMember> {
        val defPath = type.enum.cDefName.toPath()
        return type.enum.attrs
            .map { attr ->
                val defName = defPath.subName(attr.rName)
                val prop = C_NamespaceProperty_RtValue(type.getValue(attr))
                C_TypeStaticMember.makeProperty(defName, attr.rName, prop, type, attr.ideInfo)
            }
            .toImmList()
    }
}
