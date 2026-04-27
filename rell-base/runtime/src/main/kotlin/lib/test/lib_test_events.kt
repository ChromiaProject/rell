/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

private val EVENT_TUPLE_RR_TYPE: RR_Type = RR_Type.Tuple(
    listOf(
        RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.TEXT)),
        RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.GTV)),
    ).toImmList()
)

/** RR_Type equivalent of `list<(text, gtv)>`. */
private val EVENT_LIST_RR_TYPE: RR_Type = RR_Type.List(EVENT_TUPLE_RR_TYPE)

object Lib_Test_Events {
    val EVENT_TUPLE_TYPE: Rt_ValueClass<*> = Rt_TupleType(
        fields = (EVENT_TUPLE_RR_TYPE as RR_Type.Tuple).fields,
        fieldClasses = immListOf(Rt_PrimitiveTypes.TEXT, Rt_PrimitiveTypes.GTV),
    )

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias(target = "rell.test.assert_events", since = "0.13.0")

        namespace("rell.test") {
            function("assert_events", "unit", since = "0.13.0") {
                comment("""
                    Assert that the given events were emitted during the construction of the last block, in the
                    specified order.

                    Events are emitted in application code using `op_context.emit_event()`.

                    #### Example - single event:
                    ##### Application code
                    ```rell
                    operation main() {
                        op_context.emit_event("my_message_type", "my_data".to_gtv());
                    }
                    ```
                    ##### Test code
                    ```rell
                    function test_main() {
                        main().run();

                        // Assertion passes
                        rell.test.assert_events(("my_message_type", "my_data".to_gtv()));
                    }

                    function test_main_bad_1() {
                        main().run();

                        // Assertion fails - wrong message type
                        rell.test.assert_events(("other_message_type", "my_data".to_gtv()));
                    }

                    function test_main_bad_2() {
                        main().run();

                        // Assertion fails - wrong message content
                        rell.test.assert_events(("my_message_type", "other_data".to_gtv()));
                    }
                    ```

                    #### Example - multiple events:
                    ##### Application code
                    ```rell
                    operation main() {
                        op_context.emit_event("my_message_type", "my_data".to_gtv());
                        op_context.emit_event("other_message_type", "other_data".to_gtv());
                    }
                    ```
                    ##### Test code
                    ```rell
                    function test_main() {
                        main().run();

                        // Assertion passes
                        rell.test.assert_events(
                            ("my_message_type", "my_data".to_gtv()),
                            ("other_message_type", "other_data".to_gtv())
                        );
                    }

                    function test_main_bad() {
                        main().run();

                        // Assertion fails - event order incorrect
                        rell.test.assert_events(
                            ("other_message_type", "other_data".to_gtv()),
                            ("my_message_type", "my_data".to_gtv())
                        );
                    }
                    ```

                    To assert a subset of events, or to assert independently of event order, use
                    `rell.test.get_events()` and make assertions over elements of the returned list.

                    @see 1. <a href="../op_context/emit_event.html"><code>op_context.emit_event()</code> - Rell Standard Library</a>
                    @see 2. <a href="get_events.html"><code>rell.test.get_events()</code> - Rell Standard Library</a>
                """)
                param("expected", "(text,gtv)", arity = L_ParamArity.ZERO_MANY) {
                    comment("expected event sequence")
                }
                bodyContextN { ctx, args ->
                    val events = ctx.exeCtx.emittedEvents
                    val rtType = ctx.exeCtx.appCtx.interpreter.resolveType(EVENT_LIST_RR_TYPE)
                    val actual: Rt_Value = Rt_ListValue(rtType, events.toMutableList())
                    val expected: Rt_Value = Rt_ListValue(rtType, args.toMutableList())
                    Lib_Test_Assert.calcAssertEquals("assert_events", expected, actual)
                }
            }

            function("get_events", "list<(text,gtv)>", since = "0.13.0") {
                comment("""
                    Get all events that were emitted during the construction of the last block.

                    Events are emitted in application code using `op_context.emit_event()`.

                    #### Example:
                    ##### Application code
                    ```rell
                    op_context.emit_event("my_message_type", "my_gtv_data".to_gtv());
                    op_context.emit_event("my_other_message_type", "my_gtv_data".to_gtv());
                    op_context.emit_event("my_another_message_type", "my_gtv_data".to_gtv());
                    ```
                    ##### Test code
                    ```rell
                    // returns: [(my_message_type,"my_gtv_data"), (my_other_message_type,"my_gtv_data"), (my_another_message_type,"my_gtv_data")]
                    return rell.test.get_events();
                    ```

                    @see 1. <a href="../op_context/emit_event.html"><code>op_context.emit_event()</code> - Rell Standard Library</a>
                """)
                bodyContext { ctx ->
                    val events = ctx.exeCtx.emittedEvents
                    val rtType = ctx.exeCtx.appCtx.interpreter.resolveType(EVENT_LIST_RR_TYPE)
                    Rt_ListValue(rtType, events.toMutableList())
                }
            }
        }
    }
}
