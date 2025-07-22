package net.postchain.rell.api.gtx

import kotlin.test.Test

internal class CompoundSingularOperationTest: BaseReplGtxTest() {

    @Test fun testMultipleUsageInvalid() {
        chkCompile("@singular @singular operation a(){}", "ct_err:modifier:dup:ann:singular")
        chkCompile("@compound @compound operation b(){}", "ct_err:modifier:dup:ann:compound")
        chkCompile("@singular @singular @compound operation c(){}", "ct_err:modifier:dup:ann:singular")
        chkCompile("@compound @compound @singular operation d(){}", "ct_err:modifier:dup:ann:compound")
    }

    @Test fun testParameterizedUsage() {
        chkCompile("@singular(true) operation a(){}", "ct_err:ann:singular:args:1")
        chkCompile("@compound(\"foo\") operation b(){}", "ct_err:ann:compound:args:1")
        chkCompile("@singular(3.1415, x'00') operation c(){}", "ct_err:ann:singular:args:2")
        chkCompile("@compound(1, 1L) operation d(){}", "ct_err:ann:compound:args:2")
        chkCompile("@compound() operation e(){}", "OK")
        chkCompile("@singular() operation f(){}", "OK")
    }

    @Test fun testUsageOnInvalidTargets() {
        chkCompile("@singular function a(){}", "ct_err:modifier:invalid:ann:singular")
        chkCompile("@compound function b(){}", "ct_err:modifier:invalid:ann:compound")
        chkCompile("@singular query c() = 0;", "ct_err:modifier:invalid:ann:singular")
        chkCompile("@compound query d() = 1;", "ct_err:modifier:invalid:ann:compound")
        chkCompile("@singular namespace f {}", "ct_err:modifier:invalid:ann:singular")
        chkCompile("@compound namespace e {}", "ct_err:modifier:invalid:ann:compound")
    }

    @Test fun testSingularOncePerTxSucceeds() {
        init()
        chkTxOk("add_person_singular('Alice')")
        chkTxOk("add_person_singular('Bob')")
        chkTxOk("add_person_singular('Charlie')")
        chkDb("Alice", "Bob", "Charlie")
    }

    @Test fun testDifferentSingularOperationsSucceeds() {
        init()
        chkTxOk("add_person_singular('Alice')", "add_person_singular_2('Bob')")
        chkDb("Alice", "Bob")
    }

    @Test fun testSingularErrCases() {
        init()
        chkTxErr("add_person_singular('Alice')", "add_person_singular('Bob')")
        chkTxErr("add_person_singular('Alice')", "add_person_singular('Alice')")
        chkTxErr("add_person_singular('Alice')", "add_person('Charlie')", "add_person_singular('Bob')")
        chkDb()
    }

    @Test fun testCompoundBeforeNormalSucceeds() {
        init()
        chkTxOk("add_person_compound('Alice')", "add_person('Bob')")
        chkDb("Alice", "Bob")
    }

    @Test fun testCompoundAfterNormalSucceeds() {
        init()
        chkTxOk("add_person('Bob')", "add_person_compound('Alice')")
        chkDb("Bob", "Alice")
    }

    @Test fun testCompoundErrCases() {
        init()
        chkTxErr("add_person_compound('Alice')")
        chkTxErr("add_person_compound('Bob')", "add_person_compound('Alice')")
        chkTxErr("add_person_compound('Bob')", "add_person_compound_2('Alice')")
        chkDb()
    }

    @Test fun testSingularCompoundBeforeNormalSucceeds() {
        init()
        chkTxOk("add_person_singular_compound('Bob')", "add_person('Alice')")
        chkDb("Bob", "Alice")
    }

    @Test fun testSingularCompoundAfterNormalSucceeds() {
        init()
        chkTxOk("add_person('Alice')", "add_person_singular_compound('Bob')")
        chkDb("Alice", "Bob")
    }

    @Test fun testSingularCompoundErrCases() {
        init()
        chkTxErr("add_person_singular_compound('Bob')")
        chkTxErr("add_person_singular_compound('Bob')", "add_person_singular_compound('Alice')")
        chkDb()
    }

    private fun init() {
        initChain()
        file("module.rell", """
            module;
            entity person { name; }
            operation add_person(name) { create person(name); }
            @singular operation add_person_singular(name) { create person(name); }
            @singular operation add_person_singular_2(name) { create person(name); }
            @compound operation add_person_compound(name) { create person(name); }
            @compound operation add_person_compound_2(name) { create person(name); }
            @singular @compound operation add_person_singular_compound(name) { create person(name); }
        """)
    }

    private fun chkTxOk(vararg opCalls: String) {
        chkTxBase("RES:unit", *opCalls)
    }

    private fun chkTxErr(vararg opCalls: String) {
        chkTxBase("rt_err:fn:rell.test.tx.run:fail:net.postchain.common.exception.TransactionIncorrect", *opCalls)
    }

    private fun chkTxBase(errStr: String, vararg opCalls: String) {
        repl.chk(opCalls.joinToString(",", "rell.test.tx([", "]).run();"), errStr)
    }

    private fun chkDb(vararg people: String) {
        repl.chk("person @* {} (.name)", people.joinToString(",", "RES:list<text>[", "]") { "text[$it]" })
    }
}