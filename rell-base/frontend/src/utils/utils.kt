/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

class Nullable<T: Any> private constructor(val value: T?) {
    override fun equals(other: Any?) = other === this || (other is Nullable<*> && value == other.value)
    override fun hashCode() = value?.hashCode() ?: 0
    override fun toString(): String = value.toString()

    companion object {
        private val NULL: Nullable<Any> = Nullable(null)

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> of(value: T? = null): Nullable<T> {
            return if (value != null) Nullable(value) else (NULL as Nullable<T>)
        }
    }
}
