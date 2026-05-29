/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.values

import com.oracle.truffle.api.staticobject.DefaultStaticProperty
import com.oracle.truffle.api.staticobject.StaticProperty
import com.oracle.truffle.api.staticobject.StaticShape
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.Rt_ValueClass
import net.postchain.rell.base.runtime.truffle.Tf_Language
import net.postchain.rell.base.runtime.truffle.Tf_PolyglotBootstrap
import net.postchain.rell.base.runtime.truffle.values.Tf_StructShapeRegistry.shapeFor
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Per-attribute slot kind hint. Encoded as a `byte` array on [Tf_StructShape] so PE folds the
 * branch — `kindAt(i)` is a single `aaload` against a `@CompilationFinal(dimensions = 1)` array.
 *
 * `OBJECT` covers everything that isn't a non-nullable Rell `integer` or `boolean`: text,
 * decimal, big_integer, byte_array, gtv, list/set/map, struct/entity/tuple values, and (critically)
 * any `integer?` / `boolean?` attribute — null is not representable in a `long` / `boolean` slot.
 */
internal const val TF_SLOT_OBJECT: Byte = 0
internal const val TF_SLOT_LONG: Byte = 1
internal const val TF_SLOT_BOOLEAN: Byte = 2

/**
 * Per-Rell-struct-type SOM shape, holding the [StaticShape] (which owns the generated final
 * subclass of [Tf_DynStruct]), the per-attribute [StaticProperty] handles indexed by attribute
 * position, the per-attribute slot-kind tag (one of [TF_SLOT_OBJECT] / [TF_SLOT_LONG] /
 * [TF_SLOT_BOOLEAN]) used by readers/writers to dispatch primitive vs object access, and the
 * cached attribute name list used by [Tf_DynStruct.effectiveNamesOrNull].
 */
class Tf_StructShape internal constructor(
    val rrType: RR_Type.Struct,
    private val staticShape: StaticShape<Tf_StructFactory>,
    val properties: Array<StaticProperty>,
    /**
     * Per-attribute slot kind. Same length and ordering as [properties]. Pinned with
     * `@CompilationFinal(dimensions = 1)` at every consumer so PE can specialise on the value.
     */
    internal val slotKinds: ByteArray,
    val attrNames: List<String>,
    internal val sizeConstraints: Array<RR_SizeConstraint?>,
    val factory: Tf_StructFactory,
) {
    /** Materialise a fresh struct instance bound to this shape. */
    fun newInstance(type: Rt_ValueClass<*>): Tf_DynStruct =
        factory.create(rrType, type, this)

    /** O(1) tag lookup. */
    internal fun slotKindAt(index: Int): Byte = slotKinds[index]
}

/**
 * SOM factory interface. SOM generates a final subclass of [Tf_DynStruct] whose constructor
 * matches this signature; [create] dispatches to that constructor. Method parameter types must
 * match [Tf_DynStruct]'s primary-constructor parameter types exactly (SOM matches by signature).
 */
fun interface Tf_StructFactory {
    fun create(rrType: RR_Type.Struct, type: Rt_ValueClass<*>, shape: Tf_StructShape): Tf_DynStruct
}

/**
 * Process-wide cache of per-Rell-struct-type SOM shapes. Builds shapes lazily on first request;
 * subsequent requests are O(1). Keyed by [RR_StructDefinition] *instance identity* — each
 * `RR_App.resolve()` returns a fresh definition object per struct, so the key isolates shapes
 * across unrelated apps even when their internal `defIndex` values collide. (A naive cache by
 * `(defIndex,)` would silently hand back a shape built for a different app's struct, which
 * manifests downstream as `ArrayIndexOutOfBoundsException` on attribute writes when the cached
 * shape's slot count doesn't match.)
 *
 * Shape construction enumerates the struct's attributes from [RR_App.allStructs]. Attribute slot
 * type is chosen from the static [RR_Type]: non-nullable `integer` → primitive `long` slot,
 * non-nullable `boolean` → primitive `boolean` slot, anything else → `Object` slot. Primitive
 * slots eliminate the [net.postchain.rell.base.runtime.Rt_IntValue] / `Rt_BooleanValue` wrapper
 * on attribute reads and writes; this is the dominant cost on struct-heavy hot paths
 * (`bench_locations` shows `Tf_MemberAccessNode$StructAttr$IntAttr.executeLong` at ~5% of
 * runtime alone). Nullable-typed `integer?` / `boolean?` attributes stay on `Object` slots since
 * `null` is not representable in a primitive.
 *
 * Returns `null` from [shapeFor] when SOM is unavailable in the current runtime — typically a
 * packaging issue where the polyglot SDK or the [Tf_Language] service registration is missing.
 * Callers fall back to [net.postchain.rell.base.runtime.Rt_HeapStruct] construction in that case.
 * The unavailability flag is sticky once construction fails: subsequent calls return `null` without
 * retrying.
 */
object Tf_StructShapeRegistry {
    private val LOG = Logger.getLogger(Tf_StructShapeRegistry::class.java.name)

    @Volatile private var somAvailable: Boolean = true

    /**
     * Identity-keyed cache. Synchronised wrap of `IdentityHashMap` rather than
     * `ConcurrentHashMap` because the JDK lacks an identity-keyed concurrent map; the cache is
     * write-once-per-key (no read concurrency hot path) and `Tf_StructCreateNode` already
     * resolves shapes once at translate time, so synchronisation cost is negligible.
     */
    private val cache: MutableMap<RR_StructDefinition, Tf_StructShape> =
        Collections.synchronizedMap(IdentityHashMap())

    fun shapeFor(rrApp: RR_App, rrType: RR_Type.Struct): Tf_StructShape? {
        if (!somAvailable) return null
        val language = Tf_PolyglotBootstrap.ensure()
        if (language == null) {
            somAvailable = false
            return null
        }
        val structDef = rrApp.allStructs[rrType.defIndex]
        return try {
            synchronized(cache) {
                cache.getOrPut(structDef) { build(language, rrType, structDef) }
            }
        } catch (e: Exception) {
            // Disable SOM permanently for the JVM lifetime once a shape build fails — a misconfigured
            // polyglot/SOM is unlikely to recover. Log once with the cause so the fall-through to
            // Rt_HeapStruct is diagnosable; without this, callers see only a silent fallback.
            somAvailable = false
            LOG.log(Level.WARNING, "SOM shape build failed for ${structDef.struct.name}; disabling SOM struct path", e)
            null
        }
    }

    private fun build(
        language: Tf_Language,
        rrType: RR_Type.Struct,
        structDef: RR_StructDefinition,
    ): Tf_StructShape {
        val attrs = structDef.struct.attributesList
        val builder = StaticShape.newBuilder(language)
        val slotKinds = ByteArray(attrs.size)
        val properties = Array<StaticProperty>(attrs.size) { i ->
            val prop = DefaultStaticProperty("attr_$i")
            val (slotType, kind) = slotTypeFor(attrs[i].type)
            slotKinds[i] = kind
            builder.property(prop, slotType, false)
            prop
        }
        val staticShape = builder.build(Tf_DynStruct::class.java, Tf_StructFactory::class.java)
        val attrNames = attrs.map { it.name }
        val sizeConstraints = Array(attrs.size) { i -> attrs[i].sizeConstraint }
        return Tf_StructShape(
            rrType,
            staticShape,
            properties,
            slotKinds,
            attrNames,
            sizeConstraints,
            staticShape.factory,
        )
    }

    /**
     * Pick the SOM slot type for a given attribute static [RR_Type]. Only non-nullable primitive
     * `integer` / `boolean` get specialised slots — `integer?` / `boolean?` keep `Object` slots so
     * that [net.postchain.rell.base.runtime.Rt_NullValue] can sit in the same slot as a wrapped
     * primitive.
     */
    private fun slotTypeFor(type: RR_Type): Pair<Class<*>, Byte> = when (type) {
        is RR_Type.Primitive if type.kind == RR_PrimitiveKind.INTEGER ->
            java.lang.Long.TYPE to TF_SLOT_LONG

        is RR_Type.Primitive if type.kind == RR_PrimitiveKind.BOOLEAN ->
            java.lang.Boolean.TYPE to TF_SLOT_BOOLEAN

        else -> Any::class.java to TF_SLOT_OBJECT
    }
}
