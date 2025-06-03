/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibSetTest: BaseRellTest() {
    @Test fun testConstructorRaw() {
        chk("set()", "ct_err:fn:sys:unresolved_type_params:[set]:T")
        chk("set([])", "ct_err:expr_list_no_type")
        chk("set([123])", "set<integer>[int[123]]")
        chk("set([123, 456, 789])", "set<integer>[int[123],int[456],int[789]]")
        chk("set([1, 2, 3, 2, 3, 4, 5])", "set<integer>[int[1],int[2],int[3],int[4],int[5]]")
        chk("set(list([123, 456, 789]))", "set<integer>[int[123],int[456],int[789]]")
        chk("set([[123]])", "ct_err:expr_call_badargs:[set]:[list<list<integer>>]")
        chk("set([1:'A',2:'B'])", "set<(integer,text)>[(int[1],text[A]),(int[2],text[B])]")
        chk("set(x=[1,2,3])", "ct_err:expr_call_badargs:[set]:[x:list<integer>]")
        chk("set(values=[1,2,3])", "set<integer>[int[1],int[2],int[3]]")

        chk("set(range(5))", "set<integer>[int[0],int[1],int[2],int[3],int[4]]")
        chk("set(x'feed')", "set<integer>[int[254],int[237]]")
    }

    @Test fun testConstructorTyped() {
        chk("set<integer>()", "set<integer>[]")
        chk("set<integer>([])", "set<integer>[]")
        chk("set<list<integer>>()",
            "ct_err:[param_bounds:set:T:-immutable:list<integer>][param_bounds:set:T:-immutable:list<integer>]")

        chk("set<integer>([123])", "set<integer>[int[123]]")
        chk("set<integer>([123, 456, 789])", "set<integer>[int[123],int[456],int[789]]")
        chk("set<integer>([1, 2, 3, 2, 3, 4, 5])", "set<integer>[int[1],int[2],int[3],int[4],int[5]]")
        chk("set<integer>(list([123, 456, 789]))", "set<integer>[int[123],int[456],int[789]]")
        chk("set<list<integer>>([[123]])",
            "ct_err:[param_bounds:set:T:-immutable:list<integer>][param_bounds:set:T:-immutable:list<integer>]")
        chk("set<(integer,text)>([:])", "set<(integer,text)>[]")
        chk("set<(integer,text)>([1:'A',2:'B'])", "set<(integer,text)>[(int[1],text[A]),(int[2],text[B])]")

        chk("set<integer>(x=[1,2,3])", "ct_err:expr_call_badargs:[set<integer>]:[x:list<integer>]")
        chk("set<integer>(values=[1,2,3])", "set<integer>[int[1],int[2],int[3]]")

        chk("set<integer>(range(5))", "set<integer>[int[0],int[1],int[2],int[3],int[4]]")
        chk("set<integer>(x'feed')", "set<integer>[int[254],int[237]]")
    }

    @Test fun testConstructorPartial() {
        chk("set(*)", "ct_err:expr:call:partial_not_supported:[set]")
        chk("set<integer>(*)", "ct_err:expr:call:partial_ambiguous:[set<integer>]")

        chkEx("{ val f: () -> set<integer> = set(*); return f; }", "ct_err:expr:call:partial_not_supported:[set]")
        chkEx("{ val f: () -> set<integer> = set<integer>(*); return f; }", "fn[set<integer>()]")
        chkEx("{ val f: () -> set<integer> = set<integer>(*); return f(); }", "set<integer>[]")

        chkEx("{ val f: (list<integer>) -> set<integer> = set(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[set]")
        chkEx("{ val f: (list<integer>) -> set<integer> = set<integer>(*); return f; }", "fn[set<integer>(*)]")

        chkEx("{ val f: (set<integer>) -> set<integer> = set(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[set]")
        chkEx("{ val f: (map<text,integer>) -> set<(text,integer)> = set(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[set]")

        chkEx("{ val f: (range) -> set<integer> = set(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[set]")
        chkEx("{ val f: (byte_array) -> set<integer> = set(*); return f; }",
            "ct_err:expr:call:partial_not_supported:[set]")
    }

    @Test fun testEmpty() {
        chk("set<integer>().empty()", "boolean[true]")
        chk("set<integer>([1]).empty()", "boolean[false]")
        chk("set<integer>([1, 2, 3, 4, 5]).empty()", "boolean[false]")
    }

    @Test fun testSize() {
        chk("set<integer>().size()", "int[0]")
        chk("set([1]).size()", "int[1]")
        chk("set([1, 2, 3, 4, 5]).size()", "int[5]")
        chk("set([1, 2, 3, 2, 3, 4, 5]).size()", "int[5]")
        chk("set<integer>().len()", "ct_err:deprecated:ALIAS:[rell:collection.len]:size")
    }

    @Test fun testEquals() {
        chk("set([1, 2, 3]) == set([1, 2, 3])", "boolean[true]")
        chk("set([1, 2, 3]) == set([4, 5, 6])", "boolean[false]")
        chk("set([1, 2, 3]) == set([1, 2])", "boolean[false]")
        chk("set([1, 2, 3]) == set([1, 2, 3, 4])", "boolean[false]")
        chk("set([1, 2, 3]) == set<integer>()", "boolean[false]")
        chk("set([1, 2, 3]) == set<text>()", "ct_err:binop_operand_type:==:[set<integer>]:[set<text>]")
        chk("set([1, 2, 3]) == set(['Hello'])", "ct_err:binop_operand_type:==:[set<integer>]:[set<text>]")
        chk("set([1, 2, 3]) == list<integer>()", "ct_err:binop_operand_type:==:[set<integer>]:[list<integer>]")
        chk("set([1, 2, 3]) == list([1, 2, 3])", "ct_err:binop_operand_type:==:[set<integer>]:[list<integer>]")
    }

    @Test fun testContains() {
        chk("set([1, 2, 3]).contains(1)", "boolean[true]")
        chk("set([1, 2, 3]).contains(3)", "boolean[true]")
        chk("set([1, 2, 3]).contains(5)", "boolean[false]")
        chk("set([1, 2, 3]).contains('Hello')", "ct_err:expr_call_badargs:[set<integer>.contains]:[text]")
    }

    @Test fun testIn() {
        chk("1 in set([1, 2, 3])", "boolean[true]")
        chk("3 in set([1, 2, 3])", "boolean[true]")
        chk("5 in set([1, 2, 3])", "boolean[false]")
        chk("'Hello' in set([1, 2, 3])", "ct_err:binop_operand_type:in:[text]:[set<integer>]")
    }

    @Test fun testContainsAll() {
        chk("set<integer>().contains_all(list<integer>())", "boolean[true]")
        chk("set<integer>().contains_all(set<integer>())", "boolean[true]")
        chk("set<integer>().contains_all(list<text>())",
            "ct_err:expr_call_badargs:[set<integer>.contains_all]:[list<text>]")
        chk("set<integer>([1, 2, 3]).contains_all([1, 2, 3])", "boolean[true]")
        chk("set<integer>([1, 2, 3]).contains_all(set([1, 2, 3]))", "boolean[true]")
        chk("set<integer>([1, 2, 3]).contains_all([0])", "boolean[false]")
        chk("set<integer>([1, 2, 3]).contains_all([2])", "boolean[true]")
        chk("set<integer>([1, 2, 3]).contains_all(set([0]))", "boolean[false]")
        chk("set<integer>([1, 2, 3]).contains_all(set([2]))", "boolean[true]")
        chk("set<integer>([1, 2, 3]).contains_all([1, 3])", "boolean[true]")
        chk("set<integer>([1, 2, 3]).contains_all([0, 1])", "boolean[false]")
        chk("set<integer>([1, 2, 3]).contains_all([1, 2, 3, 4])", "boolean[false]")
    }

    @Test fun testStr() {
        chk("set<integer>().str()", "text[[]]")
        chk("set<integer>([1]).str()", "text[[1]]")
        chk("set<integer>([1, 2, 3, 4, 5]).str()", "text[[1, 2, 3, 4, 5]]")
    }

    @Test fun testAdd() {
        tst.strictToString = false
        val init = "val x = set([1, 2, 3]);"
        chkEx("{ $init val r = x.add(4); return r+' '+x; }", "true [1, 2, 3, 4]")
        chkEx("{ $init val r = x.add(1); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add(2); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add(3); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add('Hello'); return 0; }", "ct_err:expr_call_badargs:[set<integer>.add]:[text]")
        chkEx("{ $init val r = x.add(0, 4); return 0; }", "ct_err:expr:call:too_many_args:[set<integer>.add]:1:2")
    }

    @Test fun testAddAll() {
        tst.strictToString = false
        val init = "val x = set([1, 2, 3]);"
        chkEx("{ $init val r = x.add_all(set<integer>()); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add_all(list<integer>()); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add_all(set<integer>([1, 2, 3])); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add_all(list<integer>([1, 2, 3])); return r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.add_all(set<integer>([3, 4, 5])); return r+' '+x; }", "true [1, 2, 3, 4, 5]")
        chkEx("{ $init val r = x.add_all(list<integer>([3, 4, 5])); return r+' '+x; }", "true [1, 2, 3, 4, 5]")
        chkEx("{ $init val r = x.add_all([4, 5, 6]); return r+' '+x; }", "true [1, 2, 3, 4, 5, 6]")
        chkEx("{ $init val r = x.add_all(set(['Hello'])); return 0; }",
            "ct_err:expr_call_badargs:[set<integer>.add_all]:[set<text>]")
        chkEx("{ $init val r = x.add_all(['Hello']); return 0; }",
            "ct_err:expr_call_badargs:[set<integer>.add_all]:[list<text>]")
        chkEx("{ $init val r = x.add_all(0, [4, 5, 6]); return 0; }",
            "ct_err:expr:call:too_many_args:[set<integer>.add_all]:1:2")
    }

    @Test fun testRemove() {
        tst.strictToString = false
        val init = "val x = set([1, 2, 3]);"
        chkEx("{ $init val r = x.remove(1); return ''+r+' '+x; }", "true [2, 3]")
        chkEx("{ $init val r = x.remove(2); return ''+r+' '+x; }", "true [1, 3]")
        chkEx("{ $init val r = x.remove(3); return ''+r+' '+x; }", "true [1, 2]")
        chkEx("{ $init val r = x.remove(0); return ''+r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.remove('Hello'); return 0; }", "ct_err:expr_call_badargs:[set<integer>.remove]:[text]")
    }

    @Test fun testRemoveAll() {
        tst.strictToString = false
        val init = "val x = set([1, 2, 3]);"
        chkEx("{ $init val r = x.remove_all(set([0])); return ''+r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.remove_all(set([1])); return ''+r+' '+x; }", "true [2, 3]")
        chkEx("{ $init val r = x.remove_all(set([2])); return ''+r+' '+x; }", "true [1, 3]")
        chkEx("{ $init val r = x.remove_all(set([3])); return ''+r+' '+x; }", "true [1, 2]")
        chkEx("{ $init val r = x.remove_all([0]); return ''+r+' '+x; }", "false [1, 2, 3]")
        chkEx("{ $init val r = x.remove_all([2]); return ''+r+' '+x; }", "true [1, 3]")
        chkEx("{ $init val r = x.remove_all([1, 2, 3]); return ''+r+' '+x; }", "true []")
        chkEx("{ $init val r = x.remove_all([1, 3]); return ''+r+' '+x; }", "true [2]")
        chkEx("{ $init val r = x.remove_all(['Hello']); return 0; }",
            "ct_err:expr_call_badargs:[set<integer>.remove_all]:[list<text>]")
        chkEx("{ $init val r = x.remove_all(set(['Hello'])); return 0; }",
            "ct_err:expr_call_badargs:[set<integer>.remove_all]:[set<text>]")
    }

    @Test fun testClear() {
        chkEx("{ val x = set([1, 2, 3]); x.clear(); return x; }", "set<integer>[]")
    }

    @Test fun testFor() {
        chkOp("for (i in set([123, 456, 789, 456, 123])) print(i);")
        chkOut("123", "456", "789")
    }

    @Test fun testMutableElement() {
        chkEx("{ return set([[123]]); }", "ct_err:expr_call_badargs:[set]:[list<list<integer>>]")
        chkEx("{ return set<list<integer>>(); }",
            "ct_err:[param_bounds:set:T:-immutable:list<integer>][param_bounds:set:T:-immutable:list<integer>]")
        chkEx("{ var x: set<list<integer>>; return 0; }", "ct_err:param_bounds:set:T:-immutable:list<integer>")
    }

    @Test fun testSort() {
        tst.strictToString = false
        def("struct rec { x: integer; }")

        chkEx("{ val s = set([ 5, 4, 3, 2, 1 ]); s._sort(); return s; }", "ct_err:unknown_member:[set<integer>]:_sort")

        chk("set([ 5, 4, 3, 2, 1 ]).sorted()", "[1, 2, 3, 4, 5]")
        chk("set([rec(123), rec(456)]).sorted()", "ct_err:fn:collection.sorted:not_comparable:rec")
    }
}
