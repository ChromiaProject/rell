package com.chromia.rell.dokka.dri

import net.postchain.rell.base.lmodel.L_Constructor
import net.postchain.rell.base.lmodel.L_Function
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.TypeConstructor

fun Callable.Companion.from(f: L_Function) = Callable(
        f.qualifiedName.last.str,
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