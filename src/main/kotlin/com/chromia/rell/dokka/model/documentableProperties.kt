package com.chromia.rell.dokka.model

import org.jetbrains.dokka.model.DFunction
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
