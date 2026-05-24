/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:Suppress("unused")
@file:JvmName("AocBenchmarkKt")

package net.postchain.rell.performance.benchmarks

import kotlinx.benchmark.*
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.rr.RR_QueryDefinition
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value
import org.openjdk.jmh.annotations.Fork
import kotlin.math.abs

/**
 * Advent of Code 2024 microbenchmarks, comparing the tree-walker, Truffle, and a Kotlin
 * baseline on workloads ported from https://github.com/mikaelstaldal/AdventOfCode2024.
 *
 * Each sample corresponds to one puzzle part (`dayNa` / `dayNb`) and runs against the small
 * example input embedded in the original source files. Rell source: `aoc_bench/main.rell`.
 *
 * The `kotlin` backend is a hand-port of the Rell logic with no algorithmic shortcuts —
 * same data structures (`ArrayList`, `LinkedHashSet`, data classes for named tuples), same
 * control flow, same regex / string-concat-based op-encoding for day 7 — so it functions as
 * a "what's the JVM ceiling on this code" reference for the same algorithm.
 *
 * Workload notes:
 *   - day1   → list parsing, in-place sort, abs-diff sum / nested count via Rell at-expr.
 *   - day2   → split-parse, predicate scan, dampener variant rebuilds the list per drop.
 *   - day3   → substring + regex.matches in a tight per-character loop (regex hot path).
 *   - day4   → 10x10 grid string-concat probe across 8 directions (string allocation heavy).
 *   - day5   → rules-vs-update predicate scan, bubble-sort-style fix-up loop with copy.
 *   - day6   → guard walk with cycle detection; part `b` re-walks per visited position.
 *   - day7   → recursive candidate generator (2^/3^ blow-up) with int + string-concat eval.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = BenchmarkTimeUnit.SECONDS)
@Fork(
    jvmArgsPrepend = [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "-XX:+UseJVMCINativeLibrary",
        "--enable-native-access=ALL-UNNAMED",
    ],
)
class AocBenchmark : RellBackendBenchmark() {

    @Param("interpreter", "truffle", "kotlin")
    lateinit var backend: String

    @Param(
        "day1a", "day1b",
        "day2a", "day2b",
        "day3a", "day3b",
        "day4a", "day4b",
        "day5a", "day5b",
        "day6a", "day6b",
        "day7a", "day7b",
    )
    lateinit var sample: String

    private lateinit var query: RR_QueryDefinition
    private var args: List<Rt_Value> = emptyList()
    private var reps: Long = 0L

    @Setup
    fun setUp() {
        reps = repsFor(sample)
        if (backend == "kotlin") return
        val rrApp = setUpBackend(backend, "aoc_bench/main.rell")
        query = rrApp.module(ModuleName.EMPTY)!!.queries.getValue("bench_$sample")
        args = listOf(Rt_IntValue.get(reps))
    }

    @Benchmark
    fun runQuery(blackhole: Blackhole) {
        if (backend == "kotlin") {
            blackhole.consume(KotlinBaselines.run(sample, reps))
        } else {
            blackhole.consume(interpreter.callQuery(query, exeCtx, args))
        }
    }

    // Smoke-test hook: returns the actual integer the query produced, so the smoke `main` can
    // cross-check the interpreter answer against the Kotlin baseline.
    internal fun runQueryForValue(): Long = when (backend) {
        "kotlin" -> KotlinBaselines.run(sample, reps)
        else -> (interpreter.callQuery(query, exeCtx, args) as Rt_IntValue).value
    }

    companion object {
        // Smoke-test hook (see `runQueryForValue`).
        internal fun repsForSample(sample: String): Long = repsFor(sample)

        // Per-sample reps sized so a single @Benchmark call lands in the low-millisecond
        // range on the Kotlin baseline (the cheapest of the three backends).
        private fun repsFor(sample: String): Long = when (sample) {
            "day1a", "day1b",
            "day2a", "day2b" -> 200L
            "day3a", "day3b" -> 100L
            "day4a", "day4b",
            "day5a", "day5b" -> 50L
            "day6a" -> 20L
            "day6b" -> 5L
            "day7a" -> 50L
            "day7b" -> 5L
            else -> error("Unknown sample: $sample")
        }
    }
}

// ----------------------------------------------------------------------------
// Kotlin baselines — direct ports of mikaelstaldal/AdventOfCode2024 day*.rell.
// Each `benchDayXY(reps)` mirrors the corresponding `query bench_dayXY(reps)`
// in `aoc_bench/main.rell`: outer accumulator loop wrapping the per-day driver
// invoked against the same hard-coded example input.
// ----------------------------------------------------------------------------

private object KotlinBaselines {

    fun run(sample: String, reps: Long): Long = when (sample) {
        "day1a" -> benchDay1a(reps)
        "day1b" -> benchDay1b(reps)
        "day2a" -> benchDay2a(reps)
        "day2b" -> benchDay2b(reps)
        "day3a" -> benchDay3a(reps)
        "day3b" -> benchDay3b(reps)
        "day4a" -> benchDay4a(reps)
        "day4b" -> benchDay4b(reps)
        "day5a" -> benchDay5a(reps)
        "day5b" -> benchDay5b(reps)
        "day6a" -> benchDay6a(reps)
        "day6b" -> benchDay6b(reps)
        "day7a" -> benchDay7a(reps)
        "day7b" -> benchDay7b(reps)
        else -> error("Unknown sample: $sample")
    }

    // ====== Day 1 ======

    private val DAY1_INPUT = listOf(
        "3   4",
        "4   3",
        "2   5",
        "1   3",
        "3   9",
        "3   3",
    )

    private fun day1ParseInput(input: List<String>): Pair<ArrayList<Long>, ArrayList<Long>> {
        val left = ArrayList<Long>()
        val right = ArrayList<Long>()
        for (line in input) {
            left.add(line.substring(0, line.indexOf(" ")).toLong())
            right.add(line.substring(line.lastIndexOf(" ") + 1).toLong())
        }
        return left to right
    }

    private fun day1a(input: List<String>): Long {
        val (left, right) = day1ParseInput(input)
        left.sort()
        right.sort()
        var sum = 0L
        for (i in 0 until input.size) {
            sum += abs(left[i] - right[i])
        }
        return sum
    }

    private fun day1b(input: List<String>): Long {
        val (left, right) = day1ParseInput(input)
        var sum = 0L
        for (n in left) {
            var times = 0L
            for (r in right) if (r == n) times += 1L
            sum += n * times
        }
        return sum
    }

    fun benchDay1a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day1a(DAY1_INPUT); i += 1L }
        return acc
    }

    fun benchDay1b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day1b(DAY1_INPUT); i += 1L }
        return acc
    }

    // ====== Day 2 ======

    private val DAY2_INPUT = listOf(
        "7 6 4 2 1",
        "1 2 7 8 9",
        "9 7 6 2 1",
        "1 3 2 4 5",
        "8 6 4 4 1",
        "1 3 6 7 9",
    )

    private fun day2ParseInput(input: List<String>): ArrayList<ArrayList<Long>> {
        val result = ArrayList<ArrayList<Long>>()
        for (line in input) {
            val row = ArrayList<Long>()
            for (tok in line.split(" ")) row.add(tok.toLong())
            result.add(row)
        }
        return result
    }

    private fun day2IsSafe(report: List<Long>): Boolean {
        val increasing = report[1] > report[0]
        for (i in 1 until report.size) {
            if (
                (increasing && (report[i] <= report[i - 1]))
                ||
                (!increasing && (report[i] >= report[i - 1]))
                ||
                (abs(report[i] - report[i - 1]) > 3L)
            )
                return false
        }
        return true
    }

    private fun day2a(input: List<String>): Long {
        val reports = day2ParseInput(input)
        var sum = 0L
        for (r in reports) if (day2IsSafe(r)) sum += 1L
        return sum
    }

    private fun day2IsSafeWithDampener(report: List<Long>): Boolean {
        if (day2IsSafe(report)) return true
        for (i in 0 until report.size) {
            val r = ArrayList(report)
            r.removeAt(i)
            if (day2IsSafe(r)) return true
        }
        return false
    }

    private fun day2b(input: List<String>): Long {
        val reports = day2ParseInput(input)
        var sum = 0L
        for (r in reports) if (day2IsSafeWithDampener(r)) sum += 1L
        return sum
    }

    fun benchDay2a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day2a(DAY2_INPUT); i += 1L }
        return acc
    }

    fun benchDay2b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day2b(DAY2_INPUT); i += 1L }
        return acc
    }

    // ====== Day 3 ======

    private const val DAY3A_INPUT = "xmul(2,4)%&mul[3,7]!@^do_not_mul(5,5)+mul(32,64]then(mul(11,8)mul(8,5))"
    private const val DAY3B_INPUT = "xmul(2,4)&mul[3,7]!^don't()_mul(5,5)+mul(32,64](mul(11,8)undo()?mul(8,5))"

    // Rell's `text.matches(regex)` requires a full-string match; Kotlin's `Regex.matches`
    // (and `String.matches(Regex)`) has the same semantics — `Matcher.matches()`.
    private val MUL_RE = Regex("mul\\([0-9]+,[0-9]+\\).*")
    private val DO_RE = Regex("do\\(\\).*")
    private val DONT_RE = Regex("don't\\(\\).*")

    private fun day3a(input: String): Long {
        var sum = 0L
        for (i in 0 until input.length) {
            if (input.substring(i).matches(MUL_RE)) {
                val comma = input.indexOf(",", i + 4)
                sum += input.substring(i + 4, comma).toLong() *
                        input.substring(comma + 1, input.indexOf(")", comma)).toLong()
            }
        }
        return sum
    }

    private fun day3b(input: String): Long {
        var enabled = true
        var sum = 0L
        for (i in 0 until input.length) {
            if (input.substring(i).matches(DO_RE)) {
                enabled = true
            } else if (input.substring(i).matches(DONT_RE)) {
                enabled = false
            } else if (enabled && input.substring(i).matches(MUL_RE)) {
                val comma = input.indexOf(",", i + 4)
                sum += input.substring(i + 4, comma).toLong() *
                        input.substring(comma + 1, input.indexOf(")", comma)).toLong()
            }
        }
        return sum
    }

    fun benchDay3a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day3a(DAY3A_INPUT); i += 1L }
        return acc
    }

    fun benchDay3b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day3b(DAY3B_INPUT); i += 1L }
        return acc
    }

    // ====== Day 4 ======

    private val DAY4_INPUT = listOf(
        "MMMSXXMASM",
        "MSAMXMSMSA",
        "AMXSXMAAMM",
        "MSAMASMSMX",
        "XMASAMXAMM",
        "XXAMMXXAMA",
        "SMSMSASXSS",
        "SAXAMASAAA",
        "MAMMMXMMMM",
        "MXMXAXMASX",
    )

    private fun day4a(input: List<String>): Long {
        val word = "XMAS"
        val reversedWord = word.reversed()
        val wordSize = word.length

        var occurances = 0L
        for (row in 0 until input.size) {
            for (col in 0 until input[row].length) {
                if (col <= input[row].length - wordSize) {
                    val part = "" + input[row][col] + input[row][col + 1] + input[row][col + 2] + input[row][col + 3]
                    if (part == word) occurances += 1L
                    if (part == reversedWord) occurances += 1L
                }
                if (row <= input.size - wordSize) {
                    val part = "" + input[row][col] + input[row + 1][col] + input[row + 2][col] + input[row + 3][col]
                    if (part == word) occurances += 1L
                    if (part == reversedWord) occurances += 1L
                }
                if ((row <= input.size - wordSize) && (col <= input[row].length - wordSize)) {
                    val part = "" + input[row][col] + input[row + 1][col + 1] + input[row + 2][col + 2] + input[row + 3][col + 3]
                    if (part == word) occurances += 1L
                    if (part == reversedWord) occurances += 1L
                }
                if ((row <= input.size - wordSize) && (col >= wordSize - 1)) {
                    val part = "" + input[row][col] + input[row + 1][col - 1] + input[row + 2][col - 2] + input[row + 3][col - 3]
                    if (part == word) occurances += 1L
                    if (part == reversedWord) occurances += 1L
                }
            }
        }
        return occurances
    }

    private fun day4b(input: List<String>): Long {
        var occurances = 0L
        for (row in 1 until input.size - 1) {
            for (col in 1 until input[row].length - 1) {
                if (input[row][col] == 'A') {
                    if (
                        ((input[row - 1][col - 1] == 'M' && input[row + 1][col + 1] == 'S') ||
                                (input[row - 1][col - 1] == 'S' && input[row + 1][col + 1] == 'M'))
                        &&
                        ((input[row - 1][col + 1] == 'M' && input[row + 1][col - 1] == 'S') ||
                                (input[row - 1][col + 1] == 'S' && input[row + 1][col - 1] == 'M'))
                    )
                        occurances += 1L
                }
            }
        }
        return occurances
    }

    fun benchDay4a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day4a(DAY4_INPUT); i += 1L }
        return acc
    }

    fun benchDay4b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day4b(DAY4_INPUT); i += 1L }
        return acc
    }

    // ====== Day 5 ======

    private val DAY5_INPUT = listOf(
        "47|53", "97|13", "97|61", "97|47", "75|29",
        "61|13", "75|53", "29|13", "97|29", "53|29",
        "61|53", "97|53", "61|29", "47|13", "75|47",
        "97|75", "47|61", "75|61", "47|29", "75|13",
        "53|13",
        "",
        "75,47,61,53,29",
        "97,61,53,29,13",
        "75,29,13",
        "75,97,47,61,53",
        "61,13,29",
        "97,13,75,29,47",
    )

    private fun day5ParseInput(input: List<String>): Pair<ArrayList<Pair<Long, Long>>, ArrayList<ArrayList<Long>>> {
        val rules = ArrayList<Pair<Long, Long>>()
        val updates = ArrayList<ArrayList<Long>>()
        var parsingPages = false
        for (line in input) {
            if (line.isEmpty()) {
                parsingPages = true
            } else if (parsingPages) {
                val u = ArrayList<Long>()
                for (tok in line.split(",")) u.add(tok.toLong())
                updates.add(u)
            } else {
                val parts = ArrayList<Long>()
                for (tok in line.split("|")) parts.add(tok.toLong())
                rules.add(parts[0] to parts[1])
            }
        }
        return rules to updates
    }

    private fun day5Middle(l: List<Long>): Long = l[(l.size - 1) / 2]

    private fun day5ValidUpdate(rules: List<Pair<Long, Long>>, anUpdate: List<Long>): Long {
        for (i in 1 until anUpdate.size) {
            for ((first, second) in rules) {
                if (second == anUpdate[i - 1] && first == anUpdate[i]) return 0L
            }
        }
        return day5Middle(anUpdate)
    }

    private fun day5InvalidUpdate(rules: List<Pair<Long, Long>>, anUpdate: List<Long>): Long {
        val corrected = ArrayList(anUpdate)
        var invalid = true
        while (invalid) {
            invalid = false
            for (i in 1 until anUpdate.size) {
                for ((first, second) in rules) {
                    if (second == corrected[i - 1] && first == corrected[i]) {
                        invalid = true
                        val page = corrected[i - 1]
                        corrected[i - 1] = corrected[i]
                        corrected[i] = page
                    }
                }
            }
        }
        return if (corrected != anUpdate) day5Middle(corrected) else 0L
    }

    private fun day5a(input: List<String>): Long {
        val (rules, updates) = day5ParseInput(input)
        var sum = 0L
        for (u in updates) sum += day5ValidUpdate(rules, u)
        return sum
    }

    private fun day5b(input: List<String>): Long {
        val (rules, updates) = day5ParseInput(input)
        var sum = 0L
        for (u in updates) sum += day5InvalidUpdate(rules, u)
        return sum
    }

    fun benchDay5a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day5a(DAY5_INPUT); i += 1L }
        return acc
    }

    fun benchDay5b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day5b(DAY5_INPUT); i += 1L }
        return acc
    }

    // ====== Day 6 ======

    private enum class D6 { N, S, E, W }

    private data class D6Pos(val r: Int, val c: Int)
    private data class D6PosDir(val r: Int, val c: Int, val direction: D6)

    private val DAY6_INPUT = listOf(
        "....#.....",
        ".........#",
        "..........",
        "..#.......",
        ".......#..",
        "..........",
        ".#..^.....",
        "........#.",
        "#.........",
        "......#...",
    )

    private fun day6FindStartPos(input: List<String>): D6PosDir {
        for (row in 0 until input.size) {
            for (col in 0 until input[row].length) {
                if (input[row][col] == '^') return D6PosDir(row, col, D6.N)
            }
        }
        return D6PosDir(-1, -1, D6.N)
    }

    private fun day6Step(input: List<String>, obstacle: D6Pos, current: D6PosDir): D6PosDir? {
        when (current.direction) {
            D6.N -> {
                if (current.r == 0) return null
                return if (input[current.r - 1][current.c] == '#' || (current.r - 1 == obstacle.r && current.c == obstacle.c)) {
                    day6Step(input, obstacle, D6PosDir(current.r, current.c, D6.E))
                } else {
                    D6PosDir(current.r - 1, current.c, current.direction)
                }
            }
            D6.E -> {
                if (current.c == input[current.r].length - 1) return null
                return if (input[current.r][current.c + 1] == '#' || (current.r == obstacle.r && current.c + 1 == obstacle.c)) {
                    day6Step(input, obstacle, D6PosDir(current.r, current.c, D6.S))
                } else {
                    D6PosDir(current.r, current.c + 1, current.direction)
                }
            }
            D6.S -> {
                if (current.r == input.size - 1) return null
                return if (input[current.r + 1][current.c] == '#' || (current.r + 1 == obstacle.r && current.c == obstacle.c)) {
                    day6Step(input, obstacle, D6PosDir(current.r, current.c, D6.W))
                } else {
                    D6PosDir(current.r + 1, current.c, current.direction)
                }
            }
            D6.W -> {
                if (current.c == 0) return null
                return if (input[current.r][current.c - 1] == '#' || (current.r == obstacle.r && current.c - 1 == obstacle.c)) {
                    day6Step(input, obstacle, D6PosDir(current.r, current.c, D6.N))
                } else {
                    D6PosDir(current.r, current.c - 1, current.direction)
                }
            }
        }
    }

    private fun day6a(input: List<String>): Long {
        val startPos = day6FindStartPos(input)
        val positions = LinkedHashSet<D6Pos>()
        var current = startPos
        while (true) {
            val next = day6Step(input, D6Pos(-1, -1), current) ?: break
            positions.add(D6Pos(next.r, next.c))
            current = next
        }
        return positions.size.toLong()
    }

    private fun day6b(input: List<String>): Long {
        val startPos = day6FindStartPos(input)
        val positions = LinkedHashSet<D6Pos>()
        var current = startPos
        while (true) {
            val next = day6Step(input, D6Pos(-1, -1), current) ?: break
            positions.add(D6Pos(next.r, next.c))
            current = next
        }

        var count = 0L
        for (pos in positions) {
            if (input[pos.r][pos.c] != '#' && !(pos.r == startPos.r && pos.c == startPos.c)) {
                val positions2 = LinkedHashSet<D6PosDir>()
                current = startPos
                while (true) {
                    val next = day6Step(input, pos, current) ?: break
                    if (next in positions2) {
                        count += 1L
                        break
                    } else {
                        positions2.add(next)
                        current = next
                    }
                }
            }
        }
        return count
    }

    fun benchDay6a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day6a(DAY6_INPUT); i += 1L }
        return acc
    }

    fun benchDay6b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day6b(DAY6_INPUT); i += 1L }
        return acc
    }

    // ====== Day 7 ======

    private val DAY7_INPUT = listOf(
        "190: 10 19",
        "3267: 81 40 27",
        "83: 17 5",
        "156: 15 6",
        "7290: 6 8 6 15",
        "161011: 16 10 13",
        "192: 17 8 14",
        "21037: 9 7 18 13",
        "292: 11 6 16 20",
    )

    private fun day7ParseEquation(line: String): Pair<Long, ArrayList<Long>> {
        val testValue = line.substring(0, line.indexOf(":")).toLong()
        val numbers = ArrayList<Long>()
        for (tok in line.substring(line.indexOf(":") + 2).split(" ")) numbers.add(tok.toLong())
        return testValue to numbers
    }

    private fun day7Apply(left: Long, op: Long, right: Long): Long = when (op) {
        0L -> left + right
        1L -> left * right
        2L -> (left.toString() + right.toString()).toLong()
        else -> error("Unknown op $op")
    }

    private fun day7Eval(exp: List<Long>): Long {
        var result = day7Apply(exp[0], exp[1], exp[2])
        var i = 3
        while (i < exp.size) {
            result = day7Apply(result, exp[i], exp[i + 1])
            i += 2
        }
        return result
    }

    private fun day7Concat1(left: List<Long>, right: List<Long>): ArrayList<Long> {
        val result = ArrayList(left)
        result.addAll(right)
        return result
    }

    private fun day7Concat2(
        a: List<List<Long>>,
        b: List<List<Long>>,
        c: List<List<Long>>,
    ): ArrayList<List<Long>> {
        val result = ArrayList<List<Long>>(a)
        result.addAll(b)
        result.addAll(c)
        return result
    }

    private fun day7GenerateCandidatesA(numbers: List<Long>): List<List<Long>> {
        if (numbers.size < 2) return listOf(numbers)
        val tail = numbers.subList(1, numbers.size)
        val a = ArrayList<List<Long>>()
        for (cand in day7GenerateCandidatesA(tail)) a.add(day7Concat1(listOf(numbers[0], 0L), cand))
        val b = ArrayList<List<Long>>()
        for (cand in day7GenerateCandidatesA(tail)) b.add(day7Concat1(listOf(numbers[0], 1L), cand))
        return day7Concat2(a, b, emptyList())
    }

    private fun day7GenerateCandidatesB(numbers: List<Long>): List<List<Long>> {
        if (numbers.size < 2) return listOf(numbers)
        val tail = numbers.subList(1, numbers.size)
        val a = ArrayList<List<Long>>()
        for (cand in day7GenerateCandidatesB(tail)) a.add(day7Concat1(listOf(numbers[0], 0L), cand))
        val b = ArrayList<List<Long>>()
        for (cand in day7GenerateCandidatesB(tail)) b.add(day7Concat1(listOf(numbers[0], 1L), cand))
        val c = ArrayList<List<Long>>()
        for (cand in day7GenerateCandidatesB(tail)) c.add(day7Concat1(listOf(numbers[0], 2L), cand))
        return day7Concat2(a, b, c)
    }

    private fun day7a(input: List<String>): Long {
        val equations = ArrayList<Pair<Long, ArrayList<Long>>>()
        for (line in input) equations.add(day7ParseEquation(line))
        var sum = 0L
        for ((testValue, numbers) in equations) {
            for (candidate in day7GenerateCandidatesA(numbers)) {
                if (day7Eval(candidate) == testValue) {
                    sum += testValue
                    break
                }
            }
        }
        return sum
    }

    private fun day7b(input: List<String>): Long {
        val equations = ArrayList<Pair<Long, ArrayList<Long>>>()
        for (line in input) equations.add(day7ParseEquation(line))
        var sum = 0L
        for ((testValue, numbers) in equations) {
            for (candidate in day7GenerateCandidatesB(numbers)) {
                if (day7Eval(candidate) == testValue) {
                    sum += testValue
                    break
                }
            }
        }
        return sum
    }

    fun benchDay7a(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day7a(DAY7_INPUT); i += 1L }
        return acc
    }

    fun benchDay7b(reps: Long): Long {
        var acc = 0L; var i = 0L
        while (i < reps) { acc += day7b(DAY7_INPUT); i += 1L }
        return acc
    }
}

fun main() {
    val b = Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.")
    val samples = listOf(
        "day1a", "day1b",
        "day2a", "day2b",
        "day3a", "day3b",
        "day4a", "day4b",
        "day5a", "day5b",
        "day6a", "day6b",
        "day7a", "day7b",
    )
    // Mirror the mna/ft4/struct smoke pattern: drive each workload once on the tree-walker
    // (validates the Rell module compiles and the query runs end-to-end without GraalVM), then
    // once on the in-language Kotlin baseline. Also cross-check that the interpreter and the
    // Kotlin baseline agree on the answer — if the hand-port drifts from the Rell semantics, a
    // mismatch shows up here rather than as a silently incorrect benchmark result.
    for (sample in samples) {
        val bmInterp = AocBenchmark()
        bmInterp.backend = "interpreter"
        bmInterp.sample = sample
        bmInterp.setUp()
        val reps = AocBenchmark.repsForSample(sample)
        val interpAnswer = bmInterp.runQueryForValue()
        val kotlinAnswer = KotlinBaselines.run(sample, reps)
        val tag = if (interpAnswer == kotlinAnswer) "ok" else "MISMATCH"
        println("smoke sample=$sample reps=$reps interp=$interpAnswer kotlin=$kotlinAnswer $tag")
        b.consume(interpAnswer)
        b.consume(kotlinAnswer)
    }
}
