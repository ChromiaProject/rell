/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_VirtualMapType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.doc.DocCode

object Lib_Type_VirtualMap {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("virtual_map", hidden = true, since = "0.9.0") {
            generic("K", subOf = "immutable")
            generic("V0")
            generic("V")
            parent("iterable<(K,V)>")

            rTypeMeta(R_VirtualMapType.META)

            docCode { k, v, _ ->
                DocCode.builder()
                    .keyword("virtual").raw("<")
                    .link("map").raw("<").append(k).sep(", ").append(v).raw(">")
                    .raw(">")
                    .build()
            }

            Lib_Type_Map.defCommonFunctions(this)

            function("keys", result = "set<K>", pure = true, since = "0.9.0") {
                bodyMeta {
                    val keyR = typeArgR("K")
                    bodyContext { ctx, a ->
                        val keySetType = Rt_SetType(ctx.exeCtx.appCtx.interpreter.resolveRType(keyR))
                        val map = (a as Rt_MapBackedValue).mapView
                        val r = map.keys.toMutableSet()
                        Rt_SetValue(keySetType, r)
                    }
                }
            }

            function("values", result = "list<V>", pure = true, since = "0.9.0") {
                bodyMeta {
                    val valueR = typeArgR("V")
                    bodyContext { ctx, a ->
                        val valueListType = Rt_ListType(ctx.exeCtx.appCtx.interpreter.resolveRType(valueR))
                        val map = (a as Rt_MapBackedValue).mapView
                        val r = map.values.toMutableList()
                        Rt_ListValue(valueListType, r)
                    }
                }
            }

            function("to_full", result = "map<K,V0>", since = "0.9.0") {
                bodyRaw(Lib_Type_Virtual.ToFull)
            }
        }
    }
}
