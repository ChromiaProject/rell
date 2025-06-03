/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class AtExprInExistsComplexTest: BaseRellTest(useSql = true) {
    fun initData() {
        tst.strictToString = false

        val trips = listOf(
                "London Brussels",
                "London Paris",
                "Paris Zurich",
                "Brussels Zurich",
                "Brussels Frankfurt",
                "Frankfurt Zurich",
                "Frankfurt Munich",
                "Munich Zurich",
                "Munich Venice",
                "Venice Rome",
                "Paris Madrid"
        )

        val users = listOf(
                "Bob London Zurich",
                "Alice Frankfurt Munich",
                "Trudy Munich London",
                "John Madrid Rome",
                "Mary Venice Madrid"
        )

        def("entity user { name; city1: text; city2: text; }")
        def("entity trip { city1: text; city2: text; }")

        trips.withIndex().forEach { (i, s) ->
            val parts = s.split(" ")
            insert("c0.trip", "city1,city2", "${100+i*2+0},'${parts[0]}','${parts[1]}'")
            insert("c0.trip", "city1,city2", "${100+i*2+1},'${parts[1]}','${parts[0]}'")
        }

        users.withIndex().forEach { (i, s) ->
            val parts = s.split(" ")
            insert("c0.user", "name,city1,city2", "${200+i},'${parts[0]}','${parts[1]}','${parts[2]}'")
        }
    }

    @Test fun testData() {
        initData()

        val trips = listOf(
                "(Bob,London,Zurich)",
                "(Alice,Frankfurt,Munich)",
                "(Trudy,Munich,London)",
                "(John,Madrid,Rome)",
                "(Mary,Venice,Madrid)"
        )
        chkList(trips, "user @* {} ( _=.name, _=.city1, _=.city2 )")

        chk("trip @* {} ( @group .city1 )", "[Brussels, Frankfurt, London, Madrid, Munich, Paris, Rome, Venice, Zurich]")
        chk("trip @* {} ( @group .city2 )", "[Brussels, Frankfurt, London, Madrid, Munich, Paris, Rome, Venice, Zurich]")
    }

    @Test fun testEndpoints() {
        initData()
        chk("user @* { .city1 in trip @*{} (trip.city1) } (.name)", "[Bob, Alice, Trudy, John, Mary]")
        chk("user @* { .city2 in trip @*{} (trip.city2) } (.name)", "[Bob, Alice, Trudy, John, Mary]")
        chk("user @* { .city1 in trip @*{} (trip.city1), .city2 in trip @*{} (trip.city2) } (.name)",
                "[Bob, Alice, Trudy, John, Mary]")
    }

    @Test fun testDirectTripProduct() {
        initData()

        chkList(listOf("(Alice,Frankfurt,Munich)"), """
            (user, trip) @* {
                trip.city1 == user.city1, trip.city2 == user.city2
            }
            ( _=user.name, _=trip.city1, _=trip.city2 )
        """)
    }

    @Test fun testDirectTripIn() {
        initData()

        chkList(listOf("Alice"), """
            user @* {
                user.city1 in trip @* {
                    trip.city2 == user.city2
                } (trip.city1)
            } (.name)
        """)
    }

    @Test fun testDirectTripExists() {
        initData()

        chkList(listOf("Alice"), """
            user @* {
                exists(
                    trip @* { user.city1 == trip.city1, trip.city2 == user.city2 }
                )
            } (.name)
        """)
    }

    @Test fun testOneStopProduct() {
        initData()

        val expected = listOf(
                "(Bob,London,Brussels,Brussels,Zurich)",
                "(Bob,London,Paris,Paris,Zurich)",
                "(Alice,Frankfurt,Zurich,Zurich,Munich)"
        )

        chkList(expected, """
            (user, t1: trip, t2: trip) @* {
                user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == user.city2
            } (_=.name, _=t1.city1, _=t1.city2, _=t2.city1, _=t2.city2)
        """)
    }

    @Test fun testOneStopIn() {
        initData()

        chkList(listOf("Bob", "Alice"), """
            user @* {
                user.city1 in (t1: trip) @* {
                    t1.city2 in (t2: trip) @* {
                        t2.city2 == user.city2
                    } (t2.city1)
                } (t1.city1)
            } (.name)
        """)
    }

    @Test fun testOneStopExists() {
        initData()

        chkList(listOf("Bob", "Alice"), """
            user @* {
                exists(
                    (t1: trip, t2: trip) @* {
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == user.city2
                    }
                )
            } (.name)
        """)
    }

    @Test fun testOneStopExistsMixed() {
        initData()

        chkList(listOf("Bob", "Alice"), """
            user @* {
                exists((t1: trip) @* {
                    exists((t2: trip) @* {
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == user.city2
                    })
                })
            } (.name)
        """)

        chkList(listOf("Bob", "Alice"), """
            user @* {
                exists((t1: trip) @* {
                    user.city1 == t1.city1,
                    exists((t2: trip) @* {
                        t1.city2 == t2.city1, t2.city2 == user.city2
                    })
                })
            } (.name)
        """)

        chkList(listOf("Bob", "Alice"), """
            user @* {
                exists((t1: trip) @* {
                    user.city1 == t1.city1,
                    t1.city2 in (t2: trip) @* {
                        t2.city2 == user.city2
                    } (t2.city1)
                })
            } (.name)
        """)
    }

    @Test fun testTwoStopsProduct() {
        initData()

        val expected = listOf(
                "(Bob,London,Brussels,Brussels,Frankfurt,Frankfurt,Zurich)",
                "(Alice,Frankfurt,Brussels,Brussels,Zurich,Zurich,Munich)",
                "(Trudy,Munich,Frankfurt,Frankfurt,Brussels,Brussels,London)",
                "(Trudy,Munich,Zurich,Zurich,Paris,Paris,London)",
                "(Trudy,Munich,Zurich,Zurich,Brussels,Brussels,London)"
        )

        chkList(expected, """
            (user, t1: trip, t2: trip, t3: trip) @* {
                t2.city2 != t1.city1,
                t3.city2 != t2.city1,
                user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == t3.city1, t3.city2 == user.city2
            } (_=.name, _=t1.city1, _=t1.city2, _=t2.city1, _=t2.city2, _=t3.city1, _=t3.city2)
        """)
    }

    @Test fun testTwoStopsIn() {
        initData()

        chkList(listOf("Bob", "Alice", "Trudy"), """
            user @* {
                user.city1 in (t1: trip) @* {
                    t1.city2 in (t2: trip) @* {
                        t2.city2 != t1.city1,
                        t2.city2 in (t3: trip) @* {
                            t3.city2 != t2.city1,
                            t3.city2 == user.city2
                        } (t3.city1)
                    } (t2.city1)
                } (t1.city1)
            } (.name)
        """)
    }

    @Test fun testTwoStopsExists() {
        initData()

        chkList(listOf("Bob", "Alice", "Trudy"), """
            user @* {
                exists(
                    (t1: trip, t2: trip, t3: trip) @* {
                        t2.city2 != t1.city1,
                        t3.city2 != t2.city1,
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == t3.city1, t3.city2 == user.city2
                    }
                )
            } (.name)
        """)
    }

    @Test fun testTwoStopsExistsMixed() {
        initData()

        chkList(listOf("Bob", "Alice", "Trudy"), """
            user @* {
                exists((t1: trip) @* {
                    exists((t2: trip) @* {
                        exists((t3: trip) @* {
                            t2.city2 != t1.city1,
                            t3.city2 != t2.city1,
                            user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == t3.city1, t3.city2 == user.city2
                        })
                    })
                })
            } (.name)
        """)

        chkList(listOf("Bob", "Alice", "Trudy"), """
            user @* {
                exists((t1: trip) @* {
                    user.city1 == t1.city1,
                    exists((t2: trip) @* {
                        t2.city2 != t1.city1,
                        t1.city2 == t2.city1,
                        exists((t3: trip) @* {
                            t3.city2 != t2.city1,
                            t2.city2 == t3.city1, t3.city2 == user.city2
                        })
                    })
                })
            } (.name)
        """)

        chkList(listOf("Bob", "Alice", "Trudy"), """
            user @* {
                exists((t1: trip) @* {
                    user.city1 == t1.city1,
                    t1.city2 in (t2: trip) @* {
                        t2.city2 != t1.city1,
                        exists((t3: trip) @* {
                            t3.city2 != t2.city1,
                            t3.city1 == t2.city2,
                            t3.city2 == user.city2
                        })
                    } (t2.city1)
                })
            } (.name)
        """)
    }

    @Test fun testShortestDistanceProduct() {
        initData()

        val expected = listOf(
                "(Alice,1)",
                "(Bob,2)",
                "(John,5)",
                "(Mary,4)",
                "(Trudy,3)"
        )

        chkList(expected, """
            (user, t1: trip, t2: trip, t3: trip, t4: trip, t5: trip) @* {
                user.city1 == t1.city1,
                t1.city2 == user.city2 or t1.city2 == t2.city1 and (
                    t2.city2 == user.city2 or t2.city2 == t3.city1 and (
                        t3.city2 == user.city2 or t3.city2 == t4.city1 and (
                            t4.city2 == user.city2 or t4.city2 == t5.city1 and (
                                t5.city2 == user.city2
                            )
                        )
                    )
                )
            } (
                @group _=.name,
                @min when {
                    t1.city2 == user.city2 -> 1;
                    t2.city2 == user.city2 -> 2;
                    t3.city2 == user.city2 -> 3;
                    t4.city2 == user.city2 -> 4;
                    t5.city2 == user.city2 -> 5;
                    else -> 999
                }
            )
        """)
    }

    @Test fun testShortestDistanceExists() {
        initData()

        val expected = listOf(
                "(Alice,1)",
                "(Bob,2)",
                "(John,999)",
                "(Mary,4)",
                "(Trudy,3)"
        )

        chkList(expected, """
            user @* {} (
                @sort _=user.name,
                when {
                    exists((t1: trip) @* {
                        user.city1 == t1.city1, t1.city2 == user.city2
                    }) -> 1;
                    exists((t1: trip, t2: trip) @* {
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == user.city2
                    }) -> 2;
                    exists((t1: trip, t2: trip, t3: trip) @* {
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == t3.city1, t3.city2 == user.city2
                    }) -> 3;
                    exists((t1: trip, t2: trip, t3: trip, t4: trip) @* {
                        user.city1 == t1.city1, t1.city2 == t2.city1, t2.city2 == t3.city1, t3.city2 == t4.city1, t4.city2 == user.city2
                    }) -> 4;
                    else -> 999
                }
            )
        """)
    }

    private fun chkList(expected: List<String>, expr: String) {
        val expected2 = expected.joinToString(prefix = "[", postfix = "]")
        chk(expr, expected2)
    }
}
