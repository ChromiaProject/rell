/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import kotlin.test.Test

class SqlInitVersionControlTest: BaseSqlInitTest() {
    @Test fun testAddEntityAttrKey() {
        compatibility = "0.13.8"
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData()

        chkInit("entity user { name; key id: integer; }",
            "rt_err:meta:attr:new_key:user:id,dbinit:index_diff:user:code:key:id")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData()
    }

    @Test fun testAddEntityAttrIndex() {
        compatibility = "0.13.8"
        chkInit("entity user { name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData()

        chkInit("entity user { name; index id: integer; }",
            "rt_err:meta:attr:new_index:user:id,dbinit:index_diff:user:code:index:id")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkData()
    }

    @Test fun testKeyChange() {
        chkKeyIndexChange("key", "index")
    }

    @Test fun testIndexChange() {
        chkKeyIndexChange("index", "key")
    }

    private fun chkKeyIndexChange(key: String, index: String) {
        compatibility = "0.13.8"
        val attrs = "first_name: text; last_name: text; address: text; year_ob: integer;"
        chkKeyIndex(attrs, "$key last_name, first_name;", "OK")

        val errPref = "dbinit:index_diff:user"
        val err1 = "rt_err:$errPref:database:$key:last_name,first_name"
        val errBase = "$err1,$errPref"

        chkKeyIndex(attrs, "$key first_name, last_name;", "$errBase:code:$key:first_name,last_name")
        chkKeyIndex(attrs, "$key first_name;", "$errBase:code:$key:first_name")
        chkKeyIndex(attrs, "$key last_name;", "$errBase:code:$key:last_name")
        chkKeyIndex(attrs, "", err1)
        chkKeyIndex(attrs, "$key last_name, address;", "$errBase:code:$key:last_name,address")
        chkKeyIndex(attrs, "$key last_name, first_name, address;", "$errBase:code:$key:last_name,first_name,address")
        chkKeyIndex(attrs, "$key address, last_name, first_name;", "$errBase:code:$key:address,last_name,first_name")
        chkKeyIndex(attrs, "$key year_ob, last_name, first_name;", "$errBase:code:$key:year_ob,last_name,first_name")

        chkKeyIndex(attrs, "$index last_name, first_name;", "$errBase:code:$index:last_name,first_name")
        chkKeyIndex(attrs, "$key last_name, first_name; $key address;", "rt_err:$errPref:code:$key:address")

        chkKeyIndex(attrs, "$key last_name, first_name; $key address, year_ob;",
            "rt_err:$errPref:code:$key:address,year_ob")
        chkKeyIndex(attrs, "$key last_name, first_name; $key address, last_name;",
            "rt_err:$errPref:code:$key:address,last_name")
        chkKeyIndex(attrs, "$key last_name, first_name; $index address;",
            "rt_err:$errPref:code:$index:address")
        chkKeyIndex(attrs, "$key last_name, first_name; $index address, year_ob;",
            "rt_err:$errPref:code:$index:address,year_ob")
        chkKeyIndex(attrs, "$key last_name, first_name; $index address, last_name;",
            "rt_err:$errPref:code:$index:address,last_name")
    }

    @Suppress("SameParameterValue")
    private fun chkKeyIndex(attrs: String, extra: String, expected: String) {
        chkInit("entity user { $attrs $extra }", expected)
    }

    @Test fun testKeyRemove() {
        compatibility = "0.13.8"
        chkInit("entity user { key name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkInit("entity user { name; }", "rt_err:dbinit:index_diff:user:database:key:name")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
    }

    @Test fun testIndexRemove() {
        compatibility = "0.13.8"
        chkInit("entity user { index name; }")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
        chkInit("entity user { name; }", "rt_err:dbinit:index_diff:user:database:index:name")
        chkAll("0,user,class,false", "0,name,sys:text", "c0.user(name:text,rowid:int8)")
    }
}
