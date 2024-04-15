/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
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
                comment("Timestamp in milliseconds of the first block by default. (2020-01-01 00:00:00 UTC)")
            }
            constant("DEFAULT_BLOCK_INTERVAL", DEFAULT_BLOCK_INTERVAL, since = "0.13.3") {
                comment("Default time interval in milliseconds between each block. (10 seconds)")
            }

            property("last_block_time", type = "timestamp", since = "0.13.3") {
                comment("Timestamp in milliseconds of the previous block. Read will fail if no block has been built.")
                value { ctx ->
                    val t0 = ctx.exeCtx.testBlockClock.getLastBlockTime()
                    val t = Rt_Utils.checkNotNull(t0) {
                        "no_last_block_time" toCodeMsg "No last block time"
                    }
                    Rt_IntValue.get(t)
                }
            }

            property("last_block_time_or_null", type = "timestamp?", since = "0.13.3") {
                comment("Timestamp in milliseconds of the previous block or `null` if no block has been built.")
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getLastBlockTime()
                    if (t == null) Rt_NullValue else Rt_IntValue.get(t)
                }
            }

            property("next_block_time", type = "timestamp", since = "0.13.3") {
                comment("Timestamp in milliseconds which will be used for the next block.")
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getNextBlockTime()
                    Rt_IntValue.get(t)
                }
            }

            property("block_interval", type = "timestamp", since = "0.13.3") {
                comment("Time interval in milliseconds between current block and next block to be used.")
                value { ctx ->
                    val t = ctx.exeCtx.testBlockClock.getBlockInterval()
                    Rt_IntValue.get(t)
                }
            }

            function("set_block_interval", result = "integer", since = "0.13.3") {
                comment("""
                    Set the time interval in milliseconds between current block and the next one.
                    This property is not respected if a timestamp has been explicitly set by calling
                    `rell.test.set_next_block_time`.
                """)
                param(name = "interval", type = "integer", comment = "time interval to use between blocks")
                bodyContext { ctx, a ->
                    val clock = ctx.exeCtx.testBlockClock
                    val res = clock.getBlockInterval()
                    clock.setBlockInterval(a.asInteger())
                    Rt_IntValue.get(res)
                }
            }

            function("set_next_block_time", result = "unit", since = "0.13.3") {
                comment("Explicitly set the timestamp in milliseconds to use on the next block.")
                param(name = "time", type = "timestamp", comment = "timestamp to use on next block")
                bodyContext { ctx, a ->
                    ctx.exeCtx.testBlockClock.setNextBlockTime(a.asInteger())
                    Rt_UnitValue
                }
            }

            function("set_next_block_time_delta", result = "unit", since = "0.13.3") {
                comment("""
                    Explicitly set the timestamp in milliseconds to use on next block by specifying a time delay from the last block.
                """)
                param(name = "delta", type = "integer", comment = "time interval to use for next block")
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
            "block_interval:non_positive:$interval" toCodeMsg "Block interval must be positive (was: $interval)"
        }
        blockInterval = interval
    }

    fun setNextBlockTime(time: Long) {
        checkBlockTime(time)
        nextBlockTime = time
    }

    fun setNextBlockTimeDelta(delta: Long) {
        Rt_Utils.check(delta > 0) {
            "block_time_delta:non_positive:$delta" toCodeMsg "Block time delta must be positive (was: $delta)"
        }
        val last = lastBlockTime
        last ?: return
        nextBlockTime = checkedAdd(last, delta)
    }

    private fun checkBlockTime(time: Long) {
        Rt_Utils.check(time >= 0) {
            "block_time:negative:$time" toCodeMsg "Block time cannot be negative (was: $time)"
        }

        val last = lastBlockTime
        if (last != null) {
            Rt_Utils.check(time > last) {
                "block_time:too_old:$last:$time" toCodeMsg
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
