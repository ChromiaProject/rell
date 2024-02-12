/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualMapType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.utils.immListOf

object Lib_Type_Map {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("map_entry", hidden = true) {
            generic("-K")
            generic("-V")

            supertypeStrategyComposite { mType ->
                C_LibUtils.asMapEntryOrNull(mType) != null
            }
        }

        type("map") {
            generic("K", subOf = "immutable")
            generic("V")
            parent("iterable<(K,V)>")

            rType { k, v -> R_MapType(k, v) }

            defCommonFunctions(this)

            constructor(pure = true) {
                bodyMeta {
                    val (keyType, valueType) = fnBodyMeta.typeArgs("K", "V")
                    val mapType = R_MapType(keyType, valueType)
                    body { ->
                        Rt_MapValue(mapType, mutableMapOf())
                    }
                }
            }

            constructor(pure = true) {
                param("entries", type = "iterable<-map_entry<K,V>>")
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

            function("keys", result = "set<K>", pure = true) {
                body { a ->
                    val mapValue = a.asMapValue()
                    val r = mutableSetOf<Rt_Value>()
                    r.addAll(mapValue.map.keys)
                    Rt_SetValue(mapValue.type.keySetType, r)
                }
            }

            function("values", result = "list<V>", pure = true) {
                body { a ->
                    val mapValue = a.asMapValue()
                    val r = mutableListOf<Rt_Value>()
                    r.addAll(mapValue.map.values)
                    Rt_ListValue(mapValue.type.valueListType, r)
                }
            }

            function("clear", result = "unit") {
                body { a ->
                    val map = a.asMutableMap()
                    map.clear()
                    Rt_UnitValue
                }
            }

            function("put", result = "unit") {
                param("key", type = "K")
                param("value", type = "V")
                body { a, b, c ->
                    val map = a.asMutableMap()
                    map[b] = c
                    Rt_UnitValue
                }
            }

            function("put_all", result = "unit") {
                alias("putAll", C_MessageType.ERROR)
                param("map", type = "map<-K,-V>")
                body { a, b ->
                    val map1 = a.asMutableMap()
                    val map2 = b.asMap()
                    map1.putAll(map2)
                    Rt_UnitValue
                }
            }

            function("remove", result = "V") {
                param("key", type = "K")
                body { a, b ->
                    val map = a.asMutableMap()
                    val v = map.remove(b)
                    v ?: throw Rt_Exception.common("fn:map.remove:novalue:${b.strCode()}", "Key not in map: ${b.str()}")
                }
            }

            function("remove_or_null", result = "V?") {
                param("key", type = "K")
                body { a, b ->
                    val map = a.asMutableMap()
                    val v = map.remove(b)
                    v ?: Rt_NullValue
                }
            }
        }
    }

    fun defCommonFunctions(m: Ld_TypeDefDsl) = with(m) {
        function("to_text", result = "text") {
            alias("str")
            bodyRaw(Lib_Type_Any.ToText_NoDb)
        }

        function("empty", result = "boolean", pure = true) {
            body { a ->
                val map = a.asMap()
                Rt_BooleanValue.get(map.isEmpty())
            }
        }

        function("size", result = "integer", pure = true) {
            alias("len", C_MessageType.ERROR)
            body { a ->
                val map = a.asMap()
                Rt_IntValue.get(map.size.toLong())
            }
        }

        function("get", result = "V", pure = true) {
            param("key", type = "K")
            body { self, a ->
                val map = self.asMap()
                val v = map[a]
                v ?: throw Rt_Exception.common("fn:map.get:novalue:${a.strCode()}", "Key not in map: ${a.str()}")
            }
        }

        function("get_or_null", result = "V?", pure = true) {
            param("key", type = "K")
            body { self, a ->
                val map = self.asMap()
                val r = map[a]
                r ?: Rt_NullValue
            }
        }

        function("get_or_default", pure = true) {
            generic("R", superOf = "V")
            result(type = "R")
            param("key", type = "K")
            param("default", type = "R", lazy = true)
            body { self, a, b ->
                val map = self.asMap()
                map[a] ?: b.asLazyValue()
            }
        }

        function("contains", result = "boolean", pure = true) {
            param("key", type = "K")
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
): R_Type("map<${keyValueTypes.key.strCode()},${keyValueTypes.value.strCode()}>") {
    constructor(keyType: R_Type, valueType: R_Type): this(R_MapKeyValueTypes(keyType, valueType))

    val keyType = keyValueTypes.key
    val valueType = keyValueTypes.value
    val keySetType = R_SetType(keyType)
    val valueListType = R_ListType(valueType)
    val virtualType = R_VirtualMapType(this)

    val entryType = R_TupleType.create(keyType, valueType)

    val legacyEntryType: R_TupleType by lazy {
        R_TupleType.createNamed("k" to keyType, "v" to valueType)
    }

    private val isError = keyType.isError() || valueType.isError()

    override fun equals0(other: R_Type) = other is R_MapType && keyValueTypes == other.keyValueTypes
    override fun hashCode0() = keyValueTypes.hashCode()

    override fun isReference() = true
    override fun isError() = isError
    override fun isDirectMutable() = true
    override fun isDirectVirtualable() = keyType == R_TextType

    override fun strCode() = name
    override fun componentTypes() = listOf(keyType, valueType)

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
            "value" to valueType.toMetaGtv()
    ).toGtv()
}

class Rt_MapValue(val type: R_MapType, map: MutableMap<Rt_Value, Rt_Value>): Rt_Value() {
    val map: Map<Rt_Value, Rt_Value> = map

    private val mutableMap = map

    override val valueType = Rt_CoreValueTypes.MAP.type()

    override fun type() = type
    override fun asMap() = map
    override fun asMutableMap() = mutableMap
    override fun asMapValue() = this
    override fun toFormatArg() = map
    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, showTupleFieldNames, map)
    override fun str() = map.entries.joinToString(", ", "{", "}") { "${it.key.str()}=${it.value.str()}" }
    override fun equals(other: Any?) = other === this || (other is Rt_MapValue && map == other.map)
    override fun hashCode() = map.hashCode()

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
    override fun directCompatibility() = R_GtvCompatibility(true, true)

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
                    .mapKeys { (k, _) -> Rt_TextValue.get(k) as Rt_Value }
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
