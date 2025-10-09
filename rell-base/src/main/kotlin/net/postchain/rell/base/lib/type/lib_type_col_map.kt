/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.collect.Iterables
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.immListOf

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
                    val (keyType, valueType) = fnBodyMeta.typeArgs("K", "V")
                    val mapType = R_MapType(keyType, valueType)
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
                    val (keyType, valueType) = fnBodyMeta.typeArgs("K", "V")
                    val mapType = R_MapType(keyType, valueType)
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
                                Rt_Utils.check(v0 == null) { "map:new:iterator:dupkey:${k.strCode()}" toCodeMsg
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
                    Rt_SetValue(mapValue.type.keySetType, r)
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
                    Rt_ListValue(mapValue.type.valueListType, r)
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

data class R_MapKeyValueTypes(val key: R_Type, val value: R_Type)

class R_MapType(
    val keyValueTypes: R_MapKeyValueTypes
): R_CompositeType("map<${keyValueTypes.key.strCode()},${keyValueTypes.value.strCode()}>") {
    constructor(keyType: R_Type, valueType: R_Type): this(R_MapKeyValueTypes(keyType, valueType))

    val keyType = keyValueTypes.key
    val valueType = keyValueTypes.value
    val keySetType = R_SetType(keyType)
    val valueListType = R_ListType(valueType)
    val virtualType = R_VirtualMapType(this)

    val entryType = R_TupleType.make(keyType, valueType)

    val legacyEntryType: R_TupleType by lazy {
        R_TupleType.makeNamed("k" to keyType, "v" to valueType)
    }

    private val isError = keyType.isError() || valueType.isError()

    override fun equals0(other: R_Type) = other is R_MapType && keyValueTypes == other.keyValueTypes
    override fun hashCode0() = keyValueTypes.hashCode()

    override fun isReference() = true
    override fun isError() = isError
    override fun isDirectMutable() = true
    override fun isDirectVirtualable() = keyType == R_TextType

    override fun strCode() = name
    override fun getTypeArgs() = immListOf(keyType, valueType)

    override fun getLibType0() = C_LibType.make(Lib_Rell.MAP_TYPE, keyType, valueType)

    override fun fromCli(s: String): Rt_Value {
        val map = s.split(",").associate {
            val (k, v) = it.split("=")
            keyType.fromCli(k) to valueType.fromCli(v)
        }
        return Rt_MapValue(this, map.toMutableMap())
    }

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Map(this)

    override fun toMetaGtv() = mapOf(
        "type" to "map".toGtv(),
        "key" to keyType.toMetaGtv(),
        "value" to valueType.toMetaGtv(),
    ).toGtv()

    companion object {
        internal val META = R_TypeMeta.make { k, v -> R_MapType(k, v) }
    }
}

class Rt_MapValue(val type: R_MapType, map: MutableMap<Rt_Value, Rt_Value>): Rt_Value() {
    val map: Map<Rt_Value, Rt_Value> = map

    private val mutableMap = map

    override val valueType = Rt_CoreValueTypes.MAP.type()

    override fun type() = type
    override fun asMap() = map
    override fun asMutableMap() = mutableMap
    override fun asMapValue() = this

    override fun equals(other: Any?) = other === this || (other is Rt_MapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, showTupleFieldNames, map)

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
        val entryType = if (legacy) type.legacyEntryType else type.entryType
        return Iterables.transform(map.entries) { entry ->
            Rt_TupleValue(entryType, immListOf(entry.key, entry.value))
        }
    }

    companion object {
        fun strCode(type: R_Type, showTupleFieldNames: Boolean, map: Map<Rt_Value, Rt_Value>): String {
            val entries = map.entries.joinToString(",") { (key, value) ->
                key.strCode(false) + "=" + value.strCode(false)
            }
            return "${type.strCode()}[$entries]"
        }
    }
}

private class GtvRtConversion_Map(val type: R_MapType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val keyType = type.keyType
        val valueType = type.valueType
        val m = rt.asMap()
        return if (keyType == R_TextType) {
            val m2 = m.mapKeys { (k, _) -> k.asString() }
                    .mapValues { (_, v) -> valueType.rtToGtv(v, pretty) }
            GtvFactory.gtv(m2)
        } else {
            val entries = m.map { (k, v) -> GtvArray(arrayOf(keyType.rtToGtv(k, pretty), valueType.rtToGtv(v, pretty))) }
            GtvArray(entries.toTypedArray())
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val map = if (type.keyType == R_TextType && gtv.type == GtvType.DICT) {
            GtvRtUtils.gtvToMap(ctx, gtv, type)
                    .mapKeys { (k, _) -> Rt_TextValue.get(k) }
                    .mapValues { (_, v) -> type.valueType.gtvToRt(ctx, v) }
                    .toMutableMap()
        } else {
            val tmp = mutableMapOf<Rt_Value, Rt_Value>()
            for (gtvEntry in GtvRtUtils.gtvToArray(ctx, gtv, type)) {
                val (key, value) = gtvToRtEntry(ctx, gtvEntry)
                if (key in tmp) {
                    throw GtvRtUtils.errGtv(ctx, "map_dup_key:${key.strCode()}", "Duplicate map key: ${key.str()}")
                }
                tmp[key] = value
            }
            tmp
        }
        return ctx.rtValue {
            Rt_MapValue(type, map)
        }
    }

    private fun gtvToRtEntry(ctx: GtvToRtContext, gtv: Gtv): Pair<Rt_Value, Rt_Value> {
        val array = GtvRtUtils.gtvToArray(ctx, gtv, 2, "map_entry_size", type)
        val key = type.keyType.gtvToRt(ctx, array[0])
        val value = type.valueType.gtvToRt(ctx, array[1])
        return Pair(key, value)
    }
}
