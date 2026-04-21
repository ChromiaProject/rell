/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.model.R_MapType
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.immListOf

private fun rrTypeName(rrType: RR_Type): String = when (rrType) {
    is RR_Type.Primitive -> rrType.kind.name.lowercase()
    is RR_Type.Nullable -> "${rrTypeName(rrType.value)}?"
    is RR_Type.List -> "list<${rrTypeName(rrType.element)}>"
    is RR_Type.Set -> "set<${rrTypeName(rrType.element)}>"
    is RR_Type.Map -> "map<${rrTypeName(rrType.key)},${rrTypeName(rrType.value)}>"
    else -> "?"
}

object Lib_Type_Map {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("map_entry", hidden = true, since = "0.13.2") {
            comment("""
                A supertype of all two-element tuples `(K, V)` (with named and unnamed fields), where `K` is an
                immutable type, and `V` may be either mutable or immutable. Generally used for iterating over maps and
                constructing maps from iterables.
            """)
            generic("-K")
            generic("-V")

            supertypeStrategyComposite { mType ->
                C_LibUtils.asMapEntryOrNull(mType) != null
            }
        }

        type("map", since = SINCE0) {
            comment("""
                A mutable map from keys of type `K` to values of type `V`, where `K` is immutable, and `V` may be either
                mutable or immutable. `map<K,V> is a subtype of `iterable<(K,V)>`. It is implemented as a hash-map, with
                iteration order determined by the order in which the entries were added.
                @see 1. <a href="../iterable/index.html"><code>iterable</code> - Rell Standard Library</a>
            """)
            generic("K", subOf = "immutable")
            generic("V")
            parent("iterable<(K,V)>")

            rTypeMeta(R_MapType.META)

            defCommonFunctions(this)

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty map.")
                bodyMeta {
                    val (keyType, valueType) = typeArgsRt("K", "V")
                    val mapType = rtMapType(keyType, valueType)
                    body { ->
                        Rt_MapValue(mapType, mutableMapOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("""
                    Construct a new map by copying the entries from another iterable. The provided entries are an
                    iterable with element type `map_entry<K,V>`; a supertype of the two-element tuple `(K,V)`.
                """)
                param("entries", type = "iterable<-map_entry<K,V>>", comment = "entries with which to populate the map")
                bodyMeta {
                    val (keyType, valueType) = typeArgsRt("K", "V")
                    val mapType = rtMapType(keyType, valueType)
                    body { arg ->
                        val map = if (arg is Rt_MapValue) {
                            arg.map.toMutableMap()
                        } else {
                            val iterable = arg.asIterable()
                            val tmap = mutableMapOf<Rt_Value, Rt_Value>()
                            for (item in iterable) {
                                val tup = item.asTuple()
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
                comment("""
                    Returns a set containing the keys of this map.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].keys()` returns `set([1, 2, 3])`.
                    - `map<K,V>().keys()` returns `set()` (where `K` and `V` are valid types).
                """)
                body { a ->
                    val mapValue = a.asMapValue()
                    val r = mutableSetOf<Rt_Value>()
                    r.addAll(mapValue.map.keys)
                    val mapRR = mapValue.type().rrType as? RR_Type.Map
                    val rrType = if (mapRR != null) RR_Type.Set(mapRR.key) else RR_Type.Error
                    val name = if (mapRR != null) "set<${rrTypeName(mapRR.key)}>" else "set<?>"
                    Rt_SetValue(Rt_Type(rrType, name), r)
                }
            }

            function("values", result = "list<V>", pure = true, since = SINCE0) {
                comment("""
                    Returns a list containing the values of this map.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].values()` returns `['a', 'b', 'c']`.
                    - `map<K,V>().values()` returns `[]` (where `K` and `V` are valid types).
                """)
                body { a ->
                    val mapValue = a.asMapValue()
                    val r = mutableListOf<Rt_Value>()
                    r.addAll(mapValue.map.values)
                    val mapRR = mapValue.type().rrType as? RR_Type.Map
                    val rrType = if (mapRR != null) RR_Type.List(mapRR.value) else RR_Type.Error
                    val name = if (mapRR != null) "list<${rrTypeName(mapRR.value)}>" else "list<?>"
                    Rt_ListValue(Rt_Type(rrType, name), r)
                }
            }

            function("clear", result = "unit", since = SINCE0) {
                comment("""
                    Clear this map; i.e. remove all its entries. Immediately after this method returns, this map is
                    empty.
                """)
                body { a ->
                    val map = a.asMutableMap()
                    map.clear()
                    Rt_UnitValue
                }
            }

            function("put", result = "unit", since = SINCE0) {
                comment("Associate the specified value with the specified key in this map.")
                param("key", type = "K", comment = "the key to associate with the specified value")
                param("value", type = "V", comment = "the value to associate with the specified key")
                body { a, b, c ->
                    val map = a.asMutableMap()
                    map[b] = c
                    Rt_UnitValue
                }
            }

            function("put_all", result = "unit", since = "0.9.0") {
                comment("""
                    Update this map with the entries from another map. Where a key is found in both this and the passed
                    map, the corresponding value in the passed map overwrites the value in this map. In other words,
                    this is a *right-biased* operation.
                """)
                alias("putAll", C_MessageType.ERROR, since = SINCE0)
                param("map", type = "map<-K,-V>", comment = "the map whose entries are used to update this map")
                body { a, b ->
                    val map1 = a.asMutableMap()
                    val map2 = b.asMap()
                    map1.putAll(map2)
                    Rt_UnitValue
                }
            }

            function("remove", result = "V", since = SINCE0) {
                comment("""
                    Remove an entry from this map.
                    @return the value of the removed entry
                    @throws exception if the key is not found in the map
                """)
                param("key", type = "K", comment = "the key of the entry to remove")
                body { a, b ->
                    val map = a.asMutableMap()
                    val v = map.remove(b)
                    v ?: throw Rt_Exception.common("fn:map.remove:novalue:${b.strCode()}", "Key not in map: ${b.str()}")
                }
            }

            function("remove_or_null", result = "V?", since = "0.11.0") {
                comment("""
                    Remove an entry from this map.
                    @return the value of the removed entry, or `null` if the key is not found in the map
                """)
                param("key", type = "K", comment = "the key of the entry to remove")
                body { a, b ->
                    val map = a.asMutableMap()
                    val v = map.remove(b)
                    v ?: Rt_NullValue
                }
            }

            function("put_all_copy", result = "map<K, V>", since = "0.14.16") {
                comment("""
                    Returns a new map with the mappings of this map combined with the mappings of the given map.

                    Where a value exists for a given key in both this and the other map, the returned map has the value
                    of the other map, as opposed to the value in this map (right-biased).

                    `a.put_all_copy(b)` is equivalent to `a + b`, where `a` and `b` are both maps.

                    Examples:
                    - `[1: 'a', 2: 'b', 3: 'c'].put_all_copy([1: 'Z', 4: 'd', 5: 'e'])` returns
                        `[1: 'Z', 2: 'b', 3: 'c', 4: 'd', 5: 'e']`
                    - `[1: 'a', 2: 'b', 3: 'c'].put_all_copy([4: 'd', 5: 'e'])` returns
                        `[1: 'a', 2: 'b', 3: 'c', 4: 'd', 5: 'e']`
                """)
                param("map", type = "map<-K, -V>", comment = "the other map")
                body(::evalMergeMap)
            }
        }
    }

    fun defCommonFunctions(m: Ld_TypeDefDsl) = with(m) {
        function("to_text", result = "text", since = SINCE0) {
            comment("Returns a textual representation of this map.")
            alias("str", since = SINCE0)
            bodyRaw(Lib_Type_Any.ToText_NoDb)
        }

        function("empty", result = "boolean", pure = true, since = SINCE0) {
            comment("""
                Check if this map is empty.
                @return `true` if this map is empty, `false` otherwise
            """)
            body { a ->
                val map = a.asMap()
                Rt_BooleanValue.get(map.isEmpty())
            }
        }

        function("size", result = "integer", pure = true, since = SINCE0) {
            comment("Get the size (number of entries) of this map.")
            alias("len", C_MessageType.ERROR, since = SINCE0)
            body { a ->
                val map = a.asMap()
                Rt_IntValue.get(map.size.toLong())
            }
        }

        function("get", result = "V", pure = true, since = SINCE0) {
            comment("""
                Get the value associated with the given key in this map.
                @throws exception if the key is not found
            """)
            param("key", type = "K", comment = "the key to look up")
            body { self, a ->
                val map = self.asMap()
                val v = map[a]
                v ?: throw Rt_Exception.common("fn:map.get:novalue:${a.strCode()}", "Key not in map: ${a.str()}")
            }
        }

        function("get_or_null", result = "V?", pure = true, since = "0.11.0") {
            comment("""
                Get the value associated with the given key in this map.
                @return the value associated with the given key in this map, or `null` if the key is not found
            """)
            param("key", type = "K", comment = "the key to look up")
            body { self, a ->
                val map = self.asMap()
                val r = map[a]
                r ?: Rt_NullValue
            }
        }

        function("get_or_default", pure = true, since = "0.11.0") {
            comment("""
                Get the value associated with the given key in this map.
                @return the value associated with the given key in this map, or `default` if the key is not found
            """)
            generic("R", superOf = "V")
            result(type = "R")
            param("key", type = "K", comment = "the key to look up")
            param("default", type = "R", lazy = true) {
                comment("the default value (lazily evaluated) to return if the key is not found")
            }
            body { self, a, b ->
                val map = self.asMap()
                map[a] ?: b.asLazyValue()
            }
        }

        function("contains", result = "boolean", pure = true, since = SINCE0) {
            comment("""
                Check if this map contains the given key.
                @return `true` if this map contains the given key, `false` otherwise
            """)
            param("key", type = "K", comment = "the key to look up")
            body { self, a ->
                val map = self.asMap()
                Rt_BooleanValue.get(a in map)
            }
        }
    }
}

class Rt_MapValue(private val rtType: Rt_Type, map: MutableMap<Rt_Value, Rt_Value>): Rt_Value() {
    val map: Map<Rt_Value, Rt_Value> = map
    private val mutableMap = map

    override val valueType = Rt_CoreValueTypes.MAP.type()

    override fun type() = rtType
    override fun asMap() = map
    override fun asMutableMap() = mutableMap
    override fun asMapValue() = this

    override fun equals(other: Any?) = other === this || (other is Rt_MapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(rtType, showTupleFieldNames, map)

    override fun str(format: StrFormat): String {
        return map
            .entries
            .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun strPretty(indent: Int): String {
        if (map.isEmpty()) {
            return str(StrFormat.V2)
        }
        val indentStr = "    ".repeat(indent)
        return map.entries.joinToString(",", "{", "\n$indentStr}") {
            val k = it.key.str(StrFormat.V2)
            val v = it.value.strPretty(indent + 1)
            "\n$indentStr    $k = $v"
        }
    }

    override fun asIterable(): Iterable<Rt_Value> {
        return asIterable(false)
    }

    fun asIterable(legacy: Boolean): Iterable<Rt_Value> {
        val mapRR = rtType.rrType as? RR_Type.Map
        val entryRRType = if (mapRR != null) RR_Type.Tuple(immListOf(
            RR_TupleField(if (legacy) "k" else null, mapRR.key),
            RR_TupleField(if (legacy) "v" else null, mapRR.value),
        )) else RR_Type.Error
        val entryName = if (mapRR != null) {
            val k = rrTypeName(mapRR.key)
            val v = rrTypeName(mapRR.value)
            if (legacy) "(k:$k,v:$v)" else "($k,$v)"
        } else "(?, ?)"
        val entryRtType = Rt_Type(entryRRType, entryName)
        return map.entries.map { entry ->
            Rt_TupleValue(entryRtType, immListOf(entry.key, entry.value))
        }
    }


    companion object {
        fun strCode(type: Rt_Type, showTupleFieldNames: Boolean, map: Map<Rt_Value, Rt_Value>): String {
            val entries = map.entries.joinToString(",") { (key, value) ->
                key.strCode(false) + "=" + value.strCode(false)
            }
            return "${type.name}[$entries]"
        }
    }
}

class GtvRtConversion_Map(
    private val typeName: String,
    private val isTextKey: Boolean,
    keyConversion: Lazy<Rt_TypeGtvConversion>,
    valueConversion: Lazy<Rt_TypeGtvConversion>,
    rtType: Lazy<Rt_Type>,
): GtvRtConversion {
    private val keyConversion by keyConversion
    private val valueConversion by valueConversion
    private val rtType by rtType

    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val m = rt.asMap()
        return if (isTextKey) {
            val m2 = m.mapKeys { (k, _) -> k.asString() }
                    .mapValues { (_, v) -> valueConversion.rtToGtv(v, pretty) }
            GtvFactory.gtv(m2)
        } else {
            val entries = m.map { (k, v) -> GtvArray(arrayOf(keyConversion.rtToGtv(k, pretty), valueConversion.rtToGtv(v, pretty))) }
            GtvArray(entries.toTypedArray())
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val map = if (isTextKey && gtv.type == GtvType.DICT) {
            GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                    .mapKeys { (k, _) -> Rt_TextValue.get(k) }
                    .mapValues { (_, v) -> valueConversion.gtvToRt(ctx, v) }
                    .toMutableMap()
        } else {
            val tmp = mutableMapOf<Rt_Value, Rt_Value>()
            for (gtvEntry in GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)) {
                val (key, value) = gtvToRtEntry(ctx, gtvEntry)
                if (key in tmp) {
                    throw GtvRtUtils.errGtv(ctx, "map_dup_key:${key.strCode()}", "Duplicate map key: ${key.str()}")
                }
                tmp[key] = value
            }
            tmp
        }
        return ctx.rtValue {
            Rt_MapValue(rtType, map)
        }
    }

    private fun gtvToRtEntry(ctx: GtvToRtContext, gtv: Gtv): Pair<Rt_Value, Rt_Value> {
        val array = GtvRtUtils.gtvToArray(ctx, gtv, 2, "map_entry_size", typeName)
        val key = keyConversion.gtvToRt(ctx, array[0])
        val value = valueConversion.gtvToRt(ctx, array[1])
        return Pair(key, value)
    }
}
