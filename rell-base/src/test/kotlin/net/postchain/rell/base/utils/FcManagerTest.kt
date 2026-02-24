/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.utils.futures.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class FcManagerTest {
    @Test fun testFibonacci() {
        chkFibonacci(0, 0)
        chkFibonacci(1, 1)
        chkFibonacci(2, 1)
        chkFibonacci(3, 2)
        chkFibonacci(4, 3)
        chkFibonacci(5, 5)
        chkFibonacci(6, 8)
        chkFibonacci(7, 13)
        chkFibonacci(8, 21)
        chkFibonacci(9, 34)
        chkFibonacci(10, 55)
        chkFibonacci(11, 89)
        chkFibonacci(12, 144)
    }

    private fun chkFibonacci(input: Int, expected: Int) {
        val mgr = FcManager.create()

        fun fibonacci(n: Int): FcFuture<Int> {
            return when (n) {
                0, 1 -> mgr.future().compute { n }
                else -> {
                    val f1 = mgr.future().delegate { fibonacci(n - 2) }
                    val f2 = mgr.future().delegate { fibonacci(n - 1) }
                    mgr.future().after(f1).after(f2).compute { (res1, res2) ->
                        res1 + res2
                    }
                }
            }
        }

        val future = mgr.future().delegate { fibonacci(input) }
        mgr.finish()

        val actual = future.getResult()
        assertEquals(expected, actual)
    }

    @Test fun testCycleTail() {
        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["a"] = mgr.future().name("a").delegate { map.getValue("b") }
        map["b"] = mgr.future().name("b").delegate { map.getValue("c") }
        map["c"] = mgr.future().name("c").delegate { map.getValue("d") }
        map["d"] = mgr.future().name("d").delegate { map.getValue("b") }

        chkErr("cycle:b,c,d,b") {
            mgr.finish()
        }
    }

    @Test fun testCycleOnDemand() {
        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["a"] = mgr.future().name("a").computeOnDemand(true).compute { map.getValue("b").getResult() }
        map["b"] = mgr.future().name("b").computeOnDemand(true).compute { map.getValue("c").getResult() }
        map["c"] = mgr.future().name("c").computeOnDemand(true).compute { map.getValue("d").getResult() }
        map["d"] = mgr.future().name("d").computeOnDemand(true).compute { map.getValue("b").getResult() }

        chkErr("cycle:b,c,d,b") {
            mgr.finish()
        }
    }

    @Test fun testCycleOnDemand2() {
        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["c"] = mgr.future().name("c").delegate { map.getValue("a") }
        map["b"] = mgr.future().name("b").after(map.getValue("c")).compute { it }
        map["a"] = mgr.future().name("a").after(map.getValue("b")).computeOnDemand(true).compute { it }

        val a = map.getValue("a")
        chkErr("cycle:a,b,c,a") {
            a.getResult()
        }
    }

    @Test fun testCycleMixed() {
        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["c"] = mgr.future().name("c").compute { map.getValue("b").getResult() }
        map["b"] = mgr.future().name("b").computeOnDemand(true).delegate { map.getValue("a") }
        map["a"] = mgr.future().name("a").after(map.getValue("c")).compute { it }

        chkErr("cycle:c,b,a,c") {
            mgr.finish()
        }
    }

    @Test fun testCycleMixedComplexExecute() {
        val mgr = FcManager.create()
        initCycleMixedComplex(mgr)
        chkErr("cycle:f,g,b,c,d,e,f") {
            mgr.finish()
        }
    }

    @Test fun testCycleMixedComplexGetResult() {
        val mgr = FcManager.create()
        val map = initCycleMixedComplex(mgr)
        val a = map.getValue("a")
        chkErr("cycle:b,c,d,e,f,g,b") {
            a.getResult()
        }
    }

    private fun initCycleMixedComplex(mgr: FcExecutor): Map<String, FcFuture<Int>> {
        val map = mutableMapOf<String, FcFuture<Int>>()
        map["g"] = mgr.future().name("g").computeOnDemand(true).delegate { map.getValue("b") }
        map["f"] = mgr.future().name("f").computeOnDemand(true).compute { map.getValue("g").getResult() }
        map["e"] = mgr.future().name("e").compute { map.getValue("f").getResult() }
        map["d"] = mgr.future().name("d").after(map.getValue("e")).compute { it }
        map["c"] = mgr.future().name("c").computeOnDemand(true).after(map.getValue("d")).compute { it }
        map["b"] = mgr.future().name("b").computeOnDemand(true).compute { map.getValue("c").getResult() }
        map["a"] = mgr.future().name("a").computeOnDemand(true).compute { map.getValue("b").getResult() }
        return map.toImmMap()
    }

    @Test fun testComputeOnDemand() {
        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["a"] = mgr.future().name("a").compute { 123 }
        map["b"] = mgr.future().name("b").computeOnDemand(true).compute { 456 }
        map["c"] = mgr.future().name("c").compute { map["d"]!!.getResult() }
        map["d"] = mgr.future().name("d").compute { 789 }

        chkErr("no_result:a") {
            map["a"]!!.getResult()
        }

        assertEquals(456, map["b"]!!.getResult())

        chkErr("no_result:d") {
            mgr.execute()
        }
    }

    @Test fun testComputeOnDemandPromise() {
        val mgr = FcManager.create()

        val a = mgr.promise<Int>()
        val b = mgr.future().name("b").computeOnDemand(true).after(a.future()).compute { it }
        val c = mgr.promise<Int>()
        val d = mgr.future().name("d").computeOnDemand(true).after(c.future()).compute { it }

        a.setResult(123)
        assertEquals(123, b.getResult())

        chkErr("on_demand:promise_unset:d") {
            d.getResult()
        }
    }

    @Test fun testExecutionOrder() {
        val list = mutableListOf<String>()

        val mgr = FcManager.create()

        val map = mutableMapOf<String, FcFuture<Int>>()
        map["a"] = mgr.future().name("a").compute {
            list.add("a")
            123
        }
        map["b"] = mgr.future().name("b").delegate {
            list.add("b")
            map["d"]!!
        }
        map["c"] = mgr.future().name("c").after(map["b"]!!).compute {
            list.add("c:$it")
            it + 1
        }
        map["d"] = mgr.future().name("d").compute {
            list.add("d")
            456
        }

        mgr.execute()

        assertEquals("[a, b, d, c:456]", list.toString())
        assertEquals(123, map["a"]!!.getResult())
        assertEquals(456, map["b"]!!.getResult())
        assertEquals(457, map["c"]!!.getResult())
        assertEquals(456, map["d"]!!.getResult())
    }

    @Test fun testExecutionOrderPromise() {
        val list = mutableListOf<String>()

        val mgr = FcManager.create()
        val p = mgr.promise<Int>()

        val a = mgr.future().after(p.future()).compute {
            list.add("a:$it")
            it + 1
        }
        val b = mgr.future().after(a).compute {
            list.add("b:$it")
            it + 1
        }
        val c = mgr.future().compute {
            list.add("c")
            p.setResult(123)
            456
        }
        val d = mgr.future().compute {
            list.add("d")
            789
        }

        mgr.execute()

        assertEquals("[c, d, a:123, b:124]", list.toString())
        assertEquals(124, a.getResult())
        assertEquals(125, b.getResult())
        assertEquals(456, c.getResult())
        assertEquals(789, d.getResult())
    }

    private fun chkErr(exp: String, block: () -> Unit) {
        try {
            block()
            fail("no error, but expected: $exp")
        } catch (e: FcException) {
            assertEquals(exp, e.code)
        }
    }
}
