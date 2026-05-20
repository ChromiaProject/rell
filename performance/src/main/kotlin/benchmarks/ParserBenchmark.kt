/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.toolbox.parser.AntlrRellParser
import org.antlr.v4.runtime.BailErrorStrategy
import org.antlr.v4.runtime.atn.PredictionMode

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
class ParserBenchmark {

    @Param("profiling-dapp", "deathmatch", "operations_queries_api")
    lateinit var sample: String

    private lateinit var source: String
    private lateinit var sourcePath: C_SourcePath
    private lateinit var idePath: IdeSourcePathFilePath
    private lateinit var antlrParser: AntlrRellParser

    @Setup
    fun setUp() {
        val resource = "samples/$sample.rell"

        source = javaClass.classLoader.getResourceAsStream(resource)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Benchmark resource not found on classpath: $resource")

        sourcePath = C_SourcePath.parse("$sample.rell")
        idePath = IdeSourcePathFilePath(sourcePath)
        antlrParser = AntlrRellParser()
    }

    @Benchmark
    fun betterParse(blackhole: Blackhole) = blackhole.consume(C_Parser.parse(sourcePath, idePath, source))

    @Benchmark
    fun antlr(blackhole: Blackhole) = blackhole.consume(antlrParser.parse(source))

    @Benchmark
    fun antlrSLL(blackhole: Blackhole) {
        val parser = antlrParser.parserFor(source)
        parser.errorHandler = BailErrorStrategy()
        parser.buildParseTree = false
        parser.interpreter.predictionMode = PredictionMode.SLL
        blackhole.consume(parser.ruleX_RootParser())
    }
}
