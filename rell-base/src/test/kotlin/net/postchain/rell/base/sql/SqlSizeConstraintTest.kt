package net.postchain.rell.base.sql

import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.sql.SqlUtils.getExistingSizeConstraints
import net.postchain.rell.base.utils.immMapOf
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SqlSizeConstraintTest: BaseSqlInitTest() {
    @Test fun testSizeConstraints() {
        chkSizeConstraints("user", "entity user { name; }", immMapOf())
        chkSizeConstraints("user", "entity user { @min_size(9) name; }", immMapOf("name" to (9L to null)))
        chkSizeConstraints("user", "entity user { @max_size(8) code: text; }", immMapOf("code" to (null to 8L)))
        chkSizeConstraints("user", "entity user { @size(10) hash: byte_array; }", immMapOf("hash" to (10L to 10L)))
        chkSizeConstraints("user", "entity user { @size(11, 12) foo: byte_array; }", immMapOf("foo" to (11L to 12L)))

        chkSizeConstraints("bar", "entity bar { x: integer; @size(11, 12) foo: byte_array; }",
            immMapOf("foo" to (11L to 12L)))
        chkSizeConstraints("bar", "entity bar { @min_size(3) x: text; @size(11, 12) foo: byte_array; }",
            immMapOf("foo" to (11L to 12L), "x" to (3L to null)))
    }

    private fun chkSizeConstraints(defName: String, code: String, expected: Map<String, Pair<Long?, Long?>>) {
        tstCtx.sqlMgr().transaction { SqlUtils.dropAll(it, false) }
        chkInit(code)
        val table = "c0.$defName"
        val actual = tstCtx.sqlMgr().access { getExistingSizeConstraints(it, table) }
        assertEquals(expected, actual)
    }

    @Test fun testConstraintSameDefinitionMultipleNamespaceNoConflict() {
        chkInit("""
            namespace a { entity user { @size(1) name; } }
            namespace b { entity user { @size(2) name; } }
        """)
        assertEquals(immMapOf("name" to (1L to 1L)),
            tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.a.user") })
        assertEquals(immMapOf("name" to (2L to 2L)),
            tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.b.user") })
    }

    @Test fun testMinSizeConstraintAddedEntityNoRecords() {
        chkInit("entity user { name; }")
        chkInit("entity user { @min_size(9) name; }")

        chkOp("create user('Alex');", "rt_err:entity:user:attribute:name:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Alex'")
    }

    @Test fun testMaxSizeConstraintAddedEntityNoRecords() {
        chkInit("entity user { name; }")
        chkInit("entity user { @max_size(9) name; }")

        chkOp("create user('Wayne Rooney');", "rt_err:entity:user:attribute:name:validator:size:too_large");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Wayne Rooney'")
    }

    @Test fun testFixedSizeConstraintAddedEntityNoRecords() {
        chkInit("entity user { name; }")
        chkInit("entity user { @size(9) name; }")

        chkOp("create user('Michelangelo');", "rt_err:entity:user:attribute:name:validator:size:too_large");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Michelangelo'")
    }

    @Test fun testMinMaxSizeConstraintAddedEntityNoRecords() {
        chkInit("entity user { name; }")
        chkInit("entity user { @min_size(9) @max_size(11) name; }")

        chkOp("create user('Anton');", "rt_err:entity:user:attribute:name:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Anton'")
    }

    @Test fun testMinSizeConstraintChangedEntityNoRecords() {
        chkInit("entity user { @min_size(8) name; }")
        chkInit("entity user { @min_size(9) name; }")

        chkOp("create user('Iaroslav');", "rt_err:entity:user:attribute:name:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Iaroslav'")
    }

    @Test fun testMaxSizeConstraintChangedEntityNoRecords() {
        chkInit("entity user { @max_size(10) name; }")
        chkInit("entity user { @max_size(9) name; }")

        chkOp("create user('Mithrandir');", "rt_err:entity:user:attribute:name:validator:size:too_large");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Mithrandir'")
    }

    @Test fun testFixedSizeConstraintChangedEntityNoRecords() {
        chkInit("entity user { @size(3) name; }")
        chkInit("entity user { @size(9) name; }")

        chkOp("create user('Sam');", "rt_err:entity:user:attribute:name:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Sam'")
    }

    @Test fun testMinMaxSizeConstraintChangedEntityNoRecords() {
        chkInit("entity user { @min_size(9) @max_size(11) name; }")
        chkInit("entity user { @min_size(10) @max_size(10) name; }")

        chkOp("create user('Alexander');", "rt_err:entity:user:attribute:name:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Alexander'")
    }

    @Test fun testMinSizeConstraintAddedEntityWithRecords() {
        chkInit("entity user { name; }")
        insert("c0.user", "name", "200,'Lobelia Sackville-Baggins'")
        chkInit("entity user { @min_size(9) name; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:name")
    }

    @Test fun testMaxSizeConstraintAddedEntityWithRecords() {
        chkInit("entity user { name; }")
        insert("c0.user", "name", "200,'Lotho'")
        chkInit("entity user { @max_size(9) name; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:name")
    }

    @Test fun testFixedSizeConstraintAddedEntityWithRecords() {
        chkInit("entity user { name; }")
        insert("c0.user", "name", "200,'Wormtongue'")
        chkInit("entity user { @size(10) name; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:name")
    }

    @Test fun testMinMaxSizeConstraintAddedEntityWithRecords() {
        chkInit("entity user { name; }")
        insert("c0.user", "name", "200,'Sharkey'")
        chkInit("entity user { @min_size(5) @max_size(10) name; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:name")
    }

    @Test fun testMinSizeConstraintWidenedEntityWithRecords() {
        chkInit("entity user { @min_size(9) name; }")
        insert("c0.user", "name", "200,'Glorfindel'")
        chkInit("entity user { @min_size(8) name; }")
    }

    @Test fun testMaxSizeConstraintWidenedEntityWithRecords() {
        chkInit("entity user { @max_size(9) name; }")
        insert("c0.user", "name", "200,'Elrohir'")
        chkInit("entity user { @max_size(10) name; }")
    }

    @Test fun testFixedSizeConstraintWidenedEntityWithRecords() {
        chkInit("entity user { @size(9) name; }")
        insert("c0.user", "name", "200,'Galadriel'")
        chkInit("entity user { @size(8, 10) name; }")
    }

    @Test fun testMinMaxSizeConstraintWidenedEntityWithRecords() {
        chkInit("entity user { @min_size(9) @max_size(11) name; }")
        insert("c0.user", "name", "200,'Mithrandir'")
        chkInit("entity user { @min_size(8) @max_size(12) name; }")
    }

    @Test fun testMinSizeConstraintNarrowedEntityWithRecords() {
        chkInit("entity user { @min_size(9) name; }")
        insert("c0.user", "name", "200,'Glorfindel'")
        chkInit("entity user { @min_size(10) name; }",
            "rt_err:dbinit:attr:min_size_constraint_increased_records_exist:user:name")
    }

    @Test fun testMaxSizeConstraintNarrowedEntityWithRecords() {
        chkInit("entity user { @max_size(9) name; }")
        insert("c0.user", "name", "200,'Elrohir'")
        chkInit("entity user { @max_size(8) name; }",
            "rt_err:dbinit:attr:max_size_constraint_decreased_records_exist:user:name")
    }

    @Test fun testFixedSizeConstraintNarrowedEntityWithRecords() {
        chkInit("entity user { @size(9) name; }")
        insert("c0.user", "name", "200,'Galadriel'")
        chkInit("entity user { @size(8) name; }",
            "rt_err:dbinit:attr:max_size_constraint_decreased_records_exist:user:name")
        chkInit("entity user { @size(10) name; }",
            "rt_err:dbinit:attr:min_size_constraint_increased_records_exist:user:name")
    }

    @Test fun testMinMaxSizeConstraintNarrowedEntityWithRecords() {
        chkInit("entity user { @min_size(9) @max_size(11) name; }")
        insert("c0.user", "name", "200,'Mithrandir'")
        chkInit("entity user { @min_size(10) @max_size(11) name; }",
            "rt_err:dbinit:attr:min_size_constraint_increased_records_exist:user:name")
        chkInit("entity user { @min_size(9) @max_size(10) name; }",
            "rt_err:dbinit:attr:max_size_constraint_decreased_records_exist:user:name")
    }

    @Test fun testMinSizeConstraintByteArrayAddedEntityNoRecords() {
        chkInit("entity user { foo: byte_array; }")
        chkInit("entity user { @min_size(9) foo: byte_array; }")

        chkOp("create user(x'1122');", "rt_err:entity:user:attribute:foo:validator:size:too_small");
        chkInsertViolatesCheckConstraint("c0.user", "foo", "200,'\\x1122'")
    }

    @Test fun testMaxSizeConstraintByteArrayChangedEntityNoRecords() {
        chkInit("entity user { @max_size(10) foo: byte_array; }")
        chkInit("entity user { @max_size(9) foo: byte_array; }")

        chkOp("create user(x'11223344556677889900');", "rt_err:entity:user:attribute:foo:validator:size:too_large");
        chkInsertViolatesCheckConstraint("c0.user", "foo", "200,'\\x11223344556677889900'")
    }

    @Test fun testFixedSizeConstraintByteArrayAddedEntityWithRecords() {
        chkInit("entity user { foo: byte_array; }")
        insert("c0.user", "foo", "200,'Wormtongue'")
        chkInit("entity user { @size(10) foo: byte_array; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:foo")
    }

    @Test fun testMinMaxSizeConstraintByteArrayWidenedEntityWithRecords() {
        chkInit("entity user { @min_size(9) @max_size(11) foo: byte_array; }")
        insert("c0.user", "foo", "200,'Mithrandir'")
        chkInit("entity user { @min_size(8) @max_size(12) foo: byte_array; }")
    }

    @Test fun testMinSizeConstraintByteArrayNarrowedEntityWithRecords() {
        chkInit("entity user { @min_size(9) foo: byte_array; }")
        insert("c0.user", "foo", "200,'Glorfindel'")
        chkInit("entity user { @min_size(10) foo: byte_array; }",
            "rt_err:dbinit:attr:min_size_constraint_increased_records_exist:user:foo")
    }

    @Test fun testMinSizeConstraintAddedObject() {
        chkInit("object user { name = 'Ernest Shackleton'; }")
        chkInit("object user { @min_size(9) name = 'Ernest Shackleton'; }",
            "rt_err:dbinit:attr:size_constraint_added_records_exist:user:name")
    }

    @Test fun testMaxSizeConstraintChangedObject() {
        chkInit("object user { @max_size(10) name = 'F. Drake'; }")
        chkInit("object user { @max_size(9) name = 'F. Drake'; }",
            "rt_err:dbinit:attr:max_size_constraint_decreased_records_exist:user:name")
    }

    @Test fun testConstraintRemovedWhenColumnRemoved() {
        chkInit("entity user { @min_size(9) name; age: integer; }")
        chkInit("entity user { age: integer; }")
        assertEquals(immMapOf(), tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.user") })
    }

    @Test fun testConstraintOnAddedColumn() {
        chkInit("entity user { age: integer; }")
        chkInit("entity user { @min_size(9) name; age: integer; }")
        assertEquals(immMapOf("name" to (9L to null)),
            tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.user") })
    }

    @Test fun testConstraintRemovedEntityFixedSizeText() {
        chkInit("entity user { @size(9) name; }")
        chkInit("entity user { name; }")

        chkOp("create user('Keith');", "OK")
        insert("c0.user", "name", "200,'Fred'")
        chkData("c0.user(1,Keith)", "c0.user(200,Fred)")
    }

    @Test fun testConstraintRemovedEntityMaxSizeByteArray() {
        chkInit("entity user { @max_size(2) data: byte_array; }")
        chkInit("entity user { data: byte_array; }")

        chkOp("create user(x'00112233445566778899');", "OK")
        insert("c0.user", "data", "200,'\\x99887766554433221100'")
        chkData("c0.user(1,0x00112233445566778899)", "c0.user(200,0x99887766554433221100)")
    }

    @Test fun testConstraintRemovedObjectMinSizeByteArray() {
        chkInit("object user { mutable @max_size(2) data: byte_array = x'0011'; }")
        chkInit("object user { mutable data: byte_array = x'0011'; }")
        chkOp("{ user.data = x'001122'; }", "OK")
    }

    @Test fun testConstraintRemovedObjectMinMaxSizeText() {
        chkInit("object user { mutable @min_size(2) @max_size(5) name = 'Alex'; }")
        chkInit("object user { mutable name = 'Alex'; }")
        chkOp("{ user.name = 'Federico'; }", "OK")
    }

    @Test fun testConstraintWeakenedEntityMinSizeText() {
        chkInit("entity user { @min_size(5) name; }")
        chkOp("create user('Benjamin');", "OK")
        chkOp("create user('Phil');", "rt_err:entity:user:attribute:name:validator:size:too_small")
        chkInsertViolatesCheckConstraint("c0.user", "name", "200,'Phil'")
        chkInit("entity user { @min_size(3) name; }")
        chkOp("create user('Phil');", "OK")
        insert("c0.user", "name", "200,'Tom'")
        chkData("c0.user(1,Benjamin)", "c0.user(2,Phil)", "c0.user(200,Tom)")
    }

    @Test fun testConstraintWeakenedEntityMaxSizeByteArray() {
        chkInit("entity user { @max_size(4) data: byte_array; }")
        chkOp("create user(x'0011');", "OK")
        chkOp("create user(x'00112233445566778899');", "rt_err:entity:user:attribute:data:validator:size:too_large")
        chkInsertViolatesCheckConstraint("c0.user", "data", "200,'\\x00112233445566778899'")
        chkInit("entity user { @max_size(12) data: byte_array; }")
        chkOp("create user(x'00112233445566778899');", "OK")
        insert("c0.user", "data", "200,'\\x99887766554433221100'")
        chkData("c0.user(1,0x0011)", "c0.user(2,0x00112233445566778899)", "c0.user(200,0x99887766554433221100)")
    }

    @Test fun testConstraintWeakenedObjectFixedSizeText() {
        chkInit("object user { mutable @size(5) name = 'James'; }")
        chkOp("{ user.name = 'Tim'; }", "rt_err:object:user:attribute:name:validator:size:too_small")
        chkInit("object user { mutable @size(4, 6) name = 'James'; }")
        chkOp("{ user.name = 'Jeremy'; }", "OK")
        assertEquals(immMapOf("name" to (4L to 6L)),
            tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.user") })
    }

    @Test fun testConstraintWeakenedObjectMinMaxSizeByteArray() {
        chkInit("object user { mutable @min_size(2) @max_size(4) data: byte_array = x'001122'; }")
        chkOp("{ user.data = x'00112233445566778899'; }", "rt_err:object:user:attribute:data:validator:size:too_large")
        chkInit("object user { mutable @min_size(1) @max_size(12) data: byte_array = x'001122'; }")
        chkOp("{ user.data = x'00112233445566778899'; }", "OK")
        assertEquals(immMapOf("data" to (1L to 12L)),
            tstCtx.sqlMgr().access { getExistingSizeConstraints(it, "c0.user") })
    }

    @Test fun testConstraintLongConstraintName() {
        chkInit("""
            entity an_entity_with_size_constrained_attributes {
                @size(5) a_size_constrained_entity_attribute_MAYBE_TRUNCATED_NAME: text;
                @size(2) a_size_constrained_entity_attribute_OTHER_TRUNCATED_NAME: byte_array;
            }
        """)
        chkOp("create an_entity_with_size_constrained_attributes('ABCDE', x'1234');", "OK")
        chkOp("create an_entity_with_size_constrained_attributes('ABCD', x'1234');",
            "rt_err:entity:an_entity_with_size_constrained_attributes:attribute:" +
            "a_size_constrained_entity_attribute_MAYBE_TRUNCATED_NAME:validator:size:too_small")
        chkOp("create an_entity_with_size_constrained_attributes('ABCDE', x'123456');",
            "rt_err:entity:an_entity_with_size_constrained_attributes:attribute:" +
            "a_size_constrained_entity_attribute_OTHER_TRUNCATED_NAME:validator:size:too_large")
    }

    private fun chkInsertViolatesCheckConstraint(table: String, columns: String, values: String) {
        assertThrows<Rt_Exception>("violates check constraint") { insert(table, columns, values) }
    }
}