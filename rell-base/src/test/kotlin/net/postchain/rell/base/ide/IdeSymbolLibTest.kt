/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import org.junit.Test

class IdeSymbolLibTest: BaseIdeSymbolTest() {
    @Test fun testChainContext() {
        file("module.rell", "struct module_args { x: integer; }")

        val chainCtx = arrayOf("chain_context=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell:chain_context")

        chkSymsExpr("chain_context", *chainCtx, err = "expr_novalue:namespace:[chain_context]")
        chkSymsExpr("chain_context.blockchain_rid", *chainCtx,
            "blockchain_rid=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:chain_context.blockchain_rid",
        )
        chkSymsExpr("chain_context.raw_config", *chainCtx,
            "raw_config=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:chain_context.raw_config",
        )

        val moduleArgs = arrayOf(
            "args=MEM_SYS_PROPERTY|-|module.rell/struct[module_args]", "?head=PROPERTY|rell:chain_context.args",
        )
        chkSymsExpr("chain_context.args", *chainCtx, *moduleArgs)
        chkSymsExpr("chain_context.args.x", *chainCtx, *moduleArgs,
            "x=MEM_STRUCT_ATTR|-|module.rell/struct[module_args].attr[x]", "?head=STRUCT_ATTR|:module_args.x",
        )
    }

    @Test fun testChainContextModuleArgs() {
        file("args.rell", "struct module_args { x: integer; }")

        chkSymsFile("args.rell",
            "module_args=DEF_STRUCT|struct[module_args]|-", "?head=STRUCT|:module_args",
            "x=MEM_STRUCT_ATTR|struct[module_args].attr[x]|-", "?head=STRUCT_ATTR|:module_args.x",
        )

        chkSymsExpr("chain_context.args",
            "args=MEM_SYS_PROPERTY|-|args.rell/struct[module_args]", "?head=PROPERTY|rell:chain_context.args",
        )
        chkSymsExpr("chain_context.args.x",
            "x=MEM_STRUCT_ATTR|-|args.rell/struct[module_args].attr[x]", "?head=STRUCT_ATTR|:module_args.x",
        )
    }

    @Test fun testOpContext() {
        file("module.rell", "function g() = gtv.from_json('');")

        val opCtx = arrayOf("op_context=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell:op_context")

        chkSymsExpr("op_context.last_block_time", *opCtx,
            "last_block_time=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:op_context.last_block_time",
        )
        chkSymsExpr("op_context.block_height", *opCtx,
            "block_height=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:op_context.block_height",
        )
        chkSymsExpr("op_context.op_index", *opCtx,
            "op_index=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:op_context.op_index",
        )
        chkSymsExpr("op_context.transaction", *opCtx,
            "transaction=MEM_SYS_PROPERTY|-|-", "?head=PROPERTY|rell:op_context.transaction",
        )

        chkSymsExpr("op_context.get_signers()", *opCtx,
            "get_signers=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:op_context.get_signers",
        )
        chkSymsExpr("op_context.is_signer(x'')", *opCtx,
            "is_signer=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:op_context.is_signer",
        )
        chkSymsExpr("op_context.get_all_operations()", *opCtx,
            "get_all_operations=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:op_context.get_all_operations",
        )

        chkSymsExpr("op_context.emit_event('', g())", *opCtx,
            "emit_event=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:op_context.emit_event",
        )
    }

    @Test fun testInteger() {
        val type = arrayOf("integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer")
        chkSymsExpr("integer.MIN_VALUE", *type, "MIN_VALUE=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:integer.MIN_VALUE")
        chkSymsExpr("integer.MAX_VALUE", *type, "MAX_VALUE=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:integer.MAX_VALUE")

        chkSymsExpr("integer('123')", "integer=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:integer")
        chkSymsExpr("integer('123', 10)", "integer=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:integer")
        chkSymsExpr("integer(123.456)", "integer=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:integer")

        chkSymsExpr("integer.from_text('123')", *type,
            "from_text=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:integer.from_text",
        )
        chkSymsExpr("integer.from_text('123', 10)", *type,
            "from_text=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:integer.from_text",
        )
        chkSymsExpr("integer.from_hex('1234')", *type,
            "from_hex=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:integer.from_hex",
        )
    }

    @Test fun testDecimal() {
        val type = arrayOf("decimal=DEF_TYPE|-|-", "?head=TYPE|rell:decimal")
        chkSymsExpr("decimal.MIN_VALUE", *type, "MIN_VALUE=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:decimal.MIN_VALUE")
        chkSymsExpr("decimal.MAX_VALUE", *type, "MAX_VALUE=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:decimal.MAX_VALUE")
        chkSymsExpr("decimal.PRECISION", *type, "PRECISION=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:decimal.PRECISION")
        chkSymsExpr("decimal.SCALE", *type, "SCALE=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:decimal.SCALE")
        chkSymsExpr("decimal.INT_DIGITS", *type, "INT_DIGITS=DEF_CONSTANT|-|-", "?head=CONSTANT|rell:decimal.INT_DIGITS")

        chkSymsExpr("decimal(123)", "decimal=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:decimal")
        chkSymsExpr("decimal('123')", "decimal=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:decimal")

        chkSymsExpr("decimal.from_text('123')", *type,
            "from_text=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:decimal.from_text",
        )
    }

    @Test fun testTypes() {
        chkSyms("function f(): unit {}", "unit=DEF_TYPE|-|-", "?doc=TYPE|rell:unit|<type> unit")

        chkLibType("boolean", "?doc=TYPE|rell:boolean|<type> boolean")
        chkLibType("text", "?doc=TYPE|rell:text|<type> text")
        chkLibType("byte_array", "?doc=TYPE|rell:byte_array|<type> byte_array: [iterable]<[integer]>")
        chkLibType("integer", "?doc=TYPE|rell:integer|<type> integer")
        chkLibType("decimal", "?doc=TYPE|rell:decimal|<type> decimal")
        chkLibType("rowid", "?doc=TYPE|rell:rowid|<type> rowid")
        chkLibType("name", "?doc=ALIAS|rell:name|<alias> name = [text]\n\n<type> text")
        chkLibType("timestamp", "?doc=ALIAS|rell:timestamp|<alias> timestamp = [integer]\n\n<type> integer")
        chkLibType("signer", "?doc=TYPE|rell:signer|<type> signer")
        chkLibType("guid", "?doc=TYPE|rell:guid|<type> guid")
        chkLibType("tuid", "?doc=ALIAS|rell:tuid|<alias> tuid = [text]\n\n<type> text")
        chkLibType("json", "?doc=TYPE|rell:json|<type> json")
        chkLibType("range", "?doc=TYPE|rell:range|<type> range: [iterable]<[integer]>")
        chkLibType("gtv", "?doc=TYPE|rell:gtv|<type> gtv")
        chkLibType("pubkey",
            "?doc=ALIAS|rell:pubkey|<alias> pubkey = [byte_array]\n\n<type> byte_array: [iterable]<[integer]>")
    }

    private fun chkLibType(type: String, expected: String) {
        chkSymsType(type, "$type=DEF_TYPE|-|-", expected)
    }

    @Test fun testTypeSpecialConstructor() {
        file("module.rell", "entity data {}")
        chkSymsExpr("rell.meta(data)", "meta=DEF_TYPE|-|-", "?doc=CONSTRUCTOR|rell:rell.meta|<constructor>(...)")
    }

    @Test fun testTypeFunctions() {
        chkSymsExpr("unit()", "unit=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:unit")
        chkSymsExpr("json('')", "json=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:json")

        val byteArray = arrayOf("byte_array=DEF_TYPE|-|-", "?head=TYPE|rell:byte_array")
        chkSymsExpr("byte_array('1234')", "byte_array=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:byte_array")
        chkSymsExpr("byte_array.from_list([1,2,3])", *byteArray, "from_list=DEF_FUNCTION_SYSTEM|-|-")
        chkSymsExpr("byte_array.from_hex('1234')", *byteArray, "from_hex=DEF_FUNCTION_SYSTEM|-|-")
        chkSymsExpr("byte_array.from_base64('1234')", *byteArray, "from_base64=DEF_FUNCTION_SYSTEM|-|-")

        val gtv = arrayOf("gtv=DEF_TYPE|-|-", "?head=TYPE|rell:gtv")
        chkSymsExpr("gtv.from_json('')", *gtv, "from_json=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:gtv.from_json")
        chkSymsExpr("gtv.from_bytes(x'')", *gtv, "from_bytes=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:gtv.from_bytes")

        chkSymsExpr("range(10)", "range=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:range")
        chkSymsExpr("range(1, 10)", "range=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:range")
        chkSymsExpr("range(1, 10, 2)", "range=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:range")

        val textFromBytes = arrayOf("from_bytes=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:text.from_bytes")
        chkSymsExpr("text.from_bytes(x'')", "text=DEF_TYPE|-|-", "?head=TYPE|rell:text", *textFromBytes)
        chkSymsExpr("text.from_bytes(x'', true)", "text=DEF_TYPE|-|-", "?head=TYPE|rell:text", *textFromBytes)
    }

    @Test fun testTestTxApi() {
        tst.testLib = true

        val rellTest = arrayOf(
            "rell=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell:rell",
            "test=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell.test:rell.test",
        )

        chkSymsType("rell.test.block", *rellTest, "block=DEF_TYPE|-|-", "?head=TYPE|rell.test:rell.test.block")
        chkSymsType("rell.test.tx", *rellTest, "tx=DEF_TYPE|-|-", "?head=TYPE|rell.test:rell.test.tx")
        chkSymsType("rell.test.op", *rellTest, "op=DEF_TYPE|-|-", "?head=TYPE|rell.test:rell.test.op")

        chkSymsExpr("rell.test.block()", *rellTest, "block=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell.test:rell.test.block")
        chkSymsExpr("rell.test.tx()", *rellTest, "tx=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell.test:rell.test.tx")
        chkSymsExpr("rell.test.op('foo')", *rellTest, "op=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell.test:rell.test.op")
    }

    @Test fun testTestKeypairs() {
        tst.testLib = true
        file("module.rell", "function kp() = rell.test.keypair(pub = x'', priv = x'');")

        val rellTest = arrayOf(
            "rell=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell:rell",
            "test=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell.test:rell.test",
        )

        chkSymsExpr("rell.test", *rellTest, err = "expr_novalue:namespace:[rell.test]")
        chkSymsExpr("rell.test.keypair()", *rellTest,
            "keypair=DEF_STRUCT|-|-", "?head=STRUCT|rell.test:rell.test.keypair",
            err = "attr_missing:[rell.test.keypair]:pub,priv",
        )
        chkSymsExpr("kp().pub", "pub=MEM_STRUCT_ATTR|-|-", "?head=STRUCT_ATTR|rell.test:rell.test.keypair.pub")
        chkSymsExpr("kp().priv", "priv=MEM_STRUCT_ATTR|-|-", "?head=STRUCT_ATTR|rell.test:rell.test.keypair.priv")

        val kps = "rell.test.keypairs"
        val keyPairs = arrayOf("keypairs=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell.test:$kps")
        chkSymsExpr(kps, *rellTest, *keyPairs, err = "expr_novalue:namespace:[rell.test.keypairs]")
        chkSymsExpr("$kps.bob", *rellTest, *keyPairs, "bob=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$kps.bob")
        chkSymsExpr("$kps.alice", *rellTest, *keyPairs, "alice=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$kps.alice")
        chkSymsExpr("$kps.trudy", *rellTest, *keyPairs, "trudy=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$kps.trudy")

        val privks = "rell.test.privkeys"
        val privKeys = arrayOf("privkeys=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell.test:$privks")
        chkSymsExpr(privks, *rellTest, *privKeys, err = "expr_novalue:namespace:[rell.test.privkeys]")
        chkSymsExpr("$privks.bob", *rellTest, *privKeys, "bob=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$privks.bob")
        chkSymsExpr("$privks.alice", *rellTest, *privKeys, "alice=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$privks.alice")
        chkSymsExpr("$privks.trudy", *rellTest, *privKeys, "trudy=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$privks.trudy")

        val pubks = "rell.test.pubkeys"
        val pubKeys = arrayOf("pubkeys=DEF_NAMESPACE|-|-", "?head=NAMESPACE|rell.test:$pubks")
        chkSymsExpr(pubks, *rellTest, *pubKeys, err = "expr_novalue:namespace:[rell.test.pubkeys]")
        chkSymsExpr("$pubks.bob", *rellTest, *pubKeys, "bob=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$pubks.bob")
        chkSymsExpr("$pubks.alice", *rellTest, *pubKeys, "alice=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$pubks.alice")
        chkSymsExpr("$pubks.trudy", *rellTest, *pubKeys, "trudy=DEF_CONSTANT|-|-", "?head=CONSTANT|rell.test:$pubks.trudy")
    }

    @Test fun testStruct() {
        file("module.rell", "struct rec { x: integer; }")
        chkSymsExpr("rec.from_gtv(gtv.from_json(''))",
            "from_gtv=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv",
        )
    }

    @Test fun testCollectionType() {
        chkSymsType("list<integer>",
            "list=DEF_TYPE|-|-", "?head=TYPE|rell:list",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
        )
        chkSymsType("set<integer>",
            "set=DEF_TYPE|-|-", "?head=TYPE|rell:set",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
        )
        chkSymsType("map<integer,text>",
            "map=DEF_TYPE|-|-", "?head=TYPE|rell:map",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "text=DEF_TYPE|-|-", "?head=TYPE|rell:text",
        )
    }

    @Test fun testCollectionConstructor() {
        chkSymsExpr("list<integer>()",
            "list=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:list",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
        )
        chkSymsExpr("set<integer>()",
            "set=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:set",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
        )
        chkSymsExpr("map<integer,text>()",
            "map=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:map",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "text=DEF_TYPE|-|-", "?head=TYPE|rell:text",
        )
        chkSymsExpr("list([123])", "list=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:list")
        chkSymsExpr("set([123])", "set=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:set")
        chkSymsExpr("map([123:'ABC'])", "map=DEF_TYPE|-|-", "?head=CONSTRUCTOR|rell:map")
    }

    @Test fun testCollectionStaticMethod() {
        chkSymsExpr("list<integer>.from_gtv(null.to_gtv())",
            "list=DEF_TYPE|-|-", "?head=TYPE|rell:list",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "from_gtv=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv",
        )

        chkSymsExpr(
            "set<integer>.from_gtv(null.to_gtv())",
            "set=DEF_TYPE|-|-", "?head=TYPE|rell:set",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "from_gtv=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv",
        )

        chkSymsExpr("map<integer,text>.from_gtv(null.to_gtv())",
            "map=DEF_TYPE|-|-", "?head=TYPE|rell:map",
            "integer=DEF_TYPE|-|-", "?head=TYPE|rell:integer",
            "text=DEF_TYPE|-|-", "?head=TYPE|rell:text",
            "from_gtv=DEF_FUNCTION_SYSTEM|-|-", "?head=FUNCTION|rell:rell.gtv_ext.from_gtv",
        )
    }

    @Test fun testBlockEntity() {
        file("module.rell", "function get_block() = block @ {};")

        val block = arrayOf("block=DEF_ENTITY|-|-", "?doc=ENTITY|:block|blocks|<entity> block")
        val blockRid = arrayOf(
            "block_rid=MEM_ENTITY_ATTR_KEY|-|-", "?doc=ENTITY_ATTR|:block.block_rid|<key> block_rid: [byte_array]",
        )
        val blockHeight = arrayOf(
            "block_height=MEM_ENTITY_ATTR_KEY|-|-", "?doc=ENTITY_ATTR|:block.block_height|<key> block_height: [integer]",
        )
        val timestamp = arrayOf(
            "timestamp=MEM_ENTITY_ATTR_NORMAL|-|-", "?doc=ENTITY_ATTR|:block.timestamp|timestamp: [integer]",
        )

        chkSymsExpr("block @* {}", *block)
        chkSymsExpr("block @* {} ( _ = .block_rid )", *block, *blockRid)
        chkSymsExpr("block @* {} ( _ = .block_height )", *block, *blockHeight)
        chkSymsExpr("block @* {} ( _ = .timestamp )", *block, *timestamp)

        chkSymsExpr("get_block().block_rid", *blockRid)
        chkSymsExpr("get_block().block_height", *blockHeight)
        chkSymsExpr("get_block().timestamp", *timestamp)
    }

    @Test fun testTransactionEntity() {
        file("module.rell", "function get_tx() = transaction @ {};")

        val tx = arrayOf("transaction=DEF_ENTITY|-|-", "?doc=ENTITY|:transaction|transactions|<entity> transaction")
        val txRid = arrayOf(
            "tx_rid=MEM_ENTITY_ATTR_KEY|-|-", "?doc=ENTITY_ATTR|:transaction.tx_rid|<key> tx_rid: [byte_array]",
        )
        val txHash = arrayOf(
            "tx_hash=MEM_ENTITY_ATTR_NORMAL|-|-", "?doc=ENTITY_ATTR|:transaction.tx_hash|tx_hash: [byte_array]",
        )
        val txData = arrayOf(
            "tx_data=MEM_ENTITY_ATTR_NORMAL|-|-", "?doc=ENTITY_ATTR|:transaction.tx_data|tx_data: [byte_array]",
        )
        val block = arrayOf("block=MEM_ENTITY_ATTR_NORMAL|-|-", "?doc=ENTITY_ATTR|:transaction.block|block: [block]")

        chkSymsExpr("transaction @* {}", *tx)
        chkSymsExpr("transaction @* {} ( _ = .tx_rid )", *tx, *txRid)
        chkSymsExpr("transaction @* {} ( _ = .tx_hash )", *tx, *txHash)
        chkSymsExpr("transaction @* {} ( _ = .tx_data )", *tx, *txData)
        chkSymsExpr("transaction @* {} ( _ = .block )", *tx, *block)

        chkSymsExpr("get_tx().tx_rid", *txRid)
        chkSymsExpr("get_tx().tx_hash", *txHash)
        chkSymsExpr("get_tx().tx_data", *txData)
        chkSymsExpr("get_tx().block", *block)
    }

    @Test fun testFunctionParameters() {
        chkSymsExpr("abs(a = 123)", "a=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|a|a: [integer]")
        chkSymsExpr("min(a = 123, b = 456L)",
            "a=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|a|a: [big_integer]",
            "b=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|b|b: [big_integer]",
        )

        chkSymsExpr("require(value = true)", "value=EXPR_CALL_ARG|-|-",
            "?doc=PARAMETER|value|@implies(TRUE) value: [boolean]")
        chkSymsExpr("require(value = true, message = 'hello')",
            "value=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|value|@implies(TRUE) value: [boolean]",
            "message=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|message|<lazy> <zero_one> message: [text]",
        )

        chkSymsExpr("integer(value = '123')", "value=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|value|value: [text]")
        chkSymsExpr("integer(value = '123', radix = 10)",
            "value=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|value|value: [text]",
            "radix=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|radix|<zero_one> radix: [integer]",
        )

        chkSymsExpr("'hello'.sub(start = 2)", "start=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|start|start: [integer]")
        chkSymsExpr("'hello'.sub(start = 2, end = 3)",
            "start=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|start|start: [integer]",
            "end=EXPR_CALL_ARG|-|-", "?doc=PARAMETER|end|end: [integer]",
        )
    }
}
