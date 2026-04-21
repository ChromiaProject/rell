/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.module

import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.rell.api.nativ.RellNativeEnvironment
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.QualifiedName
import net.postchain.rell.base.runtime.Rt_NativeFunction
import net.postchain.rell.base.runtime.Rt_NativeFunctionHeader
import net.postchain.rell.base.runtime.Rt_NativeProvider
import net.postchain.rell.base.utils.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmName

internal class PostchainRellNativeEnvironment(
    override val config: Gtv,
    override val blockchainRid: BlockchainRid,
): RellNativeEnvironment

internal object PostchainNativeUtils {
    fun getNativeProvider(env: RellNativeEnvironment, nativeConfig: Gtv?): Rt_NativeProvider {
        val funs = getNativeFuns(env, nativeConfig)
        return PostchainNativeModuleProvider(funs)
    }

    private fun getNativeFuns(env: RellNativeEnvironment, nativeConfig: Gtv?): ImmMap<FullName, Rt_NativeFunction> {
        nativeConfig ?: return immMapOf()
        return nativeConfig.asDict().map {
                val moduleName = ModuleName.of(it.key)
                val className = it.value.asString()
                getNativeModuleFuns(env, moduleName, className)
            }
            .flatMap { it.entries }
            .associateToImmMap { it.key to it.value }
    }

    private fun getNativeModuleFuns(
        env: RellNativeEnvironment,
        moduleName: ModuleName,
        className: String,
    ): ImmMap<FullName, Rt_NativeFunction> {
        val cls = Class.forName(className).kotlin
        val obj = getNativeFunsForClass(env, cls)
        return cls.memberFunctions
            .filter { it.visibility == KVisibility.PUBLIC }
            .associateToImmMap {
                val fullName = FullName(moduleName, QualifiedName.of(it.name))
                fullName to getNativeFun(obj, it)
            }
    }

    private fun getNativeFunsForClass(env: RellNativeEnvironment, cls: KClass<*>): Any {
        val pubCons = cls.constructors.filter { it.visibility == KVisibility.PUBLIC }

        val con1 = pubCons.firstOrNull {
            it.parameters.size == 1 && it.parameters[0].type.classifier == RellNativeEnvironment::class
        }

        if (con1 != null) {
            return con1.call(env)
        }

        val con0 = pubCons.firstOrNull { it.parameters.isEmpty() }
        requireNotNull(con0) { "Class does not have a suitable public constructor: ${cls.jvmName}" }
        return con0.call()
    }

    private fun getNativeFun(obj: Any, fn: KFunction<*>): Rt_NativeFunction {
        val allParams = fn.parameters
        require(allParams.isNotEmpty())

        val thisParam = allParams[0]
        require(thisParam.kind == KParameter.Kind.INSTANCE)
        require(!thisParam.isOptional && !thisParam.isVararg)

        val params = allParams.drop(1)
        val badParam = params.firstOrNull {
            it.isOptional || it.isVararg || it.kind != KParameter.Kind.VALUE
        }
        require(badParam == null) { "Bad function parameter: $badParam" }

        val paramTypes = params.mapToImmList { it.type }
        val header = Rt_NativeFunctionHeader(fn.returnType, paramTypes)
        return ReflectNativeFunction(header, obj, fn)
    }
}

private class ReflectNativeFunction(
    private val header: Rt_NativeFunctionHeader,
    private val self: Any,
    private val fn: KFunction<*>,
): Rt_NativeFunction {
    override fun getHeader() = header

    override fun call(args: ImmList<Any?>): Any? {
        return fn.call(self, *args.toTypedArray())
    }
}

private class PostchainNativeModuleProvider(
    private val funs: ImmMap<FullName, Rt_NativeFunction>,
): Rt_NativeProvider {
    override fun getFunction(name: FullName) = funs[name]
}
