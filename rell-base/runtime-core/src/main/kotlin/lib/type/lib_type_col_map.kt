/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type


import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.R_MapType
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils

object Lib_Type_Map {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("map_entry", hidden = true, since = "0.13.2") {
            """
                A supertype of all two-element tuples `(K, V)` (with named and unnamed fields), where `K` is an
                immutable type, and `V` may be either mutable or immutable. Generally used for iterating over maps and
                constructing maps from iterables.
            """.comment()
            generic("-K")
            generic("-V")

            supertypeStrategyComposite { mType ->
                C_LibUtils.asMapEntryOrNull(mType) != null
            }
        }

        type("map", since = SINCE0) {
            """
                A mutable map from keys of type `K` to values of type `V`, where `K` is immutable, and `V` may be either
                mutable or immutable. `map<K,V> is a subtype of `iterable<(K,V)>`. It is implemented as a hash-map, with
                iteration order determined by the order in which the entries were added.
                @see 1. <a href="../iterable/index.html"><code>iterable</code> - Rell Standard Library</a>
            """.comment()
            generic("K", subOf = "immutable")
            generic("V")
            parent("iterable<(K,V)>")

            rTypeMeta(R_MapType.META)

            defCommonFunctions(this)

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty map.")
                bodyMeta {
                    val (keyR, valueR) = typeArgsR("K", "V")
                    bodyContext { ctx ->
                        val interpreter = ctx.exeCtx.appCtx.interpreter
                        val mapType = Rt_MapType(interpreter.resolveRType(keyR), interpreter.resolveRType(valueR))
                        Rt_MapValue(mapType, mutableMapOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                """
                    Construct a new map by copying the entries from another iterable. The provided entries are an
                    iterable with element type `map_entry<K,V>`; a supertype of the two-element tuple `(K,V)`.
                """.comment()
                val entries by param(
                    "iterable<-map_entry<K,V>>",
                    cast = Rt_IterableValue,
                    comment = "entries with which to populate the map",
                )
                bodyMeta {
                    val (keyR, valueR) = typeArgsR("K", "V")
                    bodyContext { ctx ->
                        val interpreter = ctx.exeCtx.appCtx.interpreter
                        val mapType = Rt_MapType(interpreter.resolveRType(keyR), interpreter.resolveRType(valueR))
                        val iterable = entries
                        val map = if (iterable is Rt_MapValue) {
                            iterable.map.toMutableMap()
                        } else {
                            val tmap = mutableMapOf<Rt_Value, Rt_Value>()
                            for (item in iterable) {
                                val tup = (item as Rt_TupleValue).elements
                                val k = tup[0]
                                val v = tup[1]
                                val v0 = tmap.put(k, v)
                                Rt_Utils.check(v0 == null) { "map:new:iterator:dupkey:${k.strCode()}" to
                                        "Duplicate key: ${k.str()}" }
                            }
                            tmap
                        }
                        Rt_MapValue(mapType, map)
                    }
                }
            }

            function("keys", result = "set<K>", pure = true, since = SINCE0) {
                """
                    Returns a set containing the keys of this map.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].keys()` returns `set([1, 2, 3])`.
                    - `map<K,V>().keys()` returns `set()` (where `K` and `V` are valid types).
                """.comment()

                val self by self(Rt_MapValue)
                body {
                    val r = mutableSetOf<Rt_Value>()
                    r.addAll(self.map.keys)
                    val keySetType = (self.type as? Rt_MapType)?.let { Rt_SetType(it.key) }
                        ?: Rt_GenericRrType(RR_Type.Error, "set<?>")
                    Rt_SetValue(keySetType, r)
                }
            }

            function("values", result = "list<V>", pure = true, since = SINCE0) {
                """
                    Returns a list containing the values of this map.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].values()` returns `['a', 'b', 'c']`.
                    - `map<K,V>().values()` returns `[]` (where `K` and `V` are valid types).
                """.comment()

                val self by self(Rt_MapValue)
                body {
                    val r = self.map.values.toMutableList()
                    val valueListType = (self.type as? Rt_MapType)?.let { Rt_ListType(it.value) }
                        ?: Rt_GenericRrType(RR_Type.Error, "list<?>")
                    Rt_ListValue(valueListType, r)
                }
            }

            function("clear", result = "unit", since = SINCE0) {
                """
                    Clear this map; i.e. remove all its entries. Immediately after this method returns, this map is
                    empty.
                """.comment()

                val self by self(Rt_MapValue)
                body {
                    self.mutableMapView.clear()
                    Rt_UnitValue
                }
            }

            function("put", result = "unit", since = SINCE0) {
                comment("Associate the specified value with the specified key in this map.")
                val self by self(Rt_MapValue)
                val key by param("K", cast = Rt_Value, comment = "the key to associate with the specified value")
                val value by param("V", cast = Rt_Value, comment = "the value to associate with the specified key")
                body {
                    self.mutableMapView[key] = value
                    Rt_UnitValue
                }
            }

            function("put_all", result = "unit", since = "0.9.0") {
                """
                    Update this map with the entries from another map. Where a key is found in both this and the passed
                    map, the corresponding value in the passed map overwrites the value in this map. In other words,
                    this is a *right-biased* operation.
                """.comment()
                alias("putAll", C_MessageType.ERROR, since = SINCE0)
                val self by self(Rt_MapValue)
                val map by param(
                    "map<-K,-V>",
                    cast = Rt_MapValue,
                    comment = "the map whose entries are used to update this map",
                )
                body {
                    self.mutableMapView.putAll(map.mapView)
                    Rt_UnitValue
                }
            }

            function("remove", result = "V", since = SINCE0) {
                """
                    Remove an entry from this map.
                    @return the value of the removed entry
                    @throws exception if the key is not found in the map
                """.comment()
                val self by self(Rt_MapValue)
                val key by param("K", cast = Rt_Value, comment = "the key of the entry to remove")
                body {
                    self.mutableMapView.remove(key) ?: throw Rt_Exception.common(
                        "fn:map.remove:novalue:${key.strCode()}",
                        "Key not in map: ${key.str()}"
                    )
                }
            }

            function("remove_or_null", result = "V?", since = "0.11.0") {
                """
                    Remove an entry from this map.
                    @return the value of the removed entry, or `null` if the key is not found in the map
                """.comment()
                val self by self(Rt_MapValue)
                val key by param("K", cast = Rt_Value, comment = "the key of the entry to remove")
                body {
                    self.mutableMapView.remove(key) ?: Rt_NullValue
                }
            }

            function("put_all_copy", result = "map<K, V>", since = "0.14.16") {
                """
                    Returns a new map with the mappings of this map combined with the mappings of the given map.

                    Where a value exists for a given key in both this and the other map, the returned map has the value
                    of the other map, as opposed to the value in this map (right-biased).

                    `a.put_all_copy(b)` is equivalent to `a + b`, where `a` and `b` are both maps.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].put_all_copy([1: 'Z', 4: 'd', 5: 'e'])` returns
                        `[1: 'Z', 2: 'b', 3: 'c', 4: 'd', 5: 'e']`
                    - `[1: 'a', 2: 'b', 3: 'c'].put_all_copy([4: 'd', 5: 'e'])` returns
                        `[1: 'a', 2: 'b', 3: 'c', 4: 'd', 5: 'e']`
                """.comment()
                val self by self(Rt_MapValue)
                val map by param(
                    "map<-K, -V>",
                    cast = Rt_MapValue,
                    comment = "the other map",
                )
                body { evalMergeMap(self, map) }
            }
        }
    }

    fun <T : Rt_Value> defCommonFunctions(m: Ld_TypeDefDsl<T>) = with(m) {
        function("to_text", result = "text", since = SINCE0) {
            comment("Returns a textual representation of this map.")
            alias("str", since = SINCE0)
            bodyRaw(Lib_Type_Any.ToText_NoDb)
        }

        function("empty", result = "boolean", pure = true, since = SINCE0) {
            """
                Check if this map is empty.
                @return `true` if this map is empty, `false` otherwise
            """.comment()
            val self by self(Rt_MapBackedValue)
            body(Rt_BooleanValue) {
                self.mapView.isEmpty()
            }
        }

        function("size", result = "integer", pure = true, since = SINCE0) {
            comment("Get the size (number of entries) of this map.")
            alias("len", C_MessageType.ERROR, since = SINCE0)
            val self by self(Rt_MapBackedValue)
            body(Rt_IntValue) {
                self.mapView.size.toLong()
            }
        }

        function("get", result = "V", pure = true, since = SINCE0) {
            """
                Get the value associated with the given key in this map.
                @throws exception if the key is not found
            """.comment()
            val self by self(Rt_MapBackedValue)
            val key by param("K", cast = Rt_Value, comment = "the key to look up")
            body {
                self.mapView[key] ?: throw Rt_Exception.common(
                    "fn:map.get:novalue:${key.strCode()}",
                    "Key not in map: ${key.str()}"
                )
            }
        }

        function("get_or_null", result = "V?", pure = true, since = "0.11.0") {
            """
                Get the value associated with the given key in this map.
                @return the value associated with the given key in this map, or `null` if the key is not found
            """.comment()
            val self by self(Rt_MapBackedValue)
            val key by param("K", cast = Rt_Value, comment = "the key to look up")
            body {
                self.mapView[key] ?: Rt_NullValue
            }
        }

        function("get_or_default", pure = true, since = "0.11.0") {
            """
                Get the value associated with the given key in this map.
                @return the value associated with the given key in this map, or `default` if the key is not found
            """.comment()
            generic("R", superOf = "V")
            result(type = "R")
            val self by self(Rt_MapBackedValue)
            val key by param("K", cast = Rt_Value, comment = "the key to look up")
            val default by param(
                "R",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the default value (lazily evaluated) to return if the key is not found",
            )
            body {
                self.mapView[key] ?: default.resolveLazy()
            }
        }

        function("contains", result = "boolean", pure = true, since = SINCE0) {
            """
                Check if this map contains the given key.
                @return `true` if this map contains the given key, `false` otherwise
            """.comment()
            val self by self(Rt_MapBackedValue)
            val key by param("K", cast = Rt_Value, comment = "the key to look up")
            body(Rt_BooleanValue) {
                key in self.mapView
            }
        }
    }
}
