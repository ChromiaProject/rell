/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.model.KeyIndex
import net.postchain.rell.base.model.rr.RR_Attribute
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_FunctionParam
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.utils.toGtv

internal fun Rt_Interpreter.buildMetaGtv(): Gtv {
    val rrApp = this.rrApp
    fun String.g(): Gtv = GtvFactory.gtv(this)
    fun Boolean.g(): Gtv = GtvFactory.gtv(if (this) 1L else 0L)
    fun Map<String, Gtv>.g(): Gtv = GtvFactory.gtv(this)
    fun List<Gtv>.g(): Gtv = GtvFactory.gtv(this)

    fun typeGtv(t: RR_Type): Gtv = when (t) {
        is RR_Type.Primitive -> t.kind.name.lowercase().g()
        is RR_Type.Null -> "null".g()
        is RR_Type.Entity -> rrApp.allEntities[t.defIndex].base.appLevelName.g()
        is RR_Type.Struct -> {
            val rrStructDef = rrApp.allStructs[t.defIndex]
            val mi = rrStructDef.struct.mirrorInfo
            if (mi != null) {
                mapOf(
                    "type" to "struct".toGtv(),
                    "definition_type" to mi.definitionType.toGtv(),
                    "definition" to mi.definition.toGtv(),
                    "mutable" to mi.mutable.toGtv(),
                ).toGtv()
            } else {
                rrStructDef.base.appLevelName.g()
            }
        }

        is RR_Type.Enum -> rrApp.allEnums[t.defIndex].base.appLevelName.g()
        is RR_Type.Object -> rrApp.allObjects[t.defIndex].base.appLevelName.g()
        is RR_Type.Operation -> rrApp.allOperations[t.defIndex].base.appLevelName.g()
        is RR_Type.Nullable -> mapOf("type" to "nullable".toGtv(), "value" to typeGtv(t.value)).toGtv()
        is RR_Type.List -> mapOf("type" to "list".toGtv(), "value" to typeGtv(t.element)).toGtv()
        is RR_Type.Set -> mapOf("type" to "set".toGtv(), "value" to typeGtv(t.element)).toGtv()
        is RR_Type.Map -> mapOf(
            "type" to "map".toGtv(),
            "key" to typeGtv(t.key),
            "value" to typeGtv(t.value),
        ).toGtv()

        is RR_Type.Tuple -> mapOf(
            "type" to "tuple".toGtv(),
            "fields" to t.fields.map { f ->
                mapOf(
                    "name" to (f.name?.toGtv() ?: GtvNull),
                    "type" to typeGtv(f.type),
                ).toGtv()
            }.toGtv(),
        ).toGtv()

        is RR_Type.Function -> mapOf(
            "type" to "function".toGtv(),
            "params" to t.params.map { typeGtv(it) }.toGtv(),
            "result" to typeGtv(t.result),
        ).toGtv()

        is RR_Type.VirtualList -> mapOf(
            "type" to "virtual".toGtv(),
            "value" to typeGtv(RR_Type.List(t.element)),
        ).toGtv()

        is RR_Type.VirtualSet -> mapOf(
            "type" to "virtual".toGtv(),
            "value" to typeGtv(RR_Type.Set(t.element)),
        ).toGtv()

        is RR_Type.VirtualMap -> mapOf(
            "type" to "virtual".toGtv(),
            "value" to typeGtv(RR_Type.Map(t.key, t.value)),
        ).toGtv()

        is RR_Type.VirtualStruct -> mapOf(
            "type" to "virtual".toGtv(),
            "value" to typeGtv(RR_Type.Struct(t.defIndex)),
        ).toGtv()

        is RR_Type.VirtualTuple -> mapOf(
            "type" to "virtual".toGtv(),
            "value" to typeGtv(RR_Type.Tuple(t.fields)),
        ).toGtv()

        is RR_Type.Generic -> if (t.args.isEmpty()) {
            t.name.g()
        } else {
            t.name.g()
        }

        is RR_Type.Error -> "<error>".g()
    }

    fun paramMeta(p: RR_FunctionParam) = mapOf("name" to p.name.str.g(), "type" to typeGtv(p.type)).g()
    fun attrMeta(a: RR_Attribute) =
        mapOf("name" to a.name.g(), "type" to typeGtv(a.type), "mutable" to a.mutable.g()).g()

    fun keyMeta(k: KeyIndex) = mapOf("attributes" to k.attribs.map { it.str }.map { it.g() }.g()).g()
    fun entityMeta(e: RR_EntityDefinition, full: Boolean): Gtv {
        val m =
            mutableMapOf("mount" to e.mountName.str().g(), "attributes" to e.attributes.values.map { attrMeta(it) }.g())
        if (full) {
            m["log"] = e.flags.log.g(); m["keys"] = e.keys.map { keyMeta(it) }.g(); m["indexes"] =
                e.indexes.map { keyMeta(it) }.g()
        }
        return m.g()
    }

    fun <T> addDefs(m: MutableMap<String, Gtv>, key: String, defs: Map<String, T>, fn: (T) -> Gtv) {
        if (defs.isNotEmpty()) m[key] = defs.keys.sorted().associateWith { fn(defs.getValue(it)) }.g()
    }

    return mapOf(
        "modules" to rrApp.modules.associate { mod ->
            val name = mod.name.str()
            val extChain = mod.externalChain
            val fullName = if (extChain == null) name else "$name[$extChain]"
            val m = mutableMapOf("name" to mod.name.str().g())
            if (mod.abstract) m["abstract"] = true.g()
            if (mod.external) m["external"] = true.g()
            if (extChain != null) m["externalChain"] = extChain.g()
            if (mod.disabled) m["disabled"] = true.g()
            addDefs(m, "entities", mod.entities) { entityMeta(it, true) }
            addDefs(m, "objects", mod.objects) { entityMeta(it.rEntity, false) }
            addDefs(m, "structs", mod.structs) {
                mapOf("attributes" to it.struct.attributesList.map { a -> attrMeta(a) }.g()).g()
            }
            addDefs(m, "enums", mod.enums) {
                mapOf("values" to it.attrs.map { a -> mapOf("name" to a.name.g()).g() }.g()).g()
            }
            addDefs(m, "operations", mod.operations) {
                mapOf(
                    "mount" to it.mountName.str().g(),
                    "parameters" to it.params.map { p -> paramMeta(p) }.g(),
                ).g()
            }
            addDefs(m, "queries", mod.queries) { q ->
                mapOf(
                    "mount" to q.mountName.str().g(),
                    "type" to typeGtv(q.type()),
                    "parameters" to q.params().map { p -> paramMeta(p) }.g(),
                ).g()
            }
            addDefs(m, "functions", mod.functions) { f ->
                mapOf(
                    "type" to typeGtv(f.fnBase.resultType),
                    "parameters" to f.fnBase.params.map { p -> paramMeta(p) }.g(),
                ).g()
            }
            addDefs(m, "constants", mod.constants) { c ->
                val cm = mutableMapOf("type" to typeGtv(c.type))
                c.metaGtvJson?.let { cm["value"] = GtvDecoder.decodeGtv(java.util.Base64.getDecoder().decode(it)) }
                cm.g()
            }
            fullName to m.g()
        }.g(),
    ).g()
}
