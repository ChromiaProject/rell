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

object IsEntity: ExtraProperty<DClasslike>, ExtraProperty.Key<DClasslike, IsEntity> {
    override val key: ExtraProperty.Key<DClasslike, *> get() = this
}

fun DClasslike.isEntity() = this is DClass && this.extra[IsEntity] != null

object IsStruct: ExtraProperty<DClasslike>, ExtraProperty.Key<DClasslike, IsStruct> {
    override val key: ExtraProperty.Key<DClasslike, *> get() = this
}

fun DClasslike.isStruct() = this is DClass && this.extra[IsStruct] != null

object IsType: ExtraProperty<DClasslike>, ExtraProperty.Key<DClasslike, IsType> {
    override val key: ExtraProperty.Key<DClasslike, *> get() = this
}

fun DClasslike.isType() = this is DClass && this.extra[IsType] != null

object IsObject: ExtraProperty<DClasslike>, ExtraProperty.Key<DClasslike, IsObject> {
    override val key: ExtraProperty.Key<DClasslike, *> get() = this
}

fun DClasslike.isObject() = this is DClass && this.extra[IsObject] != null

object IsFunction: ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsFunction> {
    override val key: ExtraProperty.Key<DFunction, *> get() = this
}

fun DFunction.isFunction() = this.extra[IsFunction] != null

object IsExtendable: ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsExtendable> {
    override val key: ExtraProperty.Key<DFunction, *> get() = this
}

fun DFunction.isExtendable() = this.extra[IsExtendable] != null

object IsOperation: ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsOperation> {
    override val key: ExtraProperty.Key<DFunction, *> get() = this
}

fun DFunction.isOperation() = this.extra[IsOperation] != null
object IsQuery: ExtraProperty<DFunction>, ExtraProperty.Key<DFunction, IsQuery> {
    override val key: ExtraProperty.Key<DFunction, *> get() = this
}

fun DFunction.isQuery() = this.extra[IsQuery] != null
