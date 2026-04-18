/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class ReplValueFormatTest: BaseRellTest() {
    @Test fun testDefault() {
        initRepl("od")
        repl.chk("123", "123")
        repl.chk("l", "[(123,Hello), (456,Bye)]")
        repl.chk("m", "{123=Hello, 456=Bye}")
        repl.chk("m2", "{Hello=123, Bye=456}")
        repl.chk("19223372036854775807L", "19223372036854775807")
    }

    @Test fun testOneItemPerLine() {
        initRepl("ol")
        repl.chk("123", "123")
        repl.chk("l", "(123,Hello)\n(456,Bye)")
        repl.chk("m", "123=Hello\n456=Bye")
        repl.chk("m2", "Hello=123\nBye=456")
        repl.chk("19223372036854775807L", "19223372036854775807")
    }

    @Test fun testGtvString() {
        initRepl("og")
        repl.chk("123", "123")
        repl.chk("123L", "123L")
        repl.chk("123.0", "\"123\"")
        repl.chk("true", "1")
        repl.chk("x'1234'", "x\"1234\"")
        repl.chk("'Hello'", "\"Hello\"")
        repl.chk("l", "[[123, \"Hello\"], [456, \"Bye\"]]")
        repl.chk("m", "[[123, \"Hello\"], [456, \"Bye\"]]")
        repl.chk("m2", """["Bye": 456, "Hello": 123]""")
        repl.chk("19223372036854775807L", "19223372036854775807L")
    }

    @Test fun testGtvJson() {
        initRepl("oj")
        repl.chk("123", "123")

        repl.chk("l", """
            [
              [
                123,
                "Hello"
              ],
              [
                456,
                "Bye"
              ]
            ]
        """.trimIndent())

        repl.chk("m", """
            [
              [
                123,
                "Hello"
              ],
              [
                456,
                "Bye"
              ]
            ]
        """.trimIndent())

        repl.chk("m2", """
            {
              "Bye": 456,
              "Hello": 123
            }
        """.trimIndent())

        repl.chk("19223372036854775807L", "19223372036854775807")
    }

    @Test fun testGtvXml() {
        initRepl("ox")
        repl.chk("123", "<int>123</int>")

        repl.chk("l", """
            <array>
                <array>
                    <int>123</int>
                    <string>Hello</string>
                </array>
                <array>
                    <int>456</int>
                    <string>Bye</string>
                </array>
            </array>
        """.trimIndent())

        repl.chk("m", """
            <array>
                <array>
                    <int>123</int>
                    <string>Hello</string>
                </array>
                <array>
                    <int>456</int>
                    <string>Bye</string>
                </array>
            </array>
        """.trimIndent())

        repl.chk("m2", """
            <dict>
                <entry key="Bye">
                    <int>456</int>
                </entry>
                <entry key="Hello">
                    <int>123</int>
                </entry>
            </dict>
        """.trimIndent())

        repl.chk("19223372036854775807L", "<bigint>19223372036854775807</bigint>")
    }

    private fun initRepl(format: String) {
        repl.outPlainValues = true
        repl.chk("\\$format")
        repl.chk("val l = [(123,'Hello'),(456,'Bye')];")
        repl.chk("val m = [123:'Hello',456:'Bye'];")
        repl.chk("val m2 = ['Hello':123,'Bye':456];")
    }
}
