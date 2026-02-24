/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import net.postchain.rell.base.utils.*
import org.apache.commons.configuration2.PropertiesConfiguration
import java.io.StringReader
import java.util.*
import java.util.regex.Pattern

object RunConfigNodeConfigGen {
    private val FILE_NAME = "node-config.properties"

    fun generateNodeConfig(nodeConfig: Rcfg_NodeConfig, configDir: GeneralDir): RellPostAppNode {
        return if (nodeConfig.src != null) {
            generateNodeConfigByPath(nodeConfig.src, configDir)
        } else {
            check(nodeConfig.text != null)
            generateNodeConfigByText(nodeConfig.text)
        }
    }

    private fun generateNodeConfigByPath(path: String, configDir: GeneralDir): RellPostAppNode {
        val text = configDir.readText(path)
        val incFiles = discoverIncludedFiles(path, text, configDir)

        val dirBuilder = DirBuilder()
        val signers = mutableSetOf<Bytes33>()

        for (incf in incFiles) {
            dirBuilder.put(incf.dstPath, incf.text)
            processSigners(incf.srcPath, incf.text, signers)
        }

        return RellPostAppNode(path, null, dirBuilder.toFileMap(), signers)
    }

    private fun discoverIncludedFiles(mainPath: String, mainText: String, configDir: GeneralDir): List<IncFile> {
        val dirPath = configDir.parentPath(mainPath)

        val res = mutableListOf<IncFile>()
        val set = mutableSetOf<String>()
        val queue = ArrayDeque<IncFile>()

        set.add(mainPath)
        queue.add(IncFile(mainPath, FILE_NAME, mainText))

        while (!queue.isEmpty()) {
            val incf = queue.remove()
            res.add(incf)

            val includes = getIncludedFiles(incf.text)

            for (incPath in includes) {
                check(isSingleNamePath(incPath)) {
                    "File ${incf.srcPath} includes file $incPath, only files in the same directory are allowed"
                }

                val incFullPath = configDir.subPath(dirPath, incPath)
                if (!set.add(incFullPath)) continue

                val incText = configDir.readText(incFullPath)
                queue.add(IncFile(incFullPath, incPath, incText))
            }
        }

        return res
    }

    private fun generateNodeConfigByText(text: String): RellPostAppNode {
        val includes = getIncludedFiles(text)
        check(includes.isEmpty()) { "Node configuration includes files $includes, not allowed when not using a separate file" }

        val text2 = trimLines(text)
        val dstFiles = mapOf(FILE_NAME to TextDirFile(text2))

        val signers = mutableSetOf<Bytes33>()
        processSigners(null, text, signers)

        return RellPostAppNode(null, text, dstFiles, signers)
    }

    private fun trimLines(text: String): String {
        val res = text.split("\n").map { it.trim() }.joinToString("\n")
        return res
    }

    private fun getIncludedFiles(text: String): List<String> {
        val includes = mutableSetOf<String>()
        val propsReader = PropertiesConfiguration.PropertiesReader(StringReader(text))
        while (propsReader.nextProperty()) {
            if (propsReader.propertyName.equals(PropertiesConfiguration.getInclude(), ignoreCase = true)) {
                includes.add(propsReader.propertyValue)
            }
        }
        return includes.toImmList()
    }

    private fun processSigners(path: String?, text: String, res: MutableSet<Bytes33>) {
        val props = Properties()
        props.load(StringReader(text))

        val signersMap = mutableMapOf<Long, Bytes33>()

        val pat = Pattern.compile("node[.]([0-9]+)[.]pubkey")

        for (key in props.stringPropertyNames()) {
            val m = pat.matcher(key)
            if (m.matches()) {
                val id = m.group(1).toLong()
                check(id !in signersMap) { "duplicate node ID: $key" }

                val value = props.getProperty(key)
                val bytes = CommonUtils.calcOpt { Bytes33.parse(value) }
                check(bytes != null) {
                    var msg = "Invalid pubkey ($key)"
                    if (path != null) msg += " in file $path"
                    msg += ": '$value'"
                    msg
                }

                signersMap[id] = bytes
            }
        }

        for (id in signersMap.keys.sorted()) {
            val signer = signersMap.getValue(id)
            res.add(signer)
        }
    }

    private fun isSingleNamePath(path: String) = !path.contains("/") && !path.contains("\\") && !path.contains(":")

    private class IncFile(val srcPath: String, val dstPath: String, val text: String)
}
