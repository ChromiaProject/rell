/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import com.google.common.base.Throwables
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.lib.test.Rt_AssertEqualsError
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlInitProjExt
import net.postchain.rell.base.sql.SqlManager
import net.postchain.rell.base.sql.SqlUtils
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

private const val SEPARATOR_LEN = 72
private val PRINT_SEPARATOR = "-".repeat(SEPARATOR_LEN)

class UnitTestResult(val duration: Duration, val error: Throwable?) {
    val isOk = error == null

    private fun statusStr() = if (isOk) "OK" else "FAILED"
    override fun toString() = statusStr()

    fun caseResultToString(case: UnitTestCase): String {
        val durationStr = durationToString(duration)
        val res = "${statusStr()} ${case.name} ($durationStr)"
        return res
    }

    companion object {
        fun durationToString(duration: Duration): String {
            val durationMs = duration.toMillis()
            val durationSecStr = if (durationMs == 0L) "0" else String.format("%.3f", durationMs / 1000.0)
            return "${durationSecStr}s"
        }
    }
}

class UnitTestRunnerContext(
    val app: R_App,
    val printer: Rt_Printer,
    val sqlCtx: Rt_SqlContext,
    val sqlMgr: SqlManager,
    val sqlInitProjExt: SqlInitProjExt,
    private val globalCtx: Rt_GlobalContext,
    private val chainCtx: Rt_ChainContext,
    private val blockRunner: Rt_UnitTestBlockRunner,
    private val moduleArgsSource: Rt_ModuleArgsSource,
    val printTestCases: Boolean = true,
    val printPrettyLargeValues: Boolean = true,
    val stopOnError: Boolean = false,
    val onTestCaseStart: (UnitTestCase) -> Unit = {},
    val onTestCaseFinished: (UnitTestCaseResult) -> Unit = {},
) {
    val casePrinter: Rt_Printer = if (printTestCases) printer else Rt_NopPrinter

    fun createAppContext(): Rt_AppContext {
        return Rt_AppContext(
            globalCtx,
            chainCtx,
            app,
            repl = false,
            test = true,
            blockRunner = blockRunner,
            moduleArgsSource = moduleArgsSource,
        )
    }
}

class UnitTestRunnerChain(val name: String, val iid: Long) {
    override fun toString() = "$name[$iid]"
}

class UnitTestCase(chain: UnitTestRunnerChain?, val fn: R_FunctionDefinition) {
    val name = let {
        val f = fn.appLevelName
        if (chain == null) f else "$chain:$f"
    }

    override fun toString(): String {
        return name
    }
}

class UnitTestCaseResult(val case: UnitTestCase, val res: UnitTestResult) {
    override fun toString() = "$case:$res"
}

class UnitTestRunnerResults(
    private val printPrettyLargeValues: Boolean = true,
) {
    private val results = mutableListOf<UnitTestCaseResult>()

    fun add(res: UnitTestCaseResult) {
        results.add(res)
    }

    fun getResults() = results.toImmList()

    fun print(printer: Rt_Printer): Boolean {
        val (okTests, failedTests) = results.partition { it.res.error == null }

        if (failedTests.isNotEmpty()) {
            printer.print("")
            printer.print(PRINT_SEPARATOR)
            printer.print("FAILED TESTS:")
            for (r in failedTests) {
                printFailedTest(printer, r)
            }
        }

        printer.print("")
        printer.print(PRINT_SEPARATOR)
        printer.print("TEST RESULTS:")

        printResults(printer, okTests)
        printResults(printer, failedTests)

        val nTests = results.size
        val nOk = okTests.size
        val nFailed = failedTests.size

        val sumDuration = results.fold(Duration.ZERO) { a, b -> a.plus(b.res.duration) }
        val sumDurationStr = UnitTestResult.durationToString(sumDuration)

        printer.print("\nSUMMARY: $nFailed FAILED / $nOk PASSED / $nTests TOTAL ($sumDurationStr)\n")

        val allOk = nFailed == 0
        printer.print("\n***** ${if (allOk) "OK" else "FAILED"} *****")

        return allOk
    }

    private fun printFailedTest(printer: Rt_Printer, r: UnitTestCaseResult) {
        printer.print("")

        val name = r.case.name
        printer.print("_".repeat(name.length.coerceAtMost(SEPARATOR_LEN)))
        printer.print(name)
        printer.print("")

        printException(printer, r.res.error!!, printPrettyLargeValues, true)
    }

    private fun printResults(printer: Rt_Printer, list: List<UnitTestCaseResult>) {
        if (list.isNotEmpty()) {
            printer.print("")
            for (r in list) {
                val str = r.res.caseResultToString(r.case)
                printer.print(str)
            }
        }
    }
}

object UnitTestRunner {
    fun getTestFunctions(app: R_App, matcher: UnitTestMatcher): List<R_FunctionDefinition> {
        val modules = app.modules
            .filter { it.test && it.selected }
            .sortedBy { it.name }

        val fns = modules.flatMap { getTestFunctions(it, matcher) }
        return fns
    }

    fun getTestFunctions(module: R_Module, matcher: UnitTestMatcher): List<R_FunctionDefinition> {
        return module.functions.values
            .filter { it.moduleLevelName == "test" || it.moduleLevelName.startsWith("test_") }
            .filter { it.params().isEmpty() }
            .filter { matcher.matchFunction(it.defName) }
    }

    fun runTests(testCtx: UnitTestRunnerContext, cases: List<UnitTestCase>): Boolean {
        val testRes = UnitTestRunnerResults(testCtx.printPrettyLargeValues)
        runTests(testCtx, cases, testRes)
        return testRes.print(testCtx.printer)
    }

    fun runTests(testCtx: UnitTestRunnerContext, cases: List<UnitTestCase>, testRes: UnitTestRunnerResults) {
        for (case in cases) {
            testCtx.onTestCaseStart(case)

            val v = runTestCase(testCtx, case)
            val caseRes = UnitTestCaseResult(case, v)

            testCtx.onTestCaseFinished(caseRes)
            testRes.add(caseRes)

            if (!v.isOk && testCtx.stopOnError) {
                break
            }
        }
    }

    private fun runTestCase(testCtx: UnitTestRunnerContext, case: UnitTestCase): UnitTestResult {
        val caseName = case.name
        val startTs = System.nanoTime()

        val printer = testCtx.casePrinter
        printer.print(PRINT_SEPARATOR)
        printer.print("TEST $caseName")

        val appCtx = testCtx.createAppContext()

        if (testCtx.sqlMgr.hasConnection) {
            SqlUtils.initDatabase(
                appCtx,
                testCtx.sqlCtx,
                testCtx.sqlMgr,
                adapter = testCtx.sqlInitProjExt,
                dropTables = true,
                sqlInitLog = false,
            )
        }

        return testCtx.sqlMgr.transaction { sqlExec ->
            val exeCtx = Rt_ExecutionContext(appCtx, Rt_NullOpContext, testCtx.sqlCtx, sqlExec)
            try {
                case.fn.callTop(exeCtx, listOf())
                processResult(printer, case, startTs, null)
            } catch (e: Throwable) {
                printException(printer, e, testCtx.printPrettyLargeValues, false)
                processResult(printer, case, startTs, e)
            }
        }
    }

    private fun processResult(printer: Rt_Printer, case: UnitTestCase, startTs: Long, e: Throwable?): UnitTestResult {
        val endTs = System.nanoTime()
        val duration = Duration.ofNanos(endTs - startTs)
        val res = UnitTestResult(duration, e)
        printer.print(res.caseResultToString(case))
        return res
    }
}

private fun printException(printer: Rt_Printer, e: Throwable, printPrettyLargeValues: Boolean, extended: Boolean) {
    when (e) {
        is Rt_Exception -> {
            if (extended && e.err is Rt_AssertEqualsError) {
                printAssertEqualsError(printer, e, e.err, printPrettyLargeValues)
            } else {
                val msg = Rt_Utils.appendStackTrace("Error: ${e.message}", e.info.stack)
                printer.print(msg)
            }
        }
        else -> {
            val s = Throwables.getStackTraceAsString(e)
            printer.print(s)
        }
    }
}

private fun printAssertEqualsError(
    printer: Rt_Printer,
    e: Rt_Exception,
    err: Rt_AssertEqualsError,
    printPrettyLargeValues: Boolean,
) {
    printer.print("Error: ${e.info.extraMessage}")
    printer.print("")

    val expectedStr = valueToStr(err.expected, 1000)
    val actualStr = valueToStr(err.actual, 1000)

    if (max(expectedStr.length, actualStr.length) < 80 || !printPrettyLargeValues) {
        printer.print("Expected: $expectedStr")
        printer.print("Actual:   $actualStr")
    } else {
        printer.print("Expected:")
        printAssertEqualsValuePretty(printer, err.expected)
        printer.print("")
        printer.print("Actual:")
        printAssertEqualsValuePretty(printer, err.actual)
    }
    printer.print("")

    printAssertEqualsErrorDiff(printer, err)

    val stackMsg = Rt_Utils.appendStackTrace("Stack trace:", e.info.stack)
    printer.print(stackMsg)
}

private fun printAssertEqualsValuePretty(printer: Rt_Printer, value: Rt_Value) {
    val s = value.strPretty(0)
    val lines = s.lines()
    val truncLines = lines.take(100)
    val s2 = truncLines.joinToString("\n")
    printer.print(s2)
    if (truncLines.size < lines.size) {
        printer.print("<truncated, ${lines.size - truncLines.size} more line(s)>")
    }
}

private fun printAssertEqualsErrorDiff(printer: Rt_Printer, err: Rt_AssertEqualsError) {
    val expectedStr = valueToStr(err.expected, 1000)
    val actualStr = valueToStr(err.actual, 1000)
    if (max(expectedStr.length, actualStr.length) < 40) {
        return
    }

    val type = err.expected.type()
    val type2 = err.actual.type()
    if (type2 != type || type.componentTypes().isEmpty()) {
        return
    }

    val diff = getValueDiff(type, err.expected, err.actual)
    if (diff.isEmpty()) {
        return
    }

    printer.print("Diff:")
    printValueDiff(printer, diff)

    printer.print("")
}

private fun printValueDiff(printer: Rt_Printer, diff: Map<List<String>, Pair<Rt_Value?, Rt_Value?>>) {
    val valueTrunc = 500
    val truncDiff = diff.entries.toList().take(20).associate { it.key to it.value }

    val groupDiff = mutableMapOf<List<String>, MutableMap<String, Pair<Rt_Value?, Rt_Value?>>>()
    for ((path, pair) in truncDiff) {
        if (path.isEmpty()) continue
        val basePath = path.dropLast(1)
        val last = path.last()
        val baseMap = groupDiff.computeIfAbsent(basePath) { mutableMapOf() }
        baseMap[last] = pair
    }

    for ((basePath, subMap) in groupDiff) {
        if (subMap.size > 1) {
            val basePathStr = basePath.joinToString("")
            printer.print("    $basePathStr")
            for ((key, pair) in subMap) {
                val (v1, v2) = pair
                printer.print("        $key")
                if (v1 != null) printer.print("            expected: ${valueToStr(v1, valueTrunc)}")
                if (v2 != null) printer.print("            actual:   ${valueToStr(v2, valueTrunc)}")
            }
        } else {
            val (key, pair) = subMap.entries.first()
            val pathStr = (basePath + immListOf(key)).joinToString("")
            printer.print("    $pathStr")
            val (v1, v2) = pair
            if (v1 != null) printer.print("        expected: ${valueToStr(v1, valueTrunc)}")
            if (v2 != null) printer.print("        actual:   ${valueToStr(v2, valueTrunc)}")
        }
    }

    if (truncDiff.size < diff.size) {
        printer.print("    <truncated, ${diff.size - truncDiff.size} more value(s)>")
    }
}

private fun getValueDiff(type: R_Type, v1: Rt_Value, v2: Rt_Value): Map<List<String>, Pair<Rt_Value?, Rt_Value?>> {
    return when {
        v1 == v2 -> immMapOf()
        type is R_NullableType && v1 != Rt_NullValue && v2 != Rt_NullValue -> getValueDiff(type.valueType, v1, v2)
        type is R_TupleType -> {
            val t1 = v1.asTuple()
            val t2 = v2.asTuple()
            val res = mutableMapOf<List<String>, Pair<Rt_Value?, Rt_Value?>>()
            for (field in type.fields) {
                val subV1 = t1[field.index]
                val subV2 = t2[field.index]
                val fieldKey = if (field.name != null) ".${field.name.str}" else "[${field.index}]"
                val fieldPath = immListOf(fieldKey)
                val subDiff = getValueDiff(field.type, subV1, subV2)
                for ((subPath, subPair) in subDiff) {
                    res[fieldPath + subPath] = subPair
                }
            }
            res.toImmMap()
        }
        type is R_ListType -> {
            val list1 = v1.asList()
            val list2 = v2.asList()
            getValueDiffList(list1, list2, type.elementType)
        }
        type is R_MapType -> {
            val map1 = v1.asMap()
            val map2 = v2.asMap()
            getValueDiffMap(map1, map2, type.valueType)
        }
        type is R_StructType -> {
            val s1 = v1.asStruct()
            val s2 = v2.asStruct()
            val res = mutableMapOf<List<String>, Pair<Rt_Value?, Rt_Value?>>()
            for (attr in type.struct.attributesList) {
                val subV1 = s1.get(attr.index)
                val subV2 = s2.get(attr.index)
                val subDiff = getValueDiff(attr.type, subV1, subV2)
                val attrPath = immListOf(".${attr.name}")
                for ((subPath, subPair) in subDiff) {
                    res[attrPath + subPath] = subPair
                }
            }
            res.toImmMap()
        }
        type == R_GtvType -> {
            val g1 = v1.asGtv()
            val g2 = v2.asGtv()
            if (g1.type == g2.type && g1.type == GtvType.ARRAY) {
                val list1 = g1.asArray().map { Rt_GtvValue.get(it) }
                val list2 = g2.asArray().map { Rt_GtvValue.get(it) }
                getValueDiffList(list1, list2, R_GtvType)
            } else if (g1.type == g2.type && g1.type == GtvType.DICT) {
                val map1 = g1.asDict()
                    .map { Rt_GtvValue.get(GtvFactory.gtv(it.key)) to Rt_GtvValue.get(it.value) }
                    .toMap()
                val map2 = g2.asDict()
                    .map { Rt_GtvValue.get(GtvFactory.gtv(it.key)) to Rt_GtvValue.get(it.value) }
                    .toMap()
                getValueDiffMap(map1, map2, R_GtvType)
            } else {
                immMapOf(immListOf<String>() to (v1 to v2))
            }
        }
        else -> immMapOf(immListOf<String>() to (v1 to v2))
    }
}

private fun getValueDiffList(
    list1: List<Rt_Value>,
    list2: List<Rt_Value>,
    elementType: R_Type,
): Map<List<String>, Pair<Rt_Value?, Rt_Value?>> {
    val res = mutableMapOf<List<String>, Pair<Rt_Value?, Rt_Value?>>()
    if (list1.size != list2.size) {
        res[immListOf(".size()")] = Rt_IntValue.get(list1.size.toLong()) to Rt_IntValue.get(list2.size.toLong())
    }
    for (i in 0 until min(list1.size, list2.size)) {
        val subV1 = list1[i]
        val subV2 = list2[i]
        val iPath = immListOf("[$i]")
        val subDiff = getValueDiff(elementType, subV1, subV2)
        for ((subPath, subPair) in subDiff) {
            res[iPath + subPath] = subPair
        }
    }
    for (i in min(list1.size, list2.size) until max(list1.size, list2.size)) {
        val subV1 = list1.getOrNull(i)
        val subV2 = list2.getOrNull(i)
        val iPath = immListOf("[$i]")
        res[iPath] = (subV1 to subV2)
    }
    return res.toImmMap()
}

private fun getValueDiffMap(
    map1: Map<Rt_Value, Rt_Value>,
    map2: Map<Rt_Value, Rt_Value>,
    valueType: R_Type,
): Map<List<String>, Pair<Rt_Value?, Rt_Value?>> {
    val res = mutableMapOf<List<String>, Pair<Rt_Value?, Rt_Value?>>()
    if (map1.size != map2.size) {
        res[immListOf(".size()")] = Rt_IntValue.get(map1.size.toLong()) to Rt_IntValue.get(map2.size.toLong())
    }
    val trunc = 500
    for (k in map1.keys.intersect(map2.keys)) {
        val subV1 = map1.getValue(k)
        val subV2 = map2.getValue(k)
        val kStr = valueToStr(k, trunc)
        val kPath = immListOf("[$kStr]")
        val subDiff = getValueDiff(valueType, subV1, subV2)
        for ((subPath, subPair) in subDiff) {
            res[kPath + subPath] = subPair
        }
    }
    for (k in map1.keys.minus(map2.keys)) {
        val kStr = valueToStr(k, trunc)
        val kPath = immListOf("[$kStr]")
        res[kPath] = (map1.getValue(k) to null)
    }
    for (k in map2.keys.minus(map1.keys)) {
        val kStr = valueToStr(k, trunc)
        val kPath = immListOf("[$kStr]")
        res[kPath] = (null to map2.getValue(k))
    }
    return res.toImmMap()
}

private fun valueToStr(v: Rt_Value, truncate: Int): String = Rt_AssertEqualsError.valueToStr(v, truncate)

class UnitTestMatcher private constructor(private val patterns: List<Pattern>) {
    fun matchFunction(defName: R_DefinitionName): Boolean {
        if (match(defName.simpleName) || match(defName.qualifiedName) || match(defName.module)) {
            return true
        }

        var appLevelName = defName.appLevelName
        if (defName.module.isEmpty() && defName.appLevelName == defName.qualifiedName) {
            appLevelName = ":$appLevelName"
        }

        return match(appLevelName)
    }

    private fun match(s: String): Boolean {
        return patterns.any { it.matcher(s).matches() }
    }

    companion object {
        val ANY = make(listOf("*"))

        fun make(patterns: List<String>): UnitTestMatcher {
            val patterns2 = patterns.map { globToPattern(it) }.toImmList()
            return UnitTestMatcher(patterns2)
        }

        fun globToPattern(s: String): Pattern {
            var pat = s
            val b = StringBuilder()

            while (true) {
                val i = StringUtils.indexOfAny(pat, "*?")
                if (i >= 0) {
                    if (i > 0) b.append(Pattern.quote(pat.substring(0, i)))
                    b.append(".")
                    if (pat[i] == '*') b.append("*")
                    pat = pat.substring(i + 1)
                } else {
                    b.append(Pattern.quote(pat))
                    break
                }
            }

            return Pattern.compile(b.toString())
        }
    }
}
