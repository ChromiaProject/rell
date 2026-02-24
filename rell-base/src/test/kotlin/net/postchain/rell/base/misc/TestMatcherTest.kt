/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.utils.UnitTestMatcher
import net.postchain.rell.base.utils.checkEquals
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMatcherTest {
    @Test fun testMatchFunctionFixed() {
        chkMatchFunction("bar", "foo:ns.bar", true)
        chkMatchFunction("bar", "oof:ns.bar", true)
        chkMatchFunction("bar", "foo:sn.bar", true)
        chkMatchFunction("bar", "foo:bar", true)
        chkMatchFunction("bar", "foo:a.b.bar", true)
        chkMatchFunction("bar", "foo:ns.rab", false)
        chkMatchFunction("bar", ":bar", true)

        chkMatchFunction("ns.bar", "foo:ns.bar", true)
        chkMatchFunction("ns.bar", "oof:ns.bar", true)
        chkMatchFunction("ns.bar", ":ns.bar", true)
        chkMatchFunction("ns.bar", "foo:sn.bar", false)
        chkMatchFunction("ns.bar", "foo:ns.rab", false)
        chkMatchFunction("ns.bar", "foo:bar", false)
        chkMatchFunction("ns.bar", ":bar", false)

        chkMatchFunction("foo:ns.bar", "foo:ns.bar", true)
        chkMatchFunction("foo:ns.bar", "oof:ns.bar", false)
        chkMatchFunction("foo:ns.bar", "foo:sn.bar", false)
        chkMatchFunction("foo:ns.bar", "foo:ns.rab", false)
        chkMatchFunction("foo:ns.bar", ":ns.bar", false)
        chkMatchFunction("foo:ns.bar", ":bar", false)

        chkMatchFunction("foo:ns.bar", "foo:ns.bar", true)
        chkMatchFunction("oof:ns.bar", "foo:ns.bar", false)
        chkMatchFunction("foo:sn.bar", "foo:ns.bar", false)
        chkMatchFunction("foo:ns.rab", "foo:ns.bar", false)
    }

    @Test fun testMatchFunctionWildcard() {
        chkMatchFunction("*.bar", "foo:ns.bar", true)
        chkMatchFunction("*.bar", "oof:ns.bar", true)
        chkMatchFunction("*.bar", ":ns.bar", true)
        chkMatchFunction("*.bar", "foo:sn.bar", true)
        chkMatchFunction("*.bar", "foo:ns.rab", false)
        chkMatchFunction("*.bar", "foo:bar", false)
        chkMatchFunction("*.bar", ":bar", false)

        chkMatchFunction("ns.*", "foo:ns.bar", true)
        chkMatchFunction("ns.*", "oof:ns.bar", true)
        chkMatchFunction("ns.*", ":ns.bar", true)
        chkMatchFunction("ns.*", "foo:sn.bar", false)
        chkMatchFunction("ns.*", "foo:ns.rab", true)
        chkMatchFunction("ns.*", "foo:bar", false)
        chkMatchFunction("ns.*", ":bar", false)


        chkMatchFunction("*:ns.bar", "foo:ns.bar", true)
        chkMatchFunction("*:ns.bar", "oof:ns.bar", true)
        chkMatchFunction("*:ns.bar", "foo:sn.bar", false)
        chkMatchFunction("*:ns.bar", "foo:ns.rab", false)
        chkMatchFunction("*:ns.bar", ":ns.bar", true)
        chkMatchFunction("*:ns.bar", ":bar", false)

        chkMatchFunction("foo:*.bar", "foo:ns.bar", true)
        chkMatchFunction("foo:*.bar", "oof:ns.bar", false)
        chkMatchFunction("foo:*.bar", "foo:sn.bar", true)
        chkMatchFunction("foo:*.bar", "foo:ns.rab", false)
        chkMatchFunction("foo:*.bar", ":ns.bar", false)
        chkMatchFunction("foo:*.bar", ":bar", false)

        chkMatchFunction("foo:ns.*", "foo:ns.bar", true)
        chkMatchFunction("foo:ns.*", "oof:ns.bar", false)
        chkMatchFunction("foo:ns.*", "foo:sn.bar", false)
        chkMatchFunction("foo:ns.*", "foo:ns.rab", true)
        chkMatchFunction("foo:ns.*", ":ns.bar", false)
        chkMatchFunction("foo:ns.*", ":bar", false)
    }

    private fun chkMatchFunction(pat: String, name: String, exp: Boolean) {
        val m = UnitTestMatcher.make(listOf(pat))
        val rDefNames = parseFunctionName(name)
        assertEquals(exp, m.matchFunction(rDefNames))
    }

    private fun parseFunctionName(name: String): R_DefinitionName {
        val parts = name.split(":")
        checkEquals(parts.size, 2)
        val module = parts[0]
        val qName = parts[1]
        val nameParts = qName.split(".")
        val simpleName = nameParts.last()
        return R_DefinitionName(module = module, qualifiedName = qName, simpleName = simpleName)
    }

    @Test fun testGlob() {
        matchGlob("foo", "foo", true)
        matchGlob("foo", "bar", false)
        matchGlob("foo", "ffoo", false)
        matchGlob("foo", "fooo", false)
        matchGlob("foo", "fo", false)
        matchGlob("foo", "oo", false)

        matchGlob("*", "foo", true)
        matchGlob("*", "bar", true)
        matchGlob("*", "", true)
        matchGlob("*", "x", true)

        matchGlob("f*", "foo", true)
        matchGlob("f*", "bar", false)
        matchGlob("f*", "fee", true)
        matchGlob("f*", "f", true)

        matchGlob("*o", "foo", true)
        matchGlob("*o", "bar", false)
        matchGlob("*o", "go", true)
        matchGlob("*o", "o", true)
        matchGlob("*o", "good", false)

        matchGlob("fo?", "foo", true)
        matchGlob("fo?", "foos", false)
        matchGlob("fo?", "fo", false)
        matchGlob("fo?", "bar", false)
        matchGlob("fo?", "fog", true)
        matchGlob("fo?", "for", true)
        matchGlob("fo?", "fud", false)

        matchGlob("?oo", "foo", true)
        matchGlob("?oo", "bar", false)
        matchGlob("?oo", "boo", true)
        matchGlob("?oo", "foos", false)
        matchGlob("?oo", "oo", false)

        matchGlob("?o?", "foo", true)
        matchGlob("?o?", "fos", true)
        matchGlob("?o?", "bar", false)
        matchGlob("?o?", "bos", true)
        matchGlob("?o?", "o", false)
        matchGlob("?o?", "fo", false)
        matchGlob("?o?", "fau", false)

        matchGlob("?o*s", "foos", true)
        matchGlob("?o*s", "boos", true)
        matchGlob("?o*s", "bar", false)
        matchGlob("?o*s", "bos", true)
        matchGlob("?o*s", "gos", true)
        matchGlob("?o*s", "ago", false)
        matchGlob("?o*s", "agos", false)
    }

    private fun matchGlob(pat: String, s: String, exp: Boolean) {
        val pattern = UnitTestMatcher.globToPattern(pat)
        assertEquals(exp, pattern.matcher(s).matches())
    }
}
