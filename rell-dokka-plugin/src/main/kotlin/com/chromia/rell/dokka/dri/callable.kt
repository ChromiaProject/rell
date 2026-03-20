package com.chromia.rell.dokka.dri

import net.postchain.rell.base.lmodel.L_Constructor
import net.postchain.rell.base.lmodel.L_Function
import net.postchain.rell.base.model.R_FunctionParam
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_RoutineDefinition
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.Nullable
import org.jetbrains.dokka.links.TypeConstructor

fun Callable.Companion.from(f: L_Function) = Callable(
        f.fullName.last.str,
        params = f.header.params.map { JavaClassReference(it.type.strCode()) }
)

fun Callable.Companion.from(function: L_Constructor) = Callable(
        name = "constructor",
        params = listOf(
                TypeConstructor(
                        function.header.strCode(),
                        params = function.header.params.map { JavaClassReference(it.type.strCode()) }
                )
        )
)

fun Callable.Companion.from(f: R_RoutineDefinition) = Callable.from(f.simpleName, f.params())

fun Callable.Companion.from(name: String, params: List<R_FunctionParam>) = Callable(
        name = name,
        params = params.map {
            val type = it.type
            when (type) { // TODO: generalize
                is R_NullableType -> Nullable(JavaClassReference(type.valueType.strCode()))
                else -> TypeConstructor(it.name.str, listOf(JavaClassReference(type.strCode())))
            }
        }
)