/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.*

class RunConfigParserOptions(
        val unitTest: Boolean
)

object RunConfigParser {
    private const val TEST_CONFIG_TAG = "test-config"

    fun parseConfig(xml: RellXmlElement, opts: RunConfigParserOptions): Rcfg_Run {
        val root = xml
        root.checkTag("run")
        root.checkNoText()

        val attrs = root.attrs()
        val wipeDb = attrs.getBooleanOpt("wipe-db") ?: false
        attrs.checkNoMore()

        var nodes = false
        var nodeConfig: Rcfg_NodeConfig? = null
        var testNodeConfig: Rcfg_NodeConfig? = null
        var chains: List<Rcfg_Chain>? = null
        var tests: List<Rcfg_TestModule>? = null

        for (elem in root.elems) {
            when (elem.tag) {
                "nodes" -> {
                    elem.check(!nodes) { "nodes specified more than once" }
                    nodes = true
                    val p = parseNodes(opts, elem)
                    nodeConfig = p.first
                    testNodeConfig = p.second
                }
                "chains" -> {
                    elem.check(chains == null) { "chains specified more than once" }
                    val p = parseChains(elem)
                    chains = p.first
                    tests = p.second
                }
                else -> throw elem.errorTag()
            }
        }

        root.check(nodeConfig != null || testNodeConfig != null) { "node configuration not defined" }
        root.check(chains != null && !chains.isEmpty()) { "no chains defined" }

        return Rcfg_Run(nodeConfig, testNodeConfig, chains.orEmpty(), tests.orEmpty(), wipeDb = wipeDb)
    }

    private fun parseNodes(opts: RunConfigParserOptions, nodes: RellXmlElement): Pair<Rcfg_NodeConfig?, Rcfg_NodeConfig?> {
        nodes.checkNoText()
        nodes.attrs().checkNoMore()

        var config: Rcfg_NodeConfig? = null
        var testConfig: Rcfg_NodeConfig? = null

        for (elem in nodes.elems) {
            when (elem.tag) {
                "config" -> {
                    elem.check(config == null) { "node configuration specified more than once" }
                    config = parseNodeConfig(elem)
                }
                TEST_CONFIG_TAG -> {
                    elem.check(testConfig == null) { "test node configuration specified more than once" }
                    testConfig = parseNodeConfig(elem)
                }
                else -> throw elem.errorTag()
            }
        }

        if (opts.unitTest) {
            nodes.check(testConfig != null) { "test node configuration not defined ('$TEST_CONFIG_TAG' tag)" }
        } else {
            nodes.check(config != null) { "node configuration not defined" }
        }

        return Pair(config, testConfig)
    }

    private fun parseNodeConfig(elem: RellXmlElement): Rcfg_NodeConfig {
        elem.checkNoElems()

        val attrs = elem.attrs()
        val src = attrs.getNoBlankOpt("src")
        val addSigners = attrs.getBooleanOpt("add-signers") ?: true
        attrs.checkNoMore()

        val text = elem.text

        elem.check(src != null || text != null) { "neither src nor text specified" }
        elem.check(src == null || text == null) { "both src and text specified" }

        return Rcfg_NodeConfig(src, text, addSigners)
    }

    private fun parseChains(chains: RellXmlElement): Pair<List<Rcfg_Chain>, List<Rcfg_TestModule>> {
        chains.checkNoText()
        chains.attrs().checkNoMore()

        val ctx = ParseChainsCtx()
        val headers = mutableListOf<ParseChainHeader>()
        val tests = mutableListOf<Rcfg_TestModule>()

        for (elem in chains.elems) {
            when (elem.tag) {
                "chain" -> {
                    val header = parseChainHeader(ctx, elem)
                    headers.add(header)
                }
                "test" -> {
                    parseTestModule(elem, tests)
                }
                else -> throw elem.errorTag()
            }
        }

        val headersMap = headers.associateByToImmMap { it.name }
        val resChains = mutableMapOf<String, Rcfg_Chain>()

        for (header in headers.sortedBy { it.iid }) {
            val chain = parseChain(header, headersMap, resChains)
            check(chain.name !in resChains)
            resChains[chain.name] = chain
        }

        return Pair(resChains.values.toImmList(), tests.toImmList())
    }

    private fun parseChainHeader(ctx: ParseChainsCtx, chain: RellXmlElement): ParseChainHeader {
        chain.checkNoText()

        val attrs = chain.attrs()
        val name = attrs.getNoBlank("name")
        val iid = attrs.getType("iid", null) { val v = it.toLong(); check(v >= 0); v }
        attrs.checkNoMore()

        chain.check(ctx.names.add(name)) { "duplicate chain name: '$name'" }
        chain.check(ctx.iids.add(iid)) { "duplicate chain IID: $iid (name: '$name')" }

        return ParseChainHeader(name, iid, chain)
    }

    private fun parseChain(
            header: ParseChainHeader,
            allChains: Map<String, ParseChainHeader>,
            parsedChains: Map<String, Rcfg_Chain>
    ): Rcfg_Chain {
        val subCtx = ParseChainCtx(header, allChains, parsedChains)
        val configs = mutableListOf<Rcfg_ChainConfig>()
        val tests = mutableListOf<Rcfg_TestModule>()

        for (elem in header.elem.elems) {
            when (elem.tag) {
                "config" -> {
                    val config = parseChainConfig(subCtx, elem)
                    configs.add(config)
                }
                "test" -> {
                    parseTestModule(elem, tests)
                }
                else -> throw elem.errorTag()
            }
        }

        header.elem.check(configs.any { it.height == 0L }) { "no config for height 0" }

        return Rcfg_Chain(header.name, header.iid, configs, tests)
    }

    private fun parseChainConfig(ctx: ParseChainCtx, config: RellXmlElement): Rcfg_ChainConfig {
        config.checkNoText()

        val attrs = config.attrs()
        val height = attrs.getLongOpt("height") { it >= 0 } ?: 0
        val addDependencies = attrs.getBooleanOpt("add-dependencies") ?: true
        attrs.checkNoMore()

        config.check(ctx.configHeights.add(height)) { "duplicate height: $height (chain: '${ctx.name}')" }

        var app: Rcfg_App? = null
        val gtvs = mutableListOf<Rcfg_ChainConfigGtv>()
        var dependencies: Map<String, Rcfg_Dependency>? = null

        for (elem in config.elems) {
            when (elem.tag) {
                "app" -> {
                    elem.check(app == null) { "app specified more than once" }
                    app = parseAppConfig(elem)
                }
                "module" -> {
                    val ver = RellVersions.MODULE_SYSTEM_VERSION_STR
                    throw elem.error("module tag is deprecated since Rell $ver, use app instead")
                }
                "gtv" -> {
                    val gtv = parseChainConfigGtv(elem)
                    gtvs.add(gtv)
                }
                "dependencies" -> {
                    elem.check(dependencies == null) { "dependencies specified more than once" }
                    dependencies = parseChainDependencies(ctx, elem)
                }
                else -> throw elem.errorTag()
            }
        }

        return Rcfg_ChainConfig(height, app, gtvs, addDependencies, dependencies ?: mapOf())
    }

    private fun parseTestModule(elem: RellXmlElement, res: MutableList<Rcfg_TestModule>) {
        elem.checkNoText()
        elem.checkNoElems()

        val attrs = elem.attrs()
        val module = getModuleNameAttr(elem, attrs, "module")
        attrs.checkNoMore()

        res.add(Rcfg_TestModule(module))
    }

    private fun parseAppConfig(app: RellXmlElement): Rcfg_App {
        app.checkNoText()

        val attrs = app.attrs()
        val module = getModuleNameAttr(app, attrs, "module")
        val addDefaults = attrs.getBooleanOpt("add-defaults") ?: true
        attrs.checkNoMore()

        val args = mutableMapOf<R_ModuleName, Gtv>()

        for (elem in app.elems) {
            when (elem.tag) {
                "args" -> {
                    val (argsModule, moduleArgs) = parseModuleArgs(elem)
                    elem.check(argsModule !in args) { "args specified more than once for module '$argsModule'" }
                    args[argsModule] = moduleArgs
                }
                else -> throw elem.errorTag()
            }
        }

        return Rcfg_App(module, args.toImmMap(), addDefaults)
    }

    private fun parseModuleArgs(args: RellXmlElement): Pair<R_ModuleName, Gtv> {
        args.checkNoText()

        val attrs = args.attrs()
        val module = getModuleNameAttr(args, attrs, "module")
        attrs.checkNoMore()

        val map = mutableMapOf<String, Gtv>()

        for (elem in args.elems) {
            val (key, gtv) = parseModuleArg(elem)
            elem.check(key !in map) { "duplicate module arg key: '$key'" }
            map[key] = gtv
        }

        val gtvArgs: Gtv = GtvFactory.gtv(map)
        return Pair(module, gtvArgs)
    }

    private fun parseModuleArg(elem: RellXmlElement): Pair<String, Gtv> {
        elem.checkTag("arg")
        elem.checkNoText()

        val attrs = elem.attrs()
        val key = attrs.getNoBlank("key")
        attrs.checkNoMore()

        val gtv = RunConfigGtvParser.parseGtvRaw(elem)
        return Pair(key, gtv)
    }

    private fun parseChainConfigGtv(gtvElem: RellXmlElement): Rcfg_ChainConfigGtv {
        gtvElem.checkNoText()

        val attrs = gtvElem.attrs()

        val path = attrs.getTypeOpt("path", null) {
            val list = it.split("/")
            list.forEach { s -> check(!s.isEmpty()) }
            list
        }

        val src = attrs.getNoBlankOpt("src")

        attrs.checkNoMore()

        val gtv = if (gtvElem.elems.isEmpty()) null else RunConfigGtvParser.parseGtv(gtvElem)

        gtvElem.check(src != null || gtv != null) { "neither 'src' nor nested element specified" }
        gtvElem.check(src == null || gtv == null) { "both 'src' and nested element specified" }

        return Rcfg_ChainConfigGtv(path.orEmpty(), src, gtv)
    }

    private fun parseChainDependencies(ctx: ParseChainCtx, deps: RellXmlElement): Map<String, Rcfg_Dependency> {
        deps.checkNoText()
        deps.attrs().checkNoMore()

        val res = mutableMapOf<String, Rcfg_Dependency>()

        for (elem in deps.elems) {
            elem.checkTag("dependency")
            elem.checkNoText()
            elem.checkNoElems()

            val attrs = elem.attrs()
            val name = attrs.getNoBlank("name")
            val chain = attrs.getNoBlankOpt("chain")
            val brid = attrs.getTypeOpt("brid", "BRID") { Bytes32.parse(it) }
            attrs.checkNoMore()

            elem.check(name !in res) { "duplicate dependency name: '$name'" }
            elem.check(chain == null || brid == null) { "dependency '$name': both chain and brid specified" }
            elem.check(chain != null || brid != null) { "dependency '$name': neither chain nor brid specified" }

            val dep = parseDependency(ctx, elem, name, chain, brid)
            res[name] = dep
        }

        return res
    }

    private fun parseDependency(
            ctx: ParseChainCtx,
            elem: RellXmlElement,
            name: String,
            chain: String?,
            brid: Bytes32?
    ): Rcfg_Dependency {
        if (brid != null) {
            return Rcfg_Dependency(null, brid)
        }

        chain!!

        val depHeader = elem.checkNotNull(ctx.allChains[chain]) { "dependency '$name': unknown chain '$chain'" }

        val header = ctx.header
        elem.check(depHeader.iid < header.iid) { "dependency '$name': " +
                "chain '${header.name}' cannot depend on '${chain}', " +
                "because IID of '${header.name}' (${header.iid}) is not greater than " +
                "IID of '${chain}' (${depHeader.iid})" }

        val depChain = ctx.parsedChains.getValue(chain)
        return Rcfg_Dependency(depChain, null)
    }

    private fun getModuleNameAttr(elem: RellXmlElement, attrs: RellXmlAttrsParser, attr: String): R_ModuleName {
        val moduleStr = attrs.get(attr)
        val module = R_ModuleName.ofOpt(moduleStr)
        return module ?: throw elem.error("Invalid module name: '${moduleStr}'")
    }

    private class ParseChainHeader(val name: String, val iid: Long, val elem: RellXmlElement)

    private class ParseChainsCtx {
        val names = mutableSetOf<String>()
        val iids = mutableSetOf<Long>()
        val brids = mutableSetOf<Bytes32>()
    }

    private class ParseChainCtx(
            val header: ParseChainHeader,
            val allChains: Map<String, ParseChainHeader>,
            val parsedChains: Map<String, Rcfg_Chain>
    ) {
        val name = header.name
        val configHeights = mutableSetOf<Long>()
    }
}
