package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import kotlin.reflect.KClass

/**
 * TODO: This object is created as a temporary fix as postchain 3.7 has introduced [GtvObjectMapper.toGtvArray].
 * When postchain version is increased, remove this object
 */
@Suppress("unused", "UNUSED_PARAMETER")
object GtvObjectMapper {
    fun <T : Any> fromArray(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()): List<T> =
        throw NotImplementedError("dummy")

    fun <T : Any> fromArray(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()): List<T> =
        throw NotImplementedError("dummy")

    fun <T : Any> fromGtv(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()): T =
        throw NotImplementedError("dummy")

    fun <T : Any> fromGtv(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()): T =
        throw NotImplementedError("dummy")

    fun <T: Any> toGtvArray(obj: T): GtvArray =
        throw NotImplementedError("dummy")

    fun <T: Any> toGtvDictionary(obj: T): GtvDictionary =
        throw NotImplementedError("dummy")
}
