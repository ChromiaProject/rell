/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import kotlin.test.Test

class IdeSymbolLibExtensionTest: BaseIdeSymbolTest() {
    @Test fun testOpContextStructOpExt() {
        file("module.rell", "operation op(x: integer) {}")
        chkSymsExpr("struct<op>(123).to_gtx_operation()",
            "to_gtx_operation=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:op_context.struct_op_ext.to_gtx_operation|<function> to_gtx_operation(): [gtx_operation]",
        )
    }

    @Test fun testRellTestExts() {
        tst.testLib = true
        file("module.rell", "operation op(x: integer) {}")

        chkSymsExpr("struct<op>(123).to_test_op()",
            "to_test_op=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell.test:rell.test.struct_op_ext.to_test_op|<function> to_test_op(): [rell.test.op]",
        )

        chkSymsExpr("gtx_operation(name = 'op', args = []).to_test_op()",
            "to_test_op=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell.test:rell.test.gtx_operation_ext.to_test_op|<function> to_test_op(): [rell.test.op]",
        )
    }

    @Test fun testEntityObjectExts() {
        file("module.rell", "entity data {} object state {}")
        chkSymsExpr("(data@{}).to_struct()",
            "to_struct=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.entity_ext.to_struct|<function> to_struct(...)",
        )
        chkSymsExpr("(data@{}).to_mutable_struct()",
            "to_mutable_struct=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.entity_ext.to_mutable_struct|<function> to_mutable_struct(...)",
        )
        chkSymsExpr("state.to_struct()",
            "to_struct=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.object_ext.to_struct|<function> to_struct(...)",
        )
        chkSymsExpr("state.to_mutable_struct()",
            "to_mutable_struct=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.object_ext.to_mutable_struct|<function> to_mutable_struct(...)",
        )
    }

    @Test fun testEnumExt() {
        file("module.rell", "enum colors { red, green, blue }")
        chkSymsExpr("colors.red.name",
            "name=MEM_SYS_PROPERTY_PURE|-|-", "?doc=PROPERTY|rell:rell.enum_ext.name|<pure> name: [text]")
        chkSymsExpr("colors.red.value",
            "value=MEM_SYS_PROPERTY_PURE|-|-", "?doc=PROPERTY|rell:rell.enum_ext.value|<pure> value: [integer]")
        chkSymsExpr("colors.values()",
            "values=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.enum_ext.values|<pure> <static> <function> values(): [list]<[T]>",
        )
        chkSymsExpr("colors.value(0)",
            "value=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.enum_ext.value|<pure> <static> <function> value(\n\tvalue: [integer]\n): [T]",
        )
        chkSymsExpr("colors.value('red')",
            "value=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.enum_ext.value|<pure> <static> <function> value(\n\tname: [text]\n): [T]",
        )
    }

    @Test fun testGtvExt() {
        chkSymsExpr("text.from_gtv(gtv.from_bytes(x''))",
            "from_gtv=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.gtv_ext.from_gtv|<pure> <static> <function> from_gtv(\n\tgtv: [gtv]\n): [T]",
        )
        chkSymsExpr("text.from_gtv_pretty(gtv.from_bytes(x''))",
            "from_gtv_pretty=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.gtv_ext.from_gtv_pretty|<pure> <static> <function> from_gtv_pretty(\n\tgtv: [gtv]\n): [T]",
        )
        chkSymsExpr("''.hash()",
            "hash=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.gtv_ext.hash|<pure> <function> hash(): [byte_array]",
        )
        chkSymsExpr("''.to_gtv()",
            "to_gtv=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.gtv_ext.to_gtv|<pure> <function> to_gtv(): [gtv]",
        )
        chkSymsExpr("''.to_gtv_pretty()",
            "to_gtv_pretty=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.gtv_ext.to_gtv_pretty|<pure> <function> to_gtv_pretty(): [gtv]",
        )
    }

    @Test fun testStructExt() {
        file("module.rell", "struct data { x: integer; }")
        chkSymsExpr("data(123).to_bytes()",
            "to_bytes=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.struct_ext.to_bytes|<pure> <function> to_bytes(): [byte_array]",
        )
        chkSymsExpr("data.from_bytes(x'')",
            "from_bytes=DEF_FUNCTION_SYSTEM|-|-",
            "?doc=FUNCTION|rell:rell.struct_ext.from_bytes|<pure> <static> <function> from_bytes(\n\tbytes: [byte_array]\n): [T]",
        )
    }
}
