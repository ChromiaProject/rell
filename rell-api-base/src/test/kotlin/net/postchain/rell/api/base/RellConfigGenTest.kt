/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.base

import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_Compiler
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.testutils.GtvTestUtils
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.RellVersions
import org.junit.Test
import kotlin.test.assertEquals

class RellConfigGenTest {
    private val ver = RellTestUtils.RELL_VER

    @Test fun testNoTemplateSingleFile() {
        val files = mapOf("main.rell" to "entity foo {}")
        val sources = "'sources':{'main.rell':'entity foo {}'}"
        chkCfg(files, null, "{'gtx':{'rell':{'compilerVersion':'$ver','modules':[''],$sources,'version':'$ver'}}}")
    }

    @Test fun testNoTemplateMultipleFiles() {
        val files = mapOf(
            "main.rell" to "entity user {}",
            "foo.rell" to "module; entity foo {}",
            "bar.rell" to "module; entity bar {}",
        )
        val sources = "'sources':{'main.rell':'entity user {}'}"
        chkCfg(files, null, "{'gtx':{'rell':{'compilerVersion':'$ver','modules':[''],$sources,'version':'$ver'}}}")
    }

    @Test fun testNoTemplateImportedModules() {
        val files = mapOf(
            "main.rell" to "import foo;",
            "foo.rell" to "module; import bar;",
            "bar.rell" to "module; entity bar {}",
        )
        val expFiles = "{'bar.rell':'module; entity bar {}','foo.rell':'module; import bar;','main.rell':'import foo;'}"
        chkCfg(files, null,
            "{'gtx':{'rell':{'compilerVersion':'$ver','modules':[''],'sources':$expFiles,'version':'$ver'}}}")
    }

    @Test fun testTemplate() {
        val files = mapOf("main.rell" to "entity foo {}")

        val modules = "'modules':['']"
        val sources = "'sources':{'main.rell':'entity foo {}'}"
        val cVerEnt = "'compilerVersion':'$ver'"
        val verEnt = "'version':'$ver'"

        chkCfg(files, "{}", "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")
        chkCfg(files, "{'foo':'bar'}", "{'foo':'bar','gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")
        chkCfg(files, "{'gtx':{}}", "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")
        chkCfg(files, "{'gtx':{'foo':'bar'}}", "{'gtx':{'foo':'bar','rell':{$cVerEnt,$modules,$sources,$verEnt}}}")
        chkCfg(files, "{'gtx':{'rell':{}}}", "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'foo':'bar'}}}",
            "{'gtx':{'rell':{$cVerEnt,'foo':'bar',$modules,$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'modules':['junk','trash','garbage']}}}",
            "{'gtx':{'rell':{$cVerEnt,'modules':['junk','trash','garbage',''],$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'modules':['','','']}}}",
            "{'gtx':{'rell':{$cVerEnt,'modules':['','',''],$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{$modules}}}", "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'modules':[]}}}", "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'sources':{},$verEnt}}}",
            "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'sources':{'garbage.rell':'garbage'},$verEnt}}}",
            "{'gtx':{'rell':{$cVerEnt,$modules,'sources':{'garbage.rell':'garbage','main.rell':'entity foo {}'},$verEnt}}}")

        chkCfg(files, "{'gtx':{'rell':{'sources':{'main.rell':'garbage'},$verEnt}}}",
            "{'gtx':{'rell':{$cVerEnt,$modules,$sources,$verEnt}}}")
    }

    @Test fun testTemplateXml() {
        val files = mapOf("main.rell" to "entity foo {}")

        val sources = "'sources':{'main.rell':'entity foo {}'}"
        val cVerEnt = "'compilerVersion':'$ver'"
        val verEnt = "'version':'$ver'"

        chkCfg0(files, "<dict/>", "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")
        chkCfg0(files, "<dict></dict>", "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")

        val header = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

        chkCfg0(files, """$header<dict></dict>""",
            "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")

        chkCfg0(files, """$header<dict/>""",
            "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")

        chkCfg0(files, """$header<dict><entry key="gtx"><dict/></entry></dict>""",
            "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")

        chkCfg0(files, """$header<dict><entry key="gtx"><dict></dict></entry></dict>""",
            "{'gtx':{'rell':{$cVerEnt,'modules':[''],$sources,$verEnt}}}")
    }

    private fun chkCfg(files: Map<String, String>, templateJson: String?, expectedJson: String) {
        val templateXml = if (templateJson == null) null else jsonToXml(templateJson)
        chkCfg0(files, templateXml, expectedJson)
    }

    private fun chkCfg0(files: Map<String, String>, templateXml: String?, expectedJson: String) {
        val sourceDir = C_SourceDir.mapDirOf(files)
        val modules = listOf(R_ModuleName.EMPTY)
        val cRes = C_Compiler.compile(sourceDir, modules)

        check(cRes.errors.isEmpty()) { "Errors: ${cRes.errors.map { it.code }}" }

        val rApp = cRes.app
        checkNotNull(rApp)

        val templateGtv = if (templateXml == null) GtvFactory.gtv(mapOf()) else PostchainGtvUtils.xmlToGtv(templateXml)
        val configGen = RellConfigGen(sourceDir, RellVersions.VERSION, modules, cRes.files, rApp)

        val actualGtv = configGen.makeConfig(templateGtv)
        val actualXml = RellConfigGen.configToText(actualGtv)
        val actualJson = xmlToJson(actualXml)
        assertEquals(expectedJson, actualJson)
    }

    private fun xmlToJson(xml: String): String {
        val gtv = PostchainGtvUtils.xmlToGtv(xml)
        return GtvTestUtils.gtvToStr(gtv)
    }

    private fun jsonToXml(json: String): String {
        val gtv = GtvTestUtils.strToGtv(json)
        return PostchainGtvUtils.gtvToXml(gtv)
    }
}
