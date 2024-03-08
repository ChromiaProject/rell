package com.chromia.rell.dokka.model

import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.IsVar
import org.jetbrains.dokka.model.properties.ExtraProperty

object IsStatic : ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsStatic> {
    override val key: ExtraProperty.Key<DFunction, *> = this
}
fun DFunction.isStatic() = extra[IsStatic] != null

object IsPure : ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsPure> {
    override val key: ExtraProperty.Key<DFunction, *> = this
}
fun DFunction.isPure() = extra[IsPure] != null

// ONE_MANY and ZERO_MANY arity
object IsVararg : ExtraProperty<DParameter>, ExtraProperty.Key<DParameter, IsVararg> {
    override val key: ExtraProperty.Key<DParameter, *> = this
}
fun DParameter.isVararg() = extra[IsVararg] != null

object IsZeroOne: ExtraProperty<DParameter>, ExtraProperty.Key<DParameter, IsZeroOne> {
    override val key: ExtraProperty.Key<DParameter, *> = this
}
fun DParameter.isZeroOne() = extra[IsZeroOne] != null


object IsTuple: ExtraProperty<GenericTypeConstructor>, ExtraProperty.Key<GenericTypeConstructor, IsTuple> {
    override val key: ExtraProperty.Key<GenericTypeConstructor, *> = this
}
fun GenericTypeConstructor.isTuple() = extra[IsTuple] != null

object IsHidden: ExtraProperty<DClass>, ExtraProperty.Key<DClass, IsHidden> {
    override val key: ExtraProperty.Key<DClass, *> = this
}
fun DClasslike.isHidden() = this is DClass && extra[IsHidden] != null

fun DProperty.isMutable(): Boolean {
    return this.extra[IsVar] != null || this.setter != null
}

object IsKey: ExtraProperty<DProperty>, ExtraProperty.Key<DProperty, IsKey> {
    override val key: ExtraProperty.Key<DProperty, *> get() = this
}

fun DProperty.isKey() = this.extra[IsKey] != null

object IsIndex: ExtraProperty<DProperty>, ExtraProperty.Key<DProperty, IsIndex> {
    override val key: ExtraProperty.Key<DProperty, *> get() = this
}

fun DProperty.isIndex() = this.extra[IsIndex] != null

