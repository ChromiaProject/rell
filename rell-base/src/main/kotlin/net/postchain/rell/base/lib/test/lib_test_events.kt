/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.rell.base.lib.type.R_GtvType
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_Value

private val EVENT_LIST_TYPE: R_Type = R_ListType(Lib_Test_Events.EVENT_TYPE)

object Lib_Test_Events {
    val EVENT_TUPLE_TYPE = R_TupleType.create(R_TextType, R_GtvType)
    val EVENT_TYPE: R_Type = EVENT_TUPLE_TYPE

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias(target = "rell.test.assert_events", since = "0.13.0")

        namespace("rell.test") {
            function("assert_events", "unit", since = "0.13.0") {
                comment("Asserts that the expected events has been emitted during last block")
                param("expected", "(text,gtv)", arity = L_ParamArity.ZERO_MANY) {
                    comment("Events that are expected to be emitted")
                }
                bodyContextN { ctx, args ->
                    val events = ctx.exeCtx.emittedEvents
                    val actual: Rt_Value = Rt_ListValue(EVENT_LIST_TYPE, events.toMutableList())
                    val expected: Rt_Value = Rt_ListValue(EVENT_LIST_TYPE, args.toMutableList())
                    Lib_Test_Assert.calcAssertEquals("assert_events", expected, actual)
                }
            }

            function("get_events", "list<(text,gtv)>", since = "0.13.0") {
                comment("Get all events that have been emitted from the last block.")
                bodyContext { ctx ->
                    val events = ctx.exeCtx.emittedEvents
                    Rt_ListValue(EVENT_LIST_TYPE, events.toMutableList())
                }
            }
        }
    }
}
