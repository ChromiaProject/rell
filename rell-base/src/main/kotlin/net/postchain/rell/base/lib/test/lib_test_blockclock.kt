/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import com.google.common.math.LongMath
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.utils.Rt_Utils

object Lib_Test_BlockClock {
    const val DEFAULT_FIRST_BLOCK_TIME = 1577836800_000L // 2020-01-01 00:00:00 UTC
    const val DEFAULT_BLOCK_INTERVAL = 10_000L

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            constant("DEFAULT_FIRST_BLOCK_TIME", DEFAULT_FIRST_BLOCK_TIME, since = "0.13.3") {
                comment("""
                    The default value for the timestamp of the first block in the blockchain, in milliseconds. Equal to
                    `2020-01-01 00:00:00 UTC`, or `1577836800000`.

                    This is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e. the number of
                    milliseconds that have elapsed since midnight on 1st January 1970.

                    @see 1. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
            }
            constant("DEFAULT_BLOCK_INTERVAL", DEFAULT_BLOCK_INTERVAL, since = "0.13.3") {
                comment("""
                    The default value for the time interval between each block in the blockchain, in milliseconds.
                    Equal to `10000` (`10` seconds).

                    @see 1. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
            }

            property("last_block_time", type = "timestamp", since = "0.13.3") {
                comment("""
                    The timestamp of the most recently built block in the blockchain, in milliseconds.

                    This is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e. the number of
                    milliseconds that have elapsed since midnight on 1st January 1970.
                    @throws exception if there are no blocks in the blockchain (i.e. if the first block in the
                    blockchain has yet to be built)

                    @see 1. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                value { ctx ->
                    val t0 = ctx.exeCtx.testBlockClock.getLastBlockTime()
                    val t = Rt_Utils.checkNotNull(t0) {
                        "no_last_block_time" to "No last block time"
                    }
                    Rt_IntValue.get(t)
                }
            }

            property("last_block_time_or_null", type = "timestamp?", since = "0.13.3") {
                comment("""
                    The timestamp of the most recently built block in the blockchain, in milliseconds.

                    If there are no blocks in the blockchain (i.e. if the first block in the blockchain has yet to be
                    built), this is `null`.

                    If a value is present, it is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e.
                    the number of milliseconds that have elapsed since midnight on 1st January 1970.

                    @see 1. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getLastBlockTime()
                    if (t == null) Rt_NullValue else Rt_IntValue.get(t)
                }
            }

            property("next_block_time", type = "timestamp", since = "0.13.3") {
                comment("""
                    The timestamp that the next block will have, once built, in milliseconds.

                    Equivalent to `rell.test.last_block_time + rell.test.block_interval` (assuming there is at least one
                    block in the blockchain).

                    This is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e. the number of
                    milliseconds that will have elapsed since midnight on 1st January 1970.

                    @see 1. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getNextBlockTime()
                    Rt_IntValue.get(t)
                }
            }

            property("block_interval", type = "timestamp", since = "0.13.3") {
                comment("""
                    The time interval that will be used to determine the time of the next block to be built.

                    The next block to be built will have a timestamp equal to
                    `rell.test.last_block_time + rell.test.block_interval` unless it is explicitly set with
                    `rell.test.set_next_block_time()` or `rell.test.set_next_block_time_delta()`.

                    Defaults to `10000` (`10` seconds).

                    Can be modified with `rell.test.set_block_interval()`.

                    @see 1. <a href="set_block_interval.html"><code>rell.test.set_block_interval()</code> - Rell Standard Library</a>
                    @see 2. <a href="set_next_block_time.html"><code>rell.test.set_next_block_time()</code> - Rell Standard Library</a>
                    @see 3. <a href="set_next_block_time_delta.html"><code>rell.test.set_next_block_time_delta()</code> - Rell Standard Library</a>
                    @see 4. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getBlockInterval()
                    Rt_IntValue.get(t)
                }
            }

            function("set_block_interval", result = "integer", since = "0.13.3") {
                comment("""
                    Set the time interval between future blocks in the blockchain, in milliseconds.

                    If the timestamp for the next block has been explicitly set by calling
                    `rell.test.set_next_block_time()` or `rell.test.set_next_block_time_delta(),
                    the new interval does not take effect until after the next block.

                    @return the previous interval value, in milliseconds

                    @see 1. <a href="set_next_block_time.html"><code>rell.test.set_next_block_time()</code> - Rell Standard Library</a>
                    @see 2. <a href="set_next_block_time_delta.html"><code>rell.test.set_next_block_time_delta()</code> - Rell Standard Library</a>
                    @see 3. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                param(name = "interval", type = "integer", comment = "the time interval between blocks")
                bodyContext { ctx, a ->
                    val clock = ctx.exeCtx.testBlockClock
                    val res = clock.getBlockInterval()
                    clock.setBlockInterval(a.asInteger())
                    Rt_IntValue.get(res)
                }
            }

            function("set_next_block_time", result = "unit", since = "0.13.3") {
                comment("""
                    Set the timestamp that the next block will have, once built, in milliseconds.

                    `time` is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e. the number of
                    milliseconds that will have elapsed since midnight on 1st January 1970.

                    @throws exception if `time` is in older than `rell.test.last_block_time`, or negative

                    @see 1. <a href="set_next_block_time_delta.html"><code>rell.test.set_next_block_time_delta()</code> - Rell Standard Library</a>
                    @see 2. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                param(name = "time", type = "timestamp", comment = "timestamp to use on next block")
                bodyContext { ctx, a ->
                    ctx.exeCtx.testBlockClock.setNextBlockTime(a.asInteger())
                    Rt_UnitValue
                }
            }

            function("set_next_block_time_delta", result = "unit", since = "0.13.3") {
                comment("""
                    Set the timestamp that the next block will have, once built, relative to the previous block, in
                    milliseconds.

                    Calling `rell.time.set_next_block_time_delta(x)` means the next block will have timestamp `x`
                    milliseconds after the timestamp of the previous block.

                    `rell.test.set_next_block_time_delta(delta)` is equivalent to
                    `rell.test.set_next_block_time(rell.test.last_block_time + delta)` if there is a previous block.
                    Otherwise, this does nothing.

                    @throws exception if `delta` is less than `1`

                    @see 1. <a href="set_next_block_time.html"><code>rell.test.set_next_block_time()</code> - Rell Standard Library</a>
                    @see 2. <a href="block/index.html"><code>rell.test.block</code> - Rell Standard Library</a>
                """)
                param(name = "delta", type = "integer", comment = "the time delta between the previous and next block")
                bodyContext { ctx, a ->
                    ctx.exeCtx.testBlockClock.setNextBlockTimeDelta(a.asInteger())
                    Rt_UnitValue
                }
            }
        }
    }
}

class Rt_TestBlockClock(state: State = DEFAULT_STATE) {
    private var blockInterval: Long = state.blockInterval
    private var nextBlockTime: Long? = state.nextBlockTime
    private var lastBlockTime: Long? = state.lastBlockTime

    fun getBlockInterval() = blockInterval
    fun getLastBlockTime() = lastBlockTime

    fun setLastBlockTime(time: Long) {
        checkBlockTime(time)
        lastBlockTime = time
        nextBlockTime = null
    }

    fun getNextBlockTime(): Long {
        val manual = nextBlockTime
        if (manual != null) {
            return manual
        }

        val last = lastBlockTime
        if (last != null) {
            val interval = blockInterval
            val next = checkedAdd(last, interval)
            return next
        }

        return Lib_Test_BlockClock.DEFAULT_FIRST_BLOCK_TIME
    }

    fun setBlockInterval(interval: Long) {
        Rt_Utils.check(interval > 0) {
            "block_interval:non_positive:$interval" to "Block interval must be positive (was: $interval)"
        }
        blockInterval = interval
    }

    fun setNextBlockTime(time: Long) {
        checkBlockTime(time)
        nextBlockTime = time
    }

    fun setNextBlockTimeDelta(delta: Long) {
        Rt_Utils.check(delta > 0) {
            "block_time_delta:non_positive:$delta" to "Block time delta must be positive (was: $delta)"
        }
        val last = lastBlockTime
        last ?: return
        nextBlockTime = checkedAdd(last, delta)
    }

    private fun checkBlockTime(time: Long) {
        Rt_Utils.check(time >= 0) {
            "block_time:negative:$time" to "Block time cannot be negative (was: $time)"
        }

        val last = lastBlockTime
        if (last != null) {
            Rt_Utils.check(time > last) {
                "block_time:too_old:$last:$time" to
                        "Block time must be newer than the last time (last: $last, next: $time)"
            }
        }
    }

    private fun checkedAdd(a: Long, b: Long): Long {
        return try {
            LongMath.checkedAdd(a, b)
        } catch (e: ArithmeticException) {
            throw Rt_Exception.common("time_overflow:$a:$b", "Time overflow: $a + $b")
        }
    }

    fun toState(): State = State(
        blockInterval = blockInterval,
        nextBlockTime = nextBlockTime,
        lastBlockTime = lastBlockTime,
    )

    class State(
        val blockInterval: Long,
        val nextBlockTime: Long?,
        val lastBlockTime: Long?,
    )

    companion object {
        val DEFAULT_STATE: State = State(
            blockInterval = Lib_Test_BlockClock.DEFAULT_BLOCK_INTERVAL,
            nextBlockTime = null,
            lastBlockTime = null,
        )
    }
}
