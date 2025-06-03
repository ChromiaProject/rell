/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx.lib

import net.postchain.rell.api.gtx.testutils.PostchainRellTestProjExt
import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibRellTestBlockClockTest: BaseRellTest() {
    override fun getProjExt() = PostchainRellTestProjExt

    init {
        tst.testLib = true
    }

    @Test fun testConstants() {
        chk("rell.test.DEFAULT_FIRST_BLOCK_TIME", "int[1577836800000]")
        chk("rell.test.DEFAULT_BLOCK_INTERVAL", "int[10000]")
    }

    @Test fun testLastBlockTime() {
        init()

        repl.chk("rell.test.last_block_time", "rt_err:no_last_block_time")
        repl.chk("rell.test.last_block_time_or_null", "RES:null")

        repl.chk("op(0).run();", "RES:unit")
        chkLastBlockTime(1577836800000)

        repl.chk("op(1).run();", "RES:unit")
        chkLastBlockTime(1577836810000)

        repl.chk("op(0).run();", "rt_err:block_runner:tx_duplicate")
        chkLastBlockTime(1577836810000)

        repl.chk("op(2).run();", "RES:unit")
        chkLastBlockTime(1577836820000)
    }

    @Test fun testNextBlockTime() {
        init()

        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        repl.chk("rell.test.block_interval", "RES:int[10000]")
        repl.chk("rell.test.set_block_interval(123)", "RES:int[10000]")
        repl.chk("rell.test.block_interval", "RES:int[123]")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        chkTx(1577836800000)

        repl.chk("rell.test.next_block_time", "RES:int[1577836800123]")
        repl.chk("rell.test.set_block_interval(500)", "RES:int[123]")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800500]")
        chkTx(1577836800500)

        repl.chk("rell.test.next_block_time", "RES:int[1577836801000]")
        repl.chk("rell.test.set_block_interval(123)", "RES:int[500]")
        chkTx(1577836800623)
    }

    @Test fun testNextBlockTimeOverflow() {
        init()

        repl.chk("rell.test.set_block_interval(9223372036854775000)", "RES:int[10000]")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        chkTx(1577836800000)

        repl.chk("rell.test.next_block_time", "rt_err:time_overflow:1577836800000:9223372036854775000")
        repl.chk("rell.test.set_block_interval(250)", "RES:int[9223372036854775000]")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800250]")
        chkTx(1577836800250)
    }

    @Test fun testBlockInterval() {
        init()

        chkTx(1577836800000)

        repl.chk("rell.test.block_interval", "RES:int[10000]")
        repl.chk("rell.test.set_block_interval(1)", "RES:int[10000]")
        chkTx(1577836800001)
        chkTx(1577836800002)

        repl.chk("rell.test.set_block_interval(998422163199998)", "RES:int[1]")
        chkTx(1000000000000000)

        repl.chk("rell.test.set_block_interval(0)", "rt_err:block_interval:non_positive:0")
        repl.chk("rell.test.block_interval", "RES:int[998422163199998]")
        repl.chk("rell.test.set_block_interval(-1)", "rt_err:block_interval:non_positive:-1")
        repl.chk("rell.test.block_interval", "RES:int[998422163199998]")
        repl.chk("rell.test.set_block_interval(-100000000000)", "rt_err:block_interval:non_positive:-100000000000")
        repl.chk("rell.test.block_interval", "RES:int[998422163199998]")
        repl.chk("rell.test.set_block_interval(1)", "RES:int[998422163199998]")
        repl.chk("rell.test.block_interval", "RES:int[1]")
        chkTx(1000000000000001)
    }

    @Test fun testSetNextBlockTime() {
        init()

        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        repl.chk("rell.test.set_next_block_time(1234500000000);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1234500000000]")

        chkTx(1234500000000)
        chkTx(1234500010000)

        repl.chk("rell.test.next_block_time", "RES:int[1234500020000]")
        repl.chk("rell.test.set_next_block_time(1234500000000);", "rt_err:block_time:too_old:1234500010000:1234500000000")
        repl.chk("rell.test.next_block_time", "RES:int[1234500020000]")
        repl.chk("rell.test.set_next_block_time(1234500010000);", "rt_err:block_time:too_old:1234500010000:1234500010000")
        repl.chk("rell.test.next_block_time", "RES:int[1234500020000]")
        repl.chk("rell.test.set_next_block_time(1234500010001);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1234500010001]")

        chkTx(1234500010001)
        repl.chk("rell.test.next_block_time", "RES:int[1234500020001]")

        repl.chk("rell.test.set_next_block_time(9223372036854775807);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[9223372036854775807]")
        chkTx(9223372036854775807)

        repl.chk("rell.test.next_block_time", "rt_err:time_overflow:9223372036854775807:10000")
        chkTx(9223372036854775807, "rt_err:time_overflow:9223372036854775807:10000")
    }

    @Test fun testSetNextBlockTimeZero() {
        init()
        repl.chk("rell.test.set_next_block_time(0);", "RES:unit")
        repl.chk("rell.test.set_block_interval(1);", "RES:int[10000]")
        chkTx(0)
        chkTx(1)
        chkTx(2)
    }

    @Test fun testSetNextBlockTimeDelta() {
        init()
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")

        repl.chk("rell.test.set_next_block_time_delta(0);", "rt_err:block_time_delta:non_positive:0")
        repl.chk("rell.test.set_next_block_time_delta(-1);", "rt_err:block_time_delta:non_positive:-1")
        repl.chk("rell.test.set_next_block_time_delta(-123456789);", "rt_err:block_time_delta:non_positive:-123456789")

        repl.chk("rell.test.set_next_block_time_delta(1);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        repl.chk("rell.test.set_next_block_time_delta(123);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        repl.chk("rell.test.set_next_block_time_delta(123456789);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        chkTx(1577836800000)

        repl.chk("rell.test.next_block_time", "RES:int[1577836810000]")
        repl.chk("rell.test.set_next_block_time_delta(1);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800001]")
        repl.chk("rell.test.set_next_block_time_delta(123);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800123]")
        chkTx(1577836800123)

        repl.chk("rell.test.next_block_time", "RES:int[1577836810123]")
        repl.chk("rell.test.set_next_block_time_delta(456);", "RES:unit")
        repl.chk("rell.test.next_block_time", "RES:int[1577836800579]")
        chkTx(1577836800579)

        repl.chk("rell.test.next_block_time", "RES:int[1577836810579]")
        repl.chk("rell.test.set_next_block_time_delta(9223372036854775000);",
            "rt_err:time_overflow:1577836800579:9223372036854775000")
        repl.chk("rell.test.next_block_time", "RES:int[1577836810579]")
        chkTx(1577836810579)
    }

    @Test fun testGetLastBlockTimeInOperation() {
        file("module.rell", "operation op(x: integer) { print(op_context.last_block_time); }")
        LibRellTestTxTest.initTxChain(tstCtx, tst)

        repl.chk("rell.test.next_block_time", "RES:int[1577836800000]")
        repl.chk("op(0).run();", "OUT:-1", "RES:unit")
        repl.chk("op(1).run();", "OUT:1577836800000", "RES:unit")
        repl.chk("op(2).run();", "OUT:1577836810000", "RES:unit")

        repl.chk("rell.test.set_block_interval(3);", "RES:int[10000]")
        repl.chk("op(3).run();", "OUT:1577836820000", "RES:unit")
        repl.chk("op(4).run();", "OUT:1577836820003", "RES:unit")
        repl.chk("op(5).run();", "OUT:1577836820006", "RES:unit")
    }

    @Test fun testFullScale() {
        tstCtx.useSql = true

        file("app.rell", "module; operation op(x: integer) {}")

        file("tests.rell", """
            @test module;
            import app;

            function test_1() = run_blocks(1);
            function test_2() = run_blocks(123);
            function test_3() = run_blocks(10000);
            function test_4() = run_blocks(100500);

            function run_blocks(step: integer) {
                rell.test.set_block_interval(step);
                run_block(step, 0);
                run_block(step, 1);
                run_block(step, 2);
                run_block(step, 3);
            }

            function run_block(step: integer, i: integer) {
                app.op(i).run();
                val exp_time = rell.test.DEFAULT_FIRST_BLOCK_TIME + step * i;
                assert_equals(rell.test.last_block_time, exp_time);
                assert_equals(block @{} ( @omit @sort_desc .rowid, .timestamp ) limit 1, exp_time);
            }
        """)

        chkTests("tests", "test_1=OK,test_2=OK,test_3=OK,test_4=OK")
    }

    private fun init() {
        file("module.rell", "operation op(x: integer) {}")
        LibRellTestTxTest.initTxChain(tstCtx, tst)
    }

    private fun chkTx(expTime: Long, expRes: String = "RES:unit") {
        repl.chk("rell.test.tx().op(op(0)).nop().run();", expRes)
        chkLastBlockTime(expTime)
    }

    private fun chkLastBlockTime(expTime: Long) {
        repl.chk("rell.test.last_block_time", "RES:int[$expTime]")
        repl.chk("rell.test.last_block_time_or_null", "RES:int[$expTime]")
        repl.chk("block @{} ( @omit @sort_desc .rowid, .timestamp ) limit 1", "RES:int[$expTime]")
    }
}
