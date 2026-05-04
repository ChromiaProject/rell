/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")

package net.postchain.rell.benchmarks

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

    /** better-parse path used by the Rell compiler; produces the AST (`S_RellFile`). */
    @Benchmark
    fun betterParse(blackhole: Blackhole) = blackhole.consume(C_Parser.parse(sourcePath, idePath, source))

    /**
     * ANTLR path used by the toolbox (`AntlrRellParser`): LL prediction, full parse tree,
     * default error recovery. This is what production ships today. Not apples-to-apples
     * with [betterParse] — different parser technology — but kept as the headline number.
     */
    @Benchmark
    fun antlr(blackhole: Blackhole) = blackhole.consume(antlrParser.parse(source))

    /**
     * Same `Rell.g4` grammar as [antlr], reconfigured for the fast path: SLL
     * prediction with `BailErrorStrategy` and `buildParseTree = false`. Measures the
     * SLL win on inputs that don't hit a prediction conflict — i.e. all valid Rell
     * programs in the corpus. The compiler uses this configuration on its hot path
     * and falls back to LL only when SLL bails.
     */
    @Benchmark
    fun antlrSLL(blackhole: Blackhole) {
        val parser = antlrParser.parserFor(source)
        parser.errorHandler = BailErrorStrategy()
        parser.buildParseTree = false
        parser.interpreter.predictionMode = PredictionMode.SLL
        blackhole.consume(parser.file())
    }
}
