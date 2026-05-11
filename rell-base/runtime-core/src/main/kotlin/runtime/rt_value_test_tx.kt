/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.test.R_TestBlockType
import net.postchain.rell.base.lib.test.R_TestTxType
import net.postchain.rell.base.lib.test.RawTestOpValue
import net.postchain.rell.base.lib.test.RawTestTxValue
import net.postchain.rell.base.lib.type.Lib_Type_Gtv
import net.postchain.rell.base.model.MountName
import net.postchain.rell.base.utils.BytesKeyPair
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.toImmList
import java.util.*


class Rt_TestBlockValue(txs: List<RawTestTxValue>): Rt_Value {
    private val txs = txs.toMutableList()

    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun strCode(showTupleFieldNames: Boolean): String {
        val txsStr = txs.joinToString(",") { Rt_TestTxValue.strCode(it.ops, it.signers) }
        return "${R_TestBlockType.str()}[$txsStr]"
    }

    override fun str(format: Rt_StrFormat) = "block(${txs.joinToString(",")})"

    override fun equals(other: Any?) = other === this || (other is Rt_TestBlockValue && txs == other.txs)
    override fun hashCode() = Objects.hash(txs)

    fun txs() = txs.toImmList()

    fun addTx(tx: RawTestTxValue) {
        txs.add(tx)
    }

    companion object: Rt_ValueClass<Rt_TestBlockValue> {
        override val name
            get() = "rell.test.block"
    }
}

internal class Rt_TestTxValue(
    ops: List<RawTestOpValue>,
    signers: List<BytesKeyPair>
): Rt_Value {
    private val ops = ops.toMutableList()
    private val signers = signers.toMutableList()

    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun str(format: Rt_StrFormat) = toString(ops)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(ops, signers)

    override fun equals(other: Any?) =
        other === this || (other is Rt_TestTxValue && ops == other.ops && signers == other.signers)

    override fun hashCode() = Objects.hash(ops, signers)

    fun addOp(op: RawTestOpValue) {
        ops.add(op)
    }

    fun sign(keyPair: BytesKeyPair) {
        signers.add(keyPair)
    }

    fun copy() = Rt_TestTxValue(ops, signers)

    fun toRaw() = RawTestTxValue(ops.toImmList(), signers.toImmList())

    companion object: Rt_ValueClass<Rt_TestTxValue> {
        override val name
            get() = "rell.test.tx"
        fun strCode(ops: List<RawTestOpValue>, signers: List<BytesKeyPair>): String {
            val opsList = ops.map { Rt_TestOpValue.strCode(it.name, it.args) }
            val signersList = signers.map { it.pub.toHex().substring(0, 6).lowercase() }
            val innerStr = (opsList + signersList).joinToString(",")
            return "${R_TestTxType.str()}[$innerStr]"
        }

        fun toString(ops: List<RawTestOpValue>): String {
            val opsStr = ops.joinToString(",")
            return "tx[$opsStr]"
        }
    }
}

class Rt_TestOpValue(val mountName: MountName, val args: ImmList<Gtv>): Rt_Value {
    val nameValue: Rt_Value by lazy {
        Rt_TextValue.get(mountName.str())
    }

    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun str(format: Rt_StrFormat) = toString(mountName, args)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(mountName, args)

    override fun equals(other: Any?) =
        other === this || (other is Rt_TestOpValue && mountName == other.mountName && args == other.args)

    override fun hashCode() = Objects.hash(mountName, args)

    fun toRaw() = RawTestOpValue(mountName, args)

    fun argsValue(): Rt_Value {
        val argValues: MutableList<Rt_Value> = args.map { Rt_GtvValue.get(it) }.toMutableList()
        return Rt_ListValue(Lib_Type_Gtv.LIST_OF_GTV_TYPE, argValues)
    }

    companion object: Rt_ValueClass<Rt_TestOpValue> {
        override val name
            get() = "rell.test.op"
        fun strCode(name: MountName, args: List<Gtv>): String {
            val argsStr = args.joinToString(",") { Rt_GtvValue.get(it).str(Rt_StrFormat.V2) }
            return "op[$name($argsStr)]"
        }

        fun toString(name: MountName, args: List<Gtv>): String {
            val argsStr = args.joinToString(",") { Rt_GtvValue.get(it).str(Rt_StrFormat.V2) }
            return "$name($argsStr)"
        }
    }
}
