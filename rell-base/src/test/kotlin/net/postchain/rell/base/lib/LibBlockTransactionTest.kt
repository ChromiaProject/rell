/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import org.junit.Test

class LibBlockTransactionTest: BaseRellTest() {
    companion object {
        val BLOCK_INSERTS_0 = RellTestContext.BlockBuilder(0)
                .block(710, 10, "BC00", 1600000000000)
                .tx(720, 710, "AC00", "ACDA00", "4321")
                .list()

        val BLOCK_INSERTS_333 = RellTestContext.BlockBuilder(333)
                .block(111, 222, "DEADBEEF", 1500000000000)
                .tx(444, 111, "FADE", "EDAF", "1234")
                .list()

        val BLOCK_INSERTS_555 = RellTestContext.BlockBuilder(555)
                .block(1, 35, "FEEBDAED", 1400000000000)
                .tx(2, 1, "CEED", "FEED", "4321")
                .list()

        val BLOCK_INSERTS_CURRENT = RellTestContext.BlockBuilder(333)
                .block(101, 10, "DEADBEEF", 1500000000000)
                .block(102, 20, null, null)
                .tx(201, 101, "CEED", "FEED", "4321")
                .tx(202, 102, "FADE", "EDAF", "1234")
                .list()
    }

    @Test fun testBlockRead() {
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chk("block @* {}", "list<block>[block[111]]")
        chk("transaction @* {}", "list<transaction>[transaction[444]]")

        val block = "val b = block@{}"
        val trans = "val t = transaction@{}"

        chkEx("{ $block; return b.block_height; }", "int[222]")
        chkEx("{ $block; return b.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $block; return b.timestamp; }", "int[1500000000000]")
        chkEx("{ $block; return b.block_iid; }", "ct_err:unknown_member:[block]:block_iid")

        chkEx("{ $trans; return t.tx_rid; }", "byte_array[fade]")
        chkEx("{ $trans; return t.tx_hash; }", "byte_array[1234]")
        chkEx("{ $trans; return t.tx_data; }", "byte_array[edaf]")
        chkEx("{ $trans; return t.tx_iid; }", "ct_err:unknown_member:[transaction]:tx_iid")

        chkEx("{ $trans; return t.block; }", "block[111]")
        chkEx("{ $trans; return t.block.block_height; }", "int[222]")
        chkEx("{ $trans; return t.block.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $trans; return t.block.timestamp; }", "int[1500000000000]")
        chkEx("{ $trans; return t.block.block_iid; }", "ct_err:unknown_member:[block]:block_iid")
    }

    @Test fun testBlockReadAt() {
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chk("block @* {}", "list<block>[block[111]]")
        chk("transaction @* {}", "list<transaction>[transaction[444]]")

        chk("block@{} ( .block_height )", "int[222]")
        chk("block@{} ( .block_rid )", "byte_array[deadbeef]")
        chk("block@{} ( .timestamp )", "int[1500000000000]")
        chk("block@{} ( .block_iid )", "ct_err:expr_attr_unknown:block_iid")

        chk("transaction@{} ( .tx_rid )", "byte_array[fade]")
        chk("transaction@{} ( .tx_hash )", "byte_array[1234]")
        chk("transaction@{} ( .tx_data )", "byte_array[edaf]")
        chk("transaction@{} ( .tx_iid )", "ct_err:expr_attr_unknown:tx_iid")

        chk("transaction@{} ( .block )", "block[111]")
        chk("transaction@{} ( .block.block_height )", "int[222]")
        chk("transaction@{} ( .block.block_rid )", "byte_array[deadbeef]")
        chk("transaction@{} ( .block.timestamp )", "int[1500000000000]")
        chk("transaction@{} ( .block.block_iid )", "ct_err:unknown_member:[block]:block_iid")
    }

    @Test fun testBlockWrite() {
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chkOp("create block(block_height = 123, block_rid = x'deadbeef', timestamp = 456);",
                "ct_err:expr_create_cant:block")
        chkOp("create transaction(tx_rid = x'dead', tx_hash = x'beef', tx_data = x'cafe', block = block@{});",
                "ct_err:expr_create_cant:transaction")
        chkOp("create blocks(block_height = 123, block_rid = x'deadbeef', timestamp = 456);", "ct_err:unknown_name:blocks")
        chkOp("create transactions(tx_rid = x'dead', tx_hash = x'beef', tx_data = x'cafe', block = block@{});",
            "ct_err:unknown_name:transactions")

        chkOp("update block@{}( block_height = 999 );", "ct_err:stmt_update_cant:block")
        chkOp("update block@{}( block_rid = x'cafe' );", "ct_err:stmt_update_cant:block")
        chkOp("update block@{}( timestamp = 999 );", "ct_err:stmt_update_cant:block")

        chkOp("update transaction@{}( tx_rid = x'dead' );", "ct_err:stmt_update_cant:transaction")
        chkOp("update transaction@{}( tx_hash = x'dead' );", "ct_err:stmt_update_cant:transaction")
        chkOp("update transaction@{}( tx_data = x'dead' );", "ct_err:stmt_update_cant:transaction")
        chkOp("update transaction@{}( block = block@{} );", "ct_err:stmt_update_cant:transaction")

        chkOp("delete block@{};", "ct_err:stmt_delete_cant:block")
        chkOp("delete transaction@{};", "ct_err:stmt_delete_cant:transaction")
    }

    @Test fun testBlockMisc() {
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chkCompile("entity block {}", "ct_err:[mnt_conflict:sys:[block]:block][name_conflict:sys:block:ENTITY]")
        chkCompile("entity transaction {}",
            "ct_err:[mnt_conflict:sys:[transaction]:transaction][name_conflict:sys:transaction:ENTITY]")

        chk("block @* {}", "list<block>[block[111]]")
        chk("transaction @* {}", "list<transaction>[transaction[444]]")
        chk("blocks @* {}", "ct_err:unknown_name:blocks")
        chk("transactions @* {}", "ct_err:unknown_name:transactions")
    }

    @Test fun testBlockRef() {
        def("entity foo { x: integer; block; }")
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chkOp("create foo (123, block@{});")

        val base = "val f = foo@{}"
        chkEx("{ $base; return f.block; }", "block[111]")
        chkEx("{ $base; return f.block.block_height; }", "int[222]")
        chkEx("{ $base; return f.block.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $base; return f.block.timestamp; }", "int[1500000000000]")

        chk("foo@{} ( .block )", "block[111]")
        chk("foo@{} ( .block.block_height )", "int[222]")
        chk("foo@{} ( .block.block_rid )", "byte_array[deadbeef]")
        chk("foo@{} ( .block.timestamp )", "int[1500000000000]")
    }

    @Test fun testTransactionRef() {
        def("entity foo { x: integer; trans: transaction; }")
        tst.inserts = BLOCK_INSERTS_333
        tst.chainId = 333

        chkOp("create foo (123, transaction@{});")

        val base = "val f = foo@{}"
        chkEx("{ $base; return f.trans.block; }", "block[111]")
        chkEx("{ $base; return f.trans.block.block_height; }", "int[222]")
        chkEx("{ $base; return f.trans.block.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $base; return f.trans.block.timestamp; }", "int[1500000000000]")

        chk("foo@{} ( .trans.block )", "block[111]")
        chk("foo@{} ( .trans.block.block_height )", "int[222]")
        chk("foo@{} ( .trans.block.block_rid )", "byte_array[deadbeef]")
        chk("foo@{} ( .trans.block.timestamp )", "int[1500000000000]")
    }

    @Test fun testBlockRefChainId() {
        def("entity foo { x: integer; b: block; t: transaction; }")
        tst.chainId = 333
        tst.inserts = BLOCK_INSERTS_333
        insert("c333.foo", "x,b,t", "1,123,111,444")

        val base = "val f = foo@{}"
        chkEx("{ $base; return f.b; }", "block[111]")
        chkEx("{ $base; return f.b.block_height; }", "int[222]")
        chkEx("{ $base; return f.b.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $base; return f.b.timestamp; }", "int[1500000000000]")
        chkEx("{ $base; return f.t.block; }", "block[111]")
        chkEx("{ $base; return f.t.block.block_height; }", "int[222]")
        chkEx("{ $base; return f.t.block.block_rid; }", "byte_array[deadbeef]")
        chkEx("{ $base; return f.t.block.timestamp; }", "int[1500000000000]")

        chk("foo@{} ( .b )", "block[111]")
        chk("foo@{} ( .b.block_height )", "int[222]")
        chk("foo@{} ( .b.block_rid )", "byte_array[deadbeef]")
        chk("foo@{} ( .b.timestamp )", "int[1500000000000]")
        chk("foo@{} ( .t.block )", "block[111]")
        chk("foo@{} ( .t.block.block_height )", "int[222]")
        chk("foo@{} ( .t.block.block_rid )", "byte_array[deadbeef]")
        chk("foo@{} ( .t.block.timestamp )", "int[1500000000000]")
    }

    @Test fun testBlockReadChainId() {
        tst.strictToString = false

        val expr = """
            transaction @* {} (
                _=transaction,
                _=.tx_rid,
                _=.tx_hash,
                _=.tx_data,
                _=.block,
                _=.block.block_height,
                _=.block.block_rid,
                _=.block.timestamp
            )
        """

        chk(expr, "[]") // Does database initialization

        var t = createChainIdTester(333, 111, 444, BLOCK_INSERTS_333)

        t.chk(expr, "[(transaction[444],0xfade,0x1234,0xedaf,block[111],222,0xdeadbeef,1500000000000)]")
        t.chk("(b: block, t: transaction) @* {}", "[(b=block[111],t=transaction[444])]")

        t.chk("foo @* {} (_=foo,_=.b,_=.t)", "[(foo[1],block[111],transaction[444])]")
        t.chk("foo @* {} (_=.value)", "[0]")
        t.chkOp("update foo @* { .b.block_height >= 0, .t.tx_hash != x'' } ( value = 100 );")
        t.chk("foo @* {} (_=.value)", "[100]")
        t.chkOp("delete foo @* { .b.block_height >= 0, .t.tx_hash != x'' };")
        t.chk("foo @* {} (_=.value)", "[]")

        t = createChainIdTester(555, 1, 2, BLOCK_INSERTS_555)

        t.chk(expr, "[(transaction[2],0xceed,0x4321,0xfeed,block[1],35,0xfeebdaed,1400000000000)]")
        t.chk("(b: block, t: transaction) @* {}", "[(b=block[1],t=transaction[2])]")

        t.chk("foo @* {} (_=foo,_=.b,_=.t)", "[(foo[1],block[1],transaction[2])]")
        t.chk("foo @* {} (_=.value)", "[0]")
        t.chkOp("update foo @* { .b.block_height >= 0, .t.tx_hash != x'' } ( value = 100 );")
        t.chk("foo @* {} (_=.value)", "[100]")
        t.chkOp("delete foo @* { .b.block_height >= 0, .t.tx_hash != x'' };")
        t.chk("foo @* {} (_=.value)", "[]")
    }

    @Test fun testGtv() {
        def("struct blk { v: block; }")
        def("struct tx { v: transaction; }")
        tst.chainId = 333
        tst.inserts = BLOCK_INSERTS_333

        chk("block @ {}", "block[111]")
        chk("blk(block @ {})", "blk[v=block[111]]")
        chk("blk(block @ {}).to_gtv_pretty()", """gtv[["v": 111]]""")
        chk("""blk.from_gtv_pretty(gtv.from_json('{"v":111}'))""", "blk[v=block[111]]")
        chk("blk(block @ {}).to_gtv()", """gtv[[111]]""")
        chk("""blk.from_gtv(gtv.from_json('[111]'))""", "blk[v=block[111]]")
        chk("(block @ {}).to_gtv()", """gtv[111]""")
        chk("block.from_gtv(gtv.from_json('111'))", "block[111]")

        chk("transaction @ {}", "transaction[444]")
        chk("tx(transaction @ {})", "tx[v=transaction[444]]")
        chk("tx(transaction @ {}).to_gtv_pretty()", """gtv[["v": 444]]""")
        chk("""tx.from_gtv_pretty(gtv.from_json('{"v":444}'))""", "tx[v=transaction[444]]")
        chk("tx(transaction @ {}).to_gtv()", """gtv[[444]]""")
        chk("""tx.from_gtv(gtv.from_json('[444]'))""", "tx[v=transaction[444]]")
        chk("(transaction @ {}).to_gtv()", """gtv[444]""")
        chk("transaction.from_gtv(gtv.from_json('444'))", "transaction[444]")

        chk("block.from_gtv(gtv.from_json('555'))", "gtv_err:obj_missing:[block]:555")
        chk("transaction.from_gtv(gtv.from_json('555'))", "gtv_err:obj_missing:[transaction]:555")
        chk("""blk.from_gtv(gtv.from_json('[555]'))""", "gtv_err:obj_missing:[block]:555")
        chk("""tx.from_gtv(gtv.from_json('[555]'))""", "gtv_err:obj_missing:[transaction]:555")
    }

    @Test fun testSelectOrderByTimestamp() {
        tst.chainId = 333
        tst.inserts = RellTestContext.BlockBuilder(333)
                .block(1, 222, "DEADBEEF", 1500000000000)
                .block(2, 35, "FEEBDAED", 1400000000000)
                .block(3, 46, "BADBAD", 0)
                .list()
        chk("block @* {} ( @sort .timestamp )", "list<integer>[int[0],int[1400000000000],int[1500000000000]]")
    }

    @Test fun testSelectCurrentBlock() {
        tst.chainId = 333
        tst.inserts = BLOCK_INSERTS_CURRENT

        chk("block @* {}", "list<block>[block[101],block[102]]")
        chk("block @? { .block_height == 10 }", "block[101]")
        chk("block @? { .block_height == 20 }", "block[102]")

        chk("block @? { .block_height == 10 } (.block_rid)", "byte_array[deadbeef]")
        chk("block @? { .block_height == 10 } (.timestamp)", "int[1500000000000]")
        chk("block @? { .block_height == 20 } (.block_rid)", "rt_err:sql_null:byte_array")
        chk("block @? { .block_height == 20 } (.timestamp)", "rt_err:sql_null:integer")
    }

    @Test fun testSelectCurrentTransaction() {
        tst.chainId = 333
        tst.inserts = BLOCK_INSERTS_CURRENT
        chk("transaction @* {}", "list<transaction>[transaction[201],transaction[202]]")
        chk("transaction @? { .block.block_height == 10 }", "transaction[201]")
        chk("transaction @? { .block.block_height == 20 }", "transaction[202]")
    }

    @Test fun testMountName() {
        chkCompile("@mount('blocks') entity foo {}", "ct_err:mnt_conflict:sys:[foo]:blocks")
        chkCompile("@mount('transactions') entity foo {}", "ct_err:mnt_conflict:sys:[foo]:transactions")
        chkCompile("@mount('block') entity foo {}", "ct_err:mnt_conflict:sys:[foo]:block")
        chkCompile("@mount('transaction') entity foo {}", "ct_err:mnt_conflict:sys:[foo]:transaction")
    }

    private fun createChainIdTester(chainId: Long, blockIid: Long, txIid: Long, inserts: List<String>): RellCodeTester {
        val t = RellCodeTester(tstCtx)
        t.def("entity foo { b: block; t: transaction; mutable value: integer; }")
        t.insert(inserts)
        t.insert("c${chainId}.foo", "b,t,value", "1,$blockIid,$txIid,0")
        t.strictToString = false
        t.dropTables = false
        t.chainId = chainId
        return t
    }
}
