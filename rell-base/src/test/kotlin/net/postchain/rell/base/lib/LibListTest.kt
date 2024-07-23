/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibListTest: BaseRellTest() {
    @Test fun testLiteral() {
        chk("[]", "ct_err:expr_list_no_type")
        chk("[123]", "list<integer>[int[123]]")
        chk("[123, 456, 789]", "list<integer>[int[123],int[456],int[789]]")
        chk("['Hello', 'World']", "list<text>[text[Hello],text[World]]")
        chk("[123, 'Hello']", "ct_err:expr_list_itemtype:[integer]:[text]")
    }

    @Test fun testLiteralTypeHint() {
        chkEx("{ val x: list<integer> = []; return x; }", "list<integer>[]")
        chkEx("{ val x: list<integer?> = []; return x; }", "list<integer?>[]")
        chkEx("{ val x: list<integer?> = [123]; return x; }", "list<integer?>[int[123]]")
        chkEx("{ val x: list<(integer?,text)> = [(123,'Hello')]; return x; }",
            "list<(integer?,text)>[(int[123],text[Hello])]")
        chkEx("{ val x: list<list<integer?>> = [[123]]; return x; }", "list<list<integer?>>[list<integer?>[int[123]]]")
    }

    @Test fun testConstructorRaw() {
        chk("list()", "ct_err:fn:sys:unresolved_type_params:[list]:T")
        chk("list([])", "ct_err:expr_list_no_type")
        chk("list([123])", "list<integer>[int[123]]")
        chk("list([123, 456, 789])", "list<integer>[int[123],int[456],int[789]]")

        chk("list(set<integer>())", "list<integer>[]")
        chk("list(set([123, 456, 789]))", "list<integer>[int[123],int[456],int[789]]")
        chk("list(set<text>())", "list<text>[]")
        chk("list(set(['Hello']))", "list<text>[text[Hello]]")

        chk("list([1:'A',2:'B'])", "list<(integer,text)>[(int[1],text[A]),(int[2],text[B])]")
        chk("list(x=[1,2,3])", "ct_err:expr_call_badargs:[list]:[x:list<integer>]")
        chk("list(values=[1,2,3])", "list<integer>[int[1],int[2],int[3]]")

        chk("list(range(5))", "list<integer>[int[0],int[1],int[2],int[3],int[4]]")
        chk("list(x'feed')", "list<integer>[int[254],int[237]]")
    }

    @Test fun testConstructorTyped() {
        chk("list<integer>()", "list<integer>[]")
        chk("list<integer>([])", "list<integer>[]")
        chk("list<integer>([123])", "list<integer>[int[123]]")
        chk("list<integer>([123, 456, 789])", "list<integer>[int[123],int[456],int[789]]")
        chk("list<integer>(set<integer>())", "list<integer>[]")
        chk("list<integer>(set([123, 456, 789]))", "list<integer>[int[123],int[456],int[789]]")
        chk("list<integer>(set<text>())", "ct_err:expr_call_badargs:[list<integer>]:[set<text>]")
        chk("list<integer>(set(['Hello']))", "ct_err:expr_call_badargs:[list<integer>]:[set<text>]")
        chk("list<integer>(range(5))", "list<integer>[int[0],int[1],int[2],int[3],int[4]]")
        chk("list<(integer,text)>([:])", "list<(integer,text)>[]")
        chk("list<(integer,text)>([1:'A',2:'B'])", "list<(integer,text)>[(int[1],text[A]),(int[2],text[B])]")
        chk("list<integer>(x=[1,2,3])", "ct_err:expr_call_badargs:[list<integer>]:[x:list<integer>]")
        chk("list<integer>(values=[1,2,3])", "list<integer>[int[1],int[2],int[3]]")
    }

    @Test fun testConstructorPartial() {
        chk("list(*)", "ct_err:expr:call:partial_not_supported:[list]")
        chk("list<integer>(*)", "ct_err:expr:call:partial_ambiguous:[list<integer>]")

        chkEx("{ val f: () -> list<integer> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")
        chkEx("{ val f: () -> list<integer> = list<integer>(*); return f; }", "fn[list<integer>()]")
        chkEx("{ val f: () -> list<integer> = list<integer>(*); return f(); }", "list<integer>[]")

        chkEx("{ val f: (list<integer>) -> list<integer> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")
        chkEx("{ val f: (set<integer>) -> list<integer> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")
        chkEx("{ val f: (map<text,decimal>) -> list<(text,decimal)> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")
        chkEx("{ val f: (range) -> list<integer> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")
        chkEx("{ val f: (byte_array) -> list<integer> = list(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[list]")

        chkEx("{ val f: (list<integer>) -> list<integer> = list<integer>(*); return f; }", "fn[list<integer>(*)]")
        chkEx("{ val f: (list<integer>) -> list<integer> = list<integer>(*); return f([123]); }",
            "list<integer>[int[123]]")
        chkEx("{ val f: (set<integer>) -> list<integer> = list<integer>(*); return f; }", "fn[list<integer>(*)]")
        chkEx("{ val f: (set<integer>) -> list<integer> = list<integer>(*); return f(set([123])); }",
            "list<integer>[int[123]]")
        chkEx("{ val f: (map<integer,text>) -> list<(integer,text)> = list<(integer,text)>(*); return f; }",
            "fn[list<(integer,text)>(*)]")
        chkEx("{ val f: (map<integer,text>) -> list<(integer,text)> = list<(integer,text)>(*); return f([123:'Bob']); }",
            "list<(integer,text)>[(int[123],text[Bob])]")
    }

    @Test fun testEmpty() {
        chk("list<integer>().empty()", "boolean[true]")
        chk("list<integer>([1]).empty()", "boolean[false]")
        chk("list<integer>([1, 2, 3, 4, 5]).empty()", "boolean[false]")
    }

    @Test fun testSize() {
        chk("list<integer>().size()", "int[0]")
        chk("list([1]).size()", "int[1]")
        chk("list([1, 2, 3, 4, 5]).size()", "int[5]")
        chk("list<integer>().len()", "ct_err:deprecated:ALIAS:[rell:collection.len]:size")
    }

    @Test fun testGet() {
        chk("list([1, 2, 3, 4, 5]).get(0)", "int[1]")
        chk("list([1, 2, 3, 4, 5]).get(4)", "int[5]")
        chk("list([1, 2, 3, 4, 5]).get(-1)", "rt_err:fn:list.get:index:5:-1")
        chk("list([1, 2, 3, 4, 5]).get(5)", "rt_err:fn:list.get:index:5:5")

        chk("[1, 2, 3, 4, 5].get(0)", "int[1]")
        chk("[1, 2, 3, 4, 5].get(4)", "int[5]")
        chk("[1, 2, 3, 4, 5].get(-1)", "rt_err:fn:list.get:index:5:-1")
        chk("[1, 2, 3, 4, 5].get(5)", "rt_err:fn:list.get:index:5:5")
    }

    @Test fun testSubscriptGet() {
        chk("list([1, 2, 3, 4, 5])[0]", "int[1]")
        chk("list([1, 2, 3, 4, 5])[4]", "int[5]")
        chk("list([1, 2, 3, 4, 5])[-1]", "rt_err:list:index:5:-1")
        chk("list([1, 2, 3, 4, 5])[5]", "rt_err:list:index:5:5")

        chk("[1, 2, 3, 4, 5][0]", "int[1]")
        chk("[1, 2, 3, 4, 5][4]", "int[5]")
        chk("[1, 2, 3, 4, 5][-1]", "rt_err:list:index:5:-1")
        chk("[1, 2, 3, 4, 5][5]", "rt_err:list:index:5:5")

        chkEx("{ val x: list<integer>? = if (1>0) [1,2,3] else null; return x[1]; }", "ct_err:expr_subscript_null")
        chkEx("{ val x: list<integer>? = if (1>0) [1,2,3] else null; return x!![1]; }", "int[2]")
    }

    @Test fun testEquals() {
        chk("[1, 2, 3] == [1, 2, 3]", "boolean[true]")
        chk("[1, 2, 3] == [4, 5, 6]", "boolean[false]")
        chk("[1, 2, 3] == [1, 2]", "boolean[false]")
        chk("[1, 2, 3] == [1, 2, 3, 4]", "boolean[false]")
        chk("[1, 2, 3] == list<integer>()", "boolean[false]")
        chk("[1, 2, 3] == list<text>()", "ct_err:binop_operand_type:==:[list<integer>]:[list<text>]")
        chk("[1, 2, 3] == ['Hello']", "ct_err:binop_operand_type:==:[list<integer>]:[list<text>]")
        chk("[1, 2, 3] == set<integer>()", "ct_err:binop_operand_type:==:[list<integer>]:[set<integer>]")
        chk("[1, 2, 3] == set([1, 2, 3])", "ct_err:binop_operand_type:==:[list<integer>]:[set<integer>]")
    }

    @Test fun testContains() {
        chk("[1, 2, 3].contains(1)", "boolean[true]")
        chk("[1, 2, 3].contains(3)", "boolean[true]")
        chk("[1, 2, 3].contains(5)", "boolean[false]")
        chk("[1, 2, 3].contains('Hello')", "ct_err:expr_call_badargs:[list<integer>.contains]:[text]")
    }

    @Test fun testIn() {
        chk("1 in [1, 2, 3]", "boolean[true]")
        chk("3 in [1, 2, 3]", "boolean[true]")
        chk("5 in [1, 2, 3]", "boolean[false]")
        chk("'Hello' in [1, 2, 3]", "ct_err:binop_operand_type:in:[text]:[list<integer>]")
    }

    @Test fun testContainsAll() {
        chk("list<integer>().contains_all(list<integer>())", "boolean[true]")
        chk("list<integer>().contains_all(set<integer>())", "boolean[true]")
        chk("list<integer>().contains_all(list<text>())",
            "ct_err:expr_call_badargs:[list<integer>.contains_all]:[list<text>]")
        chk("[1, 2, 3].contains_all([1, 2, 3])", "boolean[true]")
        chk("[1, 2, 3].contains_all(set([1, 2, 3]))", "boolean[true]")
        chk("[1, 2, 3].contains_all([0])", "boolean[false]")
        chk("[1, 2, 3].contains_all([2])", "boolean[true]")
        chk("[1, 2, 3].contains_all(set([0]))", "boolean[false]")
        chk("[1, 2, 3].contains_all(set([2]))", "boolean[true]")
        chk("[1, 2, 3].contains_all([1, 3])", "boolean[true]")
        chk("[1, 2, 3].contains_all([0, 1])", "boolean[false]")
        chk("[1, 2, 3].contains_all([1, 2, 3, 4])", "boolean[false]")
    }

    @Test fun testIndexOf() {
        chk("[1, 2, 3].index_of(1)", "int[0]")
        chk("[1, 2, 3].index_of(3)", "int[2]")
        chk("[1, 2, 3].index_of(5)", "int[-1]")
        chk("[1, 2, 3].index_of('Hello')", "ct_err:expr_call_badargs:[list<integer>.index_of]:[text]")
    }

    @Test fun testSub() {
        chk("list<integer>().sub(0)", "list<integer>[]")
        chk("list<integer>().sub(0, 0)", "list<integer>[]")
        chk("list<integer>().sub(-1)", "rt_err:fn:list.sub:args:0:-1:0")
        chk("list<integer>().sub(-1, 0)", "rt_err:fn:list.sub:args:0:-1:0")
        chk("list<integer>().sub(0, 1)", "rt_err:fn:list.sub:args:0:0:1")
        chk("list<integer>().sub(0, 0, 0)", "ct_err:expr_call_badargs:[list<integer>.sub]:[integer,integer,integer]")
        chk("list<integer>([1, 2, 3]).sub(-1)", "rt_err:fn:list.sub:args:3:-1:3")
        chk("list<integer>([1, 2, 3]).sub(0)", "list<integer>[int[1],int[2],int[3]]")
        chk("list<integer>([1, 2, 3]).sub(1)", "list<integer>[int[2],int[3]]")
        chk("list<integer>([1, 2, 3]).sub(2)", "list<integer>[int[3]]")
        chk("list<integer>([1, 2, 3]).sub(3)", "list<integer>[]")
        chk("list<integer>([1, 2, 3]).sub(4)", "rt_err:fn:list.sub:args:3:4:3")
        chk("list<integer>([1, 2, 3]).sub(0, 1)", "list<integer>[int[1]]")
        chk("list<integer>([1, 2, 3]).sub(1, 2)", "list<integer>[int[2]]")
        chk("list<integer>([1, 2, 3]).sub(2, 3)", "list<integer>[int[3]]")
        chk("list<integer>([1, 2, 3]).sub(0, 2)", "list<integer>[int[1],int[2]]")
        chk("list<integer>([1, 2, 3]).sub(1, 3)", "list<integer>[int[2],int[3]]")
        chk("list<integer>([1, 2, 3]).sub(0, 3)", "list<integer>[int[1],int[2],int[3]]")
        chk("list<integer>([1, 2, 3]).sub(0, 4)", "rt_err:fn:list.sub:args:3:0:4")
        chk("list<integer>([1, 2, 3]).sub(2, 2)", "list<integer>[]")
        chk("list<integer>([1, 2, 3]).sub(2, 1)", "rt_err:fn:list.sub:args:3:2:1")
    }

    @Test fun testStr() {
        chk("list<integer>().str()", "text[[]]")
        chk("list<integer>([1]).str()", "text[[1]]")
        chk("list<integer>([1, 2, 3, 4, 5]).str()", "text[[1, 2, 3, 4, 5]]")
    }

    @Test fun testAdd() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3];"
        chkEx("{ $init val r = x.add(4); return r+' '+x; }", "true [1, 2, 3, 4]")
        chkEx("{ $init val r = x.add(0, 4); return r+' '+x; }", "true [4, 1, 2, 3]")
        chkEx("{ $init val r = x.add(3, 4); return r+' '+x; }", "true [1, 2, 3, 4]")
        chkEx("{ $init val r = x.add(2); return r+' '+x; }", "true [1, 2, 3, 2]")
        chkEx("{ $init val r = x.add(-1, 4); return r+' '+x; }", "rt_err:fn:list.add:index:3:-1")
        chkEx("{ $init val r = x.add(4, 4); return r+' '+x; }", "rt_err:fn:list.add:index:3:4")
        chkEx("{ $init val r = x.add('Hello'); return 0; }", "ct_err:expr_call_badargs:[list<integer>.add]:[text]")
    }

    @Test fun testAddAll() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3];"
        chkEx("{ $init val r = x.add_all(list<integer>()); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add_all([4, 5, 6]); return r+' '+x; }", "true [1, 2, 3, 4, 5, 6]")
        chkEx("{ $init val r = x.add_all([1, 2, 3]); return r+' '+x; }", "true [1, 2, 3, 1, 2, 3]")
        chkEx("{ $init val r = x.add_all(['Hello']); return 0; }",
            "ct_err:expr_call_badargs:[list<integer>.add_all]:[list<text>]")
        chkEx("{ $init val r = x.add_all(0, [4, 5, 6]); return r+' '+x; }", "true [4, 5, 6, 1, 2, 3]")
        chkEx("{ $init val r = x.add_all(3, [4, 5, 6]); return r+' '+x; }", "true [1, 2, 3, 4, 5, 6]")
        chkEx("{ $init val r = x.add_all(-1, [4, 5, 6]); return r+' '+x; }", "rt_err:fn:list.add_all:index:3:-1")
        chkEx("{ $init val r = x.add_all(4, [4, 5, 6]); return r+' '+x; }", "rt_err:fn:list.add_all:index:3:4")
        chkEx("{ $init val r = x.add_all(set([4, 5, 6])); return r+' '+x; }", "true [1, 2, 3, 4, 5, 6]")
        chkEx("{ $init val r = x.add_all(0, set([4, 5, 6])); return r+' '+x; }", "true [4, 5, 6, 1, 2, 3]")
    }

    @Test fun testRemove() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3, 2, 3, 4];"
        chkEx("{ $init val r = x.remove(1); return ''+r+' '+x; }", "true [2, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove(2); return ''+r+' '+x; }", "true [1, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove(3); return ''+r+' '+x; }", "true [1, 2, 2, 3, 4]")
        chkEx("{ $init val r = x.remove(0); return ''+r+' '+x; }", "false [1, 2, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove('Hello'); return 0; }", "ct_err:expr_call_badargs:[list<integer>.remove]:[text]")
    }

    @Test fun testRemoveAll() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3, 2, 3, 4];"
        chkEx("{ $init val r = x.remove_all(set([0])); return ''+r+' '+x; }", "false [1, 2, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove_all(set([1])); return ''+r+' '+x; }", "true [2, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove_all(set([2])); return ''+r+' '+x; }", "true [1, 3, 3, 4]")
        chkEx("{ $init val r = x.remove_all(set([3])); return ''+r+' '+x; }", "true [1, 2, 2, 4]")
        chkEx("{ $init val r = x.remove_all([0]); return ''+r+' '+x; }", "false [1, 2, 3, 2, 3, 4]")
        chkEx("{ $init val r = x.remove_all([2]); return ''+r+' '+x; }", "true [1, 3, 3, 4]")
        chkEx("{ $init val r = x.remove_all([1, 2, 3]); return ''+r+' '+x; }", "true [4]")
        chkEx("{ $init val r = x.remove_all([1, 3]); return ''+r+' '+x; }", "true [2, 2, 4]")
        chkEx("{ $init val r = x.remove_all(['Hello']); return 0; }",
            "ct_err:expr_call_badargs:[list<integer>.remove_all]:[list<text>]")
        chkEx("{ $init val r = x.remove_all(set(['Hello'])); return 0; }",
            "ct_err:expr_call_badargs:[list<integer>.remove_all]:[set<text>]")
    }

    @Test fun testRemoveAt() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3];"
        chkEx("{ $init val r = x.remove_at(0); return ''+r+' '+x; }", "1 [2, 3]")
        chkEx("{ $init val r = x.remove_at(1); return ''+r+' '+x; }", "2 [1, 3]")
        chkEx("{ $init val r = x.remove_at(2); return ''+r+' '+x; }", "3 [1, 2]")
        chkEx("{ $init val r = x.remove_at(-1); return ''+r+' '+x; }", "rt_err:fn:list.remove_at:index:3:-1")
        chkEx("{ $init val r = x.remove_at(3); return ''+r+' '+x; }", "rt_err:fn:list.remove_at:index:3:3")
        chkEx("{ $init val r = x.remove_at('Hello'); return 0; }",
            "ct_err:expr_call_badargs:[list<integer>.remove_at]:[text]")
    }

    @Test fun testClear() {
        chkEx("{ val x = [1, 2, 3]; x.clear(); return x; }", "list<integer>[]")
    }

    @Test fun testSet() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3];"
        chkEx("{ $init val r = x.set(0, 5); return ''+r+' '+x; }", "1 [5, 2, 3]")
        chkEx("{ $init val r = x.set(1, 5); return ''+r+' '+x; }", "2 [1, 5, 3]")
        chkEx("{ $init val r = x.set(2, 5); return ''+r+' '+x; }", "3 [1, 2, 5]")
        chkEx("{ $init val r = x.set(-1, 5); return ''+r+' '+x; }", "rt_err:fn:list.set:index:3:-1")
        chkEx("{ $init val r = x.set(3, 5); return ''+r+' '+x; }", "rt_err:fn:list.set:index:3:3")

        chkWarn()
        chkEx("{ $init val r = x._set(0, 5); return ''+r+' '+x; }", "1 [5, 2, 3]")
        chkWarn("deprecated:ALIAS:[rell:list._set]:set")
        chkEx("{ $init val r = x._set(1, 5); return ''+r+' '+x; }", "2 [1, 5, 3]")
        chkWarn("deprecated:ALIAS:[rell:list._set]:set")
    }

    @Test fun testSubscriptSet() {
        tst.strictToString = false
        val init = "val x = [1, 2, 3];"
        chkEx("{ $init x[0] = 5; return x; }", "[5, 2, 3]")
        chkEx("{ $init x[1] = 5; return x; }", "[1, 5, 3]")
        chkEx("{ $init x[2] = 5; return x; }", "[1, 2, 5]")
        chkEx("{ $init x[-1] = 5; return x; }", "rt_err:list:index:3:-1")
        chkEx("{ $init x[3] = 5; return x; }", "rt_err:list:index:3:3")

        chkEx("{ val x: list<integer>? = if (1>0) [1,2,3] else null; x[1] = 5; return x; }", "ct_err:expr_subscript_null")
        chkEx("{ val x: list<integer>? = if (1>0) [1,2,3] else null; x!![1] = 5; return x; }", "[1, 5, 3]")
    }

    @Test fun testFor() {
        chkOp("for (i in list([123, 456, 789])) print(i);")
        chkOut("123", "456", "789")
    }

    @Test fun testSort() {
        tst.strictToString = false
        def("struct rec { x: integer; }")

        chkEx("{ val l = [ 5, 4, 3, 2, 1 ]; l.sort(); return l; }", "[1, 2, 3, 4, 5]")
        chkEx("{ val l = [ 5, 4, 3, 2, 1 ]; return l.sort(); }", "ct_err:stmt_return_unit")
        chkEx("{ val l = [ 5, 4, 3, 2, 1 ]; return l.sorted(); }", "[1, 2, 3, 4, 5]")
        chkEx("{ val l = [ 5, 4, 3, 2, 1 ]; l.sorted(); return l; }", "[5, 4, 3, 2, 1]")

        chk("['F', 'E', 'D', 'C', 'B', 'A'].sorted()", "[A, B, C, D, E, F]")
        chk("[true, false].sorted()", "[false, true]")
        chk("[3, 2, 1, null].sorted()", "[null, 1, 2, 3]")
        chk("['C', 'B', 'A', null].sorted()", "[null, A, B, C]")

        chk("[(2,'B'),(2,'A'),(1,'X')].sorted()", "[(1,X), (2,A), (2,B)]")

        chk("[rec(123), rec(456)].sorted()", "ct_err:fn:collection.sorted:not_comparable:rec")

        chkWarn()
        chkEx("{ val l = [ 5, 4, 3, 2, 1 ]; l._sort(); return l; }", "[1, 2, 3, 4, 5]")
        chkWarn("deprecated:ALIAS:[rell:list._sort]:sort")
    }

    @Test fun testRepeat() {
        tst.strictToString = false

        chk("_type_of([1,2,3].repeat(3))", "list<integer>")
        chk("[1,2,3].repeat(0)", "[]")
        chk("[1,2,3].repeat(1)", "[1, 2, 3]")
        chk("[1,2,3].repeat(2)", "[1, 2, 3, 1, 2, 3]")
        chk("[1,2,3].repeat(3)", "[1, 2, 3, 1, 2, 3, 1, 2, 3]")
        chk("[1,2,3].repeat(4)", "[1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3]")
        chk("[1,2,3].repeat(5)", "[1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3]")

        chk("list<integer>().repeat(3)", "[]")
        chk("[1].repeat(3)", "[1, 1, 1]")
        chk("[1, 2].repeat(3)", "[1, 2, 1, 2, 1, 2]")

        chk("[1, 2, 3].repeat(-1)", "rt_err:fn:list.repeat:n_negative:-1")
        chk("[1, 2, 3].repeat(-1234567890123456)", "rt_err:fn:list.repeat:n_negative:-1234567890123456")
        chk("[1, 2, 3].repeat(0x80000000)", "rt_err:fn:list.repeat:n_out_of_range:2147483648")
        chk("[1, 2, 3].repeat(0x7FFFFFFF)", "rt_err:fn:list.repeat:too_big:6442450941")

        chkEx("{ val l = list<integer>(); val r = l.repeat(5); r.add(7); return (l, r); }", "([],[7])")
        chkEx("{ val l = [1, 2, 3]; val r = l.repeat(1); r.add(7); return (l, r); }", "([1, 2, 3],[1, 2, 3, 7])")
        chkEx("{ val l = [1, 2, 3]; val r1 = l.repeat(0); val r2 = l.repeat(0); r2.add(7); return (l, r1, r2); }", "([1, 2, 3],[],[7])")
    }

    @Test fun testReverse() {
        tst.strictToString = false
        chk("_type_of([1,2,3].reverse())", "unit")
        chk("[1, 2, 3].reverse()", "ct_err:query_exprtype_unit")

        chkEx("{ val l = list<integer>(); l.reverse(); return l; }", "[]")
        chkEx("{ val l = [1]; l.reverse(); return l; }", "[1]")
        chkEx("{ val l = [1, 2]; l.reverse(); return l; }", "[2, 1]")
        chkEx("{ val l = [1, 2, 3]; l.reverse(); return l; }", "[3, 2, 1]")
        chkEx("{ val l = [1, 2, 3, 4]; l.reverse(); return l; }", "[4, 3, 2, 1]")
        chkEx("{ val l = [1, 2, 3, 4, 5]; l.reverse(); return l; }", "[5, 4, 3, 2, 1]")
    }

    @Test fun testReversed() {
        tst.strictToString = false
        chk("_type_of([1,2,3].reversed())", "list<integer>")

        chk("list<integer>().reversed()", "[]")
        chk("[1].reversed()", "[1]")
        chk("[1, 2].reversed()", "[2, 1]")
        chk("[1, 2, 3].reversed()", "[3, 2, 1]")
        chk("[1, 2, 3, 4].reversed()", "[4, 3, 2, 1]")
        chk("[1, 2, 3, 4, 5].reversed()", "[5, 4, 3, 2, 1]")

        chkEx("{ val l = list<integer>(); val r = l.reversed(); r.add(7); return (l, r); }", "([],[7])")
        chkEx("{ val l = [1]; val r = l.reversed(); r.add(7); return (l, r); }", "([1],[1, 7])")
        chkEx("{ val l = [1, 2, 3]; val r = l.reversed(); r.add(7); return (l, r); }", "([1, 2, 3],[3, 2, 1, 7])")
    }

    @Test fun testCollectionType() {
        tst.testLib = true
        chk("[[123],[456]]", "list<list<integer>>[list<integer>[int[123]],list<integer>[int[456]]]")
        chk("[[123],set([456])]", "ct_err:expr_list_itemtype:[list<integer>]:[set<integer>]")
        chkEx("{ assert_equals([123],[123]); return 0; }", "int[0]")
        chkEx("{ assert_equals([123],set([123])); return 0; }",
            "ct_err:expr_call_badargs:[assert_equals]:[list<integer>,set<integer>]")
    }
}
