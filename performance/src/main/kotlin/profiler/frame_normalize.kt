/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.profiler

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Strip per-run-volatile bits from async-profiler frame strings so that two profiles of the same
 * workload diff cleanly under text comparison.
 *
 * Targets:
 *  - Lambda class IDs: `Foo$$Lambda.0x000000d8013d6a68` / `Foo$$Lambda/0x000000d8013d6a68` /
 *    `Foo$$Lambda$42/0x000000d8013d6a68` / `Foo$$Lambda$<40-hex>` → `Foo$$Lambda`. The hex
 *    address is the runtime classloader cookie — different every JVM run.
 *  - HotSpot stub / generated-name hashes: `Class_method_<32+hex>` → `Class_method`. These
 *    appear on barrier / Graal stub / FactoryMethodHolder names whose suffix is a build-derived
 *    hash that varies between JVM builds and obscures pattern matching.
 */
internal fun normalizeFrames(text: String): String {
    val sb = StringBuilder(text.length)
    var idx = 0
    while (idx < text.length) {
        val end = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
        val line = text.substring(idx, end)
        val normalized = LAMBDA_HEX.matcher(line).replaceAll(Matcher.quoteReplacement(LITERAL_LAMBDA))
            .let { STUB_HEX_SUFFIX.matcher(it).replaceAll("") }
        sb.append(normalized)
        if (end < text.length) sb.append('\n')
        idx = end + 1
    }
    return sb.toString()
}

// `$$Lambda` followed by a hex cookie in any of the JVM's flavours:
//  - HotSpot raw:        `Foo$$Lambda.0x000000d8013d6a68` / `Foo$$Lambda/0x000000d8013d6a68`
//  - HotSpot numbered:   `Foo$$Lambda$42/0x000000d8013d6a68`
//  - jfr-converter norm: `Foo$$Lambda$1341083539cb284cc904e7c5082c4127000b41db`
private val LAMBDA_HEX: Pattern = Pattern.compile(
    "\\\$\\\$Lambda(?:[./\\\$]?(?:0x)?[0-9a-fA-F]{8,})+",
)

// Trailing `_<32+ hex chars>` glued onto an identifier — HotSpot / Graal generated suffix.
// 32 chars covers MD5; SHA-1 lands at 40.
//  - The `(?:_[0-9a-f]{32,})+` outer repetition catches runs of consecutive hashes
//    (`FactoryMethodHolder_X_<hash1>_<hash2>`).
//  - We use a "next char is not hex" lookahead instead of `\b` because some libgraal
//    frames append a suffix like `_<hash>_[0]` (frame-type marker), where `\b` between
//    hex and the underscore fails (both are word chars) but the hash IS what we want gone.
private val STUB_HEX_SUFFIX: Pattern = Pattern.compile("(?:_[0-9a-f]{32,})+(?=[^0-9a-f]|\$)")

private const val LITERAL_LAMBDA = "\$\$Lambda"
