/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.test.R_TestFailureType

internal class Rt_TestFailureValue(val message: String): Rt_Value {
    val messageValue = Rt_TextValue.get(message)

    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun str(format: Rt_StrFormat): String = message
    override fun strCode(showTupleFieldNames: Boolean) = "${R_TestFailureType.name}[$message]"

    companion object: Rt_ValueClass<Rt_TestFailureValue> {
        override val name
            get() = "rell.test.failure"

        override val klass = Rt_TestFailureValue::class
    }
}
