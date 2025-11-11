/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestContext
import kotlin.test.Test

class SqlInitTest: BaseSqlInitTest() {
    @Test fun testNoMeta() {
        chkTables()
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer")
        chkColumns("c0.user(name:text,rowid:int8,score:int8)")
    }

    @Test fun testBrokenMetaNoAttrTable() {
        chkInit("")
        chkTables("c0.sys.attributes", "c0.sys.classes")

        execSql("""DROP TABLE "c0.sys.attributes";""")
        chkTables("c0.sys.classes")

        chkInit("", "rt_err:meta:notables:c0.sys.attributes")
        chkTables("c0.sys.classes")
    }

    @Test fun testBrokenMetaNoEntityTable() {
        chkInit("")
        chkTables("c0.sys.attributes", "c0.sys.classes")

        execSql("""DROP TABLE "c0.sys.classes";""")
        chkTables("c0.sys.attributes")

        chkInit("", "rt_err:meta:notables:c0.sys.classes")
        chkTables("c0.sys.attributes")
    }

    @Test fun testBrokenMetaBadEntityTable() {
        val table = "c0.sys.classes"
        val id = """"id" INT NOT NULL PRIMARY KEY"""
        val name = """"name" TEXT NOT NULL UNIQUE"""
        val type = """"type" TEXT NOT NULL"""
        val log = """"log" BOOLEAN NOT NULL"""

        chkBrokenMetaTable(table, listOf(id, name, type, log), "OK")

        chkBrokenMetaTable(table, listOf(id, name, type, log, "foo INT NOT NULL"), "rt_err:meta:extracols:c0.sys.classes:foo")

        val err = "rt_err:meta:coltype:c0.sys.classes"
        chkBrokenMetaTable(table, listOf(""""id" SMALLINT NOT NULL PRIMARY KEY""", name, type, log), "$err:id:int4:int2")
        chkBrokenMetaTable(table, listOf(id, """"name" INT NOT NULL""", type, log), "$err:name:text:int4")
        chkBrokenMetaTable(table, listOf(id, name, """"type" INT NOT NULL""", log), "$err:type:text:int4")
        chkBrokenMetaTable(table, listOf(id, name, type, """"log" TEXT NOT NULL"""), "$err:log:bool:text")

        chkBrokenMetaTable(table, listOf(name, type, log), "rt_err:meta:nocols:c0.sys.classes:id")
        chkBrokenMetaTable(table, listOf(id, type, log), "rt_err:meta:nocols:c0.sys.classes:name")
        chkBrokenMetaTable(table, listOf(id, name, log), "rt_err:meta:nocols:c0.sys.classes:type")
        chkBrokenMetaTable(table, listOf(id, name, type), "rt_err:meta:nocols:c0.sys.classes:log")
        chkBrokenMetaTable(table, listOf(id), "rt_err:meta:nocols:c0.sys.classes:name,type,log")
    }

    @Test fun testBrokenMetaBadAttrTable() {
        val table = "c0.sys.attributes"
        val classId = """"class_id" INT NOT NULL"""
        val name = """"name" TEXT NOT NULL"""
        val type = """"type" TEXT NOT NULL"""

        chkBrokenMetaTable(table, listOf(classId, name, type), "OK")

        chkBrokenMetaTable(table, listOf(classId, name, type, "foo INT NOT NULL"), "rt_err:meta:extracols:c0.sys.attributes:foo")

        val err = "rt_err:meta:coltype:c0.sys.attributes"
        chkBrokenMetaTable(table, listOf(""""class_id" TEXT NOT NULL""", name, type), "$err:class_id:int4:text")
        chkBrokenMetaTable(table, listOf(classId, """"name" INT NOT NULL""", type), "$err:name:text:int4")
        chkBrokenMetaTable(table, listOf(classId, name, """"type" INT NOT NULL"""), "$err:type:text:int4")

        chkBrokenMetaTable(table, listOf(name, type), "rt_err:meta:nocols:c0.sys.attributes:class_id")
        chkBrokenMetaTable(table, listOf(classId, type), "rt_err:meta:nocols:c0.sys.attributes:name")
        chkBrokenMetaTable(table, listOf(classId, name), "rt_err:meta:nocols:c0.sys.attributes:type")
        chkBrokenMetaTable(table, listOf(classId), "rt_err:meta:nocols:c0.sys.attributes:name,type")
    }

    private fun chkBrokenMetaTable(table: String, columns: List<String>, expected: String) {
        tstCtx.sqlMgr().transaction { sqlExec ->
            SqlUtils.dropAll(sqlExec, true)
        }

        chkInit("")
        execSql("""DROP TABLE "$table";""")

        val cols = columns.joinToString()
        val sql = """CREATE TABLE "$table" ($cols);"""
        execSql(sql)

        chkInit("", expected)
    }

    @Test fun testBrokenMetaAttrNoEntity() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text")

        execSql("""DELETE FROM "c0.sys.classes";""")
        chkAll("", "0,name,sys:text")

        chkInit("entity user { name; }", "rt_err:meta:attr_no_entity:0")
        chkAll("", "0,name,sys:text")
    }

    @Test fun testBrokenMetaBadEntityType() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        execSql("""UPDATE "c0.sys.classes" SET "type" = 'foo';""")
        chkAll("0,user,foo,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        chkInit("entity user { name; }", "rt_err:meta:entity:bad_type:0:user:foo")
        chkAll("0,user,foo,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testBrokenMetaEntityNoTable() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        execSql("""DROP TABLE "c0.user";""")
        chkAll("0,user,class,false", "0,name,sys:text", "")

        chkInit("entity user { name; }", "rt_err:meta:no_data_tables:c0.user")
        chkAll("0,user,class,false", "0,name,sys:text", "")

        chkInit("", "rt_err:meta:no_data_tables:c0.user")
        chkAll("0,user,class,false", "0,name,sys:text", "")
    }

    @Test fun testTableNoMetaEntity() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        execSql("""DELETE FROM "c0.sys.attributes";""")
        execSql("""DELETE FROM "c0.sys.classes";""")
        chkAll("", "", "c0.user(name:text,rowid:int8)")

        chkInit("entity user { name; }", "rt_err:meta:no_meta_entities:c0.user")
        chkAll("", "", "c0.user(name:text,rowid:int8)")

        chkInit("", "rt_err:meta:no_meta_entities:c0.user")
        chkAll("", "", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testTableNoMetaEntity2() {
        chkInit("")
        chkAll("", "", "")

        execSql("""CREATE TABLE "c0.foobar"("rowid" BIGINT NOT NULL PRIMARY KEY, "value" BIGINT NOT NULL);""")
        chkInit("", "rt_err:meta:no_meta_entities:c0.foobar")

        execSql("""DROP TABLE "c0.foobar";""")
        chkInit("")

        execSql("""CREATE TABLE "c0.sys.foobar"("rowid" BIGINT NOT NULL PRIMARY KEY, "value" BIGINT NOT NULL);""")
        chkInit("")
    }

    @Test fun testTableNoMetaTables() {
        tstCtx.init() // drop all tables

        execSql("""CREATE TABLE "c0.user" ("rowid" BIGINT NOT NULL PRIMARY KEY, "name" TEXT NOT NULL);""")
        chkAll("NO_TABLE", "NO_TABLE", "c0.user(name:text,rowid:int8)")

        chkInit("entity user { name; }", "rt_err:meta:no_meta_entities:c0.user")
        chkAll("NO_TABLE", "NO_TABLE", "c0.user(name:text,rowid:int8)")

        chkInit("", "rt_err:meta:no_meta_entities:c0.user")
        chkAll("NO_TABLE", "NO_TABLE", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testMetaAttrNoColumn() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        execSql("""ALTER TABLE "c0.user" DROP COLUMN "score";""")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8)")

        chkInit("entity user { name; score: integer; }", "rt_err:meta:no_data_columns:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8)")

        chkInit("entity user { name; }", "rt_err:meta:no_data_columns:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8)")

        chkInit("", "rt_err:meta:no_data_columns:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testColumnNoMetaAttr() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        execSql("""DELETE FROM "c0.sys.attributes" WHERE name = 'score';""")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("entity user { name; score: integer; }", "rt_err:meta:no_meta_attrs:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("entity user { name; }", "rt_err:meta:no_meta_attrs:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("", "rt_err:meta:no_meta_attrs:c0.user:score")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,score:int8)")
    }

    @Test fun testNoRowid() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        execSql("""ALTER TABLE "c0.user" DROP COLUMN "rowid";""")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,score:int8)")

        chkInit("entity user { name; score: integer; }", "rt_err:meta:no_data_columns:c0.user:rowid")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,score:int8)")

        chkInit("", "rt_err:meta:no_data_columns:c0.user:rowid")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,score:int8)")
    }

    @Test fun testMetaEntityNoCode() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("", "OK", "dbinit:no_code:ENTITY:user")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
    }

    @Test fun testMetaAttrNoCode() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        insert("c0.user", "name,score", "100,'Bob',123")
        chkData("c0.user(100,Bob,123)")

        chkInit("entity user { name; }", "OK", "dbinit:no_code:attrs:user:score")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.user(100,Bob,123)")
    }

    @Test fun testMetaEntityCodeDiff() {
        chkInit("@log entity user { name; }")
        chkAll("0,user,class,true", "0,name,sys:text 0,transaction,class:0:transaction")
        chkColumns("c0.user(name:text,rowid:int8,transaction:int8)")

        chkInit("entity user { name; }", "rt_err:meta:entity:diff_log:user:true:false")
        chkAll("0,user,class,true", "0,name,sys:text 0,transaction,class:0:transaction")
        chkColumns("c0.user(name:text,rowid:int8,transaction:int8)")
    }

    @Test fun testMetaEntityCodeDiff2() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        chkInit("@log entity user { name; }", "rt_err:meta:entity:diff_log:user:false:true")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testMetaAttrCodeDiff() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("entity user { name; score: text; }", "rt_err:meta:attr:diff_type:user:score:sys:integer:sys:text")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
    }

    @Test fun testAddEntity() {
        chkInit("entity company { address: text; }")
        chkAll("0,company,class,false", "0,address,sys:text", "c0.company(address:text,rowid:int8)")

        insert("c0.company", "address", "0,'Stockholm'")
        chkData("c0.company(0,Stockholm)")

        chkInit("entity company { address: text; } entity user { name; }")
        chkAll("0,company,class,false 1,user,class,false", "0,address,sys:text 1,name,sys:text")
        chkColumns("c0.company(address:text,rowid:int8)", "c0.user(name:text,rowid:int8)")
        chkData("c0.company(0,Stockholm)")
    }

    @Test fun testAddObject() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        insert("c0.user", "name,score", "0,'Bob',123")
        chkData("c0.user(0,Bob,123)")

        chkInit("entity user { name; score: integer; } object state { mutable value: integer = 456; }")
        chkAll("0,user,class,false 1,state,object,false", "0,name,sys:text 0,score,sys:integer 1,value,sys:integer")
        chkColumns("c0.state(rowid:int8,value:int8)", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.state(0,456)", "c0.user(0,Bob,123)")
    }

    @Test fun testAddObject2() {
        chkInit("object foo { mutable f: integer = 123; }")
        chkAll("0,foo,object,false", "0,f,sys:integer", "c0.foo(f:int8,rowid:int8)")
        chkData("c0.foo(0,123)")

        chkInit("object foo { mutable f: integer = 123; } object bar { mutable b: integer = 456; }")
        chkAll("0,foo,object,false 1,bar,object,false", "0,f,sys:integer 1,b,sys:integer")
        chkColumns("c0.bar(b:int8,rowid:int8)", "c0.foo(f:int8,rowid:int8)")
        chkData("c0.bar(0,456)", "c0.foo(0,123)")
    }

    @Test fun testAddObject3() {
        chkInit("")
        chkAll("", "", "")
        chkData()

        chkInit("object foo { mutable f: integer = 123; } object bar { mutable b: integer = foo.f + 456; }")
        chkAll("0,foo,object,false 1,bar,object,false", "0,f,sys:integer 1,b,sys:integer")
        chkColumns("c0.bar(b:int8,rowid:int8)", "c0.foo(f:int8,rowid:int8)")
        chkData("c0.bar(0,579)", "c0.foo(0,123)")
    }

    @Test fun testAddObjectEntity() {
        chkInit("object state { mutable value: integer = 123; }")
        chkAll("0,state,object,false", "0,value,sys:integer", "c0.state(rowid:int8,value:int8)")
        chkData("c0.state(0,123)")

        execSql("""UPDATE "c0.state" SET value = 456;""")
        chkData("c0.state(0,456)")

        chkInit("object state { mutable value: integer = 123; } entity user { name; score: integer; }")
        chkAll("0,state,object,false 1,user,class,false", "0,value,sys:integer 1,name,sys:text 1,score,sys:integer")
        chkColumns("c0.state(rowid:int8,value:int8)", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.state(0,456)")
    }

    @Test fun testMetaEntityCodeObject() {
        chkInit("entity user { name; score: integer; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")

        chkInit("object user { mutable name = 'Bob'; mutable score: integer = 123; }", "rt_err:meta:entity:diff_type:user:ENTITY:OBJECT")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
    }

    @Test fun testMetaObjectCodeEntity() {
        chkInit("object state { mutable value: integer = 123; }")
        chkAll("0,state,object,false", "0,value,sys:integer", "c0.state(rowid:int8,value:int8)")
        chkData("c0.state(0,123)")

        chkInit("entity state { value: integer; }", "rt_err:meta:entity:diff_type:state:OBJECT:ENTITY")
        chkAll("0,state,object,false", "0,value,sys:integer", "c0.state(rowid:int8,value:int8)")
        chkData("c0.state(0,123)")
    }

    @Test fun testAddEntityAttrSimpleType() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "101,'Alice'")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity user { name; score: integer; }", "rt_err:meta:attr:new_no_def_value:user:score")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity user { name; score: integer = -123; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.user(100,Bob,-123)", "c0.user(101,Alice,-123)")
    }

    @Test fun testAddEntityAttrDefValue() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "101,'Alice'")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity user { name; score: integer = 123; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.user(100,Bob,123)", "c0.user(101,Alice,123)")
    }

    @Test fun testAddEntityAttrDefValue2() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "101,'Alice'")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity company { name; } entity user { name; }")
        chkAll("0,user,class,false 1,company,class,false", "0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(name:text,rowid:int8)")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity company { name; } entity user { name; company: company = company @ { 'Microsoft' }; }",
                "rt_err:at:wrong_count:0")
        chkAll("0,user,class,false 1,company,class,false", "0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(name:text,rowid:int8)")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        insert("c0.company", "name", "200,'Apple'")
        insert("c0.company", "name", "201,'Microsoft'")
        chkData("c0.company(200,Apple)", "c0.company(201,Microsoft)", "c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity company { name; } entity user { name; company: company = company @ { 'Microsoft' }; }")
        chkAll("0,user,class,false 1,company,class,false", "0,company,class:0:company 0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(company:int8,name:text,rowid:int8)")
        chkData("c0.company(200,Apple)", "c0.company(201,Microsoft)", "c0.user(100,201,Bob)", "c0.user(101,201,Alice)")
    }

    @Test fun testAddEntityAttrNoDefValue() {
        chkInit("entity company { name; } entity user { name; }")
        chkAll("0,company,class,false 1,user,class,false", "0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(name:text,rowid:int8)")

        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "101,'Alice'")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")

        chkInit("entity company { name; } entity user { name; company; }", "rt_err:meta:attr:new_no_def_value:user:company")
        chkAll("0,company,class,false 1,user,class,false", "0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(name:text,rowid:int8)")
        chkData("c0.user(100,Bob)", "c0.user(101,Alice)")
    }

    @Test fun testAddEntityAttrNoDefValueNoRecords() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData()

        chkInit("entity company { name; } entity user { name; company; }")
        chkAll("0,user,class,false 1,company,class,false", "0,company,class:0:company 0,name,sys:text 1,name,sys:text")
        chkColumns("c0.company(name:text,rowid:int8)", "c0.user(company:int8,name:text,rowid:int8)")
        chkData()
    }

    @Test fun testAddEntityAttrKey() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", true)
        chkData()

        chkInit("entity user { name; key id: integer; }")
        chkAll(
            "0,user,class,false",
            "0,id,sys:integer 0,name,sys:text",
            "c0.user(id:int8,name:text,rowid:int8,key(rowid),key(id))",
            true,
        )
        chkData()
    }

    @Test fun testAddEntityAttrIndex() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", indexes = true)
        chkData()

        chkInit("entity user { name; index id: integer; }")
        chkAll(
            "0,user,class,false",
            "0,id,sys:integer 0,name,sys:text",
            "c0.user(id:int8,name:text,rowid:int8,key(rowid),index(id))",
            indexes = true,
        )
        chkData()
    }

    @Test fun testAddObjectAttr() {
        chkInit("object state { mutable foo: integer = 123; }")
        chkAll("0,state,object,false", "0,foo,sys:integer", "c0.state(foo:int8,rowid:int8)")
        chkData("c0.state(0,123)")

        chkInit("object state { mutable foo: integer = 123; mutable bar: text = 'Hello'; }")
        chkAll("0,state,object,false", "0,bar,sys:text 0,foo,sys:integer", "c0.state(bar:text,foo:int8,rowid:int8)")
        chkData("c0.state(0,Hello,123)")
    }

    @Test fun testAddObjectAttr2() {
        chkInit("object foo { mutable f: integer = 123; }")
        chkAll("0,foo,object,false", "0,f,sys:integer", "c0.foo(f:int8,rowid:int8)")
        chkData("c0.foo(0,123)")

        chkInit("object bar { mutable b: integer = 456; } object foo { mutable f: integer = 123; mutable p: integer = bar.b + 789; }")
        chkAll("0,foo,object,false 1,bar,object,false", "0,f,sys:integer 0,p,sys:integer 1,b,sys:integer")
        chkColumns("c0.bar(b:int8,rowid:int8)", "c0.foo(f:int8,p:int8,rowid:int8)")
        chkData("c0.bar(0,456)", "c0.foo(0,123,1245)")
    }

    @Test fun testAddMoreAttrs() {
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")

        insert("c0.user", "name", "100,'Bob'")
        chkData("c0.user(100,Bob)")

        chkInit("entity user { name; score: integer = 123; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer", "c0.user(name:text,rowid:int8,score:int8)")
        chkData("c0.user(100,Bob,123)")

        chkInit("entity user { name; score: integer = 123; u: integer = 456; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer 0,u,sys:integer")
        chkColumns("c0.user(name:text,rowid:int8,score:int8,u:int8)")
        chkData("c0.user(100,Bob,123,456)")

        chkInit("entity user { name; score: integer = 123; u: integer = 456; v: integer = 789; }")
        chkAll("0,user,class,false", "0,name,sys:text 0,score,sys:integer 0,u,sys:integer 0,v,sys:integer")
        chkColumns("c0.user(name:text,rowid:int8,score:int8,u:int8,v:int8)")
        chkData("c0.user(100,Bob,123,456,789)")
    }

    @Test fun testAddColumnToReferencedTable() {
        chkInit("entity company { name; } entity user { name; company; }")
        chkAll(
                "0,company,class,false 1,user,class,false",
                "0,name,sys:text 1,company,class:0:company 1,name,sys:text",
                "c0.company(name:text,rowid:int8) c0.user(company:int8,name:text,rowid:int8)"
        )

        insert("c0.company", "name", "100,'Apple'")
        insert("c0.user", "name,company", "200,'Steve',100")
        chkData("c0.company(100,Apple)", "c0.user(200,100,Steve)")

        chkInit("entity company { name; address: text = '?'; } entity user { name; company; }")
        chkAll(
                "0,company,class,false 1,user,class,false",
                "0,address,sys:text 0,name,sys:text 1,company,class:0:company 1,name,sys:text",
                "c0.company(address:text,name:text,rowid:int8) c0.user(company:int8,name:text,rowid:int8)"
        )
        chkData("c0.company(100,?,Apple)", "c0.user(200,100,Steve)")
    }

    @Test fun testAddColumnReference() {
        chkInit("entity company { name; } entity user { name; }")
        chkAll(
                "0,company,class,false 1,user,class,false",
                "0,name,sys:text 1,name,sys:text",
                "c0.company(name:text,rowid:int8) c0.user(name:text,rowid:int8)"
        )

        insert("c0.company", "name", "100,'Apple'")
        insert("c0.user", "name", "200,'Steve'")
        chkData("c0.company(100,Apple)", "c0.user(200,Steve)")

        chkInit("entity company { name; } entity user { name; company = company @ { 'Apple' }; }")
        chkAll(
                "0,company,class,false 1,user,class,false",
                "0,name,sys:text 1,company,class:0:company 1,name,sys:text",
                "c0.company(name:text,rowid:int8) c0.user(company:int8,name:text,rowid:int8)"
        )
        chkData("c0.company(100,Apple)", "c0.user(200,100,Steve)")

        chk("company @* {} ( .name )", "list<text>[text[Apple]]")
        chkOp("delete company @ { .name == 'Apple' };", "rt_err:sqlerr:0") // Foreign key constraint violation.
        chkOp("delete user @ { .name == 'Steve' };", "OK")
        chkOp("delete company @ { .name == 'Apple' };", "OK")
    }

    @Test fun testKeyChange() {
        chkKeyIndexChange("key", "index")
    }

    @Test fun testIndexChange() {
        chkKeyIndexChange("index", "key")
    }

    private fun chkKeyIndexChange(key: String, index: String) {
        chkKeyIndex("$key last_name, first_name;", "key(rowid),$key(last_name,first_name)")

        chkKeyIndex("$key first_name, last_name;", "key(rowid),$key(first_name,last_name)")
        chkKeyIndex("$key first_name;", "key(rowid),$key(first_name)")
        chkKeyIndex("$key last_name;", "key(rowid),$key(last_name)")
        chkKeyIndex("", "key(rowid)")
        chkKeyIndex("$key last_name, address;", "key(rowid),$key(last_name,address)")
        chkKeyIndex("$key last_name, first_name, address;", "key(rowid),$key(last_name,first_name,address)")
        chkKeyIndex("$key address, last_name, first_name;", "key(rowid),$key(address,last_name,first_name)")
        chkKeyIndex("$key year_ob, last_name, first_name;", "key(rowid),$key(year_ob,last_name,first_name)")

        chkKeyIndex("$index last_name, first_name;", "key(rowid),$index(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $key address;",
            "key(rowid),$key(address),$key(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $key address, year_ob;",
            "key(rowid),$key(address,year_ob),$key(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $key address, last_name;",
            "key(rowid),$key(address,last_name),$key(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $index address;",
            "key(rowid),$index(address),$key(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $index address, year_ob;",
            "key(rowid),$index(address,year_ob),$key(last_name,first_name)")
        chkKeyIndex("$key last_name, first_name; $index address, last_name;",
            "key(rowid),$index(address,last_name),$key(last_name,first_name)")
    }

    private fun chkKeyIndex(extra: String, expected: String) {
        val attrs = "first_name: text; last_name: text; address: text; year_ob: integer;"
        chkInit("entity user { $attrs $extra }")
        chkAll(
            "0,user,class,false",
            "0,address,sys:text 0,first_name,sys:text 0,last_name,sys:text 0,year_ob,sys:integer",
            "c0.user(address:text,first_name:text,last_name:text,rowid:int8,year_ob:int8,$expected)",
            indexes = true,
        )
    }

    @Test fun testKeyAddConflict() {
        chkInit("entity user { name; }")
        insert("c0.user", "name", "100,'Bob'")
        insert("c0.user", "name", "101,'Bob'")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", true)
        chkData("c0.user(100,Bob)", "c0.user(101,Bob)")

        chkInit("entity user { key name; }", "rt_err:sqlerr:0")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", true)
        chkData("c0.user(100,Bob)", "c0.user(101,Bob)")
    }

    @Test fun testKeyRemove() {
        chkInit("entity user { key name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid),key(name))", true)
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", true)
    }

    @Test fun testIndexRemove() {
        chkInit("entity user { index name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid),index(name))", true)
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8,key(rowid))", true)
    }

    @Test fun testDropAll() {
        RellTestContext().use { ctx ->
            val t = RellCodeTester(ctx)
            t.def("entity company { name; }")
            t.def("entity user { name; company; }")
            t.insert("c0.company", "name", "100,'Apple'")
            t.insert("c0.user", "name,company", "200,'Steve',100")
            t.chk("company @*{} ( .name )", "list<text>[text[Apple]]")
            t.chk("user @*{} ( .name )", "list<text>[text[Steve]]")
        }

        RellTestContext().use { ctx ->
            val t = RellCodeTester(ctx)
            t.def("entity company { name; boss: user; }")
            t.def("entity user { name; }")
            t.chk("company @*{} ( .name )", "list<text>[]")
            t.chk("user @*{} ( .name )", "list<text>[]")
        }
    }

    @Test fun testSysFunctions() {
        chkFunctions(listOf())
        chkInit("")

        val expectedFuns = listOf(
            "c0.make_rowid",
            "c0.make_rowids",
            "rell_biginteger_from_text",
            "rell_biginteger_power",
            "rell_bytea_substr1",
            "rell_bytea_substr2",
            "rell_decimal_from_text",
            "rell_decimal_to_text",
            "rell_integer_power",
            "rell_json_array_get",
            "rell_json_array_get_or_null",
            "rell_json_as_big_integer",
            "rell_json_as_big_integer_or_null",
            "rell_json_as_boolean_or_null",
            "rell_json_as_integer",
            "rell_json_as_integer_or_null",
            "rell_json_as_text",
            "rell_json_as_text_or_null",
            "rell_json_object_get",
            "rell_json_size",
            "rell_text_getchar",
            "rell_text_repeat",
            "rell_text_substr1",
            "rell_text_substr2",
        )

        chkFunctions(expectedFuns)

        execSql("DROP FUNCTION rell_bytea_substr2; DROP FUNCTION rell_text_substr1;")
        chkFunctions(expectedFuns.filter { it !in listOf("rell_text_substr1", "rell_bytea_substr2") })

        chkInit("")
        chkFunctions(expectedFuns)
    }
}
