package com.chromia.rell.dokka.model

import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.properties.ExtraProperty

object IsStatic : ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsStatic> {
    override val key: ExtraProperty.Key<DFunction, *> = this
}

object IsPure : ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsPure> {
    override val key: ExtraProperty.Key<DFunction, *> = this
}

object IsAlias : ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsAlias> {
    override val key: ExtraProperty.Key<DFunction, *> = this
}

// ONE_MANY and ZERO_MANY arity
object IsVararg : ExtraProperty<DParameter>, ExtraProperty.Key<DParameter, IsVararg> {
    override val key: ExtraProperty.Key<DParameter, *> = this
}

object IsZeroOne: ExtraProperty<DParameter>, ExtraProperty.Key<DParameter, IsZeroOne> {
    override val key: ExtraProperty.Key<DParameter, *> = this
}

object IsTuple: ExtraProperty<GenericTypeConstructor>, ExtraProperty.Key<GenericTypeConstructor, IsTuple> {
    override val key: ExtraProperty.Key<GenericTypeConstructor, *> = this
}