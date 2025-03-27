/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.utils.checkEquals
import picocli.CommandLine
import java.io.File
import java.nio.file.Files

private val MIGRATION_MAP = mapOf(
        "class" to "entity",
        "record" to "struct",

        "addAll" to "add_all",
        "charAt" to "char_at",
        "compareTo" to "compare_to",
        "containsAll" to "contains_all",
        "endsWith" to "ends_with",
        "fromBytes" to "from_bytes",
        "fromGTXValue" to "from_gtv",
        "fromJSON" to "from_json",
        "fromPrettyGTXValue" to "from_gtv_pretty",
        "GTXValue" to "gtv",
        "indexOf" to "index_of",
        "lastIndexOf" to "last_index_of",
        "lowerCase" to "lower_case",
        "parseHex" to "from_hex",
        "putAll" to "put_all",
        "removeAll" to "remove_all",
        "removeAt" to "remove_at",
        "requireNotEmpty" to "require_not_empty",
        "startsWith" to "starts_with",
        "toBytes" to "to_bytes",
        "toGTXValue" to "to_gtv",
        "toJSON" to "to_json",
        "toList" to "to_list",
        "toPrettyGTXValue" to "to_gtv_pretty",
        "upperCase" to "upper_case"
)

fun main(args: Array<String>) {
    RellToolsLogUtils.initLogging()
    RellToolsUtils.runCli(args, RellMigratorCliArgs(), ::main0)
}

private fun main0(args: RellMigratorCliArgs) {
    val dir = RellToolsUtils.checkDir(args.directory)

    var totalFileCount = 0
    var replaceFileCount = 0
    var replaceTokenCount = 0

    Files.walk(dir.toPath()).forEach {
        val file = it.toFile()
        if (file.isFile && file.name.endsWith(".rell")) {
            ++totalFileCount

            val count = try {
                processFile(file, args.dryRun)
            } catch (e: Throwable) {
                println("$file $e")
                0
            }

            if (count > 0) {
                println("$file $count")
                ++replaceFileCount
                replaceTokenCount += count
            }
        }
    }

    println("Replaced $replaceTokenCount tokens in $replaceFileCount of $totalFileCount .rell files")
}

private fun processFile(file: File, dryRun: Boolean): Int {
    val text = file.readText()
    val (text2, count) = replaceTokens(text)
    if (text2 != text && !dryRun) {
        file.writeText(text2)
    }
    return count
}

private fun replaceTokens(text: String): Pair<String, Int> {
    val replaces = tokenize(text)
    if (replaces.isEmpty()) return Pair(text, 0)

    var res = text
    for (rep in replaces.sortedBy { it.pos }.reversed()) {
        val t = res.substring(rep.pos, rep.pos + rep.oldStr.length)
        checkEquals(t, rep.oldStr)
        res = res.substring(0, rep.pos) + rep.newStr + res.substring(rep.pos + rep.oldStr.length)
    }

    return Pair(res, replaces.size)
}

private fun tokenize(text: String): List<TokenReplace> {
    val sourcePath = C_SourcePath.parse("?")
    val idePath = IdeSourcePathFilePath(sourcePath)
    val parserPath = C_ParserFilePath(sourcePath, idePath)
    val prod = RellTokenizer().tokenProducer(parserPath, text)

    val res = mutableListOf<TokenReplace>()
    while (true) {
        val tk = prod.nextToken() ?: break
        val dst = MIGRATION_MAP[tk.text]
        if (dst != null) res.add(TokenReplace(tk.offset, tk.text, dst))
    }

    return res
}

private class TokenReplace(val pos: Int, val oldStr: String, val newStr: String)

@CommandLine.Command(
        name = "migrator",
        description = ["Replaces deprecated keywords and names in all .rell files in the directory (recursively)"]
)
private class RellMigratorCliArgs: RellCliArgs() {
    @CommandLine.Option(names = ["--dry-run"], description = ["Do not modify files, only print replace counts"])
    var dryRun = false

    @CommandLine.Parameters(index = "0", paramLabel = "DIRECTORY", description = ["Directory"])
    var directory: String = ""
}
