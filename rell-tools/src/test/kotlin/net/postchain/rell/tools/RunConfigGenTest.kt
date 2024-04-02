/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import net.postchain.gtv.GtvDecoder
import net.postchain.rell.api.base.RellCliBasicException
import net.postchain.rell.api.base.RellCliExitException
import net.postchain.rell.api.base.testutils.TestRellCliEnv
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.testutils.GtvTestUtils
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.unwrap
import net.postchain.rell.base.utils.*
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import net.postchain.rell.tools.runcfg.RellRunConfigParams
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunConfigGenTest {
    @Test fun testNodeConfig() {
        val configFiles = mapOf(
                "my-config.properties" to """
                    include=private.properties
                    foo=123
                    node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                """.trimIndent(),
                "private.properties" to "bar=456\n",
                "other.properties" to "other=789"
        )

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", """
            include=private.properties
            foo=123
            node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
        """)

        chkFile(files, "private.properties", "bar=456")

        chkFile(files, "blockchains/33/brid.txt", "A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99")

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="signers">
                    <array>
                        <bytea>0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57</bytea>
                    </array>
                </entry>
            </dict>
        """)

        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}""")

        assertEquals(setOf(), files.keys)
    }

    @Test fun testNodeConfigIncludeMany() {
        val configFiles = mapOf(
            "my-config.properties" to """
                    include=private.properties
                    include=public.properties
                    foo=123
                    node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                """.trimIndent(),
            "private.properties" to "private=456\n",
            "public.properties" to "public=789\n",
            "other.properties" to "other=101112"
        )

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", """
            include=private.properties
            include=public.properties
            foo=123
            node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
        """)

        chkFile(files, "private.properties", "private=456")
        chkFile(files, "public.properties", "public=789")

        chkFile(files, "blockchains/33/brid.txt")
        chkFile(files, "blockchains/33/0.xml")
        chkFile(files, "blockchains/33/0.gtv")

        assertEquals(setOf(), files.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNodeConfigIncludeManyCommaSeparated() {
        val configFiles = mapOf(
            "my-config.properties" to """
                    include=private.properties,public.properties
                    foo=123
                    node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                """.trimIndent(),
            "private.properties" to "private=456\n",
            "public.properties" to "public=789\n",
            "other.properties" to "other=101112"
        )

        generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                        </config>
                    </chain>
                </chains>
            </run>
        """)
    }

    @Test fun testAddSignersFalse() {
        val configFiles = mapOf(
                "my-config.properties" to "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
        )

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties" add-signers="false"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
        chkFile(files, "blockchains/33/brid.txt", "F9161BC0ABE91C6ACAB68B4A0B18CCD5763AE5B5798BC522209DDCF6D0302630")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":[]}""")

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="signers">
                    <array/>
                </entry>
            </dict>
        """)

        assertEquals(setOf<String>(), files.keys)
    }

    @Test fun testAddSignersDefault() {
        val configFiles = mapOf(
                "my-config.properties" to "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
        )

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
        chkFile(files, "blockchains/33/brid.txt", "A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}""")

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="signers">
                    <array>
                        <bytea>0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57</bytea>
                    </array>
                </entry>
            </dict>
        """)

        assertEquals(setOf<String>(), files.keys)
    }

    @Test fun testAddSignersExisting() {
        val configFiles = mapOf(
                "my-config.properties" to "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57"
        )

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config src="my-config.properties"/>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <gtv path="signers">
                                <array>
                                    <bytea>987654321098765432109876543210987654321098765432109876543210ABCDEF</bytea>
                                </array>
                            </gtv>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
        chkFile(files, "blockchains/33/brid.txt", "A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}""")

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="signers">
                    <array>
                        <bytea>0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57</bytea>
                    </array>
                </entry>
            </dict>
        """)

        assertEquals(setOf<String>(), files.keys)
    }

    @Test fun testModule() {
        val sourceFiles = mapOf(
                "app.rell" to "module; import sub;\nfunction main(){}",
                "sub.rell" to "module; function sub(){}",
                "other.rell" to "module; function other(){}"
        )

        val files = generate(sourceFiles, mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <app module="app" add-defaults="false"/>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "x=123")

        chkFile(files, "blockchains/33/brid.txt", "85E27E656B4816227A4E1931D64D3B4C44A6931BD6D968D1B2F4F4293886A11C")

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="gtx">
                    <dict>
                        <entry key="rell">
                            <dict>
                                <entry key="modules">
                                    <array>
                                        <string>app</string>
                                    </array>
                                </entry>
                                <entry key="sources">
                                    <dict>
                                        <entry key="app.rell">
                                            <string>module; import sub;
            function main(){}</string>
                                        </entry>
                                        <entry key="sub.rell">
                                            <string>module; function sub(){}</string>
                                        </entry>
                                    </dict>
                                </entry>
                                <entry key="version">
                                    <string>${RellTestUtils.RELL_VER}</string>
                                </entry>
                            </dict>
                        </entry>
                    </dict>
                </entry>
                <entry key="signers">
                    <array/>
                </entry>
            </dict>
        """)

        chkFileBin(files, "blockchains/33/0.gtv",
                """{
                    "gtx":{
                        "rell":{
                            "modules":["app"],
                            "sources":{
                                "app.rell":"module; import sub;\nfunction main(){}","sub.rell":"module; function sub(){}"
                            },
                            "version":"${RellTestUtils.RELL_VER}"
                        }
                    },
                    "signers":[]
                }""".unwrap()
        )

        assertEquals(setOf(), files.keys)
    }

    @Test(expected = RellCliBasicException::class)
    fun testModuleNotFound() {
        generate(mapOf(), mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <app module="app" />
                        </config>
                    </chain>
                </chains>
            </run>
        """)
    }

    @Test fun testModuleArgsValidation() {
        val sourceFiles = mapOf(
            "app.rell" to "module; struct module_args { x: integer; y: text; }",
        )

        val tpl = """
            <run>
                <nodes><config>#</config></nodes>
                <chains>
                    <chain name="app" iid="33">
                        <config height="0">
                            %s
                        </config>
                    </chain>
                </chains>
            </run>
        """

        var e = assertFailsWith<RellCliBasicException> {
            generate(sourceFiles, mapOf(), tpl.format("""<app module="app"/>"""))
        }
        assertEquals("Missing module_args for module(s): app", e.message)

        e = assertFailsWith {
            generate(sourceFiles, mapOf(), tpl.format("""<app module="app"><args module="app"></args></app>"""))
        }
        assertEquals("Bad module_args for module 'app': Key missing in Gtv dictionary: field 'app:module_args.x'", e.message)

        e = assertFailsWith {
            generate(sourceFiles, mapOf(), tpl.format("""
                <app module="app">
                    <args module="app">
                        <arg key="x"><int>123</int></arg>
                        <arg key="y"><int>456</int></arg>
                    </args>
                </app>
            """))
        }
        assertEquals("Bad module_args for module 'app': Decoding type 'text': expected STRING, actual INTEGER (attribute: app:module_args.y)", e.message)

        // Must succeed.
        generate(sourceFiles, mapOf(), tpl.format("""
            <app module="app">
                <args module="app">
                    <arg key="x"><int>123</int></arg>
                    <arg key="y"><string>Hello</string></arg>
                </args>
            </app>
        """))

        // Must succeed: no error on extra module.
        generate(sourceFiles, mapOf(), tpl.format("""
            <app module="app">
                <args module="app">
                    <arg key="x"><int>123</int></arg>
                    <arg key="y"><string>Hello</string></arg>
                </args>
                <args module="foo">
                    <arg key="x"><int>123</int></arg>
                </args>
            </app>
        """))
    }

    @Test(expected = RellCliExitException::class)
    fun testCompilationError() {
        val sourceFiles = mapOf(
                "app.rell" to "module; struct foo { x: unknown; }"
        )

        generate(sourceFiles, mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <app module="app" add-defaults="false"/>
                        </config>
                    </chain>
                </chains>
            </run>
        """)
    }

    @Test(expected = RellCliBasicException::class)
    fun testTestModuleAsMainModule() {
        val sourceFiles = mapOf(
                "app.rell" to "@test module; struct foo { x: integer; }"
        )

        generate(sourceFiles, mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <app module="app" />
                        </config>
                    </chain>
                </chains>
            </run>
        """)
    }

    @Test fun testDependenciesChainName() {
        chkDependencies(""" chain="user" """)
    }

    @Test fun testDependenciesBrid() {
        chkDependencies(""" brid="A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99" """)
    }

    private fun chkDependencies(depStr: String) {
        val files = generate(mapOf(), mapOf(), """
            <run>
                <nodes>
                    <config>
                        node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                    </config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0"/>
                    </chain>
                    <chain name="city" iid="55">
                        <config height="0">
                            <dependencies>
                                <dependency name="user_dep" $depStr />
                            </dependencies>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
        chkFile(files, "blockchains/33/brid.txt", "A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}""")
        chkFile(files, "blockchains/55/brid.txt", "D2DE94C67C3FAEDFB14D241FA902D3D4BA285F4C2813A6750F59CF502B5ADDEC")

        chkFileBin(files, "blockchains/55/0.gtv", """{
            "dependencies":[["user_dep","A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99"]],
            "signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}
        """.unwrap())

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="signers">
                    <array>
                        <bytea>0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57</bytea>
                    </array>
                </entry>
            </dict>
        """)

        chkFile(files, "blockchains/55/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="dependencies">
                    <array>
                        <array>
                            <string>user_dep</string>
                            <bytea>A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99</bytea>
                        </array>
                    </array>
                </entry>
                <entry key="signers">
                    <array>
                        <bytea>0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57</bytea>
                    </array>
                </entry>
            </dict>
        """)

        assertEquals(setOf<String>(), files.keys)
    }

    @Test(expected = RellCliBasicException::class)
    fun testDependencyWithGreaterIid() {
        generate(mapOf(), mapOf(), """
            <run>
                <nodes>
                    <config>
                        node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                    </config>
                </nodes>
                <chains>
                    <chain name="user" iid="55">
                        <config height="0"/>
                    </chain>
                    <chain name="city" iid="33">
                        <config height="0">
                            <dependencies>
                                <dependency name="user_dep" chain="user" />
                            </dependencies>
                        </config>
                    </chain>
                </chains>
            </run>
        """)
    }

    @Test fun testGeneratedBrid() {
        val files = generate(mapOf(), mapOf(), """
            <run>
                <nodes>
                    <config>
                        node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
                    </config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0"/>
                    </chain>
                    <chain name="city" iid="55">
                        <config height="0">
                            <dependencies>
                                <dependency name="user_dep" chain="user" />
                            </dependencies>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57")
        chkFile(files, "blockchains/33/brid.txt", "A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}""")
        chkFile(files, "blockchains/55/brid.txt", "D2DE94C67C3FAEDFB14D241FA902D3D4BA285F4C2813A6750F59CF502B5ADDEC")

        chkFileBin(files, "blockchains/55/0.gtv", """{
            "dependencies":[["user_dep","A5D2F114B70602A5145FB705EC4FED482F47B2AAB9780DBD17564DF1E4150F99"]],
            "signers":["0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"]}
        """.unwrap())
    }

    @Test fun testTestTestConfig() {
        val sourceFiles = mapOf("app.rell" to "module;")

        val files = generate(sourceFiles, mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                    <test-config>y=456</test-config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0"/>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "x=123")
        chkFile(files, "blockchains/33/brid.txt", "F9161BC0ABE91C6ACAB68B4A0B18CCD5763AE5B5798BC522209DDCF6D0302630")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":[]}""")
    }

    @Test fun testTestTestModules() {
        val sourceFiles = mapOf("app.rell" to "module;")

        // Must not fail even when test modules don't really exist.
        val files = generate(sourceFiles, mapOf(), """
            <run>
                <nodes>
                    <config>x=123</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0"/>
                        <test module="tests.chain_test" />
                    </chain>
                    <test module="tests.common_test" />
                </chains>
            </run>
        """)

        chkFile(files, "node-config.properties", "x=123")
        chkFile(files, "blockchains/33/brid.txt", "F9161BC0ABE91C6ACAB68B4A0B18CCD5763AE5B5798BC522209DDCF6D0302630")
        chkFileBin(files, "blockchains/33/0.gtv", """{"signers":[]}""")
    }

    @Test fun testGtvMergeArray() {
        chkGtvMerge(
                """<array><int>1</int><int>2</int><int>3</int></array>""",
                """<array><int>4</int><int>5</int><int>6</int></array>""",
                """
                            <array>
                                <int>1</int>
                                <int>2</int>
                                <int>3</int>
                                <int>4</int>
                                <int>5</int>
                                <int>6</int>
                            </array>"""
        )

        chkGtvMerge(
                """<array><int>1</int><int>2</int><int>3</int></array>""",
                """<array merge="replace"><int>4</int><int>5</int><int>6</int></array>""",
                """
                            <array>
                                <int>4</int>
                                <int>5</int>
                                <int>6</int>
                            </array>"""
        )

        chkGtvMerge(
                """<array><int>1</int><int>2</int><int>3</int></array>""",
                """<array merge="append"><int>4</int><int>5</int><int>6</int></array>""",
                """
                            <array>
                                <int>1</int>
                                <int>2</int>
                                <int>3</int>
                                <int>4</int>
                                <int>5</int>
                                <int>6</int>
                            </array>"""
        )

        chkGtvMerge(
                """<array><int>1</int><int>2</int><int>3</int></array>""",
                """<array merge="prepend"><int>4</int><int>5</int><int>6</int></array>""",
                """
                            <array>
                                <int>4</int>
                                <int>5</int>
                                <int>6</int>
                                <int>1</int>
                                <int>2</int>
                                <int>3</int>
                            </array>"""
        )
    }

    @Test fun testGtvMergeDict() {
        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="C"><int>3</int></entry><entry key="D"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>1</int>
                                </entry>
                                <entry key="B">
                                    <int>2</int>
                                </entry>
                                <entry key="C">
                                    <int>3</int>
                                </entry>
                                <entry key="D">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="B"><int>3</int></entry><entry key="C"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>1</int>
                                </entry>
                                <entry key="B">
                                    <int>3</int>
                                </entry>
                                <entry key="C">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict merge="replace"><entry key="B"><int>3</int></entry><entry key="C"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="B">
                                    <int>3</int>
                                </entry>
                                <entry key="C">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict merge="keep-old"><entry key="B"><int>3</int></entry><entry key="C"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>1</int>
                                </entry>
                                <entry key="B">
                                    <int>2</int>
                                </entry>
                                <entry key="C">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict merge="keep-new"><entry key="B"><int>3</int></entry><entry key="C"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>1</int>
                                </entry>
                                <entry key="B">
                                    <int>3</int>
                                </entry>
                                <entry key="C">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testGtvMergeDictStrict() {
        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict merge="strict"><entry key="B"><int>3</int></entry><entry key="C"><int>4</int></entry></dict>""",
                ""
        )
    }

    @Test fun testGtvMergeDictEntry() {
        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="A"><int>3</int></entry><entry key="B"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>3</int>
                                </entry>
                                <entry key="B">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="A" merge="keep-old"><int>3</int></entry><entry key="B"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>1</int>
                                </entry>
                                <entry key="B">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="A" merge="keep-new"><int>3</int></entry><entry key="B"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>3</int>
                                </entry>
                                <entry key="B">
                                    <int>4</int>
                                </entry>
                            </dict>"""
        )

        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict merge="keep-old"><entry key="A" merge="keep-new"><int>3</int></entry><entry key="B"><int>4</int></entry></dict>""",
                """
                            <dict>
                                <entry key="A">
                                    <int>3</int>
                                </entry>
                                <entry key="B">
                                    <int>2</int>
                                </entry>
                            </dict>"""
        )
    }

    @Test(expected = IllegalStateException::class)
    fun testGtvMergeDictEntryStrict() {
        chkGtvMerge(
                """<dict><entry key="A"><int>1</int></entry><entry key="B"><int>2</int></entry></dict>""",
                """<dict><entry key="A" merge="strict"><int>3</int></entry><entry key="B"><int>4</int></entry></dict>""",
                ""
        )
    }

    private fun chkGtvMerge(gtv1: String, gtv2: String, expected: String) {
        val files = generate(mapOf(), mapOf(), """
            <run>
                <nodes>
                    <config add-signers="false">x=y</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <gtv path="foo/bar">$gtv1</gtv>
                            <gtv path="foo/bar">$gtv2</gtv>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="foo">
                    <dict>
                        <entry key="bar">$expected
                        </entry>
                    </dict>
                </entry>
                <entry key="signers">
                    <array/>
                </entry>
            </dict>
        """)
    }

    @Test fun testGtvFromFile() {
        val configFiles = mapOf("part.xml" to """
            <dict>
                <entry key="a"><int>123</int></entry>
                <entry key="b"><int>456</int></entry>
            </dict>
        """)

        val files = generate(mapOf(), configFiles, """
            <run>
                <nodes>
                    <config add-signers="false">x=y</config>
                </nodes>
                <chains>
                    <chain name="user" iid="33">
                        <config height="0">
                            <gtv path="foo/bar" src="part.xml"/>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "blockchains/33/0.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="foo">
                    <dict>
                        <entry key="bar">
                            <dict>
                                <entry key="a">
                                    <int>123</int>
                                </entry>
                                <entry key="b">
                                    <int>456</int>
                                </entry>
                            </dict>
                        </entry>
                    </dict>
                </entry>
                <entry key="signers">
                    <array/>
                </entry>
            </dict>
        """)
    }

    // Bug: new line at the end of a <string> tag is not preserved.
    @Test fun testBugIncludeGeneratedConfigGtvXml() {
        val gtvXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <dict>
                <entry key="gtx">
                    <dict>
                        <entry key="rell">
                            <dict>
                                <entry key="modules">
                                    <array>
                                        <string></string>
                                    </array>
                                </entry>
                                <entry key="sources">
                                    <dict>
                                        <entry key="main.rell">
                                            <string>
                                                // New line is important, testing that it's being preserved.
                                            </string>
                                        </entry>
                                    </dict>
                                </entry>
                                <entry key="version">
                                    <string>0.10.8</string>
                                </entry>
                            </dict>
                        </entry>
                    </dict>
                </entry>
                <entry key="signers">
                    <array/>
                </entry>
            </dict>
        """.trimIndent()

        val configFiles = mapOf("0.xml" to gtvXml)
        val sourceFiles = mapOf("main.rell" to "//")

        val files = generate(sourceFiles, configFiles, """
            <run wipe-db="false">
                <nodes>
                    <config add-signers="false">#</config>
                </nodes>
                <chains>
                    <chain name="capchap" iid="0">
                        <config height="0">
                            <gtv src="0.xml"/>
                        </config>
                    </chain>
                </chains>
            </run>
        """)

        chkFile(files, "blockchains/0/0.xml", gtvXml)
    }

    private fun generate(
            sourceFiles: Map<String, String>,
            configFiles: Map<String, String>,
            confText: String
    ): MutableMap<String, DirFile> {
        val sourceDir = C_SourceDir.mapDirOf(sourceFiles)
        val configDir = MapGeneralDir(configFiles)
        val params = RellRunConfigParams(sourceDir, configDir, RellVersions.VERSION, unitTest = false)
        val conf = RellRunConfigGenerator.generate(TestRellCliEnv(), params, "run.xml", confText.trimIndent())
        val files = RellRunConfigGenerator.buildFiles(conf).toMutableMap()
        return files
    }

    private fun chkFile(files: MutableMap<String, DirFile>, path: String): DirFile {
        val actualFile = files.remove(path)
        return assertNotNull(actualFile, "File not found: $path ${files.keys}")
    }

    private fun chkFile(files: MutableMap<String, DirFile>, path: String, expected: String) {
        val actualFile = chkFile(files, path)
        val actual = (actualFile as TextDirFile).text
        assertEquals(expected.trimIndent().trim(), actual.trim())
    }

    private fun chkFileBin(files: MutableMap<String, DirFile>, path: String, expected: String) {
        val actualFile = files.remove(path)
        assertNotNull(actualFile, "File not found: $path ${files.keys}")
        assertTrue(actualFile is BinaryDirFile, "File is not binary: $path")

        val bytes = actualFile.data.toByteArray()
        val gtv = GtvDecoder.decodeGtv(bytes)
        val actual = GtvTestUtils.encodeGtvStr(gtv)
        assertEquals(expected, actual)
    }
}
